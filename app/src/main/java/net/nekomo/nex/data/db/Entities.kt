package net.nekomo.nex.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

enum class RepoStatus { UNCHECKED, OK, WARNING, ERROR }

@Entity(tableName = "repositories", indices = [Index(value = ["appId", "url"], unique = true)])
data class RepositoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appId: String,
    val url: String,
    val format: String,
    val name: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val trusted: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastChecked: Long? = null,
    val status: RepoStatus = RepoStatus.UNCHECKED,
    val statusMessage: String? = null,
    val extensionCount: Int = 0,
    val latencyMs: Long? = null,
    val version: String? = null,
)

@Entity(tableName = "extensions", primaryKeys = ["repoId", "extId"], indices = [Index("appId"), Index("extId")])
data class ExtensionEntity(
    val repoId: Long,
    val appId: String,
    val extId: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val lang: String?,
    val nsfw: Boolean,
    val description: String?,
    val iconUrl: String?,
    val downloadUrl: String?,
    val kind: String,
    val sources: List<String>,
    val authors: List<String>,
    val contentTypes: List<String>,
    val status: Int?,
    val apiVersion: Int?,
    val fileSize: Long?,
    val sha256: String?,
    val homepage: String?,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val appId: String,
    val extId: String,
    val name: String,
    val action: String,
    val fromVersion: String?,
    val toVersion: String?,
    val ok: Boolean,
    val message: String?,
)

@Entity(tableName = "activity")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val kind: String,
    val title: String,
    val detail: String?,
    val ok: Boolean = true,
)

enum class ApiCategory(val label: String) { METADATA("Metadata API"), STREAMING("Streaming source"), CATALOG("Catalog source"), OTHER("Other") }

@Entity(tableName = "apis")
data class ApiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: ApiCategory = ApiCategory.METADATA,
    val appId: String? = null,
    val baseUrl: String,
    val backupUrl: String? = null,
    val testPath: String = "",
    val keyHeader: String? = null,
    val hasKey: Boolean = false,
    val enabled: Boolean = true,
    val lastStatus: String? = null,
    val lastOk: Boolean? = null,
    val lastLatencyMs: Long? = null,
    val lastChecked: Long? = null,
    val usedBackup: Boolean = false,
    val notes: String? = null,
    val origin: String? = null,
)

class Converters {
    private val list = ListSerializer(String.serializer())

    @TypeConverter fun fromList(v: List<String>): String = Json.encodeToString(list, v)
    @TypeConverter fun toList(v: String): List<String> = runCatching { Json.decodeFromString(list, v) }.getOrDefault(emptyList())
    @TypeConverter fun fromStatus(v: RepoStatus): String = v.name
    @TypeConverter fun toStatus(v: String): RepoStatus = runCatching { RepoStatus.valueOf(v) }.getOrDefault(RepoStatus.UNCHECKED)
    @TypeConverter fun fromCat(v: ApiCategory): String = v.name
    @TypeConverter fun toCat(v: String): ApiCategory = runCatching { ApiCategory.valueOf(v) }.getOrDefault(ApiCategory.OTHER)
}
