package com.firefly.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.firefly.tv.bean.Keep;
import com.firefly.tv.databinding.AdapterVodBinding;
import com.firefly.tv.ui.custom.GlassEdgeDrawable;
import com.firefly.tv.utils.ImgUtil;

public class KeepAdapter extends BaseDiffAdapter<Keep, KeepAdapter.ViewHolder> {

    private final OnClickListener listener;
    private int width, height;
    private boolean delete;

    public KeepAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(Keep item);

        void onItemDelete(Keep item);

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
        this.delete = delete;
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void clear() {
        super.clear();
        setDelete(false);
        Keep.deleteAll();
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
        Keep item = getItem(position);
        holder.binding.name.setText(item.getVodName());
        // β83 第6条（Q3 甲）：收藏行左上＝更新进度角标，与上面历史行、leanback 收藏档同一写法；
        // 老数据（35→36 迁移前）该列为 NULL／空串 ⇒ 隐藏，绝不出现空角标。源名不再进卡面。
        holder.binding.year.setText(item.getVodRemarks());
        holder.binding.year.setVisibility(item.getVodRemarks() == null || item.getVodRemarks().isEmpty()
                ? View.GONE : View.VISIBLE);
        holder.binding.site.setVisibility(View.GONE);
        holder.binding.remark.setVisibility(View.GONE);
        holder.binding.progress.setVisibility(View.GONE);
        holder.binding.delete.setVisibility(!delete ? View.GONE : View.VISIBLE);
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
        setClickListener(holder.binding, item);
    }

    private void setClickListener(AdapterVodBinding binding, Keep item) {
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
            // β84 玻璃批 2：收藏卡叠同一条方向性高光边（本卡＝首页那一行与收藏页共用的同一张卡，与 HistoryAdapter 那处成对）。
            // 本卡叠 5 层，顺序就地写全、不让人再去别处找＝①底板 black_20 ②海报图 ③**高光**（image 的 foreground）④左上/右上/左下三枚角标与删除覆盖层 ⑤根布局的水波。
            binding.image.setForeground(new GlassEdgeDrawable(binding.image.getContext()));
        }
    }
}
