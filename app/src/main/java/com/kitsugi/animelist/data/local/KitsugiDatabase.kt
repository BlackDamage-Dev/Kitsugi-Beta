package com.kitsugi.animelist.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MediaEntryEntity::class,
        PendingSyncEntity::class,
        TranslationCacheEntity::class,
        MediaMetaCacheEntity::class,
        ManagedAddonEntity::class,
        CloudstreamRepoEntity::class,
        CsPluginEntity::class,
        MangaChapterProgressEntity::class,  // ✨ Manga okuma ilerlemesi
        MangaSourceStateEntity::class,       // ✨ Manga source sağlık / istatistik
        MangaMappingEntity::class,
        MangaReadHistoryEntity::class,        // 📖 Okunan manga/novel bölüm geçmişi
        SearchHistoryEntity::class,
        HistoryEntity::class,                 // 🎬 İzleme geçmişi
        VideoEntity::class,                  // 📹 Video cache/çözümleme
        ExploreCacheEntity::class,           // 📂 Explore page cache
        PersistentDetailCacheEntity::class,  // 📂 Media details cache
        CustomButton::class,
        MangaLibraryEntity::class              // 📚 Manga kitaplığı (takip edilen seriler)
    ],
    version = 35,
    exportSchema = true  // T3-03/TASK-106: KSP → $projectDir/schemas/ konumuna yazar
)
abstract class KitsugiDatabase : RoomDatabase() {
    abstract fun mediaEntryDao(): MediaEntryDao
    abstract fun pendingSyncDao(): PendingSyncDao
    abstract fun translationCacheDao(): TranslationCacheDao
    abstract fun mediaMetaCacheDao(): MediaMetaCacheDao
    abstract fun managedAddonDao(): ManagedAddonDao
    abstract fun cloudstreamRepoDao(): CloudstreamRepoDao
    abstract fun csPluginDao(): CsPluginDao
    abstract fun mangaChapterProgressDao(): MangaChapterProgressDao  // ✨ Manga okuma ilerlemesi
    abstract fun mangaSourceStateDao(): MangaSourceStateDao         // ✨ Manga source sağlık / istatistik
    abstract fun mangaMappingDao(): MangaMappingDao
    abstract fun mangaReadHistoryDao(): MangaReadHistoryDao          // 📖 Okunan bölüm geçmişi
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun historyDao(): HistoryDao                           // 🎬 İzleme geçmişi
    abstract fun videoDao(): VideoDao                               // 📹 Video cache/çözümleme
    abstract fun exploreCacheDao(): ExploreCacheDao
    abstract fun persistentDetailCacheDao(): PersistentDetailCacheDao
    abstract fun customButtonDao(): CustomButtonDao
    abstract fun mangaLibraryDao(): MangaLibraryDao                  // 📚 Manga kitaplığı


