package com.firefly.tv.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.R;
import com.firefly.tv.api.config.LiveConfig;
import com.firefly.tv.bean.Channel;
import com.firefly.tv.bean.Group;
import com.firefly.tv.bean.Live;
import com.firefly.tv.databinding.ActivityLiveSelectBinding;
import com.firefly.tv.impl.Callback;
import com.firefly.tv.model.LiveViewModel;
import com.firefly.tv.ui.adapter.GroupAdapter;
import com.firefly.tv.ui.adapter.SelectChannelAdapter;
import com.firefly.tv.ui.base.BaseActivity;
import com.firefly.tv.ui.custom.NavDelegate;
import com.firefly.tv.ui.custom.NavOverlay;
import com.firefly.tv.utils.Clock;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 直播选台页（spec §3.5 拍板 9/13 + v1.10 拍板 44，刘先生 2026-10-26 效果图 05_live-reference 确认）：
 * 列表页形态（与首页/点播/搜索结果同模板）——顶部一级导航（直播高亮，T-135 的 navStatic/navMenu 结构）
 * + 「分组」标签与胶囊行 + 4 列频道大卡网格；焦点落卡即高亮，OK 进直播播放页（LiveActivity）。
 * <p>
 * 硬约束（拍板 9/44 + T-140 教训）：解析失败/无频道必须显示空态提示，绝不黑屏、绝不自动播放；
 * 进页只做面板定位不 selectChannel（选台确认后才起播）。本轮按 A 裁决不做选台页视频预览
 * （效果图无预览窗）；播放页换台面板预览（§3.6）保持现状不动。
 */
public class LiveSelectActivity extends BaseActivity implements GroupAdapter.OnClickListener, SelectChannelAdapter.OnClickListener {

    private ActivityLiveSelectBinding mBinding;
    private Clock mClock;
    private NavOverlay mOverlay;
    private LiveViewModel mViewModel;
    private GroupAdapter mGroupAdapter;
    private SelectChannelAdapter mChannelAdapter;
    private List<Group> mHides;
    private Group mGroup;
    // β46 / 新1：β45 的 `mHoldGridFocus`（压住 onGroupSelected 抢焦的帧级标记）已随那条抢焦一起删除——
    // 抢焦不再发生，标记失去唯一消费者。历史成因记在 onGroupSelected 的注释里（beta72 真机 5/5 停在同一张卡）。
    // β45 / B：频道网格列数——与 `setRecyclerView()` 的 `setNumColumns(4)`（:115）
    // 及 activity_live_select.xml 里 channel 的 `app:numberOfColumns="4"`（:75）三处一致，改动时同改。
    private static final int CHANNEL_COLUMNS = 4;

    public static void start(Context context) {
        context.startActivity(new Intent(context, LiveSelectActivity.class));
    }

    private Live getHome() {
        return LiveConfig.get().getHome();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityLiveSelectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mClock = Clock.create(mBinding.navBar.clock);
        // 导航栏接入（T-135 结构）：直播项高亮；返回=finish 回首页
        mOverlay = NavDelegate.bind(this, mBinding.navBar.logo, mBinding.navBar.topNav, R.id.nav_live);
        mBinding.navBar.getRoot().post(() -> mOverlay.show());
        mHides = new ArrayList<>();
        setRecyclerView();
        setViewModel();
        checkLive();
    }

