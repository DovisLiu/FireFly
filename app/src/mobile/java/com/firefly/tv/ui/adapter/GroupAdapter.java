package com.firefly.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.firefly.tv.bean.Group;
import com.firefly.tv.databinding.AdapterGroupBinding;

import java.util.ArrayList;
import java.util.List;

public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Group> mItems;

    public GroupAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void setWidth(Group item);

        void onItemClick(Group item);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void addAll(List<Group> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void add(Group item) {
        mItems.add(item);
        notifyItemInserted(getItemCount() - 1);
    }

    public Group get(int position) {
        return mItems.get(position);
    }

    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        // β68 乙案（A 令 2）：无选中项时「优第一个非空」，全空才落 0——口径同 TV β74 的 !isEmpty() 过滤。
        for (int i = 0; i < mItems.size(); i++) if (!mItems.get(i).isEmpty()) return i;
        return 0;
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
        listener.setWidth(mItems.get(position));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterGroupBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
        // β68 乙案（A 令 1）：空分组置灰不可点。判据用现成 `Group.isEmpty()`（＝getChannel().isEmpty()），与渲染处
        // renderGroupChannels 的 addAll(group.getChannel()) 同源⇒置灰项点进去必然空，不会误灰非空项。
        // 复用 holder 必须显式清空监听：只 setClickable(false) 不绑新监听，会残留上一个组的点击回调。
        if (usable) holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
        else holder.binding.getRoot().setOnClickListener(null);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterGroupBinding binding;

        ViewHolder(@NonNull AdapterGroupBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
