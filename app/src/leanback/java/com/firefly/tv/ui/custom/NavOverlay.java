package com.firefly.tv.ui.custom;

import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/**
 * TV 2.0 导航浮层（刘先生拍板：全站实施；Round 10 静态修订）：
 * 浮层纯覆盖（内容不再平移联动，消除"独立模块"感）；
 * T-156 起菜单常驻（不再收起），T-188-b 起"上键呼出"实质是"上键把焦点交回当前页导航项"。
 */
public class NavOverlay {

    private static final long DURATION = 200;

    /**
     * β4 / T-208 裁定 1（A 2026-10-28）：**页内 UP 顶边判定的唯一权威——探针法**，全站铺、不分页特例。
     * 弃用 `isTopPosition()/isTopRow()` 的 position 语义（首页标题行占独立 position ⇒ 首排卡片恒 ≥1；
     * 点播分类栏的 position 是分类下标 ⇒ 只有第 1 项能命中），改为「**同一次按键 DOWN→UP 焦点未变**」＝顶到边。
     * 该法在选台页已验收（β27/28），天然与横向位置无关，正对「四页 × 3 位置」判据，不额外消耗按键。
     */
    public static final int UP_NONE = 0;
    /** 焦点按 DOWN 前就在本条导航上 ⇒ UP 原地吃键，绝不拽回当前页项（邻项左右移后再 UP 应不动）。 */
    public static final int UP_EAT = 1;
    /** 该把焦点交给本页导航项了：要么顶到边（焦点没动），要么被系统交给了**错**的导航项/LOGO ⇒ 纠正。 */
    public static final int UP_REVEAL = 2;

    public interface OnConcealedListener {
        void onConcealed();
    }

    private final View bar;
    private final TopNavView nav;
    private final int revealId;
    private OnConcealedListener concealedListener;

    /** 顶栏根（含常驻层 LOGO/时间 + 菜单层胶囊）——判定"焦点是否已在顶栏这一带"用。 */
    private View toolbarRoot;
    /** 日志里的页面名（C 按 from/to 的视图名判定落点，页面名只用来分线）。 */
    private String page = "nav";
    private View mUpProbe;
    private View mUpFrom;
    /** T-152 语义（焦点丢失时 UP 也要把焦点交回导航）并入探针层，页面不再各写一条守卫。 */
    private boolean mUpFocusMissing;
    /** β4 P0 修正新增：本轮 DOWN→UP 是否已武装（只有 `probeUp` 消费后才清）。 */
    private boolean mUpPending;

    public NavOverlay(View bar, TopNavView nav, int revealId) {
        this.bar = bar;
        this.nav = nav;
        this.revealId = revealId;
    }

    public void setToolbarRoot(View root) {
        this.toolbarRoot = root;
    }

    public void setPage(String page) {
        this.page = page == null ? "nav" : page;
    }

