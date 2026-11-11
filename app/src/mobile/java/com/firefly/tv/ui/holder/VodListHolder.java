package com.firefly.tv.ui.holder;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.firefly.tv.bean.Vod;
import com.firefly.tv.databinding.AdapterVodListBinding;
import com.firefly.tv.ui.adapter.VodAdapter;
import com.firefly.tv.ui.base.BaseVodHolder;
import com.firefly.tv.ui.custom.GlassEdgeDrawable;
import com.firefly.tv.utils.ImgUtil;

public class VodListHolder extends BaseVodHolder {

    private final VodAdapter.OnClickListener listener;
    private final AdapterVodListBinding binding;

    public VodListHolder(@NonNull AdapterVodListBinding binding, VodAdapter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
        // β84 玻璃批 2：列表卡海报叠同一条方向性高光边。本卡叠 3 层、顺序＝①海报图 ②**高光** ③根布局的水波——实测 adapter_vod_list.xml 的 image **没有 background**（无底板层），且画面之上没有任何兄弟节点（文字在右侧列、不是角标），所以高光落在第 2 层；圆角走 Vod.Grid.Large＝12dp，与 radius_card 同档。
        binding.image.setForeground(new GlassEdgeDrawable(binding.image.getContext()));
    }

    @Override
    public void initView(Vod item) {
        binding.name.setText(item.getName());
        binding.remark.setText(item.getRemarks());
        binding.name.setVisibility(item.getNameVisible());
        binding.remark.setVisibility(item.getRemarkVisible());
        binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        binding.getRoot().setOnLongClickListener(v -> listener.onLongClick(item));
        ImgUtil.load(item.getName(), item.getPic(), binding.image);
    }

    @Override
    public void unbind() {
        Glide.with(binding.image).clear(binding.image);
    }
}
