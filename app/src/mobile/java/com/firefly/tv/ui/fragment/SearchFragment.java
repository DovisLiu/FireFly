package com.firefly.tv.ui.fragment;

import static androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.viewbinding.ViewBinding;

import com.firefly.tv.App;
import com.firefly.tv.R;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Site;
import com.firefly.tv.bean.Word;
import com.firefly.tv.databinding.FragmentSearchBinding;
import com.firefly.tv.impl.Callback;
import com.firefly.tv.impl.SiteListener;
import com.firefly.tv.setting.Setting;
import com.firefly.tv.ui.activity.SearchActivity;
import com.firefly.tv.ui.adapter.RecordAdapter;
import com.firefly.tv.ui.adapter.WordAdapter;
import com.firefly.tv.ui.base.BaseFragment;
import com.firefly.tv.ui.custom.CustomTextListener;
import com.firefly.tv.ui.dialog.SiteDialog;
import com.firefly.tv.utils.ResUtil;
import com.firefly.tv.utils.Util;
import com.github.catvod.net.OkHttp;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import okhttp3.Call;
import okhttp3.Response;

public class SearchFragment extends BaseFragment implements MenuProvider, SiteListener, WordAdapter.OnClickListener, RecordAdapter.OnClickListener {

    private FragmentSearchBinding mBinding;
    private RecordAdapter mRecordAdapter;
    private WordAdapter mWordAdapter;

    public static SearchFragment newInstance(String keyword) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        SearchFragment fragment = new SearchFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return getArguments().getString("keyword");
    }

    private boolean empty() {
        return mBinding.keyword.getText().toString().trim().isEmpty();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initMenu() {
        if (isHidden()) return;
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.setSupportActionBar(mBinding.toolbar);
        activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        activity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        activity.setTitle("");
    }

    @Override
    protected void initView() {
        setRecyclerView();
        checkKeyword();
        search();
    }

    private void setRecyclerView() {
        mBinding.wordRecycler.setHasFixedSize(false);
        mBinding.wordRecycler.setAdapter(mWordAdapter = new WordAdapter(this));
        mBinding.wordRecycler.setLayoutManager(new FlexboxLayoutManager(getContext(), FlexDirection.ROW));
        mBinding.recordRecycler.setHasFixedSize(false);
        mBinding.recordRecycler.setAdapter(mRecordAdapter = new RecordAdapter(this));
        mBinding.recordRecycler.setLayoutManager(new FlexboxLayoutManager(getContext(), FlexDirection.ROW));
    }

    @Override
    protected void initEvent() {
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SearchActivity.clearJump(requireActivity());
                search();
            }
            return true;
        });
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                requireActivity().invalidateOptionsMenu();
                getWord(s.toString());
            }
        });
        getParentFragmentManager().setFragmentResultListener("result", getViewLifecycleOwner(), (requestKey, bundle) -> {
            if (bundle.getBoolean("edit", false)) Util.showKeyboard(mBinding.keyword);
        });
    }

    private void checkKeyword() {
        boolean visible = requireActivity().getSupportFragmentManager().findFragmentByTag(CollectFragment.class.getSimpleName()) != null;
        if (TextUtils.isEmpty(getKeyword()) && !visible) Util.showKeyboard(mBinding.keyword);
        setKeyword(getKeyword());
        getWord(getKeyword());
    }

    private void setKeyword(String text) {
        mBinding.keyword.setText(text);
        mBinding.keyword.setSelection(text.length());
    }

    private void search() {
        if (empty()) return;
        String keyword = mBinding.keyword.getText().toString().trim();
        App.post(() -> mRecordAdapter.add(keyword), 250);
        Util.hideKeyboard(mBinding.keyword);
        collect(keyword);
    }

    private void collect(String keyword) {
        FragmentManager fm = requireActivity().getSupportFragmentManager();
        String collectTag = CollectFragment.class.getSimpleName();
        if (fm.findFragmentByTag(collectTag) != null) return;
        String searchTag = SearchFragment.class.getSimpleName();
        FragmentTransaction ft = fm.beginTransaction().setTransition(TRANSIT_FRAGMENT_OPEN);
        ft.add(R.id.container, CollectFragment.newInstance(keyword), collectTag);
        Optional.ofNullable(fm.findFragmentByTag(searchTag)).ifPresent(ft::hide);
        ft.setReorderingAllowed(true).addToBackStack(null).commit();
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
        OkHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + Uri.encode(text)).enqueue(getCallback(false));
    }

    private Callback getCallback(boolean hot) {
        return new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String result = response.body().string();
                if (TextUtils.isEmpty(result)) return;
                App.post(() -> setWordAdapter(result, hot));
            }
        };
    }

    private void setWordAdapter(String result, boolean save) {
        if (!save && mBinding.keyword.getText().toString().trim().isEmpty()) return;
        mWordAdapter.setItems(Word.objectFrom(result).getData());
        if (save) Setting.putHot(result);
    }

    private void onReset() {
        mBinding.keyword.setText("");
        requireActivity().invalidateOptionsMenu();
    }

    private void onSite() {
        Util.hideKeyboard(mBinding.keyword);
        mBinding.keyword.post(() -> SiteDialog.create().search().show(this));
    }

    // β28 批一（A GO 封裁⑥＝进批一，清单外新增面已由 A 单列声明并给回退选项）：
    // 本类原先不 implements SiteListener ⇒ 左上角那枚换源入口选完源只关弹层、不换源、零日志（现网缺陷）。
    // 补的是第 4 个宿主，动作与点播页、设置页那两份逐字同构＝只写主源，不新建任何刷新机制；
    // 代价（A 裁①＝乙已登记的同类限制）＝换源后不当场反映新主源，要等该页下一次真取数才生效。
    // 回滚＝本方法、implements 里那一个接口名与三条 import 一起删（备份＝backup-b28b1）。
    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void onItemClick(String text) {
        SearchActivity.clearJump(requireActivity());
        setKeyword(text);
        search();
    }

    @Override
    public void onDataChanged(int size) {
        mBinding.record.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        mBinding.recordRecycler.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        mBinding.recordRecycler.postDelayed(() -> mBinding.recordRecycler.requestLayout(), 250);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.menu_search, menu);
        ResUtil.tintMenuIcons(requireActivity(), menu);
    }

    @Override
    public void onPrepareMenu(@NonNull Menu menu) {
        menu.findItem(R.id.action_reset).setVisible(!empty());
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) requireActivity().getOnBackPressedDispatcher().onBackPressed();
        if (menuItem.getItemId() == R.id.action_reset) onReset();
        if (menuItem.getItemId() == R.id.action_site) onSite();
        return true;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) requireActivity().removeMenuProvider(this);
        else initMenu();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireActivity().removeMenuProvider(this);
    }
}
