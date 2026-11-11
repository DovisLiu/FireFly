package com.firefly.tv.ui.dialog;

import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Site;
import com.firefly.tv.databinding.DialogSiteBinding;
import com.firefly.tv.impl.SiteListener;
import com.firefly.tv.ui.adapter.SiteAdapter;
import com.firefly.tv.ui.custom.SpaceItemDecoration;
import com.firefly.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SiteDialog extends BaseAlertDialog implements SiteAdapter.OnClickListener {

    private DialogSiteBinding binding;
    private SiteListener listener;
    // β28 批一（刘先生批准动 SiteDialog，含牵连现网三调用点）：宿主关闭弹层后的收尾动作（底导那颗的高亮恢复）。
    // 走 onDismiss 这个统一出口＝四条关闭途径都能收到，不只「选了一项」那条。
    private Runnable onDismiss;
    private SiteAdapter adapter;
    private boolean search;
    private boolean change;

    public static SiteDialog create() {
        return new SiteDialog();
    }

    public SiteDialog search() {
        search = true;
        return this;
    }

    public SiteDialog change() {
        change = true;
        return this;
    }

    public SiteDialog onDismiss(Runnable action) {
        onDismiss = action;
        return this;
    }

    /**
     * β28 批一（A 裁④＝现状记录里那条「无 listener 不得静默」）：三层兜底口径严格照本仓 mobile HistoryDialog:61-76。
     * 原写法只有 :39 那一层（fragment instanceof SiteListener），拿不到就静默——onTextClick 里 :65 跳过、:66 只剩 dismiss，
     * 现象＝「点了没反应」，且与空列表那条无声秒关（:100）在日志上分不开。
     * 现按三层：外部（show 时那次赋值）＞ 父 Fragment／宿主 Activity 走 instanceof ＞ 都没有则留痕后仅 dismiss（绝不静默）。
     * 实测差异记录：SearchFragment:197 那处三层走完仍拿不到 listener（SearchFragment:52 与 SearchFragment 的宿主
     * SearchActivity:14 都没有 implements SiteListener）⇒ 行为仍是「不换源」，只是从看不见变成有一条 reason=none 的痕。
     * 回滚＝本方法与其调用点一起退回「listener 非空才调」的原样（备份＝backup-b28b1）。
     */
    private SiteListener target() {
        Fragment parent = getParentFragment();
        if (parent instanceof SiteListener) return (SiteListener) parent;
        android.app.Activity activity = getActivity();
        return activity instanceof SiteListener ? (SiteListener) activity : null;
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
        if (fragment instanceof SiteListener) listener = (SiteListener) fragment;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSiteBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new SiteAdapter(this);
        binding.recycler.setAdapter(adapter);
        adapter.search(search).change(change);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(VodConfig.getHomeIndex()));
    }

    @Override
    public void onTextClick(Site item) {
        SiteListener target = listener != null ? listener : target();
        if (target == null) android.util.Log.d("FireFlyHome", "site popup skip nav=pick reason=none");
        else target.setSite(item);
        dismiss();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onDismiss != null) onDismiss.run();
    }

    @Override
    public void onSearchClick(int position, Site item) {
        item.setSearchable(!item.isSearchable()).save();
        adapter.notifyItemChanged(position);
    }

    @Override
    public void onChangeClick(int position, Site item) {
        item.setChangeable(!item.isChangeable()).save();
        adapter.notifyItemChanged(position);
    }

    @Override
    public boolean onSearchLongClick(Site item) {
        boolean result = !item.isSearchable();
        adapter.getItems().forEach(site -> site.setSearchable(result).save());
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        return true;
    }

    @Override
    public boolean onChangeLongClick(Site item) {
        boolean result = !item.isChangeable();
        adapter.getItems().forEach(site -> site.setChangeable(result).save());
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) {
            // 空列表这条与「没有 listener」是两回事：各留一条自己的痕，否则两条 0 读数会互相冒充。
            android.util.Log.d("FireFlyHome", "site popup skip nav=list reason=empty");
            dismiss();
        }
        else if (ResUtil.isLand(requireContext())) setWidth(0.5f);
    }
}