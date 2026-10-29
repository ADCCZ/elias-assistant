package io.github.adccz.elias.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert
import io.github.adccz.elias.data.cache.CachedPayload
import io.github.adccz.elias.data.cache.SourceCache
import java.time.Instant
import javax.inject.Inject

/** One row per network source: its last raw answer that worked. */
@Entity(tableName = "source_cache")
class SourceCacheEntity(
    @PrimaryKey val key: String,
    val payload: ByteArray,
    @ColumnInfo(name = "loaded_at") val loadedAtMillis: Long,
)

@Dao
interface SourceCacheDao {
    /** Inserts the row, or replaces the one with the same key. */
    @Upsert
    suspend fun upsert(entity: SourceCacheEntity)

    @Query("SELECT * FROM source_cache WHERE `key` = :key")
    suspend fun find(key: String): SourceCacheEntity?
}

/** [SourceCache] in the Room database. Thin on purpose: the fallback logic lives in `loadWithCache`. */
class RoomSourceCache @Inject constructor(private val dao: SourceCacheDao) : SourceCache {
    override suspend fun save(key: String, payload: ByteArray, loadedAt: Instant) =
        dao.upsert(SourceCacheEntity(key, payload, loadedAt.toEpochMilli()))

    override suspend fun load(key: String): CachedPayload? =
        dao.find(key)?.let { CachedPayload(it.payload, Instant.ofEpochMilli(it.loadedAtMillis)) }
}
