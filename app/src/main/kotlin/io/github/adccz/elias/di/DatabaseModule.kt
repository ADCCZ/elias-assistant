package io.github.adccz.elias.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.adccz.elias.data.cache.SourceCache
import io.github.adccz.elias.data.db.EliasDatabase
import io.github.adccz.elias.data.db.RoomSourceCache
import io.github.adccz.elias.data.db.SourceCacheDao
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

/** The Room database: one instance for the whole app. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {
    @Binds
    abstract fun bindSourceCache(impl: RoomSourceCache): SourceCache

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): EliasDatabase =
            Room.databaseBuilder(context, EliasDatabase::class.java, EliasDatabase.FILE_NAME)
                // The platform's SQLite: no extra native library in the app.
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()

        @Provides
        fun provideSourceCacheDao(database: EliasDatabase): SourceCacheDao = database.sourceCacheDao()
    }
}
