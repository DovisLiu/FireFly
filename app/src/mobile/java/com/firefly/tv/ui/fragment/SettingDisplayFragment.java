package com.firefly.tv.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.R;
import com.firefly.tv.databinding.FragmentSettingDisplayBinding;
import com.firefly.tv.setting.PlayerSetting;
import com.firefly.tv.setting.Setting;
import com.firefly.tv.ui.activity.HomeActivity;
import com.firefly.tv.ui.base.BaseFragment;
import com.firefly.tv.ui.custom.GlassEdgeDrawable;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingDisplayFragment extends BaseFragment {

    private FragmentSettingDisplayBinding mBinding;
    private String[] size;

    public static SettingDisplayFragment newInstance() {
        return new SettingDisplayFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingDisplayBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mBinding.sizeText.setText((size = ResUtil.getStringArray(R.array.select_size))[PlayerSetting.getSize()]);
        mBinding.appearanceText.setText(ResUtil.getStringArray(R.array.select_appearance)[Setting.getThemeMode()]);
    }

    @Override
    protected void initEvent() {
        // β82 第4条＋Q1＝甲：子页是真正的三级页，箭头回设置主页（位置表里的 1），
        // 与既有分类记忆／BACK 口径同源，不动 FragmentStateManager 的位置表。
        mBinding.toolbar.setNavigationOnClickListener(v -> ((HomeActivity) requireActivity()).change(1));
        mBinding.size.setOnClickListener(this::setSize);
        mBinding.appearance.setOnClickListener(this::onAppearance);
        // β34 B1：二级页各选项行挂与一级同款方向性玻璃边。
        mBinding.size.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.appearance.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
    }

    private void setSize(View view) {
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_size).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(size, PlayerSetting.getSize(), (dialog, which) -> {
            mBinding.sizeText.setText(size[which]);
            PlayerSetting.putSize(which);
            RefreshEvent.size();
            dialog.dismiss();
        }).show();
    }

    private void onAppearance(View view) {
        String[] items = ResUtil.getStringArray(R.array.select_appearance);
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_appearance).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(items, Setting.getThemeMode(), (dialog, which) -> {
            mBinding.appearanceText.setText(items[which]);
            Setting.putThemeMode(which);
            AppCompatDelegate.setDefaultNightMode(which == Setting.MODE_SYSTEM ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM : which == Setting.MODE_LIGHT ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
            dialog.dismiss();
        }).show();
    }
}
