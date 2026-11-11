package com.firefly.tv.ui.fragment;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.firefly.tv.R;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Class;
import com.firefly.tv.bean.Config;
import com.firefly.tv.bean.Result;
import com.firefly.tv.bean.Site;
import com.firefly.tv.bean.Value;
import com.firefly.tv.databinding.FragmentVodBinding;
import com.firefly.tv.event.CastEvent;
import com.firefly.tv.event.ConfigEvent;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.event.StateEvent;
import com.firefly.tv.impl.Callback;
import com.firefly.tv.impl.ConfigListener;
import com.firefly.tv.impl.FilterListener;
import com.firefly.tv.impl.SiteListener;
import com.firefly.tv.model.SiteViewModel;
import com.firefly.tv.ui.activity.HomeActivity;
import com.firefly.tv.ui.adapter.TypeAdapter;
import com.firefly.tv.ui.base.BaseFragment;
import com.firefly.tv.ui.dialog.FilterDialog;
import com.firefly.tv.ui.dialog.LinkDialog;
import com.firefly.tv.ui.dialog.ReceiveDialog;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.ResUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

public class VodFragment extends BaseFragment implements ConfigListener, SiteListener, FilterListener, TypeAdapter.OnClickListener {

    private FragmentVodBinding mBinding;
    private SiteViewModel mViewModel;
    private TypeAdapter mAdapter;

    public static VodFragment newInstance() {
        return new VodFragment();
    }

