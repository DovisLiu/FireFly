package com.firefly.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.firefly.tv.bean.Channel;
import com.firefly.tv.databinding.AdapterChannelSelectBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 直播选台页频道大卡适配器（spec v1.10 拍板 44，刘先生 05_live-reference 效果图 2026-10-26）：
 *  - 复用 Channel 数据/台标加载与 show/name 展示口径；
 *  - 无台标时卡片中央用频道名文字兜底（拍板 44 视觉要求）；
 *  - 本轮按 A 裁决不做视频预览，焦点=高亮，OK=进直播播放页（拍板 44 裁决①）。
 */
public class SelectChannelAdapter extends RecyclerView.Adapter<SelectChannelAdapter.ViewHolder> {

    public interface OnClickListener {
        void onItemClick(Channel item);
    }

    private final OnClickListener listener;
    private final List<Channel> items = new ArrayList<>();

    public SelectChannelAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public void addAll(List<Channel> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    public Channel get(int position) {
        return items.get(position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterChannelSelectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel item = items.get(position);
        boolean hasLogo = !TextUtils.isEmpty(item.getLogo());
        holder.binding.number.setText(item.getNumber());
        holder.binding.name.setText(item.getShow());
        holder.binding.placeholder.setText(item.getShow());
        holder.binding.placeholder.setVisibility(hasLogo ? View.GONE : View.VISIBLE);
        holder.binding.logo.setVisibility(hasLogo ? View.VISIBLE : View.GONE);
        if (hasLogo) item.loadLogo(holder.binding.logo);
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        Glide.with(holder.binding.logo).clear(holder.binding.logo);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterChannelSelectBinding binding;

        ViewHolder(@NonNull AdapterChannelSelectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
