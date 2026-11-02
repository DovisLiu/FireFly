package com.firefly.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.firefly.tv.R;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.DetailBg;
import com.firefly.tv.bean.DetailLog;
import com.firefly.tv.bean.Episode;
import com.firefly.tv.bean.Flag;
import com.firefly.tv.bean.History;
import com.firefly.tv.bean.Keep;
import com.firefly.tv.bean.Result;
import com.firefly.tv.bean.Site;
import com.firefly.tv.bean.Vod;
import com.firefly.tv.databinding.ActivityDetailBinding;
import com.firefly.tv.db.AppDatabase;
import com.firefly.tv.event.RefreshEvent;
import com.firefly.tv.model.SiteViewModel;
import com.firefly.tv.ui.adapter.EpisodeAdapter;
import com.firefly.tv.ui.adapter.FlagAdapter;
import com.firefly.tv.ui.adapter.QuickAdapter;
import com.firefly.tv.ui.base.BaseActivity;
import com.firefly.tv.ui.base.ViewType;
import com.firefly.tv.utils.ImgUtil;
import com.firefly.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 手机详情页（TV 2.0 §3.9 正式同步，竖屏大海报版）：
 * 海报在上部左侧，线路/选集纵排下方；两态——
 * 确认态（历史/搜索/收藏进入，已带源）⇄ 换源态（点播页进入或点"换源"，顶部源选择行）。
 * 选源后线路/选集同步新源；点集数=直接进播放（预览机制不做，背景恒为海报图，v1.6-28）。
 */
public class DetailActivity extends BaseActivity implements FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QuickAdapter.OnClickListener {

    private ActivityDetailBinding mBinding;
    private SiteViewModel mViewModel;
    private FlagAdapter mFlagAdapter;
    private EpisodeAdapter mEpisodeAdapter;
    private QuickAdapter mQuickAdapter;
    private Vod mVideo;
    private String mKey;
    private String mId;
    private String mName;
    private String mPic;
    private boolean mAutoSource;
    private boolean mCollect;
    private boolean mSourceMode;
    private final List<Vod> mSourceList = new ArrayList<>();
    private final Set<String> mSourceKeys = new HashSet<>();

