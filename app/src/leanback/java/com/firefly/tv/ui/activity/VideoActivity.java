package com.firefly.tv.ui.activity;

import com.firefly.tv.ui.activity.DetailActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerSeekView;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import android.util.Log;

import com.bumptech.glide.request.transition.Transition;
import com.firefly.tv.App;
import com.firefly.tv.Constant;
import com.firefly.tv.R;
import com.firefly.tv.api.DanmakuApi;
import com.firefly.tv.api.SiteApi;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Danmaku;
import com.firefly.tv.bean.DetailLog;
import com.firefly.tv.bean.Episode;
import com.firefly.tv.bean.Flag;
import com.firefly.tv.bean.History;
import com.firefly.tv.bean.Keep;
import com.firefly.tv.bean.Parse;
import com.firefly.tv.bean.Result;
import com.firefly.tv.bean.Site;
import com.firefly.tv.bean.Sub;
import com.firefly.tv.bean.Vod;
import com.firefly.tv.databinding.ActivityVideoBinding;
import com.firefly.tv.db.AppDatabase;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.impl.CustomTarget;
import com.firefly.tv.model.VideoViewModel;
import com.firefly.tv.playback.PlaybackAction;
import com.firefly.tv.playback.PlaybackIntent;
import com.firefly.tv.playback.PlaybackReset;
import com.firefly.tv.playback.PlaybackResult;
import com.firefly.tv.playback.vod.VodDetailResult;
import com.firefly.tv.playback.vod.VodPlayRequest;
import com.firefly.tv.playback.vod.VodPlaybackController;
import com.firefly.tv.playback.vod.VodPlaybackHost;
import com.firefly.tv.playback.vod.VodPlaybackMedia;
import com.firefly.tv.player.media.PlaySpec;
import com.firefly.tv.service.PlaybackService;
import com.firefly.tv.setting.DanmakuSetting;
import com.firefly.tv.setting.PlayerSetting;
import com.firefly.tv.setting.SpeedSetting;
import com.firefly.tv.ui.adapter.ArrayAdapter;
import com.firefly.tv.ui.adapter.EpisodeAdapter;
import com.firefly.tv.ui.adapter.FlagAdapter;
import com.firefly.tv.ui.adapter.PartAdapter;
import com.firefly.tv.ui.adapter.QualityAdapter;
import com.firefly.tv.ui.adapter.QuickAdapter;
import com.firefly.tv.ui.custom.CustomKeyDownVod;
import com.firefly.tv.ui.custom.CustomMovement;
import com.firefly.tv.ui.dialog.ChapterDialog;
import com.firefly.tv.ui.dialog.ContentDialog;
import com.firefly.tv.ui.dialog.DanmakuDialog;
import com.firefly.tv.ui.dialog.EditionDialog;
import com.firefly.tv.ui.dialog.ParseDialog;
import com.firefly.tv.ui.dialog.PlayerEngineDialog;
import com.firefly.tv.ui.dialog.SpeedSettingDialog;
import com.firefly.tv.ui.dialog.TrackDialog;
import com.firefly.tv.utils.Clock;
import com.firefly.tv.utils.FileChooser;
import com.firefly.tv.utils.ImgUtil;
import com.firefly.tv.utils.KeyUtil;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.PartUtil;
import com.firefly.tv.utils.ResUtil;
import com.firefly.tv.utils.Sniffer;
import com.firefly.tv.utils.Traffic;
import com.firefly.tv.utils.UrlUtil;
import com.firefly.tv.utils.Util;
import com.github.bassaer.library.MDColor;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class VideoActivity extends PlaybackActivity implements VodPlaybackHost, CustomKeyDownVod.Listener, ParseDialog.Listener, ArrayAdapter.OnClickListener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, Clock.Callback {

    private ActivityVideoBinding mBinding;
    private VideoViewModel mViewModel;
    private VodPlaybackController mVod;
    private FlagAdapter mFlagAdapter;
    private EpisodeAdapter mEpisodeAdapter;
    private QualityAdapter mQualityAdapter;
    private ArrayAdapter mArrayAdapter;
    private PartAdapter mPartAdapter;
    private QuickAdapter mQuickAdapter;
    private ViewGroup.LayoutParams mFrameParams;
    private CustomKeyDownVod mKeyDown;
    private Clock mClock;
    private View mFocus1;
    private View mFocus2;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private History mHistory;
    private boolean fullscreen;
    private boolean useParse;
    private boolean mPauseBar; // 暂停态（规格 §3.4：OK=暂停条，下键=浮层，不暂停）
    private Vod mInfoVod; // 浮层信息卡数据源（detail 结果）

    // β8 / T-201（刘先生拍板选项 B）：BACK＝回"来的地方"，**不再补开详情页**。
    // 做法：每个入口启动时写一个 `source` 标记，只用于 BACK 留痕（`back src=… action=finish|home`），
    // 行为一律是 `finish` 自然后退（不 startActivity 重开，避免叠栈）；真无来源（`isTaskRoot()`）才回首页。
    // 原先的 4/5/6/8 参公开重载在 β4「入口直达详情页」之后已无调用者 ⇒ 随本批一并删除，路由面只剩这五条真实入口。

    public static void push(FragmentActivity activity, String text) {
        Uri uri = UrlUtil.uri(text);
        // 推送文本两支分开记：本地文件＝file，其余＝push（不再与嗅探 sniff 混记）。
        if (FileChooser.isValid(activity, uri)) file(activity, FileChooser.getPathFromUri(uri));
        else {
            String url = Sniffer.getUrl(text);
            start(activity, SiteApi.PUSH, url, url, null, null, false, false, "push");
        }
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        start(activity, SiteApi.PUSH, "file://" + path, name, null, null, false, false, "file");
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic(), null, false, true, "cast");
    }

    /**
     * 两参重载的两个真实调用点都属"嗅探/推送"类：`PushActivity:58`（TV 剪贴板）与 `extractor/Push:19`（push://）⇒ 记 sniff。
     * （`main` 源集里的 extractor 各 flavor 都编译，故这个签名保持原样不动。）
     */
    public static void start(Activity activity, String url) {
        start(activity, SiteApi.PUSH, url, url, null, null, false, false, "sniff");
    }

    /**
     * Round 38 / R2③ 建、β8 / T-201 改：详情页专用入口。
     * `fromDetail` 布尔位保留，但用途只剩 β4 的「详情点播放即全屏」判定（initView 里 `isFromDetail()`）；
     * BACK 不再读它，也不再补开详情页——栈下本来就压着那张详情页，`finish` 天然回去。
     */
    public static void startFromDetail(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, false, false, "detail");
    }

    private static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, String source) {
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("collect", collect);
        intent.putExtra("cast", cast);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        intent.putExtra("fromDetail", "detail".equals(source));
        // β8 / T-201：来源标记只用于日志，不参与任何路由判定。
        intent.putExtra("source", Objects.toString(source, "none"));
        activity.startActivity(intent);
    }

    private boolean isCast() {
        return getIntent().getBooleanExtra("cast", false);
    }

    /** Round 38 / R2③：本页是否由详情页启动（现只服务 β4 的"详情点播放即全屏"，不再服务 BACK）。 */
    private boolean isFromDetail() {
        return getIntent().getBooleanExtra("fromDetail", false);
    }

    /**
     * β8 / T-201：来源标记，取值域 {@code home|vod|search|keep|history|detail|cast|push|sniff|file|none}。
     * 实测现状（`grep -rn "VideoActivity\." app/src`）：真实入口只有 detail / cast / push / sniff / file 这五种——
     * 首页、点播、搜索(合集)、收藏、历史五类**没有直进播放的路径**，一律「来源页 → 详情页 → 播放页」，
     * 所以 BACK 第一层天然回详情页、第二层才回来源页；这五个取值当前不会出现在日志里，
     * **出现 `src=none` 才说明某个调用点漏了标记**（缺陷指示，不是正常态）。
     */
    private String getSource() {
        return Objects.toString(getIntent().getStringExtra("source"), "none");
    }

    // β8 / T-201：`backToDetail()`（Round 38 / R2③ 的"BACK 补开详情页"）**整块删除**——
    // 语义已废弃：补开的详情页会叠在真实返回栈上（投屏/本地文件 BACK 冒出一个详情页就是它的症状），
    // 且它违背"BACK 回来的地方"这条拍板口径。现在的 BACK 只剩 `finish` 自然后退（见 onBackInvoked）。

    private String getName() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getMark() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    private String getKey() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    private String getId() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    private boolean isSameVideo(Intent intent) {
        String key = Objects.toString(intent.getStringExtra("key"), "");
        String id = Objects.toString(intent.getStringExtra("id"), "");
        return TextUtils.equals(key, getKey()) && TextUtils.equals(id, getId());
    }

    @Override
    public String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private Episode getEpisode() {
        return mEpisodeAdapter.getActivated();
    }

    private int getScale() {
        return mHistory != null && mHistory.getScale() != -1 ? mHistory.getScale() : PlayerSetting.getScale();
    }

    private void setScale(int scale) {
        mVod.setScale(scale);
        mBinding.player.setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    @Override
    public boolean isFromCollect() {
        return getIntent().getBooleanExtra("collect", false);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
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
        mVod.onPlaybackServiceReady();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (TextUtils.isEmpty(intent.getStringExtra("id")) || isSameVideo(intent)) return;
        saveHistory(true);
        mVod.reset();
        setIntent(intent);
        updateNavigationKey();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        mFrameParams = mBinding.video.getLayoutParams();
        mClock = Clock.create(mBinding.widget.clock);
        mKeyDown = CustomKeyDownVod.create(this);
        mR1 = this::hideControl;
        mR2 = this::updateFocus;
        mR3 = this::setTraffic;
        mR4 = this::showEmpty;
        setRecyclerView();
        setVideoView();
        setViewModel();
        setSeekDownFocus();
        checkCast();
        // β4 / T-201（A 案）：「详情页点播放 → 直达播放页且全屏，不再停小窗」。
        // 判据是既有的 `fromDetail` 标记（startFromDetail，:184-187），走的是投屏同一条成熟路径
        // （checkCast 里 `isCast() && !isFullscreen() → enterFullscreen()`，:813-816）——
        // 此处 mFrameParams 已存（:323）、video 已可聚焦，不新增任何状态机。
        // 四类"仍直达播放"的入口（push/cast 续播/嗅探/本地文件）**不动**，它们不带 fromDetail 标记。
        if (isFromDetail() && !isFullscreen()) {
            Log.d("FireFlyPlay", "entry fullscreen fromDetail=true -> enterFullscreen");
            enterFullscreen();
        }
        checkId();
    }

    /**
     * T-189-b（Round 40）：焦点在进度条按 DOWN 必须落按钮行**首位 next（下一集）**，不能交给系统焦点搜索——
     * 按钮行十几项同处一行，搜索按"离进度条水平中心最近"取，实测会落在中间某项（刘先生所见）。
     * 首位由 A 裁定按刘先生原话定为「下一集」，Round 38 R2 的 prev 首位口径作废，
     * 行内 RIGHT/LEFT 的端点环绕同步改走 next ↔ ending（见 view_control_vod_action.xml 注释）。
     * 进度条一侧有两个可能的焦点持有者：PlayerSeekView 本体（T-167 显式 focusable，showControl 直接
     * requestFocus 它）与库内 `exo_progress`（DefaultTimeBar 自带 focusable，胶囊的 nextFocusUp 就指它），
     * 故两处都写 nextFocusDown。
     */
    private void setSeekDownFocus() {
        int first = mBinding.control.action.next.getId();
        mBinding.control.seek.setNextFocusDownId(first);
        View timeBar = mBinding.control.seek.findViewById(androidx.media3.ui.R.id.exo_progress);
        if (timeBar != null) timeBar.setNextFocusDownId(first);
        Log.d("FireFlyPlay", "seek down focus=next bound=" + (timeBar != null));
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        // T-194-b2（Round 43，C beta27 实测 ctrlFocus 全量 logcat 0 次）：上一版把日志挂在 dispatchKeyEvent 末尾，
        // 但"呼出"那一次按键走 mKeyDown 的提前 return，根本走不到末尾那行；控制层又 3 秒自动收起
        // ⇒ C 很难在"可见期间"补按一次 ⇒ 一条都没打出来。**不是被 R8 剔除**（包内 dex 有该串），是挂载点选错了。
        // 现挂到全局焦点变化上：焦点进入/移动到控制层内部就打印，与按键走哪条分支无关。
        mBinding.control.getRoot().getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus != null && isInsideControl(newFocus)) logCtrlFocus(mLastKey);
        });
        mBinding.keep.setOnClickListener(view -> onKeep());
        mBinding.video.setOnClickListener(view -> onVideo());
        mBinding.change.setOnClickListener(view -> onChange());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.ending.setUpListener(this::onEndingAdd);
        mBinding.control.action.ending.setDownListener(this::onEndingSub);
        mBinding.control.action.opening.setUpListener(this::onOpeningAdd);
        mBinding.control.action.opening.setDownListener(this::onOpeningSub);
        mBinding.control.action.next.setOnClickListener(view -> checkNext());
        mBinding.control.action.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.replay.setOnClickListener(view -> onReplay());
        mBinding.control.action.parse.setOnClickListener(view -> onParse());
        mBinding.control.action.player.setOnClickListener(view -> onPlayer());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.repeat.setOnClickListener(view -> onRepeat());
        mBinding.control.action.danmaku.setOnClickListener(view -> onDanmaku());
        mBinding.control.action.edition.setOnClickListener(view -> onEdition());
        // Round 38 / R2②（刘先生裁定）：控制栏"详情/换源"两个按钮已从按钮行整块删除，
        // 对应监听一并移除——详情与换源入口改由 BACK 回落详情页承载（见 onBackInvoked + backToDetail）。
        mBinding.control.action.chapter.setOnClickListener(view -> onChapter());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.flag.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mFlagAdapter.getItemCount() > 0) onItemClick(mFlagAdapter.get(position));
            }
        });
        mBinding.episode.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (child != null && mBinding.video != mFocus1) mFocus1 = child.itemView;
            }
        });
        mBinding.array.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mEpisodeAdapter.getItemCount() > 20 && position > 1) mBinding.episode.setSelectedPosition((position - 2) * 20);
            }
        });
    }

    private void setRecyclerView() {
        mBinding.flag.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.episode.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.episode.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this));
        mBinding.quality.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quality.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
        mBinding.array.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.array.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.array.setAdapter(mArrayAdapter = new ArrayAdapter(this));
        mBinding.part.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.part.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.part.setAdapter(mPartAdapter = new PartAdapter(item -> mVod.search(item)));
        mBinding.quick.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quick.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
    }

    private void setVideoView() {
        // Round 38 / R2①（口径补充第 1 条）：按钮行首位由「下集 next」改为「上集 prev」
        // → 进度条下键目标必须同步改到新行首位，否则"下键落第 2 项、首位被跳过"。
        setSeekNextFocusDown(R.id.prev);
        setActionFocusBoundary(mBinding.control.action.getRoot());
        PlayerEngineDialog.setText(mBinding.control.action.player);
        mBinding.control.action.danmaku.setVisibility(DanmakuSetting.isLoad() ? View.VISIBLE : View.GONE);
    }

    private void setPlaybackMode() {
        PlaybackAction.setPlaybackMode(player(), mBinding.control.action.player, mBinding.control.action.decode);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(VideoViewModel.class);
        mVod = mViewModel.createPlaybackController(this);
        observeWhenServiceReady(mViewModel.getDetail(), this::onDetailObserved);
        observeWhenServiceReady(mViewModel.getSearch(), this::onSearchObserved);
        mViewModel.getSearchFailed().observe(this, failed -> {
            if (Boolean.TRUE.equals(failed)) Notify.show(R.string.search_all_failed);
        });
        observeWhenServiceReady(mViewModel.getPreload(), this::onPreloadObserved);
        observeWhenServiceReady(mViewModel.getPlayback(), this::onPlaybackObserved);
    }

    private void onDetailObserved(VodDetailResult result) {
        if (result != null && result.result() != null && result.result().getVod() != null) {
            mInfoVod = result.result().getVod();
            // T-157 方案 C：信息卡已从浮层移除（不再调 setInfoCard）
        }
        mVod.onDetailResult(result);
    }

    private void onPlaybackObserved(PlaybackResult<VodPlayRequest> result) {
        mVod.onPlaybackResult(result);
    }

    private void onPreloadObserved(PlaybackResult<VodPlayRequest> result) {
        mVod.onPreloadResult(result);
    }

    private void onSearchObserved(Result result) {
        mVod.onSearchResult(result);
    }

    @Override
    public String getVodKey() {
        return getKey();
    }

    @Override
    public String getVodId() {
        return getId();
    }

    @Override
    public void setVodId(String id) {
        getIntent().putExtra("id", id);
    }

    @Override
    public String getVodName() {
        String name = mBinding.name.getText().toString();
        return name.isEmpty() ? getName() : name;
    }

    @Override
    public String getVodPic() {
        return getPic();
    }

    @Override
    public String getVodMark() {
        return getMark();
    }

    @Override
    public boolean isSiteChangeable() {
        return getSite().isChangeable();
    }

    @Override
    public boolean isHostFinishing() {
        return isFinishing() || isDestroyed();
    }

    @Override
    public boolean isPlayerEmpty() {
        return player().isEmpty();
    }

    @Override
    public boolean hasPlaybackSession() {
        return service() != null && isOwner() && player().hasPlaySpec();
    }

    @Override
    public boolean isFullscreenForPlayback() {
        return isFullscreen();
    }

    @Override
    public boolean isLivePlayback() {
        return service() != null && isOwner() && player().isLive();
    }

    @Override
    public boolean canTrackPlaybackProgress() {
        return service() != null && isOwner() && player().isVod();
    }

    @Override
    public boolean canPreloadNext() {
        return service() != null && isOwner() && player().canPreloadNext();
    }

    @Override
    public long getPlayerPosition() {
        return player().getPosition();
    }

    @Override
    public long getPlayerDuration() {
        return player().getDuration();
    }

    @Override
    public void usePushId(String id) {
        getIntent().putExtra("key", SiteApi.PUSH).putExtra("id", id);
    }

    @Override
    public void requestDetail(String key, String id) {
        mViewModel.detailContent(key, id);
    }

    @Override
    public void requestPlayer(VodPlayRequest request) {
        mViewModel.playerContent(request);
        showProgress();
    }

    @Override
    public void requestPreload(VodPlayRequest request) {
        mViewModel.preloadContent(request);
    }

    @Override
    public void requestSearch(List<Site> sites, String keyword) {
        mQuickAdapter.clear();
        mViewModel.searchContent(sites, keyword, true);
    }

    @Override
    public void prepareSource(Vod item) {
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getPic());
        getIntent().putExtra("id", item.getId());
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        updateNavigationKey();
        player().reset();
        player().stop();
    }

    @Override
    public void stopPlaybackForRefresh() {
        player().stop();
        player().clear();
        mClock.setCallback(null);
    }

    @Override
    public void resetPlaybackForError(String msg) {
        PlaybackReset.afterError(player(), () -> mClock.setCallback(null));
        showError(msg);
    }

    @Override
    public void replay(long position) {
        player().replay(position);
    }

    @Override
    public void startPlayback(Result result, boolean useParse, long startPositionMs, MediaMetadata metadata) {
        startPlayer(getHistoryKey(), result, useParse, getSite().getTimeout(), startPositionMs, metadata);
    }

    @Override
    public boolean preloadPlayback(Result result, long startPositionMs, MediaMetadata metadata) {
        return player().preload(PlaySpec.from(result, getHistoryKey(), metadata), startPositionMs);
    }

    @Override
    public void clearPreload() {
        if (service() != null && isOwner()) player().clearPreload();
    }

    @Override
    public void loadDanmaku(Result result, History history, Episode episode) {
        VodPlaybackMedia.searchDanmaku(result, history, episode, player()::setDanmaku, player()::addDanmaku);
    }

    @Override
    public void renderDetail(Vod item, History history) {
        mHistory = history;
        mBinding.progressLayout.showContent();
        mBinding.name.setText(item.getName());
        mBinding.video.requestFocus();
        App.removeCallbacks(mR4);
        setArtwork(item.getPic());
        checkKeepImg();
        setText(item);
        updateKeep();
    }

    @Override
    public void renderVodUpdate(Vod item) {
        if (!item.getId().isEmpty()) updateNavigationKey();
        renderVodMetadata(item.getName(), item.getPic());
        setText(item);
    }

    private void renderVodMetadata(String name, String pic) {
        if (name.isEmpty() && pic.isEmpty()) return;
        if (!name.isEmpty()) mBinding.name.setText(name);
        if (!name.isEmpty()) setPartAdapter();
        if (!pic.isEmpty()) setArtwork();
        updateKeep();
    }

    @Override
    public void renderEmptyDetail() {
        showEmpty();
    }

    @Override
    public void renderFallbackName(String name) {
        mBinding.name.setText(name);
    }

    @Override
    public void renderFlags(List<Flag> items) {
        mBinding.flag.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mFlagAdapter.addAll(items);
    }

    @Override
    public void renderEpisodes(List<Episode> items) {
        setEpisodeAdapter(items);
    }

    @Override
    public void renderFlagSelection(Flag item) {
        mBinding.flag.setSelectedPosition(mFlagAdapter.indexOf(item));
        notifyItemChanged(mBinding.flag, mFlagAdapter);
    }

    @Override
    public void renderEpisodeSelection(Episode item) {
        notifyItemChanged(mBinding.episode, mEpisodeAdapter);
        mBinding.episode.setSelectedPosition(mEpisodeAdapter.getPosition());
    }

    @Override
    public void renderReverseEpisodes(List<Episode> items, boolean scroll) {
        setEpisodeAdapter(items);
        if (scroll) mBinding.episode.setSelectedPosition(mEpisodeAdapter.getPosition());
    }

    @Override
    public void renderQuality(Result result, boolean visible) {
        mQualityAdapter.addAll(result);
        setQualityVisible(visible);
    }

    @Override
    public void renderQualityVisible(boolean visible) {
        setQualityVisible(visible);
    }

    @Override
    public void renderSources(List<Vod> items) {
        mQuickAdapter.addAll(items);
        mBinding.quick.setVisibility(mQuickAdapter.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void renderHistory(History history) {
        mHistory = history;
        mBinding.control.action.opening.setText(history.getOpening() <= 0 ? getString(R.string.play_op) : Util.timeMs(history.getOpening()));
        mBinding.control.action.ending.setText(history.getEnding() <= 0 ? getString(R.string.play_ed) : Util.timeMs(history.getEnding()));
        player().setSpeed(SpeedSetting.getPlayback());
        setScale(getScale());
        setPartAdapter();
    }

    @Override
    public void renderUseParse(boolean useParse) {
        setUseParse(useParse);
        mBinding.control.action.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void renderArtwork(String url) {
        setArtwork(url);
    }

    @Override
    public void renderDescription(String desc) {
        mBinding.content.setTag(desc);
    }

    @Override
    public void renderPlaybackMetadata(MediaMetadata metadata) {
        if (service() != null && isOwner()) player().setMetadata(metadata);
        mBinding.widget.title.setText(metadata.displayTitle);
        mBinding.widget.title.setSelected(true);
    }

    @Override
    public void onDetailFallbackScheduled() {
        App.post(mR4, 10000);
    }

    @Override
    public void onDetailFallbackCancelled() {
        App.removeCallbacks(mR4);
    }

    @Override
    public void onSearchStarted(String keyword) {
        mBinding.part.setTag(keyword);
    }

    @Override
    public void onSearchResult() {
        App.removeCallbacks(mR4);
    }

    @Override
    public void showDetailMessage(String msg) {
        Notify.show(msg);
    }

    @Override
    public void showSwitchLine(Flag flag) {
        Notify.show(getString(R.string.play_switch_flag, flag.getFlag()));
    }

    @Override
    public void showSwitchSource(Vod item) {
        Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
    }

    @Override
    public void showEpisodeReady(Episode item) {
        Notify.show(getString(R.string.play_ready, item.getName()));
    }

    @Override
    public void showNoNext(boolean reversed) {
        Notify.show(reversed ? R.string.error_play_prev : R.string.error_play_next);
    }

    @Override
    public void showNoPrev(boolean reversed) {
        Notify.show(reversed ? R.string.error_play_next : R.string.error_play_prev);
    }

    @Override
    public void finishVod() {
        finish();
    }

    private void checkCast() {
        if (isCast() && !isFullscreen()) enterFullscreen();
        else mBinding.progressLayout.showProgress();
    }

    private void checkId() {
        mVod.checkId();
    }

    private void showEmpty() {
        mBinding.progressLayout.showEmpty();
    }

    private void setText(Vod item) {
        mBinding.content.setTag(item.getContent());
        setText(mBinding.year, R.string.detail_year, item.getYear());
        setText(mBinding.area, R.string.detail_area, item.getArea());
        // 本地/推送播放（push_agent）时爬虫返回的 type_name 无意义，不展示类型；其他视频保留
        setText(mBinding.type, R.string.detail_type, com.firefly.tv.api.SiteApi.PUSH.equals(getKey()) ? "" : item.getTypeName());
        setText(mBinding.site, R.string.detail_site, getSite().getName());
        setText(mBinding.director, R.string.detail_director, item.getDirector());
        setText(mBinding.actor, R.string.detail_actor, item.getActor());
        setText(mBinding.remark, 0, item.getRemarks());
    }

    private void setText(TextView view, int resId, String text) {
        if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(view.getText())) return;
        view.setText(Sniffer.buildClickable(resId > 0 ? getString(resId, text) : text, this::clickableSpan), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        view.setLinkTextColor(MDColor.YELLOW_500);
        CustomMovement.bind(view);
    }

    private ClickableSpan clickableSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                VodActivity.start(getActivity(), getKey(), result);
                setRedirect(true);
            }
        };
    }

    @Override
    public void onItemClick(Flag item) {
        mVod.selectFlag(item);
    }

    private void setEpisodeAdapter(List<Episode> items) {
        mBinding.episode.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mEpisodeAdapter.addAll(items);
        setArrayAdapter(items.size());
        setR2Callback();
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        mVod.selectEpisode(item);
    }

    private void setQualityVisible(boolean visible) {
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        setR2Callback();
    }

    @Override
    public void onItemClick(Result result) {
        mVod.selectQuality(result);
    }

    private void setArrayAdapter(int size) {
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.play_reverse));
        items.add(getString(mHistory.getRevPlayText()));
        mBinding.array.setVisibility(size > 1 ? View.VISIBLE : View.GONE);
        if (mHistory.isRevSort()) for (int i = size; i > 0; i -= 20) items.add(i + "-" + Math.max(i - 19, 1));
        else for (int i = 0; i < size; i += 20) items.add((i + 1) + "-" + Math.min(i + 20, size));
        mArrayAdapter.addAll(items);
    }

    private int findFocusDown(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.episode, R.id.array, R.id.part, R.id.quick);
        for (int i = 0; i < orders.size(); i++) if (i > index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private int findFocusUp(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.episode, R.id.array, R.id.part, R.id.quick);
        for (int i = orders.size() - 1; i >= 0; i--) if (i < index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private void updateFocus() {
        mPartAdapter.setNextFocusUp(findFocusUp(4));
        mEpisodeAdapter.setNextFocusUp(findFocusUp(2));
        mFlagAdapter.setNextFocusDown(findFocusDown(0));
        mEpisodeAdapter.setNextFocusDown(findFocusDown(2));
        notifyItemChanged(mBinding.episode, mEpisodeAdapter);
        notifyItemChanged(mBinding.part, mPartAdapter);
        notifyItemChanged(mBinding.flag, mFlagAdapter);
    }

    @Override
    public void onRevSort() {
        mVod.setRevSort(!mHistory.isRevSort());
        mVod.reverseEpisode(false);
    }

    @Override
    public void onRevPlay(TextView view) {
        mVod.setRevPlay(!mHistory.isRevPlay());
        view.setText(mHistory.getRevPlayText());
        Notify.show(mHistory.getRevPlayHint());
    }

    private boolean shouldEnterFullscreen(Episode item) {
        boolean enter = !isFullscreen() && item.isSelected();
        if (enter) enterFullscreen();
        return enter;
    }

    private void enterFullscreen() {
        mFocus1 = getCurrentFocus();
        mBinding.video.requestFocus();
        mBinding.video.setForeground(null);
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        mBinding.flag.setSelectedPosition(mFlagAdapter.getPosition());
        mKeyDown.setFull(true);
        setFullscreen(true);
        mFocus2 = null;
    }

    private void exitFullscreen() {
        mBinding.video.setForeground(ResUtil.getDrawable(R.drawable.selector_video));
        mBinding.video.setLayoutParams(mFrameParams);
        getFocus1().requestFocus();
        mKeyDown.setFull(false);
        setFullscreen(false);
        mFocus2 = null;
        hideInfo();
    }

    private void onContent() {
        if (mBinding.content.getTag() == null) return;
        ContentDialog.create().content(mBinding.content.getTag().toString()).show(this);
    }

    private void onKeep() {
        Keep keep = Keep.find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        checkKeepImg();
    }

    private void onVideo() {
        if (!isFullscreen()) enterFullscreen();
    }

    private void onChange() {
        mVod.manualSwitchSource();
    }

    private void onRepeat() {
        player().setRepeatOne(!player().isRepeatOne());
        setRepeatVisual(player().isRepeatOne());
    }

    /**
     * Round 38 / R5（A 裁定 P2「开关类选中 = 加粗 + 1.5dp 描边、不加底」）：
     * 「循环播放」是控制栏唯一的持续 on/off 开关，其选中态由两件事同时表达——
     * 描边走 view_control_vod_action.xml 上换成的 selector_chip_switch（1.5dp、不加底），
     * 加粗只能在这里做：Android 的字体粗细不是状态列表可表达的属性（res/color 只管颜色），
     * 所以 setSelected 与加粗必须成对出现，**任何一个调用点漏改都会让开关"半更新"**
     * ——这也是把它们收进一个方法的原因（原先两处 setSelected 各写一遍）。
     * 加粗统一走 ResUtil.setSwitchBold（真实粗体字面；TextView 无 setFakeBoldText，
     * 直接写会编译不通过——beta23 首次构建即为此错，实现与理由见该方法注释）。
     */
    private void setRepeatVisual(boolean on) {
        mBinding.control.action.repeat.setSelected(on);
        ResUtil.setSwitchBold(mBinding.control.action.repeat, on);
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        setRepeatVisual(player().isRepeatOne());
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        mVod.nextEpisode(notify);
    }

    private void checkPrev() {
        mVod.prevEpisode(true);
    }

    private void onNext(boolean notify) {
        Episode item = mEpisodeAdapter.getNext();
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_prev : R.string.error_play_next);
    }

    private void onPrev(boolean notify) {
        Episode item = mEpisodeAdapter.getPrev();
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_next : R.string.error_play_prev);
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        setScale(index == array.length - 1 ? 0 : ++index);
    }

    private void onSpeed() {
        SpeedSettingDialog.create().player(player()).save(true).show(this);
        hideControl();
    }

    private boolean onSpeedLong() {
        SpeedSetting.putPlayback(PlaybackAction.toggleSpeed(player(), mBinding.widget.message));
        return true;
    }

    private void onReset() {
        onRefresh();
    }

    private void onParse() {
        ParseDialog.create().show(this);
        hideControl();
    }

    private void onReplay() {
        mVod.replay();
    }

    private void onRefresh() {
        mVod.refresh();
    }

    private void onOpening() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) setOpening(position);
    }

    private void onOpeningAdd() {
        setOpening(Math.max(0, Math.max(0, mHistory.getOpening()) + 1000));
    }

    private void onOpeningSub() {
        setOpening(Math.max(0, Math.max(0, mHistory.getOpening()) - 1000));
    }

    private boolean onOpeningReset() {
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        mVod.setOpening(opening);
        mBinding.control.action.opening.setText(opening <= 0 ? getString(R.string.play_op) : Util.timeMs(mHistory.getOpening()));
    }

    private void onEnding() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetEnding(position, duration)) setEnding(duration - position);
    }

    private void onEndingAdd() {
        setEnding(Math.max(0, Math.max(0, mHistory.getEnding()) + 1000));
    }

    private void onEndingSub() {
        setEnding(Math.max(0, Math.max(0, mHistory.getEnding()) - 1000));
    }

    private boolean onEndingReset() {
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        mVod.setEnding(ending);
        mBinding.control.action.ending.setText(ending <= 0 ? getString(R.string.play_ed) : Util.timeMs(mHistory.getEnding()));
    }

    private void onPlayer() {
        PlayerEngineDialog.show(this, mBinding.control.action.player, player());
        hideControl();
    }

    private void onDecode() {
        mClock.setCallback(null);
        player().toggleDecode();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).view(mBinding.player.getSubtitleView()).show(this);
        hideControl();
    }

    private void onEdition() {
        EditionDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onChapter() {
        ChapterDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onDanmaku() {
        DanmakuDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl(getFocus2());
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR3, 0);
        hideCenter();
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR3);
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

    private void showInfo() {
        mBinding.widget.top.setVisibility(View.VISIBLE);
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.duration.setText(player().getDurationTime());
        mBinding.widget.position.setText(player().getPositionTime(0));
    }

    private void hideInfo() {
        mBinding.widget.top.setVisibility(View.GONE);
        mBinding.widget.center.setVisibility(View.GONE);
    }

    // T-157 方案 C（拍板 51）：浮层信息卡（海报/片名/类型/元信息/简介）已移除——仅保留进度条+播放功能按钮行，
    // 下键呼出焦点落进度条（对齐 1.2.0 控制层交互）；原 setInfoCard() 实现随 view_control_vod.xml 的 info 一并删除

    private void showControl(View view) {
        showControl(view, true);
    }

    /** actionVisible=false 时仅显示进度条区（暂停态）；浮层态=进度条+播放功能按钮行
     *  T-157 方案 C（拍板 51）：已去掉信息卡（海报/源信息），下键呼出焦点落进度条 */
    /**
     * β61／丙：把「落焦点的目标」从外层容器换成真正处理按键的内层 timebar。
     * 单独成方法＋返回新引用，是为了让调用侧能写成 final（参数一旦被重赋，捕获它的 lambda 就编译不过）。
     * 非 PlayerSeekView 的调用点（按钮行、opening/ending、getFocus2 回退）原样返回 ⇒ 行为零变化。
     */
    private static View focusTargetOf(View view) {
        if (view instanceof PlayerSeekView) {
            Object bar = ((PlayerSeekView) view).getTimeBar();
            if (bar instanceof View) return (View) bar;
        }
        return view;
    }

    private void showControl(View view, boolean actionVisible) {
        Log.d("FireFlyPlay", "showControl actionVisible=" + actionVisible + " view=" + (view == null ? "null" : view.getId()) + " full=" + isFullscreen());
        mPauseBar = !actionVisible;

        // β61 / 丙（A 拍：公共路径统一，理由是只修 :1627 会把同一个 bug 留在其它入口）。
        // 事实：@+id/seek 是外层 androidx.media3.ui.PlayerSeekView——它 extends FrameLayout 且【没有 onKeyDown／dispatchKeyEvent】
        // （media3-ui 1.11.0-alpha01 javap 实测），真正处理左右拖动的是内层 DefaultTimeBar（它有 public boolean onKeyDown）。
        // 外层又被布局设为 focusable，FrameLayout 默认 FOCUS_BEFORE_DESCENDANTS ⇒ requestFocus 成功但焦点停在**不处理按键的容器**上：
        // 既没有进度条焦点态、左右键也走系统焦点搜索 ⇒ 刘先生看到的“焦点没落到进度条”。这里统一把目标下取成内层 timebar。
        // ⚠ 这里绝不能写成 `view = …`：:1208 那条 App.post(lambda) 捕获了 view，重赋参数会破掉 effectively final
        //    （闸门 r2 的编译错就是它，我交的影响面清单漏核了「参数被 lambda 捕获」这一类，靠闸门抓到）。
        final View focusTarget = focusTargetOf(view);
        mBinding.control.action.getRoot().setVisibility(actionVisible ? View.VISIBLE : View.GONE);
        // Round 38 / R2④：此处原为 updateEndRemain()（双时间行刷新），该行已整行删除
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        // T-167（C beta13：尺寸已修 ctrlH=291/ctrlW=1920，但 dump 无 control/seek、focus≠seek）：
        // ①z-order：把控制层提到 video 子视图最上层（防被 widget/progress 遮挡——uiautomator 只 dump visibleToUser）
        mBinding.control.getRoot().bringToFront();
        // β39 / beta69 问题2（A 派单）：原先这一句是同帧直调，视图刚显形还没就绪时 requestFocus()
        // 返回 false 且**没人读返回值** ⇒ 静默失败，焦点留在 video 上（刘先生所见"呼出控制层焦点不在进度条"）。
        // 直接尝试保留为第一跳（就绪时零延迟），失败才照 LiveActivity#showControl 同法延一帧重试。
        boolean attempt = focusTarget.requestFocus();
        logControlFocus("attempt", focusTarget, attempt);
        if (!attempt) App.post(() -> logControlFocus("after-post", focusTarget, focusTarget.requestFocus()), 25);
        setR1Callback();
        // T-167 诊断日志（post 下一帧读真实状态）：补 alpha/locationOnScreen/seek focusable/hasFocus
        View ctrl = mBinding.control.getRoot();
        View seek = mBinding.control.seek;
        ctrl.post(() -> {
            int[] loc = new int[2];
            ctrl.getLocationOnScreen(loc);
            Log.d("FireFlyPlay", "showControl post: ctrlVis=" + ctrl.getVisibility()
                    + " ctrlH=" + ctrl.getHeight() + " ctrlW=" + ctrl.getWidth()
                    + " ctrlMeasured=" + ctrl.getMeasuredWidth() + "x" + ctrl.getMeasuredHeight()
                    + " ctrlLp=" + (ctrl.getLayoutParams() == null ? "null" : ctrl.getLayoutParams().width + "x" + ctrl.getLayoutParams().height + "@" + ctrl.getLayoutParams().getClass().getSimpleName())
                    + " ctrlAttached=" + ctrl.isAttachedToWindow() + " ctrlToken=" + (ctrl.getWindowToken() != null)
                    + " ctrlAlpha=" + ctrl.getAlpha() + " ctrlLoc=" + loc[0] + "," + loc[1]
                    + " ctrlParent=" + (ctrl.getParent() == null ? "null" : ctrl.getParent().getClass().getSimpleName())
                    + " seekVis=" + seek.getVisibility() + " seekH=" + seek.getHeight() + " seekMeasured=" + seek.getMeasuredHeight()
                    + " seekFocusable=" + seek.isFocusable() + " seekHasFocus=" + seek.hasFocus()
                    + " seekLp=" + (seek.getLayoutParams() == null ? "null" : seek.getLayoutParams().height)
                    + " focus=" + (getCurrentFocus() == null ? "null" : getCurrentFocus().getId()));
        });
    }

    /** 落焦结果留痕（A 要求 C 真机可抓）：`showControl focus attempt|after-post` 两跳各出一条，带 seekHasFocus 与实际焦点控件 id。 */
    private void logControlFocus(String stage, View view, boolean result) {
        Log.d("FireFlyPlay", "showControl focus " + stage + " result=" + result
                + " view=" + (view == null ? "null" : view.getId())
                + " seekHasFocus=" + mBinding.control.seek.hasFocus()
                + " focus=" + (getCurrentFocus() == null ? "null" : getCurrentFocus().getId()));
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        hideInfo();
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.progress.traffic);
        // Round 38 / R2④：控制栏双时间行已删除，此处原为该行的每秒刷新入口
        App.post(mR3, 1000);
    }

    private void setR1Callback() {
        if (isScrubbing()) return;
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    @Override
    protected void onScrubbingChanged(boolean scrubbing) {
        if (scrubbing) App.removeCallbacks(mR1);
        else if (isVisible(mBinding.control.getRoot())) setR1Callback();
    }

    private void setR2Callback() {
        App.post(mR2, 500);
    }

    private void setArtwork(String url) {
        // T-150（拍板 48）：图片与播放地址解耦。原实现在这里把播放解析返回的 artwork（网盘类源常为
        // "网盘 LOGO"）直接写进 History.vodPic 并随后被 createKeep/updateKeep 落库 →
        // 历史/收藏/首页"继续观看"（三处入口都传 item.getVodPic()）永久显示 LOGO 而非海报。
        // 修复：artwork **仅用于播放器封面显示**；只有锁定图确实为空（无列表图）时才允许它补位落库。
        if (TextUtils.isEmpty(url)) {
            DetailLog.picSkip("empty", url);
        } else if (mHistory == null) {
            DetailLog.picSkip("nolock", url);
        } else if (!TextUtils.isEmpty(mHistory.getVodPic())) {
            DetailLog.picSkip("same", url);
        } else {
            mHistory.setVodPic(url);
            DetailLog.picSet(url);
        }
        setArtwork();
    }

    private void setArtwork() {
        ImgUtil.load(this, mHistory.getVodPic(), new CustomTarget<>() {
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

    private void setPartAdapter() {
        mPartAdapter.addAll(PartUtil.split(mHistory.getVodName()));
        mBinding.part.setVisibility(View.VISIBLE);
        setR2Callback();
    }

    private void saveHistory(boolean exit) {
        PlaybackService service = service();
        boolean owner = service != null && getPlaybackKey().equals(service.player().getKey());
        long position = owner ? service.player().getPosition() : C.TIME_UNSET;
        long duration = owner ? service.player().getDuration() : C.TIME_UNSET;
        if (mVod != null) mVod.saveHistory(exit, System.currentTimeMillis(), position, duration);
    }

    private void checkKeepImg() {
        mBinding.keep.setCompoundDrawablesWithIntrinsicBounds(Keep.find(getHistoryKey()) == null ? R.drawable.ic_detail_keep_off : R.drawable.ic_detail_keep_on, 0, 0, 0);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        // T-150（拍板 48）：收藏图优先用进入播放页时带入的**列表锁定图**（intent "pic"），
        // 不再取 mHistory.getVodPic()——后者可能被播放解析的 artwork 污染（本文件 setArtwork(String) 已修）。
        keep.setVodPic(TextUtils.isEmpty(getPic()) ? mHistory.getVodPic() : getPic());
        keep.setVodName(mHistory.getVodName());
        keep.setSiteName(getSite().getName());
        // β18 / T-213：播放页新建收藏——更新进度取 mHistory（History 本来就有这一列，同源同语义）
        keep.setVodRemarks(mHistory.getVodRemarks() == null ? "" : mHistory.getVodRemarks());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    private void updateKeep() {
        Keep keep = Keep.find(getHistoryKey());
        if (keep != null) {
            keep.setVodName(mHistory.getVodName());
            // T-150（拍板 48）：同 createKeep，收藏刷新也只用列表锁定图，避免 artwork(LOGO) 落库
            keep.setVodPic(TextUtils.isEmpty(getPic()) ? mHistory.getVodPic() : getPic());
            // β18 / T-213：收藏刷新也带上更新进度（与 createKeep 同一路来源，避免同一收藏两态角标不一致）
            keep.setVodRemarks(mHistory.getVodRemarks() == null ? "" : mHistory.getVodRemarks());
            keep.save();
        }
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            checkNext();
        }

        @Override
        public void onPrev() {
            checkPrev();
        }

        @Override
        public void onStop() {
            finish();
        }

        @Override
        public void onReplay() {
            VideoActivity.this.onReplay();
        }
    };

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

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
    protected void onMediaOptionsChanged() {
        setMediaOptionVisible();
    }

    @Override
    protected void onError(String msg) {
        mVod.playbackError(msg);
    }

    @Override
    protected void onReclaim() {
        mVod.refresh();
    }

    @Override
    protected void onStateChanged(int state) {
        switch (state) {
            case Player.STATE_BUFFERING:
                showProgress();
                mClock.setCallback(null);
                break;
            case Player.STATE_READY:
                hideProgress();
                player().reset();
                mClock.setCallback(this);
                break;
            case Player.STATE_ENDED:
                hideProgress();
                mVod.playbackEnded();
                mClock.setCallback(null);
                break;
        }
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (isPlaying) {
            hideCenter();
        } else if (isPaused()) {
            if (isFullscreen()) showInfo();
            else hideInfo();
        }
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        mBinding.widget.size.setText(player().getSizeText());
    }

    @Override
    public void onTimeChanged(long time) {
        if (!isOwner() || !player().isVod()) return;
        long position = player().getPosition();
        long duration = player().getDuration();
        if (position < 0 || duration <= 0) return;
        mVod.onTimeChanged(time, position, duration);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) mVod.requestDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) mVod.refresh();
        else if (event.getType() == RefreshEvent.Type.VOD) mVod.updateVod(event.getVod());
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) player().setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) player().setDanmaku(Danmaku.from(event.getPath()));
    }

    @Override
    protected long startPositionMs() {
        return mVod == null ? C.TIME_UNSET : mVod.startPositionMs();
    }

    private void setTrackVisible() {
        PlaybackAction.setTracks(player(), mBinding.control.action.text, mBinding.control.action.audio, mBinding.control.action.video);
    }

    private void setMediaOptionVisible() {
        PlaybackAction.setMediaOptions(player(), mBinding.control.action.edition, mBinding.control.action.chapter);
    }

    @Override
    public void onItemClick(Vod item) {
        mVod.selectSource(item);
    }

    @Override
    public void onParse(Parse item) {
        mVod.selectParse(item);
    }

    private void onPaused() {
        controller().pause();
    }

    private void onPlay() {
        if (mHistory != null && isEnded()) controller().seekTo(mHistory.getOpening());
        if (!player().isEmpty() && isIdle()) controller().prepare();
        controller().play();
    }

    private boolean onSeekBack() {
        controller().seekBack();
        return true;
    }

    private boolean onSeekForward() {
        controller().seekForward();
        return true;
    }

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    private View getFocus1() {
        return mFocus1 == null || mFocus1.getVisibility() != View.VISIBLE ? mBinding.video : mFocus1;
    }

    private View getFocus2() {
        // T-189-b（Round 40，A 裁定按刘先生原话）：兜底焦点随行首位走 —— 由 prev 改回 **next（下一集）**，
        // 与 setSeekDownFocus「进度条下键落行首位」同一口径；opening/ending 是上下键标记项，不作为兜底目标（原逻辑保留）。
        return mFocus2 == null || mFocus2.getVisibility() != View.VISIBLE || mFocus2 == mBinding.control.action.opening || mFocus2 == mBinding.control.action.ending ? mBinding.control.action.next : mFocus2;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // T-164 排查日志（tag FireFlyPlay）：验证按键是否到达、当前是否全屏、控制层可见性、service 就绪
        Log.d("FireFlyPlay", "dispatch k=" + event.getKeyCode() + " a=" + event.getAction() + " full=" + isFullscreen() + " ctrl=" + isVisible(mBinding.control.getRoot()) + " svc=" + (service() != null));
        mLastKey = event.getKeyCode();   // T-194-b2：给全局焦点监听器标注"是哪把键导致的移动"
        // T-202（β1，刘先生拍板「完全回到 1.1 语义」）：撤销原 Round 41/42 把「预览态 DOWN」与「播放态 DOWN」
        // 归一的那两个分支（原写法：MENU 非全屏 → enterForControl()；非全屏 DOWN 一律 → enterForControl()）。
        // 回到 619e991 的分发原则——**只有 `isFullscreen()` 才允许劫持按键**，非全屏（小窗）的一切方向键
        // 交回 `super.dispatchKeyEvent`，由 activity_video.xml 的静态连线逐级下行（`:22` video→flag 线路行；
        // 该 11 条连线自 1.1 版逐字未改，见 round1-alpha-B.md §B4）。
        // 小窗呼出只留显式路径：OK/点小窗进全屏（shouldEnterFullscreen / 点击监听），MENU 在非全屏下
        // **只做 enterFullscreen()，不进控制层**——A 裁定「不新增 DOWN 分支」。
        if (KeyUtil.isMenuKey(event)) {
            if (isFullscreen()) onToggle();
            else enterFullscreen();
            return true;
        }
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (isVisible(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();
        if (isFullscreen() && isGone(mBinding.control.getRoot()) && mKeyDown.hasEvent(event) && service() != null) return mKeyDown.onKeyDown(event);
        if (KeyUtil.isMediaFastForward(event)) return onSeekForward();
        if (KeyUtil.isMediaRewind(event)) return onSeekBack();
        boolean handled = super.dispatchKeyEvent(event);
        // T-194-b（Round 42，A 要求补的可验日志）：控制层不在 accessibility 树、全屏 Surface 截图全黑、
        // 3 秒自动收起 ⇒ C 无法 dump 焦点（T-189-b 取不到证据）。这里在每次按键的下一帧打焦点资源名，
        // 让 C 用 logcat 逐次验"进度条 → 下一集 → 上一集 → … → 末项再 RIGHT 回首项"的环绕顺序。
        if (isVisible(mBinding.control.getRoot())) logCtrlFocus(event.getKeyCode());
        return handled;
    }

    private String lastCtrlFocus = "";

    /** 上一把按键的键码，供全局焦点监听器把日志归到具体操作上（T-194-b2）。 */
    private int mLastKey;

    /** T-194-b2：焦点是否落在控制层（进度条 + 按钮行）内部——用它把"信息区行的焦点变化"过滤掉。 */
    private boolean isInsideControl(View v) {
        View p = v;
        View root = mBinding.control.getRoot();
        while (p != null) {
            if (p == root) return true;
            p = p.getParent() instanceof View ? (View) p.getParent() : null;
        }
        return false;
    }

    /** T-194-b：打控制行焦点。只在焦点真的变了时打，避免连按方向键刷屏；资源名取不到时打 id 十进制。 */
    private void logCtrlFocus(int keyCode) {
        mBinding.control.getRoot().post(() -> {
            View f = getCurrentFocus();
            String name = "null";
            if (f != null && f.getId() != View.NO_ID) {
                try {
                    name = getResources().getResourceEntryName(f.getId());
                } catch (Exception e) {
                    name = "id/" + f.getId();
                }
            }
            if (name.equals(lastCtrlFocus)) return;
            lastCtrlFocus = name;
            Log.d("FireFlyPlay", "ctrlFocus=" + name + " k=" + keyCode
                    + " full=" + isFullscreen() + " ctrl=" + isVisible(mBinding.control.getRoot()));
        });
    }

    @Override
    public void onSeeking(long time) {
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.duration.setText(player().getDurationTime());
        mBinding.widget.position.setText(player().getPositionTime(time));
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        if (seekTo(time)) hideCenter();
        mKeyDown.reset();
    }

    @Override
    public void onSpeedUp() {
        if (!player().isPlaying()) return;
        PlaybackAction.startSpeedPress(player(), mBinding.widget.message);
    }

    @Override
    public void onSpeedEnd() {
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
        player().setSpeed(SpeedSetting.getPlayback());
    }

    @Override
    public void onKeyUp() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) {
            showControl(mBinding.control.action.opening);
        } else if (player().canSetEnding(position, duration)) {
            showControl(mBinding.control.action.ending);
        } else {
            showControl(getFocus2());
        }
    }

    @Override
    public void onKeyDown() {
        Log.d("FireFlyPlay", "onKeyDown -> showControl(seek,true)");
        // T-157（拍板 51 路线 A，刘先生 beta7 反馈三：播放页按下键浮层操作不顺、焦点不在进度条）：
        // 参照 1.1.0 浮层交互——下键呼出后焦点落【进度条】（左右键 seek、下键进功能按钮行、
        // 功能行上键回进度条）；三态框架与其余浮层功能保留不动（原实现落 getFocus2()=next 按钮）。
        showControl(mBinding.control.seek, true);
    }

    @Override
    public void onKeyCenter() {
        if (player().isPlaying()) {
            // 沉浸态 OK → 暂停态：暂停 + 暂停条（仅进度条，规格拍板 12）
            onPaused();
            showControl(mBinding.control.seek, false);
        } else if (player().isEmpty()) {
            onRefresh();
        } else {
            // 暂停态 OK → 恢复播放回沉浸态
            onPlay();
            hideControl();
        }
    }

    @Override
    public void onSingleTap() {
        if (isFullscreen()) onToggle();
    }

    @Override
    public void onDoubleTap() {
        if (isFullscreen()) onKeyCenter();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1001) PlaybackIntent.onExternalResult(data, service()::dispatchNext, controller()::seekTo);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveHistory(false);
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        // β10 / A2 ①（入口无条件留痕，零行为）：A2 的病根正是"这一被 BACK 被哪个层吃掉了"看不清，
        // 而三层里只有 `widget.center` 从没进过任何日志。这里一次把三层状态打全，
        // 使 C 拿一条 `back enter` 就能对出"该收层"还是"该返回"。
        // `full=` 用 1/0（与前缀串区分开，避免和 :1502 的 `dispatch k=… full=true` 混淆）。
        // 挂载点可靠性：`dispatchKeyEvent`:1502 本来每把键都无条件打 `dispatch k=…`，BACK(=4) 也在内，
        // 但 `dispatch` 行不含 center；本行是唯一含 center 的 BACK 入口证据。
        // 本页没有第二处吃 BACK 的地方：`BaseActivity.setBackCallback()`（leanback :77-84）挂的是
        // `addCallback(this, OnBackPressedCallback(true))`（onCreate 内、无条件启用），命中即直调本方法；
        // `CustomKeyDownVod.hasEvent()` 不含 BACK ⇒ :1518 那条全屏劫持分支不会截住 BACK。
        Log.d("FireFlyPlay", "back enter ctrl=" + (isVisible(mBinding.control.getRoot()) ? "visible" : "hidden")
                + " center=" + (isVisible(mBinding.widget.center) ? "visible" : "hidden")
                + " full=" + (isFullscreen() ? 1 : 0) + " src=" + getSource());
        if (isVisible(mBinding.control.getRoot())) {
            // β9 / A1：两条"收层"支路各补一条留痕（零行为），C 因此能一眼分出「这一被是收层」还是「这一被该回详情」
            Log.d("FireFlyPlay", "back ctrl=visible action=hideControl");
            hideControl();
        } else {
            // β10 / A2（P1，根因是**我们自己**、不是媒体库也不是 CustomKeyDownVod）：
            // 原来这里有一条 `else if (isVisible(mBinding.widget.center)) { … hideCenter(); }`——
            //   · `widget.center` 是播放状态中心提示（快进/快退/暂停图标），由 showInfo():1165 与 onSeeking():1566 置 VISIBLE；
            //   · 它被 hideCenter()→hideInfo()（:1172）收掉，代价是**第二下 BACK 花在收它上面**、第三下才返回
            //     ⇒ 与 C 的实测序列完全一致：BACK1=收控制层、BACK2=收中心提示（β9 我加的日志是 `back center=visible action=hideCenter`，
            //     C 说"BACK2 无日志"应是只 grep 了 `back ctrl=`/`back src=` 两种串），BACK3=finish。
            // 修法：中心提示**不再算拦截层**（用户按 BACK 的意图是离开，不是收起一个本来就会自动消失的图标）；
            //       改为在返回路径里离开前顺手收起（下面 hideCenter()），切页动画里也不会留悬空图标。
            // 判据（A 的字面串）：控制层已收起时那一下必为 `back ctrl=hidden action=finish`。
            // β9 / A1（P1）：全屏且控制层收起时不再"退出全屏"吃第一下（原 `else if (isFullscreen()) exitFullscreen();` 已删）。
            //   被删的 exitFullscreen()/mFrameParams/mFocus1/getFocus1 现无调用者，仍**不连带删**（清理与 T-190/T-202 纠缠），
            //   登记见 round1-beta9-patch/INDEX.md 与本轮 INDEX。
            mViewModel.stopSearch();
            // β8 / T-201（刘先生拍板选项 B）：BACK 一律「回来的地方」＝`finish` 自然后退，**不补开任何页面**；
            // 只有本页是任务根（投屏续播 / 推送 / 嗅探 / 本地文件这类无来源入口）才 startActivity 回首页——
            // 这条是既有行为（原 :1680），不是本批新增；除此之外一律不重开页面，避免"叠栈/跳过一页"。
            boolean root = isTaskRoot();
            // 两行日志各有用途，合成一条会让两个 grep 互相打断：
            //   第一行满足 β10/A2 的字面判据 `back ctrl=hidden action=finish`；
            //   第二行是 β8/β9 起 C 已在用的分组键 `back src=…`。
            // β10：这里原先重复带的 `center=` 已移除——入口 `back enter` 行已经打过，两层信息不必在同一序列里出现两次。
            Log.d("FireFlyPlay", "back ctrl=hidden action=" + (root ? "home" : "finish") + " src=" + getSource());
            Log.d("FireFlyPlay", "back src=" + getSource() + " action=" + (root ? "home" : "finish"));
            hideCenter();   // 顺手收起中心提示（不消费按键，纯清理）
            if (root) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        mClock.release();
        saveHistory(true);
        DanmakuApi.cancel();
        RefreshEvent.keep();
        App.removeCallbacks(mR1, mR2, mR3, mR4);
        super.onDestroy();
    }
}
