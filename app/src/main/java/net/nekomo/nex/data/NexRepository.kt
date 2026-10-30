package net.nekomo.nex.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import net.nekomo.nex.BuildConfig
import net.nekomo.nex.data.db.ActivityEntity
import net.nekomo.nex.data.db.ApiCategory
import net.nekomo.nex.data.db.ApiEntity
import net.nekomo.nex.data.db.ExtensionEntity
import net.nekomo.nex.data.db.HistoryEntity
import net.nekomo.nex.data.db.NexDatabase
import net.nekomo.nex.data.db.RepoStatus
import net.nekomo.nex.data.db.RepositoryEntity
import net.nekomo.nex.integrations.Capability
import net.nekomo.nex.integrations.Formats
import net.nekomo.nex.integrations.IntegrationAdapter
import net.nekomo.nex.integrations.IntegrationRegistry
import net.nekomo.nex.integrations.NekomoAdapter
import net.nekomo.nex.integrations.RepoFormatException
import net.nekomo.nex.log.NexLog
import net.nekomo.nex.net.FetchResult
import net.nekomo.nex.net.NexHttp
import net.nekomo.nex.net.describe
import net.nekomo.nex.pkg.ApkExpectation
import net.nekomo.nex.pkg.ApkInstaller
import net.nekomo.nex.pkg.InstallOutcome
import net.nekomo.nex.util.Urls
import java.io.File

/** An installed extension paired with what the enabled repositories offer for it. */
data class ExtensionRow(
    val appId: AppId,
    val id: String,
    val name: String,
    val installed: InstalledExtension?,
    val offers: List<ExtensionEntity>,
) {
    val latest: ExtensionEntity? get() = offers.maxByOrNull { it.versionCode }
    val hasUpdate: Boolean get() = installed != null && latest != null && latest!!.versionCode > installed.versionCode
    val duplicateRepos: Boolean get() = offers.map { it.repoId }.distinct().size > 1
}

data class AppUpdate(val app: AppInstall, val toVersion: String, val toCode: Long, val apkUrl: String, val sha256: String?, val size: Long?, val notes: String?)

@Serializable
data class BackupRepo(val appId: String, val url: String, val name: String, val enabled: Boolean, val trusted: Boolean)

@Serializable
data class BackupApi(
    val name: String, val category: String, val appId: String? = null, val baseUrl: String, val backupUrl: String? = null,
    val testPath: String = "", val keyHeader: String? = null, val enabled: Boolean = true, val notes: String? = null,
)

@Serializable
data class NexBackup(
    val nexBackup: Int = 1,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val createdAt: Long = System.currentTimeMillis(),
    val repositories: List<BackupRepo> = emptyList(),
    val apis: List<BackupApi> = emptyList(),
    val settings: Settings? = null,
)

sealed interface AddResult {
    data class Added(val repo: RepositoryEntity, val warnings: List<String>) : AddResult
    data class Failed(val message: String) : AddResult
}

class NexRepository(private val context: Context, private val db: NexDatabase, val settings: SettingsStore, val secure: SecureStore) {
    private val tag = "NexRepository"

    val repositories: Flow<List<RepositoryEntity>> = db.repositories().observe()
    val remoteExtensions: Flow<List<ExtensionEntity>> = db.extensions().observeEnabled()
    val history: Flow<List<HistoryEntity>> = db.history().observe()
    val activity: Flow<List<ActivityEntity>> = db.activity().observe()
    val apis: Flow<List<ApiEntity>> = db.apis().observe()
    fun extensionsOf(repoId: Long) = db.extensions().observeForRepo(repoId)

    private val _installed = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val installed: StateFlow<List<InstalledExtension>> = _installed.asStateFlow()
    private val _apps = MutableStateFlow<List<AppInstall>>(emptyList())
    val apps: StateFlow<List<AppInstall>> = _apps.asStateFlow()
    private val _appUpdates = MutableStateFlow<List<AppUpdate>>(emptyList())
    val appUpdates: StateFlow<List<AppUpdate>> = _appUpdates.asStateFlow()
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    /** Keys of operations in progress ("refresh:<id>", "install:<pkg>", "check"). */
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private suspend fun <T> busy(key: String, block: suspend () -> T): T {
        _busy.value = _busy.value + key
        try {
            return block()
        } finally {
            _busy.value = _busy.value - key
        }
    }

