package com.firefly.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.firefly.tv.bean.Keep;

import java.util.List;

@Dao
public abstract class KeepDao extends BaseDao<Keep> {

    @Query("SELECT * FROM Keep")
    public abstract List<Keep> findAll();

    @Query("SELECT * FROM Keep WHERE type = 0 ORDER BY createTime DESC")
    public abstract List<Keep> getVod();

    // T-212：展示链（首页收藏行 / 我的收藏页）按当前仓库取，全量版仅供备份与同步导出使用，
    // 两者不得混用——否则换源过滤会让备份悄悄漏掉其它源的收藏数据。
    @Query("SELECT * FROM Keep WHERE type = 0 AND cid = :cid ORDER BY createTime DESC")
    public abstract List<Keep> getVod(int cid);

    @Query("SELECT * FROM Keep WHERE type = 1 ORDER BY createTime DESC")
    public abstract List<Keep> getLive();

    @Query("SELECT * FROM Keep WHERE type = 0 AND cid = :cid AND `key` = :key")
    public abstract Keep find(int cid, String key);

    @Query("SELECT * FROM Keep WHERE type = 1 AND `key` = :key")
    public abstract Keep find(String key);

    @Query("DELETE FROM Keep WHERE type = 1 AND `key` = :key")
    public abstract void delete(String key);

    @Query("DELETE FROM Keep WHERE type = 0 AND cid = :cid AND `key` = :key")
    public abstract void delete(int cid, String key);

    @Query("DELETE FROM Keep WHERE type = 0 AND cid = :cid")
    public abstract void delete(int cid);

    @Query("DELETE FROM Keep WHERE type = 0")
    public abstract void delete();
}
