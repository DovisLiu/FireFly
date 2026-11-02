package com.firefly.tv.ui.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.Product;
import com.firefly.tv.R;
import com.firefly.tv.bean.Collect;
import com.firefly.tv.bean.Result;
import com.firefly.tv.bean.Vod;
import com.firefly.tv.databinding.FragmentTypeBinding;
import com.firefly.tv.model.SiteViewModel;
import com.firefly.tv.ui.activity.DetailActivity;
import com.firefly.tv.ui.activity.VodActivity;
import com.firefly.tv.ui.base.BaseFragment;
import com.firefly.tv.ui.custom.CustomRowPresenter;
import com.firefly.tv.ui.custom.CustomScroller;
import com.firefly.tv.ui.custom.CustomSelector;
import com.firefly.tv.ui.presenter.VodPresenter;
import com.firefly.tv.utils.ResUtil;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

public class CollectFragment extends BaseFragment implements CustomScroller.Callback, VodPresenter.OnClickListener {

    private FragmentTypeBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private ArrayObjectAdapter mLast;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private Collect mCollect;
    private String mKeyword;

    public static CollectFragment newInstance(String keyword, Collect collect) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        CollectFragment fragment = new CollectFragment().setCollect(collect);
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return mKeyword = mKeyword == null ? getArguments().getString("keyword") : mKeyword;
    }

    private CollectFragment setCollect(Collect collect) {
        this.mCollect = collect;
        return this;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentTypeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setRecyclerView();
        setViewModel();
        addVideo(mCollect);
    }

    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.addOnScrollListener(mScroller = new CustomScroller(this));
        mBinding.recycler.setHeader(getActivity(), R.id.result, R.id.recycler);
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, result -> {
            mScroller.endLoading(result);
            addVideo(result.getList());
        });
    }

    private boolean checkLastSize(List<Vod> items) {
        if (mLast == null || items.isEmpty()) return false;
        int size = Product.getColumn() - mLast.size();
        if (size == 0) return false;
        size = Math.min(size, items.size());
        mLast.addAll(mLast.size(), items.subList(0, size));
        addVideo(items.subList(size, items.size()));
        return true;
    }

    private void addVideo(Collect collect) {
        if (collect != null) addVideo(collect.getList());
    }

    public void addVideo(List<Vod> items) {
        if (checkLastSize(items) || getActivity() == null || getActivity().isFinishing()) return;
        List<ListRow> rows = new ArrayList<>();
        VodPresenter presenter = new VodPresenter(this);
        for (List<Vod> part : Lists.partition(items, Product.getColumn())) {
            mLast = new ArrayObjectAdapter(presenter);
            mLast.addAll(0, part);
            rows.add(new ListRow(mLast));
        }
        mAdapter.addAll(mAdapter.size(), rows);
    }

    @Override
    public void onItemClick(Vod item) {
        requireActivity().setResult(Activity.RESULT_OK);
        // β4 / T-201（A 案「入口直达详情页、取消预览页中转」）：搜索结果/合集条目原先走
        // `VideoActivity.collect(...)` = **直进播放页小窗形态**（这就是刘先生说的那条"多一跳"），
        // 现改为直达 `DetailActivity`——这是全站**唯一**还绕开详情页的入口
        // （首页 HomeActivity:536/548/573、点播 TypeFragment:291、收藏 KeepActivity:99/119、
        //   历史 HistoryActivity:91 本来就直达详情，本轮不动）。
        // 直达之后 BACK 天然是「播放页→详情页→来源页」各回一层，不再依赖 backToDetail() 补开。
        if (item.isFolder()) VodActivity.start(requireActivity(), item.getSiteKey(), Result.folder(item));
        else DetailActivity.start(requireActivity(), item.getSiteKey(), item.getId(), item.getName(), item.getPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        return false;
    }

    @Override
    public boolean onLoadMore(String page) {
        if (mCollect == null || "all".equals(mCollect.getSite().getKey())) return false;
        mViewModel.searchContent(mCollect.getSite(), getKeyword(), false, page);
        return true;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (mBinding != null && !isVisibleToUser) mBinding.recycler.moveToTop();
    }
}
