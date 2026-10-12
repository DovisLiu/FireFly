package com.firefly.tv.utils;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;

import com.firefly.tv.App;

import java.util.List;

/**
 * 查询本应用上一次的进程退出原因（Android 11+ 官方 API，无需任何权限）。
 * 用于取证"闪退但无崩溃页"的场景：若 reason=SIGNALED/CRASH_NATIVE 即为原生层崩溃，
 * Java 层任何异常兜底（含主循环救援）都无法拦截；若为 CRASH 则可被崩溃链路捕获。
 */
public class ExitInfo {

    public static String describe() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "系统不支持查询退出原因（需 Android 11+）";
        try {
            ActivityManager am = (ActivityManager) App.get().getSystemService(Context.ACTIVITY_SERVICE);
            List<ApplicationExitInfo> list = am == null ? null : am.getHistoricalProcessExitReasons(App.get().getPackageName(), 0, 3);
            if (list == null || list.isEmpty()) return "无历史退出记录";
            StringBuilder sb = new StringBuilder();
            for (ApplicationExitInfo info : list) {
                sb.append("reason=").append(reasonName(info.getReason()))
                        .append(" status=").append(info.getStatus())
                        .append(" time=").append(new java.util.Date(info.getTimestamp()))
                        .append("\n");
            }
            return sb.toString().trim();
        } catch (Throwable e) {
            return "查询失败: " + e;
        }
    }

    /** 最近一次退出是否异常（崩溃/信号/ANR/低内存），用于启动时提示取证 */
    public static boolean lastExitAbnormal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        try {
            ActivityManager am = (ActivityManager) App.get().getSystemService(Context.ACTIVITY_SERVICE);
            List<ApplicationExitInfo> list = am == null ? null : am.getHistoricalProcessExitReasons(App.get().getPackageName(), 0, 1);
            if (list == null || list.isEmpty()) return false;
            int reason = list.get(0).getReason();
            return reason == ApplicationExitInfo.REASON_CRASH || reason == ApplicationExitInfo.REASON_CRASH_NATIVE
                    || reason == ApplicationExitInfo.REASON_SIGNALED || reason == ApplicationExitInfo.REASON_ANR
                    || reason == ApplicationExitInfo.REASON_LOW_MEMORY;
        } catch (Throwable e) {
            return false;
        }
    }

    private static String reasonName(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_EXIT_SELF:
                return "EXIT_SELF(应用自行退出,疑似System.exit)";
            case ApplicationExitInfo.REASON_SIGNALED:
                return "SIGNALED(被信号杀死,疑似原生层崩溃)";
            case ApplicationExitInfo.REASON_CRASH:
                return "CRASH(Java未捕获异常)";
            case ApplicationExitInfo.REASON_CRASH_NATIVE:
                return "CRASH_NATIVE(原生层崩溃)";
            case ApplicationExitInfo.REASON_ANR:
                return "ANR(无响应被杀)";
            case ApplicationExitInfo.REASON_LOW_MEMORY:
                return "LOW_MEMORY(内存不足被杀)";
            case ApplicationExitInfo.REASON_USER_REQUESTED:
            case ApplicationExitInfo.REASON_USER_STOPPED:
                return "USER(用户正常退出)";
            default:
                return "REASON_" + reason;
        }
    }
}
