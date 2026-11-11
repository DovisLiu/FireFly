package com.firefly.tv.ui.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
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
import com.firefly.tv.playback.PlaybackOrientation;
import com.firefly.tv.playback.PlaybackReset;
import com.firefly.tv.playback.PlaybackResult;
import com.firefly.tv.playback.live.LivePlayRequest;
import com.firefly.tv.playback.live.LivePlaybackController;
import com.firefly.tv.playback.live.LivePlaybackHost;
import com.firefly.tv.player.extractor.Source;
import com.firefly.tv.service.PlaybackService;
import com.firefly.tv.setting.LiveSetting;
import com.firefly.tv.ui.adapter.ChannelAdapter;
import com.firefly.tv.ui.adapter.EpgDataAdapter;
import com.firefly.tv.ui.adapter.GroupAdapter;
import com.firefly.tv.ui.custom.CustomKeyDown;
import com.firefly.tv.ui.dialog.CastDialog;
import com.firefly.tv.ui.dialog.HistoryDialog;
import com.firefly.tv.ui.dialog.InfoDialog;
import com.firefly.tv.ui.dialog.LiveDialog;
import com.firefly.tv.ui.dialog.PassDialog;
import com.firefly.tv.ui.dialog.PlayerEngineDialog;
import com.firefly.tv.ui.dialog.SpeedSettingDialog;
import com.firefly.tv.ui.dialog.TrackDialog;
import com.firefly.tv.utils.Biometric;
import com.firefly.tv.utils.ImgUtil;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.PiP;
import com.firefly.tv.utils.ResUtil;
import com.firefly.tv.utils.Traffic;
import com.firefly.tv.utils.Util;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LiveActivity extends PlaybackActivity implements CustomKeyDown.Listener, Biometric.Callback, PassListener, ConfigListener, LiveListener, GroupAdapter.OnClickListener, ChannelAdapter.OnClickListener, EpgDataAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener, LivePlaybackHost {

    private ActivityLiveBinding mBinding;
    private LiveViewModel mViewModel;
    private LivePlaybackController mLive;
    private GroupAdapter mGroupAdapter;
    private ChannelAdapter mChannelAdapter;
    private EpgDataAdapter mEpgDataAdapter;
    private CustomKeyDown mKeyDown;
    private PiP mPiP;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private List<Group> mHides;
    private Group mGroup;
    private Channel mChannel;
    private String mPlaybackKey;
    private boolean rotate;
    private int count;
    private String mIntentGroup;
    private String mIntentChannel;
    private boolean mFromBoot;
    private boolean mPlayedOnce;

    public static void start(Context context) {
        context.startActivity(new Intent(context, LiveActivity.class).putExtra("empty", LiveConfig.isEmpty()));
    }

    /** β75 甲案：从选台页进入，带指定台（组名＋台名，extras 与 TV 侧 LiveSelectActivity:437 同名） */
    public static void start(Context context, String group, String channel) {
        context.startActivity(new Intent(context, LiveActivity.class).putExtra("empty", LiveConfig.isEmpty()).putExtra("group", group).putExtra("channel", channel));
    }

    /** BOOT 开机自启：直达播放页（A 裁的产品判断），extras 打标——首次起播失败从这里退选台页 */
    public static void startBoot(Context context) {
        context.startActivity(new Intent(context, LiveActivity.class).putExtra("empty", LiveConfig.isEmpty()).putExtra("boot", true));
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.hideSystemUI(this);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        mIntentGroup = getIntent().getStringExtra("group");
        mIntentChannel = getIntent().getStringExtra("channel");
        mFromBoot = getIntent().getBooleanExtra("boot", false);
        mKeyDown = CustomKeyDown.create(this, mBinding.player);
        setPadding(mBinding.control.getRoot());
        setPadding(mBinding.recycler, true);
        mHides = new ArrayList<>();
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::hideInfo;
        mPiP = new PiP();
        setRecyclerView();
        setVideoView();
        setViewModel();
        checkLive();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.control.back.setOnClickListener(view -> onBack());
        mBinding.control.cast.setOnClickListener(view -> onCast());
        mBinding.control.info.setOnClickListener(view -> onInfo());
        mBinding.control.play.setOnClickListener(view -> checkPlay());
        mBinding.control.next.setOnClickListener(view -> nextChannel());
        mBinding.control.prev.setOnClickListener(view -> prevChannel());
        mBinding.control.right.lock.setOnClickListener(view -> onLock());
        mBinding.control.right.rotate.setOnClickListener(view -> onRotate());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.home.setOnClickListener(view -> onHome());
        mBinding.control.action.line.setOnClickListener(view -> onLine());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.config.setOnClickListener(view -> onConfig());
        mBinding.control.action.invert.setOnClickListener(view -> onInvert());
        mBinding.control.action.across.setOnClickListener(view -> onAcross());
        mBinding.control.action.change.setOnClickListener(view -> onChange());
        mBinding.control.action.player.setOnClickListener(view -> onPlayer());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.getRoot().setOnTouchListener(this::onActionTouch);
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
    }

    private void setRecyclerView() {
        mBinding.group.setItemAnimator(null);
        mBinding.channel.setItemAnimator(null);
        mBinding.epgData.setItemAnimator(null);
        mBinding.group.setAdapter(mGroupAdapter = new GroupAdapter(this));
        mBinding.channel.setAdapter(mChannelAdapter = new ChannelAdapter(this));
        mBinding.epgData.setAdapter(mEpgDataAdapter = new EpgDataAdapter(this));
    }

    private void setVideoView() {
        setScale(LiveSetting.getScale());
        PlayerEngineDialog.setText(mBinding.control.action.player);
        mBinding.control.action.invert.setSelected(LiveSetting.isInvert());
        mBinding.control.action.across.setSelected(LiveSetting.isAcross());
        mBinding.control.action.change.setSelected(LiveSetting.isChange());
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mPiP.update(this, view));
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
                // A 裁判据的另一档：源配置本身加载失败，BOOT 直达同样退选台页
                if (mFromBoot && !mPlayedOnce) {
                    Log.d("FireFlyLiveSelect", "boot live load fail msg=" + msg);
                    // β29 批二（A 落码单接线④）：选台已收编为宿主 Fragment（position 8），退档改走 HomeActivity＋EXTRA_TAB(live)
                    startActivity(new Intent(LiveActivity.this, HomeActivity.class)
                            .putExtra(HomeActivity.EXTRA_TAB, R.id.live)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                }
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
        for (Group group : live.getGroups()) (group.isHidden() ? mHides : items).add(group);
        mGroupAdapter.addAll(items);
        // β75：指定台优先于 keep（选台页进来按选的台定位；找不到回落 keep，不黑屏卡死）
        setPosition(mIntentChannel != null ? findIntentPosition(items) : LiveConfig.get().findKeepPosition(items));
    }

    private int[] findIntentPosition(List<Group> items) {
        for (int i = 0; i < items.size(); i++) {
            Group group = items.get(i);
            if (group == null || !group.getName().equals(mIntentGroup)) continue;
            int index = group.find(mIntentChannel);
            if (index >= 0) {
                Log.d("FireFlyLiveSelect", "intent play group=" + mIntentGroup + " channel=" + mIntentChannel + " at=" + i + "," + index);
                return new int[]{i, index};
            }
            break;
        }
        Log.d("FireFlyLiveSelect", "intent play miss group=" + mIntentGroup + " channel=" + mIntentChannel);
        return LiveConfig.get().findKeepPosition(items);
    }

    private void setWidth(Live live) {
        int padding = ResUtil.dp2px(48);
        if (live.getWidth() == 0) for (Group item : live.getGroups()) live.setWidth(Math.max(live.getWidth(), ResUtil.getTextWidth(item.getName(), 14)));
        int width = live.getWidth() == 0 ? 0 : Math.min(live.getWidth() + padding, ResUtil.getScreenWidth() / 4);
        setWidth(mBinding.group, width);
    }

    @Override
    public void setWidth(Group group) {
        int logo = ResUtil.dp2px(56);
        int padding = ResUtil.dp2px(60);
        if (group.isKeep()) group.setWidth(0);
        if (group.getWidth() == 0) for (Channel item : group.getChannel()) group.setWidth(Math.max(group.getWidth(), (item.getLogo().isEmpty() ? 0 : logo) + ResUtil.getTextWidth(item.getNumber() + item.getName(), 14)));
        int width = group.getWidth() == 0 ? 0 : Math.min(group.getWidth() + padding, ResUtil.getScreenWidth() / 2);
        setWidth(mBinding.channel, width);
    }

    private void setWidth(Epg epg) {
        int padding = ResUtil.dp2px(48);
        if (epg.getList().isEmpty()) return;
        int minWidth = ResUtil.getTextWidth(epg.getList().get(0).getTime(), 12);
        if (epg.getWidth() == 0) for (EpgData item : epg.getList()) epg.setWidth(Math.max(epg.getWidth(), ResUtil.getTextWidth(item.getTitle(), 14)));
        int maxWidth = ResUtil.getScreenWidth() / 2;
        int minContentWidth = Math.min(minWidth + padding, maxWidth);
        int width = epg.getWidth() == 0 ? 0 : Math.clamp(epg.getWidth() + padding, minContentWidth, maxWidth);
        setWidth(mBinding.epgData, width);
    }

    private void setWidth(View view, int width) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params.width == width) return;
        params.width = width;
        view.setLayoutParams(params);
    }

    private void setPosition(int[] position) {
        if (position[0] == -1) return;
        int size = mGroupAdapter.getItemCount();
        if (size == 1 || position[0] >= size) return;
        // β68 乙案（A 令 4）：记住的落点若指向空组⇒改第一个非空，否则「进页就空白」不解决（findKeepPosition 未记台时返 {0,0}）。
        if (mGroupAdapter.get(position[0]).isEmpty()) {
            int next = -1;
            for (int i = 0; i < size; i++) if (!mGroupAdapter.get(i).isEmpty()) { next = i; break; }
            if (next == -1) return;
            position = new int[]{next, 0};
        }
        mGroup = mGroupAdapter.get(position[0]);
        mGroup.setPosition(position[1]);
        mLive.selectGroup(mGroup);
        mLive.selectChannel(mGroup.current());
    }

    private void setPosition() {
        if (mChannel == null) return;
        mGroup = mChannel.getGroup();
        int position = mGroupAdapter.indexOf(mGroup);
        boolean change = mGroupAdapter.getPosition() != position;
        if (change) mGroupAdapter.setSelected(position);
        if (change) mChannelAdapter.addAll(mGroup.getChannel());
        if (change) mChannelAdapter.setSelected(mGroup.getPosition());
        scrollToPosition(mBinding.channel, mGroup.getPosition());
        scrollToPosition(mBinding.group, position);
    }

    private void onBack() {
        finish();
    }

    private void onCast() {
        CastDialog.create(player()).show(this);
    }

    private void onInfo() {
        InfoDialog.create(player()).show(this);
    }

    private void onLock() {
        setLock(!isLock());
        setRequestedOrientation(getLockOrient());
        mKeyDown.setLock(isLock());
        checkLockImg();
        showControl();
    }

    private void onRotate() {
        setR1Callback();
        setRotate(!isRotate());
        setRequestedOrientation(PlaybackOrientation.getRotateOrientation(this));
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
        else LiveDialog.show(this);
        hideControl();
    }

    private void onLine() {
        nextLine(false);
    }

    private void onScale() {
        int index = LiveSetting.getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        if (mKeyDown.getScale() != 1.0f) mKeyDown.resetScale();
        else setScale(index == array.length - 1 ? 0 : ++index);
        setR1Callback();
    }

    private void onSpeed() {
        SpeedSettingDialog.create().player(player()).show(this);
        hideControl();
    }

    private boolean onSpeedLong() {
        PlaybackAction.toggleSpeed(player(), mBinding.widget.message);
        setR1Callback();
        return true;
    }

    private void onConfig() {
        HistoryDialog.create().live().readOnly().show(this);
        hideControl();
    }

    private void onInvert() {
        setR1Callback();
        LiveSetting.putInvert(!LiveSetting.isInvert());
        mBinding.control.action.invert.setSelected(LiveSetting.isInvert());
    }

    private void onAcross() {
        setR1Callback();
        LiveSetting.putAcross(!LiveSetting.isAcross());
        mBinding.control.action.across.setSelected(LiveSetting.isAcross());
    }

    private void onChange() {
        setR1Callback();
        LiveSetting.putChange(!LiveSetting.isChange());
        mBinding.control.action.change.setSelected(LiveSetting.isChange());
    }

    private void onDecode() {
        player().toggleDecode();
        setR1Callback();
    }

    private void onPlayer() {
        PlayerEngineDialog.show(this, mBinding.control.action.player, player());
        hideControl();
    }

    private boolean onActionTouch(View v, MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) setR1Callback();
        return false;
    }

    private int getLockOrient() {
        return PlaybackOrientation.getLockOrientation(this, isLock(), isRotate());
    }

    private void hideUI() {
        if (isGone(mBinding.recycler)) return;
        mBinding.recycler.setVisibility(View.GONE);
        setPosition();
    }

    private void showUI() {
        if (isVisible(mBinding.recycler) || mGroupAdapter.getItemCount() == 0) return;
        mBinding.recycler.setVisibility(View.VISIBLE);
        mBinding.channel.requestFocus();
        setPosition();
        hideEpg();
    }

    private void showEpg(Channel item) {
        if (mChannel == null || mChannel.getData(mViewModel.getZoneId()).getList().isEmpty() || mEpgDataAdapter.getItemCount() == 0 || !mChannel.equals(item) || !mChannel.getGroup().equals(mGroup)) return;
        scrollToPosition(mBinding.epgData, item.getData(mViewModel.getZoneId()).getSelected());
        mBinding.epgData.setVisibility(View.VISIBLE);
        mBinding.channel.setVisibility(View.GONE);
        mBinding.group.setVisibility(View.GONE);
    }

    private void hideEpg() {
        mBinding.channel.setVisibility(View.VISIBLE);
        mBinding.group.setVisibility(View.VISIBLE);
        mBinding.epgData.setVisibility(View.GONE);
    }

    @Override
    public void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR2, 0);
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
        mBinding.widget.error.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.error.setText("");
    }

    private void showControl() {
        if (service() == null || isInPictureInPictureMode()) return;
        mBinding.control.info.setVisibility(player().isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.cast.setVisibility(player().isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.right.rotate.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.center.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.bottom.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.back.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.top.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        setR1Callback();
        hideInfo();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    private void showInfo() {
        mBinding.widget.infoPip.setVisibility(isInPictureInPictureMode() ? View.VISIBLE : View.GONE);
        mBinding.widget.info.setVisibility(isInPictureInPictureMode() ? View.GONE : View.VISIBLE);
        setR3Callback();
        hideControl();
        setInfo();
    }

    private void hideInfo() {
        mBinding.widget.infoPip.setVisibility(View.GONE);
        mBinding.widget.info.setVisibility(View.GONE);
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
        if (item.isEmpty()) return;   // β68 乙案（A 令 3）：防御其他路径把空组递进来（置灰已在 GroupAdapter 做）
        mLive.selectGroup(item);
        scrollToPosition(mBinding.channel, Math.max(item.getPosition(), 0));
        if (!item.isKeep() || ++count < 5 || mHides.isEmpty()) return;
        if (Biometric.enable()) Biometric.show(this);
        else PassDialog.create().show(this);
        resetPass();
    }

    @Override
    public void onItemClick(Channel item) {
        if (!item.getData(mViewModel.getZoneId()).getList().isEmpty() && item.isSelected() && mChannel != null && mChannel.equals(item) && mChannel.getGroup().equals(mGroup)) {
            showEpg(item);
        } else if (mGroup != null) {
            mLive.selectChannel(item.group(mGroup));
        }
    }

    // β67 甲案说明（注释级，行为零改动）：本方法的唯一调用点是 ChannelAdapter 里那行长按绑定，绑定摘除后它在包内不可达；
    // 本体一字未动，保留作本改动的回滚点。要让长按有别的落点请单独给令。
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
        mBinding.widget.namePip.setText(mChannel.getShow());
        mBinding.widget.number.setText(mChannel.getNumber());
        mBinding.widget.numberPip.setText(mChannel.getNumber());
        mBinding.widget.line.setVisibility(mChannel.getLineVisible());
        mBinding.control.action.line.setText(mBinding.widget.line.getText());
        mBinding.control.action.line.setVisibility(mBinding.widget.line.getVisibility());
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
        if (mChannel != null && success)
            mViewModel.getEpg(mChannel);
    }

    private void stopPlayer() {
        player().clear();
        player().stop();
        checkControl();
    }

    @Override
    public int getGroupCount() {
        return mGroupAdapter.getItemCount();
    }

    @Override
    public int getGroupPosition() {
        return mGroupAdapter.getPosition();
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
        mPlayedOnce = true;
        startPlayer(mPlaybackKey = result.getRealUrl(), result, false, getHome().getTimeout(), position, metadata);
    }

    @Override
    public void resetPlaybackForError(String msg) {
        // BOOT 直达（A 追加约束）：从未成功起播过且走到这里＝首起失败（主层 applyPlaybackResult 的 hasMsg／realUrl 空，
        // 或逐线兜底到最后一线仍失败）⇒ 退选台页，不停黑屏。已播过任何一次（mPlayedOnce）的后续断流维持原 showError 行为。
        if (mFromBoot && !mPlayedOnce) {
            Log.d("FireFlyLiveSelect", "boot first play fail msg=" + msg);
            // β29 批二（A 落码单接线④）：同上，退档改走宿主 Fragment 档
            startActivity(new Intent(this, HomeActivity.class)
                    .putExtra(HomeActivity.EXTRA_TAB, R.id.live)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
            return;
        }
        PlaybackReset.afterError(player());
        showError(msg);
    }

    @Override
    public void renderGroupSelection(Group group) {
        mGroupAdapter.setSelected(mGroup = group);
    }

    @Override
    public void renderGroupChannels(Group group) {
        mChannelAdapter.addAll(group.getChannel());
        mChannelAdapter.setSelected(group.getPosition());
    }

    @Override
    public void renderChannelSelection(Channel channel) {
        if (mGroup != null) mGroup.setPosition(mChannelAdapter.setSelected(channel.group(mGroup)));
        mChannel = channel;
        setArtwork();
        showInfo();
        hideUI();
    }

    @Override
    public void renderLineSelection(Channel channel, boolean show) {
        if (show) showInfo();
        else setInfo();
    }

    @Override
    public void renderEpgSelection(EpgData data) {
        mEpgDataAdapter.setSelected(data);
    }

    @Override
    public void renderPlaybackMetadata(MediaMetadata metadata) {
        if (service() != null && isOwner()) player().setMetadata(metadata);
        mBinding.control.title.setText(metadata.displayTitle);
        mBinding.control.title.setSelected(true);
    }

    @Override
    public void showCatchupReady(EpgData data) {
        Notify.show(getString(R.string.play_ready, data.getTitle()));
    }

    private void checkControl() {
        if (isVisible(mBinding.control.getRoot())) showControl();
    }

    private void checkLockImg() {
        mBinding.control.right.lock.setImageResource(isLock() ? R.drawable.ic_control_lock_on : R.drawable.ic_control_lock_off);
    }

    private void resetAdapter() {
        mBinding.control.action.line.setVisibility(View.GONE);
        mBinding.control.title.setText("");
        mEpgDataAdapter.clear();
        mChannelAdapter.clear();
        mGroupAdapter.clear();
        mHides.clear();
        mChannel = null;
        mGroup = null;
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

        @Override
        public void onAudio() {
            Util.moveToBackground(LiveActivity.this);
            setAudioOnly(true);
        }
    };

    @Override
    protected void onPrepare() {
        setPlaybackMode();
        checkControl();
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
                checkControl();
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
        mBinding.control.play.setImageResource(isPlaying ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
        mPiP.update(this, isPlaying);
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

    @Override
    public void onBiometricSuccess() {
        unlock(null);
    }

    private void unlock(String pass) {
        boolean first = true;
        Iterator<Group> iterator = mHides.iterator();
        while (iterator.hasNext()) {
            Group item = iterator.next();
            if (pass != null && !pass.equals(item.getPass())) continue;
            mGroupAdapter.add(item);
            if (first) onItemClick(item);
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

    private void nextLine(boolean show) {
        mLive.nextLine(show);
    }

    private void onPaused() {
        controller().pause();
    }

    private void onPlay() {
        controller().play();
    }

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
        if (rotate) {
            noPadding(mBinding.recycler);
            noPadding(mBinding.control.getRoot());
        } else {
            setPadding(mBinding.recycler, true);
            setPadding(mBinding.control.getRoot());
        }
    }

    private void scrollToPosition(RecyclerView view, int position) {
        view.post(() -> view.scrollToPosition(position));
    }

    @Override
    public void onCasted() {
        player().stop();
    }

    @Override
    public void onSpeedUp() {
        if (player().isLive()) return;
        if (!player().isPlaying()) return;
        PlaybackAction.startSpeedPress(player(), mBinding.widget.message);
    }

    @Override
    public void onSpeedEnd() {
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
        player().setSpeed(1.0f);
    }

    @Override
    public void onBright(int progress) {
        mBinding.widget.bright.setVisibility(View.VISIBLE);
        mBinding.widget.brightProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_high);
    }

    @Override
    public void onVolume(int progress) {
        mBinding.widget.volume.setVisibility(View.VISIBLE);
        mBinding.widget.volumeProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
    }

    @Override
    public void onFlingUp() {
        if (LiveSetting.isInvert()) nextChannel();
        else prevChannel();
    }

    @Override
    public void onFlingDown() {
        if (LiveSetting.isInvert()) prevChannel();
        else nextChannel();
    }

    @Override
    public void onSeeking(long time) {
        if (player().isLive()) return;
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.time.setText(player().getPositionTime(time));
        mBinding.widget.seek.setVisibility(View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        if (player().isLive()) return;
        seekTo(time);
    }

    @Override
    public void onSingleTap() {
        if (isVisible(mBinding.recycler)) hideUI();
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl();
    }

    @Override
    public void onDoubleTap() {
        onToggle();
    }

    @Override
    public void onTouchEnd() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mBinding.widget.bright.setVisibility(View.GONE);
        mBinding.widget.volume.setVisibility(View.GONE);
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isRedirect()) return;
        if (isLock()) App.post(this::onLock, 500);
        if (service() != null && player().haveTrack(C.TRACK_TYPE_VIDEO)) mPiP.enter(this, player().getVideoWidth(), player().getVideoHeight(), LiveSetting.getScale());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            hideControl();
            hideInfo();
            hideUI();
        } else {
            hideInfo();
            if (isStop()) finish();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Util.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Util.hideSystemUI(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        setAudioOnly(false);
        setStop(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isAudioOnly()) setStop(true);
    }

    @Override
    protected void onBackInvoked() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.info)) {
            hideInfo();
        } else if (isVisible(mBinding.recycler)) {
            hideUI();
        } else if (!isLock()) {
            if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        Source.get().exit();
        App.removeCallbacks(mR1, mR2, mR3);
        super.onDestroy();
    }
}
