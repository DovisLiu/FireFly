package com.firefly.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.App;
import com.firefly.tv.R;
import com.firefly.tv.databinding.ActivitySettingDisplayBinding;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.setting.PlayerSetting;
import com.firefly.tv.setting.Setting;
import com.firefly.tv.ui.base.BaseActivity;
import com.firefly.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingDisplayActivity extends BaseActivity {

    private ActivitySettingDisplayBinding mBinding;
    private String[] size;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingDisplayActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingDisplayBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.size.requestFocus();
        mBinding.sizeText.setText((size = ResUtil.getStringArray(R.array.select_size))[PlayerSetting.getSize()]);
        mBinding.appearanceText.setText(ResUtil.getStringArray(R.array.select_appearance)[Setting.getThemeMode()]);
    }

    @Override
    protected void initEvent() {
        mBinding.size.setOnClickListener(this::setSize);
        mBinding.appearance.setOnClickListener(this::onAppearance);
    }

    private void setSize(View view) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setting_size)
                .setSingleChoiceItems(size, PlayerSetting.getSize(), (dialog, which) -> {
                    PlayerSetting.putSize(which);
                    mBinding.sizeText.setText(size[which]);
                    RefreshEvent.size();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void onAppearance(View view) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setting_appearance)
                .setSingleChoiceItems(R.array.select_appearance, Setting.getThemeMode(), (dialog, which) -> {
                    Setting.putThemeMode(which);
                    AppCompatDelegate.setDefaultNightMode(which == Setting.MODE_SYSTEM ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM : which == Setting.MODE_LIGHT ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
                    mBinding.appearanceText.setText(ResUtil.getStringArray(R.array.select_appearance)[Setting.getThemeMode()]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }
}
