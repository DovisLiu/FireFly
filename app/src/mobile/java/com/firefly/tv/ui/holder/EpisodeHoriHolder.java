package com.firefly.tv.ui.holder;

import androidx.annotation.NonNull;

import com.firefly.tv.bean.Episode;
import com.firefly.tv.databinding.AdapterEpisodeHoriBinding;
import com.firefly.tv.ui.adapter.EpisodeAdapter;
import com.firefly.tv.ui.base.BaseEpisodeHolder;
import com.firefly.tv.utils.EpisodeNameUtil;
import com.firefly.tv.utils.ResUtil;

public class EpisodeHoriHolder extends BaseEpisodeHolder {

    private final EpisodeAdapter.OnClickListener listener;
    private final AdapterEpisodeHoriBinding binding;
    private final int maxWidth;

    public EpisodeHoriHolder(@NonNull AdapterEpisodeHoriBinding binding, EpisodeAdapter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
        this.maxWidth = ResUtil.getScreenWidth() - ResUtil.dp2px(32);
    }

    @Override
    public void initView(Episode item) {
        binding.text.setMaxWidth(maxWidth);
        binding.text.setSelected(item.isSelected());
        // β31 A4：剥 [大小GB/MB] 前缀与扩展名后缀，保留名主体；剥完为空回落原名（绝不显示空卡）
        String label = EpisodeNameUtil.stripSizeAndExt(item.getDesc().concat(item.getName()));
        binding.text.setText(label.isEmpty() ? item.getName() : label);
        binding.text.setOnClickListener(v -> listener.onItemClick(item));
    }
}
