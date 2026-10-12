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
import com.firefly.tv.ui.base.BaseFragment;
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
        mBinding.size.setOnClickListener(this::setSize);
        mBinding.appearance.setOnClickListener(this::onAppearance);
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
