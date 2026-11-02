package com.firefly.tv.ui.dialog;

import android.util.Log;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Site;
import com.firefly.tv.databinding.DialogSiteBinding;
import com.firefly.tv.impl.SiteListener;
import com.firefly.tv.setting.Setting;
import com.firefly.tv.ui.adapter.SiteAdapter;
import com.firefly.tv.ui.custom.SpaceItemDecoration;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SiteDialog extends BaseAlertDialog implements SiteAdapter.OnClickListener {

    private static final int GRID_COUNT = 10;

    private RecyclerView.ItemDecoration decoration;
    private DialogSiteBinding binding;
    private SiteListener listener;
    private SiteAdapter adapter;
    private boolean action;
    private int type;

    public static SiteDialog create() {
        return new SiteDialog();
    }

    public SiteDialog search() {
        type = 1;
        return this;
    }

    public SiteDialog action() {
        action = true;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
        if (activity instanceof SiteListener) listener = (SiteListener) activity;
        // T-210：接口未接上必须留痕——原写法 instanceof 失败时静默降级，onItemClick 只剩 dismiss()，
        // 表现是「点了没反应、日志里什么都没有」，与 T-186 的 NoPresenter 同一类失败模式。
        // T-212（β2）补仓库字段：本项目「源」有两级——SiteDialog 只列**当前仓库内**的站点
        // （SiteAdapter.addAll 走 VodConfig.get().getSites()），切仓库要另走 LOGO/设置里的仓库列表。
        // 打出 repo= 与 cid 后，C 一眼就能判"找不到原源"是站点级还是仓库级的问题，不再靠猜。
        Log.d("FireFlyHome", "siteDialog host=" + activity.getClass().getSimpleName()
                + " siteListener=" + (listener != null)
                + " repo=" + VodConfig.get().getConfig().getName()
                + " cid=" + VodConfig.getCid());
    }

    private boolean list() {
        return Setting.getSiteMode() == 0 || adapter.getItemCount() < GRID_COUNT;
    }

    private int getCount() {
        return list() ? 1 : Math.clamp((int) Math.ceil((double) adapter.getItemCount() / GRID_COUNT), 2, 3);
    }

    private int getIcon() {
        return list() ? com.firefly.tv.R.drawable.ic_site_grid : com.firefly.tv.R.drawable.ic_site_list;
    }

    private float getWidth() {
        return 0.4f + (getCount() - 1) * 0.2f;
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
        if (action) binding.action.setVisibility(View.VISIBLE);
        setType(type);
        setRecyclerView();
        setMode();
    }

    @Override
    protected void initEvent() {
        binding.mode.setOnClickListener(this::onMode);
        binding.select.setOnClickListener(v -> adapter.selectAll());
        binding.cancel.setOnClickListener(v -> adapter.cancelAll());
        binding.search.setOnClickListener(v -> setType(v.isSelected() ? 0 : 1));
        binding.change.setOnClickListener(v -> setType(v.isSelected() ? 0 : 2));
    }

    private void setRecyclerView() {
        binding.recycler.setAdapter(adapter);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        if (decoration != null) binding.recycler.removeItemDecoration(decoration);
        binding.recycler.addItemDecoration(decoration = new SpaceItemDecoration(getCount(), 16));
        binding.recycler.setLayoutManager(new GridLayoutManager(requireContext(), getCount()));
        if (!binding.mode.hasFocus()) binding.recycler.post(() -> binding.recycler.scrollToPosition(VodConfig.getHomeIndex()));
    }

    private void setType(int type) {
        binding.search.setSelected(type == 1);
        binding.change.setSelected(type == 2);
        binding.select.setClickable(type > 0);
        binding.cancel.setClickable(type > 0);
        adapter.setType(this.type = type);
    }

    private void setMode() {
        if (adapter.getItemCount() < GRID_COUNT) Setting.putSiteMode(0);
        binding.mode.setEnabled(adapter.getItemCount() >= GRID_COUNT);
        binding.mode.setImageResource(getIcon());
    }

    private void setWidth() {
        setWidth(getWidth());
    }

    private void onMode(View view) {
        Setting.putSiteMode(Math.abs(Setting.getSiteMode() - 1));
        setRecyclerView();
        setMode();
        setWidth();
    }

    @Override
    public void onItemClick(Site item) {
        if (listener != null) listener.setSite(item);
        dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) dismiss();
        else setWidth();
    }
}