    fun adapter(id: AppId): IntegrationAdapter = IntegrationRegistry[id]
    fun adapter(id: String): IntegrationAdapter? = AppId.of(id)?.let { IntegrationRegistry[it] }

    suspend fun log(kind: String, title: String, detail: String? = null, ok: Boolean = true) {
        db.activity().insert(ActivityEntity(kind = kind, title = title, detail = detail, ok = ok))
        db.activity().trim()
        if (ok) NexLog.i(tag, "$kind: $title ${detail.orEmpty()}") else NexLog.w(tag, "$kind: $title ${detail.orEmpty()}")
    }

    // ------------------------------------------------------------------ device scan

    suspend fun scanDevice() = withContext(Dispatchers.IO) {
        _apps.value = IntegrationRegistry.adapters.flatMap { a -> runCatching { a.detect(context) }.getOrDefault(emptyList()) }
        _installed.value = IntegrationRegistry.adapters
            .filter { it.has(Capability.DETECT_INSTALLED_EXTENSIONS) }
            .flatMap { a -> runCatching { a.installedExtensions(context) }.onFailure { NexLog.e(tag, "scan ${a.appId}", it) }.getOrDefault(emptyList()) }
    }

    // ------------------------------------------------------------------ repositories

    data class Preview(val appId: AppId, val url: String, val format: RepoFormat, val index: RepoIndex, val latencyMs: Long)

    /** Fetches and validates a repository without saving it. [appId] null = auto-detect from content. */
    suspend fun preview(input: String, appId: AppId?): Result<Preview> = withContext(Dispatchers.IO) {
        val s = settings.current()
        val raw = when (val v = Urls.validate(appId?.let { adapter(it).normalizeRepoUrl(input) } ?: Formats.normalizeCloudStreamUrl(input), s.allowHttp)) {
            is Urls.Validation.Invalid -> return@withContext Result.failure(RepoFormatException(v.reason))
            is Urls.Validation.Valid -> v.url
        }
        suspend fun fetch(u: String) = NexHttp.get(u)
        var url = raw
        var res = fetch(url)
        // A bare Mihon/Aniyomi folder URL: try index.min.json when auto-detecting.
        if (res !is FetchResult.Ok && appId == null && !url.endsWith(".json")) {
            val alt = Formats.normalizeTachiyomiUrl(url)
            val r2 = fetch(alt)
            if (r2 is FetchResult.Ok) { url = alt; res = r2 }
        }
        if (res !is FetchResult.Ok) return@withContext Result.failure(RepoFormatException("Couldn't reach the repository: ${res.describe()}"))
        val format = Formats.sniff(res.body)
            ?: appId?.let { a -> adapter(a).repoFormats.firstOrNull { it == RepoFormat.TACHIYOMI_INDEX || it == RepoFormat.ANIYOMI_INDEX } }
                ?.takeIf { Formats.isEmptyArray(res.body) }
            ?: return@withContext Result.failure(RepoFormatException("Unrecognised repository format. Supported: ${RepoFormat.entries.joinToString { it.label }}"))
        val app = appId ?: Formats.defaultApp(format)
        val ad = adapter(app)
        if (format !in ad.repoFormats)
            return@withContext Result.failure(RepoFormatException("This is a ${format.label}, which ${app.displayName} doesn't use. ${Formats.defaultApp(format).displayName} does."))
        val index = try {
            ad.parseIndex(url, res.body)
        } catch (e: RepoFormatException) {
            return@withContext Result.failure(e)
        } catch (e: Exception) {
            NexLog.e(tag, "parse $url", e)
            return@withContext Result.failure(RepoFormatException("Invalid repository: ${e.message}"))
        }
        Result.success(Preview(app, url, format, index, res.latencyMs))
    }

