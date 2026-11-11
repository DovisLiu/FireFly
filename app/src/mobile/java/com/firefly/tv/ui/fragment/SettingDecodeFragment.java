package com.firefly.tv.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.DolbyVisionOutputPolicy;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.R;
import com.firefly.tv.databinding.FragmentSettingDecodeBinding;
import com.firefly.tv.setting.DecodeSetting;
import com.firefly.tv.setting.PlayerSetting;
import com.firefly.tv.setting.Setting;
import com.firefly.tv.ui.activity.HomeActivity;
import com.firefly.tv.ui.base.BaseFragment;
import com.firefly.tv.ui.custom.GlassEdgeDrawable;
import com.firefly.tv.utils.ResUtil;

public class SettingDecodeFragment extends BaseFragment {

    private FragmentSettingDecodeBinding mBinding;

    public static SettingDecodeFragment newInstance() {
        return new SettingDecodeFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingDecodeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setVisible();
        refresh();
    }

    @Override
    protected void initEvent() {
        // β82 第4条＋Q1＝甲：子页是真正的三级页，箭头回设置主页（位置表里的 1），
        // 与既有分类记忆／BACK 口径同源，不动 FragmentStateManager 的位置表。
        mBinding.toolbar.setNavigationOnClickListener(v -> ((HomeActivity) requireActivity()).change(1));
        mBinding.aac.setOnClickListener(this::setAAC);
        mBinding.tunnel.setOnClickListener(this::setTunnel);
        mBinding.audioPrefer.setOnClickListener(this::setAudioPrefer);
        mBinding.videoPrefer.setOnClickListener(this::setVideoPrefer);
        mBinding.dolbyVisionOutput.setOnClickListener(this::setDolbyVisionOutput);
        mBinding.audioPassThrough.setOnClickListener(this::setAudioPassThrough);
        // β34 B1：二级页各选项行挂与一级同款方向性玻璃边。
        mBinding.aac.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.tunnel.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.audioPrefer.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.videoPrefer.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.dolbyVisionOutput.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
        mBinding.audioPassThrough.setForeground(new GlassEdgeDrawable(requireContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_setting_row));
    }

    private void setVisible() {
        boolean exo = PlayerSetting.isExo();
        mBinding.aac.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.tunnel.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.audioPrefer.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.videoPrefer.setVisibility(exo ? View.VISIBLE : View.GONE);
    }

    private void refresh() {
        mBinding.aacText.setText(Setting.getSwitch(DecodeSetting.isPreferAAC()));
        mBinding.tunnelText.setText(Setting.getSwitch(DecodeSetting.isTunnel()));
        mBinding.audioPreferText.setText(Setting.getSwitch(DecodeSetting.isAudioPrefer()));
        mBinding.videoPreferText.setText(Setting.getSwitch(DecodeSetting.isVideoPrefer()));
        mBinding.dolbyVisionOutputText.setText(ResUtil.getStringArray(R.array.select_dolby_vision_output)[DecodeSetting.getDolbyVisionOutputPolicy()]);
        mBinding.audioPassThroughText.setText(Setting.getSwitch(DecodeSetting.isAudioPassThrough()));
    }

    private void setTunnel(View view) {
        if (PlayerSetting.isMpv()) return;
        DecodeSetting.putTunnel(!DecodeSetting.isTunnel());
        mBinding.tunnelText.setText(Setting.getSwitch(DecodeSetting.isTunnel()));
    }

    private void setAudioPassThrough(View view) {
        DecodeSetting.putAudioPassThrough(!DecodeSetting.isAudioPassThrough());
        mBinding.audioPassThroughText.setText(Setting.getSwitch(DecodeSetting.isAudioPassThrough()));
    }

    private void setAudioPrefer(View view) {
        DecodeSetting.putAudioPrefer(!DecodeSetting.isAudioPrefer());
        mBinding.audioPreferText.setText(Setting.getSwitch(DecodeSetting.isAudioPrefer()));
    }

    private void setVideoPrefer(View view) {
        DecodeSetting.putVideoPrefer(!DecodeSetting.isVideoPrefer());
        mBinding.videoPreferText.setText(Setting.getSwitch(DecodeSetting.isVideoPrefer()));
    }

    private void setDolbyVisionOutput(View view) {
        int mode = (DecodeSetting.getDolbyVisionOutputPolicy() + 1) % (DolbyVisionOutputPolicy.ASSUME_UNSUPPORTED + 1);
        DecodeSetting.putDolbyVisionOutputPolicy(mode);
        mBinding.dolbyVisionOutputText.setText(ResUtil.getStringArray(R.array.select_dolby_vision_output)[mode]);
    }

    private void setAAC(View view) {
        DecodeSetting.putPreferAAC(!DecodeSetting.isPreferAAC());
        mBinding.aacText.setText(Setting.getSwitch(DecodeSetting.isPreferAAC()));
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) initView();
    }
}
