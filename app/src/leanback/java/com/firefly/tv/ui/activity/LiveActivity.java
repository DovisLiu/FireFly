package com.firefly.tv.ui.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerSeekView;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.transition.Transition;
import com.firefly.tv.App;
import com.firefly.tv.Constant;
import com.firefly.tv.R;
import com.firefly.tv.api.config.LiveConfig;
import com.firefly.tv.bean.Channel;
import com.firefly.tv.bean.Config;
import com.firefly.tv.bean.Epg;
import com.firefly.tv.bean.EpgData;
import com.firefly.tv.bean.Group;
import com.firefly.tv.bean.Keep;
import com.firefly.tv.bean.Live;
import com.firefly.tv.bean.Result;
import com.firefly.tv.databinding.ActivityLiveBinding;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.impl.Callback;
import com.firefly.tv.impl.ConfigListener;
import com.firefly.tv.impl.CustomTarget;
import com.firefly.tv.impl.LiveListener;
import com.firefly.tv.impl.PassListener;
import com.firefly.tv.model.LiveViewModel;
import com.firefly.tv.playback.PlaybackAction;
import com.firefly.tv.playback.PlaybackReset;
import com.firefly.tv.playback.PlaybackResult;
import com.firefly.tv.playback.live.LivePlayRequest;
import com.firefly.tv.playback.live.LivePlaybackController;
import com.firefly.tv.playback.live.LivePlaybackHost;
import com.firefly.tv.player.extractor.Source;
import com.firefly.tv.service.PlaybackService;
import com.firefly.tv.setting.LiveSetting;
import com.firefly.tv.setting.PlayerSetting;
import com.firefly.tv.ui.adapter.ChannelAdapter;
import com.firefly.tv.ui.adapter.EpgDataAdapter;
import com.firefly.tv.ui.adapter.GroupAdapter;
import com.firefly.tv.ui.custom.CustomKeyDownLive;
import com.firefly.tv.ui.custom.CustomLiveListView;
import com.firefly.tv.ui.custom.NavDelegate;
import com.firefly.tv.ui.custom.NavOverlay;
import com.firefly.tv.ui.dialog.HistoryDialog;
import com.firefly.tv.ui.dialog.LiveDialog;
import com.firefly.tv.ui.dialog.PassDialog;
import com.firefly.tv.ui.dialog.PlayerEngineDialog;
import com.firefly.tv.ui.dialog.SpeedSettingDialog;
import com.firefly.tv.ui.dialog.TrackDialog;
import com.firefly.tv.utils.Clock;
import com.firefly.tv.utils.ImgUtil;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.ResUtil;
import com.firefly.tv.utils.Traffic;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LiveActivity extends PlaybackActivity implements GroupAdapter.OnClickListener, ChannelAdapter.OnClickListener, EpgDataAdapter.OnClickListener, CustomKeyDownLive.Listener, CustomLiveListView.Callback, PassListener, ConfigListener, LiveListener, LivePlaybackHost {

    private ActivityLiveBinding mBinding;
    private LiveViewModel mViewModel;
    private LivePlaybackController mLive;
    private GroupAdapter mGroupAdapter;
    private ChannelAdapter mChannelAdapter;
    private EpgDataAdapter mEpgDataAdapter;
    private CustomKeyDownLive mKeyDown;
    private NavOverlay mOverlay;
    // β19 / T-314①（刘先生：「从导航菜单点直播后，直播页面刷出时焦点直接落到直播分类菜单，
    // 与点播的处理方式不同（点播是焦点停留在导航的点播，下翻进入分类菜单）」）：
    // 进场后的**第一次**面板呼出不再把焦点甩到分组行，而是留在导航「直播」项；之后的每次 showUI 照旧落分组行
    // （T-154 拍板 50 的语义不变）。标志位只被消费一次，不进任何持久化状态。
    private boolean mFirstPanelFocus = true;
    // β15 / T-222甲：面板内 UP 的 DOWN 已就地完成移动（或长按重复 DOWN 已交回网格），
    // 抬起必须吃掉 ⇒ 一次键只动一行。语义与组③的"状态标记"同一条路（读后即清 ＋ 别键作废），不引入时间窗。
    private boolean mPanelUpArmed;
    private Clock mClock;
    private View mOldView;
    private View mFocus2;
    private Runnable mR0;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Runnable mR5;
    private List<Group> mHides;
    private Group mGroup;
    private Channel mChannel;
    private Channel mPreviewTarget;
    private boolean mPreviewed;
    private String mPlaybackKey;
    private String mIntentGroup;
    private String mIntentChannel;
    private int count;

    public static void start(Context context) {
        context.startActivity(new Intent(context, LiveActivity.class).putExtra("empty", LiveConfig.isEmpty()));
    }

    /** T-141（spec §3.5 + 拍板 44）：从直播选台页确认选台后进入——带分组/频道名，进页直接起播该频道 */
    public static void start(Context context, String group, String channel) {
        context.startActivity(new Intent(context, LiveActivity.class)
                .putExtra("empty", LiveConfig.isEmpty())
                .putExtra("group", group)
                .putExtra("channel", channel));
    }

    private boolean isEmpty() {
        return getIntent().getBooleanExtra("empty", true);
    }

    private Group getKeep() {
        return mGroupAdapter.get(0);
    }

    private Live getHome() {
        return LiveConfig.get().getHome();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityLiveBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected String getPlaybackKey() {
        return mPlaybackKey;
    }

    @Override
    protected PlayerView getPlayerView() {
        return mBinding.player;
    }

    @Override
    protected PlayerSeekView getSeekView() {
        return mBinding.control.seek;
    }

    @Override
    protected void onServiceConnected() {
        mLive.onPlaybackServiceReady();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        // T-141（拍板 44）：选台页传入的分组/频道（仅消费一次，回退/刷新不重复起播）
        mIntentGroup = getIntent().getStringExtra("group");
        mIntentChannel = getIntent().getStringExtra("channel");
        mClock = Clock.create(mBinding.widget.clock);
        // T-191-a（Round 40）：本页补上通用顶栏。LOGO/时间保持不可见（拍板 41 补充「沉浸页不保留 LOGO/时间」
        // 仍然有效；INVISIBLE 而非 GONE，是为保住 bounds 不参与焦点搜索——不可见的视图拿不到焦点，
        // 这正好也挡住了 188-b 里「UP 落到 LOGO」的那条路径），只让胶囊菜单常驻。
        mBinding.navBar.logo.setVisibility(View.INVISIBLE);
        mBinding.navBar.clock.setVisibility(View.INVISIBLE);
        mOverlay = NavDelegate.bind(this, null, mBinding.navBar.topNav, R.id.nav_live);
        mKeyDown = CustomKeyDownLive.create(this);
        mHides = new ArrayList<>();
        mR0 = this::setSelected;
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::hideInfo;
        mR4 = this::hideUIAuto; // T-174：自动隐藏走抑制版（面板态不自动收起）
        mR5 = this::applyPreview;
        setRecyclerView();
        setVideoView();
        setViewModel();
        checkLive();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.group.setListener(this);
        // T-154：channel 改用 VerticalGridView（真 2D 网格）——不再有 CustomLiveListView.setListener
        mBinding.epgData.setListener(this);
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.home.setOnClickListener(view -> onHome());
        mBinding.control.action.line.setOnClickListener(view -> onLine());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.config.setOnClickListener(view -> onConfig());
        mBinding.control.action.action.setOnClickListener(view -> onAction());
        mBinding.control.action.invert.setOnClickListener(view -> onInvert());
        mBinding.control.action.across.setOnClickListener(view -> onAcross());
        // β43 / A2：本排原末项「换源」（id=change，实际功能是切线路、与第 4 项「线路」重复）已删，
        // 其回调 onChange() 一并删除；「换源」这个名字改给 id=config 的**选直播源**按钮（XML 里改名并移到末项），
        // 回调仍走 onConfig()，一字未改。
        mBinding.control.action.player.setOnClickListener(view -> onPlayer());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.group.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mGroupAdapter.getItemCount() > 0) onChildSelected(child, mGroup = mGroupAdapter.get(position));
            }
        });
        // 频道焦点跟随预览（spec §3.5/§3.6：焦点停 800ms 起播试看）
        mBinding.channel.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mChannelAdapter.getItemCount() > 0 && position >= 0) onChannelFocused(mChannelAdapter.get(position));
            }
        });
    }

    private void setRecyclerView() {
        mBinding.group.setItemAnimator(null);
        mBinding.channel.setItemAnimator(null);
        mBinding.epgData.setItemAnimator(null);
        // T-154：频道网格（VerticalGridView）卡间距——BaseGridView 间距无 xml 属性，用代码 API 设置
        mBinding.channel.setHorizontalSpacing(ResUtil.dp2px(12));
        mBinding.channel.setVerticalSpacing(ResUtil.dp2px(12));
        mBinding.group.setAdapter(mGroupAdapter = new GroupAdapter(this));
        mBinding.channel.setAdapter(mChannelAdapter = new ChannelAdapter(this));
        mBinding.epgData.setAdapter(mEpgDataAdapter = new EpgDataAdapter(this));
    }

    private void setVideoView() {
        setScale(LiveSetting.getScale());
        setSeekNextFocusDown(R.id.config);
        setActionFocusBoundary(mBinding.control.action.getRoot());
        PlayerEngineDialog.setText(mBinding.control.action.player);
        mBinding.control.action.invert.setSelected(LiveSetting.isInvert());
        mBinding.control.action.across.setSelected(LiveSetting.isAcross());
        // β43 / A2：原「换源」（id=change）按钮与其 setSelected(false) 一并删除——T-191-c 那段说明（动作按钮
        // 不表达选中态）随本按钮消失；isChange 偏好本身仍由报错回落路径（LivePlaybackController:191）消费。
        // invert/across 仍是真开关，选中态照旧从 LiveSetting 回读；config 是动作按钮，也不表达选中态。
    }

    private void setPlaybackMode() {
        PlaybackAction.setPlaybackMode(player(), mBinding.control.action.player, mBinding.control.action.decode);
    }

    private void setScale(int scale) {
        LiveSetting.putScale(scale);
        mBinding.player.setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(LiveViewModel.class);
        mLive = mViewModel.createPlaybackController(this);
        observeWhenServiceReady(mViewModel.playback(), this::onPlaybackObserved);
        observeWhenServiceReady(mViewModel.error(), this::onLoadErrorObserved);
        observeForever(mViewModel.epg(), this::onEpgLoaded);
        mViewModel.live().observe(this, this::onLiveParsed);
        mViewModel.xml().observe(this, this::onXmlParsed);
    }

    private void onLiveParsed(Live live) {
        mViewModel.parseXml(live);
        setGroup(live);
        setWidth(live);
    }

    private void onPlaybackObserved(PlaybackResult<LivePlayRequest> result) {
        mLive.onPlaybackResult(result);
    }

    private void onLoadErrorObserved(String msg) {
        if (msg == null || msg.isEmpty()) return;
        resetPlaybackForError(msg);
    }

    private void checkLive() {
        if (isEmpty()) {
            LiveConfig.get().init().load(getCallback());
        } else {
            getLive();
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                getLive();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    private void getLive() {
        mBinding.control.action.home.setText(LiveConfig.isOnly() ? getString(R.string.live_refresh) : getHome().getName());
        mViewModel.parse(getHome());
        showProgress();
    }

    private void setGroup(Live live) {
        List<Group> items = new ArrayList<>();
        // T-153（C beta5 实测：选台页 OK 进播放页 NPE Group.isHidden on null）：遍历前过滤 null 分组，
        // 解析产生的 null 在 LiveApi.parse 已清理，此处再兜底一层，任何情况下不崩溃。
        int nulls = 0;
        for (Group group : live.getGroups()) {
            if (group == null) {
                nulls++;
                continue;
            }
            (group.isHidden() ? mHides : items).add(group);
        }
        if (nulls > 0) Log.w("FireFlyLive", "setGroup: skip " + nulls + " null group(s), groups=" + live.getGroups().size());
        mGroupAdapter.addAll(items);
        // T-140（spec §3.5 拍板 9/13，刘先生 2026-10-26 实测回归）：进直播必先到选台页——
        // 原实现无条件 setPosition → selectChannel 立即起播（无历史时 findKeepPosition 还返回 {1,0}），
        // 源流不可用即黑屏；改为只做面板定位、不自动起播。
        if (items.isEmpty()) {
            // 空态兜底（拍板 9）：解析失败/无频道必须给提示，不能黑屏直接播放
            Notify.show(getString(R.string.live_empty));
            return;
        }
        if (mIntentChannel != null) {
            // T-141（spec §3.5 + 拍板 44）：从选台页确认选台进入——定位该频道并直接起播（播放态，不弹面板）
            playIntentChannel(items);
            mIntentGroup = null;
            mIntentChannel = null;
            return;
        }
        setPosition(LiveConfig.get().findKeepPosition(items), false);
        // 直播选台先行（规格 §3.5）：首进先显示选台面板，选台确认后进播放
        showUI();
        // 选台页不自动收起面板（5s 自动隐藏只用于播放中呼出面板的场景，T-140）
        App.removeCallbacks(mR4);
    }

    /** 选台页确认选台后的直接起播：按名字定位分组/频道（找不到则退第 1 组第 1 台），起播并收面板 */
    private void playIntentChannel(List<Group> items) {
        Log.d("FireFlyLive", "playIntentChannel group=" + mIntentGroup + " channel=" + mIntentChannel + " groups=" + items.size());
        Group group = null;
        for (Group item : items) {
            // T-153：空值防护——分组为 null 或名为空时跳过，绝不 NPE
            if (item == null || item.getName() == null) continue;
            if (item.getName().equals(mIntentGroup)) {
                group = item;
                break;
            }
        }
        if (group == null && !items.isEmpty()) group = items.get(0);
        // T-153：查不到分组/频道时安全降级——留在面板选台（不自动起播、不崩溃）
        if (group == null || group.getChannel().isEmpty()) {
            showUI();
            App.removeCallbacks(mR4);
            return;
        }
        int index = group.find(mIntentChannel);
        if (index < 0 || index >= group.getChannel().size()) index = 0;
        mGroup = group;
        mBinding.group.setSelectedPosition(items.indexOf(group));
        group.setPosition(index);
        mLive.selectGroup(group);
        mLive.selectChannel(group.getChannel().get(index)); // 用户已确认选台，起播
        hideUI();
    }

    private void setWidth(Live live) {
        // 换台面板已全屏化（效果图 09），分组改横向胶囊行，不再按内容计算列宽
    }

    private Group setWidth(Group group) {
        // 频道已改网格布局（5 列），不再按内容计算列宽
        return group;
    }

    private void setWidth(Epg epg) {
        int padding = ResUtil.dp2px(52);
        if (epg.getList().isEmpty()) return;
        int minWidth = ResUtil.getTextWidth(epg.getList().get(0).getTime(), 14);
        if (epg.getWidth() == 0) for (EpgData item : epg.getList()) epg.setWidth(Math.max(epg.getWidth(), ResUtil.getTextWidth(item.getTitle(), 16)));
        int maxWidth = ResUtil.getScreenWidth() / 2;
        int minContentWidth = Math.min(minWidth + padding, maxWidth);
        int width = epg.getWidth() == 0 ? 0 : Math.clamp(epg.getWidth() + padding, minContentWidth, maxWidth);
        setWidth(mBinding.epgData, width);
    }

    private void setWidth(View view, int width) {
        view.post(() -> {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params.width == width) return;
            params.width = width;
            view.setLayoutParams(params);
        });
    }

    private void setPosition(int[] position) {
        setPosition(position, true);
    }

    /** @param play true=选中即起播（数字键找台等场景）；false=只做面板定位不播放（进页选台，T-140） */
    private void setPosition(int[] position, boolean play) {
        if (position[0] == -1) return;
        int size = mGroupAdapter.getItemCount();
        if (size == 1 || position[0] >= size) return;
        mGroup = mGroupAdapter.get(position[0]);
        mBinding.group.setSelectedPosition(position[0]);
        mGroup.setPosition(position[1]);
        mLive.selectGroup(mGroup);
        if (play) mLive.selectChannel(mGroup.current());
    }

    private void setPosition() {
        if (mChannel == null) return;
        mGroup = mChannel.getGroup();
        int position = mGroupAdapter.indexOf(mGroup);
        boolean change = mBinding.group.getSelectedPosition() != position;
        if (change) mBinding.group.setSelectedPosition(position);
        if (change) mChannelAdapter.addAll(mGroup.getChannel());
        mBinding.channel.setSelectedPosition(mGroup.getPosition());
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child, Group group) {
        if (mOldView != null) mOldView.setSelected(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setSelected(true);
        onItemClick(group);
        resetPass();
    }

    private void setSelected() {
        mChannelAdapter.setSelected(mChannel);
        notifyItemChanged(mBinding.channel, mChannelAdapter);
    }

    private void setSelected(EpgData item) {
        mEpgDataAdapter.setSelected(item);
        notifyItemChanged(mBinding.epgData, mEpgDataAdapter);
    }

    private void checkPlay() {
        if (player().isPlaying()) onPaused();
        else onPlay();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).view(mBinding.player.getSubtitleView()).show(this);
        hideControl();
    }

    private void onHome() {
        if (LiveConfig.isOnly()) setLive(getHome());
        else LiveDialog.create().show(this);
        hideControl();
    }

    private void onLine() {
        nextLine(false);
    }

    private void onScale() {
        int index = LiveSetting.getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        setScale(index == array.length - 1 ? 0 : ++index);
    }

    private void onSpeed() {
        SpeedSettingDialog.create().player(player()).show(this);
        hideControl();
    }

    private boolean onSpeedLong() {
        PlaybackAction.toggleSpeed(player(), mBinding.widget.message);
        return true;
    }

    private void onConfig() {
        HistoryDialog.create().live().readOnly().show(this);
        hideControl();
    }

    private void onAction() {
        checkPlay();
    }

    private void onInvert() {
        // T-191-c（Round 40，A 裁定 §三）：与同排按钮统一为「点击即生效 + 关闭菜单」，不再用选中态自证 toggle。
        // 语义查实：反转＝换台方向反转（不是画面镜像）。消费点在 onKeyUp/onKeyDown/onKeyLeft/onKeyRight
        // 四处（本文件 1168/1178/1189/1203 一带），**此前就已接**；但常态下 UP/DOWN 都要换台，
        // 用户看不出"按与不按的区别"，所以本轮补 Toast 作为可验证反馈 + 立即收菜单。
        boolean invert = !LiveSetting.isInvert();
        LiveSetting.putInvert(invert);
        mBinding.control.action.invert.setSelected(invert);
        Notify.show(getString(invert ? R.string.live_invert_on : R.string.live_invert_off));
        Log.d("FireFlyLive", "invert click=" + invert + " action=nextPrevSwap");
        hideControl();
    }

    private void onAcross() {
        // T-191-c：跨类＝组边界是否跨组换台（不是"切换分类"）。消费点唯一：
        // LivePlaybackController:163 `if (LiveSetting.isAcross() && limit) moveGroup(delta);`
        // ⇒ 只有"已到本组首/尾还往外按"时才显形，常态按 DOWN/UP 在组内走时看不出差别，故同样补 Toast。
        boolean across = !LiveSetting.isAcross();
        LiveSetting.putAcross(across);
        mBinding.control.action.across.setSelected(across);
        Notify.show(getString(across ? R.string.live_across_on : R.string.live_across_off));
        Log.d("FireFlyLive", "across click=" + across + " action=groupBoundary");
        hideControl();
    }

    /**
     * β43 / A3（刘先生拍板：直播页左右键一律换线路，不要快进快退）：左右键共用的切线路入口。
     * 由原菜单按钮 `onChange()`（那个叫"换源"、实为切下一条线路的按钮，A2 已删该按钮）搬来，
     * 保留它两条有价值的口径——①**单线路频道必须给提示**（`Channel.switchLine` 在 isOnly 时是静默 return，
     * 用户会觉得"按了没反应"）；②每次切换都留痕 idx/total/当前是否回看态。
     * 方向语义：左＝上一条、右＝下一条，循环（{@code Channel.switchLine} 取模，见 bean/Channel.java:307-313）。
     * 刻意不读 {@code LiveSetting.isInvert()}——反转只管换台方向，与线路无关。
     */
    private void changeLine(boolean next) {
        Channel channel = mChannel;
        if (channel == null) return;
        if (channel.isOnly()) {
            Notify.show(getString(R.string.live_line_only));
            Log.d("FireFlyLive", "line " + (next ? "right" : "left") + "=skip reason=onlyLine total=1");
            return;
        }
        nextLine(next);
        Log.d("FireFlyLive", "line " + (next ? "right" : "left") + "=nextLine idx=" + channel.getIndex()
                + " total=" + channel.getUrls().size() + " mode=" + (player().isLive() ? "live" : "catchup"));
    }

    private void onPlayer() {
        PlayerEngineDialog.show(this, mBinding.control.action.player, player());
        hideControl();
    }

    private void onDecode() {
        player().toggleDecode();
    }

    /** T-174（C beta15：焦点进频道网格后 ~1s 面板自动收起——mR4 5s 定时剩余触发）：
     *  自动隐藏路径（mR4 定时等）——面板态一律抑制，只有用户操作（OK toggle/BACK/换台）才收起面板 */
    private void hideUIAuto() {
        if (isVisible(mBinding.recycler)) {
            Log.d("FireFlyLive", "autoHide suppressed panelVis=true");
            return;
        }
        hideUI();
    }

    private void hideUI() {
        App.removeCallbacks(mR4);
        App.removeCallbacks(mR5);
        mPreviewed = false;
        // β45 / C（A 派单第②点）：顶栏收起**提到早退之前**。原顺序下 `if (isGone(recycler)) return;` 会让
        // 下面那句 GONE 在"面板本来就已收起（＝全屏播放态）"时永远执行不到——这就是刘先生看到的
        // "69 版直播进入全屏后，导航菜单没有收起"的直接成因之一。提到前面后它是幂等赋值，早退挡不住它。
        // T-205：沉浸播放态顶栏**真隐藏**（GONE 而非靠 video 盖住）——退出直播是 finish 回
        // LiveSelectActivity，那边有自己的顶栏，天然恢复；UP 主动呼出时由 :1134 那条分支再显。
        mBinding.navBar.getRoot().setVisibility(View.GONE);
        // β45 / C：顶栏可见性变更原先在 logcat 上完全静默（取证缺口），补一条正向留痕给真机对照用。
        // recGone=false＝这次是"面板可见→收起"顺带收顶栏；true＝面板本就收起，这次是收回路径生效。
        Log.d("FireFlyLive", "nav hide by hideUI recGone=" + isGone(mBinding.recycler));
        if (isGone(mBinding.recycler)) return;
        mBinding.recycler.setVisibility(View.GONE);
        // T-168：面板收起后恢复 video 区域可聚焦（面板态曾禁用，防焦点逃逸）
        mBinding.video.setFocusable(true);
        setPosition();
    }

    private void showUI() {
        if (isVisible(mBinding.recycler) || mGroupAdapter.getItemCount() == 0) return;
        // T-205：面板态顶栏恢复可见（与 hideUI 的 GONE 成对）
        mBinding.navBar.getRoot().setVisibility(View.VISIBLE);
        // 面板顶部频道名大字（效果图 09）
        if (mChannel != null) mBinding.channelName.setText(mChannel.getNumber().concat(" ").concat(mChannel.getName()));
        mBinding.recycler.setVisibility(View.VISIBLE);
        // T-168（C beta11：面板网格边缘 RIGHT/LEFT/UP 焦点逃逸到全屏 video FrameLayout）：
        // 面板显示期间禁用 video 区域可聚焦——收紧焦点域到面板内（channel 已配 focusOutEnd/focusOutFront=false），
        // hideUI 时恢复；这是"面板内自由选台"的必要条件
        mBinding.video.setFocusable(false);
        setPosition();
        // T-166（C beta10 实测：面板内按 BACK 直接退出播放回选台页）：根因=面板 5s 自动隐藏
        // （showUI/dispatchKeyEvent 的 setUITimer）→ C 按 BACK 时面板已收起 → 走 super 退出播放。
        // 修复=面板态不再自动隐藏（与 T-140「选台页不自动收起面板」语义一致），BACK 时面板可见 → 收面板回播放。
        App.removeCallbacks(mR4);
        hideEpg();
        // T-154（拍板 50，刘先生 beta7 反馈一）：面板呼出后焦点落分组行——左右切分组、下键进频道网格；
        // 原实现未请求焦点，焦点留在播放层导致面板"不可选"、只能顺序换台
        if (mFirstPanelFocus) {
            // β19 / T-314①：进场首帧＝焦点停在 nav_live（与点播页一致），判据靠这条日志＋ uiautomator 焦点核验证；
            // TopNavView.requestFocusItem 抢不到焦点时回 false，本页**不因此吃键**（C1 同口径），照常交给系统。
            mFirstPanelFocus = false;
            boolean stay = mBinding.navBar.topNav.requestFocusItem(R.id.nav_live);
            Log.d("FireFlyLive", "live entry focus stayOnNav=" + stay + " mode=down -> panel on DOWN");
            if (stay) return;
        }
        mBinding.group.post(() -> mBinding.group.requestFocus());
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            mLive.nextChannel();
        }

        @Override
        public void onPrev() {
            mLive.prevChannel();
        }

        @Override
        public void onStop() {
            finish();
        }
    };

    @Override
    protected void onPrepare() {
        setPlaybackMode();
    }

    @Override
    protected void onDecodeChanged() {
        setPlaybackMode();
    }

    @Override
    protected void onTracksChanged() {
        setTrackVisible();
    }

    @Override
    protected void onError(String msg) {
        mLive.playbackError(msg);
    }

    @Override
    protected void onReclaim() {
        mLive.refresh();
    }

    @Override
    protected void onStateChanged(int state) {
        switch (state) {
            case Player.STATE_BUFFERING:
                showProgress();
                break;
            case Player.STATE_READY:
                hideProgress();
                player().reset();
                break;
            case Player.STATE_ENDED:
                mLive.playbackEnded();
                updatePlayControl(false);
                break;
        }
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (isPlaying || isPaused()) updatePlayControl(isPlaying);
    }

    private void updatePlayControl(boolean isPlaying) {
        mBinding.control.action.action.setText(isPlaying ? R.string.pause : R.string.play);
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        mBinding.widget.size.setText(player().getSizeText());
    }

    @Override
    public void showEpg(Channel item) {
        if (mChannel == null || mChannel.getData(mViewModel.getZoneId()).getList().isEmpty() || mEpgDataAdapter.getItemCount() == 0 || !mChannel.equals(item) || !mChannel.getGroup().equals(mGroup)) return;
        mBinding.epgData.setSelectedPosition(mChannel.getData(mViewModel.getZoneId()).getSelected());
        mBinding.epgData.setVisibility(View.VISIBLE);
        mBinding.channel.setVisibility(View.GONE);
        mBinding.group.setVisibility(View.GONE);
        mBinding.epgData.requestFocus();
    }

    @Override
    public void hideEpg() {
        mBinding.channel.setVisibility(View.VISIBLE);
        mBinding.group.setVisibility(View.VISIBLE);
        mBinding.epgData.setVisibility(View.GONE);
        mBinding.channel.requestFocus();
    }

    @Override
    public void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR2, 0);
        hideCenter();
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset();
    }

    private void showError(String text) {
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showControl(View view) {
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        mBinding.widget.top.setVisibility(View.VISIBLE);
        // β45 / 缺陷3（照 β69 在 VideoActivity 的同一手法，那条 A 当时批准过）：原先这里只有
        // `App.post(view::requestFocus, 25)` 一跳，且**没人读返回值** ⇒ 控制层刚显形、视图还没就绪时
        // requestFocus 返回 false 就静默失败，焦点留在 video 上，用户「呼出控制层→UP→左右步进」
        // 这条路在**第一跳**就断了（C 真机 `seekFocus=false` 与此吻合）。
        // 现在：先直调（就绪时零延迟感），失败才延 25ms 重试一次；两跳各留痕，真机一眼能看出断在哪一跳。
        boolean attempt = view.requestFocus();
        Log.d("FireFlyLive", "live ctrl focus attempt result=" + attempt + " view=" + view.getId()
                + " seekFocus=" + mBinding.control.seek.hasFocus());
        if (!attempt) {
            App.post(() -> Log.d("FireFlyLive", "live ctrl focus retry result=" + view.requestFocus()
                    + " seekFocus=" + mBinding.control.seek.hasFocus()), 25);
        }
        setR1Callback();
        hideInfo();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        mBinding.widget.top.setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        mBinding.widget.center.setVisibility(View.GONE);
    }

    private void showInfo() {
        mBinding.widget.bottom.setVisibility(View.VISIBLE);
        setR3Callback();
        setInfo();
    }

    private void hideInfo() {
        mBinding.widget.bottom.setVisibility(View.GONE);
        App.removeCallbacks(mR3);
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.progress.traffic);
        App.post(mR2, 1000);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR3Callback() {
        App.post(mR3, Constant.INTERVAL_HIDE);
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else if (isVisible(mBinding.recycler)) hideUI();
        else showUI();
        hideInfo();
    }

    private void resetPass() {
        this.count = 0;
    }

    private void setArtwork() {
        ImgUtil.load(this, mChannel.getLogo(), new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                mBinding.player.setDefaultArtwork(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                mBinding.player.setDefaultArtwork(errorDrawable);
            }
        });
    }

    @Override
    public void onItemClick(Group item) {
        mLive.selectGroup(item);
        if (!item.isKeep() || ++count < 5 || mHides.isEmpty()) return;
        PassDialog.create().show(this);
        App.removeCallbacks(mR4);
        resetPass();
    }

    @Override
    public void onItemClick(Channel item) {
        if (mPreviewed && mChannel != null && mChannel.equals(item)) {
            // 预览已切到该台：OK=确认选台，收面板回播放（spec §3.5）
            hideUI();
        } else if (!item.getData(mViewModel.getZoneId()).getList().isEmpty() && item.isSelected() && mChannel != null && mChannel.equals(item) && mChannel.getGroup().equals(mGroup)) {
            showEpg(item);
        } else if (mGroup != null) {
            mGroup.setPosition(mBinding.channel.getSelectedPosition());
            mLive.selectChannel(item.group(mGroup));
            hideUI();
        }
    }

    // ================= 直播预览（spec §3.5/§3.6：预览=后台换台，设置可关） =================

    private void onChannelFocused(Channel item) {
        if (!isVisible(mBinding.recycler) || !LiveSetting.isPreview() || item == null) return;
        if (mChannel != null && mChannel.equals(item)) return;
        App.removeCallbacks(mR5);
        mPreviewTarget = item;
        App.post(mR5, 800); // 焦点停留 800ms 才起预览，防快速滑动雪崩（QA-5）
    }

    private void applyPreview() {
        if (mPreviewTarget == null || mGroup == null || !isVisible(mBinding.recycler)) return;
        // T-175（A 口径确认：原预览实际调用 mLive.selectChannel = 真实换台（打断当前播放、setKeep 改记录）；
        // 按"仅预览/高亮"设计修正——播放台不变，正式换台只由 OK 触发（onItemClick → selectChannel + hideUI））
        mPreviewed = true;
        mPreviewTarget.group(mGroup); // 仅关联分组引用（不触发播放）
    }

    @Override
    public boolean onLongClick(Channel item) {
        if (mGroup.isHidden()) return false;
        boolean exist = Keep.exist(item.getName());
        Notify.show(exist ? R.string.keep_del : R.string.keep_add);
        if (exist) delKeep(item);
        else addKeep(item);
        return true;
    }

    @Override
    public void onItemClick(EpgData item) {
        mLive.selectEpg(item, player().getPosition());
    }

    private void addKeep(Channel item) {
        getKeep().add(item);
        Keep keep = new Keep();
        keep.setKey(item.getName());
        keep.setType(1);
        keep.save();
    }

    private void delKeep(Channel item) {
        if (mGroup.isKeep()) mChannelAdapter.remove(item);
        if (mChannelAdapter.getItemCount() == 0) mBinding.group.requestFocus();
        getKeep().getChannel().remove(item);
        Keep.delete(item.getName());
    }

    private void setInfo() {
        mViewModel.getEpg(mChannel);
        mBinding.widget.play.setText("");
        mBinding.widget.name.setMaxEms(48);
        mChannel.loadLogo(mBinding.widget.logo);
        mBinding.widget.line.setText(mChannel.getLine());
        mBinding.widget.name.setText(mChannel.getShow());
        mBinding.widget.number.setText(mChannel.getNumber());
        mBinding.control.action.line.setText(mChannel.getLine());
        mBinding.widget.line.setVisibility(mChannel.getLineVisible());
        mBinding.control.action.line.setVisibility(mChannel.getLineVisible());
    }

    private void onEpgLoaded(Epg epg) {
        if (mChannel == null || !mChannel.getTvgId().equals(epg.getKey())) return;
        EpgData data = epg.getEpgData();
        boolean hasTitle = !data.getTitle().isEmpty();
        mEpgDataAdapter.addAll(epg.getList());
        mBinding.widget.name.setMaxEms(hasTitle ? 12 : 48);
        mBinding.widget.play.setText(data.format());
        mLive.onEpgChanged(data);
        setWidth(epg);
    }

    private void onXmlParsed(boolean success) {
        if (mChannel != null && success) mViewModel.getEpg(mChannel);
    }

    private void stopPlayer() {
        player().clear();
        player().stop();
    }

    @Override
    public int getGroupCount() {
        return mGroupAdapter.getItemCount();
    }

    @Override
    public int getGroupPosition() {
        return mBinding.group.getSelectedPosition();
    }

    @Override
    public Group getGroup(int position) {
        return mGroupAdapter.get(position);
    }

    @Override
    public boolean isPlayerLive() {
        return player().isLive();
    }

    @Override
    public boolean hasPlaybackSession() {
        return mPlaybackKey != null && service() != null && isOwner() && player().hasPlaySpec();
    }

    @Override
    public boolean isPlaybackServiceReady() {
        return service() != null;
    }

    @Override
    public void restorePlaybackKey(@Nullable String key) {
        if (key == null || !player().hasPlaySpec() || !key.equals(player().getKey())) return;
        updateNavigationKey(mPlaybackKey = key);
    }

    @Override
    public long getPlayerPosition() {
        return player().getPosition();
    }

    @Override
    public ZoneId getZoneId() {
        return mViewModel.getZoneId();
    }

    @Override
    public void requestUrl(LivePlayRequest request) {
        if (request.isCatchup()) hideUI();
        mViewModel.getUrl(request);
    }

    @Override
    public void stopPlaybackForRefresh() {
        stopPlayer();
    }

    @Override
    public void startPlayback(Result result, long position, MediaMetadata metadata) {
        startPlayer(mPlaybackKey = result.getRealUrl(), result, false, getHome().getTimeout(), position, metadata);
    }

    @Override
    public void resetPlaybackForError(String msg) {
        PlaybackReset.afterError(player());
        showError(msg);
    }

    @Override
    public void renderGroupSelection(Group group) {
        mGroup = group;
        mBinding.group.setSelectedPosition(mGroupAdapter.indexOf(group));
    }

    @Override
    public void renderGroupChannels(Group group) {
        mChannelAdapter.addAll(setWidth(group).getChannel());
        mBinding.channel.setSelectedPosition(Math.max(group.getPosition(), 0));
    }

    @Override
    public void renderChannelSelection(Channel channel) {
        App.post(mR0, 100);
        mChannel = channel;
        setArtwork();
        showInfo();
    }

    @Override
    public void renderLineSelection(Channel channel, boolean show) {
        if (show) showInfo();
        else setInfo();
    }

    @Override
    public void renderEpgSelection(EpgData data) {
        setSelected(data);
    }

    @Override
    public void renderPlaybackMetadata(MediaMetadata metadata) {
        if (service() != null && isOwner()) player().setMetadata(metadata);
        mBinding.widget.title.setText(metadata.displayTitle);
        mBinding.widget.title.setSelected(true);
    }

    @Override
    public void showCatchupReady(EpgData data) {
        Notify.show(getString(R.string.play_ready, data.getTitle()));
    }

    private void resetAdapter() {
        mBinding.control.action.line.setVisibility(View.GONE);
        mBinding.widget.title.setText("");
        mEpgDataAdapter.clear();
        mChannelAdapter.clear();
        mGroupAdapter.clear();
        mHides.clear();
        mChannel = null;
        mGroup = null;
    }

    @Override
    public void setConfig(Config config) {
        Config current = LiveConfig.get().getConfig();
        LiveConfig.load(config, getCallback(current));
    }

    private Callback getCallback(Config config) {
        return new Callback() {
            @Override
            public void start() {
                showProgress();
            }

            @Override
            public void success() {
                setLive(getHome());
            }

            @Override
            public void error(String msg) {
                LiveConfig.load(config, new Callback());
                Notify.show(msg);
                hideProgress();
            }
        };
    }

    @Override
    public void setLive(Live item) {
        if (item.isSelected()) item.getGroups().clear();
        LiveConfig.get().setHome(item);
        player().reset();
        player().clear();
        player().stop();
        resetAdapter();
        hideControl();
        mLive.reset();
        getLive();
    }

    @Override
    public void setPass(String pass) {
        unlock(pass);
    }

    private void unlock(String pass) {
        boolean first = true;
        int position = mGroupAdapter.getItemCount();
        Iterator<Group> iterator = mHides.iterator();
        while (iterator.hasNext()) {
            Group item = iterator.next();
            if (pass != null && !pass.equals(item.getPass())) continue;
            mGroupAdapter.add(mGroupAdapter.getItemCount(), item);
            if (first) mBinding.group.setSelectedPosition(position);
            if (first) onItemClick(mGroup = item);
            iterator.remove();
            first = false;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case LIVE -> setLive(getHome());
            case PLAYER -> mLive.refresh();
        }
    }

    private void setTrackVisible() {
        PlaybackAction.setTracks(player(), mBinding.control.action.text, mBinding.control.action.audio, mBinding.control.action.video, mBinding.control.action.speed);
    }

    private void prevChannel() {
        mLive.prevChannel();
    }

    private void nextChannel() {
        mLive.nextChannel();
    }

    private void prevLine() {
        mLive.prevLine();
    }

    private void nextLine(boolean show) {
        mLive.nextLine(show);
    }

    private void onPaused() {
        controller().pause();
    }

    private void onPlay() {
        controller().play();
    }

    private View getFocus2() {
        // β61（A 拍“只改回退值为第一个，保留记住上次”）：首次呼出（mFocus2 仍为 null）落【第一个可见项 home】，
        // 原先写死 action.config=「换源」，而它是本排可见项的最后一个（view_control_live_action.xml:105）。⚠ “记住上次”这条仍在：
        // 用户停在某项再按 MENU，下一次会回到那一项（:1309 记 mFocus2），这不是回归，是有意保留的体验。
        return mFocus2 == null || mFocus2.getVisibility() != View.VISIBLE ? mBinding.control.action.home : mFocus2;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // T-168/T-171 诊断日志（C beta14 两轮未生效，先自证按键路径）：记录 dispatch 到达、面板/控制层可见性
        // T-175 补充 focus=<当前焦点id>：C 验证网格内焦点是否随方向键移动（focusSearch 自证）
        Log.d("FireFlyLive", "dispatchKey k=" + event.getKeyCode() + " a=" + event.getAction()
                + " panelVis=" + isVisible(mBinding.recycler) + " recVis=" + mBinding.recycler.getVisibility()
                + " ctrlVis=" + isVisible(mBinding.control.getRoot())
                + " focus=" + (getCurrentFocus() == null ? "null" : getCurrentFocus().getId()));
        // β4 / T-208：探针记账（必须在其它分支移动焦点之前）；沉浸态 video 的 UP 由后面的前置条件排除，
        // 不会与 CustomKeyDownLive 的"上一台"抢键。
        mOverlay.onKeyDown(event, getCurrentFocus());
        // T-193-b（Round 41，C beta25 实测 k=82 到达但"设置面板打不开"）：MENU 原先只有
        // CustomKeyDownLive.check() 里那一条 `isMenuKey → onMenu()`，而它前面还有一道 `dispatch(true)` 门
        // （面板可见时 recGone=false ⇒ 返回 false ⇒ check 不执行）⇒ 面板态 MENU 完全失效。现由本页直接处理并吃掉。
        if (com.firefly.tv.utils.KeyUtil.isMenuKey(event)) {
            onMenu();
            return true;
        }
        // β19 / T-314①：进场后焦点停在导航「直播」项 ⇒ 第一把 DOWN 就是"下翻进入分类菜单"（与点播页同形）。
        // 面板本来就可见时（T-154 之后不再自动收起）showUI 不会被重新调用，所以这里显式把焦点交给分组行，
        // 并把这次 DOWN 吃掉——它是被本页正常消费的"进菜单"，不存在抢焦失败。
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN
                && mBinding.navBar.topNav.isNavItem(getCurrentFocus())) {
            boolean moved = mBinding.group.requestFocus();
            Log.d("FireFlyLive", "live DOWN from=nav -> group moved=" + moved);
            if (moved) return true;
        }
        // T-188-b（Round 41，C 实测直播页 UP×2 落 nav_search 而非 nav_live）：原先的交接分支带
        // `isVisible(mBinding.recycler)` 前置，面板被 5s 计时收起后即使焦点还在分组行也不进 reveal，
        // UP 交给系统焦点搜索、命中导航条首项（那是 nav_search）。现只按"焦点在哪"判定，不再看面板显隐。
        // β4 / T-208：判定换成共享探针（与全站同一条），但保留本页两条**前置排除**——
        // ①沉浸态焦点在 video 上时 UP=上一台（CustomKeyDownLive，:1188 那条），绝不能被"交回导航"劫持；
        // ②面板可见且焦点在频道卡上时，UP 是页内层间移动（T-176/T-177：顶行→分组行、非顶行→上移一行，
        //   由下面的面板分支处理），探针不能抢这一步。
        // 其余（焦点在分组行、或面板已计时收起而焦点仍在网格/卡片上）走探针：
        // 顶到边或被系统抢到顶栏别处 ⇒ 落 nav_live（T-188-b 语义不变，且不再有"落 nav_search"的路径）。
        // T-205 的可见性恢复必须仍在 requestFocus 之前（GONE 的顶栏拿不到焦点）。
        View upFrom = mOverlay.probeFocus();
        boolean immersiveVideo = upFrom == mBinding.video;
        boolean panelRoutesInside = isVisible(mBinding.recycler)
                && upFrom != null && mBinding.channel.findContainingItemView(upFrom) != null;
        // β19 / T-314②（规范 R3 第二批，同首页 HomeActivity:751 那条口）：UP 的落点在这把键的 **ACTION_DOWN**
        // 阶段一次定死 ⇒ 不再出现刘先生说的"先落在其他项再跳回直播项"（旧结构是 DOWN 放行给系统几何搜索、
        // 抬起由探针补抢，两段各产生一次移动）。**两条前置排除必须照用**（沉浸态 video 的 UP＝上一台、
        // 面板内频道卡的 UP＝页内层间移动），所以放在 skip 判定之后；顶栏先恢复可见（T-205 同一条约束）。
        boolean skip = immersiveVideo || panelRoutesInside;
        // β46 / 新2（A 派单，刘先生实机「直播全屏按 MENU 呼出设置菜单时，导航菜单会一并呼出而且不消失」）：
        // 下面这段"被动显形"对**任意非 skip 的键**都把顶栏置回 VISIBLE，MENU 的 ACTION_DOWN 也走它
        // （`isMenuKey` 只在 ACTION_UP 为真，见 KeyUtil:43-44，所以下半拍不在 :1161 返回）⇒ 顶栏在 MENU
        // 按下的那一拍就被带出来了。A 的口径是**区分两种呼出**：用户主动 UP 呼导航（保留，含 :1228 那条），
        // MENU 只开控制条（不得牵连导航）。这里只把 MENU 这一把键排除出显形分支，其余键行为一字未动。
        // ⚠ β62 连带说明：上面描述的「被动显形」通路本版已整段取消（见下面 β62 那条注释），MENU 与导航的区分现在只剩下面这条留痕日志。
        // β62 /（A 拍：「两案都不选——刘先生要的是上下键都不露，目标＝直播全屏态导航栏恒 GONE」）：
        // 事实：被动显形的键集 passiveKey 就是 UP/DOWN＝换台两把键，而刘先生的口径是这两把键**都不该**露顶栏
        // ⇒ 这条通路没有剩下的键可服务，整段取消（不是只摘 isUpKey，那样 LEFT/RIGHT/OK/数字会退回「任意键显形」＝β61 之前的老问题）。
        // ⚠ 有意保留的：:1249 起探针主动显形 UP_REVEAL 一条不删——它服务的是**选台面板态**「分组行 UP → 导航直播项」
        //   （β45/T-188-b，L2 验过）。全屏态下它本就因 skip=true 进不去（UP 在 :1187 武装、:1188 immersiveVideo、:1195 skip）。
        // ⚠ 代价（照实写）：β85 真机验过的「全屏 DOWN 换台顶栏露一次＋5 秒收回」本版起不再出现＝刘先生命令的直接结果，属预期回退。
        boolean ctrlUp = isVisible(mBinding.control.getRoot());
        boolean navKey = com.firefly.tv.utils.KeyUtil.isUpKey(event) || com.firefly.tv.utils.KeyUtil.isDownKey(event);
        // β62 /（A 定乙）：**与 skip 并列的第二道独立守卫**——选台面板开着，**或**焦点还在本页分组行／频道卡上，UP 才允许把焦点交给导航。
        // 为什么不能只看面板显隐（A 定乙的第一条理由，证据是本文件 :1177-1179 的注释原文）：T-188-b 当年正是**去掉** isVisible(mBinding.recycler)
        // 这个前置才修好「面板被 5s 计时收起后焦点还在分组行，按 UP 落 nav_search 而非 nav_live」；守卫若只认面板可见，就会把那条已验收的修复踩回缺陷。
        // 目标「直播全屏态导航栏恒 GONE」现在有**两道独立防线**，**任意一道单独成立就够挡**（A 三点名要写清这句），将来谁改其中一条，另一条仍在：
        //   第一道 skip（immersiveVideo／panelRoutesInside）⇒ verdict=UP_NONE；第二道 !navAllowed ⇒ verdict=UP_NONE。
        // 保留 A 二批②要留进台账的那句：分组行 UP → nav_live 是 β45/T-188-b 的既定语义，乙方案的核心保护点就是这一档。
        // 为什么**三种焦点归属都算「还在本页网格内」**（A 一批点名要写理由，不是只列结论）：分组行是 CustomLiveGridView（leanback 网格），
        // UP 那一刻焦点可能停在 ①网格容器本身（本页有六处直接 group.requestFocus()）、②网格里的分组项（adapter_group 子 view，findContainingItemView 才认得）、
        // ③频道卡（channel 的子 view）。三者都属于「用户还站在本页上按上翻」，都是 β45 已验收的 T-188-b 语义要覆盖的现场；
        // 少列任一种就会把其中一档误判成全屏态、挡回系统焦点搜索。多算无害：navAllowed=true 只是**允许探针去判** UP_REVEAL，不是强制显形，
        // 而真正的沉浸态（焦点在 video）早被第一道 skip 挡在外面——这就是 A 一批里那句「不会把顶栏带出来」的根据。
        boolean navAllowed = isVisible(mBinding.recycler)
                || (upFrom != null && (upFrom == mBinding.group
                        || mBinding.group.findContainingItemView(upFrom) != null
                        || mBinding.channel.findContainingItemView(upFrom) != null));
        // β45 / 缺陷3 那条诊断日志原先在本块内部（只在“显形”时才打）。收窄后“UP＋控制层可见”根本进不来，
        // 那条判据会消失 ⇒ 移到块外无条件打，可观测性一字不减（它零行为）。
        // ⚠ 该行的第三个字段 revealOnDown= 本版起**不再打**：它的值是 mOverlay.revealOnDown() 的返回值，
        // 而那个方法**会真的把焦点改派到导航项**（有副作用）。控制层可见时按 UP 现在不该再改派（那正是刘先生报的那一半），
        // 为了凑一个日志字段去重复调用它＝新缺陷。 ⇒ 这是本轮唯一被删掉的既有判据串字段，交付件里单列给 A。
        if (!skip && com.firefly.tv.utils.KeyUtil.isUpKey(event) && ctrlUp) {
            View upRouteFocus = getCurrentFocus();
            Log.d("FireFlyLive", "UP route ctrlVis=true focus="
                    + (upRouteFocus == null ? "null" : upRouteFocus.getId())
                    + " seekFocus=" + mBinding.control.seek.hasFocus());
        }
        if (skip && navKey) {
            // β62（A 三.1 必做）：skip 把换台键挡掉时**必须正面留痕**，否则「顶栏没出来」只能靠零行推断＝否定判据。
            // 与 :1219 那条 UP route 日志同样零行为，只观测。
            Log.d("FireFlyLive", "nav reveal skip reason=" + (immersiveVideo ? "immersiveVideo" : "panelRoutesInside")
                    + " key=" + event.getKeyCode() + " a=" + event.getAction()
                    + " ctrlVis=" + ctrlUp + " navVis=" + mBinding.navBar.getRoot().getVisibility());
        } else if (!skip && event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            // β46 / 新2 留痕（C 的判据，日志三行字面一字未改）：MENU 不改顶栏可见性，全屏按 MENU 后这条必出且顶栏仍 GONE。
            // β62 纠正（B 自查发现、**清单外单列**）：β85 把条件从「else 无条件」改成 KeyUtil.isMenuKey(event)，而该方法要求 ACTION_UP
            // （KeyUtil.java:44），MENU 的抬起半拍已在 :1164 被本页 onMenu() 直接 return 掉 ⇒ 这条自 beta85 起永不输出。
            // 证据：beta73/77/78 真机原文打的是 a=0（DOWN 半拍），与 isMenuKey 只能为 UP 矛盾。现改回按 keyCode 判，两半拍都真＝与 β46 同期行为一致。
            Log.d("FireFlyLive", "nav not revealed by MENU a=" + event.getAction()
                    + " navVis=" + mBinding.navBar.getRoot().getVisibility()
                    + " ctrlVis=" + isVisible(mBinding.control.getRoot()));
        } else if (!skip) {
            // β61 新增可观测态：被收窄挡掉、又不是 MENU 的键必须留痕（本版起全屏态四向键之外的键也走这条）。
            Log.d("FireFlyLive", "nav reveal suppressed key=" + event.getKeyCode() + " a=" + event.getAction()
                    + " navAllowed=" + navAllowed + " skip=" + skip + " immersive=" + immersiveVideo
                    + " ctrlVis=" + ctrlUp + " navVis=" + mBinding.navBar.getRoot().getVisibility());
        }
        // β62（A 补那行时追加的要求：乙案保的那一档**必须有正面日志串**，否则 C 只能靠「焦点看起来对了」判＝拿没有反证当证据）。
        // 这一档的定义：面板已收起（navAllowed 只能靠第二项成立）且焦点仍在本页分组行／频道卡上，此时 UP 应当被放行给探针。
        if (!isVisible(mBinding.recycler) && navAllowed && com.firefly.tv.utils.KeyUtil.isUpKey(event)) {
            Log.d("FireFlyLive", "nav allowed panelGone gridFocus upFrom="
                    + (upFrom == null ? "null" : upFrom.getId()) + " a=" + event.getAction());
        }
        // β62：verdict 的闸门由「skip」一条变两条——skip（沉浸态/面板内页内移动）**或** 面板未开（全屏态）都不进探针。
        int verdict = (skip || !navAllowed)
                ? NavOverlay.UP_NONE : mOverlay.probeUp(event, getCurrentFocus());
        if (verdict == NavOverlay.UP_EAT) return true;
        if (verdict == NavOverlay.UP_REVEAL) {
            Log.d("FireFlyLive", "UP -> nav reveal (probe)");
            mBinding.navBar.getRoot().setVisibility(View.VISIBLE);
            // β45 / C：同上——这条是探针把 UP 主动交回导航（用户呼出菜单），不自动收回。
            // 若也要它超时收起，属于新语义（且焦点正在顶栏上，收起会清焦），需 A/刘先生另行点头，本轮未做。
            App.removeCallbacks(mR4);
            return mOverlay.revealToPage();
        }
        // T-171（C beta14 三次 OK 面板一直显示——根因：面板可见时 dispatch(true)=false 使 OK 进不了 check/onKeyCenter）：
        // 面板态 OK 在 Activity 层直接处理：焦点不在频道卡上 → toggle 收起；焦点在卡上 → 放行给焦点系统换台（卡点击自动收起）
        if (isVisible(mBinding.recycler) && com.firefly.tv.utils.KeyUtil.isEnterKey(event) && com.firefly.tv.utils.KeyUtil.isActionUp(event)) {
            View focus = getCurrentFocus();
            boolean onCard = focus != null && mBinding.channel.findContainingItemView(focus) != null;
            Log.d("FireFlyLive", "panel OK focusOnCard=" + onCard + " -> " + (onCard ? "pass(换台)" : "hideUI(收起)"));
            if (!onCard) {
                hideUI();
                return true;
            }
        }
        // T-176/T-177（C beta18/beta19：网格顶行 UP 不回分组行；非顶行 UP 不上移/跳行）：
        // Grid 的 focusSearch(UP) 在行内不交父容器 → 统一在 Activity 层处理：
        // 顶行（pos<cols）→ 聚焦分组行；非顶行 → 手动上移一行（scrollToPosition + post 聚焦目标卡）
        // β15 / T-222甲（A 裁定 2026-10-29）：**整段从 ACTION_UP 挪到 ACTION_DOWN，抬起不再动**（两把杠杆里选低风险的那把）。
        // 双动根因（已坐实）：原条件要求 `isActionUp` ⇒ 这把 UP 的 **DOWN 不满足条件**，一路落到下面 `super.dispatchKeyEvent`
        //   ⇒ 网格按默认焦点搜索**先上移一行**；抬起再在本分支 `pos - cols` **手动再移一行** ⇒ 一次键＝净两行。
        // 现在移动在 DOWN 就地完成并 `return true`（系统不再有那次机会），抬起由紧随其后的 `mPanelUpArmed` 分支吃掉 ⇒ 净一行。
        // 照旧保留的两条约定：①「面板态方向键只导航」——判定仍是 `isVisible(recycler)` ∧ `isUpKey` ∧ 焦点在频道卡上（findContainingViewHolder），
        //   沉浸态（焦点在 video、面板不可见）完全不进这里，UP 仍是 CustomKeyDownLive 的"上一台"；
        // ②**长按护栏**——`repeat>0` 的重复 DOWN **不**手动移，交回网格原生滚动（与改前长按一致），
        //   但抬起一律吃掉：改前抬起也必定 `return true`，绝不会漏进下面的 `mKeyDown.onKeyDown`，这条不能放开。
        if (isVisible(mBinding.recycler) && com.firefly.tv.utils.KeyUtil.isUpKey(event) && com.firefly.tv.utils.KeyUtil.isActionDown(event)) {
            View focus = getCurrentFocus();
            androidx.recyclerview.widget.RecyclerView.ViewHolder vh = focus == null ? null : mBinding.channel.findContainingViewHolder(focus);
            if (vh != null) {
                mPanelUpArmed = true;
                if (event.getRepeatCount() == 0) {
                    // T-177：改用绝对位置（getAdapterPosition 在复用/滚动后可能错位——C 实测第二次 UP 读到 1）
                    int pos = vh.getAbsoluteAdapterPosition();
                    int cols = 5; // 与 activity_live.xml channel 的 app:numberOfColumns=5 保持一致
                    boolean topRow = pos >= 0 && pos < cols;
                    int target = topRow ? -1 : pos - cols;
                    Log.d("FireFlyLive", "panel UP pos=" + pos + " cols=" + cols + " -> " + (topRow ? "group.requestFocus" : "up-" + target) + " on=down");
                    if (topRow) {
                        mBinding.group.requestFocus();
                        return true;
                    }
                    if (target >= 0) {
                        mBinding.channel.scrollToPosition(target);
                        final int t = target;
                        mBinding.channel.post(() -> {
                            androidx.recyclerview.widget.RecyclerView.ViewHolder tvh = mBinding.channel.findViewHolderForAdapterPosition(t);
                            Log.d("FireFlyLive", "panel UP target=" + t + " holder=" + (tvh != null)
                                    + " focus=" + (getCurrentFocus() == null ? "null" : getCurrentFocus().getId()));
                            if (tvh != null) tvh.itemView.requestFocus();
                            else mBinding.channel.requestFocus();
                        });
                        return true;
                    }
                }
            }
        }
        // β15 / T-222甲：同一把 UP 的**抬起一律吃掉**（抬起不再产生第二次移动，也不放行给 CustomKeyDownLive）。
        // C 的判据口径：一次按键序列里 `panel UP pos=` **只出现一条**，且相邻两次按键的 pos 差＝**5**（一行＝5 格）。
        if (mPanelUpArmed && com.firefly.tv.utils.KeyUtil.isUpKey(event) && com.firefly.tv.utils.KeyUtil.isActionUp(event)) {
            mPanelUpArmed = false;
            Log.d("FireFlyLive", "panel up key eaten on=up focus=" + (getCurrentFocus() == null ? "null" : getCurrentFocus().getId()));
            return true;
        }
        // 面板内其它键（LEFT/RIGHT/DOWN/OK/MENU…）的 DOWN 作废这次等待，避免标记泄漏到下一把 UP 上误吃键。
        if (com.firefly.tv.utils.KeyUtil.isActionDown(event) && !com.firefly.tv.utils.KeyUtil.isUpKey(event)) {
            mPanelUpArmed = false;
        }
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (isVisible(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();
        // T-166：面板态不自动隐藏（原 T-154 加的 setUITimer 每次按键刷新 5s 计时，导致 BACK 前面板被收起）
        if (mKeyDown.hasEvent(event) && service() != null) mKeyDown.onKeyDown(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void setUITimer() {
        App.post(mR4, Constant.INTERVAL_HIDE);
    }

    @Override
    public boolean dispatch(boolean check) {
        // T-168 诊断：check 保护是否放行（面板可见时应返回 false → check 不执行 → 方向键交给面板）
        boolean result = !check || isGone(mBinding.recycler) && isGone(mBinding.control.getRoot());
        Log.d("FireFlyLive", "dispatch check=" + check + " -> " + result
                + " recGone=" + isGone(mBinding.recycler) + " ctrlGone=" + isGone(mBinding.control.getRoot()));
        return result;
    }

    @Override
    public void onShow(String number) {
        mBinding.widget.digital.setText(number);
        mBinding.widget.digital.setVisibility(View.VISIBLE);
    }

    @Override
    public void onFind(String number) {
        mBinding.widget.digital.setVisibility(View.GONE);
        setPosition(LiveConfig.get().findByChannelNumber(number, mGroupAdapter.unmodifiableList()));
    }

    /**
     * β43 / A3（刘先生拍板：直播页左右键统一换线路、**不要快进快退**）：本方法保留只为满足
     * {@code CustomKeyDownLive.Listener} 接口；按键解释器里 {@code DOWN+LEFT/RIGHT → onSeeking}
     * 那两条绑定已一并去掉，这里再守一道直接返回，免得日后有人重新接线又把中心提示带回来。
     * 回看态的快进改由进度条自己承担（呼出控制层 → 焦点上推到进度条 → 左右键步进），见交付说明。
     */
    @Override
    public void onSeeking(long time) {
    }

    @Override
    public void onKeyUp() {
        Log.d("FireFlyLive", "onKeyUp panelVis=" + isVisible(mBinding.recycler) + " recVis=" + mBinding.recycler.getVisibility());
        // T-168（C beta13：面板态方向键触发换台并把面板关掉）：面板可见时方向键交给面板内焦点系统，不换台
        if (isVisible(mBinding.recycler)) return;
        if (LiveSetting.isInvert()) nextChannel();
        else prevChannel();
    }

    @Override
    public void onKeyDown() {
        Log.d("FireFlyLive", "onKeyDown panelVis=" + isVisible(mBinding.recycler) + " recVis=" + mBinding.recycler.getVisibility());
        // T-170（拍板 53，刘先生 2026-10-26 晚间）：直播播放中 DOWN=下一台、UP=上一台、OK=换台面板；保留反转设置
        // T-168：面板可见时不换台（方向键由面板内焦点系统处理）
        if (isVisible(mBinding.recycler)) return;
        if (LiveSetting.isInvert()) prevChannel();
        else nextChannel();
    }

    @Override
    public void onKeyLeft(long time) {
        changeLineBySide(false);
    }

    @Override
    public void onKeyRight(long time) {
        changeLineBySide(true);
    }

    /**
     * β43 / A3（刘先生拍板）：直播页 LEFT/RIGHT **一律＝换线路**（左＝上一条、右＝下一条，循环），
     * 不再判 {@code player().isLive()}、也不再 seek——原"沉浸态非直播流 seek／直播流换台"两套分支一并去掉。
     * 留两条"仍交回原处"的守卫，只为不破坏既有语义：
     * ①换台面板可见 ⇒ 方向键继续在面板里移焦点（T-168 原样，未改）；
     * ②焦点已在进度条上 ⇒ 左右交给 media3 进度条自己步进（回看态唯一的快进路径：
     *   {@code DefaultTimeBar} 构造里 {@code setFocusable(true)}、{@code onKeyDown} 处理 DPAD_LEFT/RIGHT(21/22)
     *   → {@code scrubIncrementally}，步进值由 {@code PlaybackActivity#updateKeyIncrement} 按时长分档）。
     *   本方法本来只在"面板与控制层都不可见"时被调到（{@code dispatch()} 闸门在先），这条属双保险。
     */
    private void changeLineBySide(boolean next) {
        Log.d("FireFlyLive", "line key=" + (next ? "right" : "left") + " panelVis=" + isVisible(mBinding.recycler)
                + " seekFocus=" + mBinding.control.seek.hasFocus());
        if (isVisible(mBinding.recycler)) return;
        if (mBinding.control.seek.hasFocus()) return;
        changeLine(next);
    }

    @Override
    public void onKeyCenter() {
        Log.d("FireFlyLive", "onKeyCenter panelVis=" + isVisible(mBinding.recycler) + " recVis=" + mBinding.recycler.getVisibility());
        // T-171（C beta13：面板态再按 OK 未收起面板）：OK=toggle——面板可见→收起；隐藏→呼出
        if (isVisible(mBinding.recycler)) {
            hideUI();
            return;
        }
        hideInfo();
        showUI();
    }

    @Override
    public void onMenu() {
        // T-193-b（Round 41，C beta25 实测 k=82 到达但"设置面板打不开"）：原实现单向 showControl，
        // 再按一次 MENU 不会收起（看起来像"开了又自己关掉"），且被 CustomKeyDownLive 的 dispatch(true) 门挡住
        // ——面板可见时 recGone=false ⇒ pass=false ⇒ check() 不执行 ⇒ MENU 整个失效（C 的另一条实测）。
        // 现改为 toggle，并由 LiveActivity.dispatchKeyEvent 直接处理 MENU，不再经过那道门（见同文件 dispatch 段）。
        if (isVisible(mBinding.control.getRoot())) {
            Log.d("FireFlyLive", "onMenu -> hideControl");
            hideControl();
        } else {
            Log.d("FireFlyLive", "onMenu -> showControl panelVis=" + isVisible(mBinding.recycler));
            if (isVisible(mBinding.recycler)) hideUI();
            showControl(getFocus2());
        }
    }

    @Override
    public void onSingleTap() {
        onToggle();
    }

    @Override
    public void onDoubleTap() {
        if (isVisible(mBinding.recycler)) hideUI();
        else if (isVisible(mBinding.control.getRoot())) hideControl();
        else onMenu();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.bottom)) {
            hideInfo();
        } else if (isVisible(mBinding.recycler)) {
            hideUI();
        } else {
            if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        mClock.release();
        Source.get().exit();
        App.removeCallbacks(mR0, mR1, mR2, mR3, mR4, mR5);
        super.onDestroy();
    }
}
