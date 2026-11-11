package com.firefly.tv.ui.fragment;

import android.content.Intent;
import org.json.JSONObject;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.BuildConfig;
import com.firefly.tv.R;
import com.firefly.tv.App;
import com.firefly.tv.Updater;
import com.firefly.tv.api.config.LiveConfig;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Config;
import com.firefly.tv.bean.Live;
import com.firefly.tv.bean.Site;
import androidx.appcompat.widget.LinearLayoutCompat;
import com.firefly.tv.databinding.FragmentSettingBinding;
import com.firefly.tv.db.AppDatabase;
import androidx.appcompat.app.AppCompatDelegate;

import com.firefly.tv.event.ConfigEvent;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.impl.Callback;
import com.firefly.tv.impl.ConfigListener;
import com.firefly.tv.impl.LiveListener;
import com.firefly.tv.impl.SiteListener;
import com.firefly.tv.setting.PlayerSetting;
import com.firefly.tv.setting.Setting;
import com.firefly.tv.ui.activity.HomeActivity;
import com.firefly.tv.ui.custom.GlassEdgeDrawable;
import android.util.Log;
import com.firefly.tv.ui.base.BaseFragment;
import com.firefly.tv.ui.dialog.ConfigDialog;
import com.firefly.tv.ui.dialog.HistoryDialog;
import com.firefly.tv.ui.dialog.LiveDialog;
import com.firefly.tv.ui.dialog.RestoreDialog;
import com.firefly.tv.ui.dialog.SiteDialog;
import com.firefly.tv.utils.FileUtil;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.PermissionUtil;
import com.firefly.tv.utils.ResUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.firefly.tv.utils.Task;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingFragment extends BaseFragment implements ConfigListener, SiteListener, LiveListener {

    private FragmentSettingBinding mBinding;

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    private HomeActivity getRoot() {
        return (HomeActivity) requireActivity();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        mBinding.versionText.setText(BuildConfig.VERSION_NAME);
        setOtherText();
        setCacheText();
    }

    private void setOtherText() {
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.incognitoText.setText(Setting.getSwitch(Setting.isIncognito()));
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    @Override
    protected void initEvent() {
        // β82 第4条＋Q1＝甲：设置主页是一级 tab，箭头＝切到首页 tab（与一级页 BACK 同口径，不另开语义）。
        mBinding.toolbar.setNavigationOnClickListener(v -> getRoot().change(7));
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.display.setOnClickListener(this::onDisplay);
        mBinding.about.setOnClickListener(this::onAbout);
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.export.setOnClickListener(this::onExport);
        mBinding.player.setOnClickListener(this::onPlayer);
        mBinding.danmaku.setOnClickListener(this::onDanmaku);
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.version.setOnClickListener(this::onVersion);
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.live.setOnLongClickListener(this::onLiveEdit);
        mBinding.liveHome.setOnClickListener(this::onLiveHome);
        mBinding.incognito.setOnClickListener(this::setIncognito);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        // β33 D1：设置页「本地」行已删（mBinding.local.setOnClickListener(this::onLocal) 一并移除）；首页左上角本地入口不受影响。
        // β26 玻璃扩面：设置页 12 处真容器（background=shape_item，corners 实测 14dp）挂方向性环，色对随主题翻；
        // β27 第 4b 条另一半（A 裁＝四处图标块挂环、但不并入 48dp 高度档）⇒ 本段共 16 行 setForeground＝12 行容器＋4 枚图标块
        mBinding.vod.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.live.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.player.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.display.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.danmaku.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.incognito.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.doh.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.cache.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.version.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.about.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        // 第 12 处（fragment_setting.xml:467，自身无 id）：实测 backup 是 :475 的 MaterialTextView，
        // 所以 §68 写的 mBinding.backup.getRoot() 取不到——ViewBinding 对普通 View 不生成 getRoot()；正确取法＝父 View，并按 A 令加恒等断言
        View backupRow = (View) mBinding.backup.getParent();
        if (!(backupRow instanceof LinearLayoutCompat)) {
            Log.w("FireFlyGlass", "backup row is not LinearLayoutCompat=" + backupRow.getClass().getName());
        } else {
            backupRow.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        }
        // β27 第 4b 条：四枚图标块 vodHome(:79)／vodHistory(:89)／liveHome(:148)／liveHistory(:158)
        // 背景同样是 @drawable/shape_item（corners 实测 14dp），所以环仍取 radius_setting_row＝14dp，与它们自己的底同半径。
        // 半径理由（A 要我写明）：每枚＝24dp 图标＋7dp 内边距＝38dp 见方；AOSP GradientDrawable 把 corners 钳到短边一半＝19dp，
        // 14dp < 19dp ⇒ 不钳，不会出现「环比底更圆」。构造选四参那一条（色对随主题翻，与上面 12 行同一支）。
        // 不并入 48dp 高度档＝按 A 裁：minHeight 只给点播／直播两个入口行（a02 已落 :41／:110）。
        mBinding.vodHome.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.vodHistory.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.liveHome.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.liveHistory.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
    }

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
                setCacheText();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    private void onVod(View view) {
        ConfigDialog.create().vod().show(this);
    }

    private void onLive(View view) {
        ConfigDialog.create().live().show(this);
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create().vod().edit().show(this);
        return true;
    }

    private boolean onLiveEdit(View view) {
        ConfigDialog.create().live().edit().show(this);
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create().search().change().show(this);
    }

    private void onLiveHome(View view) {
        LiveDialog.show(this);
    }

    private void onVodHistory(View view) {
        HistoryDialog.create().vod().show(this);
    }

    private void onLiveHistory(View view) {
        HistoryDialog.create().live().show(this);
    }

    private void onPlayer(View view) {
        getRoot().change(2);
    }

    private void onDanmaku(View view) {
        getRoot().change(3);
    }

    private void onAbout(View view) {
        Task.execute(() -> {
            try {
                JSONObject object = new JSONObject(OkHttp.string("https://download.dovisliu.cn/firefly/about.json"));
                String msg = object.optString("name") + "\n" + object.optString("site") + "\n\n" + object.optString("disclaimer");
                App.post(() -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_about).setMessage(msg).setPositiveButton(android.R.string.ok, null).show());
            } catch (Exception e) {
                App.post(() -> com.firefly.tv.utils.Notify.show("关于信息获取失败"));
            }
        });
    }

    private void onDisplay(View view) {
        getRoot().change(6);
    }

    // 连点版本行 1 次 = 检查正式版；3 秒内连点满 5 次 = 检查测试版（隐藏入口，平时不触发）
    private int versionClicks;
    private final Runnable versionTask = this::checkOfficialUpdate;

    private void onVersion(View view) {
        App.removeCallbacks(versionTask);
        versionClicks++;
        if (versionClicks >= 5) {
            versionClicks = 0;
            Updater.create().beta().force().start(requireActivity());
            return;
        }
        App.post(versionTask, 800);
    }

    private void checkOfficialUpdate() {
        versionClicks = 0;
        Updater.create().force().start(requireActivity());
    }

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
        mBinding.incognitoText.setText(Setting.getSwitch(Setting.isIncognito()));
    }

    private void setDoh(View view) {
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_doh).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(getDohList(), getDohIndex(), (dialog, which) -> {
            setDoh(VodConfig.get().getDoh().get(which));
            dialog.dismiss();
        }).show();
    }

    private void setDoh(Doh doh) {
        OkHttp.dns().setDoh(doh);
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
    }

    // β33 D1：onLocal 包装方法随设置页「本地」行删除（仅此处引用）；HomeActivity.openLocal 仍由首页左上角入口用，保留。

    private void onCache(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                setCacheText();
            }
        });
    }

    private void onBackup(View view) {
        PermissionUtil.requestFile(this, allGranted -> AppDatabase.backup(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.backup_success);
            }

            @Override
            public void error() {
                Notify.show(R.string.backup_fail);
            }
        }));
    }

    // 导出：把 /sdcard/TV/ 下最新的备份文件通过系统分享发出（可发到流光/网盘/其他设备）
    private void onExport(View view) {
        PermissionUtil.requestFile(this, allGranted -> {
            File[] files = Path.tv().listFiles(f -> f.getName().startsWith("tv") && f.getName().endsWith(".bk.gz"));
            if (files == null || files.length == 0) {
                Notify.show(R.string.backup_export_none);
                return;
            }
            Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("*/*");
            send.putExtra(Intent.EXTRA_STREAM, FileUtil.getShareUri(files[0]));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.backup_export)));
        });
    }

    private void onRestore(View view) {
        PermissionUtil.requestFile(this, allGranted -> RestoreDialog.create().show(requireActivity(), new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                setOtherText();
                initConfig();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }));
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() != ConfigEvent.Type.COMMON) return;
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) return;
        setCacheText();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
    }
}
