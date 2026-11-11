package com.firefly.tv.ui.holder;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.firefly.tv.bean.Vod;
import com.firefly.tv.databinding.AdapterVodRectBinding;
import com.firefly.tv.ui.base.BaseVodHolder;
import com.firefly.tv.ui.presenter.VodPresenter;
import com.firefly.tv.utils.ImgUtil;

public class VodRectHolder extends BaseVodHolder {

    private final VodPresenter.OnClickListener listener;
    private final AdapterVodRectBinding binding;

    public VodRectHolder(@NonNull AdapterVodRectBinding binding, VodPresenter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
    }

    public VodRectHolder size(int[] size) {
        binding.image.getLayoutParams().height = size[1];
        binding.getRoot().getLayoutParams().width = size[0];
        return this;
    }

    @Override
    public void initView(Vod item) {
        binding.name.setText(item.getName());
        binding.year.setText(item.getYear());
        binding.remark.setText(item.getRemarks());
        // TV 2.0：源名角标隐藏（beta1 规格只保留 右上集数／更新进度（节点＝@+id/remark（binding.remark），取 getRemarks()）/左上年份）
        binding.site.setVisibility(android.view.View.GONE);
        binding.year.setVisibility(item.getYearVisible());
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
