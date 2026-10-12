package is.xyz.mpv;

import android.content.Context;
import android.view.Surface;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * mpv JNI 层（由官方 5.6.1 APK 反编译还原）。
 *
 * <p>native 方法名/签名与官方 libplayer.so 导出的
 * {@code Java_is_xyz_mpv_MPVLib_*} 符号一一对应；事件分发由 native 回调
 * {@link #event}/{@link #eventProperty}/{@link #eventEndFile}/
 * {@link #eventCommandReply}/{@link #logMessage}，Java 侧转发给观察者。
 */
public final class MPVLib {

    /** 事件观察者（对应 mpv 事件与属性变化回调）。 */
    public interface EventObserver {
        default void onEvent(int eventId) {}

        default void onEventProperty(String name, Object value) {}

        default void onEventEndFile(int reason, int error, String path) {}

        default void onEventCommandReply(long replyId, int status) {}
    }

    /** 日志观察者。 */
    public interface LogObserver {
        void onLogMessage(String prefix, int level, String text);
    }

    private static final CopyOnWriteArrayList<EventObserver> OBSERVERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<LogObserver> LOG_OBSERVERS = new CopyOnWriteArrayList<>();

    private MPVLib() {
    }

    public static void addObserver(EventObserver observer) {
        OBSERVERS.addIfAbsent(observer);
    }

    public static void removeObserver(EventObserver observer) {
        OBSERVERS.remove(observer);
    }

    public static void addLogObserver(LogObserver observer) {
        LOG_OBSERVERS.addIfAbsent(observer);
    }

    public static void removeLogObserver(LogObserver observer) {
        LOG_OBSERVERS.remove(observer);
    }

    // ---------- native：与官方 libplayer.so 的 JNI 符号对应 ----------

    /** 创建 mpv 实例（需要全局 Application Context）。 */
    public static native void create(Context context);

    /** 初始化 mpv 核心。 */
    public static native void init();

    /** 销毁 mpv 实例。 */
    public static native int destroy();

    /** 设置启动选项（须在 init 前调用）。 */
    public static native int setOptionString(String name, String value);

    // 属性设置（.so 导出符号存在；实现优先使用 enqueueCommand 的 set_property）
    public static native int setPropertyString(String name, String value);

    public static native int setPropertyInt(String name, int value);

    public static native int setPropertyDouble(String name, double value);

    public static native int setPropertyBoolean(String name, boolean value);

    /** 异步执行 mpv 命令，replyId 用于关联回调。 */
    public static native int enqueueCommand(long replyId, String[] args);

    /** 读取属性。 */
    public static native String getPropertyString(String name);

    public static native Integer getPropertyInt(String name);

    public static native Double getPropertyDouble(String name);

    public static native Boolean getPropertyBoolean(String name);

    public static native byte[] getPropertyByteArray(String name);

    /** 订阅属性变化（format 见 MpvFormat 常量）。 */
    public static native int observeProperty(String name, int format);

    /** 视频输出 Surface。 */
    public static native void attachSurface(Surface surface);

    public static native void detachSurface();

    public static native void replaceSurface(Surface surface);

    /** OSD 输出 Surface。 */
    public static native void attachOsdSurface(Surface surface);

    public static native void detachOsdSurface();

    public static native void replaceOsdSurface(Surface surface);

    // ---------- 事件回调（由 native 线程调用） ----------

    public static void event(int eventId) {
        for (EventObserver observer : OBSERVERS) {
            observer.onEvent(eventId);
        }
    }

    public static void eventProperty(String name, String value) {
        for (EventObserver observer : OBSERVERS) {
            observer.onEventProperty(name, value);
        }
    }

    public static void eventProperty(String name, double value) {
        for (EventObserver observer : OBSERVERS) {
            observer.onEventProperty(name, value);
        }
    }

    public static void eventProperty(String name, long value) {
        for (EventObserver observer : OBSERVERS) {
            observer.onEventProperty(name, value);
        }
    }

    public static void eventProperty(String name, boolean value) {
        for (EventObserver observer : OBSERVERS) {
            observer.onEventProperty(name, value);
        }
    }

    public static void eventEndFile(int reason, int error, String path) {
        for (EventObserver observer : OBSERVERS) {
            observer.onEventEndFile(reason, error, path);
        }
    }

    public static void eventCommandReply(long replyId, int status) {
        for (EventObserver observer : OBSERVERS) {
            observer.onEventCommandReply(replyId, status);
        }
    }

    public static void logMessage(String prefix, int level, String text) {
        for (LogObserver observer : LOG_OBSERVERS) {
            observer.onLogMessage(prefix, level, text);
        }
    }

    // ---------- mpv 常量 ----------

    /** mpv 属性格式（observeProperty 的 format 参数）。 */
    public static final class MpvFormat {
        public static final int MPV_FORMAT_NONE = 0;
        public static final int MPV_FORMAT_STRING = 1;
        public static final int MPV_FORMAT_OSD_STRING = 2;
        public static final int MPV_FORMAT_FLAG = 3;
        public static final int MPV_FORMAT_INT64 = 4;
        public static final int MPV_FORMAT_DOUBLE = 5;
        public static final int MPV_FORMAT_NODE = 6;
        public static final int MPV_FORMAT_NODE_ARRAY = 7;
        public static final int MPV_FORMAT_NODE_MAP = 8;
        public static final int MPV_FORMAT_BYTE_ARRAY = 9;

        private MpvFormat() {
        }
    }
}
