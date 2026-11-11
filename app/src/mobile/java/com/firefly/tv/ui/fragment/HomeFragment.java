package com.firefly.tv.ui.fragment;

import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.Product;
import com.firefly.tv.api.config.LiveConfig;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.R;
import com.firefly.tv.bean.Config;
import com.firefly.tv.bean.History;
import com.firefly.tv.bean.Keep;
import com.firefly.tv.bean.Result;
import com.firefly.tv.bean.Style;
import com.firefly.tv.bean.Site;
import com.firefly.tv.bean.Vod;
import com.firefly.tv.databinding.FragmentHomeBinding;
import com.firefly.tv.databinding.ItemHomeRowBinding;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.impl.Callback;
import com.firefly.tv.impl.ConfigListener;
import com.firefly.tv.impl.SiteListener;
import com.firefly.tv.model.SiteViewModel;
import com.firefly.tv.ui.activity.VideoActivity;
import com.firefly.tv.ui.activity.HistoryActivity;
import com.firefly.tv.ui.activity.KeepActivity;
import com.firefly.tv.ui.activity.SearchActivity;
import com.firefly.tv.ui.activity.HomeActivity;
import com.firefly.tv.ui.adapter.HistoryAdapter;
import com.firefly.tv.ui.adapter.KeepAdapter;
import com.firefly.tv.ui.adapter.VodAdapter;
import com.firefly.tv.ui.dialog.HistoryDialog;
import com.firefly.tv.ui.dialog.SiteDialog;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.PermissionUtil;
import com.firefly.tv.ui.base.BaseFragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 手机独立首页（乙方案，A 裁：底部导航加「首页」，与「点播」分开；冷启落本页）。
 * <p>
 * 三行与 TV 首页同名同行序＝继续观看 → 我的收藏 → 站点推荐；数据全部走现成方法，
 * 不新增接口、不改 main／leanback：
 * 继续观看＝{@link History#get()}（SQL 已带 createTime 倒序＋LIMIT 60＋当前仓库过滤）；
 * 我的收藏＝{@link Keep#loadVodSafe()}（装配口径与 TV 首页一致，含逐条兜底）；
 * 站点推荐＝{@link SiteViewModel#homeContent()}（点播页今天就在用的同一个接口）。
 * <p>
 * 「热播／新上架」不进本页：Spider 只有 homeContent／categoryContent／detailContent／
 * searchContent／liveContent 五类取数，{@code Result} 与 {@code Vod} 字段集里没有热度、
 * 评分或上架时间，本地两张表也只有进度字段——没有数据源就不做这一行，不拿分类首页的顺序冒充"新上架"。
 */
/**
 * β27 第 4 条（刘先生反馈＝首页长按要能删；A 裁＝采历史页／收藏页的 toggle、不要二次确认）。
 * 本页不再 implements HistoryAdapter／KeepAdapter 的 OnClickListener：两个接口的无参 onLongClick() 签名相同，
 * 拿同一个 this 当两行监听＝回调里分辨不出长按来自哪一行，而按行生效才是「跟历史页一样」
 * （否则长按历史卡会把收藏卡一起变成删除态＝误删风险）。⇒ 每行各给一个监听对象，点击与删除仍回调本页同名方法。
 * 推荐行仍 implements VodAdapter.OnClickListener，且 onLongClick(Vod item) 保持 return false（A 裁＝推荐行不开）。
 */
public class HomeFragment extends BaseFragment implements VodAdapter.OnClickListener, SiteListener, ConfigListener {

    private FragmentHomeBinding mBinding;
    private SiteViewModel mViewModel;
    private HistoryAdapter mHistoryAdapter;
    private KeepAdapter mKeepAdapter;
    private VodAdapter mVodAdapter;
    // β32 B1：行内空白触摸被行 RecyclerView 自身消费（onTouchEvent 返回 true、不冒泡到滚动容器）⇒
    // 删除态把退态触摸拦截挂到两个行 RecyclerView。纯 setOnClickListener 对 RecyclerView 不触发（其重写 onTouchEvent 不调 performClick），
    // 故用 OnItemTouchListener＋GestureDetector 探「点击处无卡片子 View」即退态；非删除态摘除恢复原触摸。
    private RecyclerView.OnItemTouchListener mHistoryBlankTap;
    private RecyclerView.OnItemTouchListener mKeepBlankTap;

    /** 每行一个监听对象＝长按能分辨来源行（见类注释）；点击／删除仍回调本页同名方法。 */
    private final HistoryAdapter.OnClickListener historyListener = new HistoryAdapter.OnClickListener() {
        @Override
        public void onItemClick(History item) {
            HomeFragment.this.onItemClick(item);
        }

        @Override
        public void onItemDelete(History item) {
            HomeFragment.this.onItemDelete(item);
        }

        @Override
        public boolean onLongClick() {
            return toggleDelete("history");
        }
    };
    private final KeepAdapter.OnClickListener keepListener = new KeepAdapter.OnClickListener() {
        @Override
        public void onItemClick(Keep item) {
            HomeFragment.this.onItemClick(item);
        }

        @Override
        public void onItemDelete(Keep item) {
            HomeFragment.this.onItemDelete(item);
        }

        @Override
        public boolean onLongClick() {
            return toggleDelete("keep");
        }
    };

    public static HomeFragment newInstance() {
        return new HomeFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentHomeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        int[] spec = Product.getSpec(requireActivity());
        setRow(mBinding.history, mHistoryAdapter = new HistoryAdapter(historyListener));
        setRow(mBinding.keep, mKeepAdapter = new KeepAdapter(keepListener));
        // β32 B1：两行空白退态触摸拦截器（删除态由 syncBlankTap 挂/摘；recycler 视图稳定，重建 adapter 后仍有效）
        mHistoryBlankTap = rowBlankTapListener(mBinding.history.recycler, "history");
        mKeepBlankTap = rowBlankTapListener(mBinding.keep.recycler, "keep");
        setRecommendRow();
        mHistoryAdapter.setSize(spec);
        mKeepAdapter.setSize(spec);
        mBinding.history.title.setText(R.string.home_history);
        mBinding.keep.title.setText(R.string.home_keep);
        mBinding.recommend.title.setText(R.string.home_recommend);
        mBinding.recommend.more.setVisibility(View.GONE);
        setViewModel();
        getHistory();
        getKeep();
        homeContent();
    }

    @Override
    protected void initEvent() {
        mBinding.history.more.setOnClickListener(v -> HistoryActivity.start(requireActivity()));
        mBinding.keep.more.setOnClickListener(v -> KeepActivity.start(requireActivity()));
        // β80 项1（刘先生「首页补顶部导航条」）：顶栏五控件＝本页 header 自有行为（β82 删掉点播页头栏后不再参照它）。
        // header 的收藏/历史 id 带 header_ 前缀＝避开本页 include 行的 @+id/keep/history（ViewBinding 同域撞名）。
        mBinding.logo.setOnClickListener(v -> {
            android.util.Log.d("FireFlyHome", "logo click host=HomeFragment action=history");
            HistoryDialog.create().vod().readOnly().show(this);
        });
        mBinding.search.setOnClickListener(v -> SearchActivity.start(requireActivity()));
        mBinding.headerKeep.setOnClickListener(v -> KeepActivity.start(requireActivity()));
        mBinding.headerHistory.setOnClickListener(v -> HistoryActivity.start(requireActivity()));
        // β28 批一（刘先生「本地入口移到左上角，与原来功能一样只移入口」）：这行原本是左上角那枚换源钮（SiteDialog 入口之一），
        // 换源入口已移到「底导·片源」那颗（见 HomeActivity.onNavigationItemSelected），本位给本地。
        // 行为逐字照旧支＝起 HomeActivity.openLocal()（仍 FileChooser→SAF 系统选择器，本地页本身一字未动）；
        // 宿主类型写成判断条件＋留痕＝不赌它（HistoryDialog 那次 HomeFragment→ConfigListener 强转 P0 的同型防御）。
        mBinding.headerLocal.setOnClickListener(v -> {
            if (requireActivity() instanceof HomeActivity) ((HomeActivity) requireActivity()).openLocal();
            else android.util.Log.d("FireFlyHome", "local entry host=" + requireActivity().getClass().getSimpleName() + " action=none");
        });
        // 读屏基础描述（A 裁①本轮只做"视觉可点＋一个基础描述"）：描述里带上可见文本，
        // 因为 TextView 一旦设了 contentDescription 会顶掉文本本身的朗读，只写提示就读不到"更多"。
        mBinding.history.more.setContentDescription(getString(R.string.home_history) + "，" + getString(R.string.home_more));
        mBinding.keep.more.setContentDescription(getString(R.string.home_keep) + "，" + getString(R.string.home_more));
    }

    private void setRow(ItemHomeRowBinding row, RecyclerView.Adapter<?> adapter) {
        row.recycler.setHasFixedSize(true);
        row.recycler.setItemAnimator(null);
        row.recycler.setLayoutManager(new LinearLayoutManager(requireActivity(), RecyclerView.HORIZONTAL, false));
        row.recycler.setAdapter(adapter);
    }

    /** β80 项3（刘先生「站点推荐改纵向」）：竖向宫格、列数动态取，口径直接抄 TypeFragment.setStyle 两行；
     *  在 NestedScrollView 里关自带滚动，整表随页面纵滚（横滚保留给上两行，按刘先生分工）。 */
    private void setRecommendRow() {
        Style style = Style.rect();
        mBinding.recommend.recycler.setHasFixedSize(true);
        mBinding.recommend.recycler.setItemAnimator(null);
        mBinding.recommend.recycler.setNestedScrollingEnabled(false);
        mBinding.recommend.recycler.setLayoutManager(new GridLayoutManager(requireActivity(), Product.getColumn(requireActivity(), style)));
        mBinding.recommend.recycler.setAdapter(mVodAdapter = new VodAdapter(this, style, Product.getSpec(requireActivity(), style)));
    }

    /** β80 项2：SIZE 事件的全量重建（HistoryAdapter.setSize 只赋宽高、不 notify＝实测，故数据重灌走 getHistory/getKeep）。
     *  β85 第8条（A 裁）：两行也在这一拍重建 adapter。实测＝宽高只在 onCreateViewHolder 里落 LayoutParams
     *  （HistoryAdapter:64-65），只 setSize 不重建＝旧 holder 的宽高一直留着，这就是刘先生看到的"只有这两行图片没跟着改大小"；
     *  推荐行一直是 new VodAdapter（setRecommendRow）所以本来就生效。代价（写进交件）＝两行滚动位置回行首＋重灌瞬间空白。 */
    private void rebuildSizes() {
        int[] spec = Product.getSpec(requireActivity());
        setRow(mBinding.history, mHistoryAdapter = new HistoryAdapter(historyListener));
        setRow(mBinding.keep, mKeepAdapter = new KeepAdapter(keepListener));
        mHistoryAdapter.setSize(spec);
        mKeepAdapter.setSize(spec);
        setRecommendRow();
        getHistory();
        getKeep();
    }

    private void setRowVisible(ItemHomeRowBinding row, boolean visible) {
        row.getRoot().setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * 三行的显隐与空页占位放在同一处：行内无数据⇒标题连同整行一起隐藏（TV 首页同口径）；
     * 三行全空⇒显示占位文案（A 定案的口径，新用户第一眼不是空白页）。
     * 放在 setItems 的回调里跑，是为了让计数读到 diff 提交之后的值（照 HistoryActivity 的写法）。
     */
    private void setRowsVisible() {
        int history = mHistoryAdapter.getItemCount();
        int keep = mKeepAdapter.getItemCount();
        int recommend = mVodAdapter.getItemCount();
        setRowVisible(mBinding.history, history > 0);
        setRowVisible(mBinding.keep, keep > 0);
        setRowVisible(mBinding.recommend, recommend > 0);
        boolean empty = history + keep + recommend == 0;
        mBinding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        Log.d("FireFlyHome", "home rows history=" + history + " keep=" + keep + " recommend=" + recommend + " empty=" + empty);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(getViewLifecycleOwner(), this::setRecommend);
    }

    private void getHistory() {
        List<History> items;
        try {
            items = History.get();
        } catch (Throwable t) {
            // 本页是冷启落点：装配任何一行都不得把异常冒到启动链上（TV 首页收藏行 T-186 同一口径）
            items = Collections.emptyList();
            Log.d("FireFlyHome", "home history fail " + t.getClass().getSimpleName());
        }
        mHistoryAdapter.setItems(items == null ? Collections.emptyList() : items, this::setRowsVisible);
    }

    private void getKeep() {
        List<Keep> items;
        try {
            items = Keep.loadVodSafe();
        } catch (Throwable t) {
            items = Collections.emptyList();
            Log.d("FireFlyHome", "home keep fail " + t.getClass().getSimpleName());
        }
        mKeepAdapter.setItems(items == null ? Collections.emptyList() : items, this::setRowsVisible);
    }

    private void homeContent() {
        mViewModel.homeContent();
    }

    private void setRecommend(Result result) {
        List<Vod> items = new ArrayList<>();
        List<Vod> raw = result == null ? Collections.emptyList() : result.getList();
        for (Vod item : raw) {
            // 只收"点下去能直接进详情"的卡片：动作项与文件夹项的交互需要点播页的上下文
            // （mViewModel.action／getParent().openFolder），首页不复制一套半截逻辑
            if (item == null || item.isAction() || item.isFolder()) continue;
            items.add(item);
        }
        Log.d("FireFlyHome", "home recommend in=" + raw.size() + " shown=" + items.size() + " skip=" + (raw.size() - items.size()));
        // 显隐一律放到 setItems 的回调里算：AsyncListDiffer 提交前 getItemCount() 还是旧值
        //（HistoryActivity.java:65 就是这个写法），否则空行判断会慢一拍。
        mVodAdapter.setItems(items, this::setRowsVisible);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                homeContent();
                break;
            case SIZE:
                // β80 项2（刘先生「首页图片尺寸改设置后要退出重进才变」）：列数/卡宽都来自 PlayerSetting.getSize，
                // 照 TypeFragment.setStyle 的重建路径＝重取 spec＋两行重灌数据＋推荐行重建 adapter 与列数。
                rebuildSizes();
                homeContent();
                break;
            case HISTORY:
                getHistory();
                break;
            case KEEP:
                getKeep();
                break;
        }
    }

    public void onItemClick(History item) {
        // β30 C：删除态下点卡片非垃圾桶区域＝退出删除态（不跳转）；垃圾桶仍走 onItemDelete
        if (mHistoryAdapter.isDelete()) {
            mHistoryAdapter.setDelete(false);
            syncBlankTap();
            Log.d("FireFlyHome", "home delete cancel page=home row=history");
            return;
        }
        VideoActivity.start(requireActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    public void onItemDelete(History item) {
        // 旧句已被刘先生反馈推翻，原文留档（β27 第 4 条）：
        //   「首页不进删除态（onLongClick 恒返回 false），本回调保留以对齐适配器契约；真调用即删该条记录」
        // 现在长按会进删除态（toggleDelete），本回调＝删除态里点了垃圾桶的执行体。
        // 删到空行即退出删除态＝逐字抄 HistoryActivity.onItemDelete 的口径，不留「空列表还挂着垃圾桶」。
        mHistoryAdapter.remove(item.delete(), () -> {
            if (mHistoryAdapter.getItemCount() == 0) mHistoryAdapter.setDelete(false);
            syncBlankTap();
            setRowsVisible();
        });
    }

    public void onItemClick(Keep item) {
        // β30 C：删除态下点卡片非垃圾桶区域＝退出删除态（不跳转）；垃圾桶仍走 onItemDelete
        if (mKeepAdapter.isDelete()) {
            mKeepAdapter.setDelete(false);
            syncBlankTap();
            Log.d("FireFlyHome", "home delete cancel page=home row=keep");
            return;
        }
        // 收藏行走 Keep.loadVodSafe()（已按当前仓库过滤），跨仓条目不进首页行 ⇒ 这里不会发生"点一下就悄悄换源"
        VideoActivity.start(requireActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    public void onItemDelete(Keep item) {
        // β27 第 4 条：与历史行同口径，删到空行退出删除态（HistoryActivity.onItemDelete 同形）。
        mKeepAdapter.remove(item.delete(), () -> {
            if (mKeepAdapter.getItemCount() == 0) mKeepAdapter.setDelete(false);
            syncBlankTap();
            setRowsVisible();
        });
    }

    @Override
    public void onItemClick(Vod item) {
        // β85 第9条（A 裁＝首页推荐点进去也到搜索）：改走搜索页，与点播页聚合站同一去向。
        // 代价（如实写）＝非聚合站的首页推荐此前是"直达详情＋换源态"，现在多一跳搜索、由结果卡再进详情；
        // 换来的是一致性＋不再拿首页接口的 id 去查详情（那条在聚合站上必失败＝刘先生看到的"详情加载失败"）。
        // 要退回"只聚合站走搜索"＝把这行换成 VideoActivity.start(..., autoSource=true)（合并前的原行见 git d523440 的 HomeFragment:263）。
        SearchActivity.start(requireActivity(), item.getName(), true);
    }

    @Override
    public boolean onLongClick(Vod item) {
        // β27 第 4 条（A 裁）＝推荐行不开删除态：保持 return false，交回系统默认（不吞事件、不删不改）
        return false;
    }

    /**
     * β27 第 4 条：长按某行卡片＝该行进出删除态（写法参照 HistoryActivity:99-102，只是按行分开生效）。
     * 被推翻的旧句原文留档（不抹）：
     *   「首页不做批量删除态：删除操作留在历史页／收藏页（那里有工具栏与二次确认）」
     * 回滚＝把本方法换回 return false 并恢复类上那两个 OnClickListener implements（回滚点 backup-b27h）。
     * 日志沿用现成 tag FireFlyHome，字段带页名与行名（row=history|keep），与本页既有行一致。
     */
    private boolean toggleDelete(String row) {
        boolean next;
        if ("history".equals(row)) {
            next = !mHistoryAdapter.isDelete();
            mHistoryAdapter.setDelete(next);
        } else {
            next = !mKeepAdapter.isDelete();
            mKeepAdapter.setDelete(next);
        }
        Log.d("FireFlyHome", "home longPress page=home row=" + row + " delete=" + next);
        syncBlankTap();
        return true;
    }

    /**
     * β28 批一收尾①（刘先生＝长按浮出垃圾桶后，点页面空白处要退出删除态；A 单钉行为不钉字面）：
     * 宿主＝fragment_home.xml 里 NestedScrollView 的唯一直接子（竖向 LinearLayout，现文无 id，本批也不新增 id）。
     * 前置实测（p302 r4〔1〕，xmltree 解析非正则）＝NestedScrollView 元素 1／其直接子 1／该子无 android:id／内含 empty＋三行 include 共 4 个元素；
     * 「两行之间」与「最后一行下方」（该容器 paddingBottom 16dp ＋ NestedScrollView fillViewport 撑满视口后的剩余高度）都落在这个容器上；
     * 「行内卡片右侧剩余宽度」＝item_home_row 的 RecyclerView 属性列表里没有 clickable／focusable（同件实测）⇒ 该处点击不被行内消费，冒泡到同一宿主。
     * 卡片自己的点击仍被 item 消费、滚动语义不变（容器只在子 View 未消费时才收到 UP）。
     * 不常驻＝只在至少一行处于删除态时挂、全部退出立刻摘 ⇒ 非删除态时首页触摸路径与现文逐字一致（A 单判据）。
     * 禁令照做：未改 item_home_row.xml 的 clickable、未加全局手势或触摸拦截器。
     * 编译留痕（本批 r1 的 javac 闸在此处红过一次，原句不抹）：方法引用写成 this::onBlankCancel 而方法体无参 ⇒
     *   javac 报「不兼容的类型: 条件表达式中的类型错误 / 方法引用无效：需要没有参数、找到 View」（gate-b28b1-sup2-r1.log:298-305）；
     *   OnClickListener 的函数描述符收 View，故本方法带 View 形参，且先把条件表达式落成显式局部变量再传（目标类型不再靠推断）。
     */
    private void syncBlankTap() {
        boolean any = mHistoryAdapter.isDelete() || mKeepAdapter.isDelete();
        View.OnClickListener listener = any ? this::onBlankCancel : null;
        mBinding.scroll.getChildAt(0).setOnClickListener(listener);
        // β32 B1：行内空白被行 RecyclerView 消费、不冒泡到上面的滚动容器 ⇒ 删除态把退态拦截同时挂到两个行 RecyclerView，
        // 非删除态摘除恢复原触摸。与滚动容器空白监听＋卡片 root 退态＋垃圾桶独立删除并存。
        syncRowBlankTap(mBinding.history.recycler, mHistoryAdapter.isDelete(), mHistoryBlankTap);
        syncRowBlankTap(mBinding.keep.recycler, mKeepAdapter.isDelete(), mKeepBlankTap);
    }

    private void syncRowBlankTap(RecyclerView recycler, boolean delete, RecyclerView.OnItemTouchListener tap) {
        if (tap == null) return;
        recycler.removeOnItemTouchListener(tap);
        if (delete) recycler.addOnItemTouchListener(tap);
    }

    /** β32 B1：行 RecyclerView 空白退态拦截。RecyclerView 重写 onTouchEvent 不调 performClick ⇒ 纯 OnClickListener 不触发，
     *  故用 OnItemTouchListener＋GestureDetector：onInterceptTouchEvent 恒 return false（不夺滚动/卡片点击），
     *  仅在 onSingleTapUp 且点击处无卡片子 View（findChildViewUnder==null＝行内空白/卡片间）时退态。 */
    private RecyclerView.OnItemTouchListener rowBlankTapListener(RecyclerView recycler, String row) {
        GestureDetector detector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                if (recycler.findChildViewUnder(e.getX(), e.getY()) == null) {
                    cancelRowBlank(row);
                    return true;
                }
                return false;
            }
        });
        return new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                detector.onTouchEvent(e);
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            }
        };
    }

    private void cancelRowBlank(String row) {
        if ("history".equals(row)) {
            if (!mHistoryAdapter.isDelete()) return;
            mHistoryAdapter.setDelete(false);
        } else {
            if (!mKeepAdapter.isDelete()) return;
            mKeepAdapter.setDelete(false);
        }
        Log.d("FireFlyHome", "home delete cancel page=home row=" + row + " via=rowblank");
        syncBlankTap();
    }

    /** β33 B2：供 HomeActivity.dispatchTouchEvent 根分发层统一判定（不依赖容器冒泡）。 */
    public boolean isInDeleteMode() {
        return mHistoryAdapter.isDelete() || mKeepAdapter.isDelete();
    }

    /** β33 B2：该次点击是否命中两行任一可见垃圾桶 icon（delete view 全局命中矩形，rawX/rawY 为屏幕坐标）。 */
    public boolean isTapOnTrash(float rawX, float rawY) {
        return hitTrash(mBinding.history.recycler, rawX, rawY) || hitTrash(mBinding.keep.recycler, rawX, rawY);
    }

    private boolean hitTrash(RecyclerView recycler, float rawX, float rawY) {
        int[] loc = new int[2];
        for (int i = 0; i < recycler.getChildCount(); i++) {
            View child = recycler.getChildAt(i);
            View trash = child.findViewById(R.id.delete);
            if (trash == null || trash.getVisibility() != View.VISIBLE) continue;
            trash.getLocationOnScreen(loc);
            if (rawX >= loc[0] && rawX <= loc[0] + trash.getWidth() && rawY >= loc[1] && rawY <= loc[1] + trash.getHeight()) return true;
        }
        return false;
    }

    /** β33 B2：两行退出删除态＋同步空白 tap（根分发层退态执行体；日志 via=rootdispatch 由 HomeActivity 打）。 */
    public void cancelDelete() {
        if (mHistoryAdapter.isDelete()) mHistoryAdapter.setDelete(false);
        if (mKeepAdapter.isDelete()) mKeepAdapter.setDelete(false);
        syncBlankTap();
    }

    private void onBlankCancel(View view) {
        List<String> rows = new ArrayList<>();
        if (mHistoryAdapter.isDelete()) { mHistoryAdapter.setDelete(false); rows.add("history"); }
        if (mKeepAdapter.isDelete()) { mKeepAdapter.setDelete(false); rows.add("keep"); }
        Log.d("FireFlyHome", "home blank cancel page=home rows=" + String.join("+", rows));
        syncBlankTap();
    }

    /**
     * β27 第 3 条（刘先生反馈＝首页 LOGO／切换线路能弹窗但点了没反应；A 三处选路全给）。
     * 根因（实测）＝两条弹层的监听人都是「拿宿主 Fragment 试 instanceof」：
     *   SiteDialog.java:39 只在 fragment instanceof SiteListener 时赋 listener，否则 onTextClick（:64-67）只剩 :66 的 dismiss；
     *   HistoryDialog.java:100 走 listener ?: target()，target()（:71-76）先看 parentFragment 再看 activity，
     *   本页原先两个接口都没 implements ⇒ 两条都拿到 null＝「点了没反应」。
     * A 裁②＝〔甲〕两个接口都 implements（推荐行仍用 this 不变）；〔丙〕给 SiteDialog 补注入口被否（动公共件、牵连别页）。
     */
    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
        // A 裁①＝〔甲〕改完主站点立刻重灌三行：这条反馈的本意就是「点了要有反应」。
        // 代价（A 已接）＝多一次取数、三行重灌可能轻微闪烁，出包后看。
        getHistory();
        getKeep();
        homeContent();
    }

    /**
     * A 裁③＝复制 SettingFragment:171-177 那份（含 file 权限分支），不动公共件；
     * Callback 原是 SettingFragment 的私有内部类，这里复制同形一份，
     * 按 A 的字登记为「下批注释／抽共享可收」的已知项，本批不抽。
     * 与设置页唯一的行为差＝success() 刷的是首页三行，而不是设置页自己的文案。
     */
    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) {
            PermissionUtil.requestFile(this, allGranted -> load(config));
        } else {
            load(config);
        }
    }

    private void load(Config config) {
        switch (config.getType()) {
            case 0:
                VodConfig.load(config, getCallback());
                break;
            case 1:
                LiveConfig.load(config, getCallback());
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(requireActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                // A 裁①＝〔甲〕：切仓库成功后重灌三行（本页三行的数据源都随当前仓库变）
                getHistory();
                getKeep();
                homeContent();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    /**
     * 一级页 BACK：先回顶部，已在顶部才交给 Activity 走"退后台／退出"。
     */
    @Override
    public boolean canBack() {
        if (mBinding.scroll.getScrollY() > 0) {
            mBinding.scroll.smoothScrollTo(0, 0);
            return false;
        }
        return true;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
    }
}
