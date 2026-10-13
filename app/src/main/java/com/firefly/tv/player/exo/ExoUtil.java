package com.firefly.tv.player.exo;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager;
import androidx.media3.exoplayer.trackselection.DecodeTrackSelector;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.util.EventLogger;

import com.firefly.tv.App;
import com.firefly.tv.BuildConfig;
import com.firefly.tv.player.engine.PlayerEngine;
import com.firefly.tv.player.track.LangUtil;
import com.firefly.tv.setting.DecodeSetting;
import com.firefly.tv.setting.PlayerSetting;
import com.firefly.tv.setting.SpeedSetting;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ExoUtil {

    public static ExoPlayer buildPlayer(Player.Listener listener, DefaultPreloadManager.Builder preloadManagerBuilder) {
        ExoPlayer.Builder playerBuilder = new ExoPlayer.Builder(App.get()).setSkipSilenceEnabled(SpeedSetting.isSkipSilence());
        ExoPlayer player = preloadManagerBuilder.buildExoPlayer(playerBuilder);
        if (BuildConfig.DEBUG) player.addAnalyticsListener(new EventLogger());
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.setHandleAudioBecomingNoisy(true);
        player.setPlayWhenReady(true);
        player.addListener(listener);
        return player;
    }

    public static String getMimeType(int errorCode) {
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED || errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return MimeTypes.APPLICATION_M3U8;
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED) return MimeTypes.APPLICATION_OCTET_STREAM;
        return null;
    }

    static LoadControl buildLoadControl(int maxPreloadBufferBytes) {
        int buffer = PlayerSetting.getBuffer();
        return new DefaultLoadControl.Builder().setBufferDurationsMs(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS * buffer, DefaultLoadControl.DEFAULT_MAX_BUFFER_MS * buffer, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS).setPlayerTargetBufferBytes(PlayerId.PRELOAD.name, maxPreloadBufferBytes).build();
    }

    public static Map<String, String> extractHeaders(MediaItem item) {
        Bundle extras = item.requestMetadata.extras;
        if (extras == null) return new HashMap<>();
        return extras.keySet().stream().filter(key -> extras.getString(key) != null).collect(Collectors.toMap(key -> key, extras::getString));
    }

    static DecodeTrackSelector buildTrackSelector(int decode) {
        DecodeTrackSelector trackSelector = new DecodeTrackSelector(App.get());
        DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
        if (DecodeSetting.isPreferAAC()) builder.setPreferredAudioMimeType(MimeTypes.AUDIO_AAC);
        builder.setPreferredTextLanguages(LangUtil.getPreferredTextLanguages());
        builder.setTunnelingEnabled(DecodeSetting.isTunnelingEnabled());
        builder.setForceHighestSupportedBitrate(true);
        trackSelector.setParameters(builder.build());
        setDecodePreferences(trackSelector, decode);
        return trackSelector;
    }

    static void setDecodePreferences(DecodeTrackSelector trackSelector, int decode) {
        int audioDecode = isAudioSoftwareDecode(decode) ? PlayerEngine.SOFT : PlayerEngine.HARD;
        int videoDecode = isVideoSoftwareDecode(decode) ? PlayerEngine.SOFT : PlayerEngine.HARD;
        trackSelector.setRendererDecodePreferences(audioDecode, videoDecode);
    }

    private static boolean isAudioSoftwareDecode(int decode) {
        return decode == PlayerEngine.SOFT && DecodeSetting.isAudioPrefer();
    }

    private static boolean isVideoSoftwareDecode(int decode) {
        return decode == PlayerEngine.SOFT && DecodeSetting.isVideoPrefer();
    }

    static RenderersFactory buildRenderersFactory() {
        return buildRenderersFactory(null);
    }

    static RenderersFactory buildRenderersFactory(AudioProcessor audioProcessor) {
        DefaultRenderersFactory factory = new DefaultRenderersFactory(App.get()) {
            @Override
            protected AudioSink buildAudioSink(@NonNull Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
                return ExoUtil.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams, audioProcessor);
            }

            @Override
            protected void buildAudioRenderers(@NonNull Context context, int extensionRendererMode, @NonNull androidx.media3.exoplayer.mediacodec.MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, @NonNull AudioSink audioSink, @NonNull android.os.Handler eventHandler, @NonNull androidx.media3.exoplayer.audio.AudioRendererEventListener eventListener, @NonNull java.util.ArrayList<androidx.media3.exoplayer.Renderer> out) {
                super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out);
                // FFmpeg 音频软解（downmix 补丁版）：AC3/DTS 等设备硬解不支持时的兜底
                try {
                    out.add(new androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer(eventHandler, eventListener, audioSink));
                } catch (Throwable ignored) {
                }
            }
        };
        // media3 fork 无 setDolbyVisionOutputPolicy()，杜比视界输出策略降级为默认
        return factory.setEnableDecoderFallback(true).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
    }

    private static AudioSink buildAudioSink(Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams, AudioProcessor audioProcessor) {
        DefaultAudioSink.Builder builder = new DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput).setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams);
        if (!DecodeSetting.isAudioPassThrough()) builder.setAudioOutputProvider(new AudioTrackAudioOutputProvider.Builder(null).build());
        if (audioProcessor != null) builder.setAudioProcessors(new AudioProcessor[]{audioProcessor});
        return builder.build();
    }
}
