package com.firefly.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.firefly.tv.App;
import com.firefly.tv.R;
import com.firefly.tv.api.config.LiveConfig;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Class;
import com.firefly.tv.bean.Result;
import com.firefly.tv.bean.Site;
import com.firefly.tv.impl.SiteListener;
import com.firefly.tv.databinding.ActivityVodBinding;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.ui.adapter.TypeAdapter;
import com.firefly.tv.ui.base.BaseActivity;
import com.firefly.tv.ui.custom.CustomVerticalGridView;
import com.firefly.tv.ui.custom.NavOverlay;
import com.firefly.tv.ui.custom.TopNavView;
import com.firefly.tv.ui.custom.NavDelegate;
import com.firefly.tv.ui.dialog.SiteDialog;
import com.firefly.tv.ui.fragment.FolderFragment;
import com.firefly.tv.utils.Clock;
import com.firefly.tv.utils.KeyUtil;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.ResUtil;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;
import java.util.Optional;

// T-210（扩围批准）：本页导航「片源」项调 SiteDialog，但原先未 implements SiteListener ⇒ 回调恒 null、
// 点了只 dismiss（与首页同一根因）。补接口＋补 setSite＋补落库留痕，三处入口口径一致。
public class VodActivity extends BaseActivity implements SiteListener, TypeAdapter.OnClickListener, TopNavView.OnNavListener {

    private ActivityVodBinding mBinding;
    private TypeAdapter mAdapter;
    private View mOldView;
    private Clock mClock;
    private NavOverlay mOverlay;
    private boolean mCarryUpArmed;

    public static void start(Activity activity, Result result) {
        start(activity, VodConfig.get().getHome().getKey(), result, null);
    }

    public static void start(Activity activity, String key, Result result) {
        start(activity, key, result, null);
    }

    public static void start(Activity activity, Result result, String channel) {
        start(activity, VodConfig.get().getHome().getKey(), result, channel);
    }

    public static void start(Activity activity, String key, Result result, String channel) {
        start(activity, key, result, channel, -1);
    }

    /** 二级菜单直出链路：直接带分类序号进页（免同义词匹配） */
    public static void start(Activity activity, Result result, int typeIndex) {
        if (result == null || result.getTypes().isEmpty()) return;
        start(activity, VodConfig.get().getHome().getKey(), result, null, typeIndex);
    }

    private static void start(Activity activity, String key, Result result, String channel, int typeIndex) {
        if (result == null || result.getTypes().isEmpty()) return;
        Intent intent = new Intent(activity, VodActivity.class);
        intent.putExtra("key", key);
        intent.putExtra("result", result);
        intent.putExtra("channel", channel);
        intent.putExtra("typeIndex", typeIndex);
        activity.startActivity(intent);
    }

    /** 导航频道/二级直出定位：typeIndex 优先，其次频道名同义词匹配，回落第一个 */
    private int matchChannelIndex() {
        int typeIndex = getIntent().getIntExtra("typeIndex", -1);
        if (typeIndex >= 0) {
            List<Class> types = getResult().getTypes();
            return Math.min(typeIndex, types.size() - 1);
        }
        String channel = getIntent().getStringExtra("channel");
        if (channel == null || channel.isEmpty()) return 0;
        List<Class> types = getResult().getTypes();
        String[][] synonyms = {
                {"电影", "影片", "动作", "喜剧"},
                {"剧集", "电视剧", "连续剧", "美剧", "韩剧"},
                {"动漫", "动画", "番剧", "国漫"},
                {"综艺", "综艺", "真人秀"},
        };
        for (String[] group : synonyms) {
            boolean hit = false;
            for (String word : group) if (channel.contains(word)) hit = true;
            if (!hit) continue;
            for (String word : group) {
                for (int i = 0; i < types.size(); i++) {
                    if (types.get(i).getTypeName() != null && types.get(i).getTypeName().contains(word)) return i;
                }
            }
        }
        return 0;
    }

    private String getKey() {
        return getIntent().getStringExtra("key");
    }

    private Result getResult() {
        return getIntent().getParcelableExtra("result");
    }

