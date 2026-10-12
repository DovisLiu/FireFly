package androidx.media3.mpvplayer;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Size;
import androidx.media3.mpvplayer.audio.MpvAudioFilter;
import androidx.media3.mpvplayer.video.MpvVideoEqualizer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import is.xyz.mpv.MPVLib;

/**
 * 上游定制 media3 私有模块 MpvPlayer 的真实现（基于官方 libmpv/libplayer 反编译还原）。
 *
 * <p>驱动方式：MPVLib（JNI）创建 mpv 实例，通过 loadfile 命令加载、set_property 控制、
 * observeProperty 监听（time-pos/duration/pause/eof-reached 等）更新播放状态，
 * attachSurface 输出视频。实现 {@link Player} 全接口 + 5.6.0 源码调用的自定义 API。
 */
public final class MpvPlayer implements Player, MPVLib.EventObserver {

    public static final int VIDEO_EFFECTS_SUPPORTED = 0;
    public static final int VIDEO_EFFECTS_UNSUPPORTED = 1;
    public static final int VIDEO_EFFECTS_UNSUPPORTED_DIRECT_DOLBY_VISION_OUTPUT = 2;

    public static final int AUDIO_EFFECTS_SUPPORTED = 0;
    public static final int AUDIO_EFFECTS_UNSUPPORTED = 1;
    public static final int AUDIO_EFFECTS_UNSUPPORTED_PASSTHROUGH = 2;

    // mpv 事件 ID
    private static final int MPV_EVENT_NONE = 0;
    private static final int MPV_EVENT_START_FILE = 6;
    private static final int MPV_EVENT_END_FILE = 7;
    private static final int MPV_EVENT_FILE_LOADED = 8;
    private static final int MPV_EVENT_PLAYBACK_RESTART = 21;