    /**
     * 每页 `dispatchKeyEvent` **开头**调用（不接管按键，只武装/作废探针）。
     *
     * **β4 P0 根因（C beta34 实测"首页 UP 完全没反应、FireFlyFocus 零条"）**：
     * 旧写法在 `else` 分支里对**任何**非"UP 的 ACTION_DOWN"事件都清基线
     * （旧 :72-75），而各页在 `dispatchKeyEvent` 开头无条件调用本方法
     * （如 `HomeActivity:657`），于是**同一把 UP 的 ACTION_UP 先把自己的基线清掉**，
     * 随后 `probeUp`（`HomeActivity:677`）读到的永远是 null ⇒ 判定走不到 ⇒ UP 既不接管也不落导航 ⇒ 死键。
     * 这条影响**所有走探针的 8 页**（首页/点播/搜索/合集/选台/直播/收藏/历史），
     * 其中选台页 β27/28 原本可用的局部探针也因改接本共享层而被一并打掉——这是本次打回的根，不是分页问题。
     *
     * 修正后的时序约定（唯一权威，页面不需要知道）：
     * · ACTION_UP / ACTION_MULTIPLE **一律不动基线**，留给 `probeUp` 消费后自清；
     * · 只有"UP 键的首次 DOWN（`repeatCount==0`）"武装探针；
     * · 其它键的 DOWN、以及 UP 的**长按重复**（`repeatCount>0`）作废本轮 ⇒ 满足 A 硬要求①"长按不误判"。
     */
    public void onKeyDown(KeyEvent event, View focus) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return;
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && event.getRepeatCount() == 0) {
            mUpProbe = focus;
            mUpFocusMissing = focus == null;
            mUpPending = true;
        } else {
            // β7 / ①诊断（A 裁定：纯日志、零行为）：这两句只做一件事——把“武装位被谁清掉”留痕。
            // `had=false`（本来就没武装）打了没信息量，只会淹掉判据 ⇒ 只在**真的清掉了一次有效武装**时出这一条。
            // 它把原先在日志上完全同形的两种失效分开：
            //   ①-a 跨窗口切分（这把 UP 的 DOWN 根本没到本页）⇒ 本条**不出现**，UP 只有 `reason=not-armed`；
            //   ①-b 中途被别键/同一把键的重复 DOWN 清掉 ⇒ 本条**先出现**，随后 UP 报 `reason=not-armed`。
            // 除日志外不改任何状态、不改分支走向 ⇒ 不污染 ②（有界重钉）的归因。
            if (mUpPending) {
                Log.d("FireFlyFocus", "probe arm=clear key=" + event.getKeyCode()
                        + " repeat=" + event.getRepeatCount() + " had=true page=" + page);
            }
            mUpProbe = null;
            mUpFocusMissing = false;
            mUpPending = false;
        }
    }

    /**
     * ACTION_UP 时的顶边判定；只**判定**、不改焦点（页面可先做必要的前处理，如直播页恢复顶栏可见）。
     * 三种归宿互斥：UP_NONE 放行默认路由（页内正常的层间上移）、UP_EAT 原地吃键、UP_REVEAL 交导航。
     * β4 P0 同时补足日志：**每把 UP 抬起必出一条** `mode=up probe verdict=… reason=…`，
     * 不再有"什么都不发生所以 C 无从判"的静默路径。
     */
    public int probeUp(KeyEvent event, View focus) {
        if (event.getAction() != KeyEvent.ACTION_UP || event.getKeyCode() != KeyEvent.KEYCODE_DPAD_UP) return UP_NONE;
        if (!mUpPending) {
            logProbe(null, focus, "none", "not-armed(long-press/foreign-key)");
            return UP_NONE;
        }
        View down = mUpProbe;
        boolean missing = mUpFocusMissing;
        mUpProbe = null;
        mUpFocusMissing = false;
        mUpPending = false;
        if (down == null) {
            // T-152：焦点丢失时 UP 也必须把焦点交回导航，否则按键落进系统搜索表现为"按了没反应"
            if (!missing) {
                logProbe(null, focus, "none", "no-baseline");
                return UP_NONE;
            }
            mUpFrom = null;
            logProbe(null, focus, "reveal", "focus-lost");
            return UP_REVEAL;
        }
        mUpFrom = down;
        if (nav.isNavItem(down)) {
            logProbe(down, focus, "eat", "already-on-nav");
            return UP_EAT;
        }
        if (focus == down) {
            logProbe(down, focus, "reveal", "edge-unchanged");
            return UP_REVEAL;
        }
        // 系统把焦点交给了顶栏这一带，但不是本页那一项（首页靠左=LOGO、点播非首项=点播/直播邻项）
        // ⇒ 纠正到本页项。LOGO 保持可聚焦、可点击（A 硬要求②），这里只是在它**被当成 UP 落点**时改派。
        if (isOnBar(focus)) {
            logProbe(down, focus, "reveal", "off-target-correct");
            return UP_REVEAL;
        }
        logProbe(down, focus, "none", "moved-in-page");
        return UP_NONE;
    }

    /**
     * 探针在 ACTION_DOWN 瞬间记下的焦点（未记时为 null）。
     * 供个别页面加**前置条件**用——直播播放页就靠它区分"沉浸态焦点在 video 上"（那时 UP=上一台，
     * 必须留给 {@code CustomKeyDownLive}）与"焦点在分组行/顶栏"（那时 UP=交回导航）。
     */
    public View probeFocus() {
        return mUpProbe;
    }

    /** 焦点交回本页导航项（revealId），并如实回传是否真拿到焦点——拿不到不吃键（β26「按了没反应」教训）。 */
    public boolean revealToPage() {
        return revealToPage("up -> nav");
    }

    /**
     * β15 / T-223 方案 A（A 裁定 2026-10-29）：**UP 的落点在这把键的 ACTION_DOWN 阶段一次定死**，
     * 不把键交给系统几何搜索 ⇒ 同一把键只产生**一个**落点。
     * 旧结构（beta34 起，8 页同型）是两段式：DOWN 放行 ⇒ 系统按几何最近命中顶栏**邻项**（首页右侧海报的
     * 焦点中心 x ≈1500 偏右，六胶囊整体居中 ⇒ `nav_home` 在中线左，**永远搜不到**）⇒ 抬起探针
     * `off-target-correct` 再抢 `nav_home` ⇒ 刘先生看到"先落直播、再落首页"。
     * **本期只由首页调用**（`HomeActivity:712` 那一档），其余 7 页不调＝仍走两段式，行为不变（A：先只改首页）。
     * 四条触发条件缺一不可：①这把键是 `DPAD_UP` 的**首次 DOWN**（`repeat==0` ⇒ 长按重复不劫持）；
     * ②焦点在内容区（不在导航胶囊上、也不在顶栏这一带——LOGO 按 UP 仍走原路）；
     * ③顶栏可见（GONE 的容器拿不到焦点，硬抢就是 β26 的死键）；
     * ④**已顶到边**（上方没有真正的页内目标，见下面的顶边守卫——缺这条会把非首排卡片的"上移一行"也劫持掉）。
     * 落位成功后把探针基线改写成"DOWN 那一刻焦点已在导航项上"⇒ 同一把键的 UP 走
     * `already-on-nav` → `UP_EAT`，抬起不再产生第二次移动。
     *
     * @return true＝焦点已落位，调用方吃掉这把 DOWN；false＝不改派，调用方原样放行（含抢不到焦点）。
     */
    public boolean revealOnDown(KeyEvent event, View focus) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getKeyCode() != KeyEvent.KEYCODE_DPAD_UP) return false;
        if (event.getRepeatCount() > 0) return false;
        if (focus == null || nav.isNavItem(focus) || isOnBar(focus)) return false;
        if (bar.getVisibility() != View.VISIBLE) return false;
        // 顶边守卫（落码时的自证补充——A 派单字面只写"顶栏可见 ∧ 焦点在内容区"，照字面执行会把
        // **非首排**卡片上的 UP 也劫持成跳导航，而那一档的语义是"页内上移一行"，属新缺陷）：
        // 只有在这把键的上方**没有真正的页内目标**时才改派。三种形态算顶边：
        // ①搜不到（null）；②搜到容器自身/祖先（leanback 在顶边常把滚动容器当搜索结果返回）；
        // ③搜到顶栏这一带（邻项胶囊或 LOGO——那正是本次要消灭的"第一落点"）。
        View above = focus.focusSearch(View.FOCUS_UP);
        if (above != null && above != focus && !isOnBar(above) && !isAncestorOf(above, focus)) return false;
        mUpFrom = focus;
        if (!revealToPage("up -> nav single")) return false;
        mUpProbe = navItem(revealId);
        mUpFocusMissing = false;
        mUpPending = true;
        return true;
    }

    private boolean revealToPage(String mode) {
        bar.animate().translationY(0f).setDuration(DURATION).start();
        boolean moved = nav.requestFocusItem(revealId);
        log(mUpFrom, moved ? navItem(revealId) : null, moved ? mode : "up -> nav FAILED");
        mUpFrom = null;
        return moved;
    }

    private View navItem(int id) {
        return nav.navItemOf(id);
    }

    private boolean isOnBar(View v) {
        if (toolbarRoot == null) return false;
        for (View p = v; p != null; ) {
            if (p == toolbarRoot) return true;
            if (!(p.getParent() instanceof View)) return false;
            p = (View) p.getParent();
        }
        return false;
    }

    /**
     * β15 / T-223：{@code anc} 是否为 {@code v} 的祖先。
     * 用在顶边判定上——leanback 的 {@code focusSearch(UP)} 在已经顶到边时常把滚动容器自身返回，
     * 那一类结果不能当成"页内上方还有目标"，否则本档永远不触发。
     */
    private static boolean isAncestorOf(View anc, View v) {
        if (anc == null || v == null) return false;
        for (View p = v.getParent() instanceof View ? (View) v.getParent() : null; p != null; ) {
            if (p == anc) return true;
            p = p.getParent() instanceof View ? (View) p.getParent() : null;
        }
        return false;
    }

    /** A 口径的同一条日志（tag=FireFlyFocus），C 现成的 grep 不破。 */
    private void log(View from, View to, String mode) {
        Log.d("FireFlyFocus", "focusMove from=" + viewName(from) + " to=" + viewName(to)
                + " row=-1 vis=" + (to != null) + " page=" + page + " mode=" + mode);
    }

    /**
     * β4 P0 补的**判定留痕**：每把 UP 抬起必出一条，C 只看 `FireFlyFocus` 就能判三件事——
     * ①探针有没有武装（`reason=not-armed(…)`＝基线被长按/其它键作废；不再出现"整页静默"）；
     * ②判定结论是什么（`verdict=none|eat|reveal`）；
     * ③为什么（`edge-unchanged`＝顶到边、`off-target-correct`＝被系统抢到顶栏别处后纠正、
     *   `moved-in-page`＝页内正常上移不接管、`already-on-nav`＝已在导航项上吃键、`focus-lost`＝T-152）。
     * 这里的 `vis=` 语义是"这把 UP 有没有真的让焦点移动"（`from != to`）。
     * <p>
     * β7 / ①两条口径变更（C 复测组③时按这两条读，别再按旧字面理解）：
     * · {@code from}/{@code to} 的取值集合从「名字 | none」变成「名字 | {@code null} | {@code noid@类名@实例号}」
     *   ——旧日志里的 `none` 混装了“没焦点”和“焦点在无 id 的内容卡上”，现在两者可分（见 {@link #probeViewName}）；
     * · `not-armed` 现在有了**配对的前向证据**：若这把键的武装位是被别键/重复 DOWN 清掉的，之前会先出一条
     *   `probe arm=clear key=… repeat=… had=true page=…`；**这条不出现**就说明 DOWN 压根没到本页（跨窗口切分）。
     */
    private void logProbe(View down, View current, String verdict, String reason) {
        Log.d("FireFlyFocus", "focusMove from=" + viewName(down) + " to=" + viewName(current)
                + " row=-1 vis=" + (down != null && current != null && down != current)
                + " page=" + page + " mode=up probe verdict=" + verdict + " reason=" + reason);
    }

    private String viewName(View v) {
        return probeViewName(v, nav.getContext().getResources());
    }

    /**
     * β7 / ①诊断（A 要求：`null` 与“无 id 的内容卡”不许再都写成 `none`）。
     * 三种形态从此可分：
     * · {@code null} —— 本页当下**没有焦点**（例：跨窗口切分时 UP 到达首页那一瞬）；
     * · {@code noid@类名@hashCode} —— 焦点在**无资源 id 的 view** 上（首页内容卡即此类，
     *   {@code adapter_vod_rect.xml:2-9} 根节点无 {@code android:id}），带上实例号就能判“换了一张卡”还是“同一张卡”；
     * · {@code nav_home / logo / …} —— 有 id 的焦点（胶囊、常驻层），与旧日志完全兼容。
     * 静态且只依赖入参 ⇒ 页面侧（如首页重钉日志）可直接复用同一个口径，不再各写一份。
     */
    public static String probeViewName(View v, android.content.res.Resources res) {
        if (v == null) return "null";
        if (v.getId() == View.NO_ID) return "noid@" + v.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(v));
        try {
            return res.getResourceEntryName(v.getId());
        } catch (Throwable t) {
            return "noid";
        }
    }

    /** 页面接线：导航整体焦点变化驱动显隐（焦点进内容自动收起） */
    public void attach() {
        nav.setOnBarFocusChanged(focused -> {
            if (focused) show();
            else conceal();
        });
    }

    public void setOnConcealedListener(OnConcealedListener listener) {
        this.concealedListener = listener;
    }

    /**
     * β4 / T-208 硬要求③「旧轨真删」：`canReveal()` 已**无调用者**（T-188-b 起恒真、本来就只是死前提），
     * 随 8 处页内守卫一并删除，不留一条"看起来还有用"的空 API。
     * `reveal()` 仍保留：设置页的两条"出导航"边（左列首项 UP／面板首行 UP）由行序直接判定顶边，
     * 不需要探针（那一页没有"上方还有一行内容"的语义）。
     */

    /** 呼出：滑入并焦点进入导航当前页项；焦点已在本条导航上时吃掉按键（判据"不得落 LOGO"） */
    public boolean reveal() {
        // 焦点已在某个导航项上：既不再把它拽回"当前页项"（邻项按 UP 应原地不动），
        // 也不能 return false 放行系统搜索——那正是落到 LOGO 的路径。吃掉按键。
        if (nav.hasFocus()) return true;
        bar.animate().translationY(0f).setDuration(DURATION).start();
        // T-188-b2（Round 42）：焦点是否真的落到导航项，决定这把 UP 算不算被消费。
        // 原来无条件 return true ⇒ 目标项当帧拿不到焦点时按键被吞，页面表现为"UP 完全无效"
        // （C beta26 在选台页连按 3 次卡在频道卡）。现在拿不到焦点就交回系统焦点搜索，
        // 最坏退化为 beta25 的"落邻项"，绝不会退化成"没反应"。
        boolean moved = nav.requestFocusItem(revealId);
        return moved;
    }

    /** 收起：T-156（拍板 50②，刘先生 beta7 反馈二/五.3：收放动画操作不顺 → 本期先做"常驻式置顶菜单"）：
     *  本期菜单常驻——conceal 不执行收起动画（T-188-b 起 canReveal 不再依赖 hidden，本方法仍是空实现；
     *  后期恢复收放/缩放动画时连同 canReveal 一起改回）。 */
    public void conceal() {
        // T-156：常驻置顶菜单（本期不做收起）
    }

    /** 显示（页面进入/内容滚回顶部时），不抢焦点 */
    public void show() {
        bar.animate().translationY(0f).setDuration(DURATION).start();
    }
}
