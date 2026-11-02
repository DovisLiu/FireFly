package com.firefly.tv.ui.activity;

import com.firefly.tv.ui.activity.DetailActivity;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.App;
import com.firefly.tv.NoticeManager;
import com.firefly.tv.Product;
import com.firefly.tv.R;
import com.firefly.tv.Updater;
import com.firefly.tv.api.config.LiveConfig;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Cache;
import com.firefly.tv.bean.Config;
import com.firefly.tv.bean.FavLog;
import com.firefly.tv.bean.Func;
import com.firefly.tv.bean.History;
import com.firefly.tv.bean.HomeResult;
import com.firefly.tv.bean.Keep;
import com.firefly.tv.bean.Result;
import com.firefly.tv.bean.Site;
import com.firefly.tv.bean.Style;
import com.firefly.tv.bean.Vod;
import com.firefly.tv.databinding.ActivityHomeBinding;
import com.firefly.tv.db.AppDatabase;
import com.firefly.tv.event.CastEvent;
import com.firefly.tv.event.ConfigEvent;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.event.ServerEvent;
import com.firefly.tv.impl.Callback;
import com.firefly.tv.impl.ConfigListener;
import com.firefly.tv.impl.SiteListener;
import com.firefly.tv.model.SiteViewModel;
import com.firefly.tv.player.extractor.Source;
import com.firefly.tv.server.Server;
import com.firefly.tv.service.DLNARendererService;
import com.firefly.tv.service.PlaybackService;
import com.firefly.tv.ui.adapter.BaseDiffCallback;
import com.firefly.tv.ui.base.BaseActivity;
import com.firefly.tv.ui.custom.CustomRowPresenter;
import com.firefly.tv.ui.custom.CustomSelector;
import com.firefly.tv.ui.custom.NavDelegate;
import com.firefly.tv.ui.custom.NavOverlay;
import com.firefly.tv.ui.custom.TopNavView;
import com.firefly.tv.ui.dialog.SiteDialog;
import com.firefly.tv.ui.presenter.FuncPresenter;
import com.firefly.tv.ui.presenter.HeaderPresenter;
import com.firefly.tv.ui.presenter.HistoryPresenter;
import com.firefly.tv.ui.presenter.KeepPresenter;
import com.firefly.tv.ui.presenter.ProgressPresenter;
import com.firefly.tv.ui.presenter.VodPresenter;
import com.firefly.tv.utils.Clock;
import com.firefly.tv.utils.FileChooser;
import com.firefly.tv.utils.ImgUtil;
import com.firefly.tv.utils.KeyUtil;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.PermissionUtil;
import com.firefly.tv.utils.ResUtil;
import com.firefly.tv.utils.UrlUtil;
import com.firefly.tv.utils.Util;
import com.github.catvod.net.OkHttp;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Lists;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;

