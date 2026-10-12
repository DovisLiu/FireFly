package com.firefly.tv;

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

public class Updater implements Download.Callback, UpdateListener {

    private UpdateDialog dialog;
    private String version;          // 目标版本号（来自 JSON 的 name），用于拼接 APK 下载路径
    private String[] apkUrls;         // 候选下载地址，按优先级排列（GitHub → Gitee）
    private int apkIndex = 0;         // 当前尝试到第几个下载源
    private Download download;
    private boolean beta;             // 测试通道：仅手动触发（连点版本行 5 次），读 beta JSON
    private boolean forced;           // 手动触发标记：正式检查允许同 code 重装与回退弹窗

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    public Updater beta() {
        this.beta = true;
        return this;
    }

    private File getFile() {
        return Path.cache("update.apk");
    }

    public Updater force() {
        forced = true;
        Notify.show(R.string.update_check);
        if (!beta) Setting.putUpdate(true);
        return this;
    }

    public void start(FragmentActivity activity) {
        if (!beta && !Setting.getUpdate()) return;
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
        startDownload();
    }

    private void startDownload() {
        download = Download.create(apkUrls[apkIndex], getFile());
        download.start(this);
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        if (download != null) download.cancel();
        dismiss();
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress) {
        if (dialog != null) dialog.setProgress(progress);
    }

    @Override
    public void error(String msg) {
        // 当前下载源失败，自动切换到下一个更新源重试
        if (apkIndex + 1 < apkUrls.length) {
            apkIndex++;
            startDownload();
            return;
        }
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        FileUtil.openFile(file);
        dismiss();
    }
}