    public static boolean isAvailable() {
        try {
            System.loadLibrary("player");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private final Context context;
    private final MpvPlayerConfig config;
    private final int decode;
    private final Handler mainHandler;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final List<MediaItem> mediaItems = new ArrayList<>();

    private MediaItem mediaItem;
    private boolean playWhenReady;
    private int playbackState = Player.STATE_IDLE;
    private long positionMs;
    private long durationMs = C.TIME_UNSET;
    private float volume = 1f;
    private PlaybackParameters playbackParameters = PlaybackParameters.DEFAULT;
    private Surface surface;
    private boolean prepared;
    private boolean released;
    private boolean endOfStream;
    private long videoWidth = C.LENGTH_UNSET;
    private long videoHeight = C.LENGTH_UNSET;
    private int audioChannelCount = 2;
    @Nullable private MpvAudioFilter audioFilter;
    @Nullable private MpvAudioFilter audioOutputListener;

    private MpvPlayer(Builder builder) {
        this.context = builder.context.getApplicationContext();
        this.config = builder.config;
        this.decode = builder.decode;
        this.mainHandler = new Handler(Looper.getMainLooper());
        initialize();
    }

    private void initialize() {
        MPVLib.create(context);
        applyConfig();
        MPVLib.init();
        MPVLib.addObserver(this);
        MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        MPVLib.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        MPVLib.observeProperty("width", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        MPVLib.observeProperty("height", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        MPVLib.observeProperty("audio-params/channel-count", MPVLib.MpvFormat.MPV_FORMAT_INT64);
    }

    private void applyConfig() {
        // 基础渲染选项（vo 用 gpu-next；hwdec 按解码偏好：SOFT=0 关闭硬解、HARD=1 auto-safe）
        MPVLib.setOptionString("vo", "gpu-next");
        MPVLib.setOptionString("hwdec", decode == 0 ? "no" : "auto-safe");
        MPVLib.setOptionString("audio-channels", "2");
        MPVLib.setOptionString("cache", "yes");
        if (config != null) {
            if (config.androidOptions != null) {
                if (config.androidOptions.vulkanEnabled) {
                    MPVLib.setOptionString("gpu-context", "androidvulkan");
                    MPVLib.setOptionString("gpu-api", "vulkan");
                }
                if (config.androidOptions.gpuNextEnabled) {
                    MPVLib.setOptionString("gpu-next", "yes");
                }
                if (config.androidOptions.audioPassthroughEnabled) {
                    MPVLib.setOptionString("audio-spdif", "yes");
                }
            }
            if (config.configDirectory != null) {
                MPVLib.setOptionString("config-dir", config.configDirectory.getAbsolutePath());
            }
        }
    }

    // ==================== 播放驱动 ====================

    private void loadMedia() {
        if (mediaItem == null || mediaItem.localConfiguration == null
                || mediaItem.localConfiguration.uri == null) {
            return;
        }
        Uri uri = mediaItem.localConfiguration.uri;
        endOfStream = false;
        MPVLib.enqueueCommand(0, new String[]{"loadfile", uri.toString(), "replace"});
    }

    private void setPause(boolean pause) {
        MPVLib.enqueueCommand(0, new String[]{"set_property", "pause", pause ? "yes" : "no"});
    }

    private void seekMpv(long positionMs) {
        MPVLib.enqueueCommand(0, new String[]{"seek", String.valueOf(positionMs / 1000.0), "absolute"});
    }

    private void setSpeed(float speed) {
        MPVLib.enqueueCommand(0, new String[]{"set_property", "speed", String.valueOf(speed)});
    }

    private void setMpvVolume(float volume) {
        MPVLib.enqueueCommand(0, new String[]{"set_property", "volume", String.valueOf(volume * 100f)});
    }

    // ==================== MPVLib.EventObserver ====================

    @Override
    public void onEvent(int eventId) {
        mainHandler.post(() -> {
            if (released) return;
            switch (eventId) {
                case MPV_EVENT_START_FILE:
                    positionMs = 0;
                    setPlaybackState(Player.STATE_BUFFERING);
                    break;
                case MPV_EVENT_FILE_LOADED:
                    readDuration();
                    setPlaybackState(Player.STATE_READY);
                    break;
                case MPV_EVENT_END_FILE:
                    break;
                case MPV_EVENT_PLAYBACK_RESTART:
                    break;
                default:
                    break;
            }
        });
    }

    @Override
    public void onEventProperty(String name, Object value) {
        mainHandler.post(() -> {
            if (released) return;
            switch (name) {
                case "time-pos":
                    if (value instanceof Double) {
                        positionMs = (long) (((Double) value) * 1000);
                    }
                    break;
                case "duration":
                    if (value instanceof Double) {
                        durationMs = (long) (((Double) value) * 1000);
                        notifyTimelineChanged();
                    }
                    break;
                case "pause":
                    if (value instanceof Boolean) {
                        playWhenReady = !((Boolean) value);
                        notifyPlaybackStateChanged();
                        notifyIsPlayingChanged();
                    }
                    break;
                case "eof-reached":
                    if (value instanceof Boolean && (Boolean) value) {
                        endOfStream = true;
                        setPlaybackState(Player.STATE_ENDED);
                    }
                    break;
                case "width":
                    if (value instanceof Long) videoWidth = (Long) value;
                    notifyVideoSizeChanged();
                    break;
                case "height":
                    if (value instanceof Long) videoHeight = (Long) value;
                    notifyVideoSizeChanged();
                    break;
                case "audio-params/channel-count":
                    if (value instanceof Long) audioChannelCount = ((Long) value).intValue();
                    break;
                default:
                    break;
            }
        });
    }

    private void readDuration() {
        Double d = MPVLib.getPropertyDouble("duration");
        if (d != null && d > 0) {
            durationMs = (long) (d * 1000);
        }
    }

    // ==================== 状态通知 ====================

    private void setPlaybackState(int state) {
        if (playbackState != state) {
            playbackState = state;
            notifyPlaybackStateChanged();
            notifyIsPlayingChanged();
        }
    }

    private void notifyPlaybackStateChanged() {
        for (Listener listener : listeners) {
            listener.onPlaybackStateChanged(playbackState);
        }
    }

    private void notifyIsPlayingChanged() {
        for (Listener listener : listeners) {
            listener.onIsPlayingChanged(isPlaying());
        }
    }

    private void notifyMediaItemTransition() {
        for (Listener listener : listeners) {
            listener.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
        }
    }

    private void notifyPlaybackParametersChanged() {
        for (Listener listener : listeners) {
            listener.onPlaybackParametersChanged(playbackParameters);
        }
    }

    private void notifyVolumeChanged() {
        for (Listener listener : listeners) {
            listener.onVolumeChanged(volume);
        }
    }

    private void notifyPositionDiscontinuity() {
        for (Listener listener : listeners) {
            listener.onPositionDiscontinuity(
                    Player.DISCONTINUITY_REASON_SEEK);
        }
    }

    private void notifyTimelineChanged() {
        for (Listener listener : listeners) {
            listener.onTimelineChanged(getCurrentTimeline(), Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
        }
    }

    private void notifyVideoSizeChanged() {
        for (Listener listener : listeners) {
            listener.onVideoSizeChanged(getVideoSize());
        }
    }

    // ==================== 自定义 API（5.6.0 调用面） ====================

    public int getVideoEffectsSupport() {
        return VIDEO_EFFECTS_SUPPORTED;
    }

    public boolean isVideoSharpnessSupported() {
        return true;
    }

    public int getAudioEffectsSupport() {
        return AUDIO_EFFECTS_SUPPORTED;
    }

    public int getAudioChannelCount() {
        return audioChannelCount;
    }

    public boolean setAudioFilter(MpvAudioFilter filter) {
        this.audioFilter = filter;
        return true;
    }

    public void setAudioOutputListener(@Nullable MpvAudioFilter listener) {
        this.audioOutputListener = listener;
    }

    public void addSubtitle(MediaItem.SubtitleConfiguration config) {
        if (config == null || config.uri == null) return;
        MPVLib.enqueueCommand(0, new String[]{"sub-add", config.uri.toString(), "auto"});
    }

    public void setDecode(int decode) {
        // 播放中切换解码方式：通过 hwdec 属性
        MPVLib.enqueueCommand(0, new String[]{"set_property", "hwdec", decode == 0 ? "no" : "auto-safe"});
    }

    public void setSubtitleOptions(MpvSubtitleOptions options) {
        if (options == null) return;
        MPVLib.enqueueCommand(0, new String[]{"set_property", "sub-font-size", String.valueOf(options.scale * 100)});
        MPVLib.enqueueCommand(0, new String[]{"set_property", "sub-pos", String.valueOf(options.position)});
    }

    public void setVideoEqualizer(MpvVideoEqualizer equalizer) {
        if (equalizer == null) return;
        MPVLib.enqueueCommand(0, new String[]{"set_property", "brightness", String.valueOf(equalizer.brightness)});
        MPVLib.enqueueCommand(0, new String[]{"set_property", "contrast", String.valueOf(equalizer.contrast)});
        MPVLib.enqueueCommand(0, new String[]{"set_property", "saturation", String.valueOf(equalizer.saturation)});
    }

    // ==================== Player 接口 ====================

    @Override
    public Looper getApplicationLooper() {
        return Looper.getMainLooper();
    }

    @Override
    public void addListener(Listener listener) {
        listeners.addIfAbsent(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems) {
        this.mediaItems.clear();
        this.mediaItems.addAll(mediaItems);
        this.mediaItem = mediaItems.isEmpty() ? null : mediaItems.get(0);
        notifyMediaItemTransition();
        notifyTimelineChanged();
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
        setMediaItems(mediaItems);
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        setMediaItems(mediaItems);
        this.positionMs = startPositionMs;
    }

    @Override
    public void setMediaItem(MediaItem mediaItem) {
        List<MediaItem> list = new ArrayList<>();
        if (mediaItem != null) list.add(mediaItem);
        setMediaItems(list);
    }

    @Override
    public void setMediaItem(MediaItem mediaItem, long startPositionMs) {
        setMediaItem(mediaItem);
        this.positionMs = startPositionMs;
    }

    @Override
    public void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
        setMediaItem(mediaItem);
    }

    @Override
    public void addMediaItem(MediaItem mediaItem) {
        mediaItems.add(mediaItem);
    }

    @Override
    public void addMediaItem(int index, MediaItem mediaItem) {
        mediaItems.add(index, mediaItem);
    }

    @Override
    public void addMediaItems(List<MediaItem> mediaItems) {
        this.mediaItems.addAll(mediaItems);
    }

    @Override
    public void addMediaItems(int index, List<MediaItem> mediaItems) {
        this.mediaItems.addAll(index, mediaItems);
    }

    @Override
    public void moveMediaItem(int currentIndex, int newIndex) {
        if (currentIndex >= 0 && currentIndex < mediaItems.size()) {
            MediaItem item = mediaItems.remove(currentIndex);
            mediaItems.add(Math.min(newIndex, mediaItems.size()), item);
        }
    }

    @Override
    public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    }

    @Override
    public void replaceMediaItem(int index, MediaItem mediaItem) {
        if (index >= 0 && index < mediaItems.size()) {
            mediaItems.set(index, mediaItem);
        }
    }

    @Override
    public void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
    }

    @Override
    public void removeMediaItem(int index) {
        if (index >= 0 && index < mediaItems.size()) {
            mediaItems.remove(index);
        }
    }

    @Override
    public void removeMediaItems(int fromIndex, int toIndex) {
    }

    @Override
    public void clearMediaItems() {
        mediaItems.clear();
        mediaItem = null;
    }

    @Override
    public boolean isCommandAvailable(int command) {
        return getAvailableCommands().contains(command);
    }

    @Override
    public boolean canAdvertiseSession() {
        return false;
    }

    @Override
    public Commands getAvailableCommands() {
        Commands.Builder builder = new Commands.Builder();
        builder.add(COMMAND_PLAY_PAUSE)
                .add(COMMAND_PREPARE)
                .add(COMMAND_STOP)
                .add(COMMAND_SEEK_TO_DEFAULT_POSITION)
                .add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(COMMAND_SEEK_TO_PREVIOUS)
                .add(COMMAND_SEEK_TO_NEXT)
                .add(COMMAND_SET_REPEAT_MODE)
                .add(COMMAND_SET_SHUFFLE_MODE)
                .add(COMMAND_SET_SPEED_AND_PITCH)
                .add(COMMAND_SET_VOLUME)
                .add(COMMAND_SET_VIDEO_SURFACE)
                .add(COMMAND_GET_CURRENT_MEDIA_ITEM)
                .add(COMMAND_GET_TIMELINE)
                .add(COMMAND_GET_METADATA)
                .add(COMMAND_SET_TRACK_SELECTION_PARAMETERS);
        return builder.build();
    }

    @Override
    public void prepare() {
        if (released || mediaItem == null) return;
        prepared = true;
        loadMedia();
        setPlaybackState(Player.STATE_BUFFERING);
    }

    @Override
    public int getPlaybackState() {
        return playbackState;
    }

    @Override
    public int getPlaybackSuppressionReason() {
        return Player.PLAYBACK_SUPPRESSION_REASON_NONE;
    }

    @Override
    public boolean isPlaying() {
        return playWhenReady && playbackState == Player.STATE_READY;
    }

    @Override
    public PlaybackException getPlayerError() {
        return null;
    }

    @Override
    public void play() {
        setPlayWhenReady(true);
    }

    @Override
    public void pause() {
        setPlayWhenReady(false);
    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {
        if (this.playWhenReady == playWhenReady) return;
        this.playWhenReady = playWhenReady;
        setPause(!playWhenReady);
        notifyPlaybackStateChanged();
        notifyIsPlayingChanged();
    }

    @Override
    public boolean getPlayWhenReady() {
        return playWhenReady;
    }

    @Override
    public void setRepeatMode(int repeatMode) {
    }

    @Override
    public int getRepeatMode() {
        return Player.REPEAT_MODE_OFF;
    }

    @Override
    public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    }

    @Override
    public boolean getShuffleModeEnabled() {
        return false;
    }

    @Override
    public boolean isLoading() {
        return playbackState == Player.STATE_BUFFERING;
    }

    @Override
    public void seekToDefaultPosition() {
        seekTo(0);
    }

    @Override
    public void seekToDefaultPosition(int mediaItemIndex) {
        seekTo(0);
    }

    @Override
    public void seekTo(long positionMs) {
        if (released) return;
        this.positionMs = positionMs;
        seekMpv(positionMs);
        notifyPositionDiscontinuity();
    }

    @Override
    public void seekTo(int mediaItemIndex, long positionMs) {
        seekTo(positionMs);
    }

    @Override
    public long getSeekBackIncrement() {
        return C.DEFAULT_SEEK_BACK_INCREMENT_MS;
    }

    @Override
    public void seekBack() {
        seekTo(Math.max(0, getCurrentPosition() - getSeekBackIncrement()));
    }

    @Override
    public long getSeekForwardIncrement() {
        return C.DEFAULT_SEEK_FORWARD_INCREMENT_MS;
    }

    @Override
    public void seekForward() {
        seekTo(getCurrentPosition() + getSeekForwardIncrement());
    }

    @Override
    public boolean hasPreviousMediaItem() {
        return false;
    }

    @Override
    public void seekToPreviousMediaItem() {
    }

    @Override
    public long getMaxSeekToPreviousPosition() {
        return C.TIME_UNSET;
    }

    @Override
    public void seekToPrevious() {
    }

    @Override
    public boolean hasNextMediaItem() {
        return false;
    }

    @Override
    public void seekToNextMediaItem() {
    }

    @Override
    public void seekToNext() {
    }

    @Override
    public void setPlaybackParameters(PlaybackParameters playbackParameters) {
        if (playbackParameters == null) return;
        this.playbackParameters = playbackParameters;
        setSpeed(playbackParameters.speed);
        notifyPlaybackParametersChanged();
    }

    @Override
    public void setPlaybackSpeed(float speed) {
        setPlaybackParameters(new PlaybackParameters(speed));
    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
        return playbackParameters;
    }

    @Override
    public void stop() {
        if (released) return;
        MPVLib.enqueueCommand(0, new String[]{"stop"});
        setPlaybackState(Player.STATE_IDLE);
    }

    @Override
    public void release() {
        if (released) return;
        released = true;
        MPVLib.removeObserver(this);
        MPVLib.destroy();
    }

    @Override
    public Tracks getCurrentTracks() {
        return Tracks.EMPTY;
    }

    @Override
    public TrackSelectionParameters getTrackSelectionParameters() {
        return TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;
    }

    @Override
    public void setTrackSelectionParameters(TrackSelectionParameters parameters) {
    }

    @Override
    public MediaMetadata getMediaMetadata() {
        return MediaMetadata.EMPTY;
    }

    @Override
    public MediaMetadata getPlaylistMetadata() {
        return MediaMetadata.EMPTY;
    }

    @Override
    public void setPlaylistMetadata(MediaMetadata mediaMetadata) {
    }

    @Override
    public Object getCurrentManifest() {
        return null;
    }

    @Override
    public Timeline getCurrentTimeline() {
        return new Timeline() {
            @Override
            public int getWindowCount() {
                return mediaItem == null ? 0 : 1;
            }

            @Override
            public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
                if (mediaItem != null) {
                    long durationUs = durationMs == C.TIME_UNSET ? C.TIME_UNSET : durationMs * 1000;
                    window.set(mediaItem, mediaItem, null, 0, 0, 0, durationUs != C.TIME_UNSET,
                            false, null, 0, durationUs, 0, 0, 0);
                }
                return window;
            }

            @Override
            public int getPeriodCount() {
                return mediaItem == null ? 0 : 1;
            }

            @Override
            public Period getPeriod(int periodIndex, Period period, boolean setIds) {
                if (mediaItem != null) {
                    long durationUs = durationMs == C.TIME_UNSET ? C.TIME_UNSET : durationMs * 1000;
                    period.set(mediaItem, mediaItem, 0, durationUs, 0);
                }
                return period;
            }

            @Override
            public Object getUidOfPeriod(int periodIndex) {
                return mediaItem;
            }

            @Override
            public int getIndexOfPeriod(Object uid) {
                return uid == mediaItem ? 0 : C.INDEX_UNSET;
            }
        };
    }

    @Override
    public int getCurrentPeriodIndex() {
        return 0;
    }

    @Override
    public int getCurrentWindowIndex() {
        return 0;
    }

    @Override
    public int getCurrentMediaItemIndex() {
        return 0;
    }

    @Override
    public int getNextWindowIndex() {
        return 0;
    }

    @Override
    public int getNextMediaItemIndex() {
        return 0;
    }

    @Override
    public int getPreviousWindowIndex() {
        return 0;
    }

    @Override
    public int getPreviousMediaItemIndex() {
        return 0;
    }

    @Override
    public MediaItem getCurrentMediaItem() {
        return mediaItem;
    }

    @Override
    public int getMediaItemCount() {
        return mediaItems.size();
    }

    @Override
    public MediaItem getMediaItemAt(int index) {
        return index >= 0 && index < mediaItems.size() ? mediaItems.get(index) : null;
    }

    @Override
    public long getDuration() {
        return durationMs;
    }

    @Override
    public long getCurrentPosition() {
        return positionMs;
    }

    @Override
    public long getBufferedPosition() {
        return positionMs;
    }

    @Override
    public int getBufferedPercentage() {
        return durationMs == C.TIME_UNSET || durationMs == 0 ? 0 : (int) (positionMs * 100 / durationMs);
    }

    @Override
    public long getTotalBufferedDuration() {
        return 0;
    }

    @Override
    public boolean isCurrentWindowDynamic() {
        return false;
    }

    @Override
    public boolean isCurrentMediaItemDynamic() {
        return false;
    }

    @Override
    public boolean isCurrentWindowLive() {
        return false;
    }

    @Override
    public boolean isCurrentMediaItemLive() {
        return false;
    }

    @Override
    public long getCurrentLiveOffset() {
        return C.TIME_UNSET;
    }

    @Override
    public boolean isCurrentWindowSeekable() {
        return durationMs != C.TIME_UNSET;
    }

    @Override
    public boolean isCurrentMediaItemSeekable() {
        return durationMs != C.TIME_UNSET;
    }

    @Override
    public boolean isPlayingAd() {
        return false;
    }

    @Override
    public int getCurrentAdGroupIndex() {
        return C.INDEX_UNSET;
    }

    @Override
    public int getCurrentAdIndexInAdGroup() {
        return C.INDEX_UNSET;
    }

    @Override
    public long getContentDuration() {
        return durationMs;
    }

    @Override
    public long getContentPosition() {
        return positionMs;
    }

    @Override
    public long getContentBufferedPosition() {
        return positionMs;
    }

    @Override
    public AudioAttributes getAudioAttributes() {
        return AudioAttributes.DEFAULT;
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public void setVolume(float volume) {
        if (this.volume == volume) return;
        this.volume = volume;
        setMpvVolume(volume);
        notifyVolumeChanged();
    }

    @Override
    public void mute() {
        setVolume(0f);
    }

    @Override
    public void unmute() {
        setVolume(1f);
    }

    @Override
    public void clearVideoSurface() {
        setVideoSurface(null);
    }

    @Override
    public void clearVideoSurface(Surface surface) {
        if (this.surface == surface) setVideoSurface(null);
    }

    @Override
    public void setVideoSurface(Surface surface) {
        this.surface = surface;
        if (surface != null) {
            MPVLib.attachSurface(surface);
        } else {
            MPVLib.detachSurface();
        }
    }

    @Override
    public void setVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
        setVideoSurface(surfaceHolder == null ? null : surfaceHolder.getSurface());
    }

    @Override
    public void clearVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
        setVideoSurface(null);
    }

    @Override
    public void setVideoSurfaceView(SurfaceView surfaceView) {
        setVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
    }

    @Override
    public void clearVideoSurfaceView(SurfaceView surfaceView) {
        setVideoSurface(null);
    }

    @Override
    public void setVideoTextureView(TextureView textureView) {
        // mpv 输出需 Surface；TextureView 场景由上层通过 setVideoSurface 提供
    }

    @Override
    public void clearVideoTextureView(TextureView textureView) {
        setVideoSurface(null);
    }

    @Override
    public VideoSize getVideoSize() {
        return videoWidth == C.LENGTH_UNSET || videoHeight == C.LENGTH_UNSET
                ? VideoSize.UNKNOWN : new VideoSize((int) videoWidth, (int) videoHeight);
    }

    @Override
    public Size getSurfaceSize() {
        return Size.UNKNOWN;
    }

    @Override
    public CueGroup getCurrentCues() {
        return CueGroup.EMPTY_TIME_ZERO;
    }

    @Override
    public DeviceInfo getDeviceInfo() {
        return DeviceInfo.UNKNOWN;
    }

    @Override
    public int getDeviceVolume() {
        return 0;
    }

    @Override
    public boolean isDeviceMuted() {
        return false;
    }

    @Override
    public void increaseDeviceVolume() {
    }

    @Override
    public void increaseDeviceVolume(int flags) {
    }

    @Override
    public void decreaseDeviceVolume() {
    }

    @Override
    public void decreaseDeviceVolume(int flags) {
    }

    @Override
    public void setDeviceMuted(boolean muted) {
    }

    @Override
    public void setDeviceMuted(boolean muted, int flags) {
    }

    @Override
    public void setDeviceVolume(int volume) {
    }

    @Override
    public void setDeviceVolume(int volume, int flags) {
    }

    @Override
    public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
    }

    // ==================== Builder ====================

    public static final class Builder {

        private final Context context;
        private int decode;
        private MpvPlayerConfig config;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setDecode(int decode) {
            this.decode = decode;
            return this;
        }

        public Builder setConfig(MpvPlayerConfig config) {
            this.config = config;
            return this;
        }

        public MpvPlayer build() {
            return new MpvPlayer(this);
        }
    }
}
