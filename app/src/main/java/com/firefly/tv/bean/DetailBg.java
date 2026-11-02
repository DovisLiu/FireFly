package com.firefly.tv.bean;

import android.content.Context;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;

import java.io.File;

/**
 * β25 / T-333（A 开工令 2026-10-31，边界已定）：详情页背景**三级选档 + 只解头探测**。
 *
 * 旧链两档（{@link DetailPic}：slide 无条件 → pic 当竖版兑底），本批把 pic 档拆开：
 *   1. {@code slide}：vod_pic_slide **无条件优先，不验比例**（A 边界①，ratio 恒 na）；
 *   2. {@code widePic}：pic 探测出 宽/高 ≥ {@link #WIDE_RATIO}——**只在 slide 加载失败降级时**由
 *      {@link #fallbackFromSlide} 到达（A 边界②），或该源本就无 slide（此时它是第一档命中）；
 *   3. {@code poster}：探测出竖版、或探测失败（ratio=na，保守按竖版 ⇒ TV 走 PosterWash 兑底链）。
 *
 * 职责边界：本类**只选档与探测，不发背景图请求、不打档位日志**（日志由调用方按 A 字面口径打，
 * TV/mobile 逐字一致）；探测走 Glide downloadOnly（鉴权串由调用端经 ImgUtil.getUrl 预解析）＋
 * BitmapFactory inJustDecodeBounds **只解头不解像素**。
 *
 * 同步性（编码纪律）：{@link #pickStart} 的 slide/none 分支**同步**回调——调用方须在调用前设好
 * 作废/代际状态（TV 的 mWashGen 语义）；widePic/poster 起点分支在探测完成后异步回调。
 */
public final class DetailBg {

    /** 横向判定阈值（A 判据：宽/高 ≥1.3）。 */
    public static final double WIDE_RATIO = 1.3;

    /** 档位常量（日志字面＝A 口径 slide|widePic|poster）。 */
    public static final String TIER_SLIDE = "slide";
    public static final String TIER_WIDE = "widePic";
    public static final String TIER_POSTER = "poster";

    /** 选档结果回调。url=null ⇒ 无任何可加载候选（调用方走 intent 图/既有空图分支）。 */
    public interface Callback {
        /**
         * @param tag   {@code slide|widePic|poster}
         * @param ratio 比例字符串两位小数；slide 档与探测失败＝"na"
         */
        void onPick(@Nullable String url, String tag, String ratio);
    }

    /** 探测结果消费者：int[]{w,h}；null＝探测失败（网络/解码/生命周期不可用）。 */
    private interface Probe {
        void onProbe(@Nullable int[] wh);
    }

    private DetailBg() {
    }

    /**
     * 起链判定：
     *   · slide 非空 ⇒ **同步**回调 (slide, "slide", "na")——不验比例；
     *   · 无 slide 且 pic 非空 ⇒ 异步探测 pic：横向⇒(pic,"widePic",r)，竖版⇒(pic,"poster",r)，失败⇒(pic,"poster","na")；
     *   · 两者皆空 ⇒ 同步回调 (null,"poster","na")。
     */
    public static void pickStart(Context ctx, Vod vod, Object slideModel, Object picModel, Callback cb) {
        String slide = blankToNull(vod.getPicSlide());
        String pic = blankToNull(vod.getPic());
        if (slide != null) {
            cb.onPick(slide, TIER_SLIDE, "na");
            return;
        }
        if (pic == null) {
            cb.onPick(null, TIER_POSTER, "na");
            return;
        }
        if (picModel == null) {
            cb.onPick(pic, TIER_POSTER, "na");
            return;
        }
        probe(ctx, picModel, wh -> cb.onPick(pic, tierOf(wh), ratioOf(wh)));
    }

    /**
     * slide 加载失败后的降级：探测 pic 并回调下一档。
     * 无 pic（或 pic 与 slide 同串）⇒ 同步回调 (null,"poster","na")，由调用方回退既有兜底。
     */
    public static void fallbackFromSlide(Context ctx, Vod vod, Object picModel, Callback cb) {
        String pic = blankToNull(vod.getPic());
        if (pic == null || pic.equals(blankToNull(vod.getPicSlide()))) {
            cb.onPick(null, TIER_POSTER, "na");
            return;
        }
        if (picModel == null) {
            cb.onPick(pic, TIER_POSTER, "na");
            return;
        }
        probe(ctx, picModel, wh -> cb.onPick(pic, tierOf(wh), ratioOf(wh)));
    }

    /** 探测 wh ⇒ 档位字面；null/非法一律 poster（保守竖版）。 */
    public static String tierOf(@Nullable int[] wh) {
        if (wh == null || wh[0] <= 0 || wh[1] <= 0) return TIER_POSTER;
        return (double) wh[0] / wh[1] >= WIDE_RATIO ? TIER_WIDE : TIER_POSTER;
    }

    /** 比例字符串（两位小数，Locale.US 定点）；不可测 ⇒ na。 */
    public static String ratioOf(@Nullable int[] wh) {
        if (wh == null || wh[0] <= 0 || wh[1] <= 0) return "na";
        return String.format(java.util.Locale.US, "%.2f", (double) wh[0] / wh[1]);
    }

    /** 是否横版构图（slide 或 widePic 命中都算）——TV 端 true ⇒ 直铺、不做 wash。 */
    public static boolean isWide(String tag) {
        return TIER_SLIDE.equals(tag) || TIER_WIDE.equals(tag);
    }

    /** downloadOnly 原图文件（鉴权串由调用端经 ImgUtil.getUrl 预解析）→ inJustDecodeBounds 只解头；单通道回调防重复。 */
    private static void probe(Context ctx, Object model, Probe probe) {
        try {
            // Glide 5.0.9：SimpleTarget 仅 ()/(int,int) 两构造器；File 档尺寸无意义，用无参构造（getSize 内部 final 处理）。
            // listener 先于 target 收到资源，return true 消费 ⇒ target.onResourceReady 不再触发 ⇒ 单通道。
            Glide.with(ctx).downloadOnly()
                    .load(model)
                    .listener(new RequestListener<File>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                    Target<File> target, boolean isFirstResource) {
                            probe.onProbe(null);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(File file, Object model, Target<File> target,
                                                       DataSource ds, boolean isFirstResource) {
                            BitmapFactory.Options o = new BitmapFactory.Options();
                            o.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(file.getAbsolutePath(), o);
                            probe.onProbe(o.outWidth > 0 && o.outHeight > 0 ? new int[]{o.outWidth, o.outHeight} : null);
                            return true;
                        }
                    })
                    .into(new SimpleTarget<File>() {
                        @Override
                        public void onResourceReady(@NonNull File resource, @Nullable Transition<? super File> transition) {
                            // 上面 listener 已 return true 消费，此回调不会到达；留空满足抽象方法。
                        }
                    });
        } catch (IllegalArgumentException e) {
            // 生命周期不可用（页面已销毁等）⇒ 按探测失败处理，调用方走 poster 兑底
            probe.onProbe(null);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
