package io.github.adccz.elias.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * The app's database, in its private storage (never backed up, see D9).
 * The schema of each version is exported to `app/schemas/` and kept in git.
 */
@Database(entities = [SourceCacheEntity::class], version = 1)
abstract class EliasDatabase : RoomDatabase() {
    abstract fun sourceCacheDao(): SourceCacheDao

    companion object {
        const val FILE_NAME = "elias.db"
    }
}
