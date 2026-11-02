package com.firefly.tv.ui.activity;

import android.app.Activity;
import org.json.JSONObject;
import com.firefly.tv.utils.Task;
import android.content.Intent;
import android.util.Log;
import android.view.ViewGroup;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.BuildConfig;
import com.firefly.tv.R;
import com.firefly.tv.App;
import com.firefly.tv.Updater;
import com.firefly.tv.api.config.LiveConfig;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Config;
import com.firefly.tv.bean.Live;
import com.firefly.tv.bean.Site;
import com.firefly.tv.databinding.ActivitySettingBinding;
import com.firefly.tv.db.AppDatabase;
import com.firefly.tv.event.ConfigEvent;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.impl.BufferListener;
import com.firefly.tv.impl.Callback;
import com.firefly.tv.impl.ConfigListener;
import com.firefly.tv.impl.DanmakuListener;
import com.firefly.tv.impl.LiveListener;
import com.firefly.tv.impl.SiteListener;
import com.firefly.tv.impl.UaListener;
import com.firefly.tv.player.mpv.MpvUtil;
import com.firefly.tv.setting.DanmakuSetting;
import com.firefly.tv.setting.LiveSetting;
import com.firefly.tv.setting.PlayerSetting;
import com.firefly.tv.setting.Setting;
import com.firefly.tv.ui.custom.NavDelegate;
import com.firefly.tv.ui.custom.NavOverlay;
import com.firefly.tv.ui.base.BaseActivity;
import com.firefly.tv.utils.Clock;
import com.firefly.tv.ui.dialog.BufferDialog;
import com.firefly.tv.ui.dialog.ConfigDialog;
import com.firefly.tv.ui.dialog.DanmakuApiDialog;
import com.firefly.tv.ui.dialog.DohDialog;
import com.firefly.tv.ui.dialog.HistoryDialog;
import com.firefly.tv.ui.dialog.LiveDialog;
import com.firefly.tv.ui.dialog.MpvConfDialog;
import com.firefly.tv.ui.dialog.RestoreDialog;
import com.firefly.tv.ui.dialog.SiteDialog;
import com.firefly.tv.ui.dialog.UaDialog;
import com.firefly.tv.utils.FileUtil;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.PermissionUtil;
import com.firefly.tv.utils.ResUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import android.os.Looper;
import android.os.Handler;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingActivity extends BaseActivity implements ConfigListener, SiteListener, LiveListener, DohDialog.Listener, DanmakuListener, UaListener, BufferListener {

    private ActivitySettingBinding mBinding;
    private Clock mClock;
    private NavOverlay mOverlay;
    private String[] engine, render, scale, size;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingActivity.class));
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mClock = Clock.create(mBinding.navBar.clock);
        mOverlay = NavDelegate.bind(this, mBinding.navBar.logo, mBinding.navBar.topNav, R.id.nav_setting);
        mBinding.navBar.getRoot().post(() -> mOverlay.show());
        bindRevealKeys();
        mBinding.catVod.setSelected(true);
        showPane(mBinding.catVod);
        mBinding.catVod.requestFocus();
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        mBinding.versionText.setText(BuildConfig.VERSION_NAME);
        setCacheText();
        setOtherText();
        mBinding.previewText.setText(Setting.getSwitch(LiveSetting.isPreview()));
        // β19 / T-313：点播预览开关的初始文字（与直播侧同一渲染写法）
        mBinding.vodPreviewText.setText(Setting.getSwitch(Setting.isVodPreview()));
        // T-145（spec v1.10 拍板 45）：右栏 pane 明细直出的初始值（与原子页文本同源）
        setPaneDetailTexts();
    }

    // TV 2.0 两栏范式（spec §3.8）：左分类切换右侧明细面板
    /**
     * T-207（β3）：设置页**唯一**焦点路由。本轮只接设置页——首页与详情页的焦点行为一行未改（回归底线）。
     * 取代原先的三轨并存：「pane 内 xml 零连线（仅 paneDisplay 有 3 条 UP，其中 2 条自锁）＋ 左列 setOnKeyListener
     * ＋ NavOverlay 兜底」。裁决点只有一个，方向键在本页由本方法先行决定，不交给系统几何搜索。
     * 规则（A 定，逐条对应下方分支）：
     * ① 一级→二级：左列分类上按 RIGHT，必落该分类面板的**第一个可见行**（不再跳项）；
     * ② 二级内 UP：未到首行→上一可见行；已是首行→才出到顶部导航**「设置」**项（NavDelegate.bind 传的
     *    currentNavId=R.id.nav_setting ⇒ revealId 即 nav_setting，不会落到「片源」）；reveal 失败**吃键**，
     *    不放行系统搜索（原先放行会命中同水平带的 LOGO，是「落到无关项」的根因）；
     * ③ 二级内 DOWN：未到末行→下一可见行；已是末行→吃键（不外溢）；
     * ④ 左列 UP：同 ② 的出栈语义；
     * ⑤ beta5 / T-220 方案①：面板行按 LEFT 回**对应的**左列分类（categoryOfPane，映射唯一真相源＝showPane），
     *    排在 ①-④ 之后、仅当焦点属于当前面板可见行时生效；映射不到时只打 no-map 日志并放行默认路由，不改行为。
     * 行序来自容器子 View 的实际顺序 + 可见性过滤 ⇒ 运行时改行显隐（showPane / :481-490 / :663-669）不会失配。
     * 注意：xml 中 paneDisplay 遗留的 3 条 nextFocusUp / 2 条 nextFocusDown 已被本路由截获、不再生效，
     * 本批不删（删它们要动行尾闭合符、风险不对等），留待 β4 焦点批次与其余页面一起清。
     */
    private void bindRevealKeys() {
        // T-207：本方法保留为空壳（原实现是给 9 个 topRow 各挂一个 OnKeyListener，与下方路由重复）；
        // 保留签名与 initView 的调用点，是为了让本轮 diff 只增不改、可整块回滚。
    }

    private boolean isCategory(View v) {
        for (View cat : categoryRows()) if (cat == v) return true;
        return false;
    }

    private List<View> categoryRows() {
        List<View> out = new ArrayList<>();
        for (int i = 0; i < mBinding.categories.getChildCount(); i++) {
            View c = mBinding.categories.getChildAt(i);
            // β4 / T-218：必须再过滤 `isFocusable()`——`categories` 的第 0 个子 View 是标题「设置」
            // （activity_setting.xml:34-43，无 id、不可聚焦）。只按可见性过滤会让列表实得 8 项、
            // catVod 的下标变成 1 ⇒ "非首项上移"会把焦点交给标题（requestFocus 必返 false），
            // 首项反而永远进不到导航「设置」——把一个已验收项改坏。7 个分类都走 SettingCategory
            // 样式（styles.xml:82-94 已 focusable=true），加这条过滤只剔掉那个标题。
            if (c.getVisibility() == View.VISIBLE && c.isFocusable()) out.add(c);
        }
        return out;
    }

    private ViewGroup currentPane() {
        ViewGroup[] panes = {mBinding.paneVod, mBinding.paneLive, mBinding.panePlayer, mBinding.paneDisplay, mBinding.paneDanmaku, mBinding.paneNetwork, mBinding.paneBackup};
        for (ViewGroup pane : panes) if (pane != null && pane.getVisibility() == View.VISIBLE) return pane;
        return null;
    }

    /** 当前面板的可见行（按 xml 出现顺序）；行容器是 LinearLayoutCompat，行内标题/值文字不参与焦点。 */
    private List<View> paneRows() {
        List<View> out = new ArrayList<>();
        ViewGroup pane = currentPane();
        if (pane == null) return out;
        for (int i = 0; i < pane.getChildCount(); i++) {
            View c = pane.getChildAt(i);
            if (c.getVisibility() == View.VISIBLE && c instanceof ViewGroup) out.add(c);
        }
        return out;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // β4 / T-218：左列 DOWN 的**观测探针**（只记日志、不改行为）——A 判据「DOWN 已逐项且末行被吃，保持现状」
        // 与「左列按键必须留 FireFlyFocus 日志」两条要同时成立，所以这里不接管 DOWN，只在抬起时比对落点。
        observeCategoryDown(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            View focus = getCurrentFocus();
            int code = event.getKeyCode();
            // β13 / T-224（A 裁定走方案 B：dispatch 路由＋日志）：焦点在导航「设置」上按 DOWN，必须落**左列第一项**。
            // 病根＝纯几何：`view_top_nav.xml` 里 6 颗胶囊一条 nextFocus 连线都没有（grep -c nextFocus ＝ 0），
            // 而 nav_setting 居中偏右、与右栏同一水平带 ⇒ 系统 FocusFinder 先命中右栏行。改前该键 0 条 FireFlyFocus（＝走系统的证据）。
            // 护栏：repeatCount==0（长按连发不算新一次移动，与全站探针同口径）；左列空则放行系统，绝不吃空键。
            if (code == KeyEvent.KEYCODE_DPAD_DOWN && event.getRepeatCount() == 0
                    && focus != null && focus.getId() == R.id.nav_setting) {
                List<View> cats = categoryRows();
                if (!cats.isEmpty()) {
                    View first = cats.get(0);
                    boolean moved = first.requestFocus();
                    logFocusMove(focus, moved ? first : null, 0, moved, "nav down -> cat first");
                    if (moved) return true;   // 左列的焦点监听会自动切面板（SA:406），这里不重复 showPane
                }
            }
            if (focus != null && code == KeyEvent.KEYCODE_DPAD_UP && isCategory(focus)) {
                // β4 / T-218（错在规则本身，不是子页未接管）：左列 UP 原先是**无条件 reveal**，
                // 于是 C 矩阵与刘先生都看到「从备份上翻直接到导航设置」
                // （`focusMove from=catBackup to=none row=-1 mode=cat up -> nav setting`）。
                // 现按 A 定的规则：非首项先在本列内逐项上移，**只有首项（点播）再 UP 才出到导航「设置」**；
                // 出导航失败时仍吃键，绝不放行系统搜索（那是落 LOGO / 落「片源」的路径）。
                List<View> cats = categoryRows();
                int idx = cats.indexOf(focus);
                if (idx > 0) {
                    View to = cats.get(idx - 1);
                    // T-304（审计 #7／清单 v2 §七-21）：原先把 requestFocus 写在 logFocusMove 的参数里、
                    // 随后无条件 return true ⇒ 抢焦失败时这半把 DOWN 既没被消费也没交回系统（按了没反应）。
                    // 现按 :218/:240/:253/:269 同口径：抢到才吃键、抢不到 return moved 放行默认路由。
                    boolean moved = to.requestFocus();
                    logFocusMove(focus, moved ? to : null, idx - 1, moved, "cat up");
                    return moved;
                }
                boolean moved = mOverlay.reveal();
                logFocusMove(focus, null, idx, moved, "cat first up -> nav setting");
                return moved;
            }
            if (focus != null && code == KeyEvent.KEYCODE_DPAD_RIGHT && isCategory(focus)) {
                List<View> rows = paneRows();
                if (!rows.isEmpty()) {
                    boolean moved = rows.get(0).requestFocus();
                    logFocusMove(focus, rows.get(0), 0, moved, "cat right -> pane first");
                    if (moved) return true;
                }
            }
            if (focus != null) {
                List<View> rows = paneRows();
                int idx = rows.indexOf(focus);
                if (idx >= 0) {
                    if (code == KeyEvent.KEYCODE_DPAD_UP) {
                        if (idx == 0) {
                            // β13 / T-225（规则变更，A 裁定走方案 A）：右栏首行 UP **回到末行**（栏内循环）。
                            // 出导航从此只保留左列首项那一条出口（SA:202-204 `cat first up -> nav setting`）。
                            // 旧字面 `pane first up -> nav setting` 随本块下线 ⇒ C 复查期望 0 命中。
                            View last = rows.get(rows.size() - 1);
                            boolean moved = last.requestFocus();
                            logFocusMove(focus, moved ? last : null, rows.size() - 1, moved, "pane up wrap");
                            return moved;   // 与 β26 同口径：抢不到焦点就不吃键
                        }
                        View to = rows.get(idx - 1);
                        // T-304：同 `cat up`——requestFocus 移出日志参数，失败放行（mode= 串 pane up 不变）。
                        boolean moved = to.requestFocus();
                        logFocusMove(focus, moved ? to : null, idx - 1, moved, "pane up");
                        return moved;
                    }
                    if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (idx == rows.size() - 1) {
                            // β13 / T-225：末行 DOWN 由「吃键不回绕」改成**回绕首行**，与上面的 UP 成对构成栏内循环。
                            // 旧字面 `pane last down eaten` 随本块下线（C 复查期望 0 命中）。
                            View first = rows.get(0);
                            boolean moved = first.requestFocus();
                            logFocusMove(focus, moved ? first : null, 0, moved, "pane down wrap");
                            return moved;
                        }
                        View to = rows.get(idx + 1);
                        // T-304：同上（mode= 串 pane down 不变）。
                        boolean moved = to.requestFocus();
                        logFocusMove(focus, moved ? to : null, idx + 1, moved, "pane down");
                        return moved;
                    }
                    // beta5 / T-220（方案①）：右栏 → 左列必须回「对应的」分类。本分支排在 UP/RIGHT/面板 UP/面板 DOWN 之后，
                    // 且只有 idx >= 0（焦点属于当前面板可见行）才会走到这里 ⇒ 已验收的四判据不受影响。
                    if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                        View cat = categoryOfPane(currentPane());
                        if (cat == null) {           // 新面板忘了登记 ⇒ 只留痕，不改行为
                            logFocusMove(focus, null, -1, false, "pane left no-map");
                            return false;
                        }
                        boolean moved = cat.requestFocus();
                        logFocusMove(focus, moved ? cat : null, categoryRows().indexOf(cat), moved, "pane left -> cat");
                        return moved;                // 拿不到焦点就放行默认路由，绝不吃键
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * beta5 / T-220：面板 → 分类 的反查。成对关系的唯一真相源是 showPane() 里的逐条比较
     * （paneVod-catVod … paneBackup-catBackup），顺序与 currentPane() 的 panes[] 数组一致
     * ⇒ 不新增第二份清单，避免以后两处漂移。返回 null 表示「没登记」，由调用方只打日志、放行默认路由。
     *
     * 副作用如实记：焦点落到分类后，分类自身的 setOnFocusChangeListener 会调 showPane(cat)
     * ⇒ 右栏内容会跟着换，属既有设计（焦点驱动），不是缺陷。
     */
    private View categoryOfPane(ViewGroup pane) {
        if (pane == null) return null;
        if (pane == mBinding.paneVod) return mBinding.catVod;
        if (pane == mBinding.paneLive) return mBinding.catLive;
        if (pane == mBinding.panePlayer) return mBinding.catPlayer;
        if (pane == mBinding.paneDisplay) return mBinding.catDisplay;
        if (pane == mBinding.paneDanmaku) return mBinding.catDanmaku;
        if (pane == mBinding.paneNetwork) return mBinding.catNetwork;
        if (pane == mBinding.paneBackup) return mBinding.catBackup;
        return null;
    }


    private void logFocusMove(View from, View to, int row, boolean moved, String mode) {
        Log.d("FireFlyFocus", "focusMove from=" + viewName(from) + " to=" + viewName(to) + " row=" + row + " vis=" + moved + " mode=" + mode);
    }

    /**
     * β4 / T-218 要求 3：左列 DOWN 也要有 FireFlyFocus 留痕，但**行为一个字不改**（要求 2）。
     * 做法是观测而非接管：ACTION_DOWN（非长按）记下当前分类，抬起时比较焦点——
     * `to` 就是系统给的真实落点，末项吃不到东西时 `to=none vis=false`，C 可据此判「末行被吃」而不必猜。
     * 长按护栏：`repeatCount>0` 不记基线 ⇒ 连按不产日志、也不参与任何判定。
     */
    private void observeCategoryDown(KeyEvent event) {
        boolean isDownKey = event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN;
        if (event.getAction() == KeyEvent.ACTION_DOWN && isDownKey && event.getRepeatCount() == 0) {
            View focus = getCurrentFocus();
            mCatDownProbe = isCategory(focus) ? focus : null;
            return;
        }
        if (event.getAction() != KeyEvent.ACTION_UP || !isDownKey) return;
        View from = mCatDownProbe;
        mCatDownProbe = null;
        if (from == null) return;
        View to = getCurrentFocus();
        // β4 复验对账第 3 条（**只改观测，不改行为**）：旧写法拿"to 是否还在左列"当判据，
        // 于是焦点**根本没动**的末项 DOWN 也会打成 `to=catBackup vis=true`——等于把"起点"当"落点"报了出去。
        // 现按"有没有真的移动"分档：未移动 ⇒ `to=none vis=false row=-1`（C 的判据）；
        // 移到别的分类 ⇒ `to=catXxx row=<列内下标> vis=true`；移到右栏行 ⇒ `to=<行名> row=-1 vis=true`
        // （这里的 row=-1 如实表示"落点不在左列"，不是"没落位"）。
        boolean moved = to != from;
        int row = moved ? categoryRows().indexOf(to) : -1;
        // A 裁 3 的字面口径：`mode=cat down to=<真实落点>`；末项没有落点时 `to=none vis=false`，不写模糊值。
        logFocusMove(from, moved ? to : null, row, moved, "cat down");
    }

    private View mCatDownProbe;

    private String viewName(View v) {
        if (v == null || v.getId() == View.NO_ID) return "none";
        try {
            return getResources().getResourceEntryName(v.getId());
        } catch (Throwable t) {
            return "noid";
        }
    }

    private void showPane(View cat) {
        mBinding.paneVod.setVisibility(cat == mBinding.catVod ? View.VISIBLE : View.GONE);
        mBinding.paneLive.setVisibility(cat == mBinding.catLive ? View.VISIBLE : View.GONE);
        mBinding.panePlayer.setVisibility(cat == mBinding.catPlayer ? View.VISIBLE : View.GONE);
        mBinding.paneDisplay.setVisibility(cat == mBinding.catDisplay ? View.VISIBLE : View.GONE);
        mBinding.paneDanmaku.setVisibility(cat == mBinding.catDanmaku ? View.VISIBLE : View.GONE);
        // T-160（拍板 52）："网络"+"通用"合并为"通用设置"——原 paneGeneral/catGeneral 已删除，明细并入 paneNetwork
        mBinding.paneNetwork.setVisibility(cat == mBinding.catNetwork ? View.VISIBLE : View.GONE);
        mBinding.paneBackup.setVisibility(cat == mBinding.catBackup ? View.VISIBLE : View.GONE);
        mBinding.catVod.setSelected(cat == mBinding.catVod);
        mBinding.catLive.setSelected(cat == mBinding.catLive);
        mBinding.catPlayer.setSelected(cat == mBinding.catPlayer);
        mBinding.catDisplay.setSelected(cat == mBinding.catDisplay);
        mBinding.catDanmaku.setSelected(cat == mBinding.catDanmaku);
        mBinding.catNetwork.setSelected(cat == mBinding.catNetwork);
        mBinding.catBackup.setSelected(cat == mBinding.catBackup);
    }

    /**
     * β48 / L9（C 真机 beta75：内嵌子页里按 BACK#1 直接回 HomeActivity）：原实现里 OK 在分类行上是**空动作**——
     * pane 早在 `:474` 的 `onFocusChange → showPane` 就展开了，OK 只是把同一个 pane 再设一遍，焦点始终停在左列行，
     * 于是 {@link #onBackInvoked()} 的 `inPane` 判据恒假 ⇒ 这一把键被一级页出口（backToHome）接走。
     * ⇒ 按 A 的口径把"进子页"做实：OK 之后焦点交进该 pane 的首个可聚焦行，此后 BACK 才真的在 pane 内＝回左列分类行（＝回主页）。
     * 交不出去（空 pane／行不可聚焦）**不吞键**：留在分类行，BACK 仍按原一级页语义走。
     */
    private void onCategory(View cat) {
        showPane(cat);
        View row = firstPaneRow();
        boolean moved = row != null && row.requestFocus();
        Log.d("FireFlyFocus", "setting cat OK -> pane row cat=" + viewName(cat)
                + " row=" + viewName(row) + " moved=" + moved);
    }

    /** β48 / L9：当前 pane 的首个**可聚焦**行（`paneRows()` 只按可见性＋是容器过滤，这里补焦点条件）。 */
    private View firstPaneRow() {
        for (View row : paneRows()) if (row.isFocusable()) return row;
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
    }

    // β44 / B1 的原口径（在此保留、标注被实测推翻）：当时写"设置页＝一级页 ⇒ BACK 一律回首页；6 个子页属二级、
    // 未覆写＝基类 finish、天然回设置页"。β46 / L9（C 真机）证伪：显示/播放/弹幕这几类点 OK 只走 `showPane()`
    // （:352-368）做**同一 Activity 内**的可见性切换（T-145"明细直出"把原子页平铺进右栏），根本没有第二个
    // Activity ⇒ 用户在右栏里按 BACK 被一起带回首页。真正会跳独立 Activity 的只有「解码」(`:800`) 与
    // 「预加载」(`:796`) 两行，那两页未覆写＝基类 finish，仍然天然回设置页（本条不动它们）。
    @Override
    protected void onBackInvoked() {
        // β46 / L9：焦点在当前 pane 内 ⇒ BACK 先退回左列当前分类行（同页内退一层）；已在左列/导航 ⇒ 回首页。
        ViewGroup pane = currentPane();
        View focus = getCurrentFocus();
        View cat = pane == null ? null : selectedCategory();
        boolean inPane = pane != null && focus != null && cat != null && isDescendantOf(focus, pane);
        if (inPane) {
            boolean moved = cat.requestFocus();
            Log.d("FireFlyBack", "setting back -> category column moved=" + moved
                    + " cat=" + viewName(cat) + " from=" + viewName(focus));
            // 交不出去时**不吞键**：继续往下走一级页出口
            if (moved) return;
        }
        backToHome();
    }

    /** β46 / L9：左列里当前选中的分类行——`showPane()`（:361-367）用 setSelected 标它，故可直接读出。 */
    private View selectedCategory() {
        for (View c : categoryRows()) if (c.isSelected()) return c;
        return null;
    }

    private boolean isDescendantOf(View v, ViewGroup parent) {
        for (android.view.ViewParent p = v.getParent(); p != null; p = p.getParent()) {
            if (p == parent) return true;
        }
        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
    }

    private void setOtherText() {
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.incognitoText.setText(Setting.getSwitch(Setting.isIncognito()));
    }

    private void onDisplay(View view) {
        SettingDisplayActivity.start(this);
    }

    private void onAbout(View view) {
        Task.execute(() -> {
            String json;
            try {
                json = OkHttp.string("https://download.dovisliu.cn/firefly/about.json");
            } catch (Exception e) {
                postAbout("关于获取失败(网络): " + e.getMessage());
                return;
            }
            try {
                JSONObject object = new JSONObject(json);
                String msg = object.optString("name") + "\n" + object.optString("site") + "\n\n" + object.optString("disclaimer");
                new Handler(Looper.getMainLooper()).post(() -> showAboutDialog(msg));
            } catch (Exception e) {
                postAbout("关于解析失败: " + e.getMessage());
            }
        });
    }

    private void postAbout(String msg) {
        new Handler(Looper.getMainLooper()).post(() -> Notify.show(msg));
    }

    private void showAboutDialog(String msg) {
        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(this).setTitle(R.string.setting_about).setMessage(msg).setPositiveButton(android.R.string.ok, null).show();
        if (d.getButton(android.app.AlertDialog.BUTTON_POSITIVE) != null) d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).requestFocus(); // TV 焦点：落在确认按钮
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    @Override
    protected void initEvent() {
        // 左列分类：点击/聚焦即切换右侧明细（焦点驱动）
        // T-160（拍板 52）：catGeneral 已删除（原"通用"并入"通用设置"=catNetwork）
        for (View cat : new View[]{mBinding.catVod, mBinding.catLive, mBinding.catPlayer, mBinding.catDisplay, mBinding.catDanmaku, mBinding.catNetwork, mBinding.catBackup}) {
            cat.setOnClickListener(this::onCategory);
            cat.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) showPane((View) v);
            });
        }
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.export.setOnClickListener(this::onExport);
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.version.setOnClickListener(this::onVersion);
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.live.setOnLongClickListener(this::onLiveEdit);
        mBinding.liveHome.setOnClickListener(this::onLiveHome);
        mBinding.incognito.setOnClickListener(this::setIncognito);
        mBinding.about.setOnClickListener(this::onAbout);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        mBinding.preview.setOnClickListener(this::setPreview);
        mBinding.vodPreview.setOnClickListener(this::setVodPreview);   // β19 / T-313（与直播侧同一条接线）
        // T-145（spec v1.10 拍板 45）：右栏 pane 明细直出——点击行为与原子页同源
        mBinding.size.setOnClickListener(this::setSize);
        mBinding.appearance.setOnClickListener(this::onAppearance);
        mBinding.engine.setOnClickListener(this::setEngine);
        mBinding.decode.setOnClickListener(this::onDecodeSetting);
        mBinding.adblock.setOnClickListener(this::setAdblock);
        mBinding.mpvConf.setOnClickListener(this::onMpvConf);
        mBinding.mpvGpuNext.setOnClickListener(this::setMpvGpuNext);
        mBinding.mpvVulkan.setOnClickListener(this::setMpvVulkan);
        mBinding.render.setOnClickListener(this::setRender);
        mBinding.scale.setOnClickListener(this::setScale);
        mBinding.background.setOnClickListener(this::onBackground);
        mBinding.buffer.setOnClickListener(this::onBuffer);
        mBinding.preload.setOnClickListener(this::onPreloadSetting);
        mBinding.ua.setOnClickListener(this::onUa);
        mBinding.danmakuLoad.setOnClickListener(this::setDanmakuLoad);
        mBinding.danmakuApi.setOnClickListener(this::onDanmakuApi);
        mBinding.danmakuAuto.setOnClickListener(this::setDanmakuAuto);
        mBinding.danmakuSpider.setOnClickListener(this::setDanmakuSpider);
    }

    // 直播预览开关（spec §3.5：低配设备可关）
    private void setPreview(View view) {
        LiveSetting.putPreview(!LiveSetting.isPreview());
        mBinding.previewText.setText(Setting.getSwitch(LiveSetting.isPreview()));
    }

    /** β19 / T-308~313 批次里的 T-313：点播预览开关——逐句仿 setPreview（翻转＋重绘文字），不新增第二套状态机。 */
    private void setVodPreview(View view) {
        Setting.putVodPreview(!Setting.isVodPreview());
        mBinding.vodPreviewText.setText(Setting.getSwitch(Setting.isVodPreview()));
        Log.d("FireFlySetting", "vod preview toggle on=" + Setting.isVodPreview());
    }

    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) {
            PermissionUtil.requestFile(this, allGranted -> load(config));
        } else {
            load(config);
        }
    }

    private void load(Config config) {
        switch (config.getType()) {
            case 0:
                VodConfig.load(config, getCallback());
                break;
            case 1:
                LiveConfig.load(config, getCallback());
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(getActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                setCacheText();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    private void onVod(View view) {
        ConfigDialog.create().vod().show(this);
    }

    private void onLive(View view) {
        ConfigDialog.create().live().show(this);
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create().vod().edit().show(this);
        return true;
    }

    private boolean onLiveEdit(View view) {
        ConfigDialog.create().live().edit().show(this);
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create().action().show(this);
    }

    private void onLiveHome(View view) {
        LiveDialog.create().action().show(this);
    }

    private void onVodHistory(View view) {
        HistoryDialog.create().vod().show(this);
    }

    private void onLiveHistory(View view) {
        HistoryDialog.create().live().show(this);
    }

    private void onPlayer(View view) {
        SettingPlayerActivity.start(this);
    }

    private void onDanmaku(View view) {
        SettingDanmakuActivity.start(this);
    }

    // 连点版本行 1 次 = 检查正式版；3 秒内连点满 5 次 = 检查测试版（隐藏入口，平时不触发）
    private int versionClicks;
    private final Runnable versionTask = this::checkOfficialUpdate;

    private void onVersion(View view) {
        App.removeCallbacks(versionTask);
        versionClicks++;
        if (versionClicks >= 5) {
            versionClicks = 0;
            Updater.create().beta().force().start(this);
            return;
        }
        App.post(versionTask, 800);
    }

    private void checkOfficialUpdate() {
        versionClicks = 0;
        Updater.create().force().start(this);
    }

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
        mBinding.incognitoText.setText(Setting.getSwitch(Setting.isIncognito()));
    }

    private void setDoh(View view) {
        DohDialog.create().index(getDohIndex()).show(this);
    }

    @Override
    public void setDoh(Doh doh) {
        OkHttp.dns().setDoh(doh);
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
    }

    private void onCache(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                setCacheText();
            }
        });
    }

    private void onBackup(View view) {
        PermissionUtil.requestFile(this, allGranted -> AppDatabase.backup(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.backup_success);
            }

            @Override
            public void error() {
                Notify.show(R.string.backup_fail);
            }
        }));
    }

    // 导出：把 /sdcard/TV/ 下最新的备份文件通过系统分享发出（可发到流光/网盘/其他设备）
    private void onExport(View view) {
        PermissionUtil.requestFile(this, allGranted -> {
            File[] files = Path.tv().listFiles(f -> f.getName().startsWith("tv") && f.getName().endsWith(".bk.gz"));
            if (files == null || files.length == 0) {
                Notify.show(R.string.backup_export_none);
                return;
            }
            Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("*/*");
            send.putExtra(Intent.EXTRA_STREAM, FileUtil.getShareUri(files[0]));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.backup_export)));
        });
    }

    private void onRestore(View view) {
        PermissionUtil.requestFile(this, allGranted -> RestoreDialog.create().callback(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                setOtherText();
                initConfig();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }).show(this));
    }

    // ================= T-145（spec v1.10 拍板 45）：右栏 pane 明细直出 =================
    // 以下明细行与行为从原子页（SettingPlayerActivity / SettingDisplayActivity /
    // SettingDanmakuActivity）平铺到设置页右栏，字段口径与子页保持一致；子页保留为兼容入口。

    private void setPaneDetailTexts() {
        size = ResUtil.getStringArray(R.array.select_size);
        engine = ResUtil.getStringArray(R.array.select_engine);
        render = ResUtil.getStringArray(R.array.select_render);
        scale = ResUtil.getStringArray(R.array.select_scale);
        mBinding.sizeText.setText(size[PlayerSetting.getSize()]);
        mBinding.appearanceText.setText(ResUtil.getStringArray(R.array.select_appearance)[Setting.getThemeMode()]);
        mBinding.engineText.setText(engine[PlayerSetting.getEngine()]);
        mBinding.renderText.setText(render[PlayerSetting.getRender()]);
        mBinding.scaleText.setText(scale[PlayerSetting.getScale()]);
        mBinding.adblockText.setText(Setting.getSwitch(Setting.isAdblock()));
        mBinding.bufferText.setText(String.valueOf(PlayerSetting.getBuffer()));
        mBinding.mpvVulkanText.setText(Setting.getSwitch(PlayerSetting.isMpvVulkan()));
        mBinding.mpvGpuNextText.setText(Setting.getSwitch(PlayerSetting.isMpvGpuNext()));
        mBinding.backgroundText.setText(Setting.getSwitch(PlayerSetting.isBackgroundOn()));
        mBinding.danmakuLoadText.setText(Setting.getSwitch(DanmakuSetting.isLoad()));
        mBinding.danmakuApiText.setText(getApiStatus());
        mBinding.danmakuAutoText.setText(Setting.getSwitch(DanmakuSetting.isAuto()));
        mBinding.danmakuSpiderText.setText(Setting.getSwitch(DanmakuSetting.isSpiderFirst()));
        updatePlayerDetailVisibility();
        updateDanmakuVisibility();
    }

    /** 播放器明细可见性（与 SettingPlayerActivity.setVisible 同源：EXO 隐藏 mpv 项、Vulkan 仅 mpv+support 时显示） */
    private void updatePlayerDetailVisibility() {
        boolean exo = PlayerSetting.isExo();
        boolean vulkan = !exo && MpvUtil.isVulkanSupported();
        if (PlayerSetting.isBackgroundPiP()) PlayerSetting.putBackground(1);
        mBinding.mpvConf.setVisibility(exo ? View.GONE : View.VISIBLE);
        mBinding.mpvVulkan.setVisibility(vulkan ? View.VISIBLE : View.GONE);
        mBinding.mpvGpuNext.setVisibility(exo ? View.GONE : View.VISIBLE);
        mBinding.adblock.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.buffer.setVisibility(exo ? View.VISIBLE : View.GONE);
    }

    private void setEngine(View view) {
        int index = (PlayerSetting.getEngine() + 1) % engine.length;
        PlayerSetting.putEngine(index);
        setPlaybackModeText();
        updatePlayerDetailVisibility();
    }

    private void setPlaybackModeText() {
        engine = ResUtil.getStringArray(R.array.select_engine);
        render = ResUtil.getStringArray(R.array.select_render);
        mBinding.engineText.setText(engine[PlayerSetting.getEngine()]);
        mBinding.renderText.setText(render[PlayerSetting.getRender()]);
    }

    private void setRender(View view) {
        int index = (PlayerSetting.getRender() + 1) % render.length;
        PlayerSetting.putRender(index);
        setPlaybackModeText();
    }

    private void setScale(View view) {
        int index = (PlayerSetting.getScale() + 1) % scale.length;
        PlayerSetting.putScale(index);
        mBinding.scaleText.setText(scale[index]);
    }

    private void setAdblock(View view) {
        Setting.putAdblock(!Setting.isAdblock());
        mBinding.adblockText.setText(Setting.getSwitch(Setting.isAdblock()));
    }

    private void setMpvGpuNext(View view) {
        PlayerSetting.putMpvGpuNext(!PlayerSetting.isMpvGpuNext());
        mBinding.mpvGpuNextText.setText(Setting.getSwitch(PlayerSetting.isMpvGpuNext()));
    }

    private void setMpvVulkan(View view) {
        PlayerSetting.putMpvVulkan(!PlayerSetting.isMpvVulkan());
        mBinding.mpvVulkanText.setText(Setting.getSwitch(PlayerSetting.isMpvVulkan()));
    }

    private void onMpvConf(View view) {
        MpvConfDialog.show(this);
    }

    private void onBackground(View view) {
        PlayerSetting.putBackground(PlayerSetting.isBackgroundOn() ? 0 : 1);
        mBinding.backgroundText.setText(Setting.getSwitch(PlayerSetting.isBackgroundOn()));
    }

    private void onBuffer(View view) {
        BufferDialog.show(this);
    }

    @Override
    public void setBuffer(int times) {
        PlayerSetting.putBuffer(times);
        mBinding.bufferText.setText(String.valueOf(times));
    }

    private void onUa(View view) {
        UaDialog.show(this);
    }

    @Override
    public void setUa(String ua) {
        Setting.putUa(ua);
    }

    private void onPreloadSetting(View view) {
        SettingPreloadActivity.start(this);
    }

    private void onDecodeSetting(View view) {
        SettingDecodeActivity.start(this);
    }

    private void setSize(View view) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setting_size)
                .setSingleChoiceItems(size, PlayerSetting.getSize(), (dialog, which) -> {
                    PlayerSetting.putSize(which);
                    mBinding.sizeText.setText(size[which]);
                    RefreshEvent.size();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void onAppearance(View view) {
        // T-173（C beta14：外观模式对话框 ListView 焦点 DOWN 直接跳"取消"、列表内选不了浅色）：
        // AlertDialog 默认单选列表在 TV 上列表项不可聚焦；改自定义 adapter（项 focusable + selector_item 背景），
        // 列表内 UP/DOWN 正常移动、OK 选中；跟随系统/浅色/深色/取消，选中后刷新文案
        String[] items = ResUtil.getStringArray(R.array.select_appearance);
        int checked = Setting.getThemeMode();
        androidx.appcompat.app.AlertDialog[] holder = new androidx.appcompat.app.AlertDialog[1];
        android.widget.BaseAdapter adapter = new android.widget.BaseAdapter() {
            @Override
            public int getCount() {
                return items.length;
            }

            @Override
            public Object getItem(int position) {
                return items[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                android.widget.TextView tv = (android.widget.TextView) android.view.LayoutInflater.from(SettingActivity.this)
                        .inflate(R.layout.dialog_check_item, parent, false);
                tv.setText(items[position]);
                tv.setSelected(position == checked);
                tv.setOnClickListener(v -> {
                    Setting.putThemeMode(position);
                    AppCompatDelegate.setDefaultNightMode(position == Setting.MODE_SYSTEM ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                            : position == Setting.MODE_LIGHT ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
                    mBinding.appearanceText.setText(ResUtil.getStringArray(R.array.select_appearance)[Setting.getThemeMode()]);
                    if (holder[0] != null) holder[0].dismiss();
                });
                return tv;
            }
        };
        holder[0] = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setting_appearance)
                .setAdapter(adapter, null)
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
        android.widget.ListView lv = holder[0].getListView();
        if (lv != null) lv.setItemsCanFocus(true);
    }

    private String getApiStatus() {
        return getString(TextUtils.isEmpty(DanmakuSetting.getEffectiveApiUrl()) ? R.string.none : R.string.yes);
    }

    private void onDanmakuApi(View view) {
        DanmakuApiDialog.show(this);
    }

    @Override
    public void setDanmakuApi(String url) {
        DanmakuSetting.putApiUrl(url);
        mBinding.danmakuApiText.setText(getApiStatus());
        updateDanmakuVisibility();
    }

    private void setDanmakuLoad(View view) {
        DanmakuSetting.putLoad(!DanmakuSetting.isLoad());
        mBinding.danmakuLoadText.setText(Setting.getSwitch(DanmakuSetting.isLoad()));
        updateDanmakuVisibility();
    }

    private void setDanmakuAuto(View view) {
        DanmakuSetting.putAuto(!DanmakuSetting.isAuto());
        mBinding.danmakuAutoText.setText(Setting.getSwitch(DanmakuSetting.isAuto()));
        updateDanmakuVisibility();
    }

    private void setDanmakuSpider(View view) {
        DanmakuSetting.putSpiderFirst(!DanmakuSetting.isSpiderFirst());
        mBinding.danmakuSpiderText.setText(Setting.getSwitch(DanmakuSetting.isSpiderFirst()));
    }

    /** 弹幕明细可见性（与 SettingDanmakuActivity 同源：未开启加载时不显示 API/自动/爬虫项） */
    private void updateDanmakuVisibility() {
        boolean load = DanmakuSetting.isLoad();
        boolean hasApi = !TextUtils.isEmpty(DanmakuSetting.getEffectiveApiUrl());
        mBinding.danmakuApi.setVisibility(load ? View.VISIBLE : View.GONE);
        mBinding.danmakuAuto.setVisibility(load && hasApi ? View.VISIBLE : View.GONE);
        mBinding.danmakuSpider.setVisibility(load && hasApi && DanmakuSetting.isAuto() ? View.VISIBLE : View.GONE);
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() != ConfigEvent.Type.COMMON) return;
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
    }

}
