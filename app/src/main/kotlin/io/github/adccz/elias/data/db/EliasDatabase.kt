package io.github.adccz.elias.data.db

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * The app's database, in its private storage (never backed up, see D9).
 * The schema of each version is exported to `app/schemas/` and kept in git.
 *
 * Version 2 (M4.5) adds the journal. Adding a table is a change Room can migrate on its own.
 */
@Database(
    entities = [SourceCacheEntity::class, RunEntity::class, JournalEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class EliasDatabase : RoomDatabase() {
    abstract fun sourceCacheDao(): SourceCacheDao

    abstract fun runDao(): RunDao

    abstract fun journalDao(): JournalDao

    companion object {
        const val FILE_NAME = "elias.db"
    }
}
