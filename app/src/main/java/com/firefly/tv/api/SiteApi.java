package com.firefly.tv.api;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import com.firefly.tv.App;
import com.firefly.tv.R;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Class;
import com.firefly.tv.bean.Result;
import com.firefly.tv.bean.Site;
import com.firefly.tv.bean.Vod;
import com.firefly.tv.player.extractor.Source;
import com.firefly.tv.utils.PicLog;
import com.firefly.tv.utils.ResUtil;
import com.firefly.tv.utils.Sniffer;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Call;
import okhttp3.Response;

public class SiteApi {

    public static final String PUSH = "push_agent";

    /**
     * β51 / TV-04 补图节流口径 T1：一次 detail 请求最多带出去的 ids 条数（超出留到下一次触发）。
     * 取 20 的理由：单条 id 按 ≤16 字符估 ⇒ 约 340 字节 query，远离常见网关的 2KB 限制；
     * 一次返回 20 条完整字段在 TV 侧解析与内存成本最低。<b>该源首页实际条数／空图条数／这一腿实测耗时
     * 由 {@code pic fetch batch= empty= ids= filled= cost=} 这行日志在现场校准</b>——不在这里猜。
     */
    private static final int PIC_FETCH_MAX_IDS = 20;

    /**
     * β51 / TV-04 补图节流口径 T2：同一站点两次补图的最小间隔（毫秒）。
     * 锚定实测值＝首页整行的加载预算 {@code Constant.TIMEOUT_VOD} 也是 30 秒 ⇒ 取同值可保证
     * "上一次补图还可能在预算里时，同站不会排第二个"；来回切站、下拉刷新、快速翻页只补第一下。
     * 状态表进程内、<b>不持久化</b>（不落 SP/DB，免开配置面——A 批的"不加开关"）。
     */
    private static final long PIC_FETCH_INTERVAL_MS = 30_000L;

    /** β51 / TV-04：siteKey → 上次补图时刻（毫秒）。 */
    private static final Map<String, Long> picFetchAt = new ConcurrentHashMap<>();


    public static String call(@NonNull Site site, @NonNull ArrayMap<String, String> params) throws IOException {
        if (!site.getExt().isEmpty()) params.put("extend", site.getExt());
        Call call = site.getExt().length() <= 1000 ? OkHttp.newCall(site.getApi(), site.getHeader(), params) : OkHttp.newCall(site.getApi(), site.getHeader(), OkHttp.toBody(params));
        try (Response response = call.execute()) {
            return response.body().string();
        }
    }

    private static boolean isSpider(@NonNull Site site) {
        return site.getType() == 3;
    }

    private static String ac(int type) {
        return type == 0 ? "videolist" : "detail";
    }

    @NonNull
    public static Result homeContent(@NonNull Site site) throws Exception {
        if (isSpider(site)) {
            Spider spider = site.recent().spider();
            boolean crash = Prefers.getBoolean("crash");
            String home = crash ? "" : spider.homeContent(true);
            String video = crash ? "" : spider.homeVideoContent();
            Prefers.put("crash", false);
            SpiderDebug.log("home", home);
            SpiderDebug.log("homeVideo", video);
            Result result = Result.fromJson(home);
            List<Vod> list = Result.fromJson(video).getList();
            if (!list.isEmpty()) result.setList(list);
            setTypes(site, result);
            return result;
        } else if (site.getType() == 4) {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("filter", "true");
            String homeContent = call(site.fetchExt(), params);
            SpiderDebug.log("home", homeContent);
            Result result = Result.fromJson(homeContent);
            setTypes(site, result);
            return result;
        } else {
            try (Response response = OkHttp.newCall(site.getApi(), site.getHeader()).execute()) {
                String homeContent = response.body().string();
                SpiderDebug.log("home", homeContent);
                Result result = Result.fromType(site.getType(), homeContent);
                // β51 / TV-04：首页路径改为"逐条补空图"的入口；搜索路径仍走下面的原 fetchPic（A 决定本轮不放宽搜索）。
                fetchMissingPics(site, result);
                setTypes(site, result);
                return result;
            }
        }
    }

