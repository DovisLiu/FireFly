package com.firefly.tv.ui.activity;

import com.firefly.tv.ui.activity.DetailActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.Product;
import com.firefly.tv.bean.History;
import com.firefly.tv.databinding.ActivityHistoryBinding;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.ui.adapter.HistoryAdapter;
import com.firefly.tv.ui.custom.NavDelegate;
import com.firefly.tv.ui.custom.NavOverlay;
import com.firefly.tv.ui.base.BaseActivity;
import com.firefly.tv.utils.Clock;
import com.firefly.tv.ui.custom.SpaceItemDecoration;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HistoryActivity extends BaseActivity implements HistoryAdapter.OnClickListener {

    private ActivityHistoryBinding mBinding;
    private HistoryAdapter mAdapter;
    private Clock mClock;
    private NavOverlay mOverlay;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, HistoryActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHistoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mClock = Clock.create(mBinding.navBar.clock);
        mOverlay = NavDelegate.bind(this, mBinding.navBar.logo, mBinding.navBar.topNav, -1);
        mBinding.navBar.getRoot().post(() -> mOverlay.show());
        setRecyclerView();
        getHistory();
    }

    // 浮层导航（刘先生拍板）：内容顶行按上键呼出
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        // β4 / T-208 裁定 1「全站铺」＋硬要求③「旧轨真删」：与 KeepActivity 同法换成共享探针
        // （本页在 TV 端**不可达**，A 附加要求①判「代码保留但不计回归」，改动只为不留第四条轨）。
        mOverlay.onKeyDown(event, getCurrentFocus());
        // β18 / 规范 R3 第一批（A 派单：照首页 HomeActivity:751 的做法铺三页）：**UP 的落点在这把键的
        // ACTION_DOWN 阶段一次定死**——首排顶边直接抢本页导航项，不再交给系统几何搜索 ⇒ 一次键只有一个落点
        // （日志 `mode=up -> nav single`）；非首排（上方还有页内目标）不改派 ⇒ 页内上移一行的旧语义不变；
        // 四条触发条件、长按 repeat 护栏、顶边守卫与「抢不到焦点就不吃键」全部在 NavOverlay.revealOnDown() 一条口收口。
        // 本页 NavDelegate.bind 传的 currentNavId＝-1 ⇒ revealId 回落 `R.id.nav_home`（NavDelegate.java:93），
        // 所以"直达本页导航项"在历史页上的实际落点是导航「首页」——与本页既有的 probeUp/revealToPage 同一目标。
        // 另注：本页在 TV 端**不可达**（规范 §四-5，全仓无 start 调用方），本块改动属"不留第四条轨"的一致性落地，
        // 判据①②③无法在真机上测（已回报 A）。
        if (mOverlay.revealOnDown(event, getCurrentFocus())) return true;
        int verdict = mOverlay.probeUp(event, getCurrentFocus());
        if (verdict == NavOverlay.UP_EAT) return true;
        if (verdict == NavOverlay.UP_REVEAL) return mOverlay.revealToPage();
        return super.dispatchKeyEvent(event);
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

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setAdapter(mAdapter = new HistoryAdapter(this));
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn()));
        mBinding.recycler.addItemDecoration(new SpaceItemDecoration(Product.getColumn(), 16));
    }

    private void getHistory() {
        mAdapter.setItems(History.get(), () -> mBinding.progressLayout.showContent(true, mAdapter.getItemCount()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.HISTORY) getHistory();
    }

    @Override
    public void onItemClick(History item) {
        DetailActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(History item) {
        mAdapter.remove(item.delete(), () -> {
            if (mAdapter.getItemCount() == 0) mAdapter.setDelete(false);
        });
    }

    @Override
    public boolean onLongClick() {
        mAdapter.setDelete(true);
        return true;
    }

    @Override
    protected void onBackInvoked() {
        if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else super.onBackInvoked();
    }
}