    private Class getType() {
        return mAdapter.get(mBinding.pager.getCurrentItem());
    }

    /** β5 / T-219：只用于日志的视图名（取不到返回 none/noid，不参与任何判定）。 */
    private String viewName(View v) {
        if (v == null || v.getId() == View.NO_ID) return "none";
        try {
            return getResources().getResourceEntryName(v.getId());
        } catch (Throwable t) {
            return "noid";
        }
    }

    private FolderFragment getFragment() {
        return (FolderFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVodBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mClock = Clock.create(mBinding.navBar.clock);
        setLogo(mBinding.navBar.logo);
        mBinding.navBar.topNav.setListener(this);
        mBinding.navBar.topNav.setCurrent(R.id.nav_vod);
        setOverlay();
        setRecyclerView();
        setTypes();
        setPager();
        // 导航频道（电影/剧集/动漫/综艺）进入时匹配源分类并定位
        int index = matchChannelIndex();
        if (index > 0) {
            mBinding.recycler.setSelectedPosition(index);
            mBinding.pager.setCurrentItem(index);
        }
    }

    // 浮层导航（刘先生拍板）：焦点进内容上滑隐藏，内容顶行按上键呼出
    // Round 14 / T-135（拍板 41）：动画目标改为菜单层 navMenu，LOGO/时间（navStatic）常驻不动
    private void setOverlay() {
        mOverlay = new NavOverlay(mBinding.navBar.navMenu, mBinding.navBar.topNav, R.id.nav_vod);
        // β4 / T-208：探针法需要认出顶栏这一带（含 LOGO）⇒ 交顶栏根 + 页面名
        mOverlay.setToolbarRoot(mBinding.navBar.getRoot());
        mOverlay.setPage("vod");
        mOverlay.attach();
        mBinding.navBar.getRoot().post(() -> mOverlay.show());
    }

    private void setLogo(android.view.View logo) {
        // 品牌统一：静态横版 LOGO；点击 = 仓库（多仓配置）切换（§2.1）
        logo.setFocusable(true);
        logo.setFocusableInTouchMode(true);
        logo.setOnFocusChangeListener((v, hasFocus) -> v.animate().scaleX(hasFocus ? ResUtil.FOCUS_SCALE : 1f).scaleY(hasFocus ? ResUtil.FOCUS_SCALE : 1f).setDuration(150).start());
        // T-211 扩围（全站 LOGO 一致＝历史列表）：原先这里直接弹 ConfigDialog（仓库管理页，内含二维码），
        // 是 C 报「LOGO 点开是二维码」的第二处（第一处是 NavDelegate:79，已同批收口）。
        // 二维码能力未删，现由「设置→点播→点播」（SettingActivity:320）进入。
        logo.setOnClickListener(v -> NavDelegate.openHistory(this));
    }

    @Override
    protected void initEvent() {
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mBinding.recycler.setSelectedPosition(position);
                mBinding.recycler.requestFocus();
            }
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child);
            }
        });
    }

    private void setRecyclerView() {
        mBinding.recycler.requestFocus();
        mBinding.recycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.recycler.setAdapter(mAdapter = new TypeAdapter(this));
    }

    private void setTypes() {
        mAdapter.addAll(getResult().getTypes());
    }

    private void setPager() {
        mBinding.pager.setAdapter(new PageAdapter(getSupportFragmentManager()));
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child) {
        if (mOldView != null) mOldView.setSelected(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setSelected(true);
        App.post(mRunnable, 100);
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mBinding.pager.setCurrentItem(mBinding.recycler.getSelectedPosition());
        }
    };

    private boolean isFilterVisible() {
        return Optional.ofNullable(getType()).map(Class::getFilter).orElse(false);
    }

    private void updateFilter() {
        Optional.ofNullable(getType()).ifPresent(this::updateFilter);
    }

    private void updateFilter(Class item) {
        item.setFilter(!item.getFilter());
        getFragment().toggleFilter(item.getFilter());
        mAdapter.notifyItemRangeChanged(mAdapter.indexOf(item), 1);
    }

    public void closeFilter() {
        if (isFilterVisible()) updateFilter();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.CATEGORY) getFragment().onRefresh();
    }

    @Override
    public void onItemClick(Class item) {
        updateFilter(item);
    }

    @Override
    public void onRefresh(Class item) {
        getFragment().onRefresh();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // β4 / T-208：探针记账（见 NavOverlay#onKeyDown），不接管按键
        mOverlay.onKeyDown(event, getCurrentFocus());
        if (KeyUtil.isMenuKey(event)) updateFilter();
        // β4 / T-208（A 裁定 1）：上键顶边判定换成共享探针。旧守卫 `NavOverlay.isTopPosition(recycler)`
        // 对本页的 recycler（HorizontalGridView=分类栏）取的是**分类下标**，于是只有第 1 个分类能命中，
        // 第 2/5/9 个分类按 UP 全部落回系统几何搜索 ⇒「一次不返回／返回但不在点播」（刘先生实测）。
        // 现在：分类栏任意位置 UP 一次即落 nav_vod（顶到边=交导航；被系统抢到别处=纠正回本页项）；
        // 内容网格 UP 仍先回分类栏（焦点确实上移 ⇒ probeUp 判 UP_NONE ⇒ 放行默认路由）。
        // β5 / T-219 候选①：**页内"上一层优先"**，排在共享探针之前。
        // 根因（只读定位）：TypeFragment:155 把**本页的一级分类栏**注册成网格的 header
        // （`setHeader(activity, R.id.recycler)`），`CustomVerticalGridView:44-50` 在
        // `pressDown && position==1` 时调 `hideHeader()` ⇒ 用户一进内容，分类栏就被 `setVisibility(GONE)`
        // （:62-65）。此后从筛选行「全部」再按 UP，上方唯一目标已被本页藏掉 ⇒ 焦点原地不动 ⇒
        // 探针如实判 `edge-unchanged` ⇒ 直接出 `nav_vod`，**跳过了分类栏**（C 的实测现象）。
        // 修法：这一把 UP 先把分类栏显形、再把焦点交给它（一把键到位）；只有"分类栏已可见且仍无上方目标"
        // 才允许探针继续走"交导航"那条路。合集页同款（`CollectFragment:80` 注册了 result＋recycler）。
        // β39 / beta69 问题1（A 派单：点播页上翻越行）：**页内"上一行"优先**，排在上面的显形救援之前。
        // 旧顺序下这一把 UP 无论焦点在第几行都会先进救援分支（条件只看了 pager 有焦点＋分类栏不可见），
        // 而救援的 `requestFocus()` 在同帧刚显形时可能失败 ⇒ 焦点原地 ⇒ 探针判 edge-unchanged ⇒ 出导航。
        // 现在：焦点在内容网格内且不是首行 ⇒ 由网格把上一行请回布局并交焦（`CustomVerticalGridView#carryUp`）；
        // 首行（网格外层 position==0）不进这里，仍走下面的救援＋共享探针 ⇒ 三条体验一条不减：
        // 首行向上进导航／分类栏可回退／长按（repeatCount>0）不重复接管。
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && event.getRepeatCount() == 0) {
            CustomVerticalGridView grid = contentGrid(getCurrentFocus());
            if (grid != null && grid.carryUp()) {
                mCarryUpArmed = true;
                return true;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && mBinding.pager.hasFocus() && mBinding.recycler.getVisibility() != View.VISIBLE) {
            View from = mBinding.pager.findFocus();
            mBinding.recycler.setVisibility(View.VISIBLE);
            boolean moved = mBinding.recycler.requestFocus();
            if (!moved) {   // 同帧刚显形可能还没布局 ⇒ 与仓内既有兜底同法补一帧
                mBinding.recycler.post(() -> Log.d("FireFlyFocus", "focusMove from=noid to=recycler row=0 vis="
                        + mBinding.recycler.requestFocus() + " page=vod mode=up -> bar (header was hidden, posted)"));
            }
            Log.d("FireFlyFocus", "focusMove from=" + viewName(from) + " to=recycler row=0 vis=" + moved
                    + " page=vod mode=up -> bar (header was hidden)");
            return true;
        }
        // β39 / beta69 问题1 的配套：交焦是延帧动作（25ms 起、最多再重试 3 次），抬起往往比它先到。
        // 这一把 UP 的抬起一律吃掉，否则探针在焦点还没落到上一行时就按 edge-unchanged 出导航
        // ⇒ 导航显形与页内交焦撞在一起（观感＝"跳上去又跳回来"）。与直播页面板同款口径（β15 / T-222甲）。
        if (mCarryUpArmed && KeyUtil.isUpKey(event) && KeyUtil.isActionUp(event)) {
            mCarryUpArmed = false;
            Log.d("FireFlyFocus", "up carry=eaten on=up focus=" + (getCurrentFocus() == null ? "null" : getCurrentFocus().getId()));
            return true;
        }
        // 别的键按下 ⇒ 标记作废，避免泄漏到下一把 UP 上误吃键。
        if (KeyUtil.isActionDown(event) && !KeyUtil.isUpKey(event)) mCarryUpArmed = false;
        int verdict = mOverlay.probeUp(event, getCurrentFocus());
        if (verdict == NavOverlay.UP_EAT) return true;
        if (verdict == NavOverlay.UP_REVEAL) return mOverlay.revealToPage();
        return super.dispatchKeyEvent(event);
    }

    /** 焦点当前所在的内容网格（TypeFragment 的 {@code CustomVerticalGridView}）；焦点不在网格内时返回 null。 */
    private CustomVerticalGridView contentGrid(View view) {
        while (view != null) {
            if (view instanceof CustomVerticalGridView grid) return grid;
            if (!(view.getParent() instanceof View parent)) return null;
            view = parent;
        }
        return null;
    }

    // TV 2.0 顶部胶囊导航路由（TopNavView.OnNavListener，§3.2 点播页导航高亮=点播）
    @Override
    public void onNavHome() {
        finish();
    }

    @Override
    public void onNavSearch() {
        SearchActivity.start(this);
    }

    @Override
    public void onNavVod() {
        // 当前页：无跳转
    }

    @Override
    public void onNavLive() {
        if (LiveConfig.hasUrl()) LiveActivity.start(this);
        else Notify.show(getString(R.string.home_live));
    }

    @Override
    public void onNavSource() {
        SiteDialog.create().show(this);
    }

    /** T-210（扩围）：片源回调落库，与首页/搜索页同一条留痕串，C 只靠 logcat 判「回调进来了」。 */
    @Override
    public void setSite(Site item) {
        Log.d("FireFlyHome", "site set by=Vod keyLen=" + (item == null ? 0 : item.getKey().length()));
        VodConfig.get().setHome(item);
    }

    @Override
    public void onNavSetting() {
        SettingActivity.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
        // β8 / 取证 H1（零行为）：与 HomeActivity 的 `home key t=` 同一时钟源，比对就能看出
        // “失败那把 UP 的 DOWN”是落在本窗口（还没 pause）还是落在 Home（已 pause）——
        // ①-a 的判据是：DOWN 的时间戳早于本页 pause/leaving，且 Home 侧没有对应的 action=0。
        Log.d("FireFlyFocus", "vod leaving t=" + System.currentTimeMillis() + " phase=pause");
    }

    @Override
    protected void onBackInvoked() {
        // β8 / 取证 H1：BACK 命中“真退出”这一支时留痕（前两支是收筛选/回退分类，不离开本页）
        if (!isFilterVisible() && !getFragment().canBack()) {
            Log.d("FireFlyFocus", "vod leaving t=" + System.currentTimeMillis() + " phase=backFinish");
        }
        if (isFilterVisible()) updateFilter();
        else if (getFragment().canBack()) getFragment().goBack();
        // β44 / B1：一级页出口统一回首页（原 `super.onBackInvoked()`＝finish 沿栈往下掉，可能掉进直播页）。
        // 前两支（收筛选、回退分类子页）不动；上面那条 `phase=backFinish` 日志字面保留，不破坏 C 既有 grep。
        else backToHome();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Class type = mAdapter.get(position);
            return FolderFragment.newInstance(getKey(), type);
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }
    }
}
