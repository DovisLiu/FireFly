package com.firefly.tv.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.firefly.tv.App;
import com.firefly.tv.bean.Backup;
import com.firefly.tv.bean.Config;
import com.firefly.tv.bean.Device;
import com.firefly.tv.bean.History;
import com.firefly.tv.bean.Keep;
import com.firefly.tv.bean.Live;
import com.firefly.tv.bean.Site;
import com.firefly.tv.bean.Track;
import com.firefly.tv.db.dao.ConfigDao;
import com.firefly.tv.db.dao.DeviceDao;
import com.firefly.tv.db.dao.HistoryDao;
import com.firefly.tv.db.dao.KeepDao;
import com.firefly.tv.db.dao.LiveDao;
import com.firefly.tv.db.dao.SiteDao;
import com.firefly.tv.db.dao.TrackDao;
import com.firefly.tv.utils.FileUtil;
import com.firefly.tv.utils.Formatters;
import com.firefly.tv.utils.Task;
import com.github.catvod.utils.Path;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Database(entities = {Keep.class, Site.class, Live.class, Track.class, Config.class, Device.class, History.class}, version = AppDatabase.VERSION)
public abstract class AppDatabase extends RoomDatabase {

    public static final int VERSION = 35;
    public static final String NAME = "tv";
    public static final String SYMBOL = "@@@";

    private static volatile AppDatabase instance;

    public static synchronized AppDatabase get() {
        if (instance == null) instance = create(App.get());
        return instance;
    }

    public static void backup() {
        backup(new com.firefly.tv.impl.Callback());
    }

    public static void backup(com.firefly.tv.impl.Callback callback) {
        Task.execute(() -> {
            File file = new File(Path.tv(), "tv-" + LocalDate.now().format(Formatters.DATE) + ".bk");
            Backup backup = Backup.create();
            if (backup.getConfig().isEmpty()) {
                App.post(callback::error);
            } else {
                Path.write(file, backup.toString().getBytes());
                FileUtil.gzipCompress(file);
                App.post(callback::success);
                cleanOld();
            }
        });
    }

    public static void restore(File file, com.firefly.tv.impl.Callback callback) {
        Task.execute(() -> {
            File restore = Path.cache("restore");
            FileUtil.gzipDecompress(file, restore);
            Backup backup = Backup.objectFrom(Path.read(restore));
            if (backup.getConfig().isEmpty()) {
                App.post(callback::error);
            } else {
                backup.restore();
                Path.clear(restore);
                App.post(callback::success);
            }
        });
    }

    private static void cleanOld() {
        List<File> items = new ArrayList<>();
        File[] files = Path.tv().listFiles();
        if (files == null) files = new File[0];
        for (File file : files) if (file.getName().startsWith("tv") && file.getName().endsWith(".bk.gz")) items.add(file);
        if (!items.isEmpty()) items.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        if (items.size() > 7) for (int i = 7; i < items.size(); i++) Path.clear(items.get(i));
    }

    private static AppDatabase create(Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, NAME)
                .addMigrations(Migrations.MIGRATION_30_31)
                .addMigrations(Migrations.MIGRATION_31_32)
                .addMigrations(Migrations.MIGRATION_32_33)
                .addMigrations(Migrations.MIGRATION_33_34)
                .addMigrations(Migrations.MIGRATION_34_35)
                .fallbackToDestructiveMigration(true)
                .allowMainThreadQueries().build();
    }

    public abstract KeepDao getKeepDao();

    public abstract SiteDao getSiteDao();

    public abstract LiveDao getLiveDao();

    public abstract TrackDao getTrackDao();

    public abstract ConfigDao getConfigDao();

    public abstract DeviceDao getDeviceDao();

    public abstract HistoryDao getHistoryDao();
}
