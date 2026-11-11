package com.firefly.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.firefly.tv.R;

/**
 * β84 玻璃批 2：海报卡的**方向性描边高光**（左上／右下亮、右上／左下暗）。
 *
 * 为什么自绘：Android 的 XML 渐变描边会被静默丢弃（编译过、运行时无效），MaterialShapeDrawable 的
 * stroke 只收单色 ⇒ 方向性只能走 Canvas：一条沿**主对角线**的 LinearGradient 配 STROKE 画笔。
 * 两个亮角落在 t=0／t=1，与对角线垂直的另外两角投影到中点 t=stop ⇒ 同一根线、两种亮度，不需要叠两层。
 *
 * 只描边、不覆膜：半透明填充压在海报上会洗白画面（β79 玻璃批 1 的 B 档结论，也是"玻璃看不出来"的成因之一）。
 *
 * 数值全部走 token，本类不写死任何颜色与尺寸：
 * 颜色＝R.color.glass_edge_bright／glass_edge_dim（values 与 values-night 各一套，浅档黑、深档白）；
 * 宽度＝R.dimen.glass_edge_width；停靠点＝R.integer.glass_edge_stop_mid（百分数，不用 float 型 dimen＝读数形态有歧义）；圆角＝复用现成 R.dimen.radius_card（12dp，与 main 的 Vod.Grid cornerSize 同档）。
 *
 * β26 玻璃扩面（A 2026-11-05 裁甲）：色对与圆角都改走入参，取值仍全在 token 里。
 * 三档构造各自的语义（断言写在 p129 落码器里，不在运行时）：
 *   ①单参构造＝旧调用点，色对＝glass_edge_bright／dim（随主题翻），圆角＝radius_card，一字未变；
 *   ②(色对, 圆角 token)＝设置页 12 行与选台卡，圆角走各自 token（14dp／4dp），色对随主题翻；
 *   ③(色对)＝详情页三颗恒深底钮，色对＝glass_edge_on_dark_*，圆角＝短边一半（halfShortSide）。
 * 为什么深底要另走一对：glass_edge_bright 浅档是黑（#1F000000），压在 glass_fill #73000000 的恒深底上等于看不见；
 * on_dark 那对（浅 #54FFFFFF／#1CFFFFFF，深 #A6FFFFFF／#38FFFFFF）＝A 冻结的 33%／11% 与 65%／22%。
 */
public class GlassEdgeDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int bright;
    private final int dim;
    private final float stopMid;
    private final float radius;
    private final float stroke;
    private final boolean halfShortSide;

    public GlassEdgeDrawable(@NonNull Context context) {
        this(context, R.color.glass_edge_bright, R.color.glass_edge_dim, R.dimen.radius_card);
    }

    /** 色对＋圆角 token 各走一档；本批设置页 12 行（14dp）与选台卡（4dp）用这条。 */
    public GlassEdgeDrawable(@NonNull Context context, int brightRes, int dimRes, int radiusRes) {
        this(context, brightRes, dimRes, context.getResources().getDimension(radiusRes), false);
    }

    /** 恒深底三颗：色对入参，圆角＝短边一半（不建 radius_button=24dp 那档 token，A 裁甲-1）。 */
    public GlassEdgeDrawable(@NonNull Context context, int brightRes, int dimRes) {
        this(context, brightRes, dimRes, 0f, true);
    }

    private GlassEdgeDrawable(@NonNull Context context, int brightRes, int dimRes, float radiusPx, boolean half) {
        bright = context.getColor(brightRes);
        dim = context.getColor(dimRes);
        stroke = context.getResources().getDimension(R.dimen.glass_edge_width);
        stopMid = context.getResources().getInteger(R.integer.glass_edge_stop_mid) / 100f;
        radius = radiusPx;
        halfShortSide = half;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        paint.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                new int[]{bright, dim, bright}, new float[]{0f, stopMid, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        float half = stroke / 2f;
        rect.set(bounds.left + half, bounds.top + half, bounds.right - half, bounds.bottom - half);
        // 环半径：三颗 40dp 高的钮走「短边一半」＝标准胶囊端头；其余走各自 token（同 AOSP GradientDrawable:847 的钳制口径）
        float r = halfShortSide ? Math.min(rect.width(), rect.height()) / 2f : radius;
        canvas.drawRoundRect(rect, r, r, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
