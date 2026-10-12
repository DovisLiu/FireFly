package com.firefly.tv;

import androidx.fragment.app.FragmentActivity;

import com.firefly.tv.bean.Notice;
import com.firefly.tv.setting.Setting;
import com.firefly.tv.ui.dialog.NoticeDialog;
import com.firefly.tv.utils.Update;
import com.firefly.tv.utils.Task;
import com.github.catvod.net.OkHttp;

public class NoticeManager {

    private Notice notice;
    private NoticeDialog dialog;
    private int stage;

    private NoticeManager() {
    }

    public static NoticeManager create() {
        return new NoticeManager();
    }

    // 启动时调用：拉取 notice.json 决定本次启动弹什么
    public void check(FragmentActivity activity) {
        Task.execute(() -> doInBackground(activity));
    }

    // stage=3（停止服务）时每次 onResume 调用：弹窗不在则补弹，防跳浏览器/回桌面绕过
    public void recheck(FragmentActivity activity) {
        if (stage != 3 || notice == null) return;
        if (dialog != null && dialog.getDialog() != null && dialog.getDialog().isShowing()) return;
        show(activity);
    }

    private void doInBackground(FragmentActivity activity) {
        stage = -1;
        notice = null;
        // 三源轮询：服务器 → Gitee → GitHub，任一拉到即用
        for (String url : Update.noticeUrls()) {
            try {
                Notice object = Notice.objectFrom(OkHttp.string(url));
                if (object != null && object.getStage() > 0) {
                    notice = object;
                    stage = object.getStage();
                    // 持久化最后已知阶段：此后断网/三源全挂仍按此阶段拦截（服务器改回低值即可解锁在线用户）
                    Setting.putNoticeStage(stage);
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // 拉取失败退回最后已知阶段；从未拉到过则 0 = 静默，绝不因网络问题误弹
        if (stage < 0) stage = Setting.getNoticeStage();
        if (stage <= 0) return;
        if (notice == null) notice = new Notice(stage);
        // 提示阶段每日最多弹一次；首次安装弹窗只弹一次（弹过永久记住）；强制/停止阶段每次启动都弹
        if (stage == 1 && Setting.isNoticeShownToday()) return;
        if (stage == 4 && Setting.isNoticeFirstShown()) return;
        App.post(() -> show(activity));
    }

    private void show(FragmentActivity activity) {
        if (stage == 1) Setting.putNoticeShownToday();
        if (stage == 4) Setting.putNoticeFirstShown();
        dismiss();
        dialog = NoticeDialog.create().notice(notice).stage(stage).show(activity);
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }
}
