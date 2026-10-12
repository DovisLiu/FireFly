package androidx.media3.exoplayer.source.preload;

import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;

/**
 * 上游定制 media3 私有类 DiskPreloadManager 的还原实现。
 *
 * <p>由官方 5.6.1 APK 反编译还原（类名混淆，public API 面由 5.6.0 源码调用确定）：
 * 监听 {@link ExoPlayer} 事件，定时把播放位置之后的媒体区间通过
 * {@link PreCacheHelper} 预下载到磁盘 {@link Cache}，实现"边播边缓存"。
 *
 * <p>依赖官方 media3 preload 包（webhtv 1.11 AAR 自带 DefaultPreloadManager /
 * PreCacheHelper），行为与官方一致。
 */
public final class DiskPreloadManager implements Player.Listener {

    private final Cache cache;
    private final DataSource.Factory upstreamDataSourceFactory;
    private final RenderersFactory renderersFactory;
    @Nullable private final PriorityTaskManager priorityTaskManager;
    private final HandlerThread preloadThread;
    private final Handler handler;
    private final Runnable ticker;

    @Nullable private ExoPlayer player;
    @Nullable private MediaItem mediaItem;
    @Nullable private Options options;
    @Nullable private PreCacheHelper preCacheHelper;
    private long lastPreloadStartMs;
    private boolean released;

    private DiskPreloadManager(Builder builder) {
        cache = builder.cache;
        upstreamDataSourceFactory = builder.upstreamDataSourceFactory;
        renderersFactory = builder.renderersFactory;
        priorityTaskManager = builder.priorityTaskManager;
        lastPreloadStartMs = C.TIME_UNSET;
        ticker = this::tick;
        preloadThread = new HandlerThread("Media3:DiskPreload");
        preloadThread.start();
        handler = new Handler(preloadThread.getLooper());
    }

    /** 开始为指定媒体进行磁盘预加载。 */
    public void start(@NonNull ExoPlayer player, @NonNull MediaItem mediaItem, @NonNull Options options) {
        if (released) return;
        this.player = player;
        this.mediaItem = mediaItem;
        this.options = options;
        PreCacheHelper.Factory factory =
                new PreCacheHelper.Factory(cache, upstreamDataSourceFactory, renderersFactory, handler.getLooper());
        if (options.maxThreads > 0) {
            factory.setDownloadExecutor(
                    java.util.concurrent.Executors.newFixedThreadPool(options.maxThreads));
        }
        preCacheHelper = factory.create(mediaItem);
        player.addListener(this);
        schedule();
    }

    /** 停止并释放全部资源。 */
    public void release() {
        if (released) return;
        released = true;
        if (player != null) {
            player.removeListener(this);
        }
        handler.removeCallbacksAndMessages(null);
        if (preCacheHelper != null) {
            preCacheHelper.release(false);
            preCacheHelper = null;
        }
        preloadThread.quitSafely();
        player = null;
        mediaItem = null;
        options = null;
    }

    // ---------- Player.Listener ----------

    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
        if (this.mediaItem != null && mediaItem != null
                && !Util.areEqual(this.mediaItem.mediaId, mediaItem.mediaId)) {
            lastPreloadStartMs = C.TIME_UNSET;
        }
    }

    @Override
    public void onTimelineChanged(Timeline timeline, int reason) {
        tick();
    }

    @Override
    public void onTracksChanged(Tracks tracks) {
        tick();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        tick();
    }

    @Override
    public void onEvents(Player player, Player.Events events) {
        lastPreloadStartMs = C.TIME_UNSET;
        tick();
    }

    // ---------- 内部逻辑 ----------

    private void schedule() {
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 1000);
    }

    private void tick() {
        if (released || player == null || mediaItem == null || preCacheHelper == null || options == null) {
            return;
        }
        int state = player.getPlaybackState();
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
            preCacheHelper.stop();
            schedule();
            return;
        }
        long durationMs = player.getDuration();
        if (durationMs <= 0) {
            schedule();
            return;
        }
        long positionMs = player.getCurrentPosition();
        long windowStartMs =
                Math.max(
                        positionMs,
                        Math.max(positionMs, player.getBufferedPosition())
                                - Math.min(5000, durationMs / 10));
        if (windowStartMs >= durationMs) {
            preCacheHelper.stop();
            schedule();
            return;
        }
        if (lastPreloadStartMs != C.TIME_UNSET
                && windowStartMs >= lastPreloadStartMs
                && windowStartMs - lastPreloadStartMs
                        < Math.max(5000, Math.min(30000, durationMs / 4))) {
            // 与上次预载起点接近，跳过本次调度（节流）
            schedule();
            return;
        }
        long windowDurationMs = Math.min(options.durationMs, durationMs - windowStartMs);
        if (windowDurationMs > 0) {
            preCacheHelper.preCache(windowStartMs, windowDurationMs);
            lastPreloadStartMs = windowStartMs;
        }
        schedule();
    }

    /** 磁盘预加载选项。 */
    public static final class Options {

        private final long durationMs;
        private final int maxThreads;

        private Options(long durationMs, int maxThreads) {
            this.durationMs = durationMs;
            this.maxThreads = maxThreads;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {

            private long durationMs = 300_000;
            private int maxThreads = 2;

            public Builder setDurationMs(long durationMs) {
                this.durationMs = durationMs;
                return this;
            }

            public Builder setMaxThreads(int maxThreads) {
                this.maxThreads = maxThreads;
                return this;
            }

            public Options build() {
                return new Options(durationMs, maxThreads);
            }
        }
    }

    /** 构建器。 */
    public static final class Builder {

        private final Cache cache;
        private final DataSource.Factory upstreamDataSourceFactory;
        private final RenderersFactory renderersFactory;
        @Nullable private PriorityTaskManager priorityTaskManager;

        public Builder(
                @NonNull Cache cache,
                @NonNull DataSource.Factory upstreamDataSourceFactory,
                @NonNull RenderersFactory renderersFactory) {
            this.cache = cache;
            this.upstreamDataSourceFactory = upstreamDataSourceFactory;
            this.renderersFactory = renderersFactory;
        }

        public Builder setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager) {
            this.priorityTaskManager = priorityTaskManager;
            return this;
        }

        public DiskPreloadManager build() {
            return new DiskPreloadManager(this);
        }
    }
}
