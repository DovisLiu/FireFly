package com.firefly.tv.ui.fragment;

import com.firefly.tv.ui.activity.DetailActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.Product;
import com.firefly.tv.R;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Cache;
import com.firefly.tv.bean.Filter;
import com.firefly.tv.bean.Result;
import com.firefly.tv.bean.Site;
import com.firefly.tv.bean.Style;
import com.firefly.tv.bean.Value;
import com.firefly.tv.bean.Vod;
import com.firefly.tv.databinding.FragmentTypeBinding;
import com.firefly.tv.model.SiteViewModel;
import com.firefly.tv.ui.activity.CollectActivity;
import com.firefly.tv.ui.activity.VideoActivity;
import com.firefly.tv.ui.base.BaseFragment;
import com.firefly.tv.ui.custom.CustomRowPresenter;
import com.firefly.tv.ui.custom.CustomScroller;
import com.firefly.tv.ui.custom.CustomSelector;
import com.firefly.tv.ui.presenter.FilterPresenter;
import com.firefly.tv.ui.presenter.VodPresenter;
import com.firefly.tv.utils.FilterUtil;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.ResUtil;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TypeFragment extends BaseFragment implements CustomScroller.Callback, VodPresenter.OnClickListener, SwipeRefreshLayout.OnRefreshListener {

    private HashMap<String, String> mExtends;
    private FragmentTypeBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private ArrayObjectAdapter mLast;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private List<Filter> mFilters;
    private boolean headerVisible;
    private boolean filterVisible;

    public static TypeFragment newInstance(String key, String typeId, Style style, HashMap<String, String> extend, boolean folder) {
        Bundle args = new Bundle();
        args.putString("key", key);
        args.putString("typeId", typeId);
        args.putBoolean("folder", folder);
        args.putParcelable("style", style);
        args.putSerializable("extend", extend);
        TypeFragment fragment = new TypeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKey() {
        return getArguments().getString("key");
    }

    private String getTypeId() {
        return getArguments().getString("typeId");
    }

    private boolean isFolder() {
        return getArguments().getBoolean("folder");
    }

    private Style getStyle() {
        return isFolder() ? Style.list() : getSite().getStyle(getArguments().getParcelable("style"));
    }

    private HashMap<String, String> getExtend() {
        return (HashMap<String, String>) getArguments().getSerializable("extend");
    }

    private List<Filter> getFilter() {
        // T-190-b（Round 40）：排序/热度族（热度/最近/积分…）整行不入列表。
        // 过滤必须发生在 getFilter() 这唯一的入口：mFilters 同时是 showFilter() 建行、hideFilter()
        // 删行、checkFilter() 保留行数三处的口径，任何一处再单独跳过都会让口径分叉——
        // 被隐藏的行不会消失，而是变成一条空行占位（刘先生判据明确禁止占位）。
        List<Filter> kept = new ArrayList<>();
        for (Filter filter : Cache.copy(getTypeId())) {
            if (filter == null) continue;
            if (FilterUtil.isSortFamily(filter)) {
                Log.d("FireFlyVod", "filter hide key=" + filter.getKey() + " name=" + filter.getName() + " values=" + filter.getValue().size());
            } else {
                kept.add(filter);
            }
        }
        Log.d("FireFlyVod", "filter rows=" + kept.size());
        return kept;
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private FolderFragment getParent() {
        return ((FolderFragment) getParentFragment());
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentTypeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mScroller = new CustomScroller(this);
        mExtends = getExtend();
        mFilters = getFilter();
        setRecyclerView();
        setViewModel();
        setFilters();
        // 筛选行常显（效果图 02/03：分类+筛选同屏，刘先生反馈"没有三级菜单"）
        if (!mFilters.isEmpty()) {
            filterVisible = true;
            mBinding.recycler.post(() -> showFilter());
        }
        getVideo();
    }

    @Override
    protected void initEvent() {
        mBinding.swipeLayout.setOnRefreshListener(this);
        mBinding.recycler.addOnScrollListener(mScroller);
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(8, FocusHighlight.ZOOM_FACTOR_NONE, HorizontalGridView.FOCUS_SCROLL_ALIGNED), FilterPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setHeader(getActivity(), R.id.recycler);
        // T-190-a（Round 40）：本视图纵向行距 16dp(32px)→8dp(16px)，与 fragment_type 的 paddingTop 同档。
        // 刘先生所见「间隔太大」发生在筛选行与筛选行之间，而 leanback 的纵向间距是 GridLayoutManager
        // 全视图统一值（CustomRowPresenter 第一个参数只管行内水平间距），没有逐行独立档可用，
        // 故这里必须改它才能收紧菜单行。连带效应（如实报，不隐藏）：同视图的海报行↔海报行
        // 也由 32px 收到 16px——卡内 name 自带 6dp(12px) 内边距，收紧后海报上下留白仍≈28px，
        // ZOOM_FACTOR_SMALL 的放大余量远小于此，不会互贴；观感最终由 C 真机截图判读。
        // 未做「逐 presenter 行高补偿」把海报节奏钉回 32px：Style.list() 站点的海报是直接进网格的
        // Vod 项（不经 ListRow），per-presenter 补偿覆盖不到，反而造成不同站点节奏不一致。
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(8));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(getViewLifecycleOwner(), this::setAdapter);
        mViewModel.getAction().observe(getViewLifecycleOwner(), result -> Notify.show(result.getMsg()));
    }

    private void setFilters() {
        for (Filter filter : mFilters) {
            if (mExtends.containsKey(filter.getKey())) {
                filter.setSelected(mExtends.get(filter.getKey()));
            }
        }
    }

    private void setClick(ArrayObjectAdapter adapter, String key, Value item) {
        for (int i = 0; i < adapter.size(); i++) ((Value) adapter.get(i)).setSelected(item);
        adapter.notifyArrayItemRangeChanged(0, adapter.size());
        if (item.isSelected()) mExtends.put(key, item.getV());
        else mExtends.remove(key);
        onRefresh();
    }

    private void getVideo() {
        mLast = null;
        checkFilter();
        mScroller.reset();
        getVideo(getTypeId(), "1");
    }

    private void getVideo(String typeId, String page) {
        mViewModel.categoryContent(getKey(), typeId, page, true, mExtends);
    }

    private void setAdapter(Result result) {
        boolean first = mScroller.first();
        boolean flag = mExtends.isEmpty();
        int size = result.getList().size();
        mBinding.progressLayout.showContent(first & flag, size);
        mBinding.swipeLayout.setRefreshing(false);
        mScroller.endLoading(result);
        if (size > 0) addVideo(result);
    }

    private void addVideo(Result result) {
        Style style = result.getStyle(getStyle());
        if (style.isList()) mAdapter.addAll(mAdapter.size(), result.getList());
        else addGrid(result.getList(), style);
        checkMore();
    }

    private void checkMore() {
        if (mScroller.isDisable() || mAdapter.size() >= 5) return;
        mScroller.checkMore();
    }

    private boolean checkLastSize(List<Vod> items, Style style) {
        if (mLast == null || items.isEmpty()) return false;
        int size = Product.getColumn(style) - mLast.size();
        if (size == 0) return false;
        size = Math.min(size, items.size());
        mLast.addAll(mLast.size(), items.subList(0, size));
        addGrid(items.subList(size, items.size()), style);
        return true;
    }

    private void addGrid(List<Vod> items, Style style) {
        if (checkLastSize(items, style)) return;
        List<ListRow> rows = new ArrayList<>();
        VodPresenter presenter = new VodPresenter(this, style);
        for (List<Vod> part : Lists.partition(items, Product.getColumn(style))) {
            mLast = new ArrayObjectAdapter(presenter);
            mLast.addAll(0, part);
            rows.add(new ListRow(mLast));
        }
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private ListRow getRow(Filter filter) {
        FilterPresenter presenter = new FilterPresenter(filter.getKey());
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
        presenter.setOnClickListener((key, item) -> setClick(adapter, key, item));
        adapter.setItems(filter.getValue(), null);
        return new ListRow(adapter);
    }

    private void showFilter() {
        List<ListRow> rows = new ArrayList<>();
        for (Filter filter : mFilters) rows.add(getRow(filter));
        mBinding.recycler.postDelayed(() -> mBinding.recycler.scrollToPosition(0), 48);
        mAdapter.addAll(0, rows);
    }

    private void hideFilter() {
        mAdapter.removeItems(0, mFilters.size());
    }

    public void toggleFilter(boolean visible) {
        if (mFilters.isEmpty()) return;
        this.filterVisible = visible;
        if (visible) showFilter();
        else hideFilter();
    }

    private void checkFilter() {
        int adapterSize = mAdapter.size();
        int filterSize = filterVisible ? mFilters.size() : 0;
        if (adapterSize > filterSize) mAdapter.removeItems(filterSize, mAdapter.size() - filterSize);
        if (adapterSize == 0) mBinding.progressLayout.showProgress();
        else mBinding.swipeLayout.setRefreshing(true);
    }

    public void onRefresh() {
        getVideo();
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) {
            mViewModel.action(getKey(), item.getAction());
        } else if (item.isFolder()) {
            getParent().openFolder(item.getId(), mExtends);
            headerVisible = mBinding.recycler.isHeaderVisible();
        } else {
            if (getSite().isIndex()) CollectActivity.start(requireActivity(), item.getName());
            else DetailActivity.start(requireActivity(), getKey(), item.getId(), item.getName(), item.getPic());
        }
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction() || item.isFolder()) return false;
        CollectActivity.start(requireActivity(), item.getName());
        return true;
    }

    @Override
    public boolean onLoadMore(String page) {
        getVideo(getTypeId(), page);
        return true;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            mBinding.recycler.showHeader();
        } else {
            if (headerVisible) mBinding.recycler.showHeader();
            else mBinding.recycler.hideHeader();
            mBinding.recycler.requestFocus();
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (mBinding != null) mBinding.recycler.moveToTop();
    }
}
