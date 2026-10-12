package androidx.media3.exoplayer.trackselection;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

/**
 * 上游定制 media3 私有类 DecodeTrackSelector 的替代实现（stub）。
 *
 * <p>继承官方 DefaultTrackSelector（原生支持硬解优先、参数构建/设置），满足 ExoUtil
 * 的构造与 buildUponParameters/setParameters 调用。上游 项目特有的
 * {@link #setRendererDecodePreferences(int, int)} 在此保留接口为空实现——官方
 * DefaultTrackSelector 已按系统解码能力自动选轨（硬解优先），行为与合理默认一致，
 * 不修改任何媒体管线逻辑。
 */
public class DecodeTrackSelector extends DefaultTrackSelector {

    public DecodeTrackSelector(@NonNull Context context) {
        super(context);
    }

    /**
     * 上游定制 API：按硬解/软解偏好设置渲染器解码方式。
     * 官方 DefaultTrackSelector 无此能力，此处保留签名兼容，逻辑由官方默认选轨行为覆盖。
     *
     * @param audioDecode PlayerEngine.SOFT / PlayerEngine.HARD
     * @param videoDecode PlayerEngine.SOFT / PlayerEngine.HARD
     */
    public void setRendererDecodePreferences(int audioDecode, int videoDecode) {
        // no-op：官方默认选轨已按系统解码能力处理
    }
}
