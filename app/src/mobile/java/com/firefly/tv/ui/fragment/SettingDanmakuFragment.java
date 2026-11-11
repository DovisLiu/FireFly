package com.firefly.tv.ui.fragment;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.R;
import com.firefly.tv.databinding.FragmentSettingDanmakuBinding;
import com.firefly.tv.impl.DanmakuListener;
import com.firefly.tv.setting.DanmakuSetting;
import com.firefly.tv.setting.Setting;
import com.firefly.tv.ui.activity.HomeActivity;
import com.firefly.tv.ui.base.BaseFragment;
import com.firefly.tv.ui.custom.GlassEdgeDrawable;
import com.firefly.tv.ui.dialog.DanmakuApiDialog;

public class SettingDanmakuFragment extends BaseFragment implements DanmakuListener {

    private FragmentSettingDanmakuBinding mBinding;

    public static SettingDanmakuFragment newInstance() {
        return new SettingDanmakuFragment();
    }

    private String getApiStatus() {
        return getString(TextUtils.isEmpty(DanmakuSetting.getEffectiveApiUrl()) ? R.string.none : R.string.yes);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingDanmakuBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mBinding.danmakuApiText.setText(getApiStatus());
        mBinding.danmakuAutoText.setText(Setting.getSwitch(DanmakuSetting.isAuto()));
        mBinding.danmakuLoadText.setText(Setting.getSwitch(DanmakuSetting.isLoad()));
        mBinding.danmakuSpiderText.setText(Setting.getSwitch(DanmakuSetting.isSpiderFirst()));
        updateApiVisibility();
    }

    @Override
    protected void initEvent() {
        // β82 第4条＋Q1＝甲：子页是真正的三级页，箭头回设置主页（位置表里的 1），
        // 与既有分类记忆／BACK 口径同源，不动 FragmentStateManager 的位置表。
        mBinding.toolbar.setNavigationOnClickListener(v -> ((HomeActivity) requireActivity()).change(1));
        mBinding.danmakuApi.setOnClickListener(this::onDanmakuApi);
        mBinding.danmakuAuto.setOnClickListener(this::setDanmakuAuto);
        mBinding.danmakuLoad.setOnClickListener(this::setDanmakuLoad);
        mBinding.danmakuSpider.setOnClickListener(this::setDanmakuSpider);
        // β34 B1：二级页各选项行挂与一级同款方向性玻璃边。
        mBinding.danmakuApi.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.danmakuAuto.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.danmakuLoad.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.danmakuSpider.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
    }

    private void setDanmakuLoad(View view) {
        DanmakuSetting.putLoad(!DanmakuSetting.isLoad());
        mBinding.danmakuLoadText.setText(Setting.getSwitch(DanmakuSetting.isLoad()));
        updateApiVisibility();
    }

    private void updateApiVisibility() {
        boolean load = DanmakuSetting.isLoad();
        mBinding.danmakuApi.setVisibility(load ? View.VISIBLE : View.GONE);
        updateAutoVisibility();
    }

    private void updateAutoVisibility() {
        boolean show = DanmakuSetting.isLoad() && !TextUtils.isEmpty(DanmakuSetting.getEffectiveApiUrl());
        mBinding.danmakuAuto.setVisibility(show ? View.VISIBLE : View.GONE);
        updateSpiderVisibility();
    }

    private void updateSpiderVisibility() {
        boolean show = DanmakuSetting.isLoad() && !TextUtils.isEmpty(DanmakuSetting.getEffectiveApiUrl()) && DanmakuSetting.isAuto();
        mBinding.danmakuSpider.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void onDanmakuApi(View view) {
        DanmakuApiDialog.show(this);
    }

    @Override
    public void setDanmakuApi(String url) {
        DanmakuSetting.putApiUrl(url);
        mBinding.danmakuApiText.setText(getApiStatus());
        updateAutoVisibility();
    }

    private void setDanmakuAuto(View view) {
        DanmakuSetting.putAuto(!DanmakuSetting.isAuto());
        mBinding.danmakuAutoText.setText(Setting.getSwitch(DanmakuSetting.isAuto()));
        updateSpiderVisibility();
    }

    private void setDanmakuSpider(View view) {
        DanmakuSetting.putSpiderFirst(!DanmakuSetting.isSpiderFirst());
        mBinding.danmakuSpiderText.setText(Setting.getSwitch(DanmakuSetting.isSpiderFirst()));
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) initView();
    }
}
