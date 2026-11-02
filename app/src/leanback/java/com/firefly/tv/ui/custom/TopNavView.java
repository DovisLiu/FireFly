package com.firefly.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.appcompat.widget.LinearLayoutCompat;

import androidx.annotation.Nullable;

import com.firefly.tv.R;
import com.firefly.tv.databinding.ViewTopNavBinding;
import com.firefly.tv.utils.ResUtil;

/**
 * TV 2.0 顶部胶囊导航（tv-2.0-ui-spec.md §2.1 定稿，固定 6 项）：
 * 搜索(图标) / 首页 / 点播 / 直播 / 片源 / 设置。
 * 源分类（电影/剧集/动漫/综艺等）属点播页二级菜单，源返回什么就列什么，不占一级导航。
 * 规格见 tv-2.0-ui-spec.md §2.1：一条整体胶囊容器；当前页高亮胶囊反色字；
 * 仓库切换由左上 LOGO 承载（不占导航）。
 */
public class TopNavView extends LinearLayoutCompat {

    public interface OnNavListener {
        void onNavHome();

        void onNavSearch();

        void onNavVod();

        void onNavLive();

        void onNavSource();

        void onNavSetting();

        /** 焦点悬停导航项（TV 2.0：首页二级菜单直出用，其他页默认无操作） */
        default void onNavHover(int navId) {
        }
    }

    public interface OnBarFocusChanged {
        void onChanged(boolean focused);
    }

    private ViewTopNavBinding binding;
    private OnNavListener listener;
    private OnBarFocusChanged barFocusListener;
    private int focusCount;

    public TopNavView(Context context) {
        this(context, null);
    }

    public TopNavView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(LinearLayoutCompat.HORIZONTAL);
        binding = ViewTopNavBinding.inflate(android.view.LayoutInflater.from(context), this, true);
        bind(binding.navSearch, () -> { if (listener != null) listener.onNavSearch(); }, R.id.nav_search);
        bind(binding.navHome, () -> { if (listener != null) listener.onNavHome(); }, R.id.nav_home);
        bind(binding.navVod, () -> { if (listener != null) listener.onNavVod(); }, R.id.nav_vod);
        bind(binding.navLive, () -> { if (listener != null) listener.onNavLive(); }, R.id.nav_live);
        bind(binding.navSource, () -> { if (listener != null) listener.onNavSource(); }, R.id.nav_source);
        bind(binding.navSetting, () -> { if (listener != null) listener.onNavSetting(); }, R.id.nav_setting);
    }

    private void bind(View view, Runnable action, int navId) {
        view.setOnClickListener(v -> action.run());
        view.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? ResUtil.FOCUS_SCALE : 1f).scaleY(hasFocus ? ResUtil.FOCUS_SCALE : 1f).setDuration(150).start();
            if (hasFocus) {
                if (focusCount++ == 0 && barFocusListener != null) barFocusListener.onChanged(true);
                if (listener != null) listener.onNavHover(navId);
            } else if (--focusCount == 0 && barFocusListener != null) {
                barFocusListener.onChanged(false);
            }
        });
    }

    /** 导航条整体焦点状态（浮层显隐联动用） */
    public void setOnBarFocusChanged(OnBarFocusChanged listener) {
        this.barFocusListener = listener;
    }

    View navItemOf(int id) {
        if (id == R.id.nav_search) return binding.navSearch;
        if (id == R.id.nav_vod) return binding.navVod;
        if (id == R.id.nav_live) return binding.navLive;
        if (id == R.id.nav_source) return binding.navSource;
        if (id == R.id.nav_setting) return binding.navSetting;
        return binding.navHome;
    }

    /**
     * β4 / T-208：判定某视图**是不是那 6 颗胶囊之一**。
     * 不用 `getParent()==this`——`view_top_nav.xml` 的根是 LinearLayoutCompat，
     * inflate 进 TopNavView 后胶囊的父级是那个内层根，而非 TopNavView 本身
     * （旧代码里 `focus.getParent() == topNav` 因此恒为 false，是条死守卫）。
     */
    public boolean isNavItem(View v) {
        if (v == null) return false;
        return v == binding.navSearch || v == binding.navHome || v == binding.navVod
                || v == binding.navLive || v == binding.navSource || v == binding.navSetting;
    }

    /** 呼出后焦点落位（浮层 reveal 用） */
    public void requestItem(int id) {
        navItemOf(id).requestFocus();
    }

    /**
     * T-188-b2（Round 42，C beta26 实测「UP 完全不响应」）：与 requestItem 同一条落点，但把
     * requestFocus 的结果如实回传——原先 NavOverlay.reveal() 无论焦点是否真的移动都 return true，
     * 一旦目标项当帧拿不到焦点，按键就被吞掉，表现为"按 UP 什么都不发生"（比 beta25 落错项更糟）。
     */
    public boolean requestFocusItem(int id) {
        View item = navItemOf(id);
        return item.requestFocus() || item.hasFocus();
    }

    /** 当前页高亮（selected 态 = 高亮胶囊反色字） */
    public void setCurrent(int id) {
        for (View v : new View[]{binding.navSearch, binding.navHome, binding.navVod, binding.navLive, binding.navSource, binding.navSetting}) {
            v.setSelected(false);
        }
        View target = null;
        if (id == R.id.nav_home) target = binding.navHome;
        else if (id == R.id.nav_vod) target = binding.navVod;
        else if (id == R.id.nav_live) target = binding.navLive;
        else if (id == R.id.nav_source) target = binding.navSource;
        else if (id == R.id.nav_setting) target = binding.navSetting;
        if (target != null) target.setSelected(true);
    }

    /** 首页页内默认焦点：导航行 */
    public void requestInitialFocus() {
        binding.navHome.requestFocus();
    }

    public void setListener(OnNavListener listener) {
        this.listener = listener;
    }

    public ViewTopNavBinding getBinding() {
        return binding;
    }
}
