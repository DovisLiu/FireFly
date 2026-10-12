package com.firefly.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

// Firefly EXO-only: MPV 入口已禁用，避免缺少 lib-media3-mpvplayer.aar 时崩溃

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.databinding.DialogPlayerEngineBinding;
import com.firefly.tv.playback.PlaybackAction;
import com.firefly.tv.player.PlayerManager;
import com.firefly.tv.setting.PlayerSetting;
import com.firefly.tv.ui.activity.PlaybackActivity;

public final class PlayerEngineDialog extends BaseBottomSheetDialog {

    private DialogPlayerEngineBinding binding;
    private PlayerManager player;
    private TextView target;

    public static void setText(TextView view) {
        setText(view, null);
    }

    public static void setText(TextView view, PlayerManager player) {
        if (view == null) return;
        view.setText(PlaybackAction.getEngineText(player));
    }

    public static void show(FragmentActivity activity, TextView view, PlayerManager player) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) if (fragment instanceof PlayerEngineDialog) return;
        PlayerEngineDialog dialog = new PlayerEngineDialog();
        dialog.player = player;
        dialog.target = view;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    private static int getCurrentEngine(PlayerManager player) {
        return PlaybackAction.getEngine(player);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogPlayerEngineBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.mpv.setVisibility(View.GONE);
        setSelected();
        getSelectedView().requestFocus();
    }

    @Override
    protected void initEvent() {
        binding.debug.setOnClickListener(this::selectDebug);
        binding.other.setOnClickListener(this::selectOther);
        binding.exo.setOnClickListener(view -> selectEngine(PlayerSetting.ENGINE_EXO));
    }

    private void selectDebug(View view) {
        PlaybackActivity activity = getPlaybackActivity();
        if (activity == null) return;
        activity.toggleDebugView();
        dismiss();
    }

    private void selectOther(View view) {
        PlaybackActivity activity = getPlaybackActivity();
        if (activity != null) activity.onChoose();
        dismiss();
    }

    private void selectEngine(int engine) {
        if (player == null) PlayerSetting.putEngine(engine);
        else player.setEngine(engine);
        setText(target, player);
        dismiss();
    }

    private void setSelected() {
        binding.exo.setSelected(true);
    }

    private View getSelectedView() {
        return binding.exo;
    }

    private PlaybackActivity getPlaybackActivity() {
        FragmentActivity activity = getActivity();
        return activity instanceof PlaybackActivity owner ? owner : null;
    }
}
