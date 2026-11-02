package com.firefly.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.HorizontalGridView;

import com.firefly.tv.utils.KeyUtil;

/**
 * TV 2.0 直播换台面板横向列表（效果图 05/09：分组胶囊横行 / 频道卡网格）。
 * 左右键=行内循环（与竖版 CustomLiveListView 行为对齐），焦点事件回调 setUITimer。
 */
public class CustomLiveGridView extends HorizontalGridView {

    private CustomLiveListView.Callback listener;

    public CustomLiveGridView(@NonNull Context context) {
        super(context);
    }

    public CustomLiveGridView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomLiveGridView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setListener(CustomLiveListView.Callback listener) {
        this.listener = listener;
    }

    private boolean onKeyRight() {
        if (getSelectedPosition() != getAdapter().getItemCount() - 1) return false;
        setSelectedPosition(0);
        return true;
    }

    private boolean onKeyLeft() {
        if (getSelectedPosition() != 0) return false;
        setSelectedPosition(getAdapter().getItemCount() - 1);
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (getVisibility() == View.GONE || event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        if (getVisibility() == View.VISIBLE && listener != null) listener.setUITimer();
        if (KeyUtil.isRightKey(event)) return onKeyRight();
        if (KeyUtil.isLeftKey(event)) return onKeyLeft();
        return super.dispatchKeyEvent(event);
    }
}
