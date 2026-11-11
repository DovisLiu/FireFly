package com.firefly.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.firefly.tv.bean.Class;
import com.firefly.tv.bean.Result;
import com.firefly.tv.databinding.AdapterTypeBinding;

import java.util.ArrayList;
import java.util.List;

public class TypeAdapter extends RecyclerView.Adapter<TypeAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Class> mItems;

    public TypeAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(int position, Class item);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void addAll(Result result) {
        // β82 第1条（A 裁 Q6＝随导航批）：不再往分类条首位塞「推荐」伪项——它与手机首页的站点推荐行重合。
        // 伪项构造方法本体、它取的那条文案与随之失效的两个 import 一并删净；该文案在三档语言文件里
        // 变零引用（shrinkResources 会从包里物理删名），源文件留着不删＝保留回滚点。
        mItems.addAll(result.getTypes());
        if (!mItems.isEmpty()) mItems.get(0).setSelected(true);
        notifyDataSetChanged();
    }

    public void setSelected(int position) {
        for (Class item : mItems) item.setSelected(false);
        mItems.get(position).setSelected(true);
        notifyItemRangeChanged(0, mItems.size());
    }

    public Class get(int position) {
        return mItems.get(position);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTypeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Class item = mItems.get(position);
        holder.binding.text.setText(item.getTypeName());
        holder.binding.text.setSelected(item.isSelected());
        // 选中项放大加粗显示，无底色（参考大厂视频首页分类栏）
        holder.binding.text.getPaint().setFakeBoldText(item.isSelected());
        holder.binding.text.setTextSize(item.isSelected() ? 17f : 15f);
        holder.binding.text.setOnClickListener(v -> listener.onItemClick(position, item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTypeBinding binding;

        ViewHolder(@NonNull AdapterTypeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}