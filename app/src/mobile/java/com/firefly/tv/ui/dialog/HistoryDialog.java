package com.firefly.tv.ui.dialog;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.bean.Config;
import com.firefly.tv.databinding.DialogHistoryBinding;
import com.firefly.tv.impl.ConfigListener;
import com.firefly.tv.ui.adapter.ConfigAdapter;
import com.firefly.tv.ui.custom.SpaceItemDecoration;
import com.firefly.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class HistoryDialog extends BaseAlertDialog implements ConfigAdapter.OnClickListener {

    private DialogHistoryBinding binding;
    private ConfigListener listener;
    private ConfigAdapter adapter;

    private int type;
    private boolean readOnly;

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

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
    }

    private boolean isFull() {
        return getParentFragment() == null;
    }

    /**
     * P0 现场一（beta21 覆盖安装后 ClassCastException: HomeFragment→ConfigListener，2026-11-04 定案，A 裁＝甲）。
     * 原写法在 onAttach 里无条件强转宿主（按 isFull() 二选一：宿主 Context 或 parentFragment），没有任何 instanceof。
     * 首页 LOGO 用 show(this) 把本弹窗挂到 HomeFragment 的 childFragmentManager 上，而 HomeFragment 没 implements ConfigListener
     * ⇒ onAttach 当场崩（包内实证：抛点＝该方法第二条 check-cast，目标类型 r00＝ConfigListener，被转的对象 b91＝HomeFragment）。
     * 现按仓内现成范式改成三层（样板＝leanback 本类 :43-48 与 :80，T-211 扩围已把口径写死）：
     * 外部注入 listener ＞ 父 Fragment／宿主 Activity 走 instanceof ＞ 都没有则留痕后仅 dismiss（绝不静默，T-210／T-186 同型教训）。
     * ⇒ onAttach 里不再有任何强转，整个重写删掉（父类 Fragment.onAttach 自带实现，本类无须再覆盖）。
     * 行为差：首页那张是 readOnly() 列表，选中条目现在＝留痕＋关闭、不切配置（原＝崩）；要让它生效由宿主 .listener(...) 注入。
     * 回退＝git checkout de8fc90 -- 本文件（会重新引入崩溃，勿作常规回退；本批回滚点另见补丁块）。
     */
    public HistoryDialog listener(ConfigListener listener) {
        this.listener = listener;
        return this;
    }

    private ConfigListener target() {
        Fragment parent = getParentFragment();
        if (parent instanceof ConfigListener) return (ConfigListener) parent;
        android.app.Activity activity = getActivity();
        return activity instanceof ConfigListener ? (ConfigListener) activity : null;
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
        if (isFull()) binding.recycler.setMaxHeight(ResUtil.dp2px(264));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.setAdapter(adapter.readOnly(readOnly).addAll(type));
    }

    @Override
    public void onTextClick(Config item) {
        ConfigListener target = listener != null ? listener : target();
        android.util.Log.d("FireFlyHome", "history pick host=" + (target != null ? "resolved" : "none") + " action=setConfig");
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
        else if (ResUtil.isLand(requireContext())) setWidth(0.5f);
    }
}