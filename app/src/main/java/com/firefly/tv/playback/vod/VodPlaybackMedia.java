package com.firefly.tv.playback.vod;

import androidx.media3.common.MediaMetadata;

import com.firefly.tv.api.DanmakuApi;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.bean.Danmaku;
import com.firefly.tv.bean.Episode;
import com.firefly.tv.bean.History;
import com.firefly.tv.bean.Result;
import com.firefly.tv.player.media.MediaItemFactory;
import com.firefly.tv.setting.DanmakuSetting;

import java.util.function.Consumer;

public final class VodPlaybackMedia {

    public static MediaMetadata metadata(History history, Episode episode) {
        String title = history.getVodName();
        String name = episode.getName();
        if (name.equals(title)) name = "";
        return MediaItemFactory.buildMetadata(title, name, history.getVodPic(), name);
    }

    public static void searchDanmaku(Result result, History history, Episode episode, Consumer<Danmaku> set, Consumer<Danmaku> add) {
        if (!DanmakuApi.canSearch()) return;
        if (VodConfig.get().getSite(result.getKey()).getDanmaku() == 0) return;
        DanmakuApi.search(history.getVodName(), episode.getName(), danmaku -> {
            if (DanmakuSetting.isSpiderFirst() && !result.getDanmaku().isEmpty()) add.accept(danmaku);
            else set.accept(danmaku);
        });
    }
}