    public static void confirm(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, false, false);
    }

    public static void confirm(Activity activity, String key, String id, String name, String pic, boolean collect) {
        start(activity, key, id, name, pic, false, collect);
    }

    /** 点播页进入=换源态（可直接挑源，交互与 TV 端一致） */
    public static void source(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, true, false);
    }

    private static void start(Activity activity, String key, String id, String name, String pic, boolean autoSource, boolean collect) {
        Intent intent = new Intent(activity, DetailActivity.class);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("autoSource", autoSource);
        intent.putExtra("collect", collect);
        activity.startActivity(intent);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityDetailBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mKey = getIntent().getStringExtra("key");
        mId = getIntent().getStringExtra("id");
        mName = getIntent().getStringExtra("name");
        mPic = getIntent().getStringExtra("pic");
        mAutoSource = getIntent().getBooleanExtra("autoSource", false);
        mCollect = getIntent().getBooleanExtra("collect", false);
        if (mName == null) mName = "";
        mBinding.progressLayout.showProgress();
        setRecyclerView();
        setViewModel();
        mViewModel.detailContent(mKey, mId);
        if (mAutoSource) enterSourceMode();
    }

    @Override
    protected void initEvent() {
        mBinding.play.setOnClickListener(v -> playCurrent());
        mBinding.keep.setOnClickListener(v -> toggleKeep());
    }

    private void setRecyclerView() {
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this, ViewType.GRID));
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, this::setDetail);
        mViewModel.getSearch().observe(this, this::onSearchObserved);
    }

    private void setDetail(Result result) {
        Vod vod = result == null ? null : result.getVod();
        if (vod == null || vod.getName().isEmpty()) {
            mBinding.progressLayout.showContent();
            Notify.show(getString(R.string.detail_error));
            return;
        }
        mBinding.progressLayout.showContent();
        mVideo = vod;
        bind();
    }

    private void bind() {
        Vod vod = mVideo;
        // T-149（拍板 47）：手机端同步（A 裁决 D-4=手机代码同批改）——背景大图走 getBgPic()（横版优先→竖版裁切），
        // 但手机详情页的 110×150 竖版海报位（mBinding.poster）**仍必须用竖版 vod_pic**，
        // 否则横版图被 centerCrop 塞进竖框会切掉人物；故 mPic 保持"背景图"语义、海报位单独取 getPic()。
        if (mPic == null || mPic.isEmpty()) mPic = vod.getBgPic();
        // β25 / T-333（A 裁决 D-4 同批改手机代码）：背景三级链＝slide 无条件优先 → 横向 pic（≥1.3 只解头探测）
        // → 竖版兑底；slide 加载失败⇒探测 pic 降级打 slideFail 行。海报位仍走 getPic()（上方 mPic 锁定语义一行未动）。
        // `pic pick` 行移交链回调（口径字面不变；链异步，bind 时刻定不了档位）。
        // β26 修 P0（与 TV 同源）：`getPicSlide()/getPic()` 可能为空，`ImgUtil.getUrl` 无空值防护 ⇒ 调用点先兜空。
        Object slideUrl = (vod.getPicSlide() == null || vod.getPicSlide().isEmpty()) ? null : com.firefly.tv.utils.ImgUtil.getUrl(vod.getPicSlide());
        Object picUrl = (vod.getPic() == null || vod.getPic().isEmpty()) ? null : com.firefly.tv.utils.ImgUtil.getUrl(vod.getPic());
        DetailBg.pickStart(this, vod, slideUrl, picUrl, (url, tag, ratio) -> {
            String u = url != null ? url : mPic;
            android.util.Log.d(DetailLog.TAG, "bg pick=" + tag + " ratio=" + ratio);
            DetailLog.pick(DetailLog.len(vod.getPicSlide()), DetailLog.len(vod.getPic()),
                    url == null ? "none" : (DetailBg.isWide(tag) ? "slide" : "pic"));
            loadBgPic(u, DetailBg.isWide(tag) && url != null, DetailBg.TIER_SLIDE.equals(tag) && url != null);
        });
        ImgUtil.load(mName, vod.getPic().isEmpty() ? mPic : vod.getPic(), mBinding.poster);
        mBinding.name.setText(vod.getName().isEmpty() ? mName : vod.getName());
        String score = vod.getRemarks();
        mBinding.score.setVisibility(score.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.score.setText(score);
        StringBuilder meta = new StringBuilder();
        if (!vod.getYear().isEmpty()) meta.append(vod.getYear()).append(" · ");
        if (!vod.getArea().isEmpty()) meta.append(vod.getArea()).append(" · ");
        if (!vod.getTypeName().isEmpty()) meta.append(vod.getTypeName());
        mBinding.meta.setText(meta.toString());
        mBinding.site.setText(getString(R.string.detail_site, getSite().getName()));
        // β39 / beta70 批 1 第 4 项：人员行（导演＋演员合并单行，规格与 meta／site 同档 13sp）。
        // 文案走 main 那对带参资源（zh-rCN 实测＝「导演：%s」／「演员：%s」）——注意 TV 在 leanback 源集
        // 把同名资源覆盖成了**无参版**，所以这里必须按带参形态调用，不能照抄 TV 的 getString 写法。
        // 两者皆空 ⇒ 整行 GONE 不留空白（同 TV β20/T-318）；" · " 只在两侧都非空时加，不留悬空分隔符。
        String director = vod.getDirector();
        String actor = vod.getActor();
        StringBuilder credits = new StringBuilder();
        if (!director.isEmpty()) credits.append(getString(R.string.detail_director, director));
        if (!actor.isEmpty()) {
            if (credits.length() > 0) credits.append(" · ");
            credits.append(getString(R.string.detail_actor, actor));
        }
        mBinding.credits.setText(credits.toString());
        mBinding.credits.setVisibility(credits.length() == 0 ? View.GONE : View.VISIBLE);
        // T-180（Round 37，与 TV 端同源同修，A 先例 D-4）：字面量 "&nbsp;" 换空格，不引 HTML 解析、不处理其它实体
        String content = vod.getContent().replace("&nbsp;", " ");
        boolean hasDesc = !vod.getContent().isEmpty();
        mBinding.desc.setText(content);
        mBinding.desc.setVisibility(hasDesc ? View.VISIBLE : View.GONE);
        // 简介弹层入口（A 派单第 4 项：手机简介原本纯展示不可点 ⇒ 让它可点）：点开＝人员全文＋简介全文。
        mBinding.desc.setClickable(hasDesc);
        mBinding.desc.setOnClickListener(hasDesc ? v -> showDescDialog() : null);
        // 无简介但有人员 ⇒ 入口接棒到 credits（TV β34/beta65 同口径；不加这条，无简介片就点不出弹层＝入口失效）。
        boolean creditsEntry = !hasDesc && credits.length() > 0;
        mBinding.credits.setClickable(creditsEntry);
        mBinding.credits.setOnClickListener(creditsEntry ? v -> showDescDialog() : null);
        android.util.Log.d(DetailLog.TAG, "desc entry=" + (hasDesc ? "desc" : (creditsEntry ? "credits" : "none"))
                + " descLen=" + content.length() + " creditsLen=" + credits.length());
        // 历史进度：当前集高亮（与 TV 端一致）
        History history = History.find(historyKey());
        String mark = history == null ? "" : history.getVodRemarks();
        if (!vod.getFlags().isEmpty()) {
            int flagIndex = 0;
            if (history != null && !history.getVodFlag().isEmpty()) {
                for (int i = 0; i < vod.getFlags().size(); i++) {
                    if (vod.getFlags().get(i).getShow().equals(history.getVodFlag())) {
                        flagIndex = i;
                        break;
                    }
                }
            }
            for (Flag flag : vod.getFlags()) {
                for (Episode episode : flag.getEpisodes()) episode.setSelected(false);
            }
            for (Episode episode : vod.getFlags().get(flagIndex).getEpisodes()) {
                if (episode.getName().equals(mark)) episode.setSelected(true);
            }
            mFlagAdapter.addAll(vod.getFlags());
            setEpisodes(vod.getFlags().get(flagIndex));
        } else {
            mFlagAdapter.addAll(new ArrayList<>());
            mEpisodeAdapter.addAll(new ArrayList<>());
        }
        checkKeep();
    }

    /**
     * β39 / beta70 批 1 第 4 项：手机侧简介弹层（改前手机详情页的简介是纯展示、不可点，也没有这个弹层）。
     * 拼接口径与 TV 的 {@code showDescDialog} 一致：人员段在前＋空一行＋简介全文；无人员不留前导空行，
     * 无简介（走 credits 那条入口）不留尾空行；两者皆空直接早退。人员串与页面那行同源
     * （{@code Vod#getDirector/#getActor}，getter 层已清洗 UGC 宏＝β54/T-332），不新增请求、不重排源侧分隔符。
     * 宿主用 {@code MaterialAlertDialogBuilder}（仓内既有直接建 builder 的同例见 HistoryActivity:77），
     * 因此它自动吃本轮新加的 {@code materialAlertDialogTheme}＝四角 16dp。
     */
    private void showDescDialog() {
        if (mVideo == null) return;
        String content = mVideo.getContent().replace("&nbsp;", " ");
        String director = mVideo.getDirector();
        String actor = mVideo.getActor();
        StringBuilder head = new StringBuilder();
        if (!director.isEmpty()) head.append(getString(R.string.detail_director, director));
        if (!actor.isEmpty()) {
            if (head.length() > 0) head.append(" · ");
            head.append(getString(R.string.detail_actor, actor));
        }
        if (content.isEmpty() && head.length() == 0) return;
        String full;
        if (head.length() == 0) full = content;
        else if (content.isEmpty()) full = head.toString();
        else full = head + "\n\n" + content;
        new MaterialAlertDialogBuilder(this).setTitle(mBinding.name.getText()).setMessage(full)
                .setPositiveButton(R.string.dialog_positive, null).show();
    }

    /** β25 / T-333：手机详情页背景图加载（档位口径同 TV 端；slideFail＝slide 首试失败的降级标记）。 */
    private void loadBgPic(String url, boolean wide, boolean slideFail) {
        if (url == null || url.isEmpty()) {
            mBinding.bg.setImageDrawable(null);
            return;
        }
        if (wide) mBinding.bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(this).load(com.firefly.tv.utils.ImgUtil.getUrl(url)).listener(new RequestListener<Drawable>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                if (slideFail) onSlideFailed(mVideo);
                return false;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                return false;
            }
        }).into(mBinding.bg);
    }

    /**
     * β25 / T-333：slide 首试加载失败⇒探测 vod_pic 降级（A 边界②），日志字面与 TV 端逐字一致：
     * `bg pick=slideFail->widePic|poster` ＋ `bg pick=… ratio=…`。降级请求不再二次降级（防循环）。
     */
    private void onSlideFailed(Vod vod) {
        if (vod == null) return;
        DetailBg.fallbackFromSlide(this, vod, com.firefly.tv.utils.ImgUtil.getUrl(vod.getPic()), (url, tag, ratio) -> {
            android.util.Log.d(DetailLog.TAG, "bg pick=slideFail->" + (url == null ? "poster" : tag));
            android.util.Log.d(DetailLog.TAG, "bg pick=" + tag + " ratio=" + ratio);
            DetailLog.pick(DetailLog.len(vod.getPicSlide()), DetailLog.len(vod.getPic()),
                    url == null ? "none" : (DetailBg.isWide(tag) ? "slide" : "pic"));
            loadBgPic(url != null ? url : mPic, url != null && DetailBg.isWide(tag), false);
        });
    }

    private void setEpisodes(Flag flag) {
        mEpisodeAdapter.addAll(flag.getEpisodes());
        int position = mEpisodeAdapter.getPosition();
        if (position > 0) mBinding.episode.scrollToPosition(position);
    }

    private Site getSite() {
        Site site = VodConfig.get().getHome();
        return site == null ? new Site() : site;
    }

    // ================= 两态（§3.9：确认态 ⇄ 换源态） =================

    private void enterSourceMode() {
        if (mSourceMode) return;
        List<Site> sites = VodConfig.get().getSites();
        if (sites == null || sites.size() <= 1 || mName.isEmpty()) {
            Notify.show(getString(R.string.play_change_none));
            return;
        }
        mSourceMode = true;
        mBinding.sourceTitle.setVisibility(View.VISIBLE);
        mBinding.quick.setVisibility(View.VISIBLE);
        mQuickAdapter.clear();
        mViewModel.searchContent(sites, mName, true);
    }

    private void exitSourceMode() {
        mSourceMode = false;
        mBinding.sourceTitle.setVisibility(View.GONE);
        mBinding.quick.setVisibility(View.GONE);
        mViewModel.stopSearch();
    }

    private void onSearchObserved(Result result) {
        if (!mSourceMode || result == null) return;
        for (Vod vod : result.getList()) {
            if (vod.getSite() == null || vod.getId() == null) continue;
            String uniq = vod.getSite().getKey() + "@" + vod.getId();
            if (!mSourceKeys.add(uniq)) continue;
            String vodName = vod.getName();
            if (vodName == null || !vodName.contains(mName) && !mName.contains(vodName)) continue;
            mSourceList.add(vod);
        }
        mQuickAdapter.addAll(mSourceList);
        mBinding.quick.setVisibility(mQuickAdapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
    }

    /** 换源：选源后立即同步新源线路/集数（v1.5-22），可直接点集数播放 */
    @Override
    public void onItemClick(Vod item) {
        if (item.getSite() == null || item.getId() == null) return;
        if (item.getSite().getKey().equals(mKey) && item.getId().equals(mId)) return;
        mKey = item.getSite().getKey();
        mId = item.getId();
        mName = item.getName();
        if (item.getPic() != null && !item.getPic().isEmpty() && (mPic == null || mPic.isEmpty())) mPic = item.getPic();
        mSourceList.clear();
        mSourceKeys.clear();
        mQuickAdapter.clear();
        mBinding.progressLayout.showProgress();
        mViewModel.detailContent(mKey, mId);
    }

    @Override
    public void onItemClick(Flag item) {
        // 行内切换线路：setSelected(Flag) 标记选中并接管选集，随后刷新选集区
        for (Flag flag : currentFlags()) flag.setSelected(item);
        mFlagAdapter.notifyDataSetChanged();
        setEpisodes(item);
    }

    @Override
    public void onItemClick(Episode item) {
        play(item);
    }

    private List<Flag> currentFlags() {
        return mVideo == null ? new ArrayList<>() : mVideo.getFlags();
    }

    private void playCurrent() {
        Episode target = null;
        if (!mEpisodeAdapter.isEmpty()) target = mEpisodeAdapter.getItems().get(mEpisodeAdapter.getPosition());
        play(target);
    }

    private void play(Episode episode) {
        if (episode == null) return;
        VideoActivity.start(this, mKey, mId, mName, mPic, episode.getName(), mCollect);
    }

    // ================= 收藏 =================

    private String historyKey() {
        return mKey.concat(AppDatabase.SYMBOL).concat(mId).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    private void toggleKeep() {
        if (mVideo == null) return;
        Keep keep = Keep.find(historyKey());
        if (keep != null) {
            keep.delete();
            RefreshEvent.keep();
        } else {
            keep = new Keep();
            keep.setKey(historyKey());
            keep.setCid(VodConfig.getCid());
            keep.setVodPic(mPic);
            keep.setVodName(mVideo.getName().isEmpty() ? mName : mVideo.getName());
            keep.setSiteName(getSite().getName());
            // β18 / T-213：手机侧收藏同样回填更新进度——两端口的是**同一张 Keep 表**，
            // 手机不写就会让 TV 首页收藏行这一列永远空（角标缺失跨端）
            keep.setVodRemarks(mVideo.getRemarks() == null ? "" : mVideo.getRemarks());
            keep.setCreateTime(System.currentTimeMillis());
            keep.save();
            RefreshEvent.keep();
        }
        checkKeep();
    }

    private void checkKeep() {
        boolean exist = Keep.find(historyKey()) != null;
        mBinding.keep.setImageResource(exist ? R.drawable.ic_control_keep_on : R.drawable.ic_action_keep);
    }

    @Override
    protected void onBackInvoked() {
        if (mSourceMode) {
            // 换源态返回=回确认态（锁定当前所选，§3.9 两态）
            exitSourceMode();
        } else {
            super.onBackInvoked();
        }
    }
}
