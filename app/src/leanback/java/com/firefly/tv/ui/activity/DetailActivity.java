package com.firefly.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.TextureView;
import android.view.ViewTreeObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.firefly.tv.R;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.DetailBg;
import com.firefly.tv.bean.DetailLog;
import com.firefly.tv.bean.Episode;
import com.firefly.tv.bean.FavLog;
import com.firefly.tv.bean.Flag;
import com.firefly.tv.bean.History;
import com.firefly.tv.bean.Keep;
import com.firefly.tv.bean.Result;
import com.firefly.tv.bean.Site;
import com.firefly.tv.bean.Vod;
import com.firefly.tv.databinding.ActivityDetailBinding;
import com.firefly.tv.databinding.AdapterDetailEpisodeBinding;
import com.firefly.tv.databinding.AdapterDetailFlagBinding;
import com.firefly.tv.databinding.AdapterSourceBinding;
import com.firefly.tv.databinding.DialogDetailDescBinding;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.model.SiteViewModel;
import com.firefly.tv.player.exo.ExoMediaSourceFactory;
import com.firefly.tv.player.media.MediaItemFactory;
import com.firefly.tv.player.media.PlaySpec;
import com.firefly.tv.setting.Setting;   // β19 / T-313：点播预览开关
import com.firefly.tv.api.SiteApi;
import com.firefly.tv.ui.base.BaseActivity;
import android.graphics.drawable.BitmapDrawable;
import com.firefly.tv.utils.ResUtil;   // β19 / T-311：换源列表的行内间距
import com.firefly.tv.utils.PosterWash;
import com.firefly.tv.utils.ImgUtil;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.Task;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TV 2.0 详情页（1.3.0-beta2，规格 tv-2.0-ui-spec.md §3.3 定稿）。
 * 单页两态：确认态（线路+选集）/ 换源态（顶部源选择行+线路+选集）。
 * 层级导航（v1.8/v1.9）：源行(≥2 源)/线路行(≥2 线路)/集数网格，单选项层隐藏；
 * 焦点在源/线路行=行内切值（去抖，下层跟随刷新，零预览请求）；OK=焦点下移；集数 OK=播放。
 * 背景锁定海报：换源永不刷新（拍板 11）。
 * 背景静音预览（v1.9）列入 beta3 与直播预览统一接入。
 */
public class DetailActivity extends BaseActivity {

    // T-301⑤（A 派单：源层下线）：确认态与换源态两枚状态常量随换源态一并删除，页面只余确认态。
    // SWITCH_DEBOUNCE 保留——线路行「聚焦去抖切线路」仍在用（源行那条 500ms 去抖已整段下线）。
    private static final long SWITCH_DEBOUNCE = 300;
    // β17 / T-301②：线路行选中态的局部刷新 payload（配合「关 change 动画」才保得住焦点，见 FlagAdapter#setSelected）
    private static final String PAYLOAD_SELECTION = "flag-selection";

    // T-184（Round 39）建，β8 / 海报改：文字区判据与遮罩参数**必须与 res/drawable/shape_detail_scrim.xml 逐值对齐**
    // （证据行 dim 是用这组数算出来的，改 drawable 不改常量 = 日志失真）。
    // β8 水平档：左 #E6 → 全透明，止于屏宽 58%（旧三段 E0/CC/80 作废 ⇒ SCRIM_MID_A / SCRIM_RIGHT_A 删除）。
    private static final float SCRIM_LEFT_A = 0xE6 / 255f;
    private static final float SCRIM_STOP_FRACTION = 0.58f;
    private static final float SCRIM_BOTTOM_A = 0x99 / 255f;
    private static final float SCRIM_BOTTOM_DP = 120f;
    // β8 / 海报（A 裁定三条）：竖版时背景层退化为“同图模糊 ＋ 逐像素压暗 50%”的延展底。
    // **不再用 ImageView.setAlpha**：alpha 是与页面底色混合（整体发灰），乘算是把图本身压暗（才是效果图那种层次）。
    // 旧 FILL_DIM_ALPHA=0.30（T-187-b 的“铺底层强压暗”）随本批下线——它与新写法是同一位置的两种解法，不并存。
    // β11 / 组B 三轮（A 定位＝「问题不是过渡太窄，是背景内容与主体不同源」⇒ 改成接缝连续）：
    // 延展层不再模糊，改成「主体左缘那一列横向拉伸」，纵向映射与主体层完全一致 ⇒ 接缝处背景值＝主体首列值（构造性相等）。
    // 旧的 BLUR_*（同图 centerCrop 到 480×270 → 3× 盒式模糊 → ×0.50 压暗）与 FEATHER_*（接缝居中 32px／右覆反向渐隐 96px 的羽化带）
    // **整块下线**，连带 BlurUtil.java 与模糊图磁盘缓存（PNG＋LRU 50）一起删；理由与算式见 PosterWash 类注释与本轮 INDEX。
    private static final int WASH_WIDTH = 96;             // 延展小图列数（水平方向是常数，96 列足够表达斜坡；每列≈20 屏幕像素）
    private static final int WASH_PLATEAU_PX = 96;        // 贴接缝的全亮平台：k≡1，连续性的来源（这一段一压暗就破 ≤2/255）
    private static final int WASH_RAMP_PX = 320;          // 平台向左降到 kMin 的斜坡宽（推导：Δk≈0.6 摊到 320px≈0.002/px，不构成单列峰值）
    private static final float WASH_TARGET_RATIO = 0.50f; // 左区/全图 平均亮度目标（判据上限 0.55，留一档余量）
    // 判据文字区（A 第二节 = 1920×1080 上的 x≤1400、y320–900），按视图尺寸等比折算，非 1080p 也不跑偏
    private static final float TEXT_RIGHT_FRACTION = 1400f / 1920f;
    private static final float TEXT_TOP_FRACTION = 320f / 1080f;
    private static final float TEXT_BOTTOM_FRACTION = 900f / 1080f;
    // 缩样采样：长边 ≤96、网格步长 20px（1080p 文字区约 70×29=2030 点，主线程一次 <1ms 量级）
    private static final int SAMPLE_MAX = 96;
    private static final int SAMPLE_STEP = 20;