    @NonNull
    public static Result categoryContent(@NonNull String key, @NonNull String tid, @NonNull String page, boolean filter, @NonNull HashMap<String, String> extend) throws Exception {
        SpiderDebug.log("category", "key=%s,tid=%s,page=%s,filter=%s,extend=%s", key, tid, page, filter, extend);
        Site site = VodConfig.get().getSite(key);
        if (isSpider(site)) {
            String categoryContent = site.recent().spider().categoryContent(tid, page, filter, extend);
            SpiderDebug.log("category", categoryContent);
            return Result.fromJson(categoryContent);
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            if (site.getType() == 1 && !extend.isEmpty()) params.put("f", App.gson().toJson(extend));
            if (site.getType() == 4) params.put("ext", Util.base64(App.gson().toJson(extend), Util.URL_SAFE));
            params.put("ac", ac(site.getType()));
            params.put("t", tid);
            params.put("pg", page);
            String categoryContent = call(site, params);
            SpiderDebug.log("category", categoryContent);
            return Result.fromType(site.getType(), categoryContent);
        }
    }

    @NonNull
    public static Result detailContent(@NonNull String key, @NonNull String id) throws Exception {
        SpiderDebug.log("detail", "key=%s,id=%s", key, id);
        Site site = VodConfig.get().getSite(key);
        if (site.isEmpty() && PUSH.equals(key)) {
            Vod vod = new Vod();
            vod.setId(id);
            vod.setName(id);
            vod.setPlayUrl(id);
            vod.setPlayFrom(ResUtil.getString(R.string.push));
            vod.setPic(ResUtil.getString(R.string.push_image));
            Source.get().parse(vod.setFlags());
            return Result.vod(vod);
        } else if (isSpider(site)) {
            String detailContent = site.recent().spider().detailContent(Arrays.asList(id));
            SpiderDebug.log("detail", detailContent);
            Result result = Result.fromJson(detailContent);
            Source.get().parse(result.getVod().setFlags());
            return result;
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("ac", ac(site.getType()));
            params.put("ids", id);
            String detailContent = call(site, params);
            SpiderDebug.log("detail", detailContent);
            Result result = Result.fromType(site.getType(), detailContent);
            Source.get().parse(result.getVod().setFlags());
            return result;
        }
    }

    @NonNull
    public static Result playerContent(@NonNull String key, @NonNull String flag, @NonNull String id) throws Exception {
        SpiderDebug.log("player", "key=%s,flag=%s,id=%s", key, flag, id);
        Site site = VodConfig.get().getSite(key);
        Source.get().stop();
        if (site.getType() == 3) {
            String playerContent = site.recent().spider().playerContent(flag, id, VodConfig.get().getFlags());
            SpiderDebug.log("player", playerContent);
            Result result = Result.fromJson(playerContent);
            if (result.getFlag().isEmpty()) result.setFlag(flag);
            result.setUrl(Source.get().fetch(result));
            result.setHeader(site.getHeader());
            result.setKey(key);
            return result;
        } else if (site.getType() == 4) {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("play", id);
            params.put("flag", flag);
            String playerContent = call(site, params);
            SpiderDebug.log("player", playerContent);
            Result result = Result.fromJson(playerContent);
            if (result.getFlag().isEmpty()) result.setFlag(flag);
            result.setUrl(Source.get().fetch(result));
            result.setHeader(site.getHeader());
            result.setKey(key);
            return result;
        } else if (site.isEmpty() && "push_agent".equals(key)) {
            Result result = new Result();
            result.setUrl(id);
            result.setKey(key);
            result.setParse(0);
            result.setFlag(flag);
            result.setUrl(Source.get().fetch(result));
            SpiderDebug.log("player", result.toString());
            return result;
        } else {
            Result result = new Result();
            result.setUrl(id);
            result.setKey(key);
            result.setFlag(flag);
            result.setHeader(site.getHeader());
            result.setPlayUrl(site.getPlayUrl());
            result.setParse(Sniffer.isVideoFormat(id) && result.getPlayUrl().isEmpty() ? 0 : 1);
            result.setUrl(Source.get().fetch(result));
            SpiderDebug.log("player", result.toString());
            return result;
        }
    }

