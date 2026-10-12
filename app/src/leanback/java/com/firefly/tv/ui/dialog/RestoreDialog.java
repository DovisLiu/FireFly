package com.firefly.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.R;
import com.firefly.tv.databinding.DialogRestoreBinding;
import com.firefly.tv.db.AppDatabase;
import com.firefly.tv.impl.Callback;
import com.firefly.tv.ui.adapter.RestoreAdapter;
import com.firefly.tv.ui.custom.SpaceItemDecoration;
import com.firefly.tv.utils.FileChooser;
import com.firefly.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

public class RestoreDialog extends BaseAlertDialog implements RestoreAdapter.OnClickListener {

    private DialogRestoreBinding binding;
    private RestoreAdapter adapter;
    private Callback callback;

    // 导入：系统文件选择器任选位置（网盘/Download/其他设备传来的 .bk.gz），不限于 /sdcard/TV/
    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) {
            Notify.show(R.string.backup_import_fail);
            return;
        }
        restore(new File(path));
    });

    public static RestoreDialog create() {
        return new RestoreDialog();
    }

    public RestoreDialog callback(Callback callback) {
        this.callback = callback;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogRestoreBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new RestoreAdapter(this);
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
    }

    @Override
    protected void initEvent() {
        binding.importButton.setOnClickListener(v -> FileChooser.from(launcher).show());
    }

    @Override
    public void onItemClick(File item) {
        restore(item);
    }

    private void restore(File file) {
        AppDatabase.restore(file, callback);
        dismiss();
    }

    @Override
    public void onDeleteClick(File item) {
        if (adapter.remove(item) == 0) dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        // 列表为空不再自动关闭：保留导入入口
        setWidth(0.4f);
    }
}
