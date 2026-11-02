package com.firefly.tv.ui.dialog;

import android.util.Log;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.bean.Config;
import com.firefly.tv.databinding.DialogHistoryBinding;
import com.firefly.tv.impl.ConfigListener;
import com.firefly.tv.ui.adapter.ConfigAdapter;
import com.firefly.tv.ui.custom.SpaceItemDecoration;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class HistoryDialog extends BaseAlertDialog implements ConfigAdapter.OnClickListener {

    private DialogHistoryBinding binding;
    private ConfigAdapter adapter;
    private ConfigListener listener;
    private boolean readOnly;
    private int type;

    public static HistoryDialog create() {
        return new HistoryDialog();
    }

    public HistoryDialog vod() {
        type = 0;
        return this;
    }

    public HistoryDialog live() {
        type = 1;
        return this;
    }

    public HistoryDialog readOnly() {
        readOnly = true;
        return this;
    }

    /**
     * T-211 扩围（全站 LOGO＝历史列表）：允许宿主**注入回调**，不再强制要求 Activity implements ConfigListener。
     * 原写法 {@code onTextClick} 里 `((ConfigListener) requireActivity())` 是强制转型——
     * 点播/搜索/历史/收藏/选台/推送六页都没实现该接口，LOGO 一旦指向本弹窗就会 ClassCastException 崩。
     * 注入优先级：外部 listener > 宿主自身（instanceof）> null（留痕后仅 dismiss，绝不静默）。
     */
    public HistoryDialog listener(ConfigListener listener) {
        this.listener = listener;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogHistoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new ConfigAdapter(this);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.recycler.setAdapter(adapter.readOnly(readOnly).addAll(type));
    }

    @Override
    public void onTextClick(Config item) {
        // T-211 扩围：外部 listener 优先，其次宿主自身；两者都没有就留痕后关闭——
        // 既不能崩（强制转型），也不能静默 dismiss（T-210/T-186 同型教训）。
        ConfigListener target = listener != null ? listener : (requireActivity() instanceof ConfigListener ? (ConfigListener) requireActivity() : null);
        Log.d("FireFlyHome", "history pick repoLen=" + (item == null ? 0 : item.getName().length()) + " target=" + (target != null ? "ok" : "none"));
        if (target != null) target.setConfig(item);
        dismiss();
    }

    @Override
    public void onDeleteClick(Config item) {
        if (adapter.remove(item) == 0) dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) dismiss();
        else setWidth(0.4f);
    }
}