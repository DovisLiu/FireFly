package com.firefly.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.firefly.tv.App;
import com.firefly.tv.R;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Collect;
import com.firefly.tv.bean.Site;
import com.firefly.tv.databinding.ActivityCollectBinding;
import com.firefly.tv.model.SiteViewModel;
import com.firefly.tv.setting.Setting;
import com.firefly.tv.ui.adapter.CollectAdapter;
import com.firefly.tv.ui.custom.NavDelegate;
import com.firefly.tv.ui.custom.NavOverlay;
import com.firefly.tv.ui.base.BaseActivity;
import com.firefly.tv.ui.fragment.CollectFragment;
import com.firefly.tv.utils.Clock;
import com.firefly.tv.utils.ResUtil;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class CollectActivity extends BaseActivity {

    private ActivityCollectBinding mBinding;
    private Clock mClock;
    private NavOverlay mOverlay;
    private CollectAdapter mAdapter;
    private SiteViewModel mViewModel;
    private List<Site> mSites;
    private View mOldView;

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, CollectActivity.class);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    private CollectFragment getFragment() {
        return (CollectFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, 0);
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getIntent().putExtras(intent);
        mAdapter.clear();
        setPager();
        search();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mClock = Clock.create(mBinding.navBar.clock);
        mOverlay = NavDelegate.bind(this, mBinding.navBar.logo, mBinding.navBar.topNav, R.id.nav_search);
        mBinding.navBar.getRoot().post(() -> mOverlay.show());
        setRecyclerView();
        setViewModel();
        saveKeyword();
        setSites();
        setPager();
        search();
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        // β4 / T-208（A 裁定 1：全站铺，不留分页特例）：旧守卫 `canReveal() + isTopPosition(recycler)`
        // 取的是 leanback 选中位，站点 tab 行/首排卡片的 position 语义与"上方还有没有可聚焦节点"无关，
        // 换用共享探针：同一次按键 DOWN→UP 焦点未变＝顶到边 → 落本页导航项（nav_search）。
        mOverlay.onKeyDown(event, getCurrentFocus());
        // β5 / T-219 同款（合集/搜索结果页）：`CollectFragment.java:80` 把 `R.id.result`（标题）与
        // `R.id.recycler`（站点 tab 行）注册成网格 header ⇒ 一进内容 `hideHeader()` 会把这两个视图一起 GONE，
        // 于是从结果首行再按 UP 时上方无目标 ⇒ 探针判顶边 ⇒ 直接出 nav_search（跳过站点 tab）。
        // 这里显形的正是那两个被注册的视图（与 showHeader 的效果一致），再把焦点交给站点 tab 行。
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_UP
                && mBinding.pager.hasFocus() && mBinding.recycler.getVisibility() != View.VISIBLE) {
            View from = mBinding.pager.findFocus();
            mBinding.result.setVisibility(View.VISIBLE);
            mBinding.recycler.setVisibility(View.VISIBLE);
            boolean moved = mBinding.recycler.requestFocus();
            if (!moved) {
                mBinding.recycler.post(() -> Log.d("FireFlyFocus", "focusMove from=noid to=recycler row=0 vis="
                        + mBinding.recycler.requestFocus() + " page=collect mode=up -> bar (header was hidden, posted)"));
            }
            Log.d("FireFlyFocus", "focusMove from=" + viewName(from) + " to=recycler row=0 vis=" + moved
                    + " page=collect mode=up -> bar (header was hidden)");
            return true;
        }
        // β18 / 规范 R3 第一批（A 派单：照首页 HomeActivity:751 的做法铺三页）：**UP 的落点在这把键的
        // ACTION_DOWN 阶段一次定死**——首排顶边直接抢本页导航项，不再交给系统几何搜索 ⇒ 一次键只有一个落点
        // （日志 `mode=up -> nav single`）；非首排（上方还有页内目标）不改派 ⇒ 页内上移一行的旧语义不变；
        // 四条触发条件、长按 repeat 护栏、顶边守卫与「抢不到焦点就不吃键」全部在 NavOverlay.revealOnDown() 一条口收口。
        // 顺序说明（判据④不回退）：本档必须排在上面那段 T-219「header 被隐藏时先显形再落站点 tab 行」
        // **之后**——那一档的落点是页内视图（站点 tab 行），若让 revealOnDown 先命中，结果首行按 UP 会直接跳出
        // 导航、跳过站点 tab 行，β5/T-219 的既有验收就废了。
        if (mOverlay.revealOnDown(event, getCurrentFocus())) return true;
        int verdict = mOverlay.probeUp(event, getCurrentFocus());
        if (verdict == NavOverlay.UP_EAT) return true;
        if (verdict == NavOverlay.UP_REVEAL) return mOverlay.revealToPage();
        return super.dispatchKeyEvent(event);
    }

    /** β5 / T-219：只用于日志的视图名（取不到返回 none/noid，不参与任何判定）。 */
    private String viewName(View v) {
        if (v == null || v.getId() == View.NO_ID) return "none";
        try {
            return getResources().getResourceEntryName(v.getId());
        } catch (Throwable t) {
            return "noid";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
    }

    @Override
    protected void initEvent() {
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mBinding.recycler.setSelectedPosition(position);
                mBinding.recycler.requestFocus();
            }
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child);
            }
        });
    }

    private void setRecyclerView() {
        mBinding.recycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.recycler.setAdapter(mAdapter = new CollectAdapter());
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getSearch().observe(this, result -> {
            if (result.getList().isEmpty()) return;
            getFragment().addVideo(result.getList());
            mAdapter.add(Collect.create(result.getList()));
            mBinding.pager.getAdapter().notifyDataSetChanged();
        });
        mViewModel.getSearchFailed().observe(this, failed -> {
            if (Boolean.TRUE.equals(failed)) mBinding.result.setText(R.string.search_all_failed);
        });
    }

    private void saveKeyword() {
        List<String> items = Setting.getKeyword().isEmpty() ? new ArrayList<>() : App.gson().fromJson(Setting.getKeyword(), TypeToken.getParameterized(List.class, String.class).getType());
        items.remove(getKeyword());
        items.add(0, getKeyword());
        if (items.size() > 9) items.remove(9);
        Setting.putKeyword(App.gson().toJson(items));
    }

    private void setSites() {
        mSites = VodConfig.get().getSites().stream().filter(Site::isSearchable).toList();
    }

    private void setPager() {
        mBinding.pager.setAdapter(new PageAdapter(getSupportFragmentManager()));
    }

    private void search() {
        if (mSites.isEmpty()) return;
        mAdapter.add(Collect.all());
        mBinding.pager.getAdapter().notifyDataSetChanged();
        mBinding.result.setText(getString(R.string.collect_result, getKeyword()));
        mViewModel.searchContent(mSites, getKeyword(), false);
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child) {
        if (mOldView != null) mOldView.setSelected(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setSelected(true);
        App.post(mRunnable, 100);
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mBinding.pager.setCurrentItem(mBinding.recycler.getSelectedPosition());
        }
    };

    @Override
    protected void onBackInvoked() {
        mViewModel.stopSearch();
        super.onBackInvoked();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return CollectFragment.newInstance(getKeyword(), mAdapter.get(position));
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }

        @Nullable
        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void restoreState(@Nullable Parcelable state, @Nullable ClassLoader loader) {
        }
    }
}
