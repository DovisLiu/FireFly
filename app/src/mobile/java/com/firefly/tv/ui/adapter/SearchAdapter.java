package com.firefly.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.firefly.tv.bean.Vod;
import com.firefly.tv.databinding.AdapterSearchBinding;
import com.firefly.tv.ui.custom.GlassEdgeDrawable;
import com.firefly.tv.utils.ImgUtil;

public class SearchAdapter extends BaseDiffAdapter<Vod, SearchAdapter.ViewHolder> {

    private final OnClickListener listener;

    public SearchAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(Vod item);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSearchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Vod item = getItem(position);
        holder.binding.name.setText(item.getName());
        // β87 第3条（A 裁＝落实甲1"手机一页一档"，搜索卡是当时漏掉的那一档）：
        // 左上位改承载集数档文本（remarks），源名不再占左上位；可见性跟着 remarks 走。写法照 KeepAdapter／HistoryAdapter 既有先例。
        holder.binding.site.setText(item.getRemarks());
        holder.binding.site.setVisibility(item.getRemarkVisible());
        // 连带（单列＋给回退）：remarks 已上角标，正文那行若继续灌同一串＝同卡重复显示，故置 GONE。
        // 回退＝把本行换回原两行 setText(item.getRemarks()) ＋ setVisibility(item.getRemarkVisible())。
        holder.binding.remark.setVisibility(View.GONE);
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        ImgUtil.load(item.getName(), item.getPic(), holder.binding.image);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        Glide.with(holder.binding.image).clear(holder.binding.image);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSearchBinding binding;

        ViewHolder(@NonNull AdapterSearchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            // β84 玻璃批 2：搜索结果卡海报叠方向性高光边。本卡叠 5 层、顺序＝①底板 black_10 ②海报图 ③**高光**（挂 image 的 foreground，不新增布局节点）④左上角标（源名，Round 38/R3 已收进统一规格）⑤根布局的水波。
            binding.image.setForeground(new GlassEdgeDrawable(binding.image.getContext()));
        }
    }
}
