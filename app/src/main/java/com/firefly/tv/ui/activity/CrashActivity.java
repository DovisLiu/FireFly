package com.firefly.tv.ui.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.appcompat.app.AlertDialog;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.App;
import com.firefly.tv.R;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.ResetLog;
import com.firefly.tv.db.AppDatabase;
import com.firefly.tv.db.dao.ConfigDao;
import com.firefly.tv.databinding.ActivityCrashBinding;
import com.firefly.tv.ui.base.BaseActivity;
import com.firefly.tv.utils.ExitInfo;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.Task;
import com.github.catvod.utils.Prefers;

import java.util.Objects;

import cat.ereza.customactivityoncrash.CustomActivityOnCrash;

public class CrashActivity extends BaseActivity {

    private ActivityCrashBinding mBinding;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCrashBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCrash();
    }

    @Override
    protected void initEvent() {
        mBinding.details.setOnClickListener(v -> showError());
        mBinding.copy.setOnClickListener(v -> copyError());
        mBinding.reset.setOnClickListener(v -> confirmReset());
        mBinding.restart.setOnClickListener(v -> CustomActivityOnCrash.restartApplication(this, Objects.requireNonNull(CustomActivityOnCrash.getConfigFromIntent(getIntent()))));
    }

    private void setCrash() {
        String log = CustomActivityOnCrash.getActivityLogFromIntent(getIntent());
        if (TextUtils.isEmpty(log)) return;
        String[] lines = log.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].isEmpty()) continue;
            if (lines[i].contains(HomeActivity.class.getSimpleName())) {
                Prefers.put("crash", true);
                break;
            }
        }
    }

    private void showError() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.crash_details_title)
                .setMessage(CustomActivityOnCrash.getAllErrorDetailsFromIntent(this, getIntent()))
                .setPositiveButton(R.string.crash_details_close, null)
                .show();
    }

    private void copyError() {
        String log = CustomActivityOnCrash.getAllErrorDetailsFromIntent(this, getIntent()) + "\n\n---- Last process exit records ----\n" + ExitInfo.describe();
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || TextUtils.isEmpty(log)) return;
        manager.setPrimaryClip(ClipData.newPlainText("crash", log));
        Notify.show(R.string.crash_copied);
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.crash_reset)
                .setMessage(R.string.crash_reset_confirm)
                .setPositiveButton(R.string.crash_reset, (dialog, which) -> resetConfig())
                .setNegativeButton(R.string.crash_details_close, null)
                .show();
    }

    private void resetConfig() {
        Task.execute(() -> {
            // β56 / TV-03：清源范围收窄——只删「当前点播源那一条」；直播源零改动；去掉无写入点的 type=2。
            // 口径（刘先生逐条拍定）：只清不换（不回落、不切上一条）；不升 DB 版本、不新增 DAO 方法；
            // url 为空则什么都不删、连 Prefers 也不动，只打 skip 留痕。
            ConfigDao dao = AppDatabase.get().getConfigDao();
            String url = VodConfig.get().getConfig().getUrl();
            if (TextUtils.isEmpty(url)) {
                ResetLog.skipNoUrl(dao.findByType(0).size(), dao.findByType(1).size());
            } else {
                int before = dao.findByType(0).size();
                dao.delete(url, 0);
                int remainVod = dao.findByType(0).size();
                ResetLog.one(url, before - remainVod, remainVod, dao.findByType(1).size());
                Prefers.put("config_0", "");
            }
            Prefers.put("crash", false);
            App.post(() -> {
                Notify.show(R.string.crash_reset_done);
                CustomActivityOnCrash.restartApplication(this, Objects.requireNonNull(CustomActivityOnCrash.getConfigFromIntent(getIntent())));
            });
        });
    }
}