    private ActivityDetailBinding mBinding;
    private SiteViewModel mViewModel;
    // T-301⑤：顶部可切换源行的适配器随源层下线删除
    // T-301⑤：底部只读源行的适配器随源层下线删除
    private FlagAdapter mFlagAdapter;
    // β19 / T-311：页内换源列表（数据仍是 mSourceList，不新增网络请求）；两个状态位只服务"展开/收起"
    private SourceRowAdapter mSourceRowAdapter;
    private boolean mSourceRowOpen;
    // β20 / T-322：选集单行的焦点记忆位（-1=无记忆）。只由选集卡焦点监听写入（C3：只记录不抢焦），
    // 换线路/换源重绑数据时在 selectFlag/switchSource 链里复位为 -1（新数据旧位置无意义）。
    private int mEpisodeFocusPos = -1;
    // β20 / T-321：简介全文弹层的单实例闸（连点 OK 不叠第二层）。
    private boolean mDescDialogOpen;
    private EpisodeAdapter mEpisodeAdapter;
    private Vod mVideo;
    private String mKey;
    private String mId;
    private String mName;
    private String mPic;
    // T-301④：切源前记下的「当前集名」，供新源 bind() 做同名优先保持；取用一次即清（不污染非换源的 bind）。
    private String mKeepEpisodeName;
    private final List<Vod> mSourceList = new ArrayList<>();
    private final Set<String> mSourceKeys = new HashSet<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    // β13 / T-221（A 裁定"主修＋诊断一起上"）：那根"进入后约 1 秒消失"的偏白竖线有两个候选 x——
    // 1816（＝源行右边界，1920−52dp，本机 density 2.0）与 1138（海报接缝）。下面三组探针把首帧与 +1200ms
    // 的同三个量各打一行，一次看死：过滚动模式＋各滚动区右界（假设①）、预览指示可见性（假设②）、焦点落点（假设③）。
    private static final long EDGE_PROBE_LATE_MS = 1200;
    private Runnable mEdgeProbeLate;
    private boolean mEdgeProbeDone;
    private Runnable mFlagPending;
    private Runnable mSourcePending;
    // 实时预览（spec §3.3 v1.9）：焦点驱动，源/线路行零请求
    private ExoPlayer mPreviewPlayer;
    private Episode mPreviewEpisode;
    // T-302：预览保比例居中用的变换矩阵（TextureView.setTransform 专用，不新建视图、不改尺寸）
    private final Matrix mPreviewMatrix = new Matrix();
    private boolean mPreviewFitRetried;
    private int mPreviewToken;
    private Runnable mPreviewPending;
    private boolean mBoundOnce;
    private boolean mReturnedFromPlayer;
    // T-178（Round 37 真机回归）：首屏初始焦点兜底状态——tries 限制重试帧数，rescued 保证"窗口抢焦"只纠正一次
    private int mInitFocusTries;
    private boolean mInitFocusRescued;
    // T-178 兜底闸门：用户一旦按键（OK/方向/返回…），初始焦点纠正永久停止，绝不与用户抢焦点
    private boolean mKeySeen;
    // T-146（D-1 甲）：跨站点源搜索**全生命周期只发一次**的幂等标记（进页面即后台触发，
    // 换源弹窗与页面状态日志共用同一份名单，不重复请求、不阻塞主流程）
    private boolean mSourceSearched;
    // T-301⑤：原「名单未就绪先暂存焦点、喂完再落当前源卡」的挂起标记随换源态整段下线删除。
    // T-301③：源名单三态（loading＝名单未回、none＝无结果、only＝单站点）；只用于弹窗跳过原因与状态日志，不再驱动占位文案
    private static final int SOURCE_LOADING = 0;
    private static final int SOURCE_NONE = 1;
    private static final int SOURCE_ONLY = 2;
    private int mSourceState = SOURCE_LOADING;
    private final ExecutorService mPreviewExecutor = Executors.newSingleThreadExecutor();
    // β8 / 海报 P3：背景层状态（证据行要按“屏上此刻到底是哪张底图”来算，不能只按取图档位算）。
    private Drawable mBgResource;        // 主体层那张 Drawable（横竖版都一样），背景层变化后要重算证据行
    private Bitmap mBgWash;             // β11 竖版：屏上背景层实际显示的延展小图（主体左缘列横向拉伸＋逐列亮度斜坡）；横版＝null
    private boolean mBgSlide;            // 本次背景档位（true=横版真图直铺）
    private boolean mBgSlideFirst;       // β25/T-333：当前背景请求＝slide 首试（失败⇒探测 pic 降级一次）
    private String mFillTag = "pending"; // 证据行 fill= 取值：slideCrop / pending / wash / washNa（β11 起 blur50／rawCrop／cache 下线）
    private int mWashGen;                  // β11：取图代际号——换源后旧的后台延展结果必须作废（否则旧列贴到新图上）
    private Runnable mWashTask;            // β11：延展层在后台线程算；换源与销毁时作废

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, false);
    }

    // Round 38 / R2②：原 startForSource(...)（播放页"换源"按钮 → 直接展开换源态）随按钮一并删除，
    // 全工程已无调用方；autoSource 通道（下方私有 start + :156 读取）保留，
    // 换源统一改由底部「换源」按钮弹出源名单（T-301②③）。
    private static void start(Activity activity, String key, String id, String name, String pic, boolean autoSource) {
        Intent intent = new Intent(activity, DetailActivity.class);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("autoSource", autoSource);
        activity.startActivity(intent);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityDetailBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mKey = getIntent().getStringExtra("key");
        mId = getIntent().getStringExtra("id");
        mName = getIntent().getStringExtra("name");
        mPic = getIntent().getStringExtra("pic");
        if (mName == null) mName = "";
        mBinding.progressLayout.showProgress();
        // β20 / T-316：播放图标 vector 固有 24dp，胶囊规格要 20px（10dp）⇒ 就地重设 compound drawable 的 bounds，
        // 不改 vector 本体（ic_widget_play 还被其他布局引用）。间距由布局 drawablePadding=@dimen/detail_btn_icon_gap 承担。
        android.graphics.drawable.Drawable playIcon = mBinding.play.getCompoundDrawablesRelative()[0];
        if (playIcon != null) {
            int icon = getResources().getDimensionPixelSize(R.dimen.detail_btn_icon);
            playIcon.setBounds(0, 0, icon, icon);
            mBinding.play.setCompoundDrawablesRelative(playIcon, null, null, null);
        }
        setRecyclerView();
        setViewModel();
        mViewModel.detailContent(mKey, mId);
        // T-146（D-1 甲）：进页面即先让底部源行显示 loading 占位，再后台发起**一次**跨站点搜索；
        // 不阻塞详情主流程、不弹错、不加阻塞式 loading（A 派单 D-1 裁定理由）
        // T-301⑤：底部源行刷新与「进页面自动展开换源态」两条随源层下线删除。
        //（autoSource 的私有 start 早在全工程无调用方，见 :182 注释；intent 参数本轮不删，免得牵动 start 签名）
        searchSourcesOnce(false);
    }

    @Override
    protected void initEvent() {
        mBinding.play.setOnClickListener(v -> playCurrent());
        mBinding.keep.setOnClickListener(v -> toggleKeep());
        // T-301②（A 派单）：「换源」按钮不再进换源态，改为**弹源列表弹窗**（本方法成为唯一换源入口）
        mBinding.source.setOnClickListener(v -> toggleSourceRow());   // β19 / T-311：按钮只展开/收起列表，不切源
        // β20 / T-321：简介可聚焦（层表 L_DESC），OK/点击＝弹居中可滚动全文弹层；BACK 关闭后焦点回简介（dismiss 监听保证）。
        mBinding.desc.setOnClickListener(v -> showDescDialog());
        // 播放按钮聚焦：单集立即预览；多集维持最后预览的集（v1.9 表）
        mBinding.play.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && isSingleEpisode() && !currentEpisodes().isEmpty()) requestPreview(currentEpisodes().get(0), 0);
        });
        // β4 / 焦点一次性重写（A 裁定 1.3「旧轨真删」+ 冻结令 firefly-detail-focus-rewrite-freeze）：
        // 原先挂在这里的**三条按键路由**（play DOWN、rowToEpisode 供两条源行、flagRecycler DOWN）与
        // **一条抢焦**（desc 聚焦拽回 play，即 C 看到的 `initHold reason=desc`）全部删除，
        // UP/DOWN 的落点改由本页 `dispatchKeyEvent` 的一张层表统一分派（见 layerOf / layerTarget / routeUpDown）。
        // 删掉的是"挂在哪个视图上就归谁管"的分散接线——换源态首屏由 T-181 抢焦直接落在**卡片 itemView**，
        // 行容器上的监听够不着卡片 ⇒ 卡片 UP 无人接管 ⇒ 落回系统几何搜索（β29 失败项、也是三连回归的机制）。
        // 保留：`:192` play 聚焦起单集预览（v1.9 表，不是按键路由）、各 adapter 的聚焦预览与焦点日志。
        // desc 抢焦删除后，简介是层表上的合法一层（L1），焦点落它不再被拽回 play；
        // 初始焦点竞态只剩 `mKeySeen` 一重闸门（dispatchKeyEvent 记键、onWindowFocusChanged 只在未记键时介入）。
    }

    /**
     * T-196-a：方向链的统一步进（三步兜底 + 每步一条可区分日志）。
     * chain 只描述"要去哪一行"，dir 描述方向，日志里的 mode 用来分清确认态/换源态；
     * C 需要看到的三种路径：行名本体、行名后加 :child（落到行内首个可聚焦卡）、行名后加 :posted（下一帧才抢到的兜底）。
     */
    private boolean focusStep(String chain, String dir, View target) {
        if (target == null || target.getVisibility() != View.VISIBLE) {
            Log.d("FireFlyDetail", "focusMove chain=none dir=" + dir + " reason=gone mode=confirm");
            return false;
        }
        View landed = target.requestFocus() ? target.findFocus() : null;
        if (landed == null || landed == target) {
            View child = focusFirstDescendant(target);
            if (child != null) {
                landed = child;
                chain = chain + ":child";
            }
        }
        if (landed == null) {
            final View t = target;
            final String c = chain;
            target.post(() -> {
                View card = focusFirstDescendant(t);
                boolean ok = card != null;
                Log.d("FireFlyDetail", "focusMove chain=" + c + " dir=" + dir + " retry=true ok=" + ok
                        + " to=" + nameOf(ok ? card : null) + " mode=confirm");
                if (ok) DetailLog.focus(dir, card.getId());
            });
            landed = target;
            chain = chain + ":posted";
        }
        Log.d("FireFlyDetail", "focusMove chain=" + chain + " dir=" + dir
                + " to=" + nameOf(landed) + " mode=confirm");
        DetailLog.focus(dir, landed.getId());
        return true;
    }

    /**
     * T-195-a：在给定容器的**后代**里找第一个可聚焦且可见的节点，并就地让它拿到焦点（⑥/⑦ 源卡行、线路行的兜底）。
     * 只看后代、不看传入的容器本身——否则"行容器自己已 focusable 但没子卡拿到焦点"时会原地打转。
     */
    private View focusFirstDescendant(View root) {
        if (!(root instanceof android.view.ViewGroup)) return null;
        android.view.ViewGroup group = (android.view.ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) continue;
            if (child.isFocusable() && child.requestFocus()) return child;
            View deeper = focusFirstDescendant(child);
            if (deeper != null) return deeper;
        }
        return null;
    }

    /** T-195-a：日志用的可读节点名（资源名优先，取不到退回数字，绝不为取名字抛异常）。 */
    private String nameOf(View v) {
        if (v == null) return "null";
        if (v.getId() != View.NO_ID) {
            try {
                return getResources().getResourceEntryName(v.getId());
            } catch (Exception ignored) {
                // 系统 id 没有 entry name，退回下面的数字形式
            }
        }
        return "id/" + v.getId();
    }

    private void setRecyclerView() {
        mBinding.flagRecycler.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        // β17 / T-301②（真机 5/5 焦点漂回简介）：RecyclerView 默认的 change 动画会**另绑一个 ViewHolder
        // 做交叉淡入**，正持有焦点的那颗 text 随之离开窗口 ⇒ 系统把焦点交给下一个 focusable＝简介 desc。
        // 关掉 change 动画后，notifyItemChanged(payload) 走**原地重绑**（同一颗 view），焦点不掉。
        RecyclerView.ItemAnimator flagAnimator = mBinding.flagRecycler.getItemAnimator();
        if (flagAnimator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) flagAnimator).setSupportsChangeAnimations(false);
        }
        mBinding.flagRecycler.setAdapter(mFlagAdapter = new FlagAdapter());
        // β19 / T-311：装配换源列表——与 1.1.0 `@id/quick` 同一套（HorizontalGridView 自带水平 LayoutManager，
        // 只需 spacing/rowHeight/adapter；见 619e991 的 VideoActivity:357-359）
        mBinding.sourceRow.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.sourceRow.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.sourceRow.setAdapter(mSourceRowAdapter = new SourceRowAdapter());
        // β20 / T-322：选集改**固定单行横向模块**（8 列网格下线，旧判据作废）——集数多少不再影响页面高度，
        // 左右逐集移动、到缘吃键不跳区（边界判定见 routeLeftRight）、焦点记忆与进场定位见 layerEntry/bind。
        // β28 / T-329 第六刀：选集行换 leanback HorizontalGridView（与源行同款）——GridLayoutManager 自带
        // "焦点接近边缘即带入下一项、并保住持焦 child" 的能力；只需 spacing/rowHeight/adapter，不设 LM
        // （与 619e991 的 VideoActivity:357-359 同写法）。
        mBinding.episodeRecycler.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.episodeRecycler.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.episodeRecycler.setAdapter(mEpisodeAdapter = new EpisodeAdapter());
    }

    private void setViewModel() {
        mViewModel = new androidx.lifecycle.ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, this::setDetail);
        mViewModel.getSearch().observe(this, this::onSearchObserved);
    }

    // ================= β4 / 焦点唯一权威：层表 + 单一 dispatch 路由（A 裁定 1.3、2、冻结令） =================

    /** 层名＝日志里的行名；`LAYERS` 的下标即 `focusMove row=`（C 判据可直接读序号）。 */
    private static final String L_DESC = "desc";
    private static final String L_BUTTONS = "buttons";
    private static final String L_FLAG = "flag";
    private static final String L_EPISODE = "episode";
    // β19 / T-311：换源列表是**层表上的合法一层**（A 派单"暂名 rowSource"）——收进同一张 LAYERS、
    // 同一个 edgeFrom 决定者（R7），不为它另开第二条路由；收起时 layerVisible 返回 false ⇒ 上下键自动跳过它。
    private static final String L_SOURCE_ROW = "rowSource";
    // T-301①（A 派单）：源层下线 ⇒ 层表曾收敛为四层 desc→buttons→flag→episode。
    // β19 / T-311 起为**五层**（末位追加 rowSource，见下）；β23 审计订正本注释与数组一致——**只改注释，行为未动**。
    // `LAYERS` 下标＝日志 row=。
    private static final String[] LAYERS = {L_DESC, L_BUTTONS, L_FLAG, L_EPISODE, L_SOURCE_ROW};

    /**
     * T-178 兜底闸门（记"用户已按键"）＋ β4 层表分派。
     * 详情页方向键的**唯一权威**在这里：不再有第二个视图级监听参与判定（A 硬要求③「旧轨真删」）。
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) mKeySeen = true;
        if (routeUpDown(event)) return true;
        // β20 / T-322：选集单行后左右到缘**吃键不跳区**（与上下到缘吃键同族语义）；决定者仍是本 dispatch（R7）。
        if (routeLeftRight(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    /**
     * 层表分派（与设置页 β3 同法：表 + 实时可见性 + 单一 dispatch）。
     * 边（{@link #edgeFrom}）保留**既判语义、不重排视觉**：
     * · `buttons DOWN → 源行`＝T-196-a「从 play 按 DOWN 直达源卡行」原样保留；
     * · `源行 UP → 线路行（可见且有项）否则按钮行`＝**欠账①「换源态源卡 UP 能进线路行」**，
     *   且这一条现在对**卡片 itemView** 同样生效（旧实现只挂在行容器上，抢焦落在卡片时够不着）；
     * · `desc UP → 吃键`／`episode DOWN → 吃键`＝顶/底不外溢（R3 同口径，放行系统搜索就是逃逸路径）；
     * · 覆盖式（A 裁定 2）：确认态链能依次到达 play、desc、源行容器、源卡——
     *   play↑=desc、desc↓=play、play↓=源行容器→行内首个可聚焦后代（源卡）。
     * 长按护栏同全站：`repeatCount>0` 不接管（交回默认路由做行内移动）。
     */
    private boolean routeUpDown(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) return false;
        boolean up = event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP;
        if (!up && event.getKeyCode() != KeyEvent.KEYCODE_DPAD_DOWN) return false;
        View focus = getCurrentFocus();
        String from = layerOf(focus);
        if (from == null) return false;                       // 不在层表上（预览窗/进度层）⇒ 默认路由
        // β20 / T-322：选集改**固定单行**后，行内没有"上下移动"这回事——焦点在选集层时 UP 恒出层、DOWN 恒到底/源列表，
        // 旧 8 列网格的"首行/末行才接管"边界判定（isEpisodeOnEdge）随网格一并下线。
        String to = edgeFrom(from, !up);
        if (to == null) {                                     // 到顶/到底：吃键，不外溢
            logMove(focus, null, null, up, false, "eaten");
            return true;
        }
        View target = layerEntry(to);
        if (target == null) {
            logMove(focus, null, to, up, false, "noEntry");
            return true;
        }
        boolean moved = focusStep(chainName(to), up ? "up" : "down", target);
        logMove(focus, moved ? target : null, to, up, moved, moved ? "table" : "failed");
        return moved;                                         // 落位失败就放行默认路由（绝不"按了没反应"）
    }

    /** 层 → 下一层（down=true 向下）；候选按优先级列出，第一个**实时可见**的胜出＝R5「焦点跳过不可见行」。 */
    private String edgeFrom(String layer, boolean down) {
        switch (layer) {
            case L_DESC:
                return down ? nextVisible(L_BUTTONS, L_FLAG, L_EPISODE) : null;
            case L_BUTTONS:
                // T-301①：buttons 的 DOWN **必须落 flag**——旧写法 nextVisible(源行, 集数行) 既跳过线路行、
                // 又落在已下线的源层上（取证表 §1-② B5 记的那处「路由与视觉顺序不同构」就在这里）。
                return down ? nextVisible(L_FLAG, L_EPISODE) : nextVisible(L_DESC);
            case L_FLAG:
                return down ? nextVisible(L_EPISODE) : nextVisible(L_BUTTONS, L_DESC);
            case L_EPISODE:
                // T-311：集数行 DOWN 在**换源列表展开时**进它（它就在集数之下），收起时 nextVisible 返回 null
                // ⇒ 行为与 β47 一致（吃掉这把 DOWN，不外溢）。
                return down ? nextVisible(L_SOURCE_ROW) : nextVisible(L_FLAG, L_BUTTONS, L_DESC);
            case L_SOURCE_ROW:
                // 判据②：首项 UP 回集数行；DOWN 到底（本页最末层）＝吃键，不出页。
                return down ? null : nextVisible(L_EPISODE, L_FLAG, L_BUTTONS, L_DESC);
            default:
                return null;
        }
    }

    private String nextVisible(String... layers) {
        for (String layer : layers) if (layerVisible(layer)) return layer;
        return null;
    }

    /**
     * β20 / T-322：三个横排行（线路／选集／源列表）的左右键统一路由。
     * 决定者唯一：本方法与 routeUpDown 同在本页 dispatch（R7），布局不写任何 nextFocusLeft/Right 静态连线。
     * <p>
     * β21 第一刀（长按出栏）：删 `repeatCount>0` 排除 ⇒ 到缘连发也吃（保持现状，审计确认）。
     * β22 第二刀（右缘逃逸）：edge 判据从"适配器末位"起曾放行依赖系统 focusSearch，实测被 FocusFinder
     * traversal 绝对回退接走给了简介 ⇒ 改三分（inRowPass/scrollIn/eatenLR）。
     * <p>
     * β23 / T-329 第三刀（C 真机 beta51，24 集片：`scrollIn ok=true pos=13` 后**焦点仍落到简介**，
     * 死循环 10→11→12→13→简介→10，3/16）。审计定案（对 `d4e0eb5`，两条都落地）：
     * ① **真因是时序不是吃键**——`scrollToPositionWithOffset` 只排了**异步 relayout**，
     *    而 `r.post` 挂普通消息队列、与 relayout **无先后保证**：post 先跑 ⇒ 拿到旧 holder、
     *    requestFocus 瞬时成功（ok=true 只代表"抢到"不代表"落位"）；随后 relayout 把持焦 view scrap 重绑、
     *    焦点被清 ⇒ 系统按 traversal 序回退到简介。**修法**＝改在**下一帧布局完成后**（OnPreDrawListener，
     *    一次性、自移除）取 target 的 itemView `requestFocus`，抢完**再核对一次** `getCurrentFocus()`，
     *    不符重试 1 次；仍不符 ⇒ `result=stuck`，焦点收回原卡/行内，**绝不落 desc**。
     * ② **三条静默 return 必须留痕**——`count==0 / vh==null / pos<0` 原来零日志放行；
     *    `vh==null` 最可疑（上一把 relayout 把持焦 holder 弄离窗后，下一把键直接落这里，
     *    这正是"两轮改了仍看不出为什么"的原因）。现全部打 `mode=lrSkip reason=noAdapter|noHolder|badPos`。
     *    **吃键判定保持放行**（理由：拿不到行内位置时，放行让系统兜底好过按键无响应——β26 口径；
     *    但每次放行都有日志可查，C 若在某条 lrSkip 后看到跳区，即可锁定现场）。
     * ③ `inRowPass`（下一卡 attach 且**完整在视口**）**维持放行**——审计明确：这是唯一靠"放行"工作的
     *    已通过路径（C beta51 实测行内 9/9），RV 原生处理移动/滚动跟随/marquee 启动/`mEpisodeFocusPos` 记忆位；
     *    搬进 dispatch 会把时序陷阱扩大到每一次行内移动。派单要求③的"边缘"＝目标不完整/未 attach，
     *    正是 `scrollIn` 接管的场景，与本条不冲突。
     * <p>
     * 日志四态：`eatenLR`（真首/末位吃键）／`inRowPass`（行内放行）／`scrollIn scheduled→result=ok|retry|stuck`
     * （接管带入）／`lrSkip reason=`（无法定位的留痕放行）。C 靠 `result=` 判（要求④）。
     * 线路行/选集行/源列表行同一函数（源行 GridLayoutManager 是 LinearLayoutManager 子类，走同路径）。
     */
    private boolean routeLeftRight(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        boolean left = event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT;
        if (!left && event.getKeyCode() != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        View focus = getCurrentFocus();
        String from = layerOf(focus);
        if (!L_FLAG.equals(from) && !L_EPISODE.equals(from) && !L_SOURCE_ROW.equals(from)) return false;
        String dir = left ? "left" : "right";
        RecyclerView row = L_FLAG.equals(from) ? mBinding.flagRecycler
                : L_EPISODE.equals(from) ? mBinding.episodeRecycler : mBinding.sourceRow;
        // β24 / T-329 第四刀（刘先生实感：「按左键后可能丢失焦点；再回到选集后右翻就翻不动」）：
        // 焦点若停在**行容器本身**（beta52 收回失败的残留形态：原卡 detach ⇒ requestFocus false ⇒
        // ViewRootImpl 回退分配落在容器上），findContainingViewHolder(容器)==null ⇒ 只会走 lrSkip 放行，
        // 而容器已持焦、系统又找不到下一站 ⇒ "翻不动"。现就地救援：按方向把焦点送进行内**具体 item**
        // （RIGHT→末个可见、LEFT→首个可见），吃键并打 `mode=lrRescue`——绝不把焦点留在容器上。
        if (focus == row) {
            int n = row.getChildCount();
            View child = n == 0 ? null : (left ? row.getChildAt(0) : row.getChildAt(n - 1));
            boolean rescued = child != null && child.requestFocus();
            Log.d("FireFlyFocus", "lrMove dir=" + dir + " mode=lrRescue focus=row ok=" + rescued
                    + " pos=" + (rescued ? row.getChildAdapterPosition(child) : -1) + " layer=" + from);
            return true;
        }
        RecyclerView.Adapter<?> adapter = row.getAdapter();
        int count = adapter == null ? 0 : adapter.getItemCount();
        if (count == 0) {
            Log.d("FireFlyFocus", "lrMove dir=" + dir + " mode=lrSkip reason=noAdapter"
                    + " focus=" + viewName(focus) + " layer=" + from);
            return false;
        }
        RecyclerView.ViewHolder vh = row.findContainingViewHolder(focus);
        if (vh == null) {
            Log.d("FireFlyFocus", "lrMove dir=" + dir + " mode=lrSkip reason=noHolder"
                    + " focus=" + viewName(focus) + " layer=" + from + " count=" + count);
            return false;
        }
        int pos = vh.getAbsoluteAdapterPosition();
        if (pos < 0) {
            Log.d("FireFlyFocus", "lrMove dir=" + dir + " mode=lrSkip reason=badPos"
                    + " focus=" + viewName(focus) + " layer=" + from + " pos=" + pos);
            return false;
        }
        int nextPos = left ? pos - 1 : pos + 1;
        if (nextPos < 0 || nextPos >= count) {
            logMove(focus, null, from, left, false,
                    "eatenLR repeat=" + (event.getRepeatCount() > 0) + " pos=" + pos + "/" + count);
            return true;
        }
        RecyclerView.ViewHolder nh = row.findViewHolderForAdapterPosition(nextPos);
        // β24 / 诊断①（C 真机 beta52 片A 22 集：`inRowPass pos=7/22` 但焦点实落 desc）：
        // 旧判据 `fullyInViewport` 是几何自算（child 左右缘 vs 行内缘），在"目标卡只露出十几像素"时
        // 曾给出 true ⇒ 放行后系统 focusSearch 拿不到完整目标 ⇒ traversal 回退到 desc。
        // 现改用 **LayoutManager 权威的"完全可见"区间**判定：nextPos 必须落在
        // [findFirstCompletelyVisibleItemPosition, findLastCompletelyVisibleItemPosition] 才允许 inRowPass；
        // **部分可见一律走 scrollIn 接管**。日志补 `firstComplete= lastComplete=` 供 C 对账。
        // （源行是 leanback GridLayoutManager，非 androidx LinearLayoutManager 子类 ⇒ 走几何兜底。）
        boolean completelyVisible;
        String completeInfo;
        RecyclerView.LayoutManager lm = row.getLayoutManager();
        // β28：选集/源行若为 leanback HorizontalGridView ⇒ 交给它的 GridLayoutManager 自己带入
        // （它会在焦点接近边缘时滚动并保住持焦 child），这里只要有下一项就放行，不再自己抢滚动。
        if (row instanceof androidx.leanback.widget.HorizontalGridView) {
            if (nh != null) {
                Log.d("FireFlyFocus", "lrMove from=" + viewName(focus) + " dir=" + dir
                        + " mode=inRowPass pos=" + nextPos + "/" + count + " layer=" + from
                        + " grid=true firstComplete=na lastComplete=na");
                return false;
            }
        }
        if (lm instanceof LinearLayoutManager) {
            LinearLayoutManager llm = (LinearLayoutManager) lm;
            int firstComplete = llm.findFirstCompletelyVisibleItemPosition();
            int lastComplete = llm.findLastCompletelyVisibleItemPosition();
            completelyVisible = nh != null && nextPos >= firstComplete && nextPos <= lastComplete;
            completeInfo = " firstComplete=" + firstComplete + " lastComplete=" + lastComplete;
        } else {
            completelyVisible = nh != null && fullyInViewport(row, nh.itemView);
            completeInfo = " firstComplete=na lastComplete=na";
        }
        if (completelyVisible) {
            Log.d("FireFlyFocus", "lrMove from=" + viewName(focus) + " dir=" + dir
                    + " mode=inRowPass pos=" + nextPos + "/" + count + " layer=" + from + completeInfo);
            return false;
        }
        final View fallback = focus;
        // β25 / T-329 第五刀（A 定案：位置式滚动在 wrap_content 宽 item 上行没必要保障 ⇒ 按像素推进）：
        // 第一步仍 scrollToPositionWithOffset(target, 0) 把 target 带到左缘，其后**逐帧看实际 bounds 推进**。
        scrollRowTo(row, nextPos, left);
        Log.d("FireFlyFocus", "lrMove from=" + viewName(focus) + " dir=" + dir
                + " mode=scrollIn scheduled pos=" + nextPos + "/" + count + " layer=" + from);
        pxScrollIn(row, nextPos, 0, fallback, dir, count, from, left);
        return true;
    }

    /** β25 第五刀（A 步骤①）：第一步＝scrollToPositionWithOffset(target, 0)（target 贴左缘）；
     *  源行（leanback GridLM 非 androidx LMS 子类）兜底 scrollToPosition。推进交给 pxScrollIn。 */
    private void scrollRowTo(RecyclerView row, int target, boolean left) {
        // β27：**先松开持焦子项再滚**。真机证据（beta56，19 集片 pos=14/19）：
        // 焦点仍在当前集时调 scrollBy/scrollToPositionWithOffset，布局阶段
        // `requestChildRectangleOnScreen` 会把持焦 child 拉回可视区 ⇒ 滚动被自己抵掉，
        // 表现就是 advance 执行了却每键 stuck、位置纹丝不动。
        // 清焦点后本帧无持焦子项，滚动才不被回拉；随后 OnPreDraw 里再落到目标卡。
        View cur = getCurrentFocus();
        if (cur != null && row.findContainingViewHolder(cur) != null) cur.clearFocus();
        RecyclerView.LayoutManager lm = row.getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            ((LinearLayoutManager) lm).scrollToPositionWithOffset(target, 0);
        } else if (lm != null) {
            lm.scrollToPosition(target);
        }
    }

    /**
     * β24 / T-329 第四刀（A 要求②＋诊断②）：**任何"收回"都必须落到行内的具体 item**，绝不落回 RecyclerView 容器。
     * 顺序＝原卡（还 attach 就回原位）→ **当前可见、且离目标 pos 最近的那颗**
     * （C 片B：`kept=true` 但焦点没落到任何 item——原卡 detach 时旧版退到"首/末项"，现按距离取最近，
     *  语义更贴"留在行内继续翻"）；连一个 child 都没有 ⇒ 返回 false（焦点保持现状，
     *  **绝不 `row.requestFocus()`**——容器持焦就是"翻不动"的病灶）。
     */
    private boolean focusRowItem(RecyclerView row, View preferred, int target) {
        if (preferred != null && preferred.isAttachedToWindow() && preferred.requestFocus()) {
            return true;
        }
        View best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child == null || !child.isFocusable()) continue;
            RecyclerView.ViewHolder cvh = row.getChildViewHolder(child);
            int p = cvh == null ? -1 : cvh.getAbsoluteAdapterPosition();
            if (p < 0) continue;
            int dist = Math.abs(p - target);
            if (dist < bestDist) {
                bestDist = dist;
                best = child;
            }
        }
        return best != null && best.requestFocus();
    }

    /**
     * β25 / T-329 第五刀（A 给定做法，逐字执行）：**布局完成后**先试交焦点，交不成**按像素推进**。
     *  · attempt＝推进预算 **2 步**（A：粗推最多 2 次；细推同样计入，预算用尽不再推、直接按焦点结果定性）；
     *  · 推进一律 row.scrollBy（A：位置式滚动不可靠，废重发）；
     *  · 每步都打 scrollIn probe tgtL=.. tgtR=.. rowW=.. scrolledX=..（A 要求④；holder 未知时 tgt=-1 如实打）。
     */
    private void pxScrollIn(RecyclerView row, int target, int attempt,
                            View fallback, String dir, int count, String from, boolean left) {
        if (!row.getViewTreeObserver().isAlive()) {
            boolean kept0 = focusRowItem(row, fallback, target);
            Log.d("FireFlyFocus", "lrMove dir=" + dir + " mode=scrollIn result=stuck kept=" + kept0
                    + " reason=vtoDead pos=" + target + "/" + count + " layer=" + from);
            return;
        }
        row.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (row.getViewTreeObserver().isAlive()) row.getViewTreeObserver().removeOnPreDrawListener(this);
                RecyclerView.ViewHolder h = row.findViewHolderForAdapterPosition(target);
                if (h != null && h.itemView.requestFocus()) {
                    verifyFocusFrame(row, h, target, attempt, fallback, dir, count, from, left, false);
                } else if (attempt < 2) {
                    Log.d("FireFlyFocus", "lrMove dir=" + dir + " mode=scrollIn step=advance reason=focusMiss"
                            + " attempt=" + attempt + " pos=" + target + "/" + count + " layer=" + from);
                    probeAndAdvance(row, target, attempt, fallback, dir, count, from, left);
                } else {
                    boolean kept = focusRowItem(row, fallback, target);
                    Log.d("FireFlyFocus", "lrMove dir=" + dir + " mode=scrollIn result=stuck kept=" + kept
                            + " reason=budget pos=" + target + "/" + count + " layer=" + from);
                }
                return true;
            }
        });
    }

    /** A 步骤②：布局完成后看**实际 bounds**。拿不到 holder ⇒ scrollBy(row.width/2, 0) 粗推；
     *  拿到 ⇒ 按屏上几何算 delta 精推（RIGHT：dx=tgtR-rowW+8，>0 才推；LEFT：dx=tgtL-8，<0 才推）；
     *  dx==0（已完整可见但焦点没递上）⇒ 不再推，交由下一帧焦点验证定性 ok/stuck。 */
    private void probeAndAdvance(RecyclerView row, int target, int attempt,
                                 View fallback, String dir, int count, String from, boolean left) {
        RecyclerView.ViewHolder h = row.findViewHolderForAdapterPosition(target);
        View child = h == null ? null : h.itemView;
        int rowW = row.getWidth();
        int scrolled = row.computeHorizontalScrollOffset();
        if (child == null) {
            // β29：粗推必须**分成键方向**且**按一张卡宽**推（B 复核指出：旧写法恒向右、
            // 半屏≈964px 远大于卡宽≈106px ⇒ LEFT 反向、且两步预算内来回抖动）。
            int cardW = row.getChildCount() > 0 && row.getChildAt(0) != null
                    ? Math.max(1, row.getChildAt(0).getWidth())
                    : Math.max(1, rowW / 2);
            int push = left ? -cardW : cardW;
            Log.d("FireFlyFocus", "scrollIn probe tgt=-1 rowW=" + rowW + " scrolledX=" + scrolled
                    + " push=" + push + " step=coarse attempt=" + attempt
                    + " pos=" + target + "/" + count + " layer=" + from);
            row.scrollBy(push, 0);
        } else {
            int tgtL = child.getLeft();
            int tgtR = child.getRight();
            int dx = left ? (tgtL - 8) : (tgtR - rowW + 8);
            Log.d("FireFlyFocus", "scrollIn probe tgtL=" + tgtL + " tgtR=" + tgtR + " rowW=" + rowW
                    + " scrolledX=" + scrolled + " dx=" + dx + " step=" + (dx == 0 ? "none" : "fine")
                    + " attempt=" + attempt + " pos=" + target + "/" + count + " layer=" + from);
            if (dx != 0) {
                row.scrollBy(dx, 0);
            }
        }
        pxScrollIn(row, target, attempt + 1, fallback, dir, count, from, left);
    }

    /**
     * A 步骤③：焦点验证帧（result=ok 的**唯一出口**＝getCurrentFocus() 实符）。
     *  β23 定案"ok=true 不是终态"在此闭环：交焦点后 RV 布局期 save/restoreFocus 仍可能抢走 ⇒
     *   · 相符 ⇒ ok；
     *   · 不符 ⇒ 当场收回一次（recovered=1）；
     *   · 收回仍不成 ⇒ 若 target 已 attach 且本轮未试过补推 ⇒ **补推一步再验**（postScrap，总预算 2 不变）；
     *     否则 stuck，经 focusRowItem 收回**与目标最近的可见 item**
     *     （A 第五刀原话"不是第 13 集固定位，而是与目标最近的那个"；第四刀语义保持）。
     */
    private void verifyFocusFrame(RecyclerView row, RecyclerView.ViewHolder h, int target, int attempt,
                                  View fallback, String dir, int count, String from, boolean left, boolean retried) {
        row.post(() -> {
            if (getCurrentFocus() == h.itemView) {
                Log.d("FireFlyFocus", "lrMove dir=" + dir + " mode=scrollIn result=ok"
                        + " attempt=" + attempt + " pos=" + target + "/" + count + " layer=" + from);
                return;
            }
            RecyclerView.ViewHolder h2 = row.findViewHolderForAdapterPosition(target);
            boolean again = h2 != null && h2.itemView.requestFocus();
            if (again && getCurrentFocus() == h2.itemView) {
                Log.d("FireFlyFocus", "lrMove dir=" + dir + " mode=scrollIn result=ok recovered=1"
                        + " pos=" + target + "/" + count + " layer=" + from);
                return;
            }
            // β26：h2 == null ＝ 目标在本次布局里被回收（右缘外），**不能直接判 stuck**——
            // 真机证据：pos=13/24 处 h 在 preDraw 时存在、post 帧里已被回收 ⇒ 旧条件（h2 != null）
            // 使推进分支永远进不去，表现为“每键 stuck、原位不动”。h2 为 null 同样要推进一次。
            if (attempt < 2 && !retried) {
                Log.d("FireFlyFocus", "lrMove dir=" + dir + " mode=scrollIn step=advance reason=postScrap"
                        + " attempt=" + attempt + " pos=" + target + "/" + count + " layer=" + from
                        + " h2=" + (h2 != null));
                probeAndAdvance(row, target, attempt, fallback, dir, count, from, left);
                return;
            }
            boolean inRow = row.hasFocus();
            boolean kept = focusRowItem(row, fallback, target);
            // β30（B 复核的结构性风险）：`scrollRowTo` 里的 clearFocus 只“摘”不移交，
            // 若慢路径整链失败且行内取不到可聚焦 child ⇒ 页面进入“无焦点态”，
            // 下一把键由系统 focusSearch 兜底，可能落到行外（简介）——违反铁律②。
            // 兜底：宁可让**行容器**持焦（下一次 LEFT/RIGHT 会走 lrRescue 送回行内），也不留无焦点。
            if (!kept) kept = row.requestFocus();
            Log.d("FireFlyFocus", "lrMove dir=" + dir + " mode=scrollIn result=stuck recovered=0"
                    + " inRow=" + inRow + " kept=" + kept + " pos=" + target + "/" + count + " layer=" + from);
        });
    }

    /** 卡片是否**完整**落在行的可视区内（左右各按 padding 内缘算）。 */
    private static boolean fullyInViewport(RecyclerView row, View child) {
        return child.getLeft() >= row.getPaddingLeft() && child.getRight() <= row.getWidth() - row.getPaddingRight();
    }

    /** 层的实时可见性（不缓存——行的显隐只由 setDetail 运行期改）。 */
    private boolean layerVisible(String layer) {
        switch (layer) {
            case L_DESC:
                // β34 双入口：层的可见性＝「desc 可见」**或**「credits 接棒且可聚焦」。
                // 只判 desc 的话，无简介片这一层会被 nextVisible 判成不可见 ⇒ UP/DOWN 直接跳过人员行（焦点到不了入口）。
                return mBinding.desc.getVisibility() == View.VISIBLE
                        || (mBinding.credits.getVisibility() == View.VISIBLE && mBinding.credits.isFocusable());
            case L_BUTTONS:
                return mBinding.play.getVisibility() == View.VISIBLE;
            case L_FLAG:
                // β20：线路标题与行同在一个 flagRow（T-317 同行），显隐整行控 ⇒ 层可见性也认整行。
                return mBinding.flagRow.getVisibility() == View.VISIBLE && mFlagAdapter.getItemCount() > 0;
            case L_EPISODE:
                return mBinding.episodeRecycler.getVisibility() == View.VISIBLE
                        && mBinding.episodeRecycler.getAdapter() != null
                        && mBinding.episodeRecycler.getAdapter().getItemCount() > 0;
            case L_SOURCE_ROW:
                // β33 / 详情页 v2 第一条的 **P0 连带**：源列表被包进 `sourceRowWrap`（带「换源」标题，比照 flagRow），
                // 显隐只控容器 ⇒ 内层 GridView 恒为 VISIBLE。若这里仍读 `sourceRow.getVisibility()`，
                // 面板藏起时该层会被判成"可见"⇒ 上下键能翻进一张看不见的源卡（正是 T-329 反复禁的"层表与视觉不一致"）。
                // 判据：认容器，item 数照旧。
                return mBinding.sourceRowWrap.getVisibility() == View.VISIBLE
                        && mSourceRowAdapter != null && mSourceRowAdapter.getItemCount() > 0;
            default:
                return false;
        }
    }

    /** 层的落位目标：按钮行取 play（与旧 `bindReadOnly` 的 UP 目标一致），其余取行容器（由 focusStep 下钻到卡片）。
     *  β20 / T-322 选集**焦点记忆**：离开选集层再回来（上下键往返）落"上次聚焦的那集"，而不是行首；
     *  记忆位由 EpisodeAdapter 的焦点监听**只记录不抢焦**（规范 C3）写入 {@link #mEpisodeFocusPos}；
     *  记忆位失效（换线路/换源重绑后越界、holder 未布局）⇒ 回落行容器（focusStep 下钻到当前滚动位首卡）。 */
    private View layerEntry(String layer) {
        switch (layer) {
            // β34 双入口：落点＝「desc 可见且可聚焦」用它，否则用接棒的 credits（两者都不可用时本层已判不可见，不会走到这）
            case L_DESC: return mBinding.desc.getVisibility() == View.VISIBLE && mBinding.desc.isFocusable()
                    ? mBinding.desc : mBinding.credits;
            case L_BUTTONS: return mBinding.play;
            case L_FLAG: return mBinding.flagRecycler;
            case L_EPISODE: return episodeEntry();
            case L_SOURCE_ROW: return mBinding.sourceRow;
            default: return null;
        }
    }

    private View episodeEntry() {
        if (mEpisodeFocusPos >= 0) {
            RecyclerView.ViewHolder vh = mBinding.episodeRecycler.findViewHolderForAdapterPosition(mEpisodeFocusPos);
            if (vh != null) return vh.itemView;
        }
        return mBinding.episodeRecycler;
    }

    /** 焦点归层：容器自身、行内卡片、卡片内文字都算同一层（沿父链找，不看 position，见根因第二节）。 */
    private String layerOf(View focus) {
        if (focus == null) return null;
        // β34 双入口：credits 也归 L_DESC（它是 TextView、无子节点 ⇒ 不必走 within 沿父链）。
        // 不归层的话，焦点停在 credits 上时 layerOf 返回 null ⇒ 本行的 UP/DOWN 路由整块失效（按键外溢到页面级）。
        if (focus == mBinding.desc || focus == mBinding.credits) return L_DESC;
        if (focus == mBinding.play || focus == mBinding.keep || focus == mBinding.source) return L_BUTTONS;
        if (within(mBinding.flagRecycler, focus)) return L_FLAG;
        if (within(mBinding.sourceRow, focus)) return L_SOURCE_ROW;
        if (within(mBinding.episodeRecycler, focus)) return L_EPISODE;
        return null;
    }

    private boolean within(View row, View v) {
        if (row == null || v == null) return false;
        for (View p = v; p != null; ) {
            if (p == row) return true;
            if (!(p.getParent() instanceof View)) return false;
            p = (View) p.getParent();
        }
        return false;
    }

    /** 沿用 C 已认的链路名（flag/episode/desc/buttons）；原顶部／底部源行两条链路随源层下线作废。 */
    private String chainName(String layer) {
        if (L_BUTTONS.equals(layer)) return "play";
        return layer;
    }

    /** A 口径的一条日志（tag=FireFlyFocus，与设置页同字段）：C 只靠它判"这一跳走了哪条规则、第几层、真落位没有"。 */
    private void logMove(View from, View to, String toLayer, boolean up, boolean moved, String why) {
        Log.d("FireFlyFocus", "focusMove from=" + viewName(from) + " to=" + viewName(to)
                + " row=" + layerIndex(toLayer) + " vis=" + moved + " mode=" + (up ? "up" : "down") + " " + why
                + " state=confirm path=" + layerOf(from) + "->" + toLayer);
    }

    private int layerIndex(String layer) {
        if (layer == null) return -1;
        for (int i = 0; i < LAYERS.length; i++) if (LAYERS[i].equals(layer)) return i;
        return -1;
    }

    /**
     * β13 / T-221 探针（**零行为**：只读 bounds/visibility/alpha/焦点名）。判读口径：
     *  · `edge probe` 的 `osc=`＝`View.getOverScrollMode()`：**0＝OVER_SCROLL_ALWAYS、1＝OVER_SCROLL_IF_CONTENT_SCROLLS（默认）、2＝OVER_SCROLL_NEVER**；β13 主修之后详情页这 5 个视图应当全是 **2**。取值口径不是背来的：同包里源码写 `never` 且本轮未改的对照页（`activity_history.xml:18`）编译值也＝2（`aapt2 dump xmltree` 实测 ⇒ 包内路径被 path shortening 改成 `res/XY.xml`，见本轮 INDEX）。
     *    `][1816,` 出现即＝该滚动区右边界正是刘先生说的"源列表最右边" ⇒ 假设①（EdgeEffect 画在那里）；
     *  · `loading probe` 的 `vis=4`＝VISIBLE、`vis=8`＝GONE：首帧亮、1.2 秒灭 ⇒ 假设②（容器右边距同为 52dp）；
     *  · `focus probe` 首帧若是 `source_card`／`episode*`、1.2 秒后变 `play` ⇒ 假设③（源卡焦点态是 2dp **纯白**描边）；
     *  · 三条都不动、而线仍在 ⇒ 剩下的解释是海报接缝那根（x≈1138），按 A 令底图不动。
     */
    private void logEdgeProbe(String t) {
        Log.d(DetailLog.TAG, "edge probe t=" + t + " scroll=" + probeView(mBinding.scroll)
                + " flag=" + probeView(mBinding.flagRecycler)
                + " srcRow=removed"
                + " epi=" + probeView(mBinding.episodeRecycler));
        Log.d(DetailLog.TAG, "loading probe t=" + t + " loading=" + probeView(mBinding.previewLoading)
                + " preview=" + probeView(mBinding.preview));
        Log.d(DetailLog.TAG, "focus probe t=" + t + " v=" + viewName(getCurrentFocus()) + " mode=confirm");
    }

    /** 探针用的只读格式化：左/右上/右下＋可见性＋alpha＋过滚动模式。未布局返回 na:unlaid（仍是有效信息：说明那一刻还没测量）。 */
    private static String probeView(View v) {
        if (v == null) return "null";
        if (v.getWidth() <= 0) return "na:unlaid(vis=" + v.getVisibility() + ")";
        return "[" + v.getLeft() + "," + v.getTop() + "][" + v.getRight() + "," + v.getBottom()
                + "] vis=" + v.getVisibility() + " alpha=" + v.getAlpha() + " osc=" + v.getOverScrollMode();
    }

    private String viewName(View v) {
        if (v == null || v.getId() == View.NO_ID) return "none";
        try {
            return getResources().getResourceEntryName(v.getId());
        } catch (Throwable t) {
            return "noid";
        }
    }

    /**
     * T-178 兜底②（与帧时序无关的一层）：窗口获得焦点时，若整页焦点为空、或焦点正落在 desc 上
     * （这正是本次缺陷的表现特征），且用户尚未按键 → 把焦点交给 play。
     * 用于覆盖"内容比 window focus 早到/晚到"两种时序下，框架把初始焦点给 traversal 首个 focusable 的情况。
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        // β13 / T-221：只在本 Activity 首次真正获焦时打一轮（首帧＋1.2 秒），且只读不改 ⇒ 零行为
        if (hasFocus && !mEdgeProbeDone) {
            mEdgeProbeDone = true;
            mBinding.progressLayout.post(() -> logEdgeProbe("first"));
            mEdgeProbeLate = () -> logEdgeProbe("late");
            mHandler.postDelayed(mEdgeProbeLate, EDGE_PROBE_LATE_MS);
        }
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || mKeySeen) return;
        if (!mBinding.progressLayout.isContent()) return;
        View focus = getCurrentFocus();
        if (focus != null && focus != mBinding.desc) return;
        // β4 欠账②的正面证据：desc 抢焦监听已删除，**唯一**还会把焦点交给 play 的地方就是这一处，
        // 且它被 `mKeySeen` 闸门挡住——用户按过任何键之后绝不再介入。C 只要看到
        // `FireFlyFocus focusInit gate=keySeen=true` 而没有后续 focusMove，即证明"不再与用户抢焦点"。
        Log.d("FireFlyFocus", "focusInit gate=keySeen=" + mKeySeen + " window=true current=" + viewName(focus)
                + " state=confirm mode=init hold only-before-first-key");
        if (mBinding.play.requestFocus()) DetailLog.focus("init", mBinding.play.getId());
    }

    private void setDetail(Result result) {
        Vod vod = result == null ? null : result.getVod();
        if (vod == null || vod.getName().isEmpty()) {
            mBinding.progressLayout.showContent();
            Notify.show(getString(R.string.detail_error));
            return;
        }
        mBinding.progressLayout.showContent();
        mVideo = vod;
        bind();
    }

    private void bind() {
        Vod vod = mVideo;
        // 锁定海报：进入时缓存，换源永不刷新（拍板 11）
        // T-149（拍板 47）：intent 未带图时改用 getBgPic()（横版 vod_pic_slide 优先 → 竖版 vod_pic 裁切 → null 回落锁定海报）；
        // 注意只改"背景取图"这一处，列表卡片等仍走 getPic()（实施清单 R-2：getPic() 全站 20+ 调用点，语义不动）。
        if (mPic == null || mPic.isEmpty()) mPic = vod.getBgPic();
        // β25 / T-333：`pic pick` 行移交三级链回调内打（口径字面不变，chosen=slide/pic/none；
        // 链是异步的，bind 时刻已无法确定档位——留此注释供 C 对账）。
        // T-203A（β1）：背景一律走 getBgPic()＝横版 vod_pic_slide 优先 → 竖版 vod_pic → null，
        // **不再复用 mPic**。mPic 同时是收藏入库图（setVodPic）与播放页传参，语义必须保持竖版海报——
        // 按清单字面去改 7 处调用方传 getBgPic()，会让首页收藏/历史行的海报变成横版图（且违反拍板 47
        // 「只改背景取图这一处，列表卡片等仍走 getPic()」）。此处取图与 vod.isBgSlide() 档位天然一致，
        // 顺带修掉「slide 存在但背景实际是 intent 传来的竖版」的旧不一致。
        // β25 / T-333：背景三级链＝slide（无条件优先，不验比例）→ 横向 vod_pic（宽/高≥1.3，
        // 只解头探测 inJustDecodeBounds）→ 竖版兑底（poster，才走 β11 PosterWash 延展）。
        // slide 首试**加载失败**⇒ 探测 pic 降级并打 `pick=slideFail->widePic|poster`（A 边界②）。
        // mPic 语义一行未动（竖版海报：收藏入库 setVodPic 与播放页传参仍用它，T-203A 约束在位）；
        // 异步仅"无 slide 需探测 pic"的起点；链的每次回调都先作废旧请求（loadBg 内 mWashGen++），
        // 换源重 bind 时旧探测/旧加载的结果一律不得贴到新图上。
        // β26 修 P0：`getPicSlide()` 允许为 null（T-149 语义），而 `ImgUtil.getUrl` 入口无空值防护
        // （内部 `UrlUtil.convert` 后直接 `startsWith`）⇒ 必须在调用点先兜空，别把 null 递进共享工具。
        Object slideUrl = (vod.getPicSlide() == null || vod.getPicSlide().isEmpty()) ? null : ImgUtil.getUrl(vod.getPicSlide());
        Object picUrl = (vod.getPic() == null || vod.getPic().isEmpty()) ? null : ImgUtil.getUrl(vod.getPic());
        DetailBg.pickStart(this, vod, slideUrl, picUrl, (url, tag, ratio) -> {
            String u = url != null ? url : mPic;
            boolean slideFirst = DetailBg.TIER_SLIDE.equals(tag) && url != null;
            boolean wide = DetailBg.isWide(tag) && url != null;
            Log.d(DetailLog.TAG, "bg pick=" + tag + " ratio=" + ratio);
            DetailLog.pick(DetailLog.len(vod.getPicSlide()), DetailLog.len(vod.getPic()),
                    url == null ? "none" : (wide ? "slide" : "pic"));
            DetailLog.bgSrc(vod.getPicSlide(), vod.getPic(), url == null ? "intent" : (wide ? "slide" : "pic"), tag);
            loadBg(u != null ? u : mPic, wide, slideFirst);
        });
        mBinding.name.setText(vod.getName().isEmpty() ? mName : vod.getName());
        String score = vod.getRemarks();
        mBinding.score.setVisibility(score.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.score.setText(score);
        mBinding.type.setText(vod.getTypeName());
        mBinding.type.setVisibility(vod.getTypeName().isEmpty() ? View.GONE : View.VISIBLE);
        // β33 / 详情页 v2 第四条（刘先生「豆豆 | 片单<修复所有>这个字段要去掉」＝整段去掉，不是清洗）：
        // 删掉原 `meta.append(getSite().getName())`。该段是**源配置 sites[].name 的原文**（全仓 grep「片单/修复所有/豆豆」
        // 命中 0；Site#getName 无清洗，仅 Trans.s2t），不是我方调试串，故只能在这里不绑定它。
        // 连带两处必改（否则出缺陷）：①原写法每段后固定 append(" · ") ⇒ 去源名后尾部悬空分隔符；
        // ②原代码对 meta **没有 setVisibility** ⇒ 年/地皆空时会留一个 37px 空行。改为"先判非空再加分隔"＋空则 GONE。
        StringBuilder meta = new StringBuilder();
        if (!vod.getYear().isEmpty()) meta.append(vod.getYear());
        if (!vod.getArea().isEmpty()) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(vod.getArea());
        }
        mBinding.meta.setText(meta.toString());
        mBinding.meta.setVisibility(meta.length() == 0 ? View.GONE : View.VISIBLE);
        // β20 / T-318：人员行＝导演＋演员合并单行（09 §二「导演：X · 演员：A/B/C…」）；**两者皆空则 GONE 不留空白**。
        // 演员串源侧常以 "/" 或空格分隔，原样保留不重排（不猜分隔语义）。
        String director = vod.getDirector();
        String actor = vod.getActor();
        StringBuilder credits = new StringBuilder();
        if (!director.isEmpty()) credits.append(getString(R.string.detail_director)).append(director);
        if (!actor.isEmpty()) {
            if (credits.length() > 0) credits.append(" · ");
            credits.append(getString(R.string.detail_actor)).append(actor);
        }
        mBinding.credits.setText(credits.toString());
        mBinding.credits.setVisibility(credits.length() == 0 ? View.GONE : View.VISIBLE);
        // T-180（Round 37）：源侧简介常含字面量 "&nbsp;"（Util.clean:125 只在文本含 "<" 时才还原 HTML，
        // 无标签的纯实体串被原样返回）→ 显示前把该字面量换成空格；不引 HTML 解析、不处理其它实体。
        String content = vod.getContent().replace("&nbsp;", " ");
        mBinding.desc.setText(content);
        // β20 / T-321：简介为空 ⇒ 整块隐藏**且不可聚焦**（09 §六），层表 layerVisible 按 visibility 自动跳过它。
        boolean hasDesc = !vod.getContent().isEmpty();
        mBinding.desc.setVisibility(hasDesc ? View.VISIBLE : View.GONE);
        mBinding.desc.setFocusable(hasDesc);
        mBinding.desc.setFocusableInTouchMode(hasDesc);
        // β34（刘先生拍板「修」）：**双入口、互不干扰**——
        //  · 有简介（默认态）：credits 不可聚焦也不可点 ⇒ 行为与 beta64 一字不变（入口仍是 desc）；
        //  · 简介为空且人员非空：credits 接棒成弹层入口，点开的仍是**同一个** showDescDialog（弹层内已带完整导演/演员）。
        // 「简介为空」的判定口径＝hasDesc（＝`vod.getContent().isEmpty()`）：`Vod#getContent()` 是
        // `TextUtils.isEmpty(vodContent) ? "" : Util.clean(vodContent)`（Vod.java:209-211）⇒ **永不为 null**，
        // 空串是该字段的唯一空形态，所以不必再判 null；人员侧同理（getDirector/getActor 也走 TextUtils.isEmpty 兜底）。
        boolean creditsEntry = credits.length() > 0 && !hasDesc;
        mBinding.credits.setFocusable(creditsEntry);
        mBinding.credits.setFocusableInTouchMode(creditsEntry);
        mBinding.credits.setClickable(creditsEntry);
        mBinding.credits.setOnClickListener(creditsEntry ? v -> showDescDialog() : null);
        Log.d(DetailLog.TAG, "desc entry=" + (hasDesc ? "desc" : (creditsEntry ? "credits" : "none"))
                + " creditsFocusable=" + creditsEntry + " descLen=" + content.length() + " creditsLen=" + credits.length());
        // β33 / 详情页 v2 第五条（刘先生「导演演员…和下方的内容介绍合并成一个容器」）：
        // 两子块已由 XML 收进 `descWrap`（credits 改单行＋省略号，完整名单进简介弹层）。
        // 容器显隐＝**两者皆空才整块 GONE**：只 GONE 子块会留下容器的 marginTop 6dp＝12px 空洞，
        // 故比照 :967 一带 flagRow「整行控、间距不留空洞」的既有口径补这一行（credits/desc 各自显隐一行未动）。
        mBinding.descWrap.setVisibility(credits.length() == 0 && !hasDesc ? View.GONE : View.VISIBLE);
        // 层级导航：线路行 ≥2 条才显示（v1.8-35）；β20 起标题与行同在一个 flagRow 里，显隐整行控（间距不留空洞）。
        boolean multiFlag = vod.getFlags().size() >= 2;
        mBinding.flagRow.setVisibility(multiFlag ? View.VISIBLE : View.GONE);
        // 历史进度：当前集高亮（规格 §3.3 确认态）
        History history = History.find(historyKey());
        String mark = history == null ? "" : history.getVodRemarks();
        // T-301④（A 派单）：换源后**尽力保持当前集**——切源前记下的集名优先于「新源自己的历史进度」；
        // 同名集可能挂在别的线路上，所以跨线路找一次并把 flagIndex 指过去；找不到才回落原口径（历史进度→首集）。
        // 取用一次即清，避免污染下一次非换源触发的 bind()。
        String keep = mKeepEpisodeName;
        mKeepEpisodeName = null;
        if (!vod.getFlags().isEmpty()) {
            int flagIndex = 0;
            if (history != null && !history.getVodFlag().isEmpty()) {
                for (int i = 0; i < vod.getFlags().size(); i++) {
                    if (vod.getFlags().get(i).getShow().equals(history.getVodFlag())) {
                        flagIndex = i;
                        break;
                    }
                }
            }
            String hitName = keep == null || keep.isEmpty() ? mark : keep;
            boolean hit = false;
            if (!hitName.isEmpty()) {
                for (int i = 0; i < vod.getFlags().size() && !hit; i++) {
                    for (Episode episode : vod.getFlags().get(i).getEpisodes()) {
                        if (hitName.equals(episode.getName())) {
                            flagIndex = i;
                            hit = true;
                            break;
                        }
                    }
                }
            }
            Log.d(DetailLog.TAG, "keepEpisode=" + (hit ? "hit" : "miss") + " name=" + (hitName.isEmpty() ? "none" : hitName)
                    + " from=" + (keep == null || keep.isEmpty() ? "history" : "switch")
                    + " flag=" + vod.getFlags().get(flagIndex).getShow() + " site=" + currentSourceName());
            for (Flag flag : vod.getFlags()) {
                for (Episode episode : flag.getEpisodes()) episode.setSelected(false);
            }
            for (Episode episode : vod.getFlags().get(flagIndex).getEpisodes()) {
                if (hitName.equals(episode.getName())) episode.setSelected(true);
            }
            mFlagAdapter.setItems(vod.getFlags());
            selectFlag(flagIndex, false);
            // T-187-e（Round 40）判据「选中源后线路行必须刷新为该源的线路」的两处补强：
            // ①数据侧本来就走"新源 detail → bind() → setItems(新 flags)"（switchSource:829 之后由 getResult 回调），
            //   线路名相同/条数相近时**屏上看不出差别**，所以补 mode=（哪一态触发）+ site=（刷新后的当前源名）
            //   + count/sel/names 四个字段，C 只需 grep `FireFlyDetail flags` 就能证明线路跟着源换了；
            // ②横向位置：notifyDataSetChanged 只换数据，LinearLayoutManager(HORIZONTAL) 的滚动位仍停在**上一源**的位置，
            //   观感就是"线路没切过去"——按本次选中的 flagIndex 把它滚回行首，选中项一定在屏上。
            mBinding.flagRecycler.scrollToPosition(flagIndex);
            Log.d(DetailLog.TAG, "flags mode=confirm site=" + currentSourceName() + " count=" + vod.getFlags().size()
                    + " sel=" + flagIndex + " names=" + flagNames(vod));
        } else {
            mFlagAdapter.setItems(new ArrayList<>());
            mEpisodeAdapter.setItems(new ArrayList<>());
            // 分支必打：该源一条线路都没有（或站点未回 flags）时线路块整段隐藏，也要留一行自证
            Log.d(DetailLog.TAG, "flags mode=confirm site=" + currentSourceName() + " count=0 sel=na names=none");
        }
        // T-301⑤：换源态「单集立即预览新源默认集」随该态一并下线（弹窗换源后由常规 bind→setInitialFocus 链接手）。
        mBoundOnce = true;
        checkKeep();
        // T-301⑤：底部源行对勾重算删除。detailState 的 mode 字段在换源态下线后恒为 confirm
        //（保留字段本身 ⇒ C 既有的 state=confirm 期望串不破）
        DetailLog.detailState("confirm", mSourceList.size(), currentSourceName(), null);
        // T-178（Round 37）：内容已 setText、progressLayout 已 showContent() → 首屏初始焦点显式给 play
        setInitialFocus();
    }

    /**
     * T-178（Round 37 真机回归，A 定案 2/2 复现）：详情页首屏初始焦点必须落 play。
     * 根因：activity_detail.xml 里 desc 是 focusable=true 且排在 play 之前，窗口首次分配焦点时
     * 按 focus traversal 顺序给了 desc → 简介聚焦监听自动展开全文（582px）→ play 被顶到屏底裁掉 20px、
     * 底部"播放源"行整体出屏（直接违反拍板 49"常显"）。
     * 时机：在 bind() 末尾（setText 已完成、progressLayout.showContent() 已执行）用 play.post 等一帧再抢焦，
     * 避免 setText 触发的重排在同帧内把焦点又给 desc。
     * 兜底（两层）：①requestFocus 返回 false（view 尚未可聚焦/未 attach）→ 逐帧重试，最多 3 帧；
     * ②拿到焦点后**再核对一帧**：若窗口把焦点重新分配给了 desc（本次缺陷的唯一表现特征），
     *   且用户这一帧内还没按键，则收回一次（mInitFocusRescued 保证只纠一次，绝不与用户抢焦点）。
     * 边界（T-301 之后）：换源态已下线，不再有「名单未就绪不抢焦」守卫与换源态落焦分工——
     *   autoSource 参数仍留在 intent 里（不删是为不动 start 签名）但不再触发展开；换源统一由底部「换源」按钮弹窗（T-301②③）；
     * 禁止项（A 派单）：不改 desc 的 focusable/展开逻辑，不给任何卡片绑 setRightListener，不动其它页面焦点。
     *   （maxLines 例外：Round 39 派单按文档 §3.3 明确把折叠行数定为 3，见 initEvent 注释。）
     */
    private void setInitialFocus() {
        mBinding.play.post(this::applyInitialFocus);
    }

    private void applyInitialFocus() {
        // T-301⑤：换源态已下线 ⇒ 「换源态不抢焦」守卫随之删除（本方法只剩确认态一条路径）
        if (mBinding.play.requestFocus()) {
            DetailLog.focus("init", mBinding.play.getId());
            mBinding.play.post(this::verifyInitialFocus);
        } else if (mInitFocusTries < 3) {
            mInitFocusTries++;
            mBinding.play.post(this::applyInitialFocus);
        }
    }

    private void verifyInitialFocus() {
        if (mInitFocusRescued || mKeySeen || mBinding.play.hasFocus()) return;
        // 只认"焦点被窗口分配给文字块（desc，或 β34 起接棒的 credits）"这一种缺陷表现，其它焦点状态一律不动。
        // 补 credits 的理由：无简介片这一层里只有 credits 可聚焦 ⇒ 系统首帧可能把它错当初始焦点，
        // 那是**同一种缺陷的另一个落点**；只认 desc 会让它落在救援范围外（本行原语义是"救回 play"，不是排除 credits）。
        if (!mBinding.desc.hasFocus() && !mBinding.credits.hasFocus()) return;
        mInitFocusRescued = true;
        if (mBinding.play.requestFocus()) DetailLog.focus("init", mBinding.play.getId());
    }

    /**
     * T-184（Round 39）建双层管线，β8 / 海报改各层职责（取图优先级一行未动：{@code Vod.getBgPic()} 横版优先→竖版）：
     * <p>
     * · <b>主体层 {@code bg}</b>：竖版＝**整高、右贴边、不裁不放大**（{@code adjustViewBounds}＋
     *   {@code wrap_content}＋{@code layout_gravity=end|center_vertical}，几何在 activity_detail.xml）；
     *   横版 ≈16:9 时恰好铺满。两层里它**永远零模糊**——拍板 47 排除的是"主体区模糊"，本批只糊背景。
     * · <b>背景层 {@code bgFill}</b>：横版真图在 ⇒ {@code centerCrop} 直铺（原行为）；
     *   只有竖版在 ⇒ 走本地构图（centerCrop 到 480×270 → 3×box blur → 逐像素 ×0.50 压暗 → {@code fitXY} 放大铺满），
     *   于是左侧得到一块"同色调但没有内容纹理"的延展，右侧海报不被抢画面。
     *   旧写法（T-187-b 的 {@code setAlpha(0.30)} 退成暗底补边）随本批下线——它治的"一横一竖两张海报打架"已不存在。
     * <p>
     * 遮罩 {@code shape_detail_scrim}：β8 起＝水平 左 #E6 → 透明、止于屏宽 58%（右侧不再压暗）＋底部 120dp 渐隐保留。
     * ⚠ 口径变更：旧判据"文字区 dim ≤58"是**靠全屏遮罩算出来的**，新方案遮罩变浅、靠背景压暗补 ⇒ 证据行的
     * dim/raw 仍按屏上实际两层自算，A/C 复核以"左右亮度比"与真机截图为准（见 round1-poster-design-B.md §五）。
     * 证据行 bgEvidence 由代码自算（缩样采样＋与 drawable 同组的遮罩 α），不依赖真机截图。
     */
    private void loadBg(String url, boolean slide, boolean slideFirst) {
        if (url == null || url.isEmpty()) {
            DetailLog.picLoaded(false);
            DetailLog.bgEvidence("none", "na", "na", "na", -1, -1, -1, "na");
            mBinding.bg.setImageDrawable(null);
            mBinding.bgFill.setImageDrawable(null);
            // β9 / 组B：无图＝没有接缝，羽化带与两层状态一起收（顺带把证据行要用的缓存字段清掉，防串到下一条）
            mBgWash = null;
            mBgResource = null;
            return;
        }
        // β8 / 海报（A 额外要求①：横版优先兜底规则）。取图优先级本身没动（`Vod.getBgPic()`＝slide→pic，见 Vod.java:174-181），
        // 本批把“命中哪一档、有没有回落本地构图”显式留痕：
        //   · 横版真图在 ⇒ 背景层直铺（centerCrop，原行为不变），**不做本地构图**；
        //   · 只有竖版在 ⇒ 背景层走「同图 centerCrop → 480×270 → 3×box blur → ×0.50 压暗」，再 fitXY 放大铺满
        //     （小图本身 16:9 ⇒ 放大无形变；不分配 1920×1080 位图，放大交给 GPU）。
        // 旧的“竖版把铺底层 setAlpha(0.30) 退成暗底补边”（T-187-b）随本批下线：它解决的是“两张不同裁法的海报互相打架”，
        // 新方案改成一层“同图模糊延展”，打架的前提没了，所以不再需要 alpha 压低；压暗改在**像素内**（×0.50）。
        // `shot=` 字段如实写 na:no-field：全仓 grep `screenshot` 命中 0 ⇒ Vod bean 没有 vod_pic_screenshot 字段、
        // Gson 也不会解析该键；要“未来源站填了图就自动变好”必须先在 main 的 bean 加字段（触公共层，需 A 批），我不假装支持。
        Log.d(DetailLog.TAG, "bg pick slide=" + (slide ? "hit" : "none") + " shot=na:no-field fallback=" + (slide ? "none" : "stretch"));
        // β25 / T-333：slide 参数语义扩为"横版构图"（slide 档或 widePic 档）；widePic 与 slide 同路直铺、不做 wash。
        mBgSlideFirst = slideFirst;
        mBinding.bgFill.setScaleType(slide ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_XY);
        mBgSlide = slide;
        mBgWash = null;
        mBgResource = null;
        mFillTag = slide ? "slideCrop" : "pending";
        // 换源/重进时会再走一次 loadBg：上一条未决的 80ms 兜底必须作废，否则旧图的 rawCrop 会贴到新图上
        // β11：延展层改到后台线程算（原来那条 80ms「未模糊先出图」的兜底随模糊一起下线——
        // 它防的是“模糊要几十毫秒”，现在要防的是“换源后旧列贴到新图上”，所以只保留作废动作）
        if (mWashTask != null) {
            mHandler.removeCallbacks(mWashTask);
            mWashTask = null;
            mWashGen++;   // β11：本条 loadBg 作废所有在途结果
        }
        // disallowHardwareConfig：证据行要读像素（getPixel / software Canvas 都不接受硬件位图），
        // Glide5 在 API28+ 默认可能交回 HARDWARE 配置，这里显式禁用（一张海报的内存代价可接受）。
        // T-203A（β1）：走 ImgUtil.getUrl 解析内联鉴权头（@Headers/@Cookie/@Referer/@User-Agent）——
        // 卡片/历史/台标早走这条路，背景此前是裸 load(url)，含标记的源会把鉴权串连同路径一起请求 ⇒ 防盗链必 403。
        Object model = ImgUtil.getUrl(url);
        RequestBuilder<Drawable> request = Glide.with(this).load(model).dontTransform().disallowHardwareConfig();
        if (slide) {
            request.clone().into(mBinding.bgFill);
        } else {
            // β11：竖版延展层不再单独发起取图，改由主体图解码完成那一刻从**同一张位图**取列（见 onResourceReady）
        }
        request.listener(new RequestListener<Drawable>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                DetailLog.picLoaded(false);
                DetailLog.bgEvidence(slide ? "slide" : "pic", "na", "na", "na", -1, -1, -1, mFillTag);
                // β25 / T-333：slide 首试失败⇒探测 pic 降级（A 边界②）；widePic/poster 档失败不降级（防循环）。
                if (mBgSlideFirst) {
                    mBgSlideFirst = false;
                    onSlideFailed(mVideo);
                }
                return false;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                // 主体层（bg）＝竖版整高不裁不放大、右贴边（几何见 activity_detail.xml β8 P1）；横版 ≈16:9 时恰好铺满
                DetailLog.picLoaded(true);
                Log.d(DetailLog.TAG, "bg mode=" + (slide ? "slideFit" : "mainHeightRight") + " w=" + resource.getIntrinsicWidth() + " h=" + resource.getIntrinsicHeight());
                // T-203（β1，C 建议）：解码尺寸与来源留痕，供下轮判"图本来就这么小"还是"被缩小了"
                DetailLog.bgImg(resource.getIntrinsicWidth(), resource.getIntrinsicHeight(), mBinding.bg.getWidth(), mBinding.bg.getHeight(), dataSource == null ? "na" : dataSource.name());
                mBgResource = resource;
                if (!slide) applyWashFrom(resource);   // β11：与主体层同源＝接缝同值的前提
                logBgEvidence(resource, slide, mFillTag);
                return false;
            }
        }).into(mBinding.bg);
    }

    /**
     * β25 / T-333：slide 首试**加载失败**的降级（A 边界②）——探测 vod_pic 后打
     * `bg pick=slideFail-&gt;widePic|poster` ＋ `bg pick=… ratio=…`（口径同 bind 起链），再按档位重载。
     * 无 pic（url=null）⇒ 回退"锁定海报 mPic 按竖版兑底"（ratio=na），与既有空图分支同形。
     * 只在 `mBgSlideFirst` 为真且未作废时进入；降级请求 slideFirst=false ⇒ 不再二次降级（防循环）。
     */
    private void onSlideFailed(Vod vod) {
        if (vod == null) return;
        DetailBg.fallbackFromSlide(this, vod, ImgUtil.getUrl(vod.getPic()), (url, tag, ratio) -> {
            Log.d(DetailLog.TAG, "bg pick=slideFail->" + (url == null ? "poster" : tag));
            String u = url != null ? url : mPic;
            boolean wide = url != null && DetailBg.isWide(tag);
            Log.d(DetailLog.TAG, "bg pick=" + tag + " ratio=" + ratio);
            DetailLog.pick(DetailLog.len(vod.getPicSlide()), DetailLog.len(vod.getPic()),
                    url == null ? "none" : (wide ? "slide" : "pic"));
            DetailLog.bgSrc(vod.getPicSlide(), vod.getPic(), url == null ? "intent" : (wide ? "slide" : "pic"), tag);
            loadBg(u, wide, false);
        });
    }

    /**
     * β11 / 组B 三轮（A 方案 A 的「接缝严格连续」变体）：竖版背景延展层＝**主体左缘那一列**横向拉伸。
     * 三点与 β8–β10 的实质差别：
     *   · **同源**：直接从正在显示的这张位图取第 0 列（不再另发一次 centerCrop 480×270 的取图）
     *     ⇒ 接缝处背景与主体首列来自同一批像素，「接缝列与主体首列 RGB 平均差 ≤2/255」是**构造性成立**，不是调参调出来的；
     *   · **不模糊**：纵向模糊会把接缝值变成“该列的邻域均值”，与上面那条硬指标直接冲突；
     *     而“没有内容纹理”这个目的本来就由“水平方向只有一列”天然满足（横向是常数 ⇒ 不可能出现可辨内容或单列峰值）；
     *   · **压暗改成逐列亮度斜坡**：贴接缝的 {@link #WASH_PLATEAU_PX} 平台 k≡1（一压暗就破 ≤2），
     *     再向左 {@link #WASH_RAMP_PX} 线性降到 kMin，更左恒 kMin。kMin 由「左区平均亮度 ≈ {@link #WASH_TARGET_RATIO} × 全图平均亮度」
     *     闭式解出（内容从“整图均值”变成了“左缘列”，沿用旧的固定 ×0.50 会让左右亮度比随源漂移）。
     * 遮罩 {@code shape_detail_scrim} 一行未动：它水平止于屏宽 58%，恰好不压接缝平台。
     * 取不到位图、或主体本就铺满（无接缝）⇒ 背景层留空并打 {@code wash ms=na}，主体显示不受影响。
     */
    private void applyWashFrom(Drawable resource) {
        Bitmap src = bitmapOf(resource);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int iw = resource.getIntrinsicWidth();
        int ih = resource.getIntrinsicHeight();
        int posterW = iw > 0 && ih > 0 ? Math.round(iw * (dm.heightPixels / (float) ih)) : dm.widthPixels;
        int seamX = dm.widthPixels - posterW;
        if (src == null || seamX <= 2) {
            mBgWash = null;
            mFillTag = "washNa";
            mBinding.bgFill.setImageDrawable(null);
            Log.d(DetailLog.TAG, "wash ms=na reason=" + (src == null ? "noBitmap" : "noSeam")
                    + " seam=" + seamX + " posterW=" + posterW);
            if (mBgResource != null) logBgEvidence(mBgResource, mBgSlide, mFillTag);
            return;
        }
        final int screenW = dm.widthPixels;
        final int seam = seamX;
        final Bitmap input = src;
        final long t0 = System.currentTimeMillis();
        final int gen = mWashGen;   // β11：取本条的代际号，回来时比对 ⇒ 换源即丢
        mWashTask = () -> Task.execute(() -> {
            PosterWash.Wash w = PosterWash.build(input, WASH_WIDTH, screenW, seam,
                    WASH_PLATEAU_PX, WASH_RAMP_PX, WASH_TARGET_RATIO);
            long ms = System.currentTimeMillis() - t0;
            mHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (gen != mWashGen) {
                    // 换源/重进：这条结果属于上一条海报 ⇒ 直接丢（β8 用 80ms 兜底作废防的就是同一件事）
                    Log.d(DetailLog.TAG, "wash ms=na reason=stale gen=" + gen + " now=" + mWashGen);
                    return;
                }
                mWashTask = null;   // β11：确认这条结果仍是“当前任务”才清句柄
                if (w == null) {
                    // 延展层是观感增强，失败只留空背景，绝不因为它影响主体海报
                    mFillTag = "washNa";
                    Log.d(DetailLog.TAG, "wash ms=" + ms + " reason=buildFailed seam=" + seam);
                    if (mBgResource != null) logBgEvidence(mBgResource, mBgSlide, mFillTag);
                    return;
                }
                mBgWash = w.bitmap;
                mFillTag = "wash";
                mBinding.bgFill.setImageBitmap(w.bitmap);
                Log.d(DetailLog.TAG, "wash ms=" + ms + " px=" + w.bitmap.getWidth() + "x" + w.bitmap.getHeight()
                        + " seam=" + w.seamX + " plateau=" + WASH_PLATEAU_PX + " ramp=" + WASH_RAMP_PX
                        + " kMin=" + String.format(java.util.Locale.US, "%.3f", w.kMin)
                        + " avgK=" + String.format(java.util.Locale.US, "%.3f", w.avgK)
                        + " colMean=" + w.colMean + " srcMean=" + w.srcMean + " target=" + WASH_TARGET_RATIO);
                if (mBgResource != null) logBgEvidence(mBgResource, mBgSlide, mFillTag);
            });
        });
        mHandler.post(mWashTask);
    }

    /** β11：延展层必须用**正在显示的这张位图**（同源是接缝同值的前提）；非位图型 Drawable 才回退成离屏绘制一份。 */
    private Bitmap bitmapOf(Drawable d) {
        try {
            if (d instanceof BitmapDrawable) return ((BitmapDrawable) d).getBitmap();
            int w = d.getIntrinsicWidth();
            int h = d.getIntrinsicHeight();
            if (w <= 0 || h <= 0 || (long) w * h > 4000000L) return null;
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, w, h);
            d.draw(c);
            return b;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * T-184 背景证据行（一次加载只打一行，C grep "FireFlyDetail.*bg src="）：
     * src=取图档位 / fit=主体层适配档 / anchor=主体层锚位 / scale=主体层放大倍率 / dim·raw·alpha=文字区采样。
     * 亮度口径：Rec.601 luma（0.299R+0.587G+0.114B，γ 编码值直接取平均，0–255），与截图取色的"平均亮度"同口径、
     * **不是** WCAG 相对亮度；判据 4.5:1 由"dim ≤58 ⇒ 对白字（≈245）≥8:1"覆盖，无需另算。
     * 采样全程不依赖真机：整图缩进 ≤96×96 的 software 位图，再按两层各自的等比变换反查图内坐标（20px 网格）。
     */
    private void logBgEvidence(Drawable resource, boolean slide, String fillTag) {
        String src = slide ? "slide" : "pic";
        int iw = resource.getIntrinsicWidth();
        int ih = resource.getIntrinsicHeight();
        // β8 P1 之后主体层是 wrap_content 的“右贴边一条”，**视图宽 ≠ 屏宽** ⇒ 两个坐标必须分开取：
        // 屏幕尺寸用于文字区判据框，主体视图尺寸用于算它盖到屏幕的哪一块。
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int mvw = mBinding.bg.getWidth() > 0 ? mBinding.bg.getWidth() : screenW;
        int mvh = mBinding.bg.getHeight() > 0 ? mBinding.bg.getHeight() : screenH;
        // 整高不裁 ⇒ 缩放只由高度决定；>1.005 就是“放大了”（A 裁“不放大”⇒ 期望恒 ≤1.00，C 可据此抓违反）
        float mainScale = ih > 0 ? mvh / (float) ih : 0f;
        int mainW = mainScale > 0 ? Math.round(iw * mainScale) : mvw;
        int mainX = Math.max(0, screenW - mainW);       // layout_gravity=end ⇒ 右贴边
        int mainY = Math.round((screenH - mvh) / 2f);   // center_vertical
        String fit = "na";
        if (mainScale > 0) {
            fit = mainScale > 1.005f ? "heightUp" : (Math.abs(mainW / (float) screenW) - 1f <= 0.02f ? "fill" : "height");
        }
        String anchor = mainScale > 0 ? (mainX <= 2 ? "full" : "end") : "na";
        String scale = mainScale > 0 ? String.format(java.util.Locale.US, "%.2f", mainScale) : "na";
        int[] area = mainScale > 0 ? measureTextArea(resource, iw, ih, screenW, screenH, mainScale, mainX, mainY) : null;
        DetailLog.bgEvidence(src, fit, anchor, scale,
                area == null ? -1 : area[0], area == null ? -1 : area[1], area == null ? -1 : area[2], fillTag);
    }

    /**
     * 文字区（设计值 x≤1400、y320–900 @屏幕坐标）平均亮度自算。
     * β8 起两层的几何各自独立：
     * · 主体层＝整高、右贴边（由入参 {@code mainScale/mainX/mainY} 给出，不再假设居中）；
     * · 背景层＝竖版时的延展小图（主体左缘列横向拉伸）以 **fitXY 铺满**（{@link #mBgWash} 非空 ⇒ 按屏幕线性映射回小图，
     *   亮度斜坡已烘进像素、不再叠 setAlpha）；横版时仍是同图 centerCrop 且完全不透明（{@code mBgWash==null}）。
     * 图源自带透明时按页面底色合成（{@link #baseLuma()} 保留），口径与 T-187-b 一致。
     * 遮罩 α 用与 shape_detail_scrim.xml 同组的常量（β8：左 E6 止于 0.58），避免"日志一套、屏上一套"。
     *
     * @return {@code {dim 遮罩后, raw 遮罩前, alpha 最小采样值}}；任何异常返回 null（证据行打 na，不影响显示）
     */
    private int[] measureTextArea(Drawable resource, int iw, int ih, int vw, int vh, float mainScale, int mainX, int mainY) {
        try {
            // 取一份独立副本再设 bounds：直接把 bounds 改到 0,0,iw,ih 会污染正在显示的这张 Drawable
            Drawable copy = resource.getConstantState() == null ? null : resource.getConstantState().newDrawable();
            if (copy == null) return null;
            copy.setBounds(0, 0, iw, ih);
            float imageAr = iw / (float) ih;
            int sw = Math.max(1, Math.round(SAMPLE_MAX * imageAr));
            int sh = SAMPLE_MAX;
            if (sw > SAMPLE_MAX) {
                sw = SAMPLE_MAX;
                sh = Math.max(1, Math.round(SAMPLE_MAX / imageAr));
            }
            Bitmap tiny = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(tiny);
            canvas.scale(sw / (float) iw, sh / (float) ih);
            copy.draw(canvas);

            float mainW = iw * mainScale;
            float mainH = ih * mainScale;
            // 背景层 centerCrop 兜底（横版支路）：等比放大到铺满再居中裁切
            float fillScale = Math.max(vw / (float) iw, vh / (float) ih);
            float fillX = (vw - iw * fillScale) / 2f;
            float fillY = (vh - ih * fillScale) / 2f;
            Bitmap fillTiny = mBgWash;   // β11 竖版支路：非空＝屏上现在是这张延展小图 fitXY 铺满（水平常数＋逐列亮度斜坡）

            int right = Math.min(Math.round(vw * TEXT_RIGHT_FRACTION), vw);
            int top = Math.min(Math.round(vh * TEXT_TOP_FRACTION), vh);
            int bottom = Math.min(Math.round(vh * TEXT_BOTTOM_FRACTION), vh);
            int band = Math.round(SCRIM_BOTTOM_DP * getResources().getDisplayMetrics().density);

            long sumRaw = 0;
            long sumDim = 0;
            int minAlpha = 255;
            int count = 0;
            float base = baseLuma();
            for (int px = 0; px < right; px += SAMPLE_STEP) {
                float horizontal = xAlpha(px / (float) vw);
                for (int py = top; py < bottom; py += SAMPLE_STEP) {
                    boolean inMain = px >= mainX && px < mainX + mainW && py >= mainY && py < mainY + mainH;
                    int color;
                    if (inMain) {
                        int ix = clampInt((int) ((px - mainX) / mainScale * sw / iw), 0, sw - 1);
                        int iy = clampInt((int) ((py - mainY) / mainScale * sh / ih), 0, sh - 1);
                        color = tiny.getPixel(ix, iy);
                    } else if (fillTiny != null) {
                        int fx = clampInt((int) (px / (float) vw * fillTiny.getWidth()), 0, fillTiny.getWidth() - 1);
                        int fy = clampInt((int) (py / (float) vh * fillTiny.getHeight()), 0, fillTiny.getHeight() - 1);
                        color = fillTiny.getPixel(fx, fy);
                    } else {
                        int ix = clampInt((int) ((px - fillX) / fillScale * sw / iw), 0, sw - 1);
                        int iy = clampInt((int) ((py - fillY) / fillScale * sh / ih), 0, sh - 1);
                        color = tiny.getPixel(ix, iy);
                    }
                    int alpha = Color.alpha(color);
                    float luma = 0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color);
                    if (alpha < 255) luma = base + (luma - base) * alpha / 255f;
                    if (alpha < minAlpha) minAlpha = alpha;
                    sumRaw += luma;
                    sumDim += luma * (1f - combinedAlpha(horizontal, py, vh, band));
                    count++;
                }
            }
            tiny.recycle();
            // 图源自带透明时，透明处实际合成的是"页面底色 + 底层图"而非纯黑 ⇒ dim 是被低估的下界，
            // 由 alpha 字段告知 C 本行属"下界"，不用另开日志
            if (count == 0) return null;
            return new int[]{(int) Math.round((double) sumDim / count), (int) Math.round((double) sumRaw / count), minAlpha};
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * T-187-b：页面底色（{@code ?android:attr/colorBackground}）的 Rec.601 luma，
     * 供"铺底层被压暗后露出的底"参与合成。浅色/深色两套主题各自的底色由资源系统给出；
     * 取不到就按纯黑算（判据方向更严，不会虚报达标）。
     */
    private float baseLuma() {
        try {
            android.util.TypedValue tv = new android.util.TypedValue();
            boolean ok = getTheme().resolveAttribute(android.R.attr.colorBackground, tv, true);
            if (ok && (tv.type == android.util.TypedValue.TYPE_INT_COLOR_ARGB8
                    || tv.type == android.util.TypedValue.TYPE_INT_COLOR_RGB8)) {
                int color = tv.data;
                return 0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color);
            }
        } catch (Throwable ignored) {
            // 证据行宁可少一项，也不能因为取底色把显示流程带崩
        }
        return 0f;
    }

    /**
     * 遮罩①水平档（β8 / 海报改，与 drawable 逐值对齐）：x=0 处 α=SCRIM_LEFT_A(0.902)，
     * 线性衰减到 {@code SCRIM_STOP_FRACTION=0.58} 处为 0，0.58 之后**不再遮**（右侧海报本体不被压暗）。
     */
    private static float xAlpha(float xFraction) {
        if (xFraction >= SCRIM_STOP_FRACTION) return 0f;
        return SCRIM_LEFT_A * (1f - xFraction / SCRIM_STOP_FRACTION);
    }

    /** 遮罩②底部 120dp（判据 240px @density320）由 0 线性升到 0.6，与水平层按常规 over 叠加后返回总 α。 */
    private static float combinedAlpha(float horizontal, int py, int vh, int bandPx) {
        float vertical = 0f;
        int bandTop = vh - bandPx;
        if (bandPx > 0 && py > bandTop) vertical = SCRIM_BOTTOM_A * (py - bandTop) / (float) bandPx;
        return 1f - (1f - horizontal) * (1f - vertical);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }


    private Site getSite() {
        Site site = VodConfig.get().getHome();
        return site == null ? new Site() : site;
    }

    private void selectFlag(int position, boolean fromFocus) {
        if (mVideo == null || mVideo.getFlags().isEmpty()) return;
        position = Math.max(0, Math.min(position, mVideo.getFlags().size() - 1));
        mFlagAdapter.setSelected(position);
        mEpisodeAdapter.setItems(mVideo.getFlags().get(position).getEpisodes());
        mBinding.episodeRecycler.scrollToPosition(currentEpisodeIndex());
    }

    /** T-187-e 日志字段：最多前 3 条线路名，竖线分隔；名称来自站点 playFrom，不含 URL，可直打。 */
    private String flagNames(Vod vod) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, vod.getFlags().size()); i++) {
            if (i > 0) sb.append("|");
            sb.append(vod.getFlags().get(i).getShow());
        }
        return vod.getFlags().size() > 3 ? sb.append("|+").toString() : sb.toString();
    }

    private int currentEpisodeIndex() {
        if (mVideo == null) return 0;
        List<Episode> episodes = currentEpisodes();
        for (int i = 0; i < episodes.size(); i++) if (episodes.get(i).isSelected()) return i;
        return 0;
    }

    private List<Episode> currentEpisodes() {
        if (mVideo == null || mVideo.getFlags().isEmpty() || mFlagAdapter.selected >= mVideo.getFlags().size()) return new ArrayList<>();
        return mVideo.getFlags().get(mFlagAdapter.selected).getEpisodes();
    }

    private boolean isSingleEpisode() {
        return currentEpisodes().size() == 1;
    }

    /** 预览节流（v1.9 表）：单集=立即；单线路多集=3s；常规=1s */
    private long previewDelay() {
        if (isSingleEpisode()) return 0;
        if (mVideo != null && mVideo.getFlags().size() == 1) return 3000;
        return 1000;
    }

    private void playCurrent() {
        List<Episode> episodes = currentEpisodes();
        Episode target = null;
        for (Episode episode : episodes) if (episode.isSelected()) target = episode;
        if (target == null && !episodes.isEmpty()) target = episodes.get(0);
        play(target);
    }

    private void play(Episode episode) {
        if (mVideo == null || episode == null) return;
        stopPreview(true);
        mReturnedFromPlayer = true;
        // Round 38 / R2③：改走 startFromDetail（写入 fromDetail 标记）——播放页 BACK 判定"栈下已有详情页"，
        // 只回这张详情页，不再补开第二张。
        VideoActivity.startFromDetail(this, mKey, mId, mName, mPic, episode.getName());
    }

    /**
     * T-146（D-1 甲）：跨站点源搜索**只发一次**（进页面即后台触发；换源态若尚未搜过则补发）。
     * 复用既有 searchContent 链路，不新增网络层代码；失败/为空只影响底部源行占位，
     * **不弹错、不阻塞详情主流程、不加阻塞式 loading**（A 派单 D-1 理由）。
     *
     * @param reset true=清空已知名单重新收集（换源态重进时）；false=首次收集
     */
    private void searchSourcesOnce(boolean reset) {
        if (!reset && mSourceSearched) return;
        List<Site> sites = VodConfig.get().getSites();
        if (sites == null || sites.size() <= 1 || mName.isEmpty()) {
            // 单站点源 / 无片名：无"换源"可言，底部行显示"仅当前源"（不算失败，不打 err）
            mSourceSearched = true;
            mSourceState = SOURCE_ONLY;
            if (reset) {
                mSourceList.clear();
                mSourceKeys.clear();
            }
            DetailLog.detailState("confirm", 0, currentSourceName(), "empty");
            // T-301⑤：底部行刷新与「等名单落卡」标记随源层／换源态删除；
            // 名单与 mSourceState 保留——换源弹窗要靠它们判「单源不弹空列表」
            return;
        }
        if (reset) {
            mSourceList.clear();
            mSourceKeys.clear();
        }
        mSourceSearched = true;
        mSourceState = SOURCE_LOADING;
        mViewModel.searchContent(sites, mName, true);
    }

    /** 当前源名（日志口径 4 的 cur= 字段；站点名取不到时回落 key）。 */
    private String currentSourceName() {
        Site site = VodConfig.get().getSite(mKey);
        return site == null || site.isEmpty() ? String.valueOf(mKey) : site.getName();
    }

    private void onSearchObserved(Result result) {
        // T-146（D-1 甲）：原来只允许换源态消费搜索结果；现确认态底部源行也要用同一份名单，
        // 故放宽到"任意态都收集"，但**只喂只读适配器**，不会因此产生预览/切源副作用。
        mSourceSearched = true;
        if (result == null) {
            mSourceState = SOURCE_NONE;
            DetailLog.detailState("confirm", mSourceList.size(), currentSourceName(), "err");
            return;
        }
        for (Vod vod : result.getList()) {
            if (vod.getSite() == null || vod.getId() == null) continue;
            String uniq = vod.getSite().getKey() + "@" + vod.getId();
            if (!mSourceKeys.add(uniq)) continue;
            String vodName = vod.getName();
            if (vodName == null || !vodName.contains(mName) && !mName.contains(vodName)) continue;
            mSourceList.add(vod);
        }
        // 有数据时状态位不参与占位文案判定（占位已隐藏），保留 LOADING 语义即可
        mSourceState = mSourceList.isEmpty() ? SOURCE_NONE : SOURCE_LOADING;
        // T-301⑤：两条源行的灌数据／刷新／「等名单落卡」全部删除——名单现在只服务换源弹窗
        DetailLog.detailState("confirm", mSourceList.size(), currentSourceName(), mSourceList.isEmpty() ? "empty" : null);
    }

    /**
     * T-301②③（A 派单）：源层下线后**唯一**的换源入口——「换源」按钮弹源列表。
     *  · 名单还没回来 ⇒ 先补发一次搜索，仍不足两条就**不弹空列表**（只留一行日志，C 按 state 判原因）；
     *  · 选中即调既有 switchSource（不新增第二条切源路径）；
     *  · 弹窗关闭（选中或 BACK）后焦点回「换源」按钮。
     */
    /**
     * β19 / T-311（B 定稿）：「换源」按钮**只负责展开/收起**页内底部横排源列表。四条硬约束都在这一条口里：
     *  ①**不自动切源**——本方法与其焦点/收起路径全程不调 `switchSource`（日志带 switched=none 可证）；
     *  ②展开后焦点进**首项**（post 一帧等布局完成，`focusFirst=` 可核）；
     *  ③**单源不展开空列表**并提示（`detail_source_only`）；
     *  ④按钮再点一次＝收起，页面回到原高度（选集区不被挤压）。
     */
    private void toggleSourceRow() {
        if (mSourceRowOpen) {
            closeSourceRow("button");
            return;
        }
        if (!mSourceSearched) searchSourcesOnce(false);
        if (mSourceList.size() <= 1) {
            Notify.show(getString(R.string.detail_source_only));
            Log.d(DetailLog.TAG, "source row open count=" + mSourceList.size()
                    + " state=" + sourceStateName() + " expanded=false switched=none");
            return;
        }
        mSourceRowAdapter.setAll(mSourceList);
        // β33 / 详情页 v2 第一条：显隐改对**容器** `sourceRowWrap` 下（"源不显示时也不要出现换源文字"⇒ 标题与列表同进同退）。
        mBinding.sourceRowWrap.setVisibility(View.VISIBLE);
        mSourceRowOpen = true;
        // item= ＝名单里字段完整（站点对象非空且站点名非空）的条数；count≠item 即说明有降级卡在渲染
        Log.d(DetailLog.TAG, "source row open count=" + mSourceList.size() + " item=" + countRenderable()
                + " cur=" + currentSourceName() + " expanded=true switched=none");
        mBinding.sourceRow.post(() -> {
            RecyclerView.ViewHolder h = mBinding.sourceRow.findViewHolderForAdapterPosition(0);
            boolean first = h != null && h.itemView.requestFocus();
            Log.d(DetailLog.TAG, "source row focusFirst=" + first
                    + " via=" + (h == null ? "noHolder" : "itemView"));
        });
    }

    /** 收起换源列表（BACK 或再点按钮）：焦点回「换源」按钮，**不动当前源**（判据②③）。 */
    private void closeSourceRow(String from) {
        // β33 / 详情页 v2 第一条：收起＝GONE 容器（连同「换源」标题一起消失），内层 GridView 实例与 id 一字未动，
        // 故 :490 routeLeftRight / :773 layerEntry / :792 layerOf / :1582 focusFirst 这些**按实例取行**的引用不需要改。
        mBinding.sourceRowWrap.setVisibility(View.GONE);
        mSourceRowOpen = false;
        boolean back = mBinding.source.requestFocus();
        Log.d(DetailLog.TAG, "source row closed focusBack=" + back + " from=" + from + " switched=none");
    }

    /**
     * β20 / T-321：简介全文弹层（09 §六/§九.3＝居中卡片、可滚动、BACK 关闭回焦点）。
     * 宿主用 MaterialAlertDialog（β19/T-315 已统一去紫＋胶囊圆角，不新建第二套弹窗风格）；
     * 内容视图 dialog_detail_desc.xml＝ScrollView＋全文 TextView（12.5sp、行距 1.7）。
     * **展开/关闭都不触发任何切源切集**；关闭（BACK 或点外面）在 dismiss 监听里把焦点交回简介并留痕。
     * 简介为空时入口本身已 GONE/不可聚焦（bind 控），这里再守一道空串。
     */
    private void showDescDialog() {
        if (mDescDialogOpen || mVideo == null) return;
        String content = mVideo.getContent().replace("&nbsp;", " ");
        // β33 / 详情页 v2 第五条后半（刘先生「在点内容介绍的点开弹窗里，增加导演和演员的完整字段」）：
        // 页面上的 credits 已收成 1 行＋省略号 ⇒ 全量名单只能在弹层里给。拼接口径与 bind() 的人员行**逐字一致**
        // （同一对资源 detail_director/detail_actor、同样以 " · " 连接、同样不重排源侧分隔符）；
        // 数据走 Vod#getDirector/#getActor＝getter 层已清洗 UGC 宏（β54/T-332），无新增请求、无数据缺口。
        String director = mVideo.getDirector();
        String actor = mVideo.getActor();
        StringBuilder head = new StringBuilder();
        if (!director.isEmpty()) head.append(getString(R.string.detail_director)).append(director);
        if (!actor.isEmpty()) {
            if (head.length() > 0) head.append(" · ");
            head.append(getString(R.string.detail_actor)).append(actor);
        }
        // β34（刘先生拍板「修」）：早退判据从「简介为空」改成「**简介与人员都为空**」——
        // 无简介片现在由 credits 开这个弹层，若仍按 content.isEmpty() 早退，点了等于没反应＝入口失效，
        // 正是本批要修的那条缺陷。此处属"入口能开"的必要条件，**弹层内容与排版一行未改**。
        if (content.isEmpty() && head.length() == 0) return;
        // §六③ (a) 案：人员段在前＋空一行＋简介全文；无人员时与改前逐字相同（不留前导空行）；
        // 无简介时（credits 入口那条路）只出人员段，不留尾空行。
        String full;
        if (content.isEmpty()) {
            full = head.toString();
        } else {
            full = head.length() == 0 ? content : head.append("\n\n").append(content).toString();
        }
        DialogDetailDescBinding vb = DialogDetailDescBinding.inflate(getLayoutInflater());
        vb.descFull.setText(full);
        androidx.appcompat.app.AlertDialog dialog =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setView(vb.getRoot()).create();
        dialog.setOnDismissListener(d -> {
            mDescDialogOpen = false;
            // β34：BACK 关窗后焦点回"本次的入口"——有简介回 desc（与改前逐字相同），
            // 简介为空时 desc 是 GONE（requestFocus 必失败）⇒ 回接棒的 credits，否则焦点掉回页面顶层。
            View entry = mBinding.desc.getVisibility() == View.VISIBLE && mBinding.desc.isFocusable()
                    ? mBinding.desc : mBinding.credits;
            boolean back = entry.requestFocus();
            Log.d(DetailLog.TAG, "desc dialog closed focusBack=" + back
                    + " entry=" + (entry == mBinding.desc ? "desc" : "credits") + " len=" + full.length());
        });
        mDescDialogOpen = true;
        Log.d(DetailLog.TAG, "desc dialog open len=" + full.length());
        dialog.show();
    }

    /** 弹窗跳过原因的可读串（only＝单站点、none＝搜索无结果、loading＝名单还没回）。 */
    /** P0 日志字段：名单里数据完整（site 非空＋站点名非空）的条数，其余走弹窗内的降级卡。 */
    private int countRenderable() {
        int n = 0;
        for (Vod vod : mSourceList) {
            if (vod == null) continue;
            Site site = vod.getSite();
            if (site != null && site.getName() != null && !site.getName().isEmpty()) n++;
        }
        return n;
    }

    private String sourceStateName() {
        return mSourceState == SOURCE_ONLY ? "only" : mSourceState == SOURCE_NONE ? "none" : "loading";
    }

    /** 换源：选源后立即同步新源线路/集数（v1.5-22），可直接点集数播放 */
    private void switchSource(int position) {
        if (position < 0 || position >= mSourceList.size()) return;
        Vod target = mSourceList.get(position);
        if (target.getSite().getKey().equals(mKey) && target.getId().equals(mId)) return;
        // T-301④：切源前记下「现在正在看的这一集」的名字，交给 bind() 做同名优先保持（无选中集则留空＝按原口径）
        List<Episode> before = currentEpisodes();
        if (!before.isEmpty()) {
            int idx = currentEpisodeIndex();
            if (idx >= 0 && idx < before.size()) mKeepEpisodeName = before.get(idx).getName();
        }
        mKey = target.getSite().getKey();
        mId = target.getId();
        mName = target.getName();
        if (target.getPic() != null && !target.getPic().isEmpty()) {
            // 底图锁定：换源不刷新海报（拍板 11），仅更新收藏用的 pic 引用
            mPic = mPic == null || mPic.isEmpty() ? target.getPic() : mPic;
        }
        mBinding.progressLayout.showProgress();
        mViewModel.detailContent(mKey, mId);
    }

    // ================= 实时预览（spec §3.3 v1.9） =================

    /** 预览请求：token 防过期回调；未出画保留锁定海报绝不黑屏 */
    private void requestPreview(Episode episode, long delay) {
        // β19 / T-313（点播「预览」开关，门禁落在唯一入口上）：关掉后**不排请求、不建播放器、不出
        // `preview aspect=` 一类的预览日志**，海报底图与锁定图完全照旧（判据③；开启时行为与现在一致＝判据④）。
        // 这里只留一条可核对的日志，C 用它区分「是开关关了」与「预览失败回落海报」。
        if (!Setting.isVodPreview()) {
            Log.d(DetailLog.TAG, "preview off by=vodPreview player=none");
            cancelPreview();
            return;
        }
        if (episode == null || mVideo == null) return;
        if (episode == mPreviewEpisode) return; // 同集已在预览（重试需焦点移开再回）
        mPreviewToken++;
        final int token = mPreviewToken;
        if (mPreviewPending != null) mHandler.removeCallbacks(mPreviewPending);
        mPreviewEpisode = episode;
        if (delay > 0) {
            mBinding.previewLoading.setVisibility(View.GONE);
            mPreviewPending = () -> loadPreview(token, episode);
            mHandler.postDelayed(mPreviewPending, delay);
        } else {
            mBinding.previewLoading.setVisibility(View.VISIBLE);
            loadPreview(token, episode);
        }
    }

    private void loadPreview(int token, Episode episode) {
        if (mVideo == null || mVideo.getFlags().isEmpty() || mFlagAdapter.selected >= mVideo.getFlags().size()) return;
        Flag flag = mVideo.getFlags().get(mFlagAdapter.selected);
        String key = mKey;
        String flagName = flag.getFlag();
        String url = episode.getUrl();
        mPreviewExecutor.execute(() -> {
            boolean ok = false;
            Result result = null;
            try {
                result = SiteApi.playerContent(key, flagName, url);
                // 预览仅走直链：需 parse/嗅探/DRM 的源自动回落海报（绝不黑屏）
                ok = result != null && !result.hasMsg() && !result.needParse() && !result.getRealUrl().isEmpty() && result.getDrm() == null;
            } catch (Throwable ignored) {
            }
            final boolean ready = ok;
            final Result payload = result;
            mHandler.post(() -> {
                if (token != mPreviewToken) return;
                if (ready) startPreviewPlayer(key, payload, episode);
                else previewUnavailable();
            });
        });
    }

    private void startPreviewPlayer(String key, Result result, Episode episode) {
        releasePreviewPlayer();
        try {
            PlaySpec spec = PlaySpec.from(result, key, null).checkUa();
            MediaItem item = MediaItemFactory.from(spec);
            mPreviewPlayer = new ExoPlayer.Builder(this).setMediaSourceFactory(new ExoMediaSourceFactory()).build();
            mPreviewPlayer.setVolume(0f); // 静音预览
            mPreviewPlayer.setVideoTextureView(mBinding.preview);
            mPreviewPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) fadePreviewIn();
                    else if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) previewUnavailable();
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    previewUnavailable();
                }

                // T-302：视频尺寸/旋转/像素宽高比变化 ⇒ 重算等比变换；surface 尺寸变化同理
                @Override
                public void onVideoSizeChanged(VideoSize size) {
                    applyPreviewFit();
                }

                @Override
                public void onSurfaceSizeChanged(int width, int height) {
                    applyPreviewFit();
                }
            });
            mPreviewPlayer.setMediaItem(item);
            mPreviewPlayer.prepare();
            mPreviewPlayer.setPlayWhenReady(true);
            // T-302：先按当前已知的视频尺寸铺一次（READY 前 view 还没尺寸会在方法内自留一次重试）
            applyPreviewFit();
        } catch (Throwable ignored) {
            previewUnavailable();
        }
    }

    /** 出画淡入（v1.3：alpha 过渡，不生硬跳变） */
    private void fadePreviewIn() {
        mBinding.previewLoading.setVisibility(View.GONE);
        mBinding.preview.setVisibility(View.VISIBLE);
        // T-302：可见之后才有真实宽高 ⇒ 这里补算一次，保证首帧就是等比居中（不会先拉伸再收）
        applyPreviewFit();
        mBinding.preview.animate().alpha(1f).setDuration(300).start();
    }

    /**
     * T-302（A 派单限定的做法）：背景静音预览**保持原始宽高比＋居中留边**（letterbox / pillarbox），
     * 只走 `TextureView.setTransform(Matrix)` 这一条等比变换路径。三条禁止项都守住：
     *  · **不改** `preview` 的 layout_width/height（动了就会进 ScrollView 的测量链，牵首屏 bounds 判据）；
     *  · **不碰** `main/res/values/styles.xml` 的 `@style{Player}` 的 resize_mode（公共层，改了连带手机包）；
     *  · **不碰** bg / bgFill / PosterWash（β41 冻结链）与 scrim 遮罩层。
     * scale = min(视图宽/内容宽, 视图高/内容高)；rotation 90/270 时显示宽高互换；像素宽高比计入显示宽。
     * 日志 `preview aspect=WxH view=WxH scale=… mode=fit`（C 用它量比例：aspect 与 view 两组的宽高比一致即未拉伸）。
     */
    private void applyPreviewFit() {
        TextureView view = mBinding.preview;
        if (mPreviewPlayer == null) return;
        int vw = view.getWidth();
        int vh = view.getHeight();
        VideoSize size = mPreviewPlayer.getVideoSize();
        if (vw <= 0 || vh <= 0 || size == null || size.width <= 0 || size.height <= 0) {
            Log.d(DetailLog.TAG, "preview aspect=none view=" + vw + "x" + vh + " scale=1.000 mode=fit pending"
                    + " videoSize=" + (size == null ? "null" : size.width + "x" + size.height)
                    + " rotation=" + rotOf(size));
            if ((vw <= 0 || vh <= 0) && !mPreviewFitRetried) {   // 未布局：只补一帧，不轮询
                mPreviewFitRetried = true;
                view.post(this::applyPreviewFit);
            }
            return;
        }
        mPreviewFitRetried = false;
        int rot = rotOf(size);
        // 帧自身的显示尺寸（像素宽高比计入宽）；旋转 90/270 后**显示框**宽高互换
        float fw = size.width * size.pixelWidthHeightRatio;
        float fh = size.height;
        boolean swap = rot == 90 || rot == 270;
        float dw = swap ? fh : fw;
        float dh = swap ? fw : fh;
        float scale = Math.min(vw / dw, vh / dh);
        // TextureView 默认已把整帧**拉伸**铺满视图框，所以这里必须先按 x/y 两轴分别把拉伸量除回去，
        // 再乘目标等比尺寸——单用 setScale(scale, scale) 会把「拉伸后的框」再等比缩，比例仍是错的。
        mPreviewMatrix.reset();
        mPreviewMatrix.postScale(fw * scale / vw, fh * scale / vh, vw / 2f, vh / 2f);
        if (rot != 0) mPreviewMatrix.postRotate(rot, vw / 2f, vh / 2f);
        view.setTransform(mPreviewMatrix);
        Log.d(DetailLog.TAG, "preview aspect=" + Math.round(dw) + "x" + Math.round(dh) + " view=" + vw + "x" + vh
                + " scale=" + String.format(java.util.Locale.US, "%.3f", scale) + " mode=fit"
                + " frame=" + Math.round(fw) + "x" + Math.round(fh)
                + " rotation=" + rot + " par=" + String.format(java.util.Locale.US, "%.3f", size.pixelWidthHeightRatio));
    }

    /** media3 给的是**未施加**的旋转角；归一到 [0,360) 以便与 90/270 比较。 */
    private static int rotOf(VideoSize size) {
        if (size == null) return 0;
        int rot = size.unappliedRotationDegrees % 360;
        return rot < 0 ? rot + 360 : rot;
    }

    /** 预览不可用/失败：收回预览层，回落锁定海报模糊图 */
    private void previewUnavailable() {
        mBinding.previewLoading.setVisibility(View.GONE);
        mBinding.preview.animate().alpha(0f).setDuration(150).withEndAction(() -> mBinding.preview.setVisibility(View.GONE)).start();
    }

    /** 焦点回源/线路行：零预览请求，撤预览层回海报 */
    private void cancelPreview() {
        mPreviewToken++;
        if (mPreviewPending != null) mHandler.removeCallbacks(mPreviewPending);
        releasePreviewPlayer();
        mPreviewEpisode = null;
        previewUnavailable();
    }

    /** 进播放页前释放预览；toPlayer=true 时标记返回后静音续播当前集 */
    private void stopPreview(boolean toPlayer) {
        mPreviewToken++;
        if (mPreviewPending != null) mHandler.removeCallbacks(mPreviewPending);
        releasePreviewPlayer();
        mPreviewEpisode = null;
        previewUnavailable();
    }

    private void releasePreviewPlayer() {
        if (mPreviewPlayer != null) {
            try {
                mPreviewPlayer.stop();
                mPreviewPlayer.release();
            } catch (Throwable ignored) {
            }
            mPreviewPlayer = null;
        }
    }

    // ================= 收藏 =================

    private String historyKey() {
        if (mKey == null || mId == null) return "";
        return mKey.concat(com.firefly.tv.db.AppDatabase.SYMBOL).concat(mId).concat(com.firefly.tv.db.AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    private void toggleKeep() {
        if (mVideo == null) return;
        String key = historyKey();
        if (key.isEmpty()) {
            //T-186：key 缺段等于没有身份，写进库既读不回也修不了，只能拒写并留证（push_agent 直进详情可能没带 key/id）
            FavLog.write("add", false, key, mPic, "emptyKey");
            return;
        }
        Keep keep = Keep.find(key);
        if (keep != null) {
            keep.delete();
            FavLog.write("remove", true, key, keep.getVodPic(), null);
            RefreshEvent.keep();
        } else {
            keep = new Keep();
            keep.setKey(key);
            keep.setCid(VodConfig.getCid());
            // C 复现链核查第 2 问的落码：type 过去靠 int 默认值 0 隐式成立（KeepDao 的 getVod 是 WHERE type=0），
            // 换源态走的是同一个 toggleKeep、同一组字段来源，type 不会取到别的值；但"隐式默认"不该是判据依赖，
            // 这里显式写 0，保证任何路径（含 push_agent 直进详情）落库的点播收藏都能被首页收藏行读到。
            // 注意 type 只用于 DAO 过滤，不参与 presenter 选择——presenter 由行的 child presenter 类型决定。
            keep.setType(0);
            //T-186 第 3 条：mPic 为空也要能收藏——空串占位入库，绝不把 null 写进库
            //(null 会让下次冷启动的 DiffUtil 内容比对 NPE，整机进不去，正是本轮 P0 的根因)
            keep.setVodPic(mPic == null ? "" : mPic);
            String name = mVideo.getName();
            keep.setVodName(name == null || name.isEmpty() ? mName : name);
            Site site = getSite();
            keep.setSiteName(site.getName() == null ? "" : site.getName());
            // β18 / T-213：收藏落库时把站点给的更新进度一起存下（mVideo 此刻必非 null，:1419 已有早退守卫）
            keep.setVodRemarks(mVideo.getRemarks() == null ? "" : mVideo.getRemarks());
            keep.setCreateTime(System.currentTimeMillis());
            keep.save();
            FavLog.write("add", true, key, keep.getVodPic(), null);
            RefreshEvent.keep();
        }
        checkKeep();
    }

    private void checkKeep() {
        boolean exist = Keep.find(historyKey()) != null;
        mBinding.keep.setImageResource(exist ? R.drawable.ic_detail_keep_on : R.drawable.ic_detail_keep_off);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 播放页返回=确认态静音续播当前画面（v1.5-24；v1 重新拉起当前集预览）
        if (mReturnedFromPlayer && mVideo != null && !currentEpisodes().isEmpty()) {
            mReturnedFromPlayer = false;
            Episode current = currentEpisodes().get(currentEpisodeIndex());
            requestPreview(current, 0);
        }
    }

    /** β19 / T-311 判据②：BACK **先收换源列表**并把焦点交回「换源」按钮；列表已收起后才走系统退页（第二下）。 */
    @Override
    public void onBackPressed() {
        if (mSourceRowOpen) {
            closeSourceRow("back");
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 离开页面暂停预览渲染与网络
        if (mPreviewPlayer != null) mPreviewPlayer.setPlayWhenReady(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPreviewToken++;
        if (mPreviewPending != null) mHandler.removeCallbacks(mPreviewPending);
        // β8 / 海报：页面销毁后 80ms 兜底那条不能再回来贴图（后台线程那边另有 isDestroyed 双闸）
        if (mWashTask != null) mHandler.removeCallbacks(mWashTask);
        if (mEdgeProbeLate != null) mHandler.removeCallbacks(mEdgeProbeLate);
        mEdgeProbeLate = null;
        mWashTask = null;
        releasePreviewPlayer();
        mPreviewExecutor.shutdownNow();
    }

    /**
     * β19 / T-311：页内换源列表的适配器。
     *  · 渲染口径沿用原弹窗那一份（站点名缺失→回落片名→回落占位串；备注 null→隐藏该行；
     *    当前源＝site.key ＋ vod.id 双等 ⇒ selected ＋ 左上对勾角标），**不新增第二套卡片样式**（复用 adapter_source.xml）；
     *  · **聚焦零副作用**（判据③）：不设 OnFocusChangeListener 去切源，只有点击才 switchSource（日志 pick）；
     *  · 整批换数据用 notifyDataSetChanged 是可以的（这里刷的是"列表内容"，刷完立刻显式把焦点交给首项；
     *    与 C2 管的"选中态原地刷新"不是同一场景，见规范 08 §三 C2）。
     */
    private class SourceRowAdapter extends RecyclerView.Adapter<SourceRowAdapter.Holder> {

        private final List<Vod> items = new ArrayList<>();

        void setAll(List<Vod> list) {
            items.clear();
            if (list != null) items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(AdapterSourceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Vod item = position >= 0 && position < items.size() ? items.get(position) : null;
            Site site = item == null ? null : item.getSite();
            String name = site != null && !isEmptyText(site.getName()) ? site.getName()
                    : (item != null && !isEmptyText(item.getName()) ? item.getName() : BLANK_SITE_NAME);
            String remark = item == null || item.getRemarks() == null ? "" : item.getRemarks();
            holder.binding.name.setText(name);
            holder.binding.remark.setText(remark);
            holder.binding.remark.setVisibility(remark.isEmpty() ? View.GONE : View.VISIBLE);
            boolean current = site != null && site.getKey().equals(mKey) && item.getId().equals(mId);
            holder.binding.getRoot().setSelected(current);
            holder.binding.badge.setVisibility(current ? View.VISIBLE : View.GONE);
            holder.binding.getRoot().setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                Log.d(DetailLog.TAG, "source row pick pos=" + pos + " site=" + name
                        + " current=" + current + " count=" + items.size() + " autoSwitch=false");
                if (pos < 0 || pos >= items.size()) return;
                // 选中才切源（判据④），切源走 β16 那条既有链：重建线路/集数并尽力保持当前集（keepEpisode=hit|miss）
                switchSource(pos);
                closeSourceRow("pick");
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {

            private final AdapterSourceBinding binding;

            Holder(AdapterSourceBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    /** β19 / T-311：站点名与片名都取不到时的占位卡名（沿用原弹窗那一档口径，不出现空卡）。 */
    private static final String BLANK_SITE_NAME = "未命名站点";

    private static boolean isEmptyText(String s) {
        return s == null || s.isEmpty();
    }

    // ================= Adapter：线路行 =================

    private class FlagAdapter extends RecyclerView.Adapter<FlagAdapter.Holder> {

        private final List<Flag> items = new ArrayList<>();
        private int selected;

        public void setItems(List<Flag> list) {
            items.clear();
            if (list != null) items.addAll(list);
            selected = Math.min(selected, Math.max(0, items.size() - 1));
            notifyDataSetChanged();
        }

        /**
         * β17 / T-301② 的**抢夺源**就在这里：旧写法 `selected = position; notifyDataSetChanged();`
         * 把整行 item view 全部作废重绑，而这条链正是「线路项获焦 → 300ms 去抖 → selectFlag → 本方法」，
         * 于是焦点刚落在线路项上就被自己拆掉 ⇒ C 看到的 0.4s/1.8s/2.0s 全部漂到 desc。
         * 派单要求「改掉抢夺源，不是加守卫硬顶」⇒ 这里改成**只刷新旧选中两项**（带 payload，原地重绑）；
         * 选中态变化本来也只有这两项需要重画，语义不变、刷新量更小。
         */
        public void setSelected(int position) {
            if (position == selected) return;
            int old = selected;
            selected = position;
            changeSelection(old);
            changeSelection(selected);
        }

        private void changeSelection(int position) {
            if (position < 0 || position >= getItemCount()) return;
            notifyItemChanged(position, PAYLOAD_SELECTION);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // β20 / T-317：详情页线路 chip 改专属布局 adapter_detail_flag（单行 30px 高、12sp、统一 token、
            // 无静态 nextFocusDown 连线——R7 单一决定者）；adapter_filter.xml 留给点播分类页与手机端。
            return new Holder(AdapterDetailFlagBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            bindFlag(holder, position, null);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
            bindFlag(holder, position, payloads);
        }

        /**
         * payloads 非空＝只变选中态：原地 setChecked 就够，**不重设监听、不 setText**
         * （重设 focus listener 会让 RecyclerView 走整绑路径，正是 β17 要消除的拆 view 行为）。
         */
        private void bindFlag(@NonNull Holder holder, int position, List<Object> payloads) {
            if (payloads != null && !payloads.isEmpty()) {
                holder.binding.text.setSelected(position == selected);
                return;
            }
            Flag item = items.get(position);
            holder.binding.text.setText(item.getShow());
            holder.binding.text.setSelected(position == selected);
            // 线路行左右键=行内切值：聚焦即去抖切换（v1.8-32），零预览请求
            holder.binding.text.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) return;
                scheduleFlagSelect(holder.getBindingAdapterPosition());
                if (isSingleEpisode() && !currentEpisodes().isEmpty()) requestPreview(currentEpisodes().get(0), 0);
                else cancelPreview();
            });
            // OK=焦点下移到集数网格（v1.8-33）
            holder.binding.text.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                    // β17 / T-301② 判据④：线路项按 OK 必须留痕（旧写法只有一句 requestFocus，屏上"无反应"时日志里什么都没有）
                    // 复用本页既有 logMove 字段口径（tag=FireFlyFocus），why 位写 ok 与方向键区分；抢不到焦点就不吃键
                    boolean moved = mBinding.episodeRecycler.requestFocus();
                    logMove(v, moved ? mBinding.episodeRecycler : null, L_EPISODE, false, moved, "ok");
                    return moved;
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {

            private final AdapterDetailFlagBinding binding;

            public Holder(@NonNull AdapterDetailFlagBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private void scheduleFlagSelect(int position) {
        if (mFlagPending != null) mHandler.removeCallbacks(mFlagPending);
        mFlagPending = () -> selectFlag(position, true);
        mHandler.postDelayed(mFlagPending, SWITCH_DEBOUNCE);
    }

    /**
     * β36 / beta67 A 组：选集卡的**显示文本**四步裁剪（只作用显示，见 onBindViewHolder）。
     * <p>
     * 为什么不复用现成的 {@code Util.getNumber(String)}（main/java/com/firefly/tv/utils/Util.java:110-122）：
     * 它的兜底 {@code replaceAll("\\D+", "")} 太强——真机集名 {@code [1.20GB] DASHI2026 预告.mkv} 会因
     * {@code \b(19|20)\d{2}\b} 剥不掉 {@code DASHI2026} 里的年份（I 与 2 都是 word 字符，边界不成立），
     * 于是「预告」解析出 <b>2026</b>、「花絮01」解析出 <b>202601</b>（真 JDK 实测，见
     * {@code Test/evidence/round3-beta67/number-probe-out.txt}）⇒ 只要用「number&gt;0 算解析成功」，
     * 预告片就会显示成「第2026集」。故本规则**只在有显式集号标记时造号，不留裸数字兜底**。
     * <p>
     * 四步（A 2026-10-31 放行四答已并入）：①剥体积前缀；②剥扩展名（带 {@code (?i)}）；
     * ③非正片关键词命中⇒**不造号、保留剥后的原名**（第 3 答）；④仅 {@code EP|第|E}＋1~3 位数字才显示「第N集」。
     */
    private static final Pattern EP_LABEL_SIZE = Pattern.compile("^\\[\\d+(\\.\\d+)?(GB|MB)\\]\\s*");
    private static final Pattern EP_LABEL_EXT =
            Pattern.compile("(?i)\\.(mkv|mp4|avi|ts|flv|rmvb|mov|wmv)$");
    /**
     * 非正片关键词白名单（扩充就改这一行，{@code (?i)} 已覆盖大小写）。
     * <p>SP／PV 用 {@code (?=\d)} 前瞻而**不是** {@code \b}：{@code (?i)} 下 {@code sp01} 的 p|0 两侧都是
     * word 字符⇒ {@code \b} 不成立，实测漏判过一次（该条只因④也没命中才侥幸显示对）。
     */
    private static final Pattern EP_LABEL_NON_FEATURE = Pattern.compile(
            "(?i)(预告|花絮|特别篇|彩蛋|幕后|访谈|主题曲|片头|片尾|OST|TRAILER|SPOILER|MV|SP(?=\\d)|PV(?=\\d))");
    private static final Pattern EP_LABEL_MARK = Pattern.compile("(?i)(?:EP|第|\\bE)\\s*(\\d{1,3})");

    private static String episodeLabel(String name) {
        if (name == null || name.isEmpty()) return "";
        String stripped = EP_LABEL_SIZE.matcher(name).replaceFirst("");
        stripped = EP_LABEL_EXT.matcher(stripped).replaceFirst("");
        stripped = stripped.trim();
        if (stripped.isEmpty()) return name;                     // 剥完为空（异常集名）⇒ 回落原名，绝不显示空卡
        if (EP_LABEL_NON_FEATURE.matcher(stripped).find()) return stripped;   // 非正片⇒保留原名主体
        Matcher m = EP_LABEL_MARK.matcher(stripped);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n > 0) return "第" + n + "集";                     // E00 之类不造号，走回落
        }
        return stripped;                                          // 无显式集号标记⇒保留剥后原名，不猜号
    }

    // ================= Adapter：集数网格 =================

    private class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.Holder> {

        private final List<Episode> items = new ArrayList<>();

        public List<Episode> getItems() {
            return items;
        }

        public void setItems(List<Episode> list) {
            items.clear();
            if (list != null) items.addAll(list);
            // β20 / T-322：数据整体换掉（换线路/换源/首绑）⇒ 旧焦点记忆位作废，回落"进场定位当前集"口径。
            mEpisodeFocusPos = -1;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // β20 / T-322：详情页选集卡改专属布局 adapter_detail_episode（单行 38px 高、11sp、marquee、
            // 宽自适应只设上限）；adapter_episode.xml 留给播放页选集面板（ui/adapter/EpisodeAdapter）。
            return new Holder(AdapterDetailEpisodeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Episode item = items.get(position);
            // β36 / beta67 A 组：显示走 episodeLabel（四步裁剪，见该类上方注释）。
            // **只改 setText 的入参**——`Episode.name` 一字不动：换源同名保持读的是 switchSource() 里的
            // `mKeepEpisodeName = before.get(idx).getName()`，改字段会让它回退（故此处不写死行号，防漂移）。
            holder.binding.text.setText(episodeLabel(item.getName()));
            holder.binding.text.setSelected(item.isSelected());
            holder.binding.text.setOnClickListener(v -> play(item));
            // 集数网格焦点=预览跟随（节流见 previewDelay，v1.9 表）
            // β20 / T-322：同监听里**只记录**焦点位置供焦点记忆用（规范 C3：焦点回调里不 requestFocus）。
            holder.binding.text.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    mEpisodeFocusPos = holder.getBindingAdapterPosition();
                    requestPreview(item, previewDelay());
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {

            private final AdapterDetailEpisodeBinding binding;

            public Holder(@NonNull AdapterDetailEpisodeBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
