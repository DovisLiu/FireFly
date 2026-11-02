package com.firefly.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firefly.tv.R;
import com.firefly.tv.utils.KeyUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class CustomVerticalGridView extends VerticalGridView {

    private static final int CARRY_UP_DELAY = 25;
    private static final int CARRY_UP_RETRY = 3;

    private List<View> views;
    private boolean pressDown;
    private boolean pressUp;
    private boolean moveTop;

    public CustomVerticalGridView(@NonNull Context context) {
        this(context, null);
    }

    public CustomVerticalGridView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomVerticalGridView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setMoveTop(true);
    }

    @Override
    protected void initAttributes(@NonNull Context context, @Nullable AttributeSet attrs) {
        super.initAttributes(context, attrs);
        setOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable ViewHolder child, int position, int subposition) {
                if (pressDown && position == 1) hideHeader();
                if (pressUp && position == 0) showHeader();
            }
        });
    }

    public void setHeader(FragmentActivity activity, int... layoutIds) {
        if (activity != null) views = Arrays.stream(layoutIds).mapToObj(id -> (View) activity.findViewById(id)).filter(Objects::nonNull).toList();
    }

    public void setMoveTop(boolean moveTop) {
        this.moveTop = moveTop;
    }

    public void hideHeader() {
        if (views != null) for (View view : views) view.setVisibility(View.GONE);
    }

    public void showHeader() {
        if (views != null) for (View view : views) view.setVisibility(View.VISIBLE);
    }

    public boolean isHeaderVisible() {
        if (views != null) for (View view : views) if (view.getId() == R.id.recycler && view.getVisibility() == View.VISIBLE) return true;
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (!KeyUtil.isActionDown(event)) return super.dispatchKeyEvent(event);
        if (KeyUtil.isBackKey(event)) return moveTop && moveToTop();
        pressUp = KeyUtil.isUpKey(event);
        pressDown = KeyUtil.isDownKey(event);
        return super.dispatchKeyEvent(event);
    }

    public boolean moveToTop() {
        if (views == null || getSelectedPosition() == 0 || getAdapter() == null || getAdapter().getItemCount() == 0) return false;
        for (View view : views) if (view.getId() == R.id.recycler) view.requestFocus();
        scrollToPosition(0);
        showHeader();
        return true;
    }

    /**
     * β39 / beta69 问题1（A 派单：点播页上翻越行）。
     * 根因：连续下翻后上一行已被回收，系统的几何焦点搜索没有落点 ⇒ 焦点原地 ⇒ 共享探针
     * {@code NavOverlay.probeUp} 如实判 {@code focus == down} 为 edge-unchanged ⇒ 交导航，
     * 用户看到的就是"按一次上键跳到顶部导航"。
     * 修法：由**页面自己的内容网格**先把上一行请回布局，下一帧再把焦点交过去；探针与
     * NavOverlay 的判据一字未动（它服务所有页面），只是这一把 UP 在到达探针之前已在页内到位。
     * 本方法只在"焦点确实在本网格内、且不是首行"时接管；首行返回 false，保持"首行向上进导航"。
     * 挂载面：目前只有 {@code fragment_type.xml} 的内容网格用本视图（TypeFragment），不牵其他页。
     */
    public boolean carryUp() {
        View row = getFocusedChild();
        if (row == null) return false;
        RecyclerView.ViewHolder holder = findContainingViewHolder(row);
        if (holder == null) return false;
        int position = holder.getAbsoluteAdapterPosition();
        if (position <= 0) {
            Log.d("FireFlyFocus", "up carry=skip pos=" + position + " firstVis=" + firstVisiblePosition() + " -> probe");
            return false;
        }
        int target = position - 1;
        scrollToPosition(target);
        Log.d("FireFlyFocus", "up carry=take pos=" + position + " target=" + target + " firstVis=" + firstVisiblePosition() + " sel=" + getSelectedPosition());
        focusRow(target, CARRY_UP_RETRY);
        return true;
    }

    // 上一行要重新布局才拿得到 ViewHolder ⇒ 有界重试；到位后仍用系统几何搜索交焦，
    // 这样列对齐由 FocusFinder 决定（与自然上翻同一条路），失败才退回整行的 itemView。
    private void focusRow(int target, int left) {
        postDelayed(() -> {
            RecyclerView.ViewHolder next = findViewHolderForAdapterPosition(target);
            if (next == null) {
                if (left > 0) focusRow(target, left - 1);
                else Log.d("FireFlyFocus", "up carry=failed target=" + target + " reason=not-laid-out firstVis=" + firstVisiblePosition());
                return;
            }
            View origin = findFocus();
            View dest = origin == null ? null : origin.focusSearch(View.FOCUS_UP);
            boolean moved = (dest != null && dest != origin && dest.requestFocus()) || next.itemView.requestFocus();
            Log.d("FireFlyFocus", "up carry=done target=" + target + " moved=" + moved + " firstVis=" + firstVisiblePosition()
                    + " focus=" + (findFocus() == null ? "null" : findFocus().getId()));
        }, CARRY_UP_DELAY);
    }

    private int firstVisiblePosition() {
        if (!(getLayoutManager() instanceof LinearLayoutManager linear)) return -1;
        return linear.findFirstVisibleItemPosition();
    }
}
