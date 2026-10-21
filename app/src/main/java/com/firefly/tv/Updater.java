package com.firefly.tv;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.firefly.tv.impl.UpdateListener;
import com.firefly.tv.setting.Setting;
import com.firefly.tv.ui.dialog.UpdateDialog;
import com.firefly.tv.utils.Download;
import com.firefly.tv.utils.FileUtil;
import com.firefly.tv.utils.Update;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.ResUtil;
import com.firefly.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import org.json.JSONObject;

import java.io.File;

import android.os.Handler;
import android.os.Looper;

/**
 * 应用自更新：改用系统 DownloadManager 下载（断点续传/自动重试/锁屏照下/完整性由系统保证），
 * 彻底解决自研下载器在锁屏/弱网下"下载不完整""100% 回退"的问题。
 */
public class Updater implements UpdateListener {

    private static final long POLL_MS = 500;

    private UpdateDialog dialog;
    private String version;          // 目标版本号（来自 JSON 的 name），用于拼接 APK 下载路径
    private String[] apkUrls;         // 候选下载地址，按优先级排列（服务器 → GitHub）
    private int apkIndex = 0;         // 当前尝试到第几个下载源
    private boolean beta;             // 测试通道：仅手动触发（连点版本行 5 次），读 beta JSON
    private boolean forced;           // 手动触发标记：正式检查允许同 code 重装与回退弹窗
    private long downloadId = -1;     // DownloadManager 任务 id
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollTask = this::pollProgress;
    private boolean legacyMode;       // 兜底通道：DownloadManager 异常时回退自研下载器
    private Download legacyDownload;

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    public Updater beta() {
        this.beta = true;
        return this;
    }

    public Updater force() {
        forced = true;
        Notify.show(R.string.update_check);
        return this;
    }

    public void start(FragmentActivity activity) {
        Task.execute(() -> doInBackground(activity));
    }

    private void doInBackground(FragmentActivity activity) {
        try {
            JSONObject object = beta ? fetchBetaJson() : fetchJson();
            String name = object.optString("name");
            String desc = object.optString("desc");
            int code = object.optInt("code");
            // 测试通道：code>0 即提供更新（版本号随意——允许同版本反复重装/升降级）；code=0=无测试版
            if (beta && code <= 0) {
                App.post(() -> Notify.show(R.string.update_beta_none));
                return;
            }
            // 自动检查：严格只认更高 code；手动检查（force）：非更高 code（含占位 code=0）一律提示已最新
            // 回退场景统一走测试通道（同 code 覆盖），正式通道不做降级弹窗，避免占位 JSON 误弹
            if (!beta && code <= BuildConfig.VERSION_CODE) {
                if (forced) App.post(() -> Notify.show(R.string.update_latest));
                return;
            }
            this.version = name;
            App.post(() -> show(activity, name, desc));
        } catch (Exception e) {
            if (beta) App.post(() -> Notify.show(R.string.update_beta_none));
            else e.printStackTrace();
        }
    }

    // 测试通道只问服务器一份 JSON，失败即"暂无测试版"，不回退镜像
    private JSONObject fetchBetaJson() throws Exception {
        return new JSONObject(OkHttp.string(Update.betaJsonUrl(BuildConfig.FLAVOR_mode)));
    }

    // 短路式"有新版即停"：依次问 服务器 → Gitee → GitHub，任一渠道返回比当前版本新的 code 立即采用；
    // 渠道无响应或返回的不是新版（如服务器忘了同步 JSON）则继续问下一个；全问完取见到的最新一份
    private JSONObject fetchJson() throws Exception {
        JSONObject best = null;
        Exception last = null;
        for (String url : Update.jsonUrls(BuildConfig.FLAVOR_mode)) {
            try {
                JSONObject object = new JSONObject(OkHttp.string(url));
                if (object.optInt("code") > BuildConfig.VERSION_CODE) return object;
                if (best == null || object.optInt("code") > best.optInt("code")) best = object;
            } catch (Exception e) {
                last = e;
            }
        }
        if (best != null) return best;
        throw last != null ? last : new Exception("update check failed");
    }

    private void show(FragmentActivity activity, String version, String desc) {
        dismiss();
        dialog = UpdateDialog.create().title(ResUtil.getString(R.string.update_version, version)).desc(desc).listener(this).show(activity);
    }

    @Override
    public void onConfirm(View view) {
        view.setEnabled(false);
        apkIndex = 0;
        apkUrls = Update.getApkUrls(version, BuildConfig.FLAVOR_mode, BuildConfig.FLAVOR_abi);
        // 清理自研下载器时代的残留缓存
        Path.cache("update.apk").delete();
        startDownload();
    }