    suspend fun add(p: Preview, trusted: Boolean): AddResult = withContext(Dispatchers.IO) {
        db.repositories().find(p.appId.name, p.url)?.let { return@withContext AddResult.Failed("This repository is already added for ${p.appId.displayName}") }
        val entity = RepositoryEntity(
            appId = p.appId.name, url = p.url, format = p.format.name,
            name = p.index.name ?: defaultName(p.url, p.format), description = p.index.description,
            trusted = trusted, lastChecked = System.currentTimeMillis(),
            status = if (p.index.warnings.isEmpty()) RepoStatus.OK else RepoStatus.WARNING,
            statusMessage = p.index.warnings.joinToString("\n").ifBlank { null },
            extensionCount = p.index.extensions.size, latencyMs = p.latencyMs, version = p.index.version,
        )
        val id = db.repositories().insert(entity)
        db.extensions().replaceForRepo(id, p.index.extensions.map { it.toEntity(id) })
        importManifestApis(p.index, id)
        log("repository", "Added ${entity.name}", "${p.appId.displayName} · ${p.index.extensions.size} entries")
        AddResult.Added(entity.copy(id = id), p.index.warnings)
    }

    private fun defaultName(url: String, format: RepoFormat): String {
        val u = Urls.host(url)
        val segs = url.removePrefix("https://").split('/')
        return if (u == "raw.githubusercontent.com" && segs.size > 2) "${segs[1]}/${segs[2]}" else "$u (${format.label.substringBefore(' ')})"
    }

    private suspend fun importManifestApis(index: RepoIndex, repoId: Long) {
        if (index.apis.isEmpty()) return
        val have = db.apis().all()
        val allowHttp = settings.current().allowHttp
        for (a in index.apis) {
            val url = a.url ?: continue
            if (Urls.validate(url, allowHttp) !is Urls.Validation.Valid) {
                log("api", "Skipped ${a.name} from manifest", "Endpoint is not an allowed https:// URL", ok = false)
                continue
            }
            if (have.any { it.baseUrl.trimEnd('/') == url.trimEnd('/') }) continue
            db.apis().insert(ApiEntity(name = a.name, category = ApiCategory.METADATA, appId = AppId.NEKOMO.name, baseUrl = url, notes = a.description, origin = "repo:$repoId"))
        }
    }

    suspend fun refresh(repo: RepositoryEntity): RepositoryEntity = busy("refresh:${repo.id}") {
        withContext(Dispatchers.IO) {
            val ad = adapter(repo.appId) ?: return@withContext repo
            val res = NexHttp.get(repo.url)
            val updated = when (res) {
                is FetchResult.Ok -> try {
                    val idx = ad.parseIndex(repo.url, res.body)
                    db.extensions().replaceForRepo(repo.id, idx.extensions.map { it.toEntity(repo.id) })
                    importManifestApis(idx, repo.id)
                    repo.copy(
                        name = repo.name, description = idx.description ?: repo.description,
                        status = if (idx.warnings.isEmpty()) RepoStatus.OK else RepoStatus.WARNING,
                        statusMessage = (idx.warnings + if (res.fromCache) listOf("Served from cache (offline?)") else emptyList()).joinToString("\n").ifBlank { null },
                        extensionCount = idx.extensions.size, version = idx.version,
                    )
                } catch (e: Exception) {
                    repo.copy(status = RepoStatus.ERROR, statusMessage = "Invalid index: ${e.message}")
                }
                else -> repo.copy(status = RepoStatus.ERROR, statusMessage = "Unavailable: ${res.describe()}")
            }.copy(lastChecked = System.currentTimeMillis(), latencyMs = res.latencyMs)
            db.repositories().update(updated)
            if (updated.status == RepoStatus.ERROR) log("repository", "${repo.name} failed to refresh", updated.statusMessage, ok = false)
            updated
        }
    }

    suspend fun refreshAll(): List<RepositoryEntity> = busy("refresh:all") {
        val sem = Semaphore(4)
        coroutineScope {
            db.repositories().all().filter { it.enabled }.map { r -> async { sem.withPermit { refresh(r) } } }.awaitAll()
        }.also { list ->
            val bad = list.count { it.status == RepoStatus.ERROR }
            log("refresh", "Refreshed ${list.size} repositories", if (bad > 0) "$bad unavailable" else null, ok = bad == 0)
        }
    }

