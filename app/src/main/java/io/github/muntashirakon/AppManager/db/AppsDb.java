// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import io.github.muntashirakon.AppManager.db.dao.AppDao;
import io.github.muntashirakon.AppManager.db.dao.BackupDao;
import io.github.muntashirakon.AppManager.db.dao.BatterySampleDao;
import io.github.muntashirakon.AppManager.db.dao.FmFavoriteDao;
import io.github.muntashirakon.AppManager.db.dao.FreezeTypeDao;
import io.github.muntashirakon.AppManager.db.dao.LogFilterDao;
import io.github.muntashirakon.AppManager.db.dao.OpHistoryDao;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.db.entity.BatterySample;
import io.github.muntashirakon.AppManager.db.entity.FmFavorite;
import io.github.muntashirakon.AppManager.db.entity.FreezeType;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

@Database(entities = {App.class, LogFilter.class, Backup.class, OpHistory.class, FmFavorite.class, FreezeType.class,
        BatterySample.class}, version = 10)
public abstract class AppsDb extends RoomDatabase {
    private static AppsDb sAppsDb;

    public static final Migration M_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `op_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `time` INTEGER NOT NULL, `data` TEXT NOT NULL, `status` TEXT NOT NULL, `extra` TEXT)");
        }
    };
    public static final Migration M_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `fm_favorite` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `uri` TEXT NOT NULL, `init_uri` TEXT, `options` INTEGER NOT NULL, `order` INTEGER NOT NULL, `type` INTEGER NOT NULL)");
        }
    };
    public static final Migration M_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `freeze_type` (`package_name` TEXT NOT NULL, `type` INTEGER NOT NULL, PRIMARY KEY(`package_name`))");
        }
    };
    public static final Migration M_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `app` ADD COLUMN `is_only_data_installed` INTEGER NOT NULL DEFAULT 0");
        }
    };
    public static final Migration M_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS `file_hash`");
        }
    };

    // Fork: battery-history sampler storage. Column order and nullability must
    // match BatterySample exactly or Room rejects the migration at open time.
    public static final Migration M_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `battery_sample` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`ts` INTEGER NOT NULL, "
                    + "`duration` INTEGER NOT NULL, "
                    + "`uid` INTEGER NOT NULL, "
                    + "`package_name` TEXT, "
                    + "`wifi_bytes` INTEGER NOT NULL, "
                    + "`wifi_packets` INTEGER NOT NULL, "
                    + "`mobile_bytes` INTEGER NOT NULL, "
                    + "`mobile_packets` INTEGER NOT NULL, "
                    + "`radio_active_ms` INTEGER NOT NULL, "
                    + "`wakelock_ms` INTEGER NOT NULL, "
                    + "`wakeup_count` INTEGER NOT NULL, "
                    + "`cpu_ms` INTEGER NOT NULL, "
                    + "`fg_service_ms` INTEGER NOT NULL, "
                    + "`sensor_ms` INTEGER NOT NULL, "
                    + "`screen_on_ms` INTEGER NOT NULL, "
                    + "`deep_idle_ms` INTEGER NOT NULL, "
                    + "`light_idle_ms` INTEGER NOT NULL, "
                    + "`battery_level` INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_battery_sample_ts` ON `battery_sample` (`ts`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_battery_sample_uid` ON `battery_sample` (`uid`)");
        }
    };

    // Fork: mAh (where the device's power profile is real) plus the open-ended
    // extras map. Defaults must match BatterySample's @ColumnInfo defaultValue
    // exactly — Room compares them and refuses to open the DB on a mismatch.
    public static final Migration M_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `battery_sample` ADD COLUMN `power_mah` REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `battery_sample` ADD COLUMN `power_model_usable` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `battery_sample` ADD COLUMN `extras` TEXT");
        }
    };

    // Fork: battery voltage + charging state on the device row, for the
    // level/voltage chart at the top of the battery screen.
    public static final Migration M_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `battery_sample` ADD COLUMN `voltage_mv` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `battery_sample` ADD COLUMN `charging` INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static AppsDb getInstance() {
        if (sAppsDb == null) {
            sAppsDb = Room.databaseBuilder(ContextUtils.getContext(), AppsDb.class, "apps.db")
                    .addMigrations(M_2_3, M_3_4, M_4_5, M_5_6, M_6_7, M_7_8, M_8_9, M_9_10)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build();
            try {
                sAppsDb.appDao().getAll();
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        return sAppsDb;
    }

    public abstract AppDao appDao();

    public abstract BackupDao backupDao();

    public abstract LogFilterDao logFilterDao();

    public abstract OpHistoryDao opHistoryDao();

    public abstract FmFavoriteDao fmFavoriteDao();

    public abstract FreezeTypeDao freezeTypeDao();

    public abstract BatterySampleDao batterySampleDao();
}
