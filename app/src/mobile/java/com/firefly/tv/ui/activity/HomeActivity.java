package com.firefly.tv.ui.activity;


import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.App;
import com.firefly.tv.R;
import com.firefly.tv.Updater;
import com.firefly.tv.api.config.LiveConfig;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Config;
import com.firefly.tv.databinding.ActivityHomeBinding;
import com.firefly.tv.db.AppDatabase;
import com.firefly.tv.event.ConfigEvent;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.event.ServerEvent;
import com.firefly.tv.event.StateEvent;
import com.firefly.tv.NoticeManager;
import com.firefly.tv.impl.Callback;
import com.firefly.tv.player.extractor.Source;
import com.firefly.tv.receiver.ShortcutReceiver;
import com.firefly.tv.server.Server;
import com.firefly.tv.service.PlaybackService;
import com.firefly.tv.ui.base.BaseActivity;
import com.firefly.tv.ui.dialog.SiteDialog;
import com.firefly.tv.ui.custom.FragmentStateManager;
import com.firefly.tv.ui.fragment.HomeFragment;
import com.firefly.tv.ui.fragment.LiveSelectFragment;
import com.firefly.tv.ui.fragment.SettingDanmakuFragment;
import com.firefly.tv.ui.fragment.SettingDisplayFragment;
import com.firefly.tv.ui.fragment.SettingDecodeFragment;
import com.firefly.tv.ui.fragment.SettingFragment;
import com.firefly.tv.ui.fragment.SettingPlayerFragment;
import com.firefly.tv.ui.fragment.SettingPreloadFragment;
import com.firefly.tv.ui.fragment.VodFragment;
import com.firefly.tv.utils.FileChooser;
import com.firefly.tv.utils.Notify;
import com.firefly.tv.utils.PermissionUtil;
import com.firefly.tv.utils.UrlUtil;
import com.firefly.tv.utils.Util;
import com.github.catvod.net.OkHttp;
import com.google.android.material.navigation.NavigationBarView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HomeActivity extends BaseActivity implements NavigationBarView.OnItemSelectedListener {

    private FragmentStateManager mManager;
    private ActivityHomeBinding mBinding;
    private NoticeManager mNotice;
    private final ActivityResultLauncher<Intent> localLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null)
            VideoActivity.file(this, FileChooser.getPathFromUri(result.getData().getData()));
    });
    private int orientation;

    // β85 第6条：选台页自挂底导后，"回宿主的某一档"用这个 extra 传**菜单 id**（不是 Fragment position），
    // 由 applyTab 走与手点同一套分派（vod/home/setting→change，local→openLocal）。
    public static final String EXTRA_TAB = "tab";

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkAction(intent);
        // β85 第6条：从选台页点底导其它档回来＝同一实例换档
        applyTab(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        orientation = getResources().getConfiguration().orientation;
        mBinding.splash.animate().alpha(0f).setStartDelay(600).setDuration(400).withEndAction(() -> mBinding.splash.setVisibility(View.GONE)).start();
        mBinding.navigation.setOnItemSelectedListener(this);
        PermissionUtil.requestNotify(this);
        initFragment(savedInstanceState);
        Updater.create().start(this);
        mNotice = NoticeManager.create();
        mNotice.check(this);
        initConfig();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mNotice.recheck(this);
    }

    @Override
    protected void initEvent() {
        mBinding.navigation.findViewById(R.id.live).setOnLongClickListener(this::addShortcut);
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            PermissionUtil.requestFile(this, allGranted -> checkType(intent));
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        }
    }

    private void checkType(Intent intent) {
        if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
            loadLive("file:/" + FileChooser.getPathFromUri(intent.getData()));
        } else {
            VideoActivity.push(this, intent.getData().toString());
        }
    }

    private void initFragment(Bundle savedInstanceState) {
        mManager = new FragmentStateManager(mBinding.container, getSupportFragmentManager(), position -> switch (position) {
            case 0 -> VodFragment.newInstance();
            case 1 -> SettingFragment.newInstance();
            case 2 -> SettingPlayerFragment.newInstance();
            case 3 -> SettingDanmakuFragment.newInstance();
            case 4 -> SettingPreloadFragment.newInstance();
            case 5 -> SettingDecodeFragment.newInstance();
            case 6 -> SettingDisplayFragment.newInstance();
            case 7 -> HomeFragment.newInstance();
            case 8 -> LiveSelectFragment.newInstance();
            default -> null;
        });
        // A 裁③：加首页后冷启落「首页」（position 7），不再落点播
        // β85 第6条：宿主已被系统回收时从选台页回档＝冷启也要落那一档，落不到才仍落首页
        if (savedInstanceState == null && !applyTab(getIntent())) change(7);
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                checkAction(getIntent());
            }

            @Override
            public void error(String msg) {
                checkAction(getIntent());
                StateEvent.empty();
                Notify.show(msg);
            }
        };
    }

    private void loadLive(String url) {
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                openLive();
            }
        });
    }

    private void setNavigation() {
        mBinding.navigation.getMenu().findItem(R.id.vod).setVisible(true);
        mBinding.navigation.getMenu().findItem(R.id.setting).setVisible(true);
        mBinding.navigation.getMenu().findItem(R.id.home).setVisible(true);
        mBinding.navigation.getMenu().findItem(R.id.live).setVisible(LiveConfig.hasUrl());
        // β28 批一：本行只换 id（local→source，＝片源弹层入口），取值一字不动＝行为不变（A 第三十四封：批一不裁行为）。
        // 该枚将来要不要随直播源隐藏＝批二待裁第 6 条（三档候选未裁，见设计文档〔十〕一 #6），本批不在此写口径、不写"已裁"。
        mBinding.navigation.getMenu().findItem(R.id.source).setVisible(true);
    }

    // β28 批一（A 裁①＝乙「任何档都能弹」、④＝甲「宿主＝当前页 Fragment」、②＝甲＋守卫）：
    // 底导「片源」那颗＝与首页左上角那颗同一条链（SiteDialog），只是入口换了位置。
    // 宿主取 getSupportFragmentManager().getPrimaryNavigationFragment()：实测全仓该 API 只有两处读写
    // （FragmentStateManager:33 读、:35 写），它就是现网「当前页」的唯一真值 ⇒ 零新增 getter、不动 main、
    // 且首页档换源后那三行重灌（HomeFragment.setSite 走 SiteDialog 第一层）不被搬空。
    // 换源动作本身＝只 VodConfig.setHome，各页下一次真取数才反映新主源（点播档唯一读点＝VodFragment:271，实测）。
    private void showSitePopup() {
        int prev = mBinding.navigation.getSelectedItemId();
        androidx.fragment.app.Fragment current = getSupportFragmentManager().getPrimaryNavigationFragment();
        if (!(current instanceof androidx.fragment.app.Fragment)) {
            // 拿不到当前页＝这一弹压根没发生，与「弹了但没 listener」同档留痕，判据靠有无 show 串分辨
            android.util.Log.d("FireFlyHome", "site popup skip nav=host reason=none");
            return;
        }
        android.util.Log.d("FireFlyHome", "site popup show tab=" + current.getClass().getSimpleName() + " prev=" + prev);
        SiteDialog.create().change().onDismiss(() -> restoreNav(prev)).show(current);
    }

    // 关闭弹层后把高亮退回原档（A 裁②＝甲＋守卫）。恢复走 MenuItem.setChecked(true) 那条不触发监听的路：
    // 本版实测链 MenuItemImpl.setChecked → MenuBuilder.setExclusiveItemChecked → setCheckedInt → onItemsChanged
    // → dispatchPresenterUpdate → NavigationBarPresenter.updateMenuView → NavigationBarMenuView.updateMenuView
    // →（体内按 isChecked 找到那颗）setCheckedItem；前提＝NavigationBarView 构造器实测调过 addMenuPresenter。
    // 守卫＝prev 就是 source 时不调任何恢复 API（没东西要恢复），但仍打 skip 留痕，避免被读成「漏调恢复」。
    private void restoreNav(int prev) {
        if (prev == R.id.source) {
            android.util.Log.d("FireFlyHome", "site popup skip nav=source reason=same");
            return;
        }
        MenuItem item = mBinding.navigation.getMenu().findItem(prev);
        if (item != null) item.setChecked(true);
        android.util.Log.d("FireFlyHome", "site popup restore nav=" + prev + " found=" + (item != null));
    }

    // β33 B1：删除态退态的根分发层统一判定（第四次修，不依赖容器冒泡，覆盖继续观看/收藏两行）。
    // 当前 primary fragment＝HomeFragment 且首页处于删除态且 ACTION_UP 时：未命中任何垃圾桶 icon→cancelDelete＋return true 消费该次点击；
    // 命中垃圾桶→交 super 走正常删除。非删除态/非 Home 页→原样 super（触摸/滚动/跳转路径不变）。β32 行拦截器/滚动容器监听保留作冗余。
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
            androidx.fragment.app.Fragment current = getSupportFragmentManager().getPrimaryNavigationFragment();
            if (current instanceof HomeFragment) {
                HomeFragment home = (HomeFragment) current;
                if (home.isInDeleteMode() && !home.isTapOnTrash(ev.getRawX(), ev.getRawY())) {
                    home.cancelDelete();
                    android.util.Log.d("FireFlyHome", "home delete cancel via=rootdispatch");
                    return true;
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean openLive() {
        // β29 批二（A 落码单接线①）：底导「直播」＝切 position 8 的选台 Fragment 并点亮（change 返回 void，壳层补 return true）。
        // 旧体 LiveSelectActivity.start(this) 随独立 Activity 一起删（A 裁①）；本方法还有 :183 BOOT loadLive 成功那条腿在调，
        // 保留壳、只换体，两个调用点同遍生效，不另立第三条路由。
        change(8);
        return true;
    }

    // β82 Q2＝乙：「本地」＝弹系统文件选择器（video/*）；入口同时挂底导与设置页那一行，两处同行为。
    public void openLocal() {
        FileChooser.from(localLauncher).show("video/*", new String[]{"video/*"});
    }

    private boolean addShortcut(View view) {
        ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(this, getString(R.string.nav_live)).setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher)).setIntent(new Intent(Intent.ACTION_VIEW, null, this, LiveActivity.class)).setShortLabel(getString(R.string.nav_live)).build();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(this, ShortcutReceiver.class).setAction(ShortcutReceiver.ACTION), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ShortcutManagerCompat.requestPinShortcut(this, info, pendingIntent.getIntentSender());
        return true;
    }

    // β85 第6条：底导菜单 id → 宿主动作，与 onNavigationItemSelected 同一条分派，不复制路由表。
    // 返回是否消费了这个 extra（消费后从 intent 上摘掉，避免旋转/再次 onNewIntent 重复触发）。
    // β28 批一起：这条分派不再认 R.id.local（底导已无该项，旧支「local→openLocal」随之删除）；
    // 已知限制＝选台页 goHomeTab(R.id.local) 投过来的值现在落到下面那个三元式的 else＝change(7)＝回首页（不再是弹系统选择器），
    // 选台页那枚图标仍亮仍不可用；收口在批二（LiveSelectActivity 换 id＋底导 Fragment 化一起）。
    // 〔本轮订正，A 收回「选台页侧不改」〕上面那句「仍亮仍不可用」低估了后果、标作废不抹：实测＝那枚在本页可见且可点，
    // 点它 goHomeTab(R.id.source) 投进本三元式的 else＝change(7)＝跳回首页档（是「跳去另一个页」，不是「不可用」；:97 那行 visible 是本批落的）。
    // 现由 LiveSelectActivity 自己接住 source，不再往这里投值；本三元式的 else 仍不认任何未知 id——
    // 要不要给它补一条「带值留痕」的日志（规则九十二：兜底不许静默改道）属清单外，本轮未动，单列交回待 A 裁。
    // 〔本遍 A 定档〕上面那句「本轮未动、待裁」已作废、旧句不抹：A 裁「兜底不许静默改道要留痕带值」⇒ 本遍已补，见 :261 那条串。
    private boolean applyTab(Intent intent) {
        int id = intent.getIntExtra(EXTRA_TAB, 0);
        if (id == 0) return false;
        intent.removeExtra(EXTRA_TAB);
        // β28 批一补漏（A 定档第三处）：三元式没有的 id 仍落 else＝change(7) 回首页档，但不再无声改道——带原值留痕。
        // 判据：整串常量前缀 tab fallback extra= 本版源码 ≥1／β27 包内 0（变量尾是运行时值，不进包内判据，β83 同族）。
        // 这条同时是「搬家型改动要枚举全部会把旧值投进兜底的宿主」那条纪律的机器可读证据：谁投的、投了什么值，日志里看得见。
        // 排除 R.id.home：home 是三元式 else 的正常档（position 7），不是兜底误道，不能报警。
        // β28 批一（A 第十七封第三条路）：home 显式写进三元式 ⇒ else 从此是「真兜底」，不再靠一条排除句撑安静。
        // 行为等价已由本遍穷举真值表坐实（见 landing 日志〔3〕），pos 对六个取值逐项相同，本行不是重写路由。
        int pos = id == R.id.setting ? 1 : id == R.id.vod ? 0 : id == R.id.home ? 7 : id == R.id.live ? 8 : 7;
        // 留痕条件按 A 的意图改成对 id 显式判档，不是按 pos 判：home 的 pos 也是 7，只删「&& id != R.id.home」
        // 会让每次正常回首页都打一条 fallback（比改前更吵）。要退回 A 的字面写法＝把本行改回 if (pos == 7)，一行，代价如上。
        // β29 批二：live 加进显式档（落码单接线③），else 只剩真未知 id。
        if (id != R.id.setting && id != R.id.vod && id != R.id.home && id != R.id.live) android.util.Log.d("FireFlyHome", "tab fallback extra=" + id);
        change(pos);
        return true;
    }

    // 一级页＝首页(7)／点播(0)／设置(1)／选台(8)：先切 Fragment，再点亮底导（底导项此刻可能还没 visible，
    // 只靠 setSelectedItemId 回调会切不过去，故两步都做；回调里走的是 mManager.change，不会递归）
    public void change(int position) {
        if (position == 0 || position == 1 || position == 7 || position == 8) {
            mManager.change(position);
            mBinding.navigation.setSelectedItemId(position == 0 ? R.id.vod : position == 1 ? R.id.setting : position == 8 ? R.id.live : R.id.home);
        } else mManager.change(position);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                RefreshEvent.home();
                break;
            case COMMON:
                setNavigation();
                break;
            case BOOT:
                // A 裁留痕：开机自启＝"接着看"场景，直达播放页不进选台页；
                // 首次起播失败由 LiveActivity 退到选台页（resetPlaybackForError／load error 两档）
                LiveActivity.startBoot(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.THEME) recreate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() == ServerEvent.Type.PUSH) VideoActivity.push(this, event.text());
        if (event.type() == ServerEvent.Type.SEARCH) SearchActivity.start(this, event.text());
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.home) return mManager.change(7);
        if (item.getItemId() == R.id.setting) return mManager.change(1);
        if (item.getItemId() == R.id.vod) return mManager.change(0);
        if (item.getItemId() == R.id.live) return mManager.change(8);
        // β82 那四行旧注释随本分支一起作废（原句保留在上面这条注释之外的台账与本块 replacement 记录里）：
        // β28 批一（刘先生「弹层时底导图标应亮」＋A 裁②＝甲＋守卫）＝底导「片源」那颗。
        // 本版实测高亮机制＝返回 true 才点亮（链路见 b28-batch1-design.md〔二〕1，两条独立印证：
        // 本方法三档监听体 return mManager.change(...) 而 FragmentStateManager.change 实测 return true ⇒ 那三档确实亮；
        // 旧「本地」档 return false ⇒ 不亮）。关闭后的恢复挂在 SiteDialog.onDismiss 这个统一出口上
        // ＝四条关闭途径（选了一项／空列表自动关／点项外区域／返回键）共用，不只挂在「选了一项」那条路上。
        if (item.getItemId() == R.id.source) {
            showSitePopup();
            return true;
        }
        return false;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        App.post(() -> checkOrientation(newConfig), 100);
    }

    private void checkOrientation(Configuration newConfig) {
        if (orientation != newConfig.orientation) {
            orientation = newConfig.orientation;
            RefreshEvent.home();
        }
    }

    @Override
    protected void onBackInvoked() {
        if (!mBinding.navigation.getMenu().findItem(R.id.vod).isVisible()) {
            setNavigation();
        } else if (mManager.isVisible(4) || mManager.isVisible(5)) {
            change(2);
        } else if (mManager.isVisible(6)) {
            change(1);
        } else if (mManager.isVisible(3) || mManager.isVisible(2)) {
            change(1);
        } else if (mManager.isVisible(8)) {
            // β29 批二（A 落码单裁②）：选台是一级页，BACK 回首页档；位置在 isVisible(1) 档之前（落码单原文）
            change(7);
        } else if (mManager.isVisible(1)) {
            // A 裁③：退出层级里的「点播首页」改为「首页」
            change(7);
        } else if (mManager.isVisible(7)) {
            // 首页＝一级页：先回顶部，已在顶部才退后台／退出；不新增 TV 那种弹框（A 裁原文）
            if (mManager.canBack(7)) onTopLevelBack();
        } else if (mManager.canBack(0)) {
            onTopLevelBack();
        }
    }

    private void onTopLevelBack() {
        if (PlaybackService.isRunning()) Util.moveToBackground(this);
        else super.onBackInvoked();
    }

    @Override
    protected void onDestroy() {
        LiveConfig.get().clear();
        VodConfig.get().clear();
        AppDatabase.backup();
        OkHttp.get().clear();
        Source.get().exit();
        Server.get().stop();
        super.onDestroy();
    }
}
