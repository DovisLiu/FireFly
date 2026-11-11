package com.firefly.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.firefly.tv.bean.Group;
import com.firefly.tv.databinding.AdapterGroupChipBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * β77 选台页专用分组 chip 适配器（A 批准：不改播放页浮层同用的 GroupAdapter，防把已验收的浮层一起改掉）。
 * β68 乙案三件逐条迁入：①bind 内空组 enabled/clickable/focusable=false ＋ alpha 0.3（置灰）；
 * ②复用 holder 必显式清点击监听（只 setClickable(false) 不绑新监听会残留上一个组的回调）；
 * ③选中态单一来源 setSelected ⇒ root.setSelected。与 GroupAdapter 的差异只有两处：
 * 绑定 adapter_group_chip（根 wrap_content，横滚一屏可见多个）＋不设 setWidth 回调（chip 不按内容定列宽）。
 */
public class GroupChipAdapter extends RecyclerView.Adapter<GroupChipAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Group> mItems;

    public GroupChipAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {
        void onItemClick(Group item);
    }

    public void addAll(List<Group> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public Group get(int position) {
        return mItems.get(position);
    }

    public int indexOf(Group group) {
        return mItems.indexOf(group);
    }

    public void setSelected(Group group) {
        setSelected(indexOf(group));
    }

    public void setSelected(int position) {
        for (int i = 0; i < mItems.size(); i++) mItems.get(i).setSelected(i == position);
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterGroupChipBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Group item = mItems.get(position);
        boolean usable = !item.isEmpty();
        holder.binding.name.setText(item.getName());
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setEnabled(usable);
        holder.binding.getRoot().setClickable(usable);
        holder.binding.getRoot().setFocusable(usable);
        holder.binding.name.setAlpha(usable ? 1f : 0.3f);
        if (usable) holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
        else holder.binding.getRoot().setOnClickListener(null);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterGroupChipBinding binding;

        ViewHolder(AdapterGroupChipBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
