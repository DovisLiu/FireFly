package com.firefly.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.History;
import com.firefly.tv.databinding.AdapterVodBinding;
import com.firefly.tv.ui.custom.GlassEdgeDrawable;
import com.firefly.tv.utils.ImgUtil;

public class HistoryAdapter extends BaseDiffAdapter<History, HistoryAdapter.ViewHolder> {

    private final OnClickListener listener;
    private int width, height;
    private boolean animate;
    private boolean delete;

    public HistoryAdapter(OnClickListener listener) {
        this.listener = listener;
        this.animate = true;
    }

    public interface OnClickListener {

        void onItemClick(History item);

        void onItemDelete(History item);

        boolean onLongClick();
    }

    public void setSize(int[] size) {
        this.width = size[0];
        this.height = size[1];
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.animate = false;
        this.delete = delete;
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void clear() {
        super.clear();
        setDelete(false);
        History.clear(VodConfig.getCid());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(AdapterVodBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().getLayoutParams().width = width;
        holder.binding.image.getLayoutParams().height = height;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        History item = getItem(position);
        holder.binding.name.setText(item.getVodName());
        // β83 第6条（Q3 甲）：左上＝集数、右上源名与右下备注一律不显——写法逐字对齐 leanback 首页行的历史档
        // （同一节点名、同一判空口径），不留"只改一个入口"的分叉；被删的 same 变量与站点可见性判定一并清干净。
        holder.binding.year.setText(item.getVodRemarks());
        holder.binding.year.setVisibility(item.getVodRemarks().isEmpty() ? View.GONE : View.VISIBLE);
        holder.binding.site.setVisibility(View.GONE);
        holder.binding.remark.setVisibility(View.GONE);
        holder.binding.progress.setMax((int) item.getDuration());
        holder.binding.progress.setProgress((int) item.getPosition(), animate);
        holder.binding.delete.setVisibility(!delete ? View.GONE : View.VISIBLE);
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
        setClickListener(holder.binding, item);
    }

    private void setClickListener(AdapterVodBinding binding, History item) {
        // β31 B2：root 恒 onItemClick（删除态由宿主监听退态，不再在卡上分流）；垃圾桶独立监听才删除该条。
        binding.getRoot().setOnLongClickListener(view -> listener.onLongClick());
        binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
        binding.delete.setOnClickListener(view -> listener.onItemDelete(item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterVodBinding binding;

        ViewHolder(@NonNull AdapterVodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            // β84 玻璃批 2：历史卡海报叠方向性高光边。本卡＝首页「历史」行＋历史页两个入口的同一张卡
            //（第 6 条甲1 已裁一页一档），所以这一行同时给这两处上边；本卡叠 5 层，顺序＝①底板 black_20 ②海报图 ③**高光** ④三枚角标 ⑤根布局的水波。
            binding.image.setForeground(new GlassEdgeDrawable(binding.image.getContext()));
        }
    }
}
