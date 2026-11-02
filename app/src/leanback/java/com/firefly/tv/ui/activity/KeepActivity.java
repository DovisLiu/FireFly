package com.firefly.tv.ui.activity;

import com.firefly.tv.ui.activity.DetailActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.Product;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Config;
import com.firefly.tv.bean.FavLog;
import com.firefly.tv.bean.Keep;
import com.firefly.tv.databinding.ActivityKeepBinding;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.impl.Callback;
import com.firefly.tv.ui.adapter.KeepAdapter;
import com.firefly.tv.ui.custom.NavDelegate;
import com.firefly.tv.ui.custom.NavOverlay;
import com.firefly.tv.ui.base.BaseActivity;
import com.firefly.tv.utils.Clock;
import com.firefly.tv.ui.custom.SpaceItemDecoration;
import com.firefly.tv.utils.Notify;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class KeepActivity extends BaseActivity implements KeepAdapter.OnClickListener {

    private ActivityKeepBinding mBinding;
    private KeepAdapter mAdapter;
    private Clock mClock;
    private NavOverlay mOverlay;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, KeepActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityKeepBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mClock = Clock.create(mBinding.navBar.clock);
        mOverlay = NavDelegate.bind(this, mBinding.navBar.logo, mBinding.navBar.topNav, -1);
        mBinding.navBar.getRoot().post(() -> mOverlay.show());
        setRecyclerView();
        getKeep();
    }

    // 浮层导航（刘先生拍板）：内容顶行按上键呼出
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        // β4 / T-208 裁定 1「全站铺，不留分页特例」＋硬要求③「旧轨真删」：
        // 本页与 HistoryActivity 一起换成共享探针，`NavOverlay.isTopRow()` 因此**没有任何调用者**（已删）。
        // 注意：TV 端本页**不可达**（无调用方、exported=false，T-211 实测），A 附加要求①判其
        // 「代码保留但不计回归」⇒ 本改动不为验收服务，只为不产生第四条轨、且下轮不会再有人来改它。
        mOverlay.onKeyDown(event, getCurrentFocus());
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
        mBinding.recycler.setAdapter(mAdapter = new KeepAdapter(this));
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn()));
        mBinding.recycler.addItemDecoration(new SpaceItemDecoration(Product.getColumn(), 16));
    }

    private void getKeep() {
        // T-186：收藏页同样只消费洗过的列表，装配异常就地吞掉并留证，页面不得崩
        try {
            mAdapter.setItems(Keep.loadVodSafe(), () -> mBinding.progressLayout.showContent(true, mAdapter.getItemCount()));
            FavLog.ui("keep", true, null);
        } catch (Throwable t) {
            FavLog.ui("keep", false, t.getClass().getSimpleName());
        }
    }

    private void loadConfig(Config config, Keep item) {
        VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                DetailActivity.start(getActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.KEEP) getKeep();
    }

    @Override
    public void onItemClick(Keep item) {
        Config config = Config.find(item.getCid());
        if (config == null) CollectActivity.start(this, item.getVodName());
        else if (item.getCid() != VodConfig.getCid()) loadConfig(config, item);
        else DetailActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(Keep item) {
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
