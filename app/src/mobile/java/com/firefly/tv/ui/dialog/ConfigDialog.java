package com.firefly.tv.ui.dialog;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.R;
import com.firefly.tv.api.config.LiveConfig;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Config;
import com.firefly.tv.databinding.DialogConfigBinding;
import com.firefly.tv.impl.ConfigListener;
import com.firefly.tv.ui.custom.CustomTextListener;
import com.firefly.tv.utils.FileChooser;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ConfigDialog extends BaseAlertDialog {

    private DialogConfigBinding binding;
    private boolean append = true;
    private boolean edit;
    private String ori;
    private int type;

    public static ConfigDialog create() {
        return new ConfigDialog();
    }

    public ConfigDialog vod() {
        type = 0;
        return this;
    }

    public ConfigDialog live() {
        type = 1;
        return this;
    }

    public ConfigDialog edit() {
        edit = true;
        return this;
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogConfigBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(type == 0 ? R.string.setting_vod : R.string.setting_live).setView(getBinding().getRoot()).setPositiveButton(edit ? R.string.dialog_edit : R.string.dialog_positive, this::onPositive).setNegativeButton(R.string.dialog_negative, null);
    }

    @Override
    protected void initView() {
        binding.name.setText(getConfig().getName());
        binding.url.setText(ori = getConfig().getUrl());
        binding.input.setVisibility(View.VISIBLE);
        binding.url.setSelection(TextUtils.isEmpty(ori) ? 0 : ori.length());
    }

    @Override
    protected void initEvent() {
        binding.choose.setEndIconOnClickListener(this::onChoose);
        binding.url.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
        binding.url.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive(null, 0);
            return true;
        });
    }

    private Config getConfig() {
        return switch (type) {
            case 0 -> VodConfig.get().getConfig();
            case 1 -> LiveConfig.get().getConfig();
            default -> null;
        };
    }

    /**
     * P0 现场一同型强转（A 2026-11-04 裁＝与 HistoryDialog 一并入清单，不另开一批）。
     * 原两处都写 ((ConfigListener) requireParentFragment())＝无条件强转；本页四个调用点宿主是 SettingFragment
     * （它 implements ConfigListener ⇒ 现在仍走通），但强转本身是同一颗雷，按同口径拆成 instanceof 兜底＋留痕。
     */
    private ConfigListener target() {
        Fragment parent = getParentFragment();
        ConfigListener listener = parent instanceof ConfigListener ? (ConfigListener) parent : null;
        android.util.Log.d("FireFlySetting", "config pick host=" + (listener != null ? "resolved" : "none") + " action=setConfig");
        return listener;
    }

    private void onChoose(View view) {
        FileChooser.from(launcher).show();
    }

    private void detect(String s) {
        if (append && "h".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ttp://");
        } else if (append && "f".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ile://");
        } else if (append && "a".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ssets://");
        } else if (s.length() > 1) {
            append = false;
        } else if (s.isEmpty()) {
            append = true;
        }
    }

    private void onPositive(DialogInterface dialog, int which) {
        String url = binding.url.getText().toString().trim();
        String name = binding.name.getText().toString().trim();
        // 彩蛋：地址输入 FireFly 一键填入官方订阅地址
        if ("dovisliu".equalsIgnoreCase(url)) url = "https://download.dovisliu.cn/firefly/firefly.json";
        if (edit) Config.find(ori, type).url(url).name(name).update();
        if (url.isEmpty()) Config.delete(ori, type);
        ConfigListener target = target();
        if (target != null) target.setConfig(name.isEmpty() ? Config.find(url, type) : Config.find(url, name, type));
        dismiss();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        ConfigListener target = target();
        if (target != null) target.setConfig(Config.find("file:/" + FileChooser.getPathFromUri(result.getData().getData()).replace(Path.rootPath(), ""), type));
        dismiss();
    });
}