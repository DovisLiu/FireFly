package com.firefly.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.firefly.tv.R;
import com.firefly.tv.bean.Channel;
import com.firefly.tv.databinding.AdapterChannelCardBinding;
import com.firefly.tv.ui.custom.GlassEdgeDrawable;
import com.firefly.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * β77 选台页专用频道卡片适配器（A 批准：不改播放页浮层同用的 ChannelAdapter，防把已验收的浮层一起改掉）。
 * 与 ChannelAdapter 的差异三处：
 * ①绑定 adapter_channel_card（台标在上＋台名一行的卡片，Grid 4 列）；
 * ②台标加载直接调 ImgUtil.load(…, vod=true)（A 裁的降级口径：无台标/坏域名 ⇒ 台名首字母圆角色块，
 *   色块下中心裁切＝接受的代价）——不复用 Channel.loadLogo，那份恒 vod=false，
 *   空台标会把 ImageView 置 GONE ⇒ 卡片槽位塌陷、台名上跳，网格行高参差；
 * ③接口只有 onItemClick，不带 onLongClick（β67 甲案手机收藏写入口已摘，本就不该再有长按位）。
 * 选中态单一来源沿用 ChannelAdapter 口径：setSelected(int) ⇒ 逐项 setSelected + root.setSelected。
 */
public class ChannelCardAdapter extends RecyclerView.Adapter<ChannelCardAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Channel> mItems;

    public ChannelCardAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(Channel item);
    }

    public void addAll(List<Channel> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void setSelected(int position) {
        // β83 附带（A 16:48 补的第四件；C 实测现无调用点传 -1）：-1 一进来当前写法会把整列表取消选中、
        // 还照样刷一遍。这是不可达路径上的防御，不改现有行为，只挡以后新增的调用点。
        if (position == -1) return;
        for (int i = 0; i < mItems.size(); i++) mItems.get(i).setSelected(i == position);
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterChannelCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel item = mItems.get(position);
        ImgUtil.load(item.getName(), item.getLogo(), holder.binding.logo, true);
        holder.binding.name.setText(item.getShow());
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
        // β26 玻璃扩面：选台卡根（adapter_channel_card.xml:8，bg=shape_live，corners 实测 4dp）挂方向性环
        holder.binding.getRoot().setForeground(new GlassEdgeDrawable(holder.itemView.getContext(), R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_channel_card));
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        Glide.with(holder.binding.logo).clear(holder.binding.logo);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterChannelCardBinding binding;

        ViewHolder(AdapterChannelCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
