package com.firefly.tv.bean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.firefly.tv.App;
import com.firefly.tv.api.config.VodConfig;
import com.firefly.tv.db.AppDatabase;
import com.firefly.tv.impl.Diffable;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
public class Keep implements Diffable<Keep> {

    @NonNull
    @PrimaryKey
    @SerializedName("key")
    private String key;
    @SerializedName("siteName")
    private String siteName;
    @SerializedName("vodName")
    private String vodName;
    @SerializedName("vodPic")
    private String vodPic;
    // β18 / T-213：收藏行「更新进度」角标的列（35→36 新增，可空＝老行为 NULL，渲染按空隐藏）
    @SerializedName("vodRemarks")
    private String vodRemarks;
    @SerializedName("createTime")
    private long createTime;
    @SerializedName("type")
    private int type;
    @SerializedName("cid")
    private int cid;

    public static List<Keep> arrayFrom(String str) {
        Type listType = TypeToken.getParameterized(List.class, Keep.class).getType();
        List<Keep> items;
        try {
            items = App.gson().fromJson(str, listType);
        } catch (Throwable t) {
            //T-186：远端下发的收藏同步包坏了一条不能连累整批，更不能崩
            FavLog.item("skip", "bad", null, t.getClass().getSimpleName());
            return Collections.emptyList();
        }
        if (items == null) return Collections.emptyList();
        List<Keep> result = new ArrayList<>();
        for (Keep item : items) {
            if (item == null || item.key == null || item.key.isEmpty()) {
                FavLog.item("skip", "nullKey", null, null);
                continue;
            }
            //入站即洗：null 字段补空串，避免同步把坏行写进库、下次启动再崩
            item.sanitize();
            result.add(item);
        }
        return result;
    }

    public static Keep find(String key) {
        return find(VodConfig.getCid(), key);
    }

    public static Keep find(int cid, String key) {
        return AppDatabase.get().getKeepDao().find(cid, key);
    }

    public static boolean exist(String key) {
        return AppDatabase.get().getKeepDao().find(key) != null;
    }

    public static void deleteAll() {
        AppDatabase.get().getKeepDao().delete();
    }

    public static void delete(int cid) {
        AppDatabase.get().getKeepDao().delete(cid);
    }

    public static void delete(String key) {
        AppDatabase.get().getKeepDao().delete(key);
    }

    public static List<Keep> getVod() {
        return AppDatabase.get().getKeepDao().getVod();
    }

    /**
     * T-212（β2 粒度修正）：展示链口径＝**当前仓库（cid）**可见，不再叠站点粒度。
     * β1 用的是「当前仓库 ＋ 当前首页源」双级过滤，C 在 beta31 实测
     * {@code filter kind=vod in=3 out=0}、{@code kind=history in=8 out=0} 且 {@code home=set}——
     * 站点级把同一仓库内其它站点的收藏/历史全滤掉了，首页两行近乎恒空；而切回原站点在片源弹窗里做不到
     * （SiteDialog 只列**当前仓库**的站点，见 SiteAdapter.addAll），于是观感＝"行再也回不来"。
     * 仓库粒度同时满足 A 的三条判据：冷启与 beta29 一致显示、切仓库后整行隐藏、切回原仓库原样恢复。
     * 备份/同步导出必须走全量 {@link #getVod()}，不得用本方法。
     * homeLen 仍照打（=0 即未配置首页源），供 C 判"键是否就绪"。
     */
    public static List<Keep> getVodCurrent() {
        int cid = VodConfig.getCid();
        List<Keep> items = AppDatabase.get().getKeepDao().getVod(cid);
        if (items == null) items = Collections.emptyList();
        FavLog.filter("vod", true, FavLog.len(VodConfig.get().getHome().getKey()), cid, items.size(), items.size());
        return items;
    }

    public static List<Keep> getLive() {
        return AppDatabase.get().getKeepDao().getLive();
    }

    /**
     * T-186 第 4 条（启动容错，最关键）：影视收藏的安全解包读取。
     * 任何单条坏数据只跳过该条并记日志，整体查询抛异常也只返回空列表——**绝不向调用方冒泡，绝不阻断启动**。
     */
    public static List<Keep> loadVodSafe() {
        return loadSafe("vod", true);
    }

    /** T-186 第 4 条：直播收藏的安全解包读取，口径与 {@link #loadVodSafe()} 一致。 */
    public static List<Keep> loadLiveSafe() {
        return loadSafe("live", false);
    }

    private static List<Keep> loadSafe(String kind, boolean vod) {
        List<Keep> result = new ArrayList<>();
        List<Keep> raw;
        try {
            raw = vod ? getVodCurrent() : getLive();
        } catch (Throwable t) {
            FavLog.load(kind, false, 0, 0, 0, t.getClass().getSimpleName());
            return result;
        }
        if (raw == null) {
            FavLog.load(kind, true, 0, 0, 0, null);
            return result;
        }
        int skip = 0;
        int fixed = 0;
        for (Keep item : raw) {
            try {
                if (item == null || item.key == null || item.key.isEmpty()) {
                    //主键缺失无法定位身份，也不得回写（Room 会因 not-null 列拒绝绑定）
                    skip++;
                    FavLog.item("skip", "nullKey", null, null);
                    continue;
                }
                if (item.sanitize()) {
                    fixed++;
                    String err = null;
                    try {
                        item.save();
                    } catch (Throwable t) {
                        err = t.getClass().getSimpleName();
                    }
                    //回写失败不影响本条展示（内存中已洗成干净值），err 字段供 C 判断坏行是否真被修掉
                    FavLog.item("fix", "nullField", item.key, err);
                }
                result.add(item);
            } catch (Throwable t) {
                skip++;
                FavLog.item("skip", "bad", null, t.getClass().getSimpleName());
            }
        }
        FavLog.load(kind, true, result.size(), skip, fixed, null);
        return result;
    }