    @Override
    protected void initEvent() {
        // 分组切换 → 频道网格跟随刷新（进度态不响应，避免首帧抢焦点）
        mBinding.group.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (!mBinding.progressLayout.isContent()) return;
                if (mGroupAdapter.getItemCount() > 0 && position >= 0) onGroupSelected(mGroupAdapter.get(position));
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // β4 / T-208（A 裁定 1：探针法全站铺、旧轨真删）：本页原先自带一套探针
        // （mUpProbe + 分组行的 ACTION_DOWN 立即 reveal），现整体换成共享探针，**行为等价**：
        // · 分组行按 UP：系统搜索会先把它交给顶栏那一带（胶囊/LOGO），probeUp 判 isOnBar ⇒ 纠正到 nav_live；
        //   若上方搜不到任何节点则焦点未变 ⇒ 判顶到边，同样落 nav_live；
        // · 频道网格首行按 UP：焦点确实上移到分组行 ⇒ UP_NONE，放行默认路由（行间移动不被劫持）；
        // · 网格顶行之上无可聚焦节点（T-188-b2 的"按了没反应"）：焦点未变 ⇒ 顶到边 ⇒ nav_live。
        // 长按护栏由共享层统一给（repeatCount>0 不记基线）。
        mOverlay.onKeyDown(event, getCurrentFocus());
        // β45 / B（A 派单；刘先生实测"从焦点台上翻到顶，不经过分类菜单就直接进导航菜单"）：上面 :107 那条注释
        // 是**假定**"首行 UP 焦点确实上移到分组行"，实测并不总成立——本页没有任何手动上交的代码，全靠系统搜索；
        // 一旦搜不到分类菜单，抬起就落进 `NavOverlay.java:135-137 reason=edge-unchanged`（或 `:141-143
        // off-target-correct`），**两条出口都是 UP_REVEAL ⇒ 直接跳导航**。现按直播播放页的同一条做法
        // （`LiveActivity.java:1210-1224` 顶行 UP 手动交分组行）显式接管，四条硬约束：
        //   ①**只在网格首行**（pos < CHANNEL_COLUMNS）且 repeatCount==0 的 ACTION_DOWN 接管；非首行交回系统，
        //     "页内上移一行"的既有行为不改；
        //   ②**不吃键**——交不出去就继续往下走原有路由。β46 起"交出去"的判据是**同一帧内焦点真的在分类菜单上**
        //     （`group.hasFocus()`），不再是 `requestFocus()` 的返回值；交不出去时不吞这把键，也**不会**打 `moved=true`；
        //   ③**不加延迟重试**（刘先生已明确否决）——延后那一拍的复核只**读**焦点位置并打日志，不改判据、不二次投焦点；
        //   ④落点同样**跳过空组**（与进页首落焦共用 groupIndexOrFirstNonEmpty()，空收藏组不落）。
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && event.getRepeatCount() == 0) {
            View focused = getCurrentFocus();
            RecyclerView.ViewHolder vh = focused == null ? null : mBinding.channel.findContainingViewHolder(focused);
            if (vh != null) {
                int pos = vh.getAbsoluteAdapterPosition();
                if (pos >= 0 && pos < CHANNEL_COLUMNS) {
                    int idx = groupIndexOrFirstNonEmpty();
                    boolean moved = idx >= 0 && handOverToGroup(idx);
                    Log.d("FireFlyLive", "select UP pos=" + pos + " cols=" + CHANNEL_COLUMNS
                            + " topRow -> group idx=" + idx + " moved=" + moved);
                    if (moved) return true;
                }
            }
        }
        // β47 / 新1（A 单一指令）：焦点已经在分类菜单上时按 LEFT/RIGHT 切组，**跳过 channels=0 的空组**
        // （与进页落焦、首行 UP 共用同一套"非空"规则）。真机缺陷：LEFT 一路走到 `idx=0` 的空收藏组，
        // `onGroupSelected()` 会去 `showEmpty()`，而空态根节点是 match_parent（`activity_live_select.xml:82-90`）
        // ⇒ 焦点变成 `[0,0][1920,1080]`（带外），之后左右键在页内再无落点（C 实测：随后 5 次 RIGHT 全停在同一根节点）。
        // 三条约束都守：①不延时重试；②只在"焦点真在胶囊行"时接管（判据＝真实持焦节点，见 `focusIsGroupChip()`）；
        // ③**全空/唯一非空**的兜底＝焦点留在当前胶囊（A 的兜底口径），不掉根节点并留痕——这一把键被本页消费，
        //   是"到边"而非"没反应"，日志会写明原因。
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                && focusIsGroupChip()
                && (com.firefly.tv.utils.KeyUtil.isLeftKey(event) || com.firefly.tv.utils.KeyUtil.isRightKey(event))) {
            final int dir = com.firefly.tv.utils.KeyUtil.isRightKey(event) ? 1 : -1;
            final int cur = mBinding.group.getSelectedPosition();
            final int target = nextNonEmptyFrom(cur, dir);
            Log.d("FireFlyLive", "select walk dir=" + (dir > 0 ? "right" : "left") + " cur=" + cur
                    + " target=" + target + " groups=" + mGroupAdapter.getItemCount()
                    + " focus=" + focusTrace());
            if (target >= 0) {
                if (handOverToGroup(target)) return true;
            } else {
                // 兜底：除当前组外没有别的非空组 ⇒ 焦点保持在当前胶囊（不交回系统，避免落到空组塌空态）
                Log.d("FireFlyLive", "select walk boundary cur=" + cur + " groups=" + mGroupAdapter.getItemCount()
                        + " reason=noOtherNonEmpty keepFocusOnChip=" + focusIsGroupChip());
                return true;
            }
        }
        int verdict = mOverlay.probeUp(event, getCurrentFocus());
        if (verdict == NavOverlay.UP_EAT) return true;
        if (verdict == NavOverlay.UP_REVEAL) return mOverlay.revealToPage();
        return super.dispatchKeyEvent(event);
    }

    private void setRecyclerView() {
        mBinding.group.setAdapter(mGroupAdapter = new GroupAdapter(this));
        mBinding.channel.setAdapter(mChannelAdapter = new SelectChannelAdapter(this));
        mBinding.channel.setHorizontalSpacing(ResUtil.dp2px(24));
        mBinding.channel.setVerticalSpacing(ResUtil.dp2px(16));
        mBinding.channel.setNumColumns(4); // 4 列大卡网格（效果图 4x3 观感；xml 里同时声明 numberOfColumns 双保险）
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(LiveViewModel.class);
        mViewModel.live().observe(this, this::onLiveParsed);
        mViewModel.error().observe(this, msg -> {
            if (msg == null || msg.isEmpty()) return;
            Notify.show(msg);
            showEmpty();
        });
    }

    private void checkLive() {
        mBinding.progressLayout.showProgress();
        if (LiveConfig.isEmpty()) LiveConfig.get().init().load(getCallback());
        else parse();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                parse();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                showEmpty();
            }
        };
    }

    private void parse() {
        mViewModel.parse(getHome());
    }

    private void onLiveParsed(Live live) {
        List<Group> items = new ArrayList<>();
        // T-153：过滤 null 分组（与 LiveActivity.setGroup 同款兜底，选台页/播放页解析同一 Live 对象）
        for (Group group : live.getGroups()) {
            if (group == null) continue;
            (group.isHidden() ? mHides : items).add(group);
        }
        if (items.isEmpty()) {
            // 空态兜底（拍板 9/44）：解析失败或无频道给提示，不黑屏、不自动播放
            showEmpty();
            return;
        }
        mGroupAdapter.addAll(items);
        // 进入选台页只做定位不播放（T-140 修复口径）；定位上次观看分组/频道，无记录则落第 1 组第 1 台
        int[] position = LiveConfig.get().findKeepPosition(items);
        if (position[0] < 0 || position[0] >= items.size()) position = new int[]{0, 0};
        mGroup = items.get(position[0]);
        // β45 / A 这里原本先置 `mHoldGridFocus = true` 压住 onGroupSelected 的抢焦（:177 的 setSelectedPosition
        // 会派生一次 selected 回调，而 leanback 把它排在 layout 里，可能晚于 showContent ⇒ 新落焦被拉回）。
        // β46 / 新1：那条抢焦本身已按 A 的口径整支删除（用户切组时不该被送进网格），压制标记随之取消。
        mBinding.group.setSelectedPosition(position[0]);
        mGroup.setPosition(position[1]);
        mChannelAdapter.addAll(mGroup.getChannel());
        if (mGroup.getChannel().isEmpty()) {
            // T-151：首页分组无频道同样按空态处理（拍板 9 底线）
            showEmpty();
            return;
        }
        mBinding.channel.setSelectedPosition(Math.min(position[1], mGroup.getChannel().size() - 1));
        hideEmpty();
        mBinding.progressLayout.showContent();
        // β45 / A（刘先生拍板 ③：进选台页先停在**分类菜单**，不再先落"上次那个台"）＋防雷（他追问的
        // "第一个分组是收藏，收藏没修好会不会出问题"——**成立**：空组一旦持焦，onGroupSelected() 会把频道
        // 网格清空并走 showEmpty()（`:197-201`），整页塌成空态）。规则：
        //   ①目标＝当前网格所属组（进页时就是 :174 findKeepPosition 定过的那组）；它为空或找不到就往后扫**第一个非空组**；
        //   ②**全空**＝异常态 ⇒ 交回既有空态兜底，**绝不把焦点放在空组上**；
        //   ③旧的首落焦（channel.requestFocus）不再是首选，但 :174 的 findKeepPosition **保留**——
        //     它仍负责把网格定位到上次那个台（记忆不丢，只是不抢焦点）。
        mBinding.group.post(() -> {
            int idx = groupIndexOrFirstNonEmpty();
            Log.d("FireFlyLive", "select entry focus idx=" + idx + " groups=" + mGroupAdapter.getItemCount()
                    + " keep=" + (idx >= 0 && mGroupAdapter.get(idx).isKeep()));
            if (idx < 0) {
                // 全空回退：沿用 T-151 的网格回落；拿不到焦点就显式给空态提示（不留黑屏、不落空组）
                if (!mBinding.channel.requestFocus()) showEmpty();
                return;
            }
            if (!handOverToGroup(idx)) mBinding.channel.requestFocus();
        });
    }

    /**
     * β45 / A＋B 共用：把焦点交给分类菜单的第 idx 项——先对齐 selected（胶囊行是横向列表，
     * selected 决定 requestFocus 落到哪个胶囊），再交焦点。返回**是否真的交出去了**；
     * 交不出去时调用方**必须放行**（刘先生的硬约束：不得吃键）。
     * β46（A 定论：树对、日志不对）：判据从 `requestFocus()` 的**返回值**换成**同一帧内焦点的真实位置**；
     * β47 又把那个"真实位置"从 `group.hasFocus()`（含后代＝假阳性）换成 `focusIsGroupChip()`
     * （`group.findFocus()` ＝ `getCurrentFocus()` 且父级＝胶囊行）。返回值 true 不等于焦点真落上去，
     * β45 首版就是拿它当判据。
     * β48（A 定论：不是时序，是两个视角从第一帧就不一致）：三帧 pre-draw 监控已给出结论（三帧完全一致＝
     * 焦点没在任何一帧被抢回）⇒ 按 A「不要再加帧监控」整支撤除，这里只留**同一拍的一行留痕**，
     * 把 `focus=`（框架焦点）／`selChild=`（频道网格 selected 子节点）／`winFocus=`（窗口焦点）三个身份并排打出来。
     */
    private boolean handOverToGroup(int idx) {
        mBinding.group.setSelectedPosition(idx);
        boolean gave = mBinding.group.requestFocus();
        boolean onGroup = focusIsGroupChip();
        // β48 / L2（A 定论：不是时序，是**两个视角从第一帧就不一致**）：三帧监控已给出它该给的结论（三帧一致＝
        // 焦点没在第几帧被抢回），使命结束 ⇒ 按 A「不要再加帧监控」整支撤掉（连同 startFocusWatch），不留无人使用的机制。
        // 改为同一拍把三个身份并排打：①focus= ＝框架真实持焦节点（我们判据用的那个）；
        // ②selChild= ＝频道网格里 selected=true 的那张卡——uiautomator 的 dump 里 selected 与 focused 是两个不同
        // 属性，leanback 交出焦点后仍保留 selected 子节点（「选中≠焦点」＝第一嫌疑：抓取若按 selected 取，就会稳定看到卡行）；
        // ③winFocus= ＝本窗是否持有窗口焦点（A 第 3 问「焦点窗未切换会分歧」的最便宜证据）。
        // 只读：不投焦点、不清焦点、无 sleep、无延时、无重试（刘先生两条禁令仍守）。
        Log.d("FireFlyLive", "select handover begin idx=" + idx + " gave=" + gave + " onGroup=" + onGroup
                + " focus=" + viewTag(getCurrentFocus()) + " selChild=" + selectedCardTag()
                + " winFocus=" + mBinding.group.hasWindowFocus() + " chips=" + mGroupAdapter.getItemCount());
        return onGroup;
    }

    /**
     * β48 / L2：频道网格中当前 selected=true 的子节点身份。
     * 为什么读 isSelected() 而不是 getSelectedPosition()：前者就是 RecyclerView/leanback 写给无障碍树
     * （AccessibilityNodeInfo.setSelected）的那一位，与 dump 的 selected 属性同源；后者是逻辑游标，
     * 可能指向还没布局的子项。无 selected 子节点时返回 none（空组/未布局态）。
     */
    private String selectedCardTag() {
        for (int i = 0, n = mBinding.channel.getChildCount(); i < n; i++) {
            View child = mBinding.channel.getChildAt(i);
            if (child.isSelected()) return viewTag(child);
        }
        return "none";
    }

    /**
     * β47 / L2：节点身份的可核对形式——`类名#资源entry名[左,上][右,下]`（窗口坐标；TV 全屏窗＝屏幕坐标，
     * 可与 uiautomator dump 的 bounds 直接比）。**必须带资源名**：分类胶囊与频道卡的根**同为
     * `LinearLayoutCompat`**（`adapter_group.xml:2` ／ `adapter_channel_select.xml:7`），光看类名分辨不出落点；
     * 而两者都没有 `android:id`（拿不到 entry 名时留空），所以再叠 bounds 一起判（胶囊行＝y 104..169 带）。
     */
    private String viewTag(View v) {
        if (v == null) return "none";
        String name = "";
        int id = v.getId();
        if (id != View.NO_ID && (id & 0xFF000000) == 0x7F000000) {
            try {
                name = "#" + getResources().getResourceEntryName(id);
            } catch (android.content.res.Resources.NotFoundException ignored) {
                name = "";
            }
        }
        int[] loc = new int[2];
        v.getLocationInWindow(loc);
        return v.getClass().getSimpleName() + name + "[" + loc[0] + "," + loc[1] + "]["
                + (loc[0] + v.getWidth()) + "," + (loc[1] + v.getHeight()) + "]";
    }

    /** 兼容旧调用点：当前持焦节点的 `viewTag`。 */
    private String focusTrace() {
        return viewTag(getCurrentFocus());
    }

    /**
     * β47 / L2：真判据——**当前真实持焦节点是不是胶囊行的直接子项**。
     * 三道校验：`group.findFocus()`（该子树内的持焦后代）＝`getCurrentFocus()`（全页持焦者）
     * 且其父就是 `mBinding.group`。这样可排除 `hasFocus()` 的两类假阳性：
     * ①焦点其实在网格卡（不同子树）；②行内有离屏/不可见后代曾持焦。
     * 只读判据，不投焦点、不延时、不重试（刘先生的两条禁令）。
     */
    private boolean focusIsGroupChip() {
        View inner = mBinding.group.findFocus();
        View current = getCurrentFocus();
        return inner != null && inner == current && inner.getParent() == mBinding.group;
    }

    /**
     * β47 / 新1：从 `cur` 出发按方向 `dir`（+1 右 / -1 左）找**下一个非空组**的下标，**不含 cur 自身**；
     * 找不到（其他组全空）回 -1，调用方按"焦点留在当前胶囊"处理。循环回绕与 `CustomLiveGridView`
     * 的到边回绕一致，不会出现"到头就停死"。
     */
    private int nextNonEmptyFrom(int cur, int dir) {
        int n = mGroupAdapter.getItemCount();
        if (n <= 0) return -1;
        int from = cur < 0 ? 0 : cur;
        for (int step = 1; step < n; step++) {
            int i = Math.floorMod(from + dir * step, n);
            if (!mGroupAdapter.get(i).isEmpty()) return i;
        }
        return -1;
    }

    /**
     * β45 防雷（A 与 B 共用）：分类菜单该落在/该交给哪一组——优先当前网格所属组，它为空或找不到就往后扫
     * 第一个非空组；**全空返回 -1**，调用方必须按"不落空组"处理（空收藏组是这里最现实的一种）。
     */
    private int groupIndexOrFirstNonEmpty() {
        int cur = mGroup == null ? -1 : mGroupAdapter.indexOf(mGroup);
        if (cur >= 0 && !mGroupAdapter.get(cur).isEmpty()) return cur;
        for (int i = 0, n = mGroupAdapter.getItemCount(); i < n; i++) {
            if (!mGroupAdapter.get(i).isEmpty()) return i;
        }
        return -1;
    }

    /**
     * 分组选中：切换右侧频道网格（只更新列表与**定位**；β46 起不再动焦点）。
     * <p>
     * β46 / 新1（A 派单，刘先生实机「焦点已在分类菜单，按左右切组时焦点会立即落到选台上」）：原先这个方法末尾
     * 有一条 `channel.post(channel.requestFocus())`——本意是"左右切分组后把焦点送进网格"，但**用户此刻还在选组、
     * 不是要选台**，切组被当成了"选完组"直接把焦点拽走。按 A 的口径改成**焦点留在分类菜单**：进网格只在用户自己
     * 按 DOWN 时发生（页内几何搜索本就负责这一步，β45 之前 UP/DOWN 的实测也证明它能落进首行）。
     * ⇒ 抢焦那一条整支删除；β45 为盖住它而加的 `mHoldGridFocus` 压制窗口随之**失去唯一消费者**，一并删掉
     *   （不留无人使用的机制）。进页落焦与首行 UP 上交仍走 {@link #handOverToGroup(int)} 的同一条真值判据。
     */
    private void onGroupSelected(Group group) {
        mGroup = group;
        // β46 / A 点名的留痕（删掉抢焦后仍保留）：这一行是"这一次 selected 派发发生在哪一拍、当时焦点在哪"的
        // 直接证据。idx=组下标；groupHasFocus=这一拍焦点是否真在分类菜单；toGrid 恒 false＝本方法不再动焦点。
        final boolean groupHasFocus = focusIsGroupChip();
        final int idx = group == null ? -1 : mGroupAdapter.indexOf(group);
        if (group == null || group.getChannel().isEmpty()) {
            // T-151（C beta4 实测）：分组无频道也属"无数据"→ 显示空态（拍板 9 底线），不再只是隐藏网格
            Log.d("FireFlyLive", "select group refresh idx=" + idx
                    + " groupHasFocus=" + groupHasFocus + " channels=0 toGrid=false empty=true"
                    + " focusNow=" + viewTag(getCurrentFocus()));
            // β47 / 新1（A 的兜底要求）：焦点此刻正在胶囊行 ⇒ **不塌空态**。塌了会把含胶囊行的整个内容区
            // （`progressLayout`）置 GONE 并把焦点送给 match_parent 的空态根节点 ⇒ 真机看到的
            // "进了空组后左右键全无落点"。留在胶囊行、把它留痕，正是 A 要的兜底行为。
            if (groupHasFocus) {
                Log.d("FireFlyLive", "select empty suppressed focusOnChip idx=" + idx
                        + " groups=" + mGroupAdapter.getItemCount() + " focus=" + focusTrace());
                return;
            }
            showEmpty();
            return;
        }
        hideEmpty();
        mBinding.channel.setVisibility(View.VISIBLE);
        mChannelAdapter.addAll(group.getChannel());
        mBinding.channel.setSelectedPosition(Math.max(group.getPosition(), 0));
        Log.d("FireFlyLive", "select group refresh idx=" + idx
                + " groupHasFocus=" + groupHasFocus + " channels=" + group.getChannel().size()
                + " toGrid=false empty=false"
                + " focusNow=" + viewTag(getCurrentFocus()));
    }

    /** 空态（拍板 9 底线：解析失败/无频道必须给提示，不黑屏）——隐藏内容区，焦点由空态页兜底 */
    private void showEmpty() {
        mBinding.progressLayout.setVisibility(View.GONE);
        mBinding.empty.setVisibility(View.VISIBLE);
        mBinding.empty.post(() -> mBinding.empty.requestFocus());
    }

    private void hideEmpty() {
        if (mBinding.empty.getVisibility() != View.VISIBLE) return;
        mBinding.empty.setVisibility(View.GONE);
        mBinding.progressLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public void onItemClick(Group item) {
        // 分组行点击等同于选中该分组（焦点切换已由 selected 监听处理，这里保留空实现避免误触）
    }

    @Override
    public void onItemClick(Channel item) {
        // 用户确认选台 → 进直播播放页并直接起播该频道（spec §3.5：选好台再播放）
        if (mGroup == null) return;
        LiveActivity.start(this, mGroup.getName(), item.getName());
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
    }

    // β44 / B1（A 定稿四点之一：「LiveSelect 算一级页，它现在也叠栈，一并纳入」）：
    // 本页从导航「直播」进入＝一级页 ⇒ BACK 回首页；从本页确认选台后开的 LiveActivity 属**二级**，
    // 它仍走自己的 finish 回到选台页（LiveActivity#onBackInvoked 未改）。
    @Override
    protected void onBackInvoked() {
        backToHome();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
    }
}
