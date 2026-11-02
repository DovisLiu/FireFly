package com.firefly.tv.ui.custom;

import android.util.Log;

import androidx.fragment.app.FragmentActivity;

import com.firefly.tv.R;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.HomeResult;
import com.firefly.tv.bean.Result;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.impl.Callback;
import com.firefly.tv.impl.ConfigListener;
import com.firefly.tv.ui.activity.LiveSelectActivity;
import com.firefly.tv.ui.activity.SearchActivity;
import com.firefly.tv.ui.activity.SettingActivity;
import com.firefly.tv.ui.activity.VodActivity;
import com.firefly.tv.ui.dialog.HistoryDialog;
import com.firefly.tv.ui.dialog.SiteDialog;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.PermissionUtil;
import com.firefly.tv.utils.ResUtil;

/**
 * 列表页统一导航路由（TV 2.0 §2.1/刘先生拍板：菜单在各列表页保留、当前页高亮、可直接切换）。
 * currentNavId=当前页对应导航项；点击当前页自身项不重复跳转。
 */
public class NavDelegate implements TopNavView.OnNavListener {

    private final FragmentActivity activity;
    private final int currentNavId;

    public NavDelegate(FragmentActivity activity, int currentNavId) {
        this.activity = activity;
        this.currentNavId = currentNavId;
    }

    private boolean self(int navId) {
        return navId == currentNavId;
    }

    @Override
    public void onNavHome() {
        activity.finish();
    }

    @Override
    public void onNavSearch() {
        if (self(R.id.nav_search)) return;
        SearchActivity.start(activity);
    }

    @Override
    public void onNavVod() {
        Result result = HomeResult.get();
        if (result == null || result.getTypes().isEmpty()) {
            Notify.show(activity.getString(R.string.home_vod_empty));
            return;
        }
        VodActivity.start(activity, result);
    }

    @Override
    public void onNavLive() {
        // T-141（spec §3.5 拍板 9 + v1.10 拍板 44）：直播入口先进独立选台页（不再直达播放）
        LiveSelectActivity.start(activity);
    }

    @Override
    public void onNavSource() {
        SiteDialog.create().show(activity);
    }

    @Override
    public void onNavSetting() {
        if (self(R.id.nav_setting)) return;
        SettingActivity.start(activity);
    }

    /** 列表页顶栏一键绑定：LOGO＝历史列表（T-211 扩围）+ 当前页高亮 + 统一路由 + 浮层控制器 */
    public static NavOverlay bind(FragmentActivity activity, android.view.View logoView, TopNavView nav, int currentNavId) {
        if (logoView != null) {
            logoView.setFocusable(true);
            logoView.setFocusableInTouchMode(true);
            logoView.setOnFocusChangeListener((v, hasFocus) -> v.animate().scaleX(hasFocus ? ResUtil.FOCUS_SCALE : 1f).scaleY(hasFocus ? ResUtil.FOCUS_SCALE : 1f).setDuration(150).start());
            // T-211 扩围（刘先生口径「无论哪个页，LOGO 作用应该一致＝历史列表」）：
            // 原先这里是 `ConfigDialog.create().show(activity)`（仓库管理页，内含投屏/推送二维码），
            // 现统一走 openHistory()；二维码能力没有删除，改由「设置→点播→点播」进入（SettingActivity:320）。
            logoView.setOnClickListener(v -> openHistory(activity));
        }
        nav.setCurrent(currentNavId);
        nav.setListener(new NavDelegate(activity, currentNavId));
        int revealId = currentNavId == -1 ? R.id.nav_home : currentNavId;
        NavOverlay overlay = new NavOverlay((android.view.View) nav.getParent(), nav, revealId);
        // β4 / T-208：探针法要能认出"焦点被系统交给了顶栏这一带（含常驻层 LOGO）"，
        // 故把顶栏根交给 overlay；nav.getParent()=菜单层 navMenu，其父才是 view_nav_toolbar 的根。
        android.view.View menu = (android.view.View) nav.getParent();
        if (menu.getParent() instanceof android.view.View) {
            overlay.setToolbarRoot((android.view.View) menu.getParent());
        }
        overlay.setPage(activity.getClass().getSimpleName());
        overlay.attach();
        return overlay;
    }

    /**
     * T-211 扩围：全站 LOGO 的唯一收口——弹出**点播历史（仓库）列表**，与手机端
     * {@code VodFragment.onLogo():161} 的既有行为对齐（手机早就是这个语义）。
     * 每页点击都打一行 `logo click host=<Activity> action=history`，C 可逐页判定。
     * 宿主自己实现 ConfigListener 就用它（首页/设置/直播三页）；其余六页走 fallback——
     * file:// 仓库先取存储权限，再 {@link VodConfig#load}，成功后广播 home/keep/history 三个刷新事件。
     */
    public static void openHistory(FragmentActivity activity) {
        Log.d("FireFlyHome", "logo click host=" + activity.getClass().getSimpleName() + " action=history");
        ConfigListener target = activity instanceof ConfigListener
                ? (ConfigListener) activity
                : config -> loadRepo(activity, config);
        HistoryDialog.create().vod().listener(target).show(activity);
    }

    /** openHistory 的通用落地（未实现 ConfigListener 的页面走这条）：语义与 SettingActivity.setConfig 一致。 */
    private static void loadRepo(FragmentActivity activity, com.firefly.tv.bean.Config config) {
        Log.d("FireFlyHome", "logo pick host=" + activity.getClass().getSimpleName() + " path=fallback repoLen=" + (config == null ? 0 : config.getName().length()));
        Callback callback = new Callback() {
            @Override
            public void success() {
                RefreshEvent.home();
                RefreshEvent.keep();
                RefreshEvent.history();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
        if (config != null && config.getUrl().startsWith("file")) {
            PermissionUtil.requestFile(activity, allGranted -> VodConfig.load(config, callback));
        } else {
            VodConfig.load(config, callback);
        }
    }
}
