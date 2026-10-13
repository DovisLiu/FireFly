package com.firefly.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.firefly.tv.utils.Notify;
import com.fongmi.hook.Hook;
import com.github.catvod.Init;
import com.google.gson.Gson;

public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private static volatile App instance;

    private final Handler handler;
    private final Gson gson;
    private final long time;

    private Activity activity;
    private Hook hook;

    public App() {
        instance = this;
        gson = new Gson();
        time = System.currentTimeMillis();
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    public static App get() {
        return instance;
    }

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return get().activity;
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }

    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) get().handler.removeCallbacks(r);
    }

    public void setHook(Hook hook) {
        this.hook = hook;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        applyThemeMode();
        Notify.createChannel();
        registerActivityLifecycleCallbacks(this);
        com.firefly.tv.utils.Task.execute(com.firefly.tv.utils.Report::ping);
    }

    // 外观模式：0 跟随系统 / 1 浅色 / 2 深色（AppCompatDelegate 全局生效）
    // TV 设备无系统深色开关，"跟随系统"默认按深色处理（2026-10-04 刘先生拍板）
    private void applyThemeMode() {
        int mode = com.firefly.tv.setting.Setting.getThemeMode();
        boolean leanback = getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK);
        // TV 端尊重用户选择：仅"跟随系统"时默认深色（TV 无系统深色开关）；浅色/深色设置持久生效
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode == com.firefly.tv.setting.Setting.MODE_LIGHT ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO : mode == com.firefly.tv.setting.Setting.MODE_SYSTEM ? (leanback ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }

    private final java.util.concurrent.atomic.AtomicInteger resumedCount = new java.util.concurrent.atomic.AtomicInteger();

    /** 应用是否有前台界面（供保活服务判断屏保亮屏后是否需要拉回）。 */
    public static boolean isForeground() {
        return get().resumedCount.get() > 0;
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        resumedCount.incrementAndGet();
        if (activity != activity()) this.activity = activity;
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        resumedCount.decrementAndGet();
        if (activity == activity()) this.activity = null;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }
}