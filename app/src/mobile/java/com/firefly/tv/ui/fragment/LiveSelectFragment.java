package com.firefly.tv.ui.fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.api.config.LiveConfig;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Channel;
import com.firefly.tv.bean.Group;
import com.firefly.tv.bean.Live;
import com.firefly.tv.bean.Site;
import com.firefly.tv.databinding.FragmentLiveSelectBinding;
import com.firefly.tv.impl.Callback;
import com.firefly.tv.impl.SiteListener;
import com.firefly.tv.model.LiveViewModel;
import com.firefly.tv.ui.activity.HomeActivity;
import com.firefly.tv.ui.activity.LiveActivity;
import com.firefly.tv.ui.adapter.ChannelCardAdapter;
import com.firefly.tv.ui.adapter.GroupChipAdapter;
import com.firefly.tv.ui.base.BaseFragment;
import com.firefly.tv.utils.Notify;

import java.util.ArrayList;
import java.util.List;

/**
 * β29 批二（A 落码单）：选台页由独立 Activity 收编为首页宿主 container 的 Fragment（position 8）。
 * 前身＝LiveSelectActivity（β75 甲案建、β77 返工 chip＋3 列卡片、β80 项4 台名优先），随本批删除；
 * 底导／菜单／弹层宿主／BACK 分派全部归 HomeActivity 一处，本件只留选台本义（分组＋频道＋起播）。
 * 触屏没有焦点态：分组为顶部 chip 横滚、频道为 3 列卡片网格（β77 返工 4→3 列，β80 台名优先），
 * 改前「一屏一个」的直接成因：GroupAdapter 的 item 根宽 match_parent，水平 LinearLayoutManager
 * 按父宽 EXACTLY 测量 ⇒ 换新布局（根 wrap_content）而非物质缩字号；卡片台标槽固定 40dp＋台名 1 行 ⇒ 行高一致。
 * keep 口径：本页只读 findKeepPosition 做定位＋高亮；setKeep 的写入点天然只在主层
 * requestLive（起播瞬间，LivePlaybackController:210）⇒ 进来"逛一遍"不会脏上次台记录。
 * 取数＝建页直取（initView 里 checkLive，0 处事件订阅）：β29 落码单第二节，A 收回设计页原假设，
 * 不照 VodFragment 挂事件——FragmentStateManager 是 hide/show 复用，onViewCreated（经 lazyLoad）
 * 只首建跑一次，切回来不重取，状态（分组／频道／滚动／选中）天然保住。
 */
public class LiveSelectFragment extends BaseFragment implements GroupChipAdapter.OnClickListener, ChannelCardAdapter.OnClickListener, SiteListener {

    private FragmentLiveSelectBinding mBinding;
    private LiveViewModel mViewModel;
    private GroupChipAdapter mGroupAdapter;
    private ChannelCardAdapter mChannelAdapter;
    private Group mGroup;

    public static LiveSelectFragment newInstance() {
        return new LiveSelectFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentLiveSelectBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        // 裁②（β29 落码单）：返回箭头＝回首页档 change(7)。Fragment 里不能再走 setSupportActionBar 的
        // up 导航（Activity 时代是 getSupportActionBar().setDisplayHomeAsUpEnabled(true) 画的箭头），
        // 箭头改由布局 app:navigationIcon="?attr/homeAsUpIndicator" 提供，行为落在监听这里；
        // 先判宿主类型再调＋留痕（照 β28⑤口径，不赌 getActivity() 的运行时类型）。
        mBinding.toolbar.setNavigationOnClickListener(v -> {
            Log.d("FireFlyLiveSelect", "select back to home");
            if (getActivity() instanceof HomeActivity) ((HomeActivity) getActivity()).change(7);
            else Log.d("FireFlyLiveSelect", "select back host mismatch host=" + getActivity());
        });
        mBinding.group.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        mBinding.channel.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        mBinding.group.setAdapter(mGroupAdapter = new GroupChipAdapter(this));
        mBinding.channel.setAdapter(mChannelAdapter = new ChannelCardAdapter(this));
        mViewModel = new ViewModelProvider(this).get(LiveViewModel.class);
        mBinding.progressLayout.showProgress();
        checkLive();
    }

    // 原 Activity 的 checkLive／getCallback／getLive／setGroup(Live)／setPosition／setGroup(Group,int) 六段原样搬入，
    // 仅两处必要适配：Context this→requireContext()；getLive 段 observe 的 owner 由 this 改 getViewLifecycleOwner()
    // （Fragment 必需，用 Activity 当 owner 会在视图销毁后仍持引用＝泄漏），其余一字未改（取数语义＝建页直取）。
    private void checkLive() {
        if (LiveConfig.isEmpty()) LiveConfig.get().init().load(getCallback());
        else getLive();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                getLive();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                mBinding.progressLayout.showContent(true, 0);
            }
        };
    }

    private void getLive() {
        mViewModel.live().observe(getViewLifecycleOwner(), this::setGroup);
        mViewModel.parse(LiveConfig.get().getHome());
    }

    private void setGroup(Live live) {
        List<Group> items = new ArrayList<>();
        // null 兜底同 TV LiveSelectActivity（T-153）：源侧偶发塞 null 分组
        if (live != null && live.getGroups() != null)
            for (Group group : live.getGroups()) if (group != null && !group.isHidden()) items.add(group);
        mGroupAdapter.addAll(items);
        mBinding.progressLayout.showContent(true, items.size());
        setPosition(LiveConfig.get().findKeepPosition(items));
        Log.d("FireFlyLiveSelect", "live select ready groups=" + items.size());
    }

    private void setPosition(int[] position) {
        int size = mGroupAdapter.getItemCount();
        if (size == 0) return;
        int index = position[0];
        if (index < 0 || index >= size) index = 0;
        if (mGroupAdapter.get(index).isEmpty()) {
            // 空组兜底口径同 LiveActivity.setPosition（β68 乙案）：改第一个非空组，全空就停在空态
            int next = -1;
            for (int i = 0; i < size; i++) if (!mGroupAdapter.get(i).isEmpty()) { next = i; break; }
            if (next == -1) return;
            index = next;
        }
        setGroup(mGroupAdapter.get(index), Math.max(position[1], 0));
        mBinding.group.scrollToPosition(index);
    }

    private void setGroup(Group group, int channel) {
        mGroup = group;
        mGroupAdapter.setSelected(group);
        mChannelAdapter.addAll(group.getChannel());
        int selected = Math.min(Math.max(channel, 0), Math.max(group.getChannel().size() - 1, 0));
        mChannelAdapter.setSelected(selected);
        mBinding.channel.scrollToPosition(selected);
    }

    @Override
    public void onItemClick(Group item) {
        if (item.isEmpty()) return; // 置灰项 GroupChipAdapter 已不绑点击，此行为兜底
        setGroup(item, Math.max(item.getPosition(), 0));
    }

    @Override
    public void onItemClick(Channel item) {
        if (mGroup == null) return;
        Log.d("FireFlyLiveSelect", "select open group=" + mGroup.getName() + " channel=" + item.getName());
        LiveActivity.start(requireContext(), mGroup.getName(), item.getName());
    }

    // β29 落码单第四节 5：implements SiteListener，setSite 体照 HomeFragment 那份的核心行（VodConfig.setHome）；
    // HomeFragment 那份还跟着额外行（getHistory()／getKeep()／homeContent()）的首页页内重灌，选台页不搬（差异已单列交回待 A 定口径）。
    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }
}
