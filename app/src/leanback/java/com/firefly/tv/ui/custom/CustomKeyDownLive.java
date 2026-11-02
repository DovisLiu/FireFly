package com.firefly.tv.ui.custom;

import android.content.Context;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.firefly.tv.App;
import com.firefly.tv.Constant;
import com.firefly.tv.utils.KeyUtil;

public class CustomKeyDownLive extends GestureDetector.SimpleOnGestureListener {

    private final GestureDetector detector;
    private final StringBuilder text;
    private final Listener listener;
    private long holdTime;

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            listener.onFind(text.toString());
            text.setLength(0);
        }
    };

    public static CustomKeyDownLive create(Context context) {
        return new CustomKeyDownLive(context);
    }

    private CustomKeyDownLive(Context context) {
        this.text = new StringBuilder();
        this.listener = (Listener) context;
        this.detector = new GestureDetector(context, this);
    }

    public boolean onTouchEvent(MotionEvent e) {
        return detector.onTouchEvent(e);
    }

    public boolean hasEvent(KeyEvent event) {
        return KeyUtil.isEnterKey(event) || KeyUtil.isUpKey(event) || KeyUtil.isDownKey(event) || KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event) || KeyUtil.isDigitKey(event) || KeyUtil.isMenuKey(event) || event.isLongPress();
    }

    public void onKeyDown(KeyEvent event) {
        // T-168/T-171 诊断日志：记录自定义按键层是否放行到 check（pass=false 时方向键交给面板）
        boolean pass = listener.dispatch(true);
        Log.d("FireFlyLive", "customKey k=" + event.getKeyCode() + " a=" + event.getAction() + " pass=" + pass);
        if (pass) check(event);
    }

    private void check(KeyEvent event) {
        // β43 / A3（刘先生拍板"直播页左右键统一换线路、不要快进快退"）：原先这两条
        // `DOWN + LEFT/RIGHT → listener.onSeeking(±time)`（快进/快退预览）已删除——按下不再产生任何快进语义，
        // 抬手由下面两条 onKeyLeft/onKeyRight 统一去切线路。接口方法 onSeeking 保留（Cast/Video 各自实现），
        // LiveActivity 那一版已改成空实现，双保险。
        if (KeyUtil.isActionDown(event) && KeyUtil.isUpKey(event)) {
            listener.onKeyUp();
        } else if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event)) {
            listener.onKeyDown();
        } else if (KeyUtil.isActionUp(event) && KeyUtil.isLeftKey(event)) {
            listener.onKeyLeft(holdTime);
        } else if (KeyUtil.isActionUp(event) && KeyUtil.isRightKey(event)) {
            listener.onKeyRight(holdTime);
        } else if (KeyUtil.isActionUp(event) && KeyUtil.isDigitKey(event)) {
            onKeyDown(event.getKeyCode());
        } else if (KeyUtil.isActionUp(event) && KeyUtil.isEnterKey(event)) {
            listener.onKeyCenter();
        } else if (KeyUtil.isMenuKey(event) || event.isLongPress() && KeyUtil.isEnterKey(event)) {
            listener.onMenu();
        }
    }

    private void onKeyDown(int keyCode) {
        if (text.length() >= 4) return;
        text.append(getNumber(keyCode));
        listener.onShow(text.toString());
        App.post(runnable, 2000);
    }

    @Override
    public boolean onDoubleTap(@NonNull MotionEvent e) {
        if (listener.dispatch(false)) listener.onDoubleTap();
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
        if (listener.dispatch(false)) listener.onSingleTap();
        return true;
    }

    private int getNumber(int keyCode) {
        return keyCode >= 144 ? keyCode - 144 : keyCode - 7;
    }

    private long addTime() {
        return holdTime = holdTime + Constant.INTERVAL_SEEK;
    }

    private long subTime() {
        return holdTime = holdTime - Constant.INTERVAL_SEEK;
    }

    public void reset() {
        holdTime = 0;
    }

    public interface Listener {

        boolean dispatch(boolean check);

        void onShow(String number);

        void onFind(String number);

        void onSeeking(long time);

        void onKeyUp();

        void onKeyDown();

        void onKeyLeft(long time);

        void onKeyRight(long time);

        void onKeyCenter();

        void onMenu();

        void onSingleTap();

        void onDoubleTap();
    }
}
