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
import com.firefly.tv.db.AppDatabase;
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
            AppDatabase.get().getConfigDao().deleteType(0);
            AppDatabase.get().getConfigDao().deleteType(1);
            AppDatabase.get().getConfigDao().deleteType(2);
            Prefers.put("config_0", "");
            Prefers.put("config_1", "");
            Prefers.put("config_2", "");
            Prefers.put("crash", false);
            App.post(() -> {
                Notify.show(R.string.crash_reset_done);
                CustomActivityOnCrash.restartApplication(this, Objects.requireNonNull(CustomActivityOnCrash.getConfigFromIntent(getIntent())));
            });
        });
    }
}
