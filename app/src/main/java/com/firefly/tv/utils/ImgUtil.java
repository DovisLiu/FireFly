package com.firefly.tv.utils;

import static android.widget.ImageView.ScaleType.CENTER_CROP;
import static android.widget.ImageView.ScaleType.FIT_CENTER;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.load.HttpException;
import javax.net.ssl.SSLException;
import com.firefly.tv.App;
import com.firefly.tv.R;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.impl.CustomTarget;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;
import com.google.common.net.HttpHeaders;

import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jahirfiquitiva.libs.textdrawable.TextDrawable;

public class ImgUtil {

    /**
     * β51 / TV-04：失败图片黑名单由"永久 Set"改为**带窗口的退避表**（原实现在 114 台真机上表现为
     * "一次瞬断／403 ⇒ 这台设备这次进程内该条永远出色块，只有杀进程才恢复"）。
     * <p>
     * 三条设计约束（A 批的口径，逐字对账）：
     * ①<b>到期前零请求</b>——判据仍是"命中就不发 Glide 请求"（{@link #blocked}），TTL 只决定"何时允许再试一次"，
     * <b>不引入定时器、线程或后台轮询</b>；
     * ②重试只由<b>该卡被绑定</b>这一自然事件触发，没人看首页就不会重试；
     * ③窗口<b>基值 10 分钟、同一 URL 连续失败翻倍、上限 60 分钟、成功一次立即整条删除</b>
     * ⇒ 真下线的图稳态成本＝每小时最多一次请求，而不是 beta77 以前的"永久拉黑"，也不是"每次进首页都慢一遍"。
     * <p>
     * 表上限 {@value #PIC_FAIL_MAX_ENTRIES} 条 LRU（accessOrder）——原 {@code HashSet} <b>完全无上限</b>，
     * 长时间挂机＋搜索会一直涨。value 存**下次可重试的时刻**而不是首次失败时刻，读路径只需一次比较；
     * 翻倍在 {@link #rememberFail} 里于写入时算完，判读端零算术。
     */
    private static final long PIC_FAIL_WINDOW_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long PIC_FAIL_WINDOW_MAX_MS = TimeUnit.MINUTES.toMillis(60);
    private static final int PIC_FAIL_MAX_ENTRIES = 200;


    /**
     * β59 / TV-04 追加（A 批）：豆瓣图床互为镜像的可用域名池，顺序＝<b>img3 → img9 → img1 → img2</b>。
     * <p>
     * img2 <b>保留但放末位</b>（A 第二节原话：它是「当前会坏」不是「永远坏」，直接排除等于自断一路）。
     * 域名只出现在这一个常量里——将来别的镜像坏了或新增了，改这一行就够，逻辑不动。
     * 数据出处＝A 实测：同一张 {@code p2936709341.jpg} 带 Referer 时 img1／img2／img3／img9 均 200 且同为 25,757 字节，
     * img4~img8 域名不存在（000）。
     */
    private static final String[] PIC_MIRROR_HOSTS = {"img3.doubanio.com", "img9.doubanio.com", "img1.doubanio.com", "img2.doubanio.com"};

    /** β59：豆瓣图床域名后缀，判据只用这一处字面量，免得两处写歪。 */
    private static final String PIC_DOUBANIO_SUFFIX = ".doubanio.com";