    companion object {
        @Volatile
        private var INSTANCE: KitsugiDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE media_entries ADD COLUMN synopsis TEXT"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_entries ADD COLUMN startDate TEXT")
                db.execSQL("ALTER TABLE media_entries ADD COLUMN endDate TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v5 migration (mevcut)
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_syncs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `entryJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `retryCount` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `translation_cache` (
                        `originalHash` TEXT PRIMARY KEY NOT NULL,
                        `translatedText` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_entries ADD COLUMN titleEnglish TEXT")
                db.execSQL("ALTER TABLE media_entries ADD COLUMN titleJapanese TEXT")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_meta_cache` (
                        `tmdbId` INTEGER PRIMARY KEY NOT NULL,
                        `malId` INTEGER,
                        `aniListId` INTEGER,
                        `logoUrl` TEXT,
                        `logoNotFound` INTEGER NOT NULL DEFAULT 0,
                        `cachedAtMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_meta_cache_malId` ON `media_meta_cache` (`malId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_meta_cache_aniListId` ON `media_meta_cache` (`aniListId`)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `managed_addons` (
                        `manifestUrl` TEXT PRIMARY KEY NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT,
                        `icon` TEXT,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `orderIndex` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add idPrefixes and streamTypes columns to managed_addons.
                // Both are nullable TEXT — existing rows will have NULL (= no filter applied).
                db.execSQL("ALTER TABLE managed_addons ADD COLUMN idPrefixes TEXT")
                db.execSQL("ALTER TABLE managed_addons ADD COLUMN streamTypes TEXT")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cloudstream_repos` (
                        `repoUrl` TEXT PRIMARY KEY NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT,
                        `addedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cs_plugins` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `name` TEXT NOT NULL,
                        `downloadUrl` TEXT NOT NULL,
                        `tvTypes` TEXT NOT NULL,
                        `iconUrl` TEXT,
                        `version` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL DEFAULT 1,
                        `installedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_entries` ADD COLUMN `aniListEntryId` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `media_entries` ADD COLUMN `malListId` INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `manga_chapter_progress` (
                        `chapterUrl`    TEXT    PRIMARY KEY NOT NULL,
                        `mangaUrl`      TEXT    NOT NULL,
                        `chapterName`   TEXT    NOT NULL,
                        `lastPageIndex` INTEGER NOT NULL DEFAULT 0,
                        `totalPages`    INTEGER NOT NULL DEFAULT 0,
                        `isCompleted`   INTEGER NOT NULL DEFAULT 0,
                        `updatedAt`     INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_manga_progress_mangaUrl` ON `manga_chapter_progress` (`mangaUrl`)")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `manga_source_state` (
                        `sourceKey` TEXT PRIMARY KEY NOT NULL,
                        `sourceName` TEXT NOT NULL,
                        `pkgName` TEXT NOT NULL,
                        `lang` TEXT NOT NULL,
                        `baseUrl` TEXT NOT NULL,
                        `activeDomain` TEXT,
                        `healthStatus` TEXT NOT NULL,
                        `lastReason` TEXT,
                        `lastCheckedAt` INTEGER NOT NULL DEFAULT 0,
                        `lastSuccessAt` INTEGER NOT NULL DEFAULT 0,
                        `lastFailureAt` INTEGER NOT NULL DEFAULT 0,
                        `successCount` INTEGER NOT NULL DEFAULT 0,
                        `failureCount` INTEGER NOT NULL DEFAULT 0,
                        `avgSearchMs` INTEGER NOT NULL DEFAULT 0,
                        `avgPopularMs` INTEGER NOT NULL DEFAULT 0,
                        `avgDetailsMs` INTEGER NOT NULL DEFAULT 0,
                        `avgChapterMs` INTEGER NOT NULL DEFAULT 0,
                        `avgPageMs` INTEGER NOT NULL DEFAULT 0,
                        `avgImageMs` INTEGER NOT NULL DEFAULT 0,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_manga_source_state_healthStatus` ON `manga_source_state` (`healthStatus`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_manga_source_state_updatedAt` ON `manga_source_state` (`updatedAt`)")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `manga_source_state` ADD COLUMN `lastErrorType` TEXT")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `manga_mappings` (
                        `mediaId` INTEGER PRIMARY KEY NOT NULL,
                        `mangaSource` TEXT NOT NULL,
                        `mangaUrl` TEXT NOT NULL,
                        `mangaTitle` TEXT NOT NULL,
                        `mangaThumbnail` TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_entries` ADD COLUMN `tmdbId` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `media_entries` ADD COLUMN `simklId` INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `managed_addons` ADD COLUMN `subtitleTypes` TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `search_history` (
                        `query` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        PRIMARY KEY(`query`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `watch_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `mediaId` INTEGER NOT NULL,
                        `episode` INTEGER NOT NULL,
                        `lastPositionMs` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `lastWatchedAt` INTEGER NOT NULL,
                        `addonName` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_watch_history_mediaId_episode` ON `watch_history` (`mediaId`, `episode`)")
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `video_cache` (
                        `url` TEXT PRIMARY KEY NOT NULL,
                        `mediaId` INTEGER NOT NULL,
                        `episode` INTEGER NOT NULL,
                        `quality` TEXT NOT NULL,
                        `headersJson` TEXT,
                        `resolvedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_meta_cache ADD COLUMN kitsuId TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_meta_cache_kitsuId ON media_meta_cache(kitsuId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `explore_cache` (
                        `categoryKey` TEXT PRIMARY KEY NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `cachedAtMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `persistent_details_cache` (
                        `cacheKey` TEXT PRIMARY KEY NOT NULL,
                        `detailJson` TEXT NOT NULL,
                        `cachedAtMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_meta_cache ADD COLUMN tvdbId INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_meta_cache_tvdbId ON media_meta_cache(tvdbId)")
            }
        }

        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_buttons` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `sortIndex` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `longPressContent` TEXT NOT NULL,
                        `onStartup` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_entries ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cs_plugins ADD COLUMN repositoryUrl TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE managed_addons ADD COLUMN repositoryUrl TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE manga_source_state ADD COLUMN lastErrorCode TEXT")
                db.execSQL("ALTER TABLE manga_source_state ADD COLUMN cooldownUntil INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v30: `media_entries.addedAt` — kaydın listeye EKLENME zamanı (epoch sn).
         *
         * "Son eklenen" bugüne kadar yerel satır `id`'sine göre sıralanıyordu; toplu
         * içe aktarımda bu, kullanıcının ekleme sırası değil API sayfa sırasıdır
         * (AniList: MEDIA_ID). Mevcut satırlar için en iyi tahmin olarak `updatedAt`
         * geri doldurulur (Bangumi'nin milisaniye yazdığı satırlar saniyeye çevrilir);
         * AniList/Kitsu/Shikimori bir sonraki eşitlemede gerçek `createdAt` ile
         * bu tahmini düzeltir. Bkz. COZUM_RAPORU_LISTEM_SIRALAMA_SON_EKLENEN_2026-10-23.md
         */
        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_entries ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE media_entries SET addedAt = " +
                        "CASE WHEN updatedAt > 100000000000 THEN updatedAt / 1000 ELSE updatedAt END " +
                        "WHERE updatedAt > 0"
                )
            }
        }

        /**
         * v31: `media_entries.tmdbWatchlist` & `media_entries.tmdbRating`.
         * Native TMDB Watchlist üyeliği ve yarım puan (0.5..10) saklama desteği.
         */
        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Native TMDB Watchlist membership is independent from Kitsugi WatchStatus.
                db.execSQL("ALTER TABLE media_entries ADD COLUMN tmdbWatchlist INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE media_entries ADD COLUMN tmdbRating REAL DEFAULT NULL")
            }
        }

        private val MIGRATION_29_31 = object : Migration(29, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_29_30.migrate(db)
                MIGRATION_30_31.migrate(db)
            }
        }

        /**
         * v32: Otomatik liste takibi (`watch_history.watched`).
         *
         * `watch_history.watched`, "bu bölüm izleme eşiğini geçti mi?" sorusunun kalıcı
         * cevabıdır. Otomatik takip (`AutoTrackPolicy`) bölüm tamamlandığında bu bayrağı
         * yazar; böylece aynı bölüm ikinci kez ilerlemeye sayılmaz ve "kaldığı yerden
         * devam et" satırı izlenmiş bölümü tekrar önermez. Eski satırlar `0` (izlenmedi)
         * olarak gelir — geçmiş konumları korunur.
         */
        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN watched INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 📖 Okunan manga/novel bölüm geçmişi — geçmiş sayfasının "okunan" sekmesini besler.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `manga_read_history` (
                        `id` TEXT NOT NULL,
                        `sourceName` TEXT NOT NULL,
                        `mangaUrl` TEXT NOT NULL,
                        `mangaTitle` TEXT NOT NULL,
                        `thumbnailUrl` TEXT,
                        `chapterUrl` TEXT NOT NULL,
                        `chapterName` TEXT NOT NULL,
                        `lastPageIndex` INTEGER NOT NULL,
                        `totalPages` INTEGER NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_read_history_mangaUrl` ON `manga_read_history` (`mangaUrl`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_read_history_updatedAt` ON `manga_read_history` (`updatedAt`)")
            }
        }

        /**
         * 33 → 34: manga kitaplığı tablosu.
         *
         * Kullanıcı bir seriyi kitaplığa eklediğinde kaynak adı + URL + kategori tutulur.
         * Mevcut kurulumlarda tablo yoksa oluşturulur; veri kaybı yok.
         */
        private val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `manga_library` (
                        `id` TEXT NOT NULL,
                        `sourceName` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `thumbnailUrl` TEXT,
                        `author` TEXT,
                        `category` TEXT NOT NULL DEFAULT '',
                        `totalChapters` INTEGER NOT NULL DEFAULT 0,
                        `readChapters` INTEGER NOT NULL DEFAULT 0,
                        `lastChapterName` TEXT,
                        `lastChapterUrl` TEXT,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_manga_library_updatedAt` ON `manga_library` (`updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_manga_library_category` ON `manga_library` (`category`)")
            }
        }

        internal val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE manga_chapter_progress ADD COLUMN lastPageOffset REAL NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): KitsugiDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    KitsugiDatabase::class.java,
                    "Kitsugi_database"
                )
                    .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,  // ✨ Manga okuma ilerlemesi tablosu
                        MIGRATION_15_16,  // ✨ Manga source sağlık / istatistik
                        MIGRATION_16_17,  // ✨ Manga source hata tipi
                        MIGRATION_17_18,   // ✨ Manga eşleşme tablosu
                        MIGRATION_18_19,   // ✨ TMDB / Simkl alanları
                        MIGRATION_19_20,   // ✨ Altyazı alanları
                        MIGRATION_20_21,    // ✨ Arama geçmişi
                        MIGRATION_21_22,   // 🎬 İzleme geçmişi
                        MIGRATION_22_23,   // 📹 Video cache/çözümleme
                        MIGRATION_23_24,    // 📂 Üçlü Fallback / Explore Cache
                        MIGRATION_24_25,   // 📂 TVDB ID Caching
                        MIGRATION_25_26,
                        MIGRATION_26_27,
                        MIGRATION_27_28,
                        MIGRATION_28_29,
                        MIGRATION_29_30,  // 📅 Listeye eklenme zamanı (addedAt)
                        MIGRATION_30_31,  // 🎬 TMDB Watchlist & Rating
                        MIGRATION_29_31,
                        MIGRATION_31_32,  // 🎬 Otomatik liste takibi (izlendi bayrağı)
                        MIGRATION_32_33,  // 📖 Okunan bölüm geçmişi
                        MIGRATION_33_34,  // 📚 Manga kitaplığı
                        MIGRATION_34_35   // 📐 Webtoon görsel içi kesirli ilerleme (lastPageOffset)
                    )
                
                // T3-03: Güvenlik sertleştirmesi — Üretim sürümünde verilerin kazara sıfırlanmasını engeller.
                if (com.kitsugi.animelist.BuildConfig.DEBUG) {
                    builder.fallbackToDestructiveMigration(dropAllTables = true)
                }

                val instance = builder.build()

                INSTANCE = instance
                instance
            }
        }
    }
}