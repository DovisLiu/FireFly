package com.firefly.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.text.Editable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.App;
import com.firefly.tv.R;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Site;
import com.firefly.tv.bean.Word;
import com.firefly.tv.databinding.ActivitySearchBinding;
import com.firefly.tv.impl.Callback;
import com.firefly.tv.impl.SiteListener;
import com.firefly.tv.setting.Setting;
import com.firefly.tv.ui.adapter.RecordAdapter;
import com.firefly.tv.ui.adapter.WordAdapter;
import com.firefly.tv.ui.base.BaseActivity;
import com.firefly.tv.ui.custom.NavDelegate;
import com.firefly.tv.ui.custom.NavOverlay;
import com.firefly.tv.ui.custom.CustomKeyboard;
import com.firefly.tv.utils.Clock;
import com.firefly.tv.ui.custom.CustomTextListener;
import com.firefly.tv.ui.dialog.SiteDialog;
import com.firefly.tv.utils.KeyUtil;
import com.firefly.tv.utils.Util;
import com.firefly.tv.utils.ZhuToPin;
import com.github.catvod.net.OkHttp;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Response;

// T-210（扩围批准）：本页 MENU 呼出的片源弹窗调 SiteDialog，但原先未 implements SiteListener ⇒ 回调恒 null、
// 选完只 dismiss（与首页同一根因）。补接口＋补 setSite＋补落库留痕，三处入口口径一致。
public class SearchActivity extends BaseActivity implements SiteListener, WordAdapter.OnClickListener, RecordAdapter.OnClickListener, CustomKeyboard.Callback {

