package com.firefly.tv.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.AnimRes;
import androidx.annotation.ArrayRes;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.firefly.tv.App;
import com.firefly.tv.R;

public class ResUtil {

    /**
     * Round 38 / R5①（A 裁定 Q-3「焦点缩放全站统一 1.05，收进单点」）：全站唯一的焦点缩放值。
     * 此前 1.08 / 1.1 / 1.12 三档并存（海报卡 BaseVodHolder、收藏/历史卡、顶栏胶囊与 LOGO、功能宫格各写各的），
     * 同类控件跨页面的焦点反馈强弱不一 → Java 侧一律引用本常量，不再写字面量。
     * 说明：缩放只是焦点态的"位移反馈"，描边/底色表达见 leanback+mobile 的 selector_* 族与
     * main/res/values/dimens.xml 的 state_stroke_focus / state_stroke_select。
     */
    public static final float FOCUS_SCALE = 1.05f;

    /**
     * Round 38 / R5（A 裁定 P2「开关类选中 = 加粗 + 1.5dp 描边、不加底」）：**开关类文字加粗的唯一入口**。
     * 为什么需要它（beta23 首次构建踩到的坑，写死在这里防止再犯）：TextView 上并没有
     * `setFakeBoldText(boolean)`——那是 Paint/TextPaint 的 API，`view.setFakeBoldText()` 直接编译不通过；
     * 而 `getPaint().setFakeBoldText()` 是合成伪粗（描边式加粗，字形发糊）且不会自动重绘。
     * 故走 Typeface.create(当前字族, BOLD/NORMAL)：取真实粗体字面、幂等（反复切换不累加），
     * setTypeface 自带 invalidate。
     */
    public static void setSwitchBold(TextView view, boolean on) {
        if (view == null) return;
        view.setTypeface(Typeface.create(view.getTypeface(), on ? Typeface.BOLD : Typeface.NORMAL));
    }

    public static DisplayMetrics getDisplayMetrics() {
        return getDisplayMetrics(App.get());
    }

    public static DisplayMetrics getDisplayMetrics(Context context) {
        return context.getResources().getDisplayMetrics();
    }

    public static int getScreenWidth() {
        return getScreenWidth(App.get());
    }

    public static int getScreenWidth(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return getDisplayMetrics(context).widthPixels;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect rect = windowManager.getCurrentWindowMetrics().getBounds();
            return isLand(context) ? Math.max(rect.width(), rect.height()) : Math.min(rect.width(), rect.height());
        } else {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
            return displayMetrics.widthPixels;
        }
    }

    public static int getScreenHeight() {
        return getScreenHeight(App.get());
    }

    public static int getScreenHeight(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return getDisplayMetrics(context).heightPixels;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect rect = windowManager.getCurrentWindowMetrics().getBounds();
            return isLand(context) ? Math.min(rect.width(), rect.height()) : Math.max(rect.width(), rect.height());
        } else {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
            return displayMetrics.heightPixels;
        }
    }

    public static boolean isEdge(Context context, MotionEvent e, int edge) {
        return e.getRawX() < edge || e.getRawX() > getScreenWidth(context) - edge || e.getRawY() < edge || e.getRawY() > getScreenHeight(context) - edge;
    }

    public static boolean isLand(Context context) {
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static boolean isPad() {
        return isPad(App.get());
    }

    public static boolean isPad(Context context) {
        return context.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    public static int sp2px(int sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getDisplayMetrics());
    }

    public static int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getDisplayMetrics());
    }

    public static String getString(@StringRes int resId) {
        return App.get().getResources().getString(resId);
    }

    public static String getString(@StringRes int resId, Object... formatArgs) {
        return App.get().getResources().getString(resId, formatArgs);
    }

    public static String[] getStringArray(@ArrayRes int resId) {
        return App.get().getResources().getStringArray(resId);
    }

    public static TypedArray getTypedArray(@ArrayRes int resId) {
        return App.get().getResources().obtainTypedArray(resId);
    }

    public static Drawable getDrawable(@DrawableRes int resId) {
        return ContextCompat.getDrawable(App.get(), resId);
    }

    public static int getColor(@ColorRes int resId) {
        return ContextCompat.getColor(App.get(), resId);
    }

    // 菜单图标跟随主题染色（深色=白、浅色=深），修复浅色模式下硬编码白色图标不可见。
    // 注意：不可用 resolveAttribute(colorControlNormal)——M3 主题下该属性解析可能返回非颜色值，
    // setTint(坏值) 会导致图标完全透明（beta2 首版事故）；改用 icon_primary 颜色资源（DayNight 自动切换）。
    public static void tintMenuIcons(Menu menu) {
        int color = getColor(R.color.icon_primary);
        for (int i = 0; i < menu.size(); i++) {
            Drawable icon = menu.getItem(i).getIcon();
            if (icon != null) icon.setTint(color);
        }
    }

    public static Animation getAnim(@AnimRes int resId) {
        return AnimationUtils.loadAnimation(App.get(), resId);
    }

    public static Display getDisplay(Context context) {
        return ContextCompat.getDisplayOrDefault(context);
    }

    public static int getTextWidth(String content, int size) {
        Paint paint = new Paint();
        paint.setTextSize(sp2px(size));
        return (int) paint.measureText(content);
    }
}
