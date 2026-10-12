package com.firefly.tv.ui.base;

import android.view.View;

import androidx.leanback.widget.Presenter;

import com.firefly.tv.bean.Vod;

public abstract class BaseVodHolder extends Presenter.ViewHolder {

    public BaseVodHolder(View view) {
        super(view);
        view.setOnFocusChangeListener((v, hasFocus) -> v.animate().scaleX(hasFocus ? 1.08f : 1f).scaleY(hasFocus ? 1.08f : 1f).setDuration(150).start());
    }

    public abstract void initView(Vod item);

    public abstract void unbind();
}
