package com.firefly.tv.ui.dialog;

import android.view.View;

import android.content.DialogInterface;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.databinding.DialogUpdateBinding;
import com.firefly.tv.impl.UpdateListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class UpdateDialog extends BaseAlertDialog {

    private DialogUpdateBinding binding;
    private UpdateListener listener;
    private String title;
    private String desc;

    public static UpdateDialog create() {
        return new UpdateDialog();
    }

    public UpdateDialog title(String title) {
        this.title = title;
        return this;
    }

    public UpdateDialog desc(String desc) {
        this.desc = desc;
        return this;
    }

    public UpdateDialog listener(UpdateListener listener) {
        this.listener = listener;
        return this;
    }

    private java.lang.ref.WeakReference<FragmentActivity> showActivity;

    public UpdateDialog show(FragmentActivity activity) {
        // 下载过程必须点取消才能停止：弹窗层与对话框层双重禁止点外部/返回键关闭
        setCancelable(false);
        show(activity.getSupportFragmentManager(), null);
        return this;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogUpdateBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot()).setCancelable(false);
    }

    @Override
    public void onStart() {
        super.onStart();
        // 下载更新期间保持屏幕常亮，防止锁屏中断下载
        if (getDialog() != null && getDialog().getWindow() != null)
            getDialog().getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // TV 焦点：弹窗窗口获得焦点前 requestFocus 会被重置，延迟到首帧绘制后执行
        getDialog().getWindow().getDecorView().post(() -> binding.confirm.requestFocus());
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        FragmentActivity activity = showActivity != null ? showActivity.get() : null;
        if (activity != null && !activity.isFinishing()) activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDismiss(dialog);
    }

    @Override
    protected void initView() {
        binding.version.setText(title);
        binding.desc.setText(desc);
    }

    @Override
    protected void initEvent() {
        binding.confirm.setOnClickListener(this::onConfirm);
        binding.cancel.setOnClickListener(this::onCancel);
    }

    public void setProgress(int progress) {
        binding.confirm.setText(String.format(Locale.getDefault(), "%1$d%%", progress));
    }

    private void onConfirm(View view) {
        listener.onConfirm(view);
    }

    private void onCancel(View view) {
        listener.onCancel(view);
    }
}