    suspend fun setEnabled(repo: RepositoryEntity, enabled: Boolean) {
        val updated = repo.copy(enabled = enabled)
        db.repositories().update(updated)
        if (enabled && db.extensions().countForRepo(repo.id) == 0) refresh(updated)
    }
    suspend fun setTrusted(repo: RepositoryEntity, trusted: Boolean) {
        db.repositories().update(repo.copy(trusted = trusted))
        log("security", "${repo.name} marked ${if (trusted) "trusted" else "untrusted"}")
    }

    suspend fun rename(repo: RepositoryEntity, name: String) = db.repositories().update(repo.copy(name = name.trim().ifBlank { repo.name }))

    suspend fun remove(repo: RepositoryEntity) {
        db.repositories().delete(repo.id)
        db.extensions().deleteForRepo(repo.id)
        log("repository", "Removed ${repo.name}")
    }

    suspend fun repository(id: Long) = db.repositories().get(id)

    // ------------------------------------------------------------------ extensions & updates

    fun rows(installed: List<InstalledExtension>, remote: List<ExtensionEntity>, showNsfw: Boolean): List<ExtensionRow> {
        val byKey = remote.filter { showNsfw || !it.nsfw }.groupBy { it.appId to it.extId }
        val inst = installed.associateBy { it.appId.name to it.id }
        val keys = (byKey.keys + inst.keys).distinct()
        return keys.mapNotNull { k ->
            val app = AppId.of(k.first) ?: return@mapNotNull null
            val i = inst[k]
            val offers = byKey[k].orEmpty()
            ExtensionRow(app, k.second, i?.name ?: offers.firstOrNull()?.name ?: k.second, i, offers)
        }.sortedBy { it.name.lowercase() }
    }

    /** Remote entries whose package is installed but from an extension with a different name in another repo. */
    fun duplicateNames(rows: List<ExtensionRow>): Map<String, List<ExtensionRow>> =
        rows.groupBy { it.appId.name + ":" + it.name.lowercase() }.filterValues { list -> list.map { it.id }.distinct().size > 1 }

    suspend fun install(ext: ExtensionEntity, onPermission: (android.content.Intent) -> Unit = {}): InstallOutcome = busy("install:${ext.extId}") {
        val app = AppId.of(ext.appId) ?: return@busy InstallOutcome.Failure("Unknown app")
        val ad = adapter(app)
        if (!ad.has(Capability.INSTALL_EXTENSIONS)) return@busy InstallOutcome.Failure("${app.displayName} doesn't let other apps install its extensions")
        val repo = db.repositories().get(ext.repoId)
        if (settings.current().blockUntrusted && repo?.trusted != true)
            return@busy InstallOutcome.Failure("Installs from untrusted repositories are blocked in Settings → Security")
        val url = ext.downloadUrl ?: return@busy InstallOutcome.Failure("Repository gives no download URL")
        val before = installed.value.firstOrNull { it.appId == app && it.id == ext.extId }
        val dir = File(context.cacheDir, "apk").apply { mkdirs() }
        val file = File(dir, "${ext.extId}.apk")
        NexHttp.download(url, file)?.let { err ->
            record(ext, before, false, "Download failed: $err")
            return@busy InstallOutcome.Failure("Download failed: $err")
        }
        val outcome = ApkInstaller.install(context, file, ApkExpectation(ext.extId, ext.versionCode.takeIf { it > 0 }, ext.sha256, ad.extensionFeature))
        file.delete()
        when (outcome) {
            is InstallOutcome.NeedsPermission -> onPermission(outcome.settingsIntent)
            InstallOutcome.Cancelled -> record(ext, before, false, "Cancelled")
            is InstallOutcome.Failure -> record(ext, before, false, outcome.message)
            InstallOutcome.Success -> record(ext, before, true, null)
        }
        scanDevice()
        outcome
    }

