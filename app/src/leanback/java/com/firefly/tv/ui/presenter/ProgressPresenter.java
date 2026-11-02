package com.firefly.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.firefly.tv.databinding.AdapterProgressBinding;

public class ProgressPresenter extends Presenter {

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        ViewHolder holder = new ViewHolder(AdapterProgressBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        // β49 / 裁决2（A 定的第三条路）：冷启时"加载中"占位是内容区唯一一行，旧版它不可聚焦 ⇒ 首页导航上按第一下
        // DOWN 交不出落点、放行给系统后导航条又没有向下候选（nextFocus 连线实测为 0），表现＝"第一下像被吃掉"。
        // 现在把占位行本身设为可聚焦目标，第一下就有真实落点（焦点进内容区）；数据到位那一拍再同帧把焦点交给首行
        // （见 HomeActivity 的 placeholder transfer），**不补投按键、不做延时重试**。
        holder.view.setFocusable(true);
        holder.view.setEnabled(true);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        public ViewHolder(@NonNull AdapterProgressBinding binding) {
            super(binding.getRoot());
        }
    }
}