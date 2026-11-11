package com.firefly.tv.ui.holder;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.firefly.tv.bean.Vod;
import com.firefly.tv.databinding.AdapterVodRectBinding;
import com.firefly.tv.ui.adapter.VodAdapter;
import com.firefly.tv.ui.base.BaseVodHolder;
import com.firefly.tv.ui.custom.GlassEdgeDrawable;
import com.firefly.tv.utils.ImgUtil;

public class VodRectHolder extends BaseVodHolder {

    private final VodAdapter.OnClickListener listener;
    private final AdapterVodRectBinding binding;

    public VodRectHolder(@NonNull AdapterVodRectBinding binding, VodAdapter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
        // β84 玻璃批 2：海报叠方向性高光边。本卡叠 5 层、顺序＝①底板 black_20 ②海报图 ③**高光**（挂 image 的 foreground，所以永远在画面之上）④左上/右上/左下三枚角标 ⑤根布局的水波（shape_vod 的 ripple，最后绘制、盖在全部之上）。
        // 一个 holder 一个实例（bounds 各自独立，共用一份会互相改坏），故不在 bind 里 new。
        binding.image.setForeground(new GlassEdgeDrawable(binding.image.getContext()));
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
        binding.site.setText(item.getSiteName());
        binding.remark.setText(item.getRemarks());
        // TV 2.0 规范同步（§3.9）：右上=集数／更新进度（节点＝@+id/remark（binding.remark），取 getRemarks()；本应用无评分数据源，见 main/Migrations.java:58），左上=年份；来源标签不显示（拍板 8）
        binding.site.setVisibility(android.view.View.GONE);
        binding.name.setVisibility(item.getNameVisible());
        binding.year.setVisibility(item.getYearVisible());
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