    @NonNull
    public static Result searchContent(@NonNull Site site, @NonNull String keyword, boolean quick, @NonNull String page) throws Exception {
        SpiderDebug.log("search", "site=%s,keyword=%s,quick=%s,page=%s", site.getName(), keyword, quick, page);
        boolean hasPage = !page.equals("1");
        if (isSpider(site)) {
            String searchContent = hasPage ? site.spider().searchContent(keyword, quick, page) : site.spider().searchContent(keyword, quick);
            SpiderDebug.log("search", searchContent);
            Result result = Result.fromJson(searchContent);
            for (Vod vod : result.getList()) vod.setSite(site);
            return result;
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("wd", keyword);
            params.put("quick", String.valueOf(quick));
            params.put("extend", "");
            if (hasPage) params.put("pg", page);
            String searchContent = call(site, params);
            SpiderDebug.log("search", searchContent);
            Result result = fetchPic(site, Result.fromType(site.getType(), searchContent));
            for (Vod vod : result.getList()) vod.setSite(site);
            return result;
        }
    }

    @NonNull
    public static Result action(@NonNull String key, @NonNull String action) throws Exception {
        Site site = VodConfig.get().getSite(key);
        SpiderDebug.log("action", "key=%s,action=%s", key, action);
        if (site.getType() == 3) return Result.fromJson(site.recent().spider().action(action));
        if (site.getType() == 4) return Result.fromJson(OkHttp.string(action));
        return Result.empty();
    }

    /**
     * β51 / TV-04：把首页列表里**没图的条目**补上图——仍是"一次批量 detail 请求"（现行实现本来就是批量，
     * 只是判据取错了对象），所以这次改的<b>不是请求条数，是触发面</b>。
     * <p>
     * 四处口径变更（A 已批，逐字对账见 {@code Test/evidence/round3-beta78/tv04-throttle-plan.md}）：
     * ①早退判据从"列表第 0 条有图就整批不补"改为<b>逐条看 pic</b>——老写法的 {@code result.getVod()} 就是
     * {@code getList().get(0)}，于是第 1 条往后的空图条目永远补不上（＝TV-04 缺陷本体）；
     * ②站点 categories 的第二道筛<b>放开</b>（不命中也进 ids），并<b>去掉</b>"全不命中就 {@code result.clear()}"
     * 这条整行清空路径（A 认下：不放开则某些片永远进不了 ids＝修不彻底）；
     * ③每批最多 {@link #PIC_FETCH_MAX_IDS} 条、同站 {@link #PIC_FETCH_INTERVAL_MS} 毫秒内只补一次；
     * ④<b>这一腿失败就地吞掉、返回原列表</b>：它与首页整行共用同一个 30 秒预算
     * （{@code ViewModelTaskRunner} 的 withTimeout 套在整个 callable 上，而本方法就在那个 callable 里），
     * 抛上去就不是"几张片没图"而是"整行推荐不出"；且失败<b>绝不写图片黑名单</b>
     * （列表接口失败 ≠ 图片 URL 失败，否则一次整站抖动能把全站图拉黑）。
     * <p>
     * 回填按 id、只补 pic、且仅在原值为空时补（{@link #fillPics}）——不再整表替换。
     * <p>
     * 回滚点：本方法与 {@link #fetchPic} 互不调用，删掉这里的调用点（{@code homeContent} 那一行改回
     * {@code fetchPic(site, result)}）即整块退回 beta77 行为。
     */
    @NonNull
    private static Result fetchMissingPics(@NonNull Site site, @NonNull Result result) {
        if (site.getType() > 2 || result.getList().isEmpty()) return result;
        List<Vod> missing = new ArrayList<>();
        for (Vod item : result.getList()) {
            // 源 JSON 里数组元素可以是 null（getList() 本身已兜空，元素没兜）——这个循环在 try 之外，
            // 一条 null 就能把异常送出整行，与口径④"补图不得让整行倒下"直接冲突，故显式跳过。
            if (item == null) continue;
            // 没有 id 的条目 detail 也补不动，不进 ids（老写法靠 categories 筛顺带跳过，这里显式判）
            if (item.getPic().isEmpty() && !item.getId().isEmpty()) missing.add(item);
        }
        if (missing.isEmpty()) return result;
        String key = site.getKey();
        long now = System.currentTimeMillis();
        Long last = picFetchAt.get(key);
        if (last != null && now - last < PIC_FETCH_INTERVAL_MS) {
            PicLog.throttleSkip(key, now - last);
            return result;
        }
        picFetchAt.put(key, now);
        int sent = Math.min(missing.size(), PIC_FETCH_MAX_IDS);
        List<Vod> batch = missing.subList(0, sent);
        ArrayList<String> ids = new ArrayList<>();
        for (Vod item : batch) ids.add(item.getId());
        String joined = TextUtils.join(",", ids);
        ArrayMap<String, String> params = new ArrayMap<>();
        params.put("ac", ac(site.getType()));
        params.put("ids", joined);
        long start = System.currentTimeMillis();
        try {
            String detail;
            try (Response response = OkHttp.newCall(site.getApi(), site.getHeader(), params).execute()) {
                detail = response.body().string();
            }
            int filled = fillPics(batch, Result.fromType(site.getType(), detail).getList());
            PicLog.fetch(key, result.getList().size(), missing.size(), sent, filled, System.currentTimeMillis() - start);
        } catch (Exception e) {
            // 口径④：只留痕、不抛、不入黑名单（列表接口失败 ≠ 图片 URL 失败）
            PicLog.fetchFail(site.getApi(), joined.length(), e.getClass().getSimpleName());
        }
        return result;
    }