    private ActivitySearchBinding mBinding;
    private RecordAdapter mRecordAdapter;
    private WordAdapter mWordAdapter;
    private Clock mClock;
    private NavOverlay mOverlay;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SearchActivity.class));
    }

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    private String getKeyword() {
        String keyword = getIntent().getStringExtra("keyword");
        return keyword != null ? keyword : "";
    }

    private boolean empty() {
        return mBinding.keyword.getText().toString().trim().isEmpty();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mClock = Clock.create(mBinding.navBar.clock);
        mOverlay = NavDelegate.bind(this, mBinding.navBar.logo, mBinding.navBar.topNav, R.id.nav_search);
        mBinding.navBar.getRoot().post(() -> mOverlay.show());
        CustomKeyboard.init(this, mBinding);
        setRecyclerView();
        checkKeyword();
        onSearch();
    }

    @Override
    protected void initEvent() {
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onSearch();
            return true;
        });
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                getWord(s.toString());
            }
        });
        mBinding.mic.setOnClickListener(v -> mBinding.mic.start());
        mBinding.mic.setListener(this, new CustomTextListener() {
            @Override
            public void onResults(String result) {
                if (!result.isEmpty()) setKeyword(result);
                mBinding.keyword.requestFocus();
            }
        });
    }

    private void setRecyclerView() {
        mBinding.wordRecycler.setItemAnimator(null);
        mBinding.wordRecycler.setHasFixedSize(false);
        mBinding.wordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        mBinding.wordRecycler.setAdapter(mWordAdapter = new WordAdapter(this));
        mBinding.recordRecycler.setHasFixedSize(false);
        mBinding.recordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        mBinding.recordRecycler.setAdapter(mRecordAdapter = new RecordAdapter(this));
    }

    private void checkKeyword() {
        setKeyword(getKeyword());
        getWord(getKeyword());
    }

    private void setKeyword(String text) {
        mBinding.keyword.setText(text);
        mBinding.keyword.setSelection(text.length());
    }

    private void getWord(String text) {
        if (text.isEmpty()) getHot();
        else getSuggest(text);
    }

    private void getHot() {
        mBinding.word.setText(R.string.search_hot);
        mWordAdapter.setItems(Word.objectFrom(Setting.getHot()).getData());
        OkHttp.newCall("https://api.web.360kan.com/v1/rank?cat=1", Map.of(HttpHeaders.REFERER, "https://www.360kan.com/rank/general")).enqueue(getCallback(true));
    }

    private void getSuggest(String text) {
        mBinding.word.setText(R.string.search_suggest);
        OkHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + Uri.encode(ZhuToPin.get(text))).enqueue(getCallback(false));
    }

    private Callback getCallback(boolean hot) {
        return new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String result = response.body().string();
                if (TextUtils.isEmpty(result)) return;
                App.post(() -> setAdapter(result, hot));
            }
        };
    }

    private void setAdapter(String result, boolean save) {
        if (!save && empty()) return;
        if (save) Setting.putHot(result);
        mWordAdapter.setItems(Word.objectFrom(result).getData());
    }

    @Override
    public void onItemClick(String text) {
        setKeyword(text);
        onSearch();
    }

    @Override
    public void onDataChanged(int size) {
        mBinding.recordLayout.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        if (size == 0) focusFirst(mBinding.wordRecycler);
    }

    @Override
    public void onSearch() {
        if (empty()) return;
        String keyword = mBinding.keyword.getText().toString().trim();
        App.post(() -> mRecordAdapter.add(keyword), 250);
        Util.hideKeyboard(mBinding.keyword);
        CollectActivity.start(this, keyword);
    }

    @Override
    public void showDialog() {
        SiteDialog.create().search().show(this);
    }

    /** T-210（扩围）：片源回调落库，与首页/点播页同一条留痕串，C 只靠 logcat 判「回调进来了」。 */
    @Override
    public void setSite(Site item) {
        Log.d("FireFlyHome", "site set by=Search keyLen=" + (item == null ? 0 : item.getKey().length()));
        VodConfig.get().setHome(item);
    }

    @Override
    public void onRemote() {
        PushActivity.start(this, 1);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // β4 / T-208：探针记账必须在 findFocus 之前（那一行会真的移动焦点，
        // 记的就是"移动前"的落点，ACTION_UP 才能判出这把 UP 有没有向上走）。
        mOverlay.onKeyDown(event, getCurrentFocus());
        if (KeyUtil.isMenuKey(event)) showDialog();
        // β18 / 规范 R3 第一批（A 派单：照首页 HomeActivity:751 的做法铺三页）：**UP 的落点在这把键的
        // ACTION_DOWN 阶段一次定死**——首排顶边直接抢本页导航项，不再交给系统几何搜索 ⇒ 一次键只有一个落点
        // （日志 `mode=up -> nav single`）；非首排（上方还有页内目标）不改派 ⇒ 页内上移一行的旧语义不变；
        // 四条触发条件、长按 repeat 护栏、顶边守卫与「抢不到焦点就不吃键」全部在 NavOverlay.revealOnDown() 一条口收口。
        if (mOverlay.revealOnDown(event, getCurrentFocus())) return true;
        if (KeyUtil.isActionDown(event) && findFocus(event)) return true;
        // 顶到边（输入框按 UP、上方无可聚焦节点）或被系统抢到顶栏别处 ⇒ 落本页导航项 nav_search
        int verdict = mOverlay.probeUp(event, getCurrentFocus());
        if (verdict == NavOverlay.UP_EAT) return true;
        if (verdict == NavOverlay.UP_REVEAL) return mOverlay.revealToPage();
        return super.dispatchKeyEvent(event);
    }

    private boolean findFocus(KeyEvent event) {
        View current = getCurrentFocus();
        if (current == mBinding.keyword) return handleKeywordKey(event);
        View inKeyboard = mBinding.keyboard.findContainingItemView(current);
        View inWord = mBinding.wordRecycler.findContainingItemView(current);
        View inRecord = mBinding.recordRecycler.findContainingItemView(current);
        if (inKeyboard != null) return handleKeyboardKey(event, inKeyboard);
        if (inRecord != null) return handleRecordKey(event, inRecord);
        if (inWord != null) return handleWordKey(event, inWord);
        return false;
    }

    private View findNearestInLastRow(RecyclerView rv, int targetLeft) {
        if (rv.getChildCount() == 0) return null;
        int lastTop = rv.getChildAt(rv.getChildCount() - 1).getTop();
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == lastTop) {
                int dist = Math.abs(child.getLeft() - targetLeft);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = child;
                }
            }
        }
        return nearest;
    }

    private boolean isFirstRow(RecyclerView rv, View item) {
        View first = rv.getChildAt(0);
        return first != null && item.getTop() == first.getTop();
    }

    private boolean isLastRow(RecyclerView rv, View item) {
        View last = rv.getChildAt(rv.getChildCount() - 1);
        return last != null && item.getTop() == last.getTop();
    }

    private boolean isFirstInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int left = focused.getLeft();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == top && child.getLeft() < left) return false;
        }
        return true;
    }

    private boolean isLastInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int right = focused.getRight();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == top && child.getRight() > right) return false;
        }
        return true;
    }

    private boolean handleKeywordKey(KeyEvent event) {
        // β4 / T-208：原 `if (isUpKey && canReveal()) return mOverlay.reveal()` 这条旧轨**删除**，
        // 输入框的 UP 顶边判定与其余页面同走共享探针（见 dispatchKeyEvent）。
        if (!KeyUtil.isRightKey(event)) return false;
        if (mBinding.keyword.getSelectionEnd() < mBinding.keyword.getText().length()) return false;
        boolean hasRecord = mBinding.recordLayout.getVisibility() == View.VISIBLE;
        return focusFirst(hasRecord ? mBinding.recordRecycler : mBinding.wordRecycler);
    }

    private boolean handleKeyboardKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.keyboard, item)) {
            // β18 / 规范 C1：键盘首行 UP 回输入框——**抢不到焦点就不吃键**（旧写法 requestFocus 后无条件 return true，
            // 抢焦失败时这把键被吞，表现＝按了没反应；现在放行默认路由，让系统去决定落点）
            boolean moved = mBinding.keyword.requestFocus();
            Log.d("FireFlyFocus", "focusMove from=" + viewName(item) + " to=keyword vis=" + moved
                    + " page=search mode=up -> keyword (keyboard first row)");
            return moved;
        }
        if (KeyUtil.isLeftKey(event) && isFirstInRow(mBinding.keyboard, item)) return true;
        return KeyUtil.isDownKey(event) && isLastRow(mBinding.keyboard, item);
    }

    private boolean handleWordKey(KeyEvent event, View item) {
        if (KeyUtil.isRightKey(event)) return isLastInRow(mBinding.wordRecycler, item);
        if (KeyUtil.isDownKey(event)) return isLastRow(mBinding.wordRecycler, item);
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.wordRecycler, item)) {
            if (mBinding.recordLayout.getVisibility() == View.VISIBLE) {
                View child = findNearestInLastRow(mBinding.recordRecycler, item.getLeft());
                if (child != null) {
                    mBinding.scroll.smoothScrollTo(0, 0);
                    // β18 / 规范 C1（第三处）：词库首行 UP 落历史记录——抢焦失败必须放行，不得无条件吃键
                    boolean moved = child.requestFocus();
                    Log.d("FireFlyFocus", "focusMove from=word to=record vis=" + moved + " page=search mode=up -> record (first row)");
                    return moved;
                }
            }
            return true;
        }
        return false;
    }

    private boolean handleRecordKey(KeyEvent event, View item) {
        if (KeyUtil.isRightKey(event)) return isLastInRow(mBinding.recordRecycler, item);
        if (KeyUtil.isUpKey(event)) return isFirstRow(mBinding.recordRecycler, item);
        if (KeyUtil.isDownKey(event) && isLastRow(mBinding.recordRecycler, item)) return focusFirst(mBinding.wordRecycler);
        return false;
    }

    private boolean focusFirst(RecyclerView rv) {
        View child = rv.getChildAt(0);
        if (child == null) return false;
        // β18 / 规范 C1：如实回传抢焦结果（旧写法只要 child 存在就 return true，抢焦失败照样吃键）
        return child.requestFocus();
    }

    /** β18：只用于日志的视图名（与 CollectActivity.viewName 同一写法，不参与任何判定）。 */
    private String viewName(View v) {
        if (v == null || v.getId() == View.NO_ID) return "none";
        try {
            return getResources().getResourceEntryName(v.getId());
        } catch (Throwable t) {
            return "noid";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
        mBinding.mic.setFocusable(true);
        mBinding.keyword.requestFocus();
    }

    // β44 / B1（A 定稿四点之一）：本页从导航「搜索」进入＝一级页 ⇒ BACK 统一回首页，
    // 不再用基类 finish 沿任务栈掉回"上一个实例"（那可能是点播或直播）。
    @Override
    protected void onBackInvoked() {
        backToHome();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
        mBinding.mic.setFocusable(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBinding.mic.destroy();
    }
}