    private FolderFragment getFragment() {
        return (FolderFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
    }

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private Config getConfig() {
        return VodConfig.get().getConfig();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentVodBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        setRecyclerView();
        setViewModel();
        showProgress();
        bootstrap();
    }

    @Override
    protected void initEvent() {
        mBinding.top.setOnClickListener(this::onTop);
        // β82 第3条＋Q1＝甲：旧顶栏四钮整条退场——搜索只在首页；收藏／历史走首页 header；
        // 换站点仍有三处入口（首页 header 换源钮、设置页 vodHome 行＝SettingFragment:128 接线／:207 弹窗体、详情页换源钮）。
        // 箭头＝切到首页 tab（与一级页 BACK 一律回首页同口径），不新开第二套返回语义。
        mBinding.toolbar.setNavigationOnClickListener(v -> ((HomeActivity) requireActivity()).change(7));
        mBinding.filter.setOnClickListener(this::onFilter);
        mBinding.filter.setOnLongClickListener(this::onLink);
        mBinding.appBar.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            float factor = Math.abs(verticalOffset * 1f / appBarLayout.getTotalScrollRange());
            int padding = (int) (ResUtil.dp2px(12) * factor);
            if (mBinding.type.getPaddingTop() == padding) return;
            mBinding.type.setPadding(mBinding.type.getPaddingStart(), padding, mBinding.type.getPaddingEnd(), mBinding.type.getPaddingBottom());
        });
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mBinding.type.smoothScrollToPosition(position);
                mAdapter.setSelected(position);
                setFabVisible(position);
            }
        });
    }

    private void setRecyclerView() {
        mBinding.type.setHasFixedSize(true);
        mBinding.type.setItemAnimator(null);
        mBinding.type.setAdapter(mAdapter = new TypeAdapter(this));
        mBinding.pager.setAdapter(new PageAdapter(getChildFragmentManager()));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(getViewLifecycleOwner(), this::setAdapter);
    }

    private void setAdapter(Result result) {
        android.util.Log.d("FireFlyHome", "vod result page=vod count=" + result.getList().size());
        mAdapter.addAll(result);
        mBinding.pager.getAdapter().notifyDataSetChanged();
        setFabVisible(0);
        hideProgress();
        showContent();
    }

    private void setFabVisible(int position) {
        // 本地播放入口已移至底导，原 link FAB 移除；顶部态显示筛选按钮
        if (mAdapter.getItemCount() == 0) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.filter.setVisibility(View.GONE);
        } else if (!mAdapter.get(position).getFilters().isEmpty()) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.filter.show();
        } else if (position == 0 || mAdapter.get(position).getFilters().isEmpty()) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.filter.setVisibility(View.GONE);
        }
    }

    private void onTop(View view) {
        getFragment().scrollToTop();
        mBinding.top.setVisibility(View.INVISIBLE);
        mBinding.filter.show();
    }

    private boolean onLink(View view) {
        LinkDialog.show(this);
        return true;
    }

    private void onFilter(View view) {
        if (mAdapter.getItemCount() > 0) FilterDialog.create().filter(mAdapter.get(mBinding.pager.getCurrentItem()).getFilters()).show(this);
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
    }

    private void hideContent() {
        mBinding.type.setVisibility(View.INVISIBLE);
        mBinding.pager.setVisibility(View.INVISIBLE);
    }

    private void showContent() {
        mBinding.type.setVisibility(View.VISIBLE);
        mBinding.pager.setVisibility(View.VISIBLE);
    }

    /**
     * β28 批一收尾②（刘先生＝冷启动进点播页首屏刷不出，五次全中＝必然路径；A 单定档＝照首页 HomeFragment.initView:141-143 那条先例补「建页自取一次」）：
     * 现文事实＝本页取数口只有 homeContent()，它的调用面只有 RefreshEvent 的 HOME／SIZE 两支，而事件非 sticky（b29 r5 件〔2〕〔4〕甲档）
     * ⇒ 冷启配置加载完成那次 post 发出时本页尚未创建（本页只由 factory 的 position 0 新建），之后进页就只剩 progress。
     * 就绪前置只用现文可读表达式、不新增状态位：sites 非空＝getSites() 非空（这条正是 VodConfig.isLoaded() 本体的表达式，
     * 但它是 protected、Fragment 侧不可见 ⇒ 就地读同一条表达式）；主源 key 非空＝getHome() 在 home 为 null 时返回 new Site()，故必须自己判 key，用 TextUtils.isEmpty 兜住 null。
     * 不就绪只留痕、不发请求：空 Site 发出去会走 error 分支，观感从转圈变空白，比现在更糟（A 单要求把这句理由写进注释）。
     */
    private void bootstrap() {
        int sites = VodConfig.get().getSites().size();
        String homeKey = getHome().getKey();
        boolean ready = sites > 0 && !TextUtils.isEmpty(homeKey);
        String reason = sites == 0 ? "sites_empty" : TextUtils.isEmpty(homeKey) ? "home_empty" : "ready";
        android.util.Log.d("FireFlyHome", "vod bootstrap page=vod sites=" + sites + " homeKey=" + (TextUtils.isEmpty(homeKey) ? "empty" : homeKey) + " fetch=" + ready + " reason=" + reason);
        if (ready) homeContent();
    }

    private void homeContent() {
        android.util.Log.d("FireFlyHome", "vod fetch page=vod enter");
        showProgress();
        setFabVisible(0);
        mAdapter.clear();
        mViewModel.homeContent();
        mBinding.pager.setAdapter(new PageAdapter(getChildFragmentManager()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        android.util.Log.d("FireFlyHome", "vod event page=vod type=" + event.getType());
        switch (event.getType()) {
            case HOME:
            case SIZE:
                homeContent();
                break;
            case CATEGORY:
                getFragment().onRefresh();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStateEvent(StateEvent event) {
        switch (event.type()) {
            case EMPTY:
                hideProgress();
                break;
            case PROGRESS:
                showProgress();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        ReceiveDialog.create().event(event).show(this);
    }

    @Override
    public void setConfig(Config config) {
        VodConfig.load(config, new Callback() {
            @Override
            public void start() {
                showProgress();
                hideContent();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
                showContent();
            }
        });
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void onItemClick(int position, Class item) {
        mBinding.pager.setCurrentItem(position);
        mAdapter.setSelected(position);
    }

    @Override
    public void setFilter(String key, Value value) {
        getFragment().setFilter(key, value);
    }

    @Override
    public boolean canBack() {
        if (mBinding.pager.getAdapter() == null || mBinding.pager.getAdapter().getCount() == 0) return true;
        if (!getFragment().canBack()) return true;
        getFragment().goBack();
        return false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Class type = mAdapter.get(position);
            return FolderFragment.newInstance(getHome().getKey(), type, 4);
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }
    }
}