    /**
     * T-186 第 4 条：把可空字段的 null 就地补成空串（坏行修复）。
     *
     * @return 真的改动过字段、需要回写数据库时为 true
     */
    public boolean sanitize() {
        boolean changed = false;
        if (vodName == null) {
            vodName = "";
            changed = true;
        }
        if (vodPic == null) {
            vodPic = "";
            changed = true;
        }
        if (siteName == null) {
            siteName = "";
            changed = true;
        }
        // β18 / T-213：新列同样纳入 T-186 的「null 不落库」守卫（远端 sync／旧备份导入都可能带 null）
        if (vodRemarks == null) {
            vodRemarks = "";
            changed = true;
        }
        return changed;
    }

    public static void sync(List<Config> configs, List<Keep> targets) {
        // T-186：远端同步一条坏目标只跳过该条，不连累整批，也不让服务端线程崩掉
        targets.forEach(target -> {
            try {
                configs.stream()
                        .filter(config -> target.getCid() == config.getId()).findFirst()
                        .ifPresent(config -> target.save(Config.find(config).getId()));
            } catch (Throwable t) {
                FavLog.write("add", false, target == null ? null : target.key, null, t.getClass().getSimpleName());
            }
        });
    }

    public static void replace(String oldKey, String newKey) {
        if (oldKey == null || oldKey.equals(newKey)) return;
        // T-186：新 key 不合法就不动老行——否则删了旧的、新的写不进去，收藏会静默丢失
        if (newKey == null || newKey.isEmpty()) {
            FavLog.write("add", false, newKey, null, "emptyKey");
            return;
        }
        Keep keep = find(oldKey);
        if (keep == null) return;
        keep.delete();
        keep.setKey(newKey);
        keep.save();
    }

    @NonNull
    public String getKey() {
        return key;
    }

    public void setKey(@NonNull String key) {
        this.key = key;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getVodName() {
        return vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodPic() {
        return vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public String getVodRemarks() {
        return vodRemarks;
    }

    public void setVodRemarks(String vodRemarks) {
        this.vodRemarks = vodRemarks;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getCid() {
        return cid;
    }

    public void setCid(int cid) {
        this.cid = cid;
    }

    public String getSiteKey() {
        return segment(0);
    }

    public String getVodId() {
        return segment(1);
    }

    /**
     * T-186 第 2 条：key 形如 {@code site@@@id@@@cid}，按 {@link AppDatabase#SYMBOL} 取第 index 段。
     * key 为 null、段数不足或该段为空一律返回空串，**不得再出现 ArrayIndexOutOfBoundsException**。
     * 注意 String.split 会丢弃尾部空段，所以 {@code site@@@} 只有 1 段，正是原来崩的位置。
     */
    private String segment(int index) {
        if (key == null || key.isEmpty()) return "";
        String[] parts = key.split(AppDatabase.SYMBOL);
        return parts.length > index && parts[index] != null ? parts[index] : "";
    }

    public void save(int cid) {
        String siteKey = getSiteKey();
        String vodId = getVodId();
        //T-186：坏 key（段数不足）不重洗，否则会把 "@@@1" 这类残片写成主键，收藏彻底失去身份
        if (!siteKey.isEmpty() && !vodId.isEmpty()) {
            setKey(siteKey.concat(AppDatabase.SYMBOL).concat(vodId).concat(AppDatabase.SYMBOL) + cid);
        }
        setCid(cid);
        save();
    }

    public void save() {
        // T-186 第 3 条（写入前守卫，收口在所有调用方之上）：
        // 详情页 toggleKeep、播放页 createKeep/updateKeep、直播 addKeep、远端 sync 全走这里，
        // 任一路径漏判 null 都会把 null 落库，下次冷启动首页 DiffUtil 比对内容时 NPE。
        if (key == null || key.isEmpty()) {
            FavLog.write("add", false, key, vodPic, "emptyKey");
            return;
        }
        sanitize();
        AppDatabase.get().getKeepDao().insertOrUpdate(this);
    }

    public Keep delete() {
        AppDatabase.get().getKeepDao().delete(getCid(), getKey());
        return this;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Keep it)) return false;
        return Objects.equals(getKey(), it.getKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getKey());
    }

    @Override
    public boolean isSameItem(Keep other) {
        return equals(other);
    }

    @Override
    public boolean isSameContent(Keep other) {
        if (this == other) return true;
        if (other == null) return false;
        //T-186 第 1 条：vodName/vodPic/siteKey/vodId 任一为 null 都不得 NPE。
        //逐字段用 Objects.equals（null 与 null 视为相同，null 与 "" 视为不同→触发一次重绑，可接受）；
        //siteKey/vodId 走 segment() 恒不抛、恒不为 null，坏 key 降级成空串后仍能按内容身份比对。
        return Objects.equals(getVodName(), other.getVodName())
                && Objects.equals(getVodPic(), other.getVodPic())
                && Objects.equals(getSiteKey(), other.getSiteKey())
                && Objects.equals(getVodId(), other.getVodId())
                && getCreateTime() == other.getCreateTime();
    }
}