    /**
     * β51 / TV-04：detail 结果<b>按 id 只回填 pic，且仅在原值为空时回填</b>。
     * <p>
     * 原实现是 {@code result.setList(detail.getList())} 整表替换，而 detail 返回的<b>顺序与条数都不保证与 ids 一致</b>
     * ⇒ 整表替换会重排首页列表、并把 detail 里缺席的那些条目直接丢掉（"显示的是别人的图／上一张"那类表现的温床）。
     * 逐条回填同时天然满足"不删缺席项"：detail 没返回的 id 原条目照旧留着。
     *
     * @return 实际被填上图的条数（detail 没带这些 id 时 &lt; 送出去的 ids 数，日志 {@code filled=} 就用来对这件事）
     */
    private static int fillPics(@NonNull List<Vod> target, @NonNull List<Vod> detail) {
        Map<String, String> picById = new HashMap<>();
        for (Vod item : detail) {
            if (item == null) continue;
            String id = item.getId();
            String pic = item.getPic();
            if (!id.isEmpty() && !pic.isEmpty()) picById.put(id, pic);
        }
        int filled = 0;
        for (Vod item : target) {
            if (!item.getPic().isEmpty()) continue;
            String pic = picById.get(item.getId());
            if (pic != null) {
                item.setPic(pic);
                filled += 1;
            }
        }
        return filled;
    }

    /**
     * β51 / TV-04 起本方法<b>只服务搜索路径</b>（{@code searchContent}）：A 决定本轮不放宽搜索的触发面
     * ——搜索列表通常更长，且与补图串形共享同一个 30 秒预算。首页路径见 {@link #fetchMissingPics}。
     */
    @NonNull
    public static Result fetchPic(@NonNull Site site, @NonNull Result result) throws Exception {
        if (site.getType() > 2 || result.getList().isEmpty() || !result.getVod().getPic().isEmpty()) return result;
        ArrayList<String> ids = new ArrayList<>();
        boolean empty = site.getCategories().isEmpty();
        for (Vod item : result.getList()) if (empty || site.getCategories().contains(item.getTypeName())) ids.add(item.getId());
        if (ids.isEmpty()) return result.clear();
        ArrayMap<String, String> params = new ArrayMap<>();
        params.put("ac", ac(site.getType()));
        params.put("ids", TextUtils.join(",", ids));
        try (Response response = OkHttp.newCall(site.getApi(), site.getHeader(), params).execute()) {
            result.setList(Result.fromType(site.getType(), response.body().string()).getList());
            return result;
        }
    }

    private static void setTypes(@NonNull Site site, @NonNull Result result) {
        result.getTypes().stream().filter(type -> result.getFilters().containsKey(type.getTypeId())).forEach(type -> type.setFilters(result.getFilters().get(type.getTypeId())));
        if (site.getCategories().isEmpty()) return;
        Map<String, Class> typeByName = new HashMap<>();
        result.getTypes().forEach(type -> typeByName.put(type.getTypeName(), type));
        List<Class> types = site.getCategories().stream().map(typeByName::get).filter(Objects::nonNull).toList();
        if (!types.isEmpty()) result.setTypes(types);
    }
}
