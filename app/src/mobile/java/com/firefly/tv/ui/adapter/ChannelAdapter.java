package com.firefly.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.firefly.tv.bean.Channel;
import com.firefly.tv.databinding.AdapterChannelBinding;

import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Channel> mItems;

    public ChannelAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(Channel item);

        boolean onLongClick(Channel item);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void addAll(List<Channel> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void remove(Channel item) {
        int position = mItems.indexOf(item);
        if (position == -1) return;
        mItems.remove(position);
        notifyItemRemoved(position);
    }

    public void setSelected(int position) {
        if (position == -1) return;
        for (int i = 0; i < mItems.size(); i++) mItems.get(i).setSelected(i == position);
        notifyItemRangeChanged(0, getItemCount());
    }

    public int setSelected(Channel channel) {
        int position = mItems.indexOf(channel);
        setSelected(position);
        return position;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ChannelAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterChannelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelAdapter.ViewHolder holder, int position) {
        Channel item = mItems.get(position);
        item.loadLogo(holder.binding.logo);
        holder.binding.name.setText(item.getShow());
        holder.binding.number.setText(item.getNumber());
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
        // β67 甲案（A 令，与 TV beta78 的 `b78s-01` 同形）：**「长按频道卡＝收藏」的绑定摘除**——
        // 本行是手机侧直播收藏唯一的写入口（→ `LiveActivity.onLongClick` → `addKeep` 往 Keep 表写 type=1 行）。
        // 屏蔽版必须「不显示也不写入」，故摘掉这一行；`onLongClick`／`addKeep`／`delKeep` 三个本体一字未动，
        // 它们是本改动的回滚点（恢复这一行即回到长按收藏）。长按现在的行为＝与点击同效（无监听 ⇒ 事件不再被消费）。
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        Glide.with(holder.binding.logo).clear(holder.binding.logo);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterChannelBinding binding;

        ViewHolder(@NonNull AdapterChannelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}