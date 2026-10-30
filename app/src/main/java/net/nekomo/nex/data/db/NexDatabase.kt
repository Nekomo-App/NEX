package net.nekomo.nex.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RepositoryDao {
    @Query("SELECT * FROM repositories ORDER BY appId, name COLLATE NOCASE")
    fun observe(): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories")
    suspend fun all(): List<RepositoryEntity>

    @Query("SELECT * FROM repositories WHERE id = :id")
    suspend fun get(id: Long): RepositoryEntity?

    @Query("SELECT * FROM repositories WHERE appId = :appId AND url = :url LIMIT 1")
    suspend fun find(appId: String, url: String): RepositoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(r: RepositoryEntity): Long

    @Update
    suspend fun update(r: RepositoryEntity)

    @Query("DELETE FROM repositories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM repositories")
    suspend fun deleteAll()
}

@Dao
interface ExtensionDao {
    @Query("SELECT e.* FROM extensions e JOIN repositories r ON r.id = e.repoId WHERE r.enabled = 1 ORDER BY e.name COLLATE NOCASE")
    fun observeEnabled(): Flow<List<ExtensionEntity>>

    @Query("SELECT e.* FROM extensions e JOIN repositories r ON r.id = e.repoId WHERE r.enabled = 1")
    suspend fun enabled(): List<ExtensionEntity>

    @Query("SELECT * FROM extensions WHERE repoId = :repoId ORDER BY name COLLATE NOCASE")
    fun observeForRepo(repoId: Long): Flow<List<ExtensionEntity>>

    @Query("DELETE FROM extensions WHERE repoId = :repoId")
    suspend fun deleteForRepo(repoId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<ExtensionEntity>)

    @Transaction
    suspend fun replaceForRepo(repoId: Long, list: List<ExtensionEntity>) {
        deleteForRepo(repoId)
        insertAll(list)
    }

    @Query("DELETE FROM extensions")
    suspend fun deleteAll()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY time DESC LIMIT 500")
    fun observe(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(h: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity ORDER BY time DESC LIMIT :limit")
    fun observe(limit: Int = 200): Flow<List<ActivityEntity>>

    @Insert
    suspend fun insert(a: ActivityEntity)

    @Query("DELETE FROM activity WHERE id NOT IN (SELECT id FROM activity ORDER BY time DESC LIMIT 500)")
    suspend fun trim()

    @Query("DELETE FROM activity")
    suspend fun clear()
}

@Dao
interface ApiDao {
    @Query("SELECT * FROM apis ORDER BY category, name COLLATE NOCASE")
    fun observe(): Flow<List<ApiEntity>>

    @Query("SELECT * FROM apis")
    suspend fun all(): List<ApiEntity>

    @Query("SELECT * FROM apis WHERE id = :id")
    suspend fun get(id: Long): ApiEntity?

    @Insert
    suspend fun insert(a: ApiEntity): Long

    @Update
    suspend fun update(a: ApiEntity)

    @Query("DELETE FROM apis WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM apis")
    suspend fun deleteAll()
}

@Database(
    entities = [RepositoryEntity::class, ExtensionEntity::class, HistoryEntity::class, ActivityEntity::class, ApiEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NexDatabase : RoomDatabase() {
    abstract fun repositories(): RepositoryDao
    abstract fun extensions(): ExtensionDao
    abstract fun history(): HistoryDao
    abstract fun activity(): ActivityDao
    abstract fun apis(): ApiDao

    companion object {
        const val NAME = "nex.db"
        fun build(context: Context): NexDatabase =
            Room.databaseBuilder(context, NexDatabase::class.java, NAME).build()
    }
}
