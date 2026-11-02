package com.firefly.tv.api;

import androidx.annotation.NonNull;

import com.firefly.tv.api.parser.EpgParser;
import com.firefly.tv.api.parser.LiveParser;
import com.firefly.tv.bean.Channel;
import com.firefly.tv.bean.Epg;
import com.firefly.tv.bean.EpgData;
import com.firefly.tv.bean.Live;
import com.firefly.tv.bean.Result;
import com.firefly.tv.player.extractor.Source;
import com.firefly.tv.utils.Formatters;
import com.github.catvod.net.OkHttp;

import java.time.LocalDate;
import java.time.ZoneId;

public class LiveApi {

    public static void parse(@NonNull Live item) throws Exception {
        LiveParser.start(item.recent());
        // T-153（C beta5 实测：选台页 OK 进播放页 NPE Group.isHidden on null）：
        // 解析产生的 groups 可能含 null 元素（如源 JSON 数组含 null / 分组解析失败），
        // 各页面遍历 groups 调 group.isHidden() 会 NPE；此处统一清理 null 兜底（幂等、无副作用）。
        item.getGroups().removeIf(group -> group == null || group.isEmpty());
        // β51 屏蔽版（A 指令 4a，2026-11-01；起因＝beta77 真机项 1 不通过，TV-02 整体退回 2.1）：
        // **不再插入「收藏」分类** ⇒ 选台页永不出现在该分组。原先这里的三行是一个整体，本轮**三行一起撤**：
        // 只删 :31 那句 add 而留着下一句 applyKeepsToGroups，后者会无条件往 `items.get(0)` 塞台
        // （`api/config/LiveConfig.java:215` 的 forEach 目标就是第 0 组）⇒ 收藏过的频道被塞进**第一个真实分组**，
        // 把正常分组污染成"莫名多出几个台"。`applyKeepsToGroups` 本体一字未动（仍在 LiveConfig.java:209），
        // 它是本屏蔽的回滚点：恢复这三行即回到 beta76 的装配。
        // 数据一律不动：Keep 表 type=1 的已收藏行一条不删、不改，只是不再显示、不再写入。
    }

    public static boolean parseXml(@NonNull Live item) {
        return item.getEpgXml().stream().map(url -> startXml(item, url)).reduce(false, Boolean::logicalOr);
    }

    @NonNull
    public static Epg getEpg(@NonNull Channel item, @NonNull ZoneId zoneId) {
        String today = LocalDate.now(zoneId).format(Formatters.DATE);
        for (int offset : new int[]{-1, 0, 1}) fetchEpgDay(item, zoneId, offset);
        return item.getDataList().stream().filter(epg -> epg.equal(today)).findFirst().orElseGet(Epg::new).selected();
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item) throws Exception {
        Source.get().stop();
        Result result = item.result();
        result.setUrl(Source.get().fetch(result));
        return result;
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item, @NonNull EpgData data) throws Exception {
        Result result = getUrl(item);
        result.setUrl(item.getCatchup().format(result.getRealUrl(), data));
        if (item.isRtsp()) result.getHeader().put("rtsp_range", data.getRange());
        return result;
    }

    private static boolean startXml(Live item, String url) {
        try {
            EpgParser.start(item, url);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void fetchEpgDay(@NonNull Channel item, @NonNull ZoneId zoneId, int offset) {
        String date = LocalDate.now(zoneId).plusDays(offset).format(Formatters.DATE);
        String url = item.getEpg().replace("{date}", date);
        boolean need = url.startsWith("http") && item.getDataList().stream().noneMatch(epg -> epg.equal(date));
        if (need) item.setData(Epg.objectFrom(OkHttp.string(url), item.getTvgId(), zoneId));
    }
}
