package com.firefly.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.firefly.tv.bean.Site;

import java.util.List;

@Dao
public abstract class SiteDao extends BaseDao<Site> {

    @Query("SELECT * FROM Site")
    public abstract List<Site> findAll();
}
