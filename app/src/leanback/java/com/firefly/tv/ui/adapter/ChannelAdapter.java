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

    private final OnClickListener mListener;
    private final List<Channel> mItems;

    public ChannelAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
    }

    public void addAll(List<Channel> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void remove(Channel item) {
        int index = mItems.indexOf(item);
        if (index < 0) return;
        mItems.remove(index);
        notifyItemRemoved(index);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public Channel get(int position) {
        return mItems.get(position);
    }

    public void setSelected(Channel selected) {
        for (Channel item : mItems) item.setSelected(selected);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterChannelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel item = mItems.get(position);
        item.loadLogo(holder.binding.logo);
        holder.binding.name.setText(item.getShow());
        holder.binding.number.setText(item.getNumber());
        holder.binding.getRoot().setSelected(item.isSelected());
        // T-175（C beta17：面板网格内 RIGHT 焦点不移动——根因=卡片 CustomLeftRightLayout 的 RightListener
        // 拦截 RIGHT 打开 EPG 并 return true → 焦点不动）：去掉 RightListener，RIGHT 交还焦点系统在网格内移动；
        // EPG 查看入口由其他方式保留（onItemClick 分支未动）
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
        // β51 屏蔽版（A 指令 4b）：**「长按频道卡＝收藏」的绑定摘除**——beta76 里直播收藏唯一的写入口就是这一行
        // （→ `LiveActivity.onLongClick:863` → `addKeep:877` 往 Keep 表写 type=1 行）。TV-02 整体退回 2.1，
        // 屏蔽版必须"不显示也不写入"，所以这一行必须摘；`onLongClick`/`addKeep`/`delKeep` 三个本体一字未动，
        // 它们是本屏蔽的回滚点（恢复这一行即回到 beta76 的长按收藏），代价＝这三个方法在本包内不可达。
        // 长按现在的行为＝与点击同效（无长按监听 ⇒ 事件不再被消费）；如 A 认为长按该有别的落点，请单独给令。
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        Glide.with(holder.binding.logo).clear(holder.binding.logo);
    }

    public interface OnClickListener {

        void showEpg(Channel item);

        void onItemClick(Channel item);

        boolean onLongClick(Channel item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterChannelBinding binding;

        ViewHolder(@NonNull AdapterChannelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