    private suspend fun record(ext: ExtensionEntity, before: InstalledExtension?, ok: Boolean, message: String?) {
        val action = if (before == null) "install" else "update"
        db.history().insert(HistoryEntity(appId = ext.appId, extId = ext.extId, name = ext.name, action = action, fromVersion = before?.versionName, toVersion = ext.versionName, ok = ok, message = message))
        log(action, "${if (ok) "" else "Failed: "}${ext.name} ${ext.versionName}", message, ok)
    }

    suspend fun recordUninstall(row: ExtensionRow) {
        db.history().insert(HistoryEntity(appId = row.appId.name, extId = row.id, name = row.name, action = "uninstall", fromVersion = row.installed?.versionName, toVersion = null, ok = true, message = "Uninstall requested"))
        log("uninstall", "Uninstall requested: ${row.name}")
    }

    suspend fun clearHistory() = db.history().clear()
    suspend fun clearActivity() = db.activity().clear()

    // ------------------------------------------------------------------ Nekomo app updates

    @Serializable private data class LatestBuild(val file: String, val flavor: String, val channel: String, val size: Long? = null, val sha256: String? = null)
    @Serializable private data class Latest(val version: String, val versionCode: Long, val builds: List<LatestBuild> = emptyList())
    @Serializable private data class GhRelease(val tag_name: String? = null, val name: String? = null, val body: String? = null)

    suspend fun checkAppUpdates(): List<AppUpdate> = withContext(Dispatchers.IO) {
        val installs = apps.value.filter { adapter(it.appId).has(Capability.APP_UPDATES) }
        if (installs.isEmpty()) return@withContext emptyList<AppUpdate>().also { _appUpdates.value = it }
        val res = NexHttp.get(NekomoAdapter.LATEST_URL)
        if (res !is FetchResult.Ok) {
            log("update", "Couldn't check Nekomo updates", res.describe(), ok = false)
            return@withContext _appUpdates.value
        }
        val latest = runCatching { Formats.json.decodeFromString(Latest.serializer(), res.body.decodeToString()) }.getOrNull()
            ?: return@withContext emptyList()
        val notes = (NexHttp.get(NekomoAdapter.RELEASES_API, headers = mapOf("Accept" to "application/vnd.github+json")) as? FetchResult.Ok)
            ?.let { r -> runCatching { Formats.json.decodeFromString(ListSerializer(GhRelease.serializer()), r.body.decodeToString()) }.getOrNull() }
            ?.firstOrNull { it.tag_name?.removePrefix("v") == latest.version }?.body
        installs.mapNotNull { app ->
            val (flavor, channel) = NekomoAdapter.buildKey(app.packageName)
            val b = latest.builds.firstOrNull { it.flavor == flavor && it.channel == channel } ?: return@mapNotNull null
            if (latest.versionCode <= app.versionCode) return@mapNotNull null
            AppUpdate(app, latest.version, latest.versionCode, NekomoAdapter.APK_BASE + b.file, b.sha256, b.size, notes)
        }.also { _appUpdates.value = it }
    }

    suspend fun installAppUpdate(u: AppUpdate, onPermission: (android.content.Intent) -> Unit = {}): InstallOutcome = busy("install:${u.app.packageName}") {
        if (u.sha256 == null) return@busy InstallOutcome.Failure("No checksum published; refusing to install")
        val file = File(context.cacheDir, "apk/${u.app.packageName}.apk")
        NexHttp.download(u.apkUrl, file)?.let { return@busy InstallOutcome.Failure("Download failed: $it") }
        val r = ApkInstaller.install(context, file, ApkExpectation(u.app.packageName, null, u.sha256))
        file.delete()
        if (r is InstallOutcome.NeedsPermission) onPermission(r.settingsIntent)
        val ok = r == InstallOutcome.Success
        db.history().insert(HistoryEntity(appId = u.app.appId.name, extId = u.app.packageName, name = u.app.label, action = "update", fromVersion = u.app.versionName, toVersion = u.toVersion, ok = ok, message = (r as? InstallOutcome.Failure)?.message))
        log("update", "${if (ok) "Updated" else "Update failed:"} ${u.app.label} → ${u.toVersion}", (r as? InstallOutcome.Failure)?.message, ok)
        scanDevice()
        r
    }

