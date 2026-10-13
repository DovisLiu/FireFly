package com.firefly.tv.player.engine;

import static com.firefly.tv.player.engine.PlayerEngine.Type.EXO;
import static com.firefly.tv.player.engine.PlayerEngine.Type.MPV;

import androidx.media3.common.Player;

import com.firefly.tv.player.exo.ExoPlayerEngine;
import com.firefly.tv.player.media.PlaySpec;
import com.firefly.tv.player.mpv.MpvPlayerEngine;
import com.firefly.tv.setting.PlayerSetting;
import com.firefly.tv.utils.UrlUtil;

public final class PlayerEngineFactory {

    public static PlayerEngine create(int decode, Player.Listener listener) {
        return create(decode, resolve(), listener);
    }

    public static PlayerEngine create(int decode, PlaySpec spec, Player.Listener listener) {
        return create(decode, resolve(spec), listener);
    }

    public static PlayerEngine create(int decode, PlayerEngine.Type type, Player.Listener listener) {
        return switch (type) {
            case EXO -> new ExoPlayerEngine(decode, listener);
            case MPV -> new MpvPlayerEngine(decode, listener);
        };
    }

    public static boolean matches(PlayerEngine engine, PlaySpec spec) {
        return engine != null && engine.getType() == resolve(spec);
    }

    private static PlayerEngine.Type resolve(PlaySpec spec) {
        if (requiresExo(spec)) return EXO; // DRM/SMB 仅 EXO 支持，强制回退
        if (!isMpvReady()) return EXO;     // MPV 未就绪时回退
        // 用户设置优先：设置选 MPV 用 MPV，选 EXO 用 EXO
        return com.firefly.tv.setting.PlayerSetting.getEngine() == com.firefly.tv.setting.PlayerSetting.ENGINE_MPV ? MPV : EXO;
    }

    private static PlayerEngine.Type resolve() {
        if (!isMpvReady()) return EXO;
        return com.firefly.tv.setting.PlayerSetting.getEngine() == com.firefly.tv.setting.PlayerSetting.ENGINE_MPV ? MPV : EXO;
    }

    private static boolean requiresExo(PlaySpec spec) {
        return spec.getDrm() != null || "smb".equals(UrlUtil.scheme(spec.getUrl()));
    }

    private static boolean isMpvReady() {
        // mpvplayer AAR（含 libmpv.so 双 ABI）已接入 libs，MPV 引擎恢复
        return true;
    }
}
