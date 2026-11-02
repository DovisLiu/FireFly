package com.firefly.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.firefly.tv.Product;
import com.firefly.tv.bean.Keep;
import com.firefly.tv.databinding.AdapterVodRectBinding;
import com.firefly.tv.utils.ImgUtil;
import com.firefly.tv.utils.ResUtil;

/**
 * TV 2.0 首页「我的收藏」行（竖版海报卡）。
 * Keep 无更新数/评分数据源，按 v1.9 方案如实降级：不带角标。
 */
public class KeepPresenter extends Presenter {

    private final OnClickListener listener;
    private int width, height;
    // T-303（A 派单：照抄历史链、不写第二套）：删除态标志位与 HistoryPresenter:21 同构——
    // 按 Presenter 实例存，不按行、不按页面全局。
    private boolean delete;

    public interface OnClickListener {

        void onItemClick(Keep item);

        // T-303：删除态下 OK 的分流与长按。**签名带 item** 是有意的——
        // HistoryPresenter.OnClickListener#onLongClick() 是无参的，两者在 HomeActivity 里构成合法重载；
        // 若这里也用无参签名，两个接口的同名同签名方法会被同一实现同时满足 ⇒ 收藏长按会误进历史删除态。
        void onItemDelete(Keep item);

        boolean onLongClick(Keep item);
    }

    /** T-303：与 HistoryPresenter:44-50 同一份语义。 */
    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    /** T-303：照 HistoryPresenter:52-58 逐句同构（长按进删除态；删除态下 OK＝删除，否则＝进详情）。 */
    private void setClickListener(View root, Keep item) {
        root.setOnLongClickListener(view -> listener.onLongClick(item));
        root.setOnClickListener(view -> {
            if (isDelete()) listener.onItemDelete(item);
            else listener.onItemClick(item);
        });
    }

    public KeepPresenter(OnClickListener listener) {
        this.listener = listener;
        int space = ResUtil.dp2px(48) + ResUtil.dp2px(16 * (Product.getColumn() - 1));
        int base = ResUtil.getScreenWidth() - space;
        width = base / Product.getColumn();
        height = (int) (width / 0.75f);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        ViewHolder holder = new ViewHolder(AdapterVodRectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().getLayoutParams().width = width;
        holder.binding.image.getLayoutParams().height = height;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        Keep item = (Keep) object;
        ViewHolder holder = (ViewHolder) viewHolder;
        setClickListener(holder.view, item);
        holder.binding.name.setText(item.getVodName());
        // β18 / T-213：收藏行左上＝更新进度角标，与 HistoryPresenter:77-78 同一写法同一节点（year 位）；
        // 老收藏行（35→36 迁移前）该列为 NULL／空串 ⇒ **隐藏**，绝不出现空角标或错位角标；
        // 评分（右上）本轮**不渲染**：全仓没有评分数据源，不许现查补，也不许用别的字段冒充。
        holder.binding.year.setText(item.getVodRemarks());
        holder.binding.year.setVisibility(item.getVodRemarks() == null || item.getVodRemarks().isEmpty() ? View.GONE : View.VISIBLE);
        holder.binding.site.setVisibility(View.GONE);
        holder.binding.remark.setVisibility(View.GONE);
        // T-303：删除态遮罩的可见性重绑与 HistoryPresenter:81 同一写法（同一视觉＝同一节点语义）。
        holder.binding.delete.setVisibility(!delete ? View.GONE : View.VISIBLE);
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
        ViewHolder holder = (ViewHolder) viewHolder;
        Glide.with(holder.binding.image).clear(holder.binding.image);
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterVodRectBinding binding;

        public ViewHolder(@NonNull AdapterVodRectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