// T-210：SiteListener 必须显式声明——SiteDialog.show() 靠 `activity instanceof SiteListener` 取回调对象，
// 漏声明时本类已有的 setSite() 方法体永远收不到调用（顶部导航「片源」静默失效的真因）。
// T-211：首页显式实现 ConfigListener——NavDelegate.openHistory() 优先用宿主自身回调，
// 这样选中仓库后仍走本页 getCallback()（showContent/Notify），保住首页刷新语义；
// 未实现该接口的页面会走 openHistory 里的 fallback（VodConfig.load + 三个刷新事件），不会再像
// 早期 HistoryDialog 那样强制转型崩，也不会静默无反应（两路都有日志留痕）。
public class HomeActivity extends BaseActivity implements SiteListener, ConfigListener, TopNavView.OnNavListener, VodPresenter.OnClickListener, HistoryPresenter.OnClickListener, KeepPresenter.OnClickListener {

    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mHistoryAdapter;
    private ArrayObjectAdapter mKeepAdapter;
    private KeepPresenter mKeepPresenter;   // T-303：与 mPresenter（历史侧）同构，删除态标志的持有者
    private ArrayObjectAdapter mAdapter;
    private HistoryPresenter mPresenter;
    private NavOverlay mOverlay;
    private SiteViewModel mViewModel;
    private Result mResult;
    private Clock mClock;
    private NoticeManager mNotice;
    private boolean mFirstContentShown; // T-152：首次内容加载后强制显示浮层（覆盖数据刷新期 position 瞬态 conceal）
    private long mIgnoreConcealUntil;   // T-152：初始化抑制窗口截止时间（此期间忽略位置联动 conceal）
    private boolean mLeavingHome;       // T-188-c：onPause 置真，onResume 消费 ⇒ 本次恢复是"从别的页面返回"
    // β47 / 新3：T-188-c 的 `mHomeBackAbsorbed`（回首页后第一次 BACK 只归位不退出）已整条删除——
    // 它是刘先生"要按三次才弹退出框"里被吞掉的第二下。字段、置位、日志字段、消费分支四处一起清，不留半截。
    // β7 / ②根治（组③「从点播 BACK 回首页第一把 UP 不稳」）：归位改成**有界多次钉**。
    // 定位结论见 Test/evidence/round1-beta37-locate-B.md —— DOWN 那一刻焦点已被恢复期的重绑/系统焦点恢复
    // 接管到内容卡（C 实测 `from=none to=none vis=true`），单次抢焦＋一帧复核钉不住，故按窗内三次有界补钉。
    private static final int PIN_MAX_ATTEMPTS = 3;      // 上限 3 次（A 硬约束①）
    private static final long PIN_WINDOW_MS = 500;      // 时间窗 500ms（A 硬约束①）
    private static final long PIN_STEP_MS = 120;        // 每次间隔一帧以上，避开同帧覆盖
    private long mPinDeadline;                          // 窗外不再钉（0＝未开过窗）
    private boolean mPinStop;                           // 本页收到任意按键 DOWN ⇒ 立即停（A 硬约束②）
    private int mPinAttempt;                            // 当前第几次，仅用于日志与让路留痕
    // β10 / 组③**改设计**（A 主调度线 2026-10-29 第二轮，见事实日志 BL 与本轮派单 §二）：
    // 时间窗（RETURN_WINDOW_MS=500）整块删除 ⇒ 换成**状态标记**。原因＝C 的 21 次间隔曲线：
    // 150/300/500/800/1200/2000ms＋看屏后再按，成功率 1/3–2/3 且**与间隔无关**，失败一律 `not-armed`
    // ⇒ 500ms 不是瓶颈，卡的是「这把 UP 的 DOWN 到底到没到本页」这一个事实，用标记表达比用计时准确。
    // 四条口径逐条对应：①回首页置真 ②**任何 ACTION_DOWN 到达即清**（唯一常规消除条件）
    // ③无武装 UP ∧ 标记真 ⇒ 归位 nav_home、吃键、并**消费标记**（每返回只生效一次）
    // ④保险：本页收到任何**非 UP** 键（含 LEFT/RIGHT/MENU）立即清。
    private boolean mReturnedNoDown;                    // 本次「从子页返回」后，DOWN 一颗都没到过本页
    private long mReturnAt;                             // 本次返回的时刻（0＝没返回过）；仅供 since= 观测，不再参与判定
    private String mReturnClearedBy = "never-set";       // 留痕：标记被谁清掉（down/foreign-key/consume/never-set）
    // β8 / 取证 H3（零行为）：焦点迁移序列。只登记观察者、只打日志；观察者挂在窗口的 ViewTreeObserver 上，
    // 用同一个实例增删（VTO 会变，必须存引用），onDestroy 里注销，不留泄漏。
    private android.view.ViewTreeObserver mChainVto;
    private android.view.ViewTreeObserver.OnGlobalFocusChangeListener mChainListener;

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private Config getConfig() {
        return VodConfig.get().getConfig();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkAction(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mResult = Result.empty();
        mBinding.splash.animate().alpha(0f).setStartDelay(800).setDuration(400).withEndAction(() -> mBinding.splash.setVisibility(View.GONE)).start();
        mClock = Clock.create(mBinding.navBar.clock);
        mBinding.progressLayout.showProgress();
        PermissionUtil.requestNotify(this);
        DLNARendererService.start(this);
        Updater.create().start(this);
        mNotice = NoticeManager.create();
        mNotice.check(this);
        setRecyclerView();
        setViewModel();
        setAdapter();
        initConfig();
        setNav();
        setLogo();
        setOverlay();
    }

    // 浮层导航（刘先生拍板；Round 10：浮层纯覆盖，内容不再平移）
    // Round 14 / T-137：撤销二级直出，不再有 navSub 联动（spec v1.10 拍板 43）
    // Round 14 / T-135（拍板 41）：动画目标改为菜单层 navMenu，LOGO/时间（navStatic）常驻不动
    private void setOverlay() {
        mOverlay = new NavOverlay(mBinding.navBar.navMenu, mBinding.navBar.topNav, R.id.nav_home);
        // β4 / T-208：探针法需要认出"焦点被系统交给了顶栏这一带（含常驻层 LOGO）"⇒ 交顶栏根 + 页面名
        mOverlay.setToolbarRoot(mBinding.navBar.getRoot());
        mOverlay.setPage("home");
        // T-142（问题 2 修复，A 裁决方案 A）：首页不挂 attach 的"导航失焦→conceal"焦点联动——
        // 首页是唯一同时挂「焦点联动 attach」与「位置联动 onChildViewHolderSelected」的页面：
        // 数据加载后 leanback 抢回焦点触发 conceal，而位置联动只在选中位置变化时回调 show，
        // 冷启动导航被收起后无自动恢复（问题 2 根因）。首页显隐改由位置联动 + 上键 reveal 驱动。
        mBinding.navBar.getRoot().post(() -> mOverlay.show());
    }

    @Override
    protected void initEvent() {
        mBinding.navBar.topNav.setListener(this);
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                // 浮层联动：回首行/无选中展开、滚离首行收起（不抢焦点）
                // T-142（方案 B）：position==-1 为 NO_POSITION 瞬态（数据刷新/焦点丢失），
                // 此前被误判为"滚离首行"收起导航；归入展开语义
                // T-152 续修：初始化抑制窗口内忽略位置联动 conceal（防"强制 show"被数据刷新期瞬态覆盖）
                if (position <= 0) {
                    mOverlay.show();
                } else if (System.currentTimeMillis() > mIgnoreConcealUntil) {
                    Log.d("FireFlyHome", "position conceal position=" + position);
                    mOverlay.conceal();
                } else {
                    Log.d("FireFlyHome", "conceal suppressed position=" + position);
                }
                if (mPresenter.isDelete()) setHistoryDelete(false);
                // T-303：同一个钩子同时判收藏侧（A 派单「两个退出钩子同时判 KeepPresenter」）
                if (mKeepPresenter != null && mKeepPresenter.isDelete()) setKeepDelete(false);
            }
        });
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            PermissionUtil.requestFile(this, allGranted -> checkType(intent));
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        }
    }

    private void checkType(Intent intent) {
        if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
            loadLive("file:/" + FileChooser.getPathFromUri(intent.getData()));
        } else {
            VideoActivity.push(this, intent.getData().toString());
        }
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(Integer.class, new HeaderPresenter());
        selector.addPresenter(String.class, new ProgressPresenter());
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), FuncPresenter.class);
        // T-186 根因（A 裁定 §一：真因是 presenter 注册缺失，不是 Keep 里的 null）：
        // getKeep() 于 :394 用 new ArrayObjectAdapter(new KeepPresenter(this)) 造「我的收藏」行塞进本网格，
        // 而 CustomSelector 对 ListRow 是按「行内 child presenter 的类型」查注册表的，
        // 这张表原先只有 VodPresenter/FuncPresenter/HistoryPresenter 三档 ⇒ KeepPresenter 走到 superclass 链
        // 也匹配不到 ⇒ getPresenter 返回 null ⇒ ItemBridgeAdapter.onCreateViewHolder 直接 NPE（C 抓到 3 次同栈）。
        // 这条注册同时解释了刘先生看到的两点与 C 的精确复现链（beta24，Test-result §5）：
        //   1) 「点了收藏才出现」——收藏行为空时整行不插入（getKeep 只在 items 非空时 add），
        //      一旦收藏表有第一条（详情页 toggleKeep / 直播 addKeep / 远端 sync），行被插入、下一次 layout 必崩；
        //   2) 「详情页当场就崩、不必先回首页」——BaseActivity 在 onCreate 注册 EventBus、onDestroy 才注销
        //      （leanback BaseActivity:48/122），首页在详情页前台期间仍是订阅者，toggleKeep 末尾的
        //      RefreshEvent.keep() 会立刻在主线程跑本 getKeep() 并 notifyItemInserted，
        //      首页窗口此时仍 attached ⇒ 当帧布局即命中 null presenter ⇒「Toast 收藏成功刚出→进程被杀」；
        //   3) 「多页都崩」——行宿主只有首页网格（ItemBridgeAdapter 全仓仅 Home/Type/Collect 三处，
        //      Detail/Live 用普通 RecyclerView.Adapter 不在此路径），但装配由 KEEP/SIZE 事件在任何页面触发，
        //      崩前窗口能同时看到 Detail/Collect/Vod/Home 四个页面痕迹；
        //   4) 「栈顶是 SingleRow.appendVisibleItems」——GridLayoutManager 把每个 ListRow 映射成一个 SingleRow
        //      （首页每行都是横向网格行），收藏行正是这种横向单行，与栈形态一致。
        // 换源态不是独立成因：两态共用同一个 keep 按钮（:189 单绑定）与同一个 toggleKeep，
        // Keep.type 也不进 presenter 选择（只用于 DAO 的 WHERE type=0/1 过滤），详见交付回报第 2 条。
        // 档位取与推荐行同款 CustomRowPresenter(16)：KeepPresenter 用的就是 adapter_vod_rect 竖版海报卡。
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), KeepPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16, FocusHighlight.ZOOM_FACTOR_SMALL, HorizontalGridView.FOCUS_SCROLL_ALIGNED), HistoryPresenter.class);
        // A 裁定 §一要求的「改后有 presenter」正向证据：注册表建好即自证一次；
        // 与 CustomSelector 的反向证据（err=NoPresenter 不再出现）配对，C 真机 dump 两个字段任一即可判定。
        Log.d("FireFlyFav", "registered rows=ListRow{Vod|Func|Keep|History} keep=ListRow->KeepPresenter");
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        // β19 / T-308 同步紧凑：行标题 24sp→14sp 后标题行盒自己收了约 27px，若行与行之间仍留 16dp，
        // 视觉上就是刘先生说的「字小了、行还那么高」⇒ 行距 16dp→10dp（≈20px），与标题一起收约 47px/行。
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(10));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, result -> {
            // β49 / 裁决2（A）：占位行现在是可聚焦落点（ProgressPresenter），若此刻焦点就在它上面，
            // 数据到位把它移除时会连焦点一起摘掉 ⇒ 这里做**同一次回调里的焦点转移**（事件驱动，由"数据到了"触发，
            // 不是对那把 DOWN 的补投/延时重试）。新行要等本帧 layout 才存在，所以转移挂在一次性 pre-draw
            // （排在 layout 之后、draw 之前）上——这是唯一能拿到真实子行的时机，post 会跑在 layout 之前。
            boolean focusOnPlaceholder = isProgressFocused();
            mAdapter.remove("progress");
            addVideo(mResult = result);
            HomeResult.put(result);
            Cache.clear().put(result);
            if (focusOnPlaceholder) transferFocusFromPlaceholder();
        });
    }

    private void setAdapter() {
        mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        // 标题与行按数据有无动态插拔（规格 §3.1：无内容整行隐藏），顺序=历史→收藏→推荐
    }

    /** 标题插入位置：紧跟前一个有内容的区块之后（历史→收藏→推荐固定顺序） */
    private int titleIndexFor(int titleRes) {
        int before = titleRes == R.string.home_history ? -1 : titleRes == R.string.home_keep ? R.string.home_history : R.string.home_keep;
        if (before == -1) return 0;
        int anchor = mAdapter.indexOf(before);
        if (anchor < 0 && titleRes == R.string.home_recommend) anchor = mAdapter.indexOf(R.string.home_history);
        if (anchor < 0) return 0;
        int idx = anchor + 1;
        if (idx < mAdapter.size() && mAdapter.get(idx) instanceof ListRow) idx++;
        return idx;
    }

    private void setNav() {
        mBinding.navBar.topNav.setCurrent(R.id.nav_home);
    }

    // T-137（spec v1.10 拍板 43）：撤销一级导航"点播"二级直出——原 navSub 浮层
    // （onNavHover/showNavSub/hideNavSub/enterVod/SubAdapter）整体移除；
    // 点播分类（二级）与筛选（三级）由点播页承载，一级导航只做页面跳转。
    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                showContent();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                showContent();
            }
        };
    }

    private void showContent() {
        mBinding.progressLayout.showContent();
        checkAction(getIntent());
        setFocus();
        // T-152（问题 2 续修，C beta4 实测：3 次冷启动菜单栏始终隐藏、焦点空）：数据加载完成后强制 show()，
        // 覆盖数据刷新期 RecyclerView 选中位置瞬态回调触发的 conceal；仅首次内容加载做（之后恢复位置联动语义）
        if (!mFirstContentShown) {
            mFirstContentShown = true;
            // T-152 续修（C beta6 实测：冷启动 3/8/10/16s 菜单仍隐藏）：初始化抑制窗口延长到 2000ms
            // （C 观察 16s 内隐藏，600ms 不足以覆盖数据刷新期连续 position 回调）；
            // show 后位置联动的 conceal 在窗口内被忽略，窗口过后恢复"滚离首行收起"语义。
            mIgnoreConcealUntil = System.currentTimeMillis() + 2000;
            mOverlay.show();
            mBinding.recycler.post(() -> mOverlay.show());
            App.post(() -> mOverlay.show(), 500);
            Log.d("FireFlyHome", "showContent forceShow 3-layer (suppress 2000ms)");
        }
    }

    private void loadLive(String url) {
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                LiveActivity.start(getActivity());
            }
        });
    }

    private void setFocus() {
        mBinding.navBar.topNav.getBinding().navHome.setSelected(true);
        mBinding.navBar.topNav.getBinding().navHome.setFocusable(true);
        // post 兜底：数据加载/ProgressLayout 切换后焦点可能被内容区抢走
        mBinding.navBar.topNav.post(() -> {
            if (!mBinding.navBar.topNav.hasFocus() && getCurrentFocus() == null) mBinding.navBar.topNav.getBinding().navHome.requestFocus();
            if (!mBinding.navBar.topNav.hasFocus()) mBinding.navBar.topNav.getBinding().navHome.requestFocus();
            // T-152：焦点回导航即显示浮层（T-142 去掉 attach 后的配套，保证菜单不持续隐藏）
            mOverlay.show();
        });
    }

    private void getVideo() {
        mResult = Result.empty();
        int titleIndex = mAdapter.indexOf(R.string.home_recommend);
        int index = titleIndex >= 0 ? titleIndex + 1 : mAdapter.size();
        boolean gone = mAdapter.indexOf("progress") == -1;
        if (titleIndex >= 0 && mAdapter.size() > index) mAdapter.removeItems(index, mAdapter.size() - index);
        if (gone) mAdapter.add("progress");
        mViewModel.homeContent();
    }

    private void addVideo(Result result) {
        if (result.getList().isEmpty()) return;
        if (mAdapter.indexOf(R.string.home_recommend) < 0) mAdapter.add(titleIndexFor(R.string.home_recommend), R.string.home_recommend);
        Style style = result.getStyle(getHome().getStyle());
        if (style.isList()) mAdapter.addAll(mAdapter.size(), result.getList());
        else addGrid(result.getList(), style);
    }

    private void addGrid(List<Vod> items, Style style) {
        List<ListRow> rows = new ArrayList<>();
        VodPresenter presenter = new VodPresenter(this, style);
        for (List<Vod> part : Lists.partition(items, Product.getColumn(style))) {
            ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
            adapter.addAll(0, part);
            rows.add(new ListRow(adapter));
        }
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private void getHistory() {
        getHistory(false);
    }

    private void getHistory(boolean renew) {
        List<History> items = History.get();
        int titleIndex = mAdapter.indexOf(R.string.home_history);
        boolean rowExist = titleIndex >= 0 && mAdapter.size() > titleIndex + 1 && mAdapter.get(titleIndex + 1) instanceof ListRow;
        if (renew) mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        // T-163（拍板 50，刘先生 beta7 反馈六.4：查看继续观看后返回首页，该行重复多出一栏）：
        // 原逻辑仅 items 空或 renew 时移除旧行，非 renew 刷新走 add 追加 → 重复；改为有旧行即移除再重建。
        if (rowExist) mAdapter.removeItems(titleIndex + 1, 1);
        if (!items.isEmpty()) {
            if (titleIndex < 0) {
                titleIndex = titleIndexFor(R.string.home_history);
                mAdapter.add(titleIndex, R.string.home_history);
            }
            mAdapter.add(titleIndex + 1, new ListRow(mHistoryAdapter));
        } else if (titleIndex >= 0) {
            // 无内容整行隐藏（规格 §3.1）：标题一并移除
            mAdapter.removeItems(titleIndex, 1);
        }
        mHistoryAdapter.setItems(items, new BaseDiffCallback<History>());
    }

    private void getKeep() {
        // T-186（P0）：收藏行装配整体兜底。单条坏数据由 Keep.loadVodSafe() 逐条跳过，
        // 装配本身再出任何异常也只让"收藏这一行不显示"，绝不冒泡到冷启动。
        try {
            List<Keep> items = Keep.loadVodSafe();
            int titleIndex = mAdapter.indexOf(R.string.home_keep);
            boolean rowExist = titleIndex >= 0 && mAdapter.size() > titleIndex + 1 && mAdapter.get(titleIndex + 1) instanceof ListRow;
            // T-212（β2 裁定乙）：原先收藏侧「只删标题、从不删 ListRow」，与 getHistory()（先删行再重建）不对称
            // ⇒ 切到无收藏的仓库时 in=0 走空分支，标题没了却残留一排无标题的旧卡（判据要的是「整行隐藏」）。
            // 现与历史侧同构：有旧行先移除，再按数据有无重建；titleIndex 指向标题本身，删 titleIndex+1 不影响其值。
            if (rowExist) mAdapter.removeItems(titleIndex + 1, 1);
            if (!items.isEmpty()) {
                // T-303：Presenter 实例一并留存（原先只留 adapter，无法设/退删除态）。
                if (mKeepAdapter == null) mKeepAdapter = new ArrayObjectAdapter(mKeepPresenter = new KeepPresenter(this));
                if (titleIndex < 0) {
                    titleIndex = titleIndexFor(R.string.home_keep);
                    mAdapter.add(titleIndex, R.string.home_keep);
                }
                mAdapter.add(titleIndex + 1, new ListRow(mKeepAdapter));
                mKeepAdapter.setItems(items, new BaseDiffCallback<Keep>());
            } else if (titleIndex >= 0) {
                mAdapter.removeItems(titleIndex, 1);
            }
            FavLog.ui("home", true, null);
        } catch (Throwable t) {
            FavLog.ui("home", false, t.getClass().getSimpleName());
        }
    }

    private void setHistoryDelete(boolean delete) {
        mPresenter.setDelete(delete);
        mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
    }

    /** T-303：逐句照 :459-462 的 setHistoryDelete——同一重绑方式，不另造第二条刷新路径。 */
    private void setKeepDelete(boolean delete) {
        mKeepPresenter.setDelete(delete);
        mKeepAdapter.notifyArrayItemRangeChanged(0, mKeepAdapter.size());
    }

    private void clearHistory() {
        int titleIndex = mAdapter.indexOf(R.string.home_history);
        if (titleIndex >= 0) {
            mAdapter.removeItems(titleIndex + 1, 1);
            // 清空后整行隐藏
            mAdapter.removeItems(titleIndex, 1);
        }
        History.clear(VodConfig.getCid());
        mPresenter.setDelete(false);
        mHistoryAdapter.clear();
    }

    private void setLogo() {
        // 品牌统一：使用静态横版 LOGO（不再加载点播源配置的 logo，避免未配置时空白）
        mBinding.navBar.logo.setImageResource(R.drawable.logo_lockup);
        mBinding.navBar.logo.setFocusable(true);
        mBinding.navBar.logo.setFocusableInTouchMode(true);
        mBinding.navBar.logo.setOnFocusChangeListener((v, hasFocus) -> v.animate().scaleX(hasFocus ? ResUtil.FOCUS_SCALE : 1f).scaleY(hasFocus ? ResUtil.FOCUS_SCALE : 1f).setDuration(150).start()); // 与菜单项同款缩放反馈
        // T-211 扩围：首页也走全站唯一收口 NavDelegate.openHistory()（行为＝β1 的 HistoryDialog.vod()，
        // 只是把日志与回调注入统一到一个地方）；本类已 implements ConfigListener ⇒ 用宿主自身回调，
        // 保留原有的「加载后 showContent()」语义，不改首页刷新行为。
        mBinding.navBar.logo.setOnClickListener(v -> NavDelegate.openHistory(this));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                RefreshEvent.history();
                RefreshEvent.home();
                RefreshEvent.keep();
                setLogo();
                break;
            case BOOT:
                LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                getVideo();
                break;
            case HISTORY:
                getHistory();
                break;
            case KEEP:
                getKeep();
                break;
            case SIZE:
                getVideo();
                getHistory(true);
                getKeep();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        switch (event.type()) {
            case SEARCH:
                SearchActivity.start(this, event.text());
                break;
            case PUSH:
                VideoActivity.push(this, event.text());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (VodConfig.get().getConfig().equals(event.config())) {
            VideoActivity.cast(this, event.history());
        } else {
            VodConfig.load(event.config(), getCallback(event));
        }
    }

    private Callback getCallback(CastEvent event) {
        return new Callback() {
            @Override
            public void success() {
                onCastEvent(event);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) mViewModel.action(getHome().getKey(), item.getAction());
        else if (getHome().isIndex()) CollectActivity.start(this, item.getName());
        else DetailActivity.start(this, getHome().getKey(), item.getId(), item.getName(), item.getPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction()) return false;
        CollectActivity.start(this, item.getName());
        return true;
    }

    @Override
    public void onItemClick(History item) {
        DetailActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(History item) {
        mHistoryAdapter.remove(item.delete());
        if (mHistoryAdapter.size() > 0) return;
        int titleIndex = mAdapter.indexOf(R.string.home_history);
        if (titleIndex >= 0) {
            mAdapter.removeItems(titleIndex + 1, 1);
            mAdapter.removeItems(titleIndex, 1);
        }
        mPresenter.setDelete(false);
    }

    @Override
    public void onItemClick(Keep item) {
        Config config = Config.find(item.getCid());
        if (config == null) CollectActivity.start(this, item.getVodName());
        // T-212（β1）：原写法在这里 `VodConfig.load(config, …)` —— 用户点一下收藏就把全局片仓悄悄换掉了，
        // 正是 A 判据「不得默默换源」的违规路径。现在展示链已按「当前仓库 + 当前首页源」过滤
        // （Keep.getVodCurrent / History.get），跨仓收藏本不该进列表；此处保留为兜底，**只提示、不换仓**。
        else if (item.getCid() != VodConfig.getCid()) {
            Log.d("FireFlyHome", "keep crossRepo blocked itemCid=" + item.getCid() + " curCid=" + VodConfig.getCid());
            Notify.show(getString(R.string.home_keep_other_repo));
        } else DetailActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    /** T-303：照 onItemDelete(History) 同一份写法——删调用已有 Keep.delete()（落 KeepDao），删空即整行隐藏。 */
    @Override
    public void onItemDelete(Keep item) {
        mKeepAdapter.remove(item.delete());
        if (mKeepAdapter.size() > 0) return;
        int titleIndex = mAdapter.indexOf(R.string.home_keep);
        if (titleIndex >= 0) {
            mAdapter.removeItems(titleIndex + 1, 1);
            mAdapter.removeItems(titleIndex, 1);
        }
        mKeepPresenter.setDelete(false);
    }

    /**
     * T-303（A 派单）：收藏卡长按＝与历史同一机制（框架 setOnLongClickListener 默认阈值，
     * **不得新增自定义长按时长**）。
     * **「删除态再长按＝清空」本次不做**：历史侧那条是无确认直接 clear，收藏删了不可恢复，等刘先生拍板；
     * 所以已在删除态时这里 return false 不消费该手势（也不清空），交回默认路由。
     */
    @Override
    public boolean onLongClick(Keep item) {
        if (mKeepPresenter != null && mKeepPresenter.isDelete()) {
            Log.d("FireFlyHome", "keep longClick ignored (already delete mode, clear-not-authorized)");
            return false;
        }
        setKeepDelete(true);
        return true;
    }

    @Override
    public boolean onLongClick() {
        if (mPresenter.isDelete()) clearHistory();
        else setHistoryDelete(true);
        return true;
    }

    public void showDialog() {
        SiteDialog.create().show(this);
    }

    // TV 2.0 顶部胶囊导航路由（TopNavView.OnNavListener）
    @Override
    public void onNavHome() {
        // 首页页内：无跳转
    }

    @Override
    public void onNavSearch() {
        SearchActivity.start(this);
    }

    // 点播：进点播频道页，二级菜单=源分类（源返回什么就列什么）
    @Override
    public void onNavVod() {
        if (mResult == null) {
            Notify.show(getString(R.string.home_vod_empty));
            return;
        }
        VodActivity.start(this, mResult);
    }

    // 片源：站点切换面板（§2.1 拍板：QA-2 关闭）
    @Override
    public void onNavSource() {
        showDialog();
    }

    @Override
    public void onNavLive() {
        // T-141（spec §3.5 拍板 9 + v1.10 拍板 44）：直播入口先进独立选台页（不再直达播放）
        LiveSelectActivity.start(this);
    }

    @Override
    public void onNavSetting() {
        SettingActivity.start(this);
    }

    public void onRefresh() {
        getVideo();
    }

    @Override
    public void setSite(Site item) {
        // T-210（扩围批准）：三个片源入口（首页导航 / 点播页 / 搜索页）统一打这条落库留痕，
        // 与 SiteDialog 的 `siteDialog host=… siteListener=…` 配对——C 只靠 logcat 就能判「回调真的进来了、写到哪个站」，
        // 站点键按脱敏口径只打长度。
        Log.d("FireFlyHome", "site set by=Home keyLen=" + (item == null ? 0 : item.getKey().length()));
        VodConfig.get().setHome(item);
    }

    /**
     * T-211：LOGO →「点播历史」选中一条配置后的落地，语义与 SettingActivity.setConfig 一致——
     * file:// 仓库先取存储权限，其余直接加载；成功/失败复用本页 getCallback()（showContent + Notify）。
     */
    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) {
            PermissionUtil.requestFile(this, allGranted -> VodConfig.load(config, getCallback()));
        } else {
            VodConfig.load(config, getCallback());
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // β8 / 取证 H1（A 裁定：本轮只取证、不改行为，零行为变更）：**每一个到达本页窗口的按键事件**先记一行。
        // C 的二轮数据＝失败三次全是 `not-armed` 且前后没有 `probe arm=clear` ⇒ 只剩两种解释：
        //   ①-a 这把 UP 的 ACTION_DOWN 根本没进 Home 窗口（还落在正在收尾的点播页）；
        //   或 Home 的 dispatchKeyEvent 在 DOWN 时压根没被调到。
        // 这一行就是“到没到”的唯一裁判：失败那次若**没有** `home key action=0 code=19`，①-a 坐实；
        // 有 action=0 而 UP 侧 `not-armed` ⇒ 说明 DOWN 到了但被别的东西抢先作废（另查）。
        // 带着 focus= 是为了同时回答 H3“DOWN 那一刻焦点在哪”。
        Log.d("FireFlyHome", "home key action=" + event.getAction() + " code=" + event.getKeyCode()
                + " t=" + System.currentTimeMillis() + " focus=" + pinViewName(getCurrentFocus()));
        // β10 / 组③第一步（替代 β9 的 mSawUpDown 记法）：按 A 的口径，**任何 ACTION_DOWN 到达本页**就把标记清掉；
        // 再加一道保险——**非 UP 的任意事件**（含 LEFT/RIGHT/MENU 的 UP）也清。语义＝用户的手已经落在这页 ⇒ 归位交回正常轨道。
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            clearReturnFlag("down");
        } else if (event.getKeyCode() != KeyEvent.KEYCODE_DPAD_UP) {
            clearReturnFlag("foreign-key");
        }
        // β4 / T-208：探针记账（只记 UP 的 ACTION_DOWN，长按 repeat>0 一律清空），不接管按键
        mOverlay.onKeyDown(event, getCurrentFocus());
        // β7 / ②让路闸（A 硬约束②）：本页一收到任意按键 DOWN，立刻停止恢复窗内的补钉——
        // 用户的手已经落在键盘上，焦点归属由这次键决定，不再由我们在半途拽回胶囊。
        if (event.getAction() == KeyEvent.ACTION_DOWN && mPinAttempt > 0 && !mPinStop) {
            mPinStop = true;
            Log.d("FireFlyHome", "navHome pin stop by=key code=" + event.getKeyCode() + " attempt=" + mPinAttempt);
        }
        if (KeyUtil.isMenuKey(event)) showDialog();
        // β15 / T-223 方案 A（A 裁定 2026-10-29）：**UP 的落点在这把键的 DOWN 阶段定死**——顶栏可见 ∧ 焦点在
        // 内容区 ∧ 已顶到边时直接 `requestFocusItem(nav_home)`，不再把键交给系统几何搜索，因此**不产生第一落点**；
        // 非首排（上方还有页内目标）时不改派 ⇒ 页内上移一行的旧语义不变。
        // 抬起由探针的 `already-on-nav` → `UP_EAT` 吃掉 ⇒ 一次键、一个落点（判据 `mode=up -> nav single`）。
        // 条件、长按护栏与"抢不到焦点就不吃键"全部在 `NavOverlay.revealOnDown()` 内一条口收口。
        // **只改首页**：其余 7 页（点播/搜索/合集/选台/直播/收藏/历史）仍走原两段式，下批统一（A 派单）。
        // 与组③不冲突：`orphanUp`（下面 :741-753 那段，已冻结）要求 **ACTION_UP** ∧ `mReturnedNoDown`；
        // 本档只吃 **ACTION_DOWN**，且 DOWN 一到上面 :690 已 `clearReturnFlag(down)` ⇒ 同一次按键不会两者都命中。
        if (mOverlay.revealOnDown(event, getCurrentFocus())) return true;
        if (KeyUtil.isActionDown(event) & KeyUtil.isDownKey(event)) {
            // 下键路由：导航/焦点丢失 → 内容区首个可聚焦项（T-137 撤销二级直出，点播分类由点播页承载）
            // T-147（C 实测：导航聚焦时连按 DOWN 焦点不动、DOWN 被吞）根因：原实现只对
            // recycler.getChildAt(0) 调 requestFocus——首个子项是非聚焦表头时失败且 return false 吞键；
            // 改为深度优先找首个可聚焦后代，找不到则回落 super 不吞键。
            View focus = getCurrentFocus();
            // β31 修：旧条件 `focus.getParent() == topNav` 对胶囊**恒 false**（胶囊父级是
            // view_top_nav.xml 的内层根；本文件 773-775 行注释早已点明这条守卫失真）。
            // ⇒ 导航上按 DOWN 永远进不了"落内容区首项"分支，交给系统 focusSearch 又无候选，
            // 表现为"焦点钉在导航条、只能左右"（刘先生 2026-10-31 实测）。
            // 现改为**向上找祖先**判是否属于导航条（含胶囊/内层根/LOCAL 位）。
            if (focus == null || isInsideNavBar(focus)) {
                // β48 / 新5（C 实测：冷启第一下 DOWN 不动、第二下才落卡；二级页退回首页一次就落）：
                // 行为**一字未改**（仍是"交得出就吃、交不出放行系统"），只把这一拍的四个事实打出来，用来分辨两种成因——
                // ①rows=0 或 target=none＝冷启时内容区还没有可聚焦行（数据未到、只有 progress 占位），放行给系统后
                //   导航条本身没有向下候选（view_top_nav.xml 的 nextFocus 连线实测为 0），于是"没有可见结果"；
                // ②rows>0 且 target 有值而 gave=false＝交不出去（另一类问题，须单独查）。
                // 另留一个读码事实：findFirstFocusable（本文件下方）在**搜索过程中就对候选调 requestFocus**，
                // 它返回的 target 已是它自己试着聚焦过的那个 ⇒ 这里的 gave 是第二次聚焦的结果，不是纯探测。
                View target = findFirstFocusable(mBinding.recycler);
                boolean gave = target != null && target.requestFocus();
                Log.d("FireFlyHome", "home down nav focus=" + focusTag(focus)
                        + " rows=" + mBinding.recycler.getChildCount()
                        + " target=" + focusTag(target) + " gave=" + gave);
                if (gave) return true;
                return super.dispatchKeyEvent(event);
            }
        }
        // β4 / T-208（A 裁定 1）：上键顶边判定**整体换成共享探针**（NavOverlay#probeUp + revealToPage）。
        // 删掉的三条旧守卫：`canReveal()`（T-188-b 起恒真，已是死前提）、`focus.getParent()==topNav`
        // （胶囊的父级是 view_top_nav.xml 的内层根，不是 TopNavView ⇒ 恒 false）、
        // `NavOverlay.isTopPosition(recycler)`（VerticalGridView 的 position 里**标题行占独立位**，
        // 首排卡片恒 ≥1 ⇒ 恒 false）。三条同时失真，UP 才会落到系统几何搜索：
        // 靠左命中同水平带 LOGO、靠右命中 点播/直播 邻项（刘先生实测）。
        int verdict = mOverlay.probeUp(event, getCurrentFocus());
        if (verdict == NavOverlay.UP_EAT) return true;
        if (verdict == NavOverlay.UP_REVEAL) return mOverlay.revealToPage();
        // β10 / 组③第二步（H1 的正当修复，判据从计时改成状态）：
        // 能走到这里说明探针对本把 UP 的判定是 UP_NONE＝**没武装**，而唯一能造成"没武装"的成因
        // 已由 C 坐实（失败样本只有 action=1、没有 action=0 ⇒ 这把 UP 的 DOWN 落在正在收尾的子页窗口）。
        // 三条约束怎么满足：①**标记为真**才补 ⇒ DOWN 正常到达的按键一律走 β4 探针链路（标记早就被清了）；
        // ②**一次即消**＝命中后 clearReturnFlag("consume")，同一个返回不重复劫持；
        // ③**不设时间上限**（A 明令默认无上限；若加，代价见交包 §五）⇒ 用户隔 1s/3s/10s 再按都接得住。
        // 落点仍走与正常顶边**同一条** `revealToPage()`，不新增落点逻辑；抢不到焦点就不吃键（β26 教训）。
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
            if (mReturnedNoDown) {
                clearReturnFlag("consume");
                boolean moved = mOverlay.revealToPage();
                Log.d("FireFlyHome", "no-arm orphanUp -> nav since=" + (System.currentTimeMillis() - mReturnAt)
                        + " moved=" + moved + " clearedBy=consume");
                return moved;
            }
            // 同一条 UP 没接住时留一行为什么（C 用它区分「被按键清了」和「压根没返回过」）。
            // 这条只在 UP_NONE 分支里打，正常按键到不了 ⇒ 不产生刷屏。
            Log.d("FireFlyHome", "no-arm orphanUp skipped since=" + (mReturnAt > 0 ? System.currentTimeMillis() - mReturnAt : -1)
                    + " clearedBy=" + mReturnClearedBy);
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * β10 / 组③：状态标记的唯一清除口（A 派单 §二）。
     * 只在标记**当前为真**时打留痕，避免每把键都刷一行；by 只用于日志与事后判读，不参与判定。
     */
    private void clearReturnFlag(String by) {
        if (!mReturnedNoDown) return;
        mReturnedNoDown = false;
        mReturnClearedBy = by;
        Log.d("FireFlyHome", "return flag clear by=" + by + " since=" + (System.currentTimeMillis() - mReturnAt));
    }

    /** β31：焦点是否在导航条内（胶囊父级是 view_top_nav 的内层根，不能只比一层父子）。 */
    private boolean isInsideNavBar(View v) {
        View navRoot = mBinding.navBar.getRoot();
        while (v != null) {
            if (v == navRoot || v == mBinding.navBar.topNav) return true;
            android.view.ViewParent p = v.getParent();
            v = (p instanceof View) ? (View) p : null;
        }
        return false;
    }

    /** T-147：深度优先取首个可聚焦后代（跳过表头/不可聚焦行），用于"下键进内容"兜底 */
    /**
     * β49 / 裁决2：焦点当前是否落在"加载中"占位行上（占位行本身，或其内部任意子 View）。
     * 只在结果回调**移除占位之前**调用；判据走 adapter 位置→itemView，比对持有焦点的行，不靠类名或 id。
     */
    private boolean isProgressFocused() {
        int position = mAdapter.indexOf("progress");
        if (position < 0) return false;
        View focus = getCurrentFocus();
        if (focus == null) return false;
        RecyclerView.ViewHolder holder = mBinding.recycler.findViewHolderForAdapterPosition(position);
        if (holder == null || holder.itemView == null) return false;
        return holder.itemView == focus || holder.itemView.hasFocus();
    }

    /**
     * β49 / 裁决2：把焦点从被移除的占位行交给内容区首个可聚焦行（一次性 pre-draw，同一次数据回调内注册）。
     * 落空时**不让焦点丢失**：交回导航「首页」胶囊（可见结果，不算丢键），并留痕一行给 C 对账。
     */
    private void transferFocusFromPlaceholder() {
        mBinding.recycler.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mBinding.recycler.getViewTreeObserver().removeOnPreDrawListener(this);
                View target = findFirstFocusable(mBinding.recycler);
                boolean gave = target != null && target.requestFocus();
                if (!gave) gave = mBinding.navBar.topNav.requestFocusItem(R.id.nav_home);
                Log.d("FireFlyHome", "home placeholder transfer target=" + focusTag(target) + " gave=" + gave);
                return true;
            }
        });
    }

    /** β48 / 新5：`类名#资源entry名[左,上][右,下]`——与选台页 viewTag 同形式，便于和 uiautomator dump 的 bounds 直接对。 */
    private String focusTag(View v) {
        if (v == null) return "none";
        String name = "";
        int id = v.getId();
        if (id != View.NO_ID && (id & 0xFF000000) == 0x7F000000) {
            try {
                name = "#" + getResources().getResourceEntryName(id);
            } catch (Throwable ignored) {
                name = "";
            }
        }
        int[] loc = new int[2];
        v.getLocationInWindow(loc);
        return v.getClass().getSimpleName() + name + "[" + loc[0] + "," + loc[1] + "]["
                + (loc[0] + v.getWidth()) + "," + (loc[1] + v.getHeight()) + "]";
    }

    private View findFirstFocusable(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            if (child.isFocusable() && child.isEnabled() && child.requestFocus()) return child;
            if (child instanceof ViewGroup) {
                View found = findFirstFocusable((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
        mNotice.recheck(this);
        // T-152（第三时机）：返回首页时幂等兜底显示浮层（show 不抢焦点、幂等）
        if (mOverlay != null) {
            mOverlay.show();
            Log.d("FireFlyHome", "onResume show");
        }
        // T-188-c：从其他页面 BACK 回首页 ⇒ 菜单焦点与高亮立刻归「首页」（不等系统焦点恢复，
        // 也不管上一页遗留在哪个 nav 项上）。（β47 / 新3：原先这里还会武装一次 BACK 缓冲 `mHomeBackAbsorbed`，
        // 那一次缓冲正是刘先生"第二下没反应"的元凶，现已整条删除，见 onBackInvoked 上方注释。）
        boolean returning = mLeavingHome;   // β7 / ②：先取，再消费（focusNavHome 靠它决定要不要开重钉窗）
        if (mLeavingHome) {
            mLeavingHome = false;
            // β10 / 组③：只有"从子页返回"才置标记（冷启动不置 ⇒ 组①链路一字不变；A 的「冷启动不得走」仍成立）
            mReturnAt = System.currentTimeMillis();
            mReturnedNoDown = true;
            mReturnClearedBy = "none";
            Log.d("FireFlyHome", "return flag on t=" + mReturnAt + " clearedBy=none");
        }
        // β7 / ②：只有“从别的页面返回”这一次恢复才开重钉窗（冷启动不钉，避免动到已验收的组①）。
        focusNavHome("onResume", returning);
    }

    /**
     * T-188-c：把焦点与当前页高亮一起交回「首页」项。
     * setCurrent 只写 selected 态（T-162 起 selected 无底色，高亮视觉＝焦点态），
     * 所以判据"焦点=首页项"由 requestItem 直接保证；from 仅用于日志区分调用时机。
     * β7 / ②新增第二个参数：{@code armPins=true} 时在同步抢焦＋一帧复核之后，再按"有界多次钉"补钉
     * （上限 3 次、500ms 窗内、只针对焦点落进本页 recycler 子树的情形；本页一收到任意按键 DOWN 立即停）。
     */
    private void focusNavHome(String from, boolean armPins) {
        mBinding.navBar.topNav.setCurrent(R.id.nav_home);
        mBinding.navBar.topNav.requestItem(R.id.nav_home);
        // 与 setFocus() 同款 post 兜底：窗口重新获焦时系统可能把上一页遗留的焦点重新派发回来，
        // 一帧后复核——焦点不在导航条上才补，不在导航条内乱抢邻项。
        mBinding.navBar.topNav.post(() -> {
            View focus = getCurrentFocus();
            // β5 / 首页项①：这里原先写 `focus.getParent() == mBinding.navBar.topNav` —— **恒 false**
            // （胶囊的父级是 `view_top_nav.xml` 的内层 LinearLayoutCompat，不是 TopNavView 本身，
            // 见 TopNavView.isNavItem 的注释）。恒 false ⇒ `!onNav` 永远成立 ⇒ 这一帧后**又无条件抢一次**
            // nav_home，与①onResume 的首次抢焦、②系统重新派发上一页遗留焦点撞在一起，
            // 造成"BACK 回首页后第一把 UP 被判 moved-in-page 吃掉、第二把才回"。
            // 改判为"真的不在导航条 6 颗胶囊上才补抢"，其余行为不变。
            // 注：同一写法还残在 `:665`（DOWN 进内容分支），那是**行为变更**（现在靠系统几何已完成且已验收），
            // 本批按 A 裁定只修死条件、不动它，避免两个变量同时改。
            boolean onNav = mBinding.navBar.topNav.isNavItem(focus);
            if (!onNav) mBinding.navBar.topNav.requestItem(R.id.nav_home);
            // β47 / 新3：`backAbsorb=` 字段随该标记一起删除（旧串 `navHome focus from=… backAbsorb=…` 有意变更）
            Log.d("FireFlyHome", "navHome focus from=" + from + " recheck=" + !onNav);
        });
        if (armPins) armHomePins(from);
    }

    /** β7 / ②：开一个有界重钉窗（A 硬约束①上限 3 次／500ms；②本页收到任意 DOWN 即停）。 */
    private void armHomePins(String from) {
        mPinDeadline = System.currentTimeMillis() + PIN_WINDOW_MS;
        mPinStop = false;
        mPinAttempt = 0;
        startFocusChain();
        scheduleHomePin(from, 1);
    }

    /**
     * β8 / 取证 H3：恢复窗内的焦点迁移序列（A 要的答案是“onResume 后 500ms 里焦点是怎么从胶囊跑到内容行容器的”）。
     * 只在窗口开着（含 300ms 尾巴）时打，避免把整晚的焦点变化刷进日志；**不改变任何焦点走向**，纯观察者。
     */
    private void startFocusChain() {
        if (mChainVto != null || mChainListener != null) return;
        try {
            mChainVto = getWindow().getDecorView().getViewTreeObserver();
            mChainListener = (oldFocus, newFocus) -> {
                if (System.currentTimeMillis() > mPinDeadline + 300) return;
                Log.d("FireFlyHome", "focus chain t=" + System.currentTimeMillis()
                        + " old=" + pinViewName(oldFocus) + " v=" + pinViewName(newFocus));
            };
            mChainVto.addOnGlobalFocusChangeListener(mChainListener);
        } catch (Throwable t) {
            // 取证设施自身绝不把页面带崩
            mChainVto = null;
            mChainListener = null;
        }
    }

    private void stopFocusChain() {
        try {
            if (mChainVto != null && mChainListener != null && mChainVto.isAlive()) {
                mChainVto.removeOnGlobalFocusChangeListener(mChainListener);
            }
        } catch (Throwable ignored) {
        }
        mChainVto = null;
        mChainListener = null;
    }

    /**
     * β7 / ②：一次补钉。三条硬约束逐条落地——
     * · **只在本子树**：焦点必须落在 {@code mBinding.recycler} 子树内才抢；焦点已在胶囊、
     *   或在 LOGO／其它区域 ⇒ 一律不动（不"抢回已到导航/已到其它区域的焦点"）；
     * · **有界**：次数与时间窗双闸，任一超限即打 `pin giveup n=…` 并停；
     * · **让路**：{@code mPinStop} 为真（本页已收到任意按键 DOWN，或已 onPause 离开）⇒ 立即停，只留痕。
     * 每次结果都如实回报（`ok=` 由"重钉后焦点是否真在胶囊上"决定，不假装成功）。
     */
    private void scheduleHomePin(String from, int attempt) {
        mPinAttempt = attempt;
        mBinding.navBar.topNav.postDelayed(() -> {
            if (mPinStop) {
                Log.d("FireFlyHome", "navHome pin attempt=" + attempt + " stop=byKey focus=" + pinViewName(getCurrentFocus()));
                return;
            }
            View focus = getCurrentFocus();
            if (mBinding.navBar.topNav.isNavItem(focus)) {
                Log.d("FireFlyHome", "navHome pin attempt=" + attempt + " already=nav focus=" + pinViewName(focus) + " ok=true from=" + from);
                return;
            }
            boolean inContent = isDescendantOf(focus, mBinding.recycler);
            boolean expired = System.currentTimeMillis() > mPinDeadline;
            if (!inContent || expired) {
                Log.d("FireFlyHome", "navHome pin giveup n=" + attempt + " focus=" + pinViewName(focus)
                        + " inContent=" + inContent + " expired=" + expired + " from=" + from);
                return;
            }
            // β8 / 取证 H2（零行为变更）：C 的二轮实据是 `pin attempt=3 ok=false`——`requestItem` 抢不回焦点。
            // 这条探针把“抢不回”的四种可能一次分开：胶囊不可聚焦／被禁用／自身或父链不可见／菜单层还没归位。
            // 注意 `rf=`：原来调的 `requestItem(R.id.nav_home)` 内部就是 `navItemOf(nav_home).requestFocus()`
            // （TopNavView.navItemOf 默认分支返回的正是 binding.navHome 这同一个对象），
            // 这里改成直接对同一个 View 调 `requestFocus()` 只为**拿到返回值**，动作次数与顺序一字未加 ⇒ 行为等价。
            View home = mBinding.navBar.topNav.getBinding().navHome;
            View homeParent = (View) home.getParent();
            Log.d("FireFlyHome", "navHome pin probe attempt=" + attempt
                    + " focusable=" + home.isFocusable() + " fInTouch=" + home.isFocusableInTouchMode()
                    + " enabled=" + home.isEnabled() + " vis=" + home.getVisibility()
                    + " parent=" + (homeParent == null ? "null" : homeParent.getClass().getSimpleName())
                    + " parentVis=" + (homeParent == null ? -1 : homeParent.getVisibility())
                    + " barVis=" + mBinding.navBar.navMenu.getVisibility()
                    + " barTransY=" + mBinding.navBar.navMenu.getTranslationY()
                    + " windowFocus=" + hasWindowFocus());
            boolean rf = home.requestFocus();
            boolean ok = mBinding.navBar.topNav.isNavItem(getCurrentFocus());
            Log.d("FireFlyHome", "navHome pin attempt=" + attempt + " focus=" + pinViewName(focus)
                    + " rf=" + rf + " ok=" + ok + " from=" + from);
            if (!ok && attempt < PIN_MAX_ATTEMPTS) scheduleHomePin(from, attempt + 1);
        }, PIN_STEP_MS);
    }

    /** β7 / ②：焦点是否落在指定容器子树内（含自身）。 */
    private boolean isDescendantOf(View v, View ancestor) {
        for (View p = v; p != null; ) {
            if (p == ancestor) return true;
            if (!(p.getParent() instanceof View)) return false;
            p = (View) p.getParent();
        }
        return false;
    }

    /** β7 / ②：与探针同一口径的视图名（共享层已把 `null` 与"无 id 的内容卡"分开，不再一律 `none`）。 */
    private String pinViewName(View v) {
        return NavOverlay.probeViewName(v, getResources());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // T-152（第一时机）：窗口获焦（首帧后）时幂等兜底显示浮层——此时 navMenu 已完成布局，
        // show 才真正把菜单层 translationY 归零（setOverlay 里的 post 可能早于布局完成）
        if (hasFocus && mOverlay != null) {
            mOverlay.show();
            Log.d("FireFlyHome", "onWindowFocus show");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
        mLeavingHome = true;  // T-188-c：下一次 onResume 属"从别的页面返回"
        mPinStop = true;      // β7 / ②：离开首页即停掉未跑完的补钉（已 post 的那次会在回调里看到 stop 直接返回）
    }

    @Override
    protected void onBackInvoked() {
        // β47 / 新3（A 单一指令）：**T-188-c 的 `mHomeBackAbsorbed` 缓冲层整条删除**。
        // C 的真机原文（`Test/evidence/round3-beta73/b73-runB.log:3/5/7/9`）显示：从二级页起，BACK#1 回首页、
        // BACK#2 被这一层吃掉只做归位（`home back absorb=navHome`）、BACK#3 才弹框——就是刘先生数的"三次"。
        // 现在的链固定为 `非导航态 → 导航态（焦点交 nav_home）→ 退出弹框`，**第二下必弹框、不留空按**。
        // 回滚点：本注释以下、`if (mBinding.progressLayout.isProgress())` 以上，恢复
        // `if (mHomeBackAbsorbed) { … focusNavHome("backAbsorb", true); … return; }`（β47 快照前的 :1024-1033）。
        if (mBinding.progressLayout.isProgress()) {
            showContent();
        } else if (mPresenter.isDelete()) {
            setHistoryDelete(false);
        } else if (mKeepPresenter != null && mKeepPresenter.isDelete()) {
            // T-303：BACK 退出收藏删除态，与历史侧同一条链上的第二个判据（一次 BACK 退一个，先历史后收藏）
            setKeepDelete(false);
        } else if (!mBinding.navBar.topNav.isNavItem(getCurrentFocus())) {
            // β46 / 新3（**最终口径＝A 15:40 最终指令「代码态 r7 就是最终态」**；此前两轮相反裁决按该指令作废）：
            // 现象＝焦点在海报里要按三次 BACK 才出退出提示。根因实测：链里那层
            // `getSelectedPosition() != 0 → scrollToPosition(0)` 把第一下变成"内容滚回第 0 行"（刘先生认可这一下），
            // 但 leanback 的 `selectedPosition` 要下一拍才归 0 ⇒ 第二下常常还停在这一层（再滚一次＝看着没反应），
            // 第三下才走到弹框——正好对上他数的三次。
            // 现按 A 的口径改：**第一下把焦点交给导航「首页」项，页面内容不动**（不跳历史、不滚列表），
            // 第二下焦点已在导航条上 ⇒ 直接落到"服务在跑→后台化 / 否则→弹退出框"。交不出去时**不吞这一键**，
            // 继续往下走同一链，保证任何一次 BACK 都有可见结果（刘先生铁律：不得吃键）。
            // 判据只看**在不在导航条**（6 颗胶囊任一，`isNavItem` 按视图身份比较），不看具体哪一颗。
            // 我按 16:01 那轮"先回首页"临时加过的 `isHomeNavFocused()` 已按最终指令删除；回退点＝同目录
            // `dev-ledger-b73.md` 第一节，改回只动这一个条件表达式。
            mBinding.navBar.topNav.setCurrent(R.id.nav_home);
            View from = getCurrentFocus();
            boolean moved = mBinding.navBar.topNav.requestFocusItem(R.id.nav_home);
            Log.d("FireFlyBack", "home back -> nav home focus moved=" + moved
                    + " from=" + (from == null ? "null" : from.getId())
                    + " row=" + mBinding.recycler.getSelectedPosition());
            if (moved) return;
        } else if (PlaybackService.isRunning()) {
            // β44 / B1（A 定稿第 3 点「播放服务在跑时保持现状」）：在看直播/播放时按 BACK，要的是
            // "回后台继续播"，不是退出 ⇒ 这一支**不弹确认**、仍走 moveToBackground。
            Util.moveToBackground(this);
            // β45 / 缺陷2：这条路径原先零日志（C 无从判"是走了后台化还是漏按了"），补一条 FireFlyBack 留痕。
            Log.d("FireFlyBack", "home back -> moveToBackground (service running, no dialog)");
        } else {
            // β44 / B1（A 定稿第 2 点）：原先这里直接 finish＝退出，现改为**两键式确认框**（不借用更新公告那套
            // 自定义布局；样式照 SettingActivity:419 的 MaterialAlertDialog，自动吃 β21/T-331 的 16dp 圆角）。
            // β47 / 新3：原先这里写着"`mHomeBackAbsorbed` 那一次归位不吃退出（按 A 定稿原样保留）"——该层已删除，
            // 所以确认框现在就是"焦点已在导航条上的那一下"必出现（β44 的两键式确认语义不变）。
            showExitDialog();
        }
    }

    /** β44 / B1：首页退出确认框（仅无播放服务时到达）。BACK 关框不退出＝默认 cancelable。 */
    private void showExitDialog() {
        // β45 / 缺陷1＋2（C 真机 K9-K10）：日志 tag 从 `FireFlyHome` 换成 `FireFlyBack`——C 的抓法就是
        // `logcat -s FireFlyBack:*`，旧 tag 下这两条永远抓不到（K10 报"confirmed 命中 0"的一半原因是这个）。
        Log.d("FireFlyBack", "home back -> exitDialog show");
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.exit_app_title)
                .setMessage(R.string.exit_app_msg)
                .setNegativeButton(R.string.dialog_negative, (d, which) -> Log.d("FireFlyBack", "home exit dialog cancel=button"))
                .setPositiveButton(R.string.dialog_positive, (d, which) -> {
                    Log.d("FireFlyBack", "home exit dialog confirm=finishAffinity");
                    // 改 `finish()` → `finishAffinity()`：`finish()` 只关**这一个**首页实例，而首页可能被外部入口
                    // 或 CLEAR_TOP 复用路径再叠一个实例在下面 ⇒ 关掉上面那个，`mCurrentFocus` 仍是 HomeActivity、
                    // 进程也还在（C 实测 act=HomeActivity、pid 不变）。`finishAffinity()` 一次收掉本任务里我方
                    // 全部 Activity ⇒ 与"退出应用"的字面语义一致。
                    finishAffinity();
                })
                .setOnCancelListener(d -> Log.d("FireFlyBack", "home exit dialog cancel=backDismiss"))
                .show();
        // β46 / 新4（A 派单，刘先生原话「退出提示的默认焦点在确定上，请移到取消上」）：TV 无触屏，弹框一出来
        // 焦点落在哪颗键＝遥控器 OK 就执行哪颗。β44 把焦点给了「确定」（当时为了"能验退出链路"），实际风险是
        // **误触直接退出**——现按 A/刘先生的要求改给「取消」（`dialog_negative`），确定仍可左右移焦后按 OK 执行。
        // 字段照旧全打，C 一眼能分辨"没绑上"还是"绑上了没拿到焦点"。
        android.view.View cancel = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);
        Log.d("FireFlyBack", "home exit dialog focus=cancel bound=" + (cancel != null)
                + " focusable=" + (cancel != null && cancel.isFocusable())
                + " clickable=" + (cancel != null && cancel.isClickable())
                + " requestFocus=" + (cancel != null && cancel.requestFocus()));
    }

    @Override
    protected void onDestroy() {
        stopFocusChain();   // β8 / 取证 H3：观察者随页面注销（挂在窗口 VTO 上，不注销会留到窗口销毁）
        DLNARendererService.stop(this);
        LiveConfig.get().clear();
        VodConfig.get().clear();
        AppDatabase.backup();
        OkHttp.get().clear();
        Source.get().exit();
        Server.get().stop();
        super.onDestroy();
    }
}