    /**
     * β61 / TV-04（A 的需求原文：「把坏 host 记住，下次直接走镜像，不再白等」）：**坏域名表**。
     * <p>
     * 为什么非加不可（是实测出来的成本，不是设想）：β51/β84 的 {@code failed} 表按**图片 URL** 记，而坏的是**域名**——
     * 首页每刷一次片单就不一样，一条新的 img2 地址在表里没有任何记录 ⇒ 照样要先撞一次被对端重置的握手；
     * β83 真机实测那一撞要 **15～90 秒**（TLS 反复重试到超时）。这正是刘先生说的"稍等一会会自己出来，但每次进入都要等"。
     * ⇒ 所以缺陷是<b>记错了粒度</b>，不是"没记"。
     * <p>
     * 三条约束（A 逐条批过，写死在这里免得下一个人改歪）：
     * ①<b>只认连接层失败</b>——{@link SSLException}／{@link ConnectException}／{@link SocketTimeoutException}／
     *   {@link UnknownHostException} 才计入；{@link HttpException}（能拿到状态码＝连上了）<b>一律不算</b>，
     *   否则一个偶发 404/403 会把好域名拉黑一小时；
     * ②<b>host 级、不拦请求</b>——{@code failed} 决定"这条图还要不要再发"（命中就落色块），本表只决定"首发用哪个域名"
     *   （命中就换镜像照发）；单张图重试成功<b>不删</b>本表记录（图救回来 ≠ 域名好了），清除只由 TTL 到期发生；
     *   也<b>不翻倍</b>（翻倍对域名没有意义，只会把豆瓣恢复后的回归推得更慢）；
     * ③<b>池内全坏必须兜底</b>——退回原始 url 正常发一次：宁可慢一次，不能一张都不出（{@code action=fallback-all}）。
     * <p>
     * 线程与 IO（照实写，不当作已验证的更强保证）：内存表是权威，渲染路径（{@link #load} 的绑定）<b>只读内存、不碰磁盘</b>；
     * 冷启动那次 SP 读由 {@link #warmBadHosts()} 走 {@code Task.execute} 在后台做（{@code App.onCreate} 里调，
     * 与既有 {@code Task.execute(Report::ping)} 同一条路）。预热没赶上的头一两次就按"域名不坏"处理 ⇒
     * 代价是多等一次，换掉首帧磁盘 IO。写侧 {@code Prefers.put} 内部是 {@code apply()}（catvod/Prefers.java:78-90），
     * 主线程调用不产生磁盘等待。
     */
    private static final long PIC_HOST_BAD_MS = TimeUnit.MINUTES.toMillis(60);
    private static final String PIC_HOST_BAD_KEY = "pic_bad_hosts";
    private static final Map<String, Long> badHost = Collections.synchronizedMap(new LinkedHashMap<String, Long>(8, 0.75f, false));
    private static volatile boolean badHostLoaded = false;

