package com.firefly.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;

import com.firefly.tv.event.EventIndex;
import com.firefly.tv.setting.Setting;
import com.firefly.tv.ui.activity.CrashActivity;
import com.firefly.tv.utils.ExitInfo;
import com.firefly.tv.utils.Notify;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;

import org.greenrobot.eventbus.EventBus;

import java.util.Collections;
import java.util.List;

import cat.ereza.customactivityoncrash.config.CaocConfig;

public class Startup implements Initializer<Void> {

    // 主循环救援限流：30 秒窗口内最多救援 10 次，超出视为崩溃风暴，交回 CAOC
    private static int rescueCount;
    private static long windowStart;

    @NonNull
    @Override
    public Void create(@NonNull Context context) {
        CaocConfig.Builder.create().trackActivities(true).backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT).errorActivity(CrashActivity.class).apply();
        hookBackgroundCrashes();
        Logger.addLogAdapter(new AndroidLogAdapter(PrettyFormatStrategy.newBuilder().methodCount(0).showThreadInfo(false).tag("TV").build()));
        EventBus.builder().addIndex(new EventIndex()).installDefaultEventBus();
        OkHttp.dns().setDoh(() -> Doh.objectFrom(Setting.getDoh()));
        reportLastExit();
        return null;
    }

    /**
     * 启动时检查上次进程退出原因（Android 11+ 官方 API）。
     * 若为异常退出（原生崩溃/信号/ANR 等），等首个 Activity 就绪后弹出完整详情对话框
     * （Toast 显示不全），支持一键复制全部内容，便于取证"闪退无崩溃页"类问题。
     */
    private void reportLastExit() {
        if (!ExitInfo.lastExitAbnormal()) return;
        String info = ExitInfo.describe();
        Log.e("TV", "Last abnormal exit:\n" + info);
        App.get().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                App.get().unregisterActivityLifecycleCallbacks(this);
                showExitDialog(activity, info);
            }

            @Override public void onActivityCreated(@NonNull Activity a, android.os.Bundle b) { }
            @Override public void onActivityStarted(@NonNull Activity a) { }
            @Override public void onActivityPaused(@NonNull Activity a) { }
            @Override public void onActivityStopped(@NonNull Activity a) { }
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull android.os.Bundle b) { }
            @Override public void onActivityDestroyed(@NonNull Activity a) { }
        });
    }

    private void showExitDialog(Context context, String info) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.exit_dialog_title)
                .setMessage(info)
                .setPositiveButton(R.string.crash_copy, (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("exit", info));
                    Notify.show(R.string.crash_copied);
                })
                .setNegativeButton(R.string.crash_details_close, null)
                .create();
        dialog.show();
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextIsSelectable(true);
    }

    /**
     * 第三方源 jar 的崩溃加固：
     * 1) 子线程未捕获异常：只记录日志、不杀进程（如 GoProxy 下载 so 失败后的 dlopen）；
     * 2) 主线程未捕获异常且崩溃点来自 jar（经 DexClassLoader 加载的类）："主循环救援"——
     *    记录日志后重新进入 Looper.loop()，丢弃出错的那条消息，应用继续运行（Cockroach 手法）；
     * 3) 应用自身的崩溃不受影响，仍交回原处理器（CAOC 崩溃页）。
     */
    private void hookBackgroundCrashes() {
        Thread.UncaughtExceptionHandler delegate = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (thread != Looper.getMainLooper().getThread()) {
                Log.e("TV", "Uncaught exception on background thread: " + thread.getName(), throwable);
                return;
            }
            if (isForeign(throwable) && allowRescue()) {
                Log.e("TV", "Rescuing main loop from foreign crash", throwable);
                Looper.loop();
                Log.e("TV", "Main loop exited unexpectedly, falling back to crash handler");
            }
            if (delegate != null) delegate.uncaughtException(thread, throwable);
        });
    }

    /**
     * 判断崩溃是否来自外部源 jar：取调用栈中第一个非系统帧，
     * 应用类加载器找不到该类（即经 DexClassLoader 加载）视为外部崩溃。
     */
    private static boolean isForeign(Throwable throwable) {
        for (StackTraceElement e : throwable.getStackTrace()) {
            String cls = e.getClassName();
            if (cls.startsWith("android.") || cls.startsWith("androidx.") || cls.startsWith("android.support")) continue;
            if (cls.startsWith("java.") || cls.startsWith("javax.") || cls.startsWith("sun.") || cls.startsWith("dalvik.") || cls.startsWith("com.android.internal")) continue;
            if (cls.startsWith("com.github.catvod.spider") || cls.startsWith("merge.") || cls.startsWith("com.whl.quickjs")) return true;
            try {
                Class.forName(cls, false, Startup.class.getClassLoader());
                return false;
            } catch (ClassNotFoundException ex) {
                return true;
            }
        }
        return false;
    }

    private static synchronized boolean allowRescue() {
        long now = SystemClock.elapsedRealtime();
        if (now - windowStart > 30_000) {
            windowStart = now;
            rescueCount = 0;
        }
        return ++rescueCount <= 10;
    }

    @NonNull
    @Override
    public List<Class<? extends Initializer<?>>> dependencies() {
        return Collections.emptyList();
    }
}
