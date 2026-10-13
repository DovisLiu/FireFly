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