    // ------------------------------------------------------------------ full check (worker + button)

    data class CheckSummary(val extensionUpdates: List<ExtensionRow>, val appUpdates: List<AppUpdate>, val brokenRepos: Int)

    suspend fun checkEverything(refreshRepos: Boolean = true): CheckSummary = busy("check") {
        scanDevice()
        val repos = if (refreshRepos) refreshAll() else db.repositories().all()
        val s = settings.current()
        val rows = rows(installed.value, db.extensions().enabled(), s.showNsfw)
        val apps = checkAppUpdates()
        settings.update { it.copy(lastCheck = System.currentTimeMillis()) }
        CheckSummary(rows.filter { it.hasUpdate }, apps, repos.count { it.enabled && it.status == RepoStatus.ERROR })
    }

    // ------------------------------------------------------------------ APIs & sources

    suspend fun saveApi(api: ApiEntity, key: String?): Long {
        val id = if (api.id == 0L) db.apis().insert(api) else api.id.also { db.apis().update(api) }
        when {
            key == null -> Unit
            key.isBlank() -> { secure.remove(SecureStore.apiKeyName(id)); db.apis().get(id)?.let { db.apis().update(it.copy(hasKey = false)) } }
            else -> { secure.put(SecureStore.apiKeyName(id), key.trim()); db.apis().get(id)?.let { db.apis().update(it.copy(hasKey = true)) } }
        }
        log("api", "Saved ${api.name}")
        return id
    }

    suspend fun deleteApi(api: ApiEntity) {
        db.apis().delete(api.id)
        secure.remove(SecureStore.apiKeyName(api.id))
        log("api", "Removed ${api.name}")
    }

    suspend fun setApiEnabled(api: ApiEntity, enabled: Boolean) = db.apis().update(api.copy(enabled = enabled))

    /** Tests the primary endpoint and falls back to the backup when it fails. */
    suspend fun testApi(api: ApiEntity): ApiEntity = busy("api:${api.id}") {
        val headers = buildMap {
            val key = if (api.hasKey) secure.get(SecureStore.apiKeyName(api.id)) else null
            if (key != null) put(api.keyHeader?.ifBlank { null } ?: "Authorization", if (api.keyHeader.isNullOrBlank()) "Bearer $key" else key)
        }
        suspend fun probe(base: String): FetchResult = NexHttp.get(Urls.resolve(base.trimEnd('/') + "/", api.testPath.trimStart('/')), maxBytes = 2L * 1024 * 1024, headers = headers)
        var res = probe(api.baseUrl)
        var backup = false
        if (res !is FetchResult.Ok && !api.backupUrl.isNullOrBlank()) {
            val r2 = probe(api.backupUrl)
            if (r2 is FetchResult.Ok) { res = r2; backup = true }
        }
        val ok = res is FetchResult.Ok
        val updated = api.copy(
            lastOk = ok, lastStatus = res.describe() + if (backup) " via backup" else "",
            lastLatencyMs = res.latencyMs, lastChecked = System.currentTimeMillis(), usedBackup = backup,
        )
        db.apis().update(updated)
        log("api", "${api.name}: ${if (ok) "reachable" else "unreachable"}", updated.lastStatus, ok)
        updated
    }

    suspend fun testAllApis() = db.apis().all().filter { it.enabled }.forEach { testApi(it) }

    // ------------------------------------------------------------------ backup / restore

    suspend fun exportBackup(includeSettings: Boolean = true): String {
        val b = NexBackup(
            repositories = db.repositories().all().map { BackupRepo(it.appId, it.url, it.name, it.enabled, it.trusted) },
            apis = db.apis().all().map { BackupApi(it.name, it.category.name, it.appId, it.baseUrl, it.backupUrl, it.testPath, it.keyHeader, it.enabled, it.notes) },
            settings = if (includeSettings) settings.current().copy(lastCheck = 0) else null,
        )
        return Formats.json.encodeToString(NexBackup.serializer(), b)
    }

