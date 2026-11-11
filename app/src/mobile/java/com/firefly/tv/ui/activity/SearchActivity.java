package com.firefly.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.viewbinding.ViewBinding;

import com.firefly.tv.R;
import com.firefly.tv.databinding.ActivitySearchBinding;
import com.firefly.tv.ui.base.BaseActivity;
import com.firefly.tv.ui.fragment.SearchFragment;

public class SearchActivity extends BaseActivity {

    public static void start(Activity activity) {
        start(activity, "");
    }

    public static void start(Activity activity, String keyword) {
        start(activity, keyword, false);
    }

    /** β85 第10条（A 裁＝首页推荐也进搜索、返回要一拍回到进入前的页面）：
     *  jump＝本页只是"跳板"（由卡片点进来，不是用户主动搜索）。跳板型搜索页在进详情后收掉自己，
     *  详情页的返回就落回进入前的页面；用户主动搜索（首页搜索框／推送／长按片名）不带此标记，返回仍回结果列表。 */
    public static void start(Activity activity, String keyword, boolean jump) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra("keyword", keyword);
        intent.putExtra("jump", jump);
        activity.startActivity(intent);
    }

    /** 收页只在 jump＝true 时发生，且只收 SearchActivity 自己＝其他入口进详情一律不动。 */
    public static void finishIfJump(Activity activity) {
        if (activity instanceof SearchActivity && ((SearchActivity) activity).getIntent().getBooleanExtra("jump", false)) {
            activity.finish();
        }
    }

    /** β86（A 裁乙）：页内一旦出现用户主动触发的搜索就摘掉跳板标记，此后本页与五处不带标记的 start 完全同行为＝进详情不收页、返回回结果列表。
     *  触发通路实测两条＝键盘搜索键（fragment_search.xml 的 imeOptions=actionSearch）与点热词／历史词；布局里没有独立搜索钮，SiteDialog 那个 search() 只是选源模式标志、不触发搜索。
     *  removeExtra 幂等，所以「首次」不需要计数：摘过之后再摘是空操作，标记也不会自己回来。 */
    public static void clearJump(Activity activity) {
        if (activity instanceof SearchActivity) {
            activity.getIntent().removeExtra("jump");
        }
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    @Override
    protected ViewBinding getBinding() {
        return ActivitySearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.container, SearchFragment.newInstance(getKeyword()), SearchFragment.class.getSimpleName()).commit();
        }
    }

    @Override
    protected void onBackInvoked() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            super.onBackInvoked();
        }
    }
}