    private static final Map<String, PicFail> failed = Collections.synchronizedMap(new LinkedHashMap<String, PicFail>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PicFail> eldest) {
            return size() > PIC_FAIL_MAX_ENTRIES;
        }
    });

    /**
     * β51 / TV-04：一条失败记录。{@code nextAt}＝下次允许重试的时刻；{@code windowMs}＝本次窗口长度（翻倍用）；
     * {@code hitLogged}＝本窗口内"拦下"那行日志是否已打过（一次窗口内可能绑定很多次，不打第二行免刷屏）。
     * <p>
     * 线程口径（照实写，不当作已验证的更强保证）：表本身是 {@code synchronizedMap}，但**记录内字段的读写不在锁内**——
     * 现调用方（{@code load} 的绑定与 Glide 的 {@code onLoadFailed}/{@code onResourceReady} 回调）都在主线程，
     * 故这里没加额外同步；若将来有后台线程调 {@code blocked}，要连记录字段一起上锁。
     */
    private static final class PicFail {

        private long nextAt;
        private long windowMs;
        private boolean hitLogged;

        private PicFail(long nextAt, long windowMs) {
            this.nextAt = nextAt;
            this.windowMs = windowMs;
        }
    }


    public static void logo(ImageView view) {
        try {
            Glide.with(view).load(UrlUtil.convert(VodConfig.get().getConfig().getLogo())).circleCrop().override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).error(R.drawable.ic_logo).into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(String url, CustomTarget<Bitmap> target) {
        try {
            Glide.with(App.get()).asBitmap().load(getUrl(url)).override(ResUtil.dp2px(96), ResUtil.dp2px(96)).error(R.drawable.artwork).into(target);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(Context context, String url, CustomTarget<Drawable> target) {
        try {
            Glide.with(context).load(getUrl(url)).override(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()).error(R.drawable.artwork).into(target);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(String text, String url, ImageView view) {
        load(text, url, view, true);
    }

    public static void load(String text, String url, ImageView view, boolean vod) {
        view.setScaleType(vod ? CENTER_CROP : FIT_CENTER);
        if (!vod) view.setVisibility(TextUtils.isEmpty(url) ? View.GONE : View.VISIBLE);
        if (TextUtils.isEmpty(url) || blocked(url)) view.setImageDrawable(getTextDrawable(text, vod));
        else try {
            // β59：把本次绑定的原始 url 挂在 view 的默认 tag 槽上，供失败回调判断「这张 view 还是不是我」。
            // Glide 自己用的是 keyed tag（包内 id/glide_custom_view_target_tag），实测 target 类不碰默认槽；
            // 卡片 image 视图在本仓没有任何地方 setTag 过（grep 只命中 VideoActivity 的 content/part 与线路按钮）。
            // β61：先问坏域名表——域名坏了首发就走镜像（A 的需求：不再白等一次握手）。
            // ⚠ 退避表与 tag 的键**仍是原始 url**（fired 只用于"实际发出去的是哪条"），β51/β84 的"表只认原始 url"口径不破。
            String fired = preferGoodHost(url);
            view.setTag(url);
            RequestBuilder<Drawable> builder = Glide.with(view).load(getUrl(fired)).listener(getListener(text, url, fired, view, vod));
            if (vod) builder.centerCrop().into(view);
            else builder.fitCenter().into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static Object getUrl(String url) {
        String param = null;
        url = UrlUtil.convert(url);
        if (url.startsWith("data:")) return url;
        LazyHeaders.Builder builder = new LazyHeaders.Builder();
        if (url.contains("@Headers=")) addHeader(builder, param = url.split("@Headers=")[1].split("@")[0]);
        if (url.contains("@Cookie=")) builder.addHeader(HttpHeaders.COOKIE, param = url.split("@Cookie=")[1].split("@")[0]);
        if (url.contains("@Referer=")) builder.addHeader(HttpHeaders.REFERER, param = url.split("@Referer=")[1].split("@")[0]);
        if (url.contains("@User-Agent=")) builder.addHeader(HttpHeaders.USER_AGENT, param = url.split("@User-Agent=")[1].split("@")[0]);
        url = param == null ? url : url.split("@")[0];
        return TextUtils.isEmpty(url) ? null : new GlideUrl(url, builder.build());
    }

    private static void addHeader(LazyHeaders.Builder builder, String header) {
        Map<String, String> map = Json.toMap(Json.parse(header));
        for (Map.Entry<String, String> entry : map.entrySet()) builder.addHeader(UrlUtil.fixHeader(entry.getKey()), entry.getValue());
    }

    private static Drawable getTextDrawable(String text, boolean vod) {
        TextDrawable.Builder builder = new TextDrawable.Builder();
        text = TextUtils.isEmpty(text) ? "！" : text.substring(0, 1);
        if (vod) builder.buildRect(text, ColorGenerator.get400(text));
        return builder.buildRoundRect(text, ColorGenerator.get400(text), ResUtil.dp2px(4));
    }

    /**
     * β51 / TV-04：该图片当前是否应当**不发请求**、直接落首字母色块。
     * <p>
     * 三种出口都留痕且各有判据：无记录（从未失败）→ 静默放行；窗口内 → 打一次 {@code hit}（这就是"到期前零请求"的证据）；
     * 窗口到期 → 打一次 {@code retry} 并把本次窗口按原长推后（真失败时由 {@link #rememberFail} 翻倍，真成功则整条删除）。
     * <p>
     * 只读一次 {@code System.currentTimeMillis()} 比较，不遍历、不算术、不加锁外的状态。
     */
    private static boolean blocked(String url) {
        PicFail fail = failed.get(url);
        if (fail == null) return false;
        long now = System.currentTimeMillis();
        if (now < fail.nextAt) {
            if (!fail.hitLogged) {
                fail.hitLogged = true;
                PicLog.blacklist("hit", url, fail.windowMs, failed.size());
            }
            return true;
        }
        fail.nextAt = now + fail.windowMs;
        fail.hitLogged = false;
        PicLog.blacklist("retry", url, fail.windowMs, failed.size());
        return false;
    }

    /**
     * β51 / TV-04：一次加载失败入表。首次记 10 分钟；已有记录说明上一次到期后仍失败 ⇒ <b>翻倍</b>，上限 60 分钟。
     */
    private static void rememberFail(String url) {
        PicFail old = failed.get(url);
        long window = old == null ? PIC_FAIL_WINDOW_MS : Math.min(old.windowMs * 2, PIC_FAIL_WINDOW_MAX_MS);
        failed.put(url, new PicFail(System.currentTimeMillis() + window, window));
        PicLog.blacklist("add", url, window, failed.size());
    }


    /**
     * β61：冷启动预热（后台线程调，幂等）。读盘失败只当"没有坏域名"，绝不抛上去——
     * 这条跑在 App.onCreate 的 Task 里，抛了就是启动崩溃。
     */
    public static void warmBadHosts() {
        if (badHostLoaded) return;
        try {
            String json = Prefers.getString(PIC_HOST_BAD_KEY);
            if (!TextUtils.isEmpty(json)) {
                Type type = TypeToken.getParameterized(HashMap.class, String.class, Long.class).getType();
                Map<String, Long> map = App.gson().fromJson(json, type);
                if (map != null) {
                    long now = System.currentTimeMillis();
                    for (Map.Entry<String, Long> entry : map.entrySet()) {
                        Long until = entry.getValue();
                        if (entry.getKey() != null && until != null && until > now) badHost.put(entry.getKey(), until);
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        badHostLoaded = true;
    }

    /** β61：整表回落 SP（十来个键、一行 JSON）。快照一份再序列化，避免 Gson 遍历到并发修改。 */
    private static void saveBadHosts() {
        try {
            Map<String, Long> snapshot;
            synchronized (badHost) {
                snapshot = new HashMap<>(badHost);
            }
            Prefers.put(PIC_HOST_BAD_KEY, App.gson().toJson(snapshot));
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * β61：该域名此刻是否坏。<b>只读内存表</b>（渲染路径调它，绝不能在这里读盘）。
     * 到期就地删除并留痕 {@code action=expire} ⇒ 下一次允许再试原域名，豆瓣修好了能自动恢复。
     */
    private static boolean isBadHost(String host) {
        if (host == null) return false;
        Long until = badHost.get(host);
        if (until == null) return false;
        long now = System.currentTimeMillis();
        if (now < until) return true;
        badHost.remove(host);
        saveBadHosts();
        PicLog.hostBad(host, "expire", badHost.size());
        return false;
    }

    /** β61：一次连接层失败 ⇒ 该域名记坏 60 分钟（{@code action=mark}）。原始域名与镜像域名一视同仁。 */
    private static void markBadHost(String url) {
        String host = hostOf(url);
        if (!isDoubanioHost(host)) return;
        badHost.put(host, System.currentTimeMillis() + PIC_HOST_BAD_MS);
        saveBadHosts();
        PicLog.hostBad(host, "mark", badHost.size());
    }

    /**
     * β61：分辨「域名连不上」与「图本身有问题」——A 要求只有前者才拉黑域名。
     * 实测依据：β83 真机那四条失败 {@code rc=1}，root cause 是 {@code SSLHandshakeException}（SSLException 的子类）；
     * 而 β80 发现 okhttp 集成层在**非 2xx 时抛 HttpException**（PicLog.imgFail 就是靠它取状态码）⇒ 有状态码＝连上了。
     */
    private static boolean isConnectionFailure(@Nullable GlideException e) {
        if (e == null) return false;
        for (Throwable cause : e.getCauses()) {
            if (cause instanceof HttpException) return false;
        }
        for (Throwable cause : e.getRootCauses()) {
            if (cause instanceof HttpException) return false;
            if (cause instanceof SSLException || cause instanceof ConnectException
                    || cause instanceof SocketTimeoutException || cause instanceof UnknownHostException) return true;
        }
        return false;
    }

    /** β61：池里挑第一个「不是刚失败那个、且当前没被判坏」的域名；一个都没有返回 null（调用方负责兜底）。 */
    @Nullable
    private static String pickHost(String exclude) {
        for (String candidate : PIC_MIRROR_HOSTS) {
            if (!candidate.equals(exclude) && !isBadHost(candidate)) return candidate;
        }
        return null;
    }

    /** β61：只换 host——路径与 {@code @Referer=}／{@code @User-Agent=} 宏尾巴逐字保留（镜像是同一张图的同一相对路径）。 */
    private static String swapHost(String url, String host, String target) {
        int start = url.indexOf("://") + 3;
        return url.substring(0, start) + target + url.substring(start + host.length());
    }

    /**
     * β61：<b>入口改写</b>。原始域名已在坏表里 ⇒ 首发直接走镜像，不再白等一次握手（A 的需求本体，{@code action=use}）。
     * 域名没坏时<b>原样返回</b>——第一次仍要真撞一次才知道它坏（这是无法省掉的探测，但一次探测换 60 分钟安静）。
     * 池内全坏 ⇒ 打 {@code fallback-all} 并退回原始 url 正常发一次。
     */
    private static String preferGoodHost(String url) {
        String host = hostOf(url);
        if (!isDoubanioHost(host) || !isBadHost(host)) return url;
        String next = pickHost(host);
        if (next == null) {
            PicLog.hostBad(host, "fallback-all", badHost.size());
            return url;
        }
        PicLog.hostBad(host, "use", badHost.size());
        return swapHost(url, host, next);
    }


    /**
     * β59：取 {@code scheme://host/path} 里那段 host。<b>必须在 {@code /}、{@code ?}、{@code #}、{@code @} 之前停</b>——
     * 源给的串常带 {@code @Referer=}／{@code @User-Agent=} 这类宏尾巴，把宏算进 host 会让判据全落空。
     * 没有 {@code ://} 的串返回 null（不当豆瓣域名处理）。
     */
    @Nullable
    private static String hostOf(String url) {
        int start = url.indexOf("://");
        if (start < 0) return null;
        start += 3;
        int end = start;
        while (end < url.length()) {
            char c = url.charAt(end);
            if (c == '/' || c == '?' || c == '#' || c == '@') break;
            end++;
        }
        return end > start ? url.substring(start, end) : null;
    }

    /**
     * β59：只有 {@code img0~img9.doubanio.com} 算豆瓣图床。盒子迷那种 {@code mg.lzipic.com} 一律不进这段逻辑（A 第五节第 6 条）。
     * <p>
     * 判据只认<b>单个数字</b>：豆瓣现有镜像就到 img9，为不存在的域名写兼容是提前设计（A 第二节第 2 条）。
     * 若豆瓣将来新增 {@code img10} 之类两位数标签，<b>需同步改这张判据与 {@link #PIC_MIRROR_HOSTS} 池</b>，
     * 否则新镜像会被判成非豆瓣域名、直接落色块而不重试。
     */
    private static boolean isDoubanioHost(@Nullable String host) {
        if (host == null || !host.endsWith(PIC_DOUBANIO_SUFFIX)) return false;
        String label = host.substring(0, host.length() - PIC_DOUBANIO_SUFFIX.length());
        return label.length() == 4 && label.startsWith("img") && Character.isDigit(label.charAt(3));
    }

    /**
     * β59：这条失败 URL 应当换成哪个镜像域名；返回 null＝不换。
     * <p>
     * <b>只换 host，路径与宏尾巴逐字保留</b>——镜像就是同一张图的同一相对路径（A 实测同为 25,757 字节），
     * 换 host 之后 {@code @Referer=} 照旧生效，不需要重造头。候选取池里第一个「不是刚失败那个」，
     * 所以 img3 坏了会走 img9，不会原地回到 img3。
     */
    @Nullable
    private static String mirrorUrl(String origin, String fired) {
        // β61：起点从「原始 url」换成「实际发出去的那条」——入口若已改写过，重试就要再换**另一个**域名，
        // 而不是原地重试同一个（那会白等第二轮握手）。没改写过时 fired==origin，行为与 β84 逐字一致。
        String host = hostOf(fired);
        if (!isDoubanioHost(host)) return null;
        String next = pickHost(host);
        return next == null ? null : swapHost(fired, host, next);
    }

    /**
     * β59 / TV-04：豆瓣镜像域名重试，<b>只一次</b>。
     * <p>
     * <b>β84 改的是发起时机</b>：Glide 5.0.9 在 {@code RequestListener}／{@code Target} 回调里<b>禁止</b> start/clear。
     * β83 在 {@code onLoadFailed} 里同步 {@code into(view)}，真机实测那条链是
     * {@code RequestBuilder.into(ImageView)}（{@code RequestBuilder.java:899}）→ {@code RequestManager.clear(Target):642}
     * → {@code RequestTracker.clearAndRemove:70} → {@code SingleRequest.clear():326}，其内联的
     * {@code assertNotCallingCallbacks()} 抛 {@code IllegalStateException: You can't start or clear loads in
     * RequestListener or Target callbacks}；当时那句 {@code catch (Throwable)} 只 {@code printStackTrace}，
     * 异常落进 {@code System.err} ⇒ 包内代码在跑、{@code FireFlyPic} 里却一条 {@code pic img retry} 都没有。
     * 库的提示语本身就是本版的写法：{@code consider posting your into() or clear() calls to the main thread
     * using a Handler instead}（A 批的甲）。
     * <p>
     * 三条口径（β84 对账）：
     * <ol>
     *   <li><b>先入表、后重试</b>（A 已裁「接受」）：原始 url 在 {@code onLoadFailed} 里就进退避表 ⇒
     *       <b>重试这条路无论走不走得通，退避表都不会漏记</b>。β83 把入表挂在重试的失败回调上，重试没发出 ⇒
     *       豆瓣这类失败彻底不进表、每次绑定都重发一次长超时请求，那是「把原有防护弄坏了」，比「新功能没生效」严重；</li>
     *   <li>重试<b>成功</b>＝{@code failed.remove(url)} 整条抹掉，所以那个记录只占「一次消息队列排队」那么长的窗口，
     *       这正是「先记又删」不矛盾的原因；重试<b>仍失败</b>时 {@link #rememberFail} 再记一次 ⇒ 按 β51 的
     *       「同一 URL 连续失败翻倍」推进到 20 分钟（一次绑定里原 host 与镜像都失败，语义上确属连续失败）。
     *       若嫌翻倍太狠，回退代价只有删掉 {@code onLoadFailed} 里那一行 {@code rememberFail(url)}——先入表照旧成立，
     *       只是重试失败不再把窗口推到 20 分钟；</li>
     *   <li>退避表<b>只认原始 url</b>：镜像 url 永不入表（记镜像＝把一个可用域名标成坏域名）。</li>
     * </ol>
     * runnable 里<b>再比一次 {@code view.getTag()}</b>：post 排队期间这张卡可能已被复用给别的条目，
     * 那时 {@code into(view)} 会先 {@code clear} 掉<b>新条目</b>正在飞的请求、那一格就永远不出图 ⇒ 放弃重试、
     * 打 {@code result=skip}，退回已有色块（与 β83 同一条防护，只是比对位置从回调里挪进了 runnable）。
     * <p>
     * 不循环（重试的失败回调里不再换域名）、不引入定时器／线程／后台轮询：{@code post} 只把发起挪出回调，
     * 仍在主线程的同一条消息队列里，紧随当前回调之后执行。
     */
    private static void retryMirror(String text, String url, String mirror, ImageView view, boolean vod) {
        view.post(new Runnable() {
            @Override
            public void run() {
                if (!url.equals(view.getTag())) {
                    PicLog.imgRetry(url, mirror, text, "skip", null);
                    return;
                }
                try {
                    RequestBuilder<Drawable> builder = Glide.with(view).load(getUrl(mirror)).listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                            // 镜像也失败＝同一张图连着失败两次：原始 url 的窗口在此翻倍（镜像 url 始终不入表）。
                            rememberFail(url);
                            // β61：镜像域名自己也是连接层失败 ⇒ 一并记坏（下次入口改写就会跳过它，A 的"不再白等"要求覆盖镜像）。
                            if (isConnectionFailure(e)) markBadHost(mirror);
                            PicLog.imgRetry(url, mirror, text, "fail", e);
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            failed.remove(url);
                            PicLog.imgRetry(url, mirror, text, "ok", null);
                            return false;
                        }
                    });
                    if (vod) builder.centerCrop().into(view);
                    else builder.fitCenter().into(view);
                } catch (Throwable e) {
                    // β84 的丙（A：「这条必须做」）：这里过去只 printStackTrace，异常消失在 System.err 里，
                    // β83 那一轮要靠 C 去抓 System.err 才看得见。现在把它打到 FireFlyPic，判据名 result=err。
                    PicLog.imgRetry(url, mirror, text, "err", e);
                }
            }
        });
    }

    private static RequestListener<Drawable> getListener(String text, String url, String fired, ImageView view, boolean vod) {
        return new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                view.setImageDrawable(getTextDrawable(text, vod));
                String mirror = mirrorUrl(url, fired);
                // β84（A 批的甲）：先入表、后重试——退避表不许因为「重试这条路没走通」而漏记。
                // mirror == null（非豆瓣域名／池里没有别的候选）时，这两句就是 β82 的原行为，逐字不变。
                rememberFail(url);
                PicLog.imgFail(url, text, e, isFirstResource);
                // β61：连接层失败 ⇒ 把**实际发出去的那个域名**记坏（有状态码的不记，见 isConnectionFailure）。
                if (isConnectionFailure(e)) markBadHost(fired);
                if (mirror != null) retryMirror(text, url, mirror, view, vod);
                return true;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                // β51 / TV-04：成功一次立即整条清除（A 批的"成功立即恢复"）——到期重试成功的图不会再占用退避窗口。
                failed.remove(url);
                return false;
            }
        };
    }
}