    data class RestoreResult(val repos: Int, val apis: Int, val skipped: Int, val settings: Boolean)

    /** Restores a NEX backup. Accepts a plain list of URLs (one per line) too. Existing entries are kept. */
    suspend fun importBackup(text: String, restoreSettings: Boolean): RestoreResult = withContext(Dispatchers.IO) {
        val t = text.trim()
        if (!t.startsWith("{")) {
            var added = 0
            var skipped = 0
            t.lines().map { it.trim() }.filter { it.startsWith("http") }.forEach { line ->
                val p = preview(line, null).getOrNull()
                if (p != null && add(p, false) is AddResult.Added) added++ else skipped++
            }
            return@withContext RestoreResult(added, 0, skipped, false).also { log("backup", "Imported $added repositories from URL list", if (skipped > 0) "$skipped skipped" else null) }
        }
        val b = try {
            Formats.json.decodeFromString(NexBackup.serializer(), t)
        } catch (e: Exception) {
            throw RepoFormatException("Not a NEX backup: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        var repos = 0
        var skipped = 0
        for (r in b.repositories) {
            val app = AppId.of(r.appId)
            if (app == null || db.repositories().find(r.appId, r.url) != null) { skipped++; continue }
            val fmt = adapter(app).repoFormats.first()
            db.repositories().insert(RepositoryEntity(appId = r.appId, url = r.url, format = fmt.name, name = r.name, enabled = r.enabled, trusted = r.trusted))
            repos++
        }
        val existing = db.apis().all()
        var apis = 0
        for (a in b.apis) {
            if (existing.any { it.baseUrl == a.baseUrl && it.name == a.name }) { skipped++; continue }
            db.apis().insert(ApiEntity(name = a.name, category = runCatching { ApiCategory.valueOf(a.category) }.getOrDefault(ApiCategory.OTHER), appId = a.appId, baseUrl = a.baseUrl, backupUrl = a.backupUrl, testPath = a.testPath, keyHeader = a.keyHeader, enabled = a.enabled, notes = a.notes))
            apis++
        }
        val s = b.settings
        if (restoreSettings && s != null) settings.update { cur -> s.copy(legalAckVersion = cur.legalAckVersion, lastCheck = cur.lastCheck) }
        log("backup", "Restored backup", "$repos repositories, $apis APIs" + if (skipped > 0) ", $skipped already present" else "")
        if (repos > 0) refreshAll()
        RestoreResult(repos, apis, skipped, restoreSettings && s != null)
    }

    // ------------------------------------------------------------------ maintenance

    suspend fun clearMetadataCache() {
        NexHttp.clearCache()
        File(context.cacheDir, "apk").deleteRecursively()
        log("maintenance", "Cleared caches")
    }

    suspend fun resetDatabase() {
        db.extensions().deleteAll()
        db.repositories().deleteAll()
        db.apis().deleteAll()
        db.history().clear()
        db.activity().clear()
        secure.clear()
        log("maintenance", "Database reset")
    }

    fun databaseSizeBytes(): Long = context.getDatabasePath(NexDatabase.NAME).length()
}

fun RemoteExtension.toEntity(repoId: Long) = ExtensionEntity(
    repoId = repoId, appId = appId.name, extId = id, name = name, versionName = versionName, versionCode = versionCode,
    lang = lang, nsfw = nsfw, description = description, iconUrl = iconUrl, downloadUrl = downloadUrl, kind = kind.name,
    sources = sources, authors = authors, contentTypes = contentTypes, status = status, apiVersion = apiVersion,
    fileSize = fileSize, sha256 = sha256, homepage = homepage,
)

fun ExtensionEntity.toRemote() = RemoteExtension(
    AppId.of(appId) ?: AppId.NEKOMO, extId, name, versionName, versionCode, lang, nsfw, description, iconUrl, downloadUrl,
    runCatching { PackageKind.valueOf(kind) }.getOrDefault(PackageKind.APK), sources, authors, contentTypes, status, apiVersion, fileSize, sha256, homepage,
)
