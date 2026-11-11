package com.firefly.tv.ui.activity;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.firefly.tv.ui.custom.GlassEdgeDrawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerSeekView;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.ChangeBounds;
import androidx.transition.TransitionManager;
import androidx.core.view.WindowCompat;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.firefly.tv.App;
import com.firefly.tv.Constant;
import com.firefly.tv.R;
import com.firefly.tv.api.DanmakuApi;
import com.firefly.tv.api.SiteApi;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Danmaku;
import com.firefly.tv.bean.DetailBg;
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
import com.firefly.tv.databinding.DialogDescBinding;
import com.firefly.tv.db.AppDatabase;
import com.firefly.tv.event.CastEvent;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.impl.CustomTarget;
import com.firefly.tv.model.VideoViewModel;
import com.firefly.tv.playback.PlaybackAction;
import com.firefly.tv.playback.PlaybackIntent;
import com.firefly.tv.playback.PlaybackOrientation;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.firefly.tv.ui.adapter.EpisodeAdapter;
import com.firefly.tv.ui.adapter.FlagAdapter;
import com.firefly.tv.ui.adapter.QualityAdapter;
import com.firefly.tv.ui.adapter.QuickAdapter;
import com.firefly.tv.ui.base.ViewType;
import com.firefly.tv.ui.custom.CustomKeyDown;
import com.firefly.tv.ui.custom.CustomMovement;
import com.firefly.tv.ui.custom.SpaceItemDecoration;
import com.firefly.tv.ui.dialog.CastDialog;
import com.firefly.tv.ui.dialog.ChapterDialog;
import com.firefly.tv.ui.dialog.ControlDialog;
import com.firefly.tv.ui.dialog.DanmakuDialog;
import com.firefly.tv.ui.dialog.EditionDialog;
import com.firefly.tv.ui.dialog.EpisodeGridDialog;
import com.firefly.tv.ui.dialog.EpisodeListDialog;
import com.firefly.tv.ui.dialog.InfoDialog;
import com.firefly.tv.ui.dialog.ParseDialog;
import com.firefly.tv.ui.dialog.PlayerEngineDialog;
import com.firefly.tv.ui.dialog.ReceiveDialog;
import com.firefly.tv.ui.dialog.SpeedSettingDialog;
import com.firefly.tv.ui.dialog.TrackDialog;
import com.firefly.tv.utils.Clock;
import com.firefly.tv.utils.FileChooser;
import com.firefly.tv.utils.ImgUtil;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.PiP;
import com.firefly.tv.utils.ResUtil;
import com.firefly.tv.utils.Sniffer;
import com.firefly.tv.utils.Timer;
import com.firefly.tv.utils.Traffic;
import com.firefly.tv.utils.UrlUtil;
import com.firefly.tv.utils.Util;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class VideoActivity extends PlaybackActivity implements Clock.Callback, CustomKeyDown.Listener, ControlDialog.Listener, ParseDialog.Listener, VodPlaybackHost, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener {

    private ActivityVideoBinding mBinding;
    private VideoViewModel mViewModel;
    private VodPlaybackController mVod;
    private FlagAdapter mFlagAdapter;
    private boolean sourceMode;
    private boolean mAutoSource;
    private Vod mVodDetail;
    private final Set<String> mSourceKeys = new HashSet<>();
    private EpisodeAdapter mEpisodeAdapter;
    private QualityAdapter mQualityAdapter;
    private QuickAdapter mQuickAdapter;
    private ViewGroup.LayoutParams mFrameParams;
    private ValueAnimator mAnimator;
    private CustomKeyDown mKeyDown;
    private Clock mClock;
    private PiP mPiP;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private History mHistory;
    private boolean fullscreen;
    private boolean useParse;
    private boolean rotate;

    public static void push(FragmentActivity activity, String text) {
        Uri uri = UrlUtil.uri(text);
        if (FileChooser.isValid(activity, uri)) file(activity, FileChooser.getPathFromUri(uri));
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        start(activity, SiteApi.PUSH, "file://" + path, name);
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic());
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true);
    }

    public static void start(Activity activity, String url) {
        start(activity, SiteApi.PUSH, url, url);
    }

    public static void start(Activity activity, String key, String id, String name) {
        start(activity, key, id, name, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect) {
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("collect", collect);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
    }

    /** β91 结构批（A 裁路线一）：本重载承接原详情页 source() 那条入口＝进页即进换源态（键 autoSource） */
    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean autoSource) {
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("autoSource", autoSource);
        intent.putExtra("collect", collect);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
    }

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

    private boolean isAutoRotate() {
        return Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    private boolean isLand() {
        return mBinding.getRoot().getTag().equals("land");
    }

    private boolean isPort() {
        return mBinding.getRoot().getTag().equals("port");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
    }

    /** β91 结构批（A 裁 Q11 甲）：合并页留常驻返回条＝把工具条挂成 action bar，标题留空＝片名仍只在原位出现一次 */
    @Override
    public void setSupportActionBar(@Nullable androidx.appcompat.widget.Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    /** Up＝finish（合并页仍是从来源页进来的二级页；BACK 的首页兜底见 onBackInvoked，两者不互相覆盖） */
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
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
        checkLand();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (TextUtils.isEmpty(intent.getStringExtra("id")) || isSameVideo(intent)) return;
        mBinding.swipeLayout.setRefreshing(true);
        saveHistory(true);
        mVod.reset();
        mSourceKeys.clear();
        setIntent(intent);
        updateNavigationKey();
        checkControl();
        setOrient();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> setStatusBar(insets));
        mKeyDown = CustomKeyDown.create(this, mBinding.player);
        mFrameParams = mBinding.video.getLayoutParams();
        mAutoSource = getIntent().getBooleanExtra("autoSource", false);
        mBinding.progressLayout.showProgress();
        mBinding.swipeLayout.setEnabled(false);
        mClock = Clock.create();
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::setOrient;
        mR4 = this::showEmpty;
        mPiP = new PiP();
        checkDanmakuImg();
        setRecyclerView();
        setVideoView();
        setViewModel();
        showProgress();
        setAnimator();
        checkId();
        if (mAutoSource) enterSourceMode();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.name.setOnClickListener(view -> onName());
        mBinding.detailKeep.setOnClickListener(view -> onKeep());
        mBinding.detailPlay.setOnClickListener(view -> playCurrent());
        mBinding.source.setOnClickListener(view -> enterSourceMode());
        // β30 A5：简介›入口弹窗（演员表＋简介）；正文区不弹窗（desc 不再挂点击）
        mBinding.descEntry.setOnClickListener(view -> showDescDialog());
        mBinding.episodeCount.setOnClickListener(view -> onMore());
        // β30 A2：端口态常驻返回（复用 control.back 语义）
        mBinding.videoBack.setOnClickListener(view -> onBack());
        // β26 玻璃扩面：详情页三颗（恒深底 glass_fill）走方向性环，色对＝on_dark 那对，环半径＝短边一半（A 裁甲）
        mBinding.detailPlay.setForeground(new GlassEdgeDrawable(this, R.color.glass_edge_on_dark_bright, R.color.glass_edge_on_dark_dim));
        mBinding.detailKeep.setForeground(new GlassEdgeDrawable(this, R.color.glass_edge_on_dark_bright, R.color.glass_edge_on_dark_dim));
        mBinding.source.setForeground(new GlassEdgeDrawable(this, R.color.glass_edge_on_dark_bright, R.color.glass_edge_on_dark_dim));
        mBinding.credits.setOnClickListener(view -> showDescDialog());
        mBinding.reverse.setOnClickListener(view -> onReverse());
        mBinding.desc.setOnLongClickListener(view -> onCopy());
        mBinding.name.setOnLongClickListener(view -> onChange());
        mBinding.control.back.setOnClickListener(view -> onBack());
        mBinding.control.cast.setOnClickListener(view -> onCast());
        mBinding.control.info.setOnClickListener(view -> onInfo());
        mBinding.control.keep.setOnClickListener(view -> onKeep());
        mBinding.control.play.setOnClickListener(view -> checkPlay());
        mBinding.control.next.setOnClickListener(view -> checkNext());
        mBinding.control.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.setting.setOnClickListener(view -> onSetting());
        mBinding.control.title.setOnLongClickListener(view -> onChange());
        mBinding.control.right.lock.setOnClickListener(view -> onLock());
        mBinding.control.right.rotate.setOnClickListener(view -> onRotate());
        mBinding.control.danmaku.setOnClickListener(view -> onDanmakuShow());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.replay.setOnClickListener(view -> onReplay());
        mBinding.control.action.parse.setOnClickListener(view -> onParse());
        mBinding.control.action.player.setOnClickListener(view -> onPlayer());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.repeat.setOnClickListener(view -> onRepeat());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.control.action.danmaku.setOnClickListener(view -> onDanmaku());
        mBinding.control.action.edition.setOnClickListener(view -> onEdition());
        mBinding.control.action.chapter.setOnClickListener(view -> onChapter());
        mBinding.control.action.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.control.action.getRoot().setOnTouchListener(this::onActionTouch);
        mBinding.swipeLayout.setOnRefreshListener(this::onSwipeRefresh);
    }

    private WindowInsetsCompat setStatusBar(WindowInsetsCompat insets) {
        int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
        ViewGroup.LayoutParams lp = mBinding.statusBar.getLayoutParams();
        lp.height = top;
        mBinding.statusBar.setLayoutParams(lp);
        // β30 A1：状态栏黑底白字（黑底 draws under status bar，图标强制白）
        getWindow().setStatusBarColor(Color.BLACK);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        return insets;
    }

    private void setRecyclerView() {
        mBinding.flag.setHasFixedSize(true);
        mBinding.flag.setItemAnimator(null);
        mBinding.flag.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
        // 换源态入口（§3.9 手机同步：确认态⇄换源态）
        mBinding.episode.setHasFixedSize(true);
        mBinding.episode.setItemAnimator(null);
        mBinding.episode.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this, ViewType.HORI));
        mBinding.quality.setHasFixedSize(true);
        mBinding.quality.setItemAnimator(null);
        mBinding.quality.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
    }

    private void setVideoView() {
        // β32 C1：预览容器恒 16:9（高＝屏宽*9/16），布局时落到 #video 并 requestLayout 强制生效；
        // mFrameParams 供全屏/PiP 恢复复用。内容在固定容器内由 PlayerView resizeMode 适配（letterbox），不随内容伸缩。
        // 覆盖源 changeHeight()（按视频实际比例动画改高）已置 no-op ⇒ 16:9 不再被动画推翻。
        mFrameParams.height = ResUtil.getScreenWidth() * 9 / 16;
        mBinding.video.setLayoutParams(mFrameParams);
        mBinding.video.requestLayout();
        PlayerEngineDialog.setText(mBinding.control.action.player);
        mBinding.control.action.danmaku.setVisibility(DanmakuSetting.isLoad() ? View.VISIBLE : View.GONE);
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mPiP.update(this, view));
    }

    private void setVideoView(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        } else {
            mBinding.video.setLayoutParams(mFrameParams);
        }
    }

    private void setAnimator() {
        mAnimator = new ValueAnimator();
        mAnimator.setInterpolator(new DecelerateInterpolator());
        mAnimator.addUpdateListener(animation -> {
            if (isLand() || isFullscreen() || isInPictureInPictureMode()) return;
            mFrameParams.height = (int) animation.getAnimatedValue();
            mBinding.video.setLayoutParams(mFrameParams);
        });
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
        mVod.onDetailResult(result);
    }

    private void onPlaybackObserved(PlaybackResult<VodPlayRequest> result) {
        mVod.onPlaybackResult(result);
    }

    private void onPreloadObserved(PlaybackResult<VodPlayRequest> result) {
        mVod.onPreloadResult(result);
    }

    private void onSearchObserved(Result result) {
        // β91 结构批（装机单第 21 条保住的那一层）＝去重在页侧拦一次再交给播放控制器：
        // main 层 state.setSources 拿到的就是去重后的列表 ⇒ 页侧源行（renderSources）与自动兜底链（nextSource 会 removeFirstSource 再渲染）吃同一份。
        // 片名筛不在这里重复实现（main 的 VodFallbackPolicy#mismatch 已在做，两者口径不同＝它单向 contains、原详情页双向），落码只加站点键。
        if (result != null) dedupeBySiteKey(result);
        mVod.onSearchResult(result);
    }

    private void dedupeBySiteKey(Result result) {
        List<Vod> items = new ArrayList<>(result.getList());
        items.removeIf(vod -> vod.getSite() == null || vod.getId() == null || !mSourceKeys.add(vod.getSite().getKey()));
        result.setList(items);
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
        mBinding.swipeLayout.setRefreshing(true);
        mBinding.swipeLayout.setEnabled(false);
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        updateNavigationKey();
        player().reset();
        player().stop();
        checkControl();
    }

    @Override
    public void stopPlaybackForRefresh() {
        player().stop();
        player().clear();
        mClock.setCallback(null);
        checkControl();
    }

    @Override
    public void resetPlaybackForError(String msg) {
        mBinding.swipeLayout.setEnabled(true);
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
        mBinding.swipeLayout.setRefreshing(false);
        mBinding.progressLayout.showContent();
        mBinding.name.setText(item.getName());
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
        // β31 A6/A9：线路≥2 才显示，<2 隐藏整组容器（title＋卡随框一起隐）
        boolean show = items.size() >= 2;
        mBinding.flagGroup.setVisibility(show ? View.VISIBLE : View.GONE);
        mFlagAdapter.addAll(items);
    }

    @Override
    public void renderEpisodes(List<Episode> items) {
        setEpisodeAdapter(items);
    }

    @Override
    public void renderFlagSelection(Flag item) {
        notifyItemChanged(mBinding.flag, mFlagAdapter);
        scrollToPosition(mBinding.flag, mFlagAdapter.getPosition());
    }

    @Override
    public void renderEpisodeSelection(Episode item) {
        notifyItemChanged(mBinding.episode, mEpisodeAdapter);
        scrollToPosition(mBinding.episode, mEpisodeAdapter.getPosition());
    }

    @Override
    public void renderReverseEpisodes(List<Episode> items, boolean scroll) {
        setEpisodeAdapter(items);
        if (scroll) scrollToPosition(mBinding.episode, mEpisodeAdapter.getPosition());
    }

    @Override
    public void renderQuality(Result result, boolean visible) {
        mBinding.swipeLayout.setRefreshing(false);
        mQualityAdapter.addAll(result);
        setQualityVisible(visible);
    }

    @Override
    public void renderQualityVisible(boolean visible) {
        setQualityVisible(visible);
    }

    @Override
    public void renderSources(List<Vod> items) {
        // 换源态（§3.9）：fallback 自动聚合出候选源时进入换源态展示
        mQuickAdapter.addAll(items);
        if (!sourceMode) {
            sourceMode = true;
            mBinding.sourceGroup.setVisibility(View.VISIBLE);
        }
        mBinding.quick.setVisibility(mQuickAdapter.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ================= 换源两态（spec §3.9 手机同步） =================

    /** 进入换源态：顶部源选择行+线路选集，可直接选集播放（交互与 TV 端一致，无预览） */
    private void enterSourceMode() {
        if (sourceMode) return;
        List<Site> sites = VodConfig.get().getSites();
        String name = getVodName();
        if (sites == null || sites.size() <= 1 || name == null || name.isEmpty()) {
            Notify.show(getString(R.string.play_change_none));
            return;
        }
        sourceMode = true;
        mBinding.sourceGroup.setVisibility(View.VISIBLE);
        mSourceKeys.clear();
        mQuickAdapter.clear();
        mBinding.quick.setVisibility(View.VISIBLE);
        requestSearch(sites, name);
    }

    /** 返回确认态：收起源选择行（锁定当前源） */
    private void exitSourceMode() {
        sourceMode = false;
        mBinding.sourceGroup.setVisibility(View.GONE);
        mBinding.quick.setVisibility(View.GONE);
        mViewModel.stopSearch();
    }

    @Override
    public void renderHistory(History history) {
        mHistory = history;
        mBinding.control.action.opening.setText(history.getOpening() <= 0 ? getString(R.string.play_op) : Util.timeMs(history.getOpening()));
        mBinding.control.action.ending.setText(history.getEnding() <= 0 ? getString(R.string.play_ed) : Util.timeMs(history.getEnding()));
        player().setSpeed(SpeedSetting.getPlayback());
        setScale(getScale());
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
        // β91：原写法落点是 #content（合并页已废止）。改成"简介为空才顶上"＝不让源侧串顶掉 Vod 简介。
        // β33 A1：同走纯文本路径（不带 span ⇒ ellipsize 生效）。
        if (mBinding.desc.getText().length() == 0) setPlainText(mBinding.desc, desc);
    }

    @Override
    public void renderPlaybackMetadata(MediaMetadata metadata) {
        if (service() != null && isOwner()) player().setMetadata(metadata);
        mBinding.control.title.setText(metadata.displayTitle);
        mBinding.control.title.setSelected(true);
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

    private void checkId() {
        mVod.checkId();
    }

    private void checkLand() {
        if (isPort() && ResUtil.isLand(this)) enterFullscreen();
    }

    private void showEmpty() {
        showError(getString(R.string.error_detail));
        mBinding.swipeLayout.setEnabled(true);
        mBinding.progressLayout.showEmpty();
    }

    private void setText(Vod item) {
        mVodDetail = item;
        // β31 A2/A5：元信息一行＝年·地区·类型·状态（remarks 并入尾部，主色不再红）；原 #score 红字视图已撤。整行全空不占位。
        StringBuilder meta = new StringBuilder();
        if (!item.getYear().isEmpty()) meta.append(item.getYear());
        if (!item.getArea().isEmpty()) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(item.getArea());
        }
        if (!item.getTypeName().isEmpty()) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(item.getTypeName());
        }
        if (!item.getRemarks().isEmpty()) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(item.getRemarks());
        }
        setText(mBinding.meta, 0, meta.toString());
        // 人员行（导演＋演员合并单行，完整名单进简介弹层）；串走 main 那对带参资源，不照抄 leanback 的无参覆盖版
        StringBuilder credits = new StringBuilder();
        if (!item.getDirector().isEmpty()) credits.append(getString(R.string.detail_director, item.getDirector()));
        if (!item.getActor().isEmpty()) {
            if (credits.length() > 0) credits.append(" · ");
            credits.append(getString(R.string.detail_actor, item.getActor()));
        }
        setText(mBinding.credits, 0, credits.toString());
        // 简介（T-180 口径：字面量 &nbsp; 换空格，不引 HTML 解析）。
        // β33 A1：inline desc 改纯文本（setPlainText，不带 ClickableSpan/MovementMethod）⇒ ellipsize=end＋maxLines3 真渲染省略号；
        // 带 span 文本会令 ellipsize 失效（β33 A0 根因）。链接嗅探/复制保留在长按 onCopy 与简介›弹窗（showDescDialog 独立 setText）。
        String content = item.getContent().replace("&nbsp;", " ");
        setPlainText(mBinding.desc, content);
        // 可点态／描述／水波全部动态挂：TalkBack 会用 contentDescription 顶掉文本本身 ⇒ 描述必须＝可见文本＋入口提示
        boolean hasDesc = !item.getContent().isEmpty();
        // β30 A5：简介正文区不弹窗（入口统一到 descEntry／credits），故不挂点击/水波
        mBinding.desc.setClickable(false);
        mBinding.desc.setBackground(null);
        boolean creditsEntry = !hasDesc && credits.length() > 0;
        mBinding.credits.setClickable(creditsEntry);
        mBinding.credits.setContentDescription(creditsEntry ? credits + "，" + getString(R.string.detail_credits_full) : null);
        mBinding.credits.setBackground(creditsEntry ? pressFeedback() : null);
        android.util.Log.d(DetailLog.TAG, "desc entry=" + (hasDesc ? "desc" : (creditsEntry ? "credits" : "none"))
                + " descLen=" + content.length() + " creditsLen=" + credits.length());
        // β30 A1：去底图（#bg/#bgFill 已删），背景三级链随之下线；预览区恒黑底。
    }

    private void setText(TextView view, int resId, String text) {
        if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(view.getText())) return;
        view.setText(Sniffer.buildClickable(resId > 0 ? getString(resId, text) : text, this::clickableSpan), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        view.setLinkTextColor(Color.WHITE);
        CustomMovement.bind(view);
    }

    /** β33 A1：inline 简介专用纯文本路径——不带 ClickableSpan、清 MovementMethod，使 ellipsize=end＋maxLines 渲染省略号。
     *  链接嗅探/复制不在此处（留长按 onCopy 与简介›弹窗 showDescDialog）。meta/credits 仍走上面带 span 的 setText。 */
    private void setPlainText(TextView view, String text) {
        if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(view.getText())) return;
        view.setText(text);
        view.setMovementMethod(null);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private ClickableSpan clickableSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                FolderActivity.start(getActivity(), getKey(), result);
                ((TextView) view).setMaxLines(Integer.MAX_VALUE);
                setRedirect(true);
            }
        };
    }

    private void setOther(TextView view, Vod item) {
        StringBuilder sb = new StringBuilder();
        if (!item.getYear().isEmpty()) sb.append(getString(R.string.detail_year, item.getYear())).append("  ");
        if (!item.getArea().isEmpty()) sb.append(getString(R.string.detail_area, item.getArea())).append("  ");
        // 本地/推送播放（push_agent）时爬虫返回的 type_name 无意义（如上游品牌字样），不展示类型；其他视频保留
        String typeName = com.firefly.tv.api.SiteApi.PUSH.equals(getKey()) ? "" : item.getTypeName();
        if (!typeName.isEmpty()) sb.append(getString(R.string.detail_type, typeName)).append("  ");
        view.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
        view.setText(Util.substring(sb.toString(), 2));
    }

    @Override
    public void onItemClick(Flag item) {
        mVod.selectFlag(item);
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        mVod.selectEpisode(item);
    }

    @Override
    public void onItemClick(Result result) {
        mVod.selectQuality(result);
    }

    @Override
    public void onItemClick(Vod item) {
        mSourceKeys.clear();
        mVod.selectSource(item);
    }

    private void setEpisodeAdapter(List<Episode> items) {
        mBinding.control.action.episodes.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.next.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.prev.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.reverse.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        // β31 A9：选集整组容器 <2 集隐藏（同线路 renderFlags 口径）
        mBinding.episodeGroup.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        // β30 A7：共N集›仅>5 显示（阈值由≥10 改>5），点击弹 EpisodeGridDialog；独立 more 钮已撤
        boolean many = items.size() > 5;
        mBinding.episodeCount.setVisibility(many ? View.VISIBLE : View.GONE);
        if (many) mBinding.episodeCount.setText(getString(R.string.detail_episode_total, items.size()));
        mEpisodeAdapter.addAll(items);
    }

    private void setQualityVisible(boolean visible) {
        mBinding.qualityText.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void onName() {
        String name = mBinding.name.getText().toString();
        Notify.show(getString(R.string.detail_search, name));
        mVod.search(name);
    }

    private void onMore() {
        EpisodeGridDialog.create().reverse(mHistory.isRevSort()).episodes(mEpisodeAdapter.getItems()).show(this);
    }

    /** 三钮里那颗"播放"＝播当前选中集（沿用原详情页 playCurrent 语义；空列表早退，不碰播放控制器） */
    private void playCurrent() {
        if (mEpisodeAdapter.isEmpty()) return;
        onItemClick(mEpisodeAdapter.getItems().get(mEpisodeAdapter.getPosition()));
    }

    /** 简介弹层（原详情页搬入）＝人员全文＋简介全文，人员段在前、空一行；两者皆空直接早退。β30 A5＝简介›入口复用此弹层（含演员表）。 */
    private void showDescDialog() {
        if (mVodDetail == null) return;
        String content = mVodDetail.getContent().replace("&nbsp;", " ");
        StringBuilder head = new StringBuilder();
        if (!mVodDetail.getDirector().isEmpty()) head.append(getString(R.string.detail_director, mVodDetail.getDirector()));
        if (!mVodDetail.getActor().isEmpty()) {
            if (head.length() > 0) head.append(" · ");
            head.append(getString(R.string.detail_actor, mVodDetail.getActor()));
        }
        if (content.isEmpty() && head.length() == 0) return;
        String full;
        if (head.length() == 0) full = content;
        else if (content.isEmpty()) full = head.toString();
        else full = head + "\n\n" + content;
        DialogDescBinding vb = DialogDescBinding.inflate(getLayoutInflater());
        vb.descFull.setText(full);
        new MaterialAlertDialogBuilder(this).setTitle(mBinding.name.getText()).setView(vb.getRoot())
                .setPositiveButton(R.string.dialog_positive, null).show();
    }

    /** 按压水波＝主题 selectableItemBackground；resolveAttribute 未命中时 outValue.resourceId 仍是 0 ⇒ 判返回值，未命中返回 null（＝没有水波，不崩） */
    private android.graphics.drawable.Drawable pressFeedback() {
        android.util.TypedValue tv = new android.util.TypedValue();
        if (!getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) return null;
        return getDrawable(tv.resourceId);
    }

    private void onReverse() {
        mVod.setRevSort(!mHistory.isRevSort());
        mVod.reverseEpisode(false);
    }

    private boolean onChange() {
        mVod.manualSwitchSource();
        return true;
    }

    private boolean onCopy() {
        Util.copy(mBinding.desc.getText().toString());
        return true;
    }

    private void onBack() {
        if (isFullscreen()) exitFullscreen();
        else finish();
    }

    private void onCast() {
        CastDialog.create(player()).history(mHistory).show(this);
    }

    private void onInfo() {
        InfoDialog.create(player()).show(this);
    }

    private void onKeep() {
        Keep keep = Keep.find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        checkKeepImg();
    }

    private void checkPlay() {
        setR1Callback();
        if (player().isPlaying()) onPaused();
        else if (player().isEmpty()) onRefresh();
        else onPlay();
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        setR1Callback();
        mVod.nextEpisode(notify);
    }

    private void checkPrev() {
        setR1Callback();
        mVod.prevEpisode(true);
    }

    private void onSetting() {
        ControlDialog.create().parent(mBinding).parse(isUseParse()).player(player()).show(this);
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

    private void onDanmakuShow() {
        DanmakuSetting.putShow(!DanmakuSetting.isShow());
        checkDanmakuImg();
        syncDanmakuEnabled();
    }

    private void onRepeat() {
        player().setRepeatOne(!player().isRepeatOne());
        setRepeatVisual(player().isRepeatOne());
    }

    /**
     * Round 38 / R5（P6：TV 全改 + mobile 同批改）：开关类（循环播放）的选中表达收进一个方法，
     * 与 leanback 侧 VideoActivity.setRepeatVisual 同口径——selected 与加粗必须成对，漏一处就"半更新"。
     * mobile 的控制栏没有状态背景（@style/Control 的 background 是 selectableItemBackgroundBorderless），
     * 选中本来**没有任何可见表达**，加粗是本轮给它补上的唯一表达；不新建 mobile 状态 drawable
     * （对照表 #16：mobile 沿用其既有语言=state-list 文字色，不重建 TV 那套焦点/描边）。
     * 加粗同样走 ResUtil.setSwitchBold（双端共享 util，见其注释：TextView 上没有 setFakeBoldText）。
     */
    private void setRepeatVisual(boolean on) {
        mBinding.control.action.repeat.setSelected(on);
        ResUtil.setSwitchBold(mBinding.control.action.repeat, on);
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        setRepeatVisual(player().isRepeatOne());
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        if (mKeyDown.getScale() != 1.0f) mKeyDown.resetScale();
        else setScale(index == array.length - 1 ? 0 : ++index);
        setR1Callback();
    }

    private void onSpeed() {
        SpeedSettingDialog.create().player(player()).save(true).show(this);
        hideControl();
    }

    private boolean onSpeedLong() {
        SpeedSetting.putPlayback(PlaybackAction.toggleSpeed(player(), mBinding.widget.message));
        setR1Callback();
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

    private void onDecode() {
        mClock.setCallback(null);
        player().toggleDecode();
        setR1Callback();
    }

    private void onEnding() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetEnding(position, duration)) setEnding(duration - position);
        setR1Callback();
    }

    private boolean onEndingReset() {
        setR1Callback();
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        mVod.setEnding(ending);
        mBinding.control.action.ending.setText(ending <= 0 ? getString(R.string.play_ed) : Util.timeMs(mHistory.getEnding()));
    }

    private void onOpening() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) setOpening(position);
        setR1Callback();
    }

    private boolean onOpeningReset() {
        setR1Callback();
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        mVod.setOpening(opening);
        mBinding.control.action.opening.setText(opening <= 0 ? getString(R.string.play_op) : Util.timeMs(mHistory.getOpening()));
    }

    private void onEpisodes() {
        EpisodeListDialog.create().episodes(mEpisodeAdapter.getItems()).show(this);
    }

    private void onPlayer() {
        PlayerEngineDialog.show(this, mBinding.control.action.player, player());
        hideControl();
    }

    private boolean onActionTouch(View v, MotionEvent e) {
        setR1Callback();
        return false;
    }

    private void onSwipeRefresh() {
        if (mBinding.progressLayout.isEmpty()) mVod.requestDetail();
        else mVod.refresh();
    }

    private boolean shouldEnterFullscreen(Episode item) {
        boolean enter = !isFullscreen() && item.isSelected();
        if (enter) enterFullscreen();
        return enter;
    }

    private void enterFullscreen() {
        if (isFullscreen()) return;
        setFullscreen(true);
        if (isLand() && !player().isPortrait()) setTransition();
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        setRequestedOrientation(PlaybackOrientation.getEnterFullscreenOrientation(player().isPortrait()));
        mBinding.control.title.setVisibility(View.VISIBLE);
        setRotate(player().isPortrait());
        mKeyDown.resetScale();
        App.post(mR3, 2000);
        hideControl();
    }

    private void exitFullscreen() {
        if (!isFullscreen()) return;
        setFullscreen(false);
        if (isLand() && !player().isPortrait()) setTransition();
        setRequestedOrientation(PlaybackOrientation.getExitFullscreenOrientation(isPort()));
        mBinding.episode.postDelayed(() -> mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition()), 100);
        mBinding.control.title.setVisibility(View.INVISIBLE);
        mBinding.video.setLayoutParams(mFrameParams);
        mKeyDown.resetScale();
        App.post(mR3, 2000);
        setRotate(false);
        hideControl();
    }

    private void setTransition() {
        ChangeBounds transition = new ChangeBounds();
        transition.setDuration(150);
        ViewGroup parent = (ViewGroup) mBinding.video.getParent();
        TransitionManager.beginDelayedTransition(parent, transition);
    }

    private int getLockOrient() {
        return PlaybackOrientation.getLockOrientation(this, isLock(), isRotate(), isPort() && isAutoRotate());
    }

    private void showProgress() {
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

    private void syncDanmakuEnabled() {
        player().setDanmakuEnabled(DanmakuSetting.isShow() && DanmakuSetting.isLoad());
    }

    private void hideDanmaku() {
        player().setDanmakuEnabled(false);
    }

    private void showControl() {
        if (service() == null || isInPictureInPictureMode()) return;
        mBinding.control.danmaku.setVisibility(isLock() || !player().haveDanmaku() || !DanmakuSetting.isLoad() ? View.GONE : View.VISIBLE);
        mBinding.control.setting.setVisibility(mHistory == null || isFullscreen() ? View.GONE : View.VISIBLE);
        mBinding.control.right.rotate.setVisibility(isFullscreen() && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.keep.setVisibility(mHistory == null || isFullscreen() ? View.GONE : View.VISIBLE);
        mBinding.control.action.getRoot().setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.right.lock.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.action.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
        mBinding.control.info.setVisibility(player().isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.cast.setVisibility(player().isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.center.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.bottom.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.back.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        // β30 A2：端口非全屏＝常驻 videoBack 承担返回，控制层 back 隐藏避免双返回；全屏/横屏＝control.back 承担
        boolean portBack = isPort() && !isFullscreen();
        mBinding.videoBack.setVisibility(portBack && !isLock() ? View.VISIBLE : View.GONE);
        if (portBack) mBinding.control.back.setVisibility(View.GONE);
        mBinding.control.top.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        setR1Callback();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        // β30 A2：控制层收起时端口态返回仍常驻
        mBinding.videoBack.setVisibility(isPort() && !isFullscreen() && !isLock() ? View.VISIBLE : View.GONE);
        App.removeCallbacks(mR1);
    }

    private void dismissDialogs() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) if (fragment instanceof DialogFragment dialog) dialog.dismiss();
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.progress.traffic);
        App.post(mR2, 1000);
    }

    private void setOrient() {
        if (isPort() && isAutoRotate()) setRequestedOrientation(PlaybackOrientation.getPortAutoRotateOrientation());
        if (isLand() && isAutoRotate()) setRequestedOrientation(PlaybackOrientation.getLandAutoRotateOrientation());
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

    private void setArtwork(String url) {
        // T-150（拍板 48）：与 TV 端同源缺陷同修（A 裁决 D-4=手机代码同批改）——
        // 播放解析返回的 artwork（网盘 LOGO）只用于播放器封面显示，不再写进 History.vodPic；
        // 仅当锁定图确实为空时才允许补位落库，否则手机收藏/历史同样会被 LOGO 永久覆盖。
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

    private void saveHistory(boolean exit) {
        PlaybackService service = service();
        boolean owner = service != null && getPlaybackKey().equals(service.player().getKey());
        long position = owner ? service.player().getPosition() : C.TIME_UNSET;
        long duration = owner ? service.player().getDuration() : C.TIME_UNSET;
        if (mVod != null) mVod.saveHistory(exit, System.currentTimeMillis(), position, duration);
    }

    private void checkControl() {
        if (isVisible(mBinding.control.getRoot())) showControl();
    }

    private void checkKeepImg() {
        int res = Keep.find(getHistoryKey()) == null ? R.drawable.ic_control_keep_off : R.drawable.ic_control_keep_on;
        mBinding.control.keep.setImageResource(res);
        mBinding.detailKeep.setImageResource(res);
    }

    private void checkLockImg() {
        mBinding.control.right.lock.setImageResource(isLock() ? R.drawable.ic_control_lock_on : R.drawable.ic_control_lock_off);
    }

    private void checkDanmakuImg() {
        mBinding.control.danmaku.setImageResource(DanmakuSetting.isShow() ? R.drawable.ic_control_danmaku_on : R.drawable.ic_control_danmaku_off);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        // T-150（拍板 48）：收藏图优先用进入播放页带入的列表锁定图（intent "pic"），避免 artwork(LOGO) 落库
        keep.setVodPic(TextUtils.isEmpty(getPic()) ? mHistory.getVodPic() : getPic());
        keep.setVodName(mHistory.getVodName());
        keep.setSiteName(getSite().getName());
        // β18 / T-213：与 TV 侧 createKeep 同一写法（更新进度取 mHistory）
        keep.setVodRemarks(mHistory.getVodRemarks() == null ? "" : mHistory.getVodRemarks());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    private void updateKeep() {
        Keep keep = Keep.find(getHistoryKey());
        if (keep != null) {
            keep.setVodName(mHistory.getVodName());
            // T-150（拍板 48）：同 createKeep，收藏刷新也只用列表锁定图
            keep.setVodPic(TextUtils.isEmpty(getPic()) ? mHistory.getVodPic() : getPic());
            // β18 / T-213：与 TV 侧 updateKeep 同一写法
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

        @Override
        public void onAudio() {
            Util.moveToBackground(VideoActivity.this);
            setAudioOnly(true);
        }
    };

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

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
                checkControl();
                player().reset();
                mClock.setCallback(this);
                break;
            case Player.STATE_ENDED:
                hideProgress();
                setR1Callback();
                mVod.playbackEnded();
                updatePlayControl(false);
                mClock.setCallback(null);
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
    protected void onSizeChanged(VideoSize size) {
        changeHeight();
        checkOrientation();
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
    public void onCastEvent(CastEvent event) {
        if (isRedirect()) return;
        ReceiveDialog.create().event(event).show(this);
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

    private void checkOrientation() {
        if (isFullscreen() && !isRotate() && player().isPortrait()) {
            setRequestedOrientation(PlaybackOrientation.getPortraitVideoSizeOrientation());
            setRotate(true);
        } else if (isFullscreen() && isRotate() && player().isLandscape()) {
            setRequestedOrientation(PlaybackOrientation.getLandscapeVideoSizeOrientation());
            setRotate(false);
        }
    }

    private void changeHeight() {
        // β32 C1：预览容器恒 16:9（setVideoView 已落 屏宽*9/16＋requestLayout），不再按视频实际比例动画改高。
        // 改前此处用 ValueAnimator 把 mFrameParams.height 动画到 videoHeight/videoWidth 比例（clamp 150dp~屏高/2），
        // 正是它把 setVideoView 的 16:9 推翻成≈视频原始比例（刘先生实测≈2.4:1）。现置 no-op：内容在固定 16:9 容器内由
        // PlayerView resizeMode 适配（letterbox），占位不随内容伸缩。全屏/PiP 仍走 setVideoView(boolean)/enterFullscreen/exitFullscreen。
        // 配套 ValueAnimator（setAnimator/mAnimator）随本行为休眠（不再 start）；如需清死码另开收尾批。回滚＝恢复本方法体（git 2d7910b）。
    }

    private void setTrackVisible() {
        PlaybackAction.setTracks(player(), mBinding.control.action.text, mBinding.control.action.audio, mBinding.control.action.video);
    }

    private void setMediaOptionVisible() {
        PlaybackAction.setMediaOptions(player(), mBinding.control.action.edition, mBinding.control.action.chapter);
    }

    private void onPaused() {
        controller().pause();
    }

    private void onPlay() {
        if (mHistory != null && isEnded()) controller().seekTo(mHistory.getOpening());
        if (!player().isEmpty() && isIdle()) controller().prepare();
        controller().play();
    }

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        Util.toggleFullscreen(this, this.fullscreen = fullscreen);
    }

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
        if (fullscreen && !rotate) setPadding(mBinding.control.getRoot());
        else noPadding(mBinding.control.getRoot());
    }

    private void notifyItemChanged(RecyclerView view, RecyclerView.Adapter<?> adapter) {
        view.post(() -> adapter.notifyItemRangeChanged(0, adapter.getItemCount()));
    }

    private void scrollToPosition(RecyclerView view, int position) {
        view.post(() -> view.scrollToPosition(position));
    }

    @Override
    public void onCasted() {
        player().stop();
    }

    @Override
    public void onScale(int tag) {
        mKeyDown.resetScale();
        setScale(tag);
    }

    @Override
    public void onParse(Parse item) {
        mVod.selectParse(item);
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
        if (mEpisodeAdapter.getItemCount() == 1) onRefresh();
        else mVod.nextEpisode(true);
    }

    @Override
    public void onFlingDown() {
        if (mEpisodeAdapter.getItemCount() == 1) onRefresh();
        else mVod.prevEpisode(true);
    }

    @Override
    public void onSeeking(long time) {
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.time.setText(player().getPositionTime(time));
        mBinding.widget.seek.setVisibility(View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        seekTo(time);
    }

    @Override
    public void onSingleTap() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl();
    }

    @Override
    public void onDoubleTap() {
        if (isLock()) return;
        // β30 A4：双击进全屏／全屏态双击返回播放页（补「双击全屏不能回」）
        if (!isFullscreen()) enterFullscreen();
        else exitFullscreen();
    }

    @Override
    public void onTouchEnd() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mBinding.widget.bright.setVisibility(View.GONE);
        mBinding.widget.volume.setVisibility(View.GONE);
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1001) PlaybackIntent.onExternalResult(data, service()::dispatchNext, controller()::seekTo);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isRedirect()) return;
        if (isLock()) App.post(this::onLock, 500);
        if (service() != null && player().haveTrack(C.TRACK_TYPE_VIDEO)) mPiP.enter(this, player().getVideoWidth(), player().getVideoHeight(), getScale());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig);
        if (!isBindingOwner()) return;
        if (inPictureInPictureMode) onEnterPictureInPicture();
        else onExitPictureInPicture();
    }

    private void onEnterPictureInPicture() {
        if (!isFullscreen()) setVideoView(true);
        dismissDialogs();
        hideControl();
        hideDanmaku();
    }

    private void onExitPictureInPicture() {
        if (!isFullscreen()) setVideoView(false);
        syncDanmakuEnabled();
        if (isStop()) finish();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isAutoRotate() && isPort() && newConfig.orientation == Configuration.ORIENTATION_PORTRAIT && !isRotate() && !isLock()) exitFullscreen();
        if (isAutoRotate() && isPort() && newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) enterFullscreen();
        if (isFullscreen()) Util.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (isFullscreen() && hasFocus && !Util.isFullscreen(this)) Util.hideSystemUI(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
        setAudioOnly(false);
        setStop(false);
    }

    // 注意：不可在 onResume 里调用 syncDanmakuEnabled——此时 PlaybackService 尚未完成绑定初始化
    // （内部 PlayerManager 为 null，读取即 NPE，导致点播必崩，见 beta6 事故）；弹幕状态在
    // showControl/切集等 service 就绪后的既有节点同步。

    @Override
    protected void onStop() {
        super.onStop();
        saveHistory(false);
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
        if (!isAudioOnly()) setStop(true);
    }

    @Override
    protected void onBackInvoked() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (sourceMode) {
            // 换源态返回=回确认态（§3.9 两态，锁定当前源）
            exitSourceMode();
        } else if (isFullscreen() && !isLock()) {
            exitFullscreen();
        } else if (!isLock()) {
            mViewModel.stopSearch();
            if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        mClock.release();
        saveHistory(true);
        Timer.get().reset();
        DanmakuApi.cancel();
        RefreshEvent.keep();
        App.removeCallbacks(mR1, mR2, mR3, mR4);
        super.onDestroy();
    }
}