    private void startDownload() {
        // 部分 Android 9 电视的 DownloadProvider 数据库异常（no such column），TV 端直接走自研兜底
        if ("leanback".equals(BuildConfig.FLAVOR_mode)) {
            if (!legacyMode) {
                legacyMode = true;
            }
            startLegacyDownload();
            return;
        }
        try {
            DownloadManager dm = (DownloadManager) App.get().getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) throw new IllegalStateException("DownloadManager unavailable");
            DownloadManager.Request request = new DownloadManager.Request(android.net.Uri.parse(apkUrls[apkIndex]));
            request.setTitle(ResUtil.getString(R.string.app_name) + " " + version);
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            File dir = App.get().getExternalFilesDir(null);
            File target = new File(dir, "update.apk");
            if (target.exists()) target.delete();
            request.setDestinationInExternalFilesDir(App.get(), null, "update.apk");
            downloadId = dm.enqueue(request);
            App.post(pollTask);
        } catch (Throwable e) {
            // 个别 ROM（如部分 Android 9 电视）DownloadProvider 数据库异常，兜底通道接管
            onDownloadFailed(e.getMessage());
        }
    }

    private void pollProgress() {
        if (dialog == null || downloadId < 0) return;
        DownloadManager dm = (DownloadManager) App.get().getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return;
        Cursor cursor = dm.query(new DownloadManager.Query().setFilterById(downloadId));
        if (cursor == null) return;
        try {
            if (!cursor.moveToFirst()) return;
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                File file = new File(App.get().getExternalFilesDir(null), "update.apk");
                stopPoll();
                if (file.isFile() && file.length() > 0) {
                    if (dialog != null) dialog.setProgress(100);
                    FileUtil.openFile(file);
                    dismiss();
                } else {
                    onDownloadFailed("empty download");
                }
            } else if (status == DownloadManager.STATUS_FAILED || status == DownloadManager.STATUS_PAUSED && cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)) == DownloadManager.PAUSED_WAITING_FOR_NETWORK) {
                // 失败换源；等待网络（PAUSED）时继续轮询等待恢复
                if (status == DownloadManager.STATUS_FAILED) {
                    int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                    stopPoll();
                    onDownloadFailed("status " + status + " reason " + reason);
                    return;
                }
                pollHandler.postDelayed(pollTask, POLL_MS);
            } else {
                long soFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                if (total > 0 && dialog != null) dialog.setProgress((int) (soFar * 100 / total));
                pollHandler.postDelayed(pollTask, POLL_MS);
            }
        } catch (Throwable e) {
            // 个别 ROM 的 DownloadProvider 查询异常（跨进程抛出），兜底通道接管
            stopPoll();
            onDownloadFailed(e.getMessage());
        } finally {
            try {
                cursor.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private void stopPoll() {
        pollHandler.removeCallbacks(pollTask);
    }

    private void onDownloadFailed(String msg) {
        downloadId = -1;
        // 当前下载源失败，自动切换到下一个更新源重试
        if (apkIndex + 1 < apkUrls.length) {
            apkIndex++;
            startDownload();
            return;
        }
        // 兜底：所有下载源的系统下载都失败（个别 ROM DownloadManager 异常），回退自研下载器
        if (!legacyMode) {
            legacyMode = true;
            apkIndex = 0;
            startLegacyDownload();
            return;
        }
        Notify.show(msg != null ? msg : ResUtil.getString(R.string.update_latest));
        dismiss();
    }

    // 自研下载器兜底：带完整性校验/自动重试/断点续传/WakeLock 全套补丁
    private void startLegacyDownload() {
        legacyDownload = Download.create(apkUrls[apkIndex], Path.cache("update.apk"));
        legacyDownload.start(new Download.Callback() {
            @Override
            public void progress(int progress) {
                if (dialog != null) dialog.setProgress(progress);
            }

            @Override
            public void error(String msg) {
                if (apkIndex + 1 < apkUrls.length) {
                    apkIndex++;
                    startLegacyDownload();
                    return;
                }
                Notify.show(msg != null ? msg : ResUtil.getString(R.string.update_latest));
                dismiss();
            }

            @Override
            public void success(File file) {
                if (dialog != null) dialog.setProgress(100);
                FileUtil.openFile(file);
                dismiss();
            }
        });
    }

    @Override
    public void onCancel(View view) {
        // 取消仅当次生效：本次启动不再弹，下次进入应用照常提示
        stopPoll();
        if (downloadId >= 0) {
            DownloadManager dm = (DownloadManager) App.get().getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) dm.remove(downloadId);
            downloadId = -1;
        }
        if (legacyDownload != null) legacyDownload.cancel();
        dismiss();
    }

    private void dismiss() {
        stopPoll();
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }
}
