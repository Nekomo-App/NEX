package net.nekomo.nex.integrations

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.nekomo.nex.data.ApiInfo
import net.nekomo.nex.data.AppId
import net.nekomo.nex.data.PackageKind
import net.nekomo.nex.data.RemoteExtension
import net.nekomo.nex.data.RepoFormat
import net.nekomo.nex.data.RepoIndex
import net.nekomo.nex.net.FetchResult
import net.nekomo.nex.net.NexHttp
import net.nekomo.nex.net.describe
import net.nekomo.nex.util.Urls

class RepoFormatException(message: String) : Exception(message)

/**
 * Parsers for the repository formats used by the supported apps. Each format mirrors the upstream app's own reader:
 *  - Mihon / Aniyomi: `index.min.json`, a JSON array of `{name, pkg, apk, lang, code, version, nsfw, sources}`;
 *    APKs live in `../apk/<apk>` and icons in `../icon/<pkg>.png`.
 *  - CloudStream: `repo.json` `{name, description, manifestVersion, pluginLists: [url]}`, each list a JSON array of plugins.
 *  - Zangetsu: `index.json` `{name, description, sources: [{id, name, version, type, lang, file, logo, nsfw}]}`.
 *  - Nekomo: `{"nekomoRepository": 1, name, repositories: [{type: cloudstream|zangetsu, name, url, description}], apis: [...]}`.
 */
object Formats {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    // ---------------------------------------------------------------- sniffing

    fun isEmptyArray(body: ByteArray): Boolean =
        (runCatching { json.parseToJsonElement(body.decodeToString()) }.getOrNull() as? JsonArray)?.isEmpty() == true

    fun sniff(body: ByteArray): RepoFormat? {
        val el = runCatching { json.parseToJsonElement(body.decodeToString()) }.getOrNull() ?: return null
        return when (el) {
            is JsonObject -> when {
                "nekomoRepository" in el -> RepoFormat.NEKOMO_MANIFEST
                "pluginLists" in el -> RepoFormat.CLOUDSTREAM_REPO
                (el["sources"] as? JsonArray)?.firstOrNull()?.let { it is JsonObject && "file" in it } == true -> RepoFormat.ZANGETSU_INDEX
                "sources" in el -> RepoFormat.ZANGETSU_INDEX
                else -> null
            }
            is JsonArray -> {
                val first = el.firstOrNull() as? JsonObject
                when {
                    first == null -> null
                    "pkg" in first -> {
                        val pkg = (first["pkg"] as? JsonPrimitive)?.content.orEmpty()
                        if (".animeextension." in pkg) RepoFormat.ANIYOMI_INDEX else RepoFormat.TACHIYOMI_INDEX
                    }
                    "internalName" in first -> RepoFormat.CLOUDSTREAM_REPO
                    else -> null
                }
            }
            else -> null
        }
    }

    fun defaultApp(format: RepoFormat): AppId = when (format) {
        RepoFormat.NEKOMO_MANIFEST -> AppId.NEKOMO
        RepoFormat.TACHIYOMI_INDEX -> AppId.MIHON
        RepoFormat.ANIYOMI_INDEX -> AppId.ANIYOMI
        RepoFormat.CLOUDSTREAM_REPO -> AppId.CLOUDSTREAM
        RepoFormat.ZANGETSU_INDEX -> AppId.ZANGETSU
    }

    // ---------------------------------------------------------------- Mihon / Aniyomi

    @Serializable
    data class TachiSource(val name: String? = null, val lang: String? = null, val id: String? = null, val baseUrl: String? = null)

    @Serializable
    data class TachiEntry(
        val name: String,
        val pkg: String,
        val apk: String,
        val lang: String = "all",
        val code: Long = 0,
        val version: String = "",
        val nsfw: Int = 0,
        val sources: List<TachiSource> = emptyList(),
    )

    /** Mihon's normalisation: a repo folder or `index.json` becomes `.../index.min.json`. */
    fun normalizeTachiyomiUrl(input: String): String {
        val u = input.trim()
        return when {
            u.endsWith("index.min.json") || u.endsWith(".pb") -> u
            u.endsWith("/index.json") -> u.removeSuffix("index.json") + "index.min.json"
            u.endsWith(".json") -> u
            else -> u.trimEnd('/') + "/index.min.json"
        }
    }

    fun parseTachiyomi(appId: AppId, format: RepoFormat, url: String, body: ByteArray): RepoIndex {
        if (url.substringBefore('?').endsWith(".pb"))
            throw RepoFormatException("Protobuf indexes (index.pb) aren't supported yet. Use the repository's index.min.json.")
        val entries = try {
            json.decodeFromString(ListSerializer(TachiEntry.serializer()), body.decodeToString())
        } catch (e: Exception) {
            throw RepoFormatException("Not a valid ${format.label}: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        val base = url.substringBefore('?').substringBeforeLast('/')
        val prefix = if (appId == AppId.ANIYOMI) "Aniyomi: " else "Tachiyomi: "
        val warnings = mutableListOf<String>()
        val wrongKind = entries.count { (".animeextension." in it.pkg) != (format == RepoFormat.ANIYOMI_INDEX) }
        if (wrongKind > 0) warnings += "$wrongKind entries look like they belong to a different app"
        return RepoIndex(
            format = format,
            name = null,
            description = null,
            extensions = entries.map { e ->
                RemoteExtension(
                    appId = appId,
                    id = e.pkg,
                    name = e.name.removePrefix(prefix).removePrefix("Tachiyomi: ").removePrefix("Aniyomi: "),
                    versionName = e.version,
                    versionCode = e.code,
                    lang = e.lang,
                    nsfw = e.nsfw == 1,
                    iconUrl = "$base/icon/${e.pkg}.png",
                    downloadUrl = "$base/apk/${e.apk}",
                    kind = PackageKind.APK,
                    sources = e.sources.mapNotNull { s -> s.name?.let { n -> if (s.lang != null && s.lang != e.lang) "$n (${s.lang})" else n } },
                    homepage = e.sources.firstOrNull()?.baseUrl,
                )
            },
            warnings = warnings,
        )
    }

    // ---------------------------------------------------------------- CloudStream

    @Serializable
    data class CsRepository(
        val name: String? = null,
        val description: String? = null,
        val manifestVersion: Int? = null,
        val pluginLists: List<String> = emptyList(),
        val iconUrl: String? = null,
    )

    @Serializable
    data class CsPlugin(
        val url: String,
        val status: Int = 1,
        val version: Int = 0,
        val apiVersion: Int? = null,
        val name: String? = null,
        val internalName: String,
        val authors: List<String> = emptyList(),
        val description: String? = null,
        val repositoryUrl: String? = null,
        val language: String? = null,
        val iconUrl: String? = null,
        val tvTypes: List<String>? = null,
        val fileSize: Long? = null,
    )

    /** Accepts `cloudstreamrepo://host/path` and `https://cs.repo/?<url>` wrappers like CloudStream does. */
    fun normalizeCloudStreamUrl(input: String): String {
        val t = input.trim()
        return when {
            t.startsWith("cloudstreamrepo://") -> "https://" + t.removePrefix("cloudstreamrepo://")
            t.startsWith("https://cs.repo/") -> t.removePrefix("https://cs.repo/").removePrefix("?").let { if ("://" in it) it else "https://$it" }
            else -> t
        }
    }

    fun parseCloudStreamRepo(body: ByteArray): CsRepository = try {
        json.decodeFromString(CsRepository.serializer(), body.decodeToString())
    } catch (e: Exception) {
        throw RepoFormatException("Not a valid CloudStream repo.json: ${e.message?.lineSequence()?.firstOrNull()}")
    }

    fun parseCloudStreamPlugins(appId: AppId, body: ByteArray): List<RemoteExtension> =
        json.decodeFromString(ListSerializer(CsPlugin.serializer()), body.decodeToString()).map { p ->
            RemoteExtension(
                appId = appId,
                id = p.internalName,
                name = p.name ?: p.internalName,
                versionName = p.version.toString(),
                versionCode = p.version.toLong(),
                lang = p.language,
                nsfw = p.tvTypes.orEmpty().any { it.equals("NSFW", ignoreCase = true) },
                description = p.description,
                iconUrl = p.iconUrl?.replace("%size%", "128"),
                downloadUrl = p.url,
                kind = PackageKind.CS3,
                authors = p.authors,
                contentTypes = p.tvTypes.orEmpty(),
                status = p.status,
                apiVersion = p.apiVersion,
                fileSize = p.fileSize,
                homepage = p.repositoryUrl,
            )
        }

    suspend fun loadCloudStream(appId: AppId, url: String, body: ByteArray): RepoIndex {
        val repo = parseCloudStreamRepo(body)
        if (repo.pluginLists.isEmpty()) throw RepoFormatException("repo.json lists no plugin lists")
        val warnings = mutableListOf<String>()
        val all = mutableListOf<RemoteExtension>()
        for (list in repo.pluginLists) {
            val listUrl = Urls.resolve(url, list)
            when (val r = NexHttp.get(listUrl)) {
                is FetchResult.Ok -> runCatching { all += parseCloudStreamPlugins(appId, r.body) }
                    .onFailure { warnings += "Plugin list ${Urls.host(listUrl)} is invalid: ${it.message?.lineSequence()?.firstOrNull()}" }
                else -> warnings += "Plugin list $listUrl unavailable: ${r.describe()}"
            }
        }
        if (all.isEmpty() && warnings.isNotEmpty()) throw RepoFormatException(warnings.joinToString("\n"))
        return RepoIndex(RepoFormat.CLOUDSTREAM_REPO, repo.name, repo.description, repo.manifestVersion?.toString(), all.distinctBy { it.id }, warnings = warnings)
    }

    // ---------------------------------------------------------------- Zangetsu

    @Serializable
    data class ZgSource(
        val id: String,
        val name: String,
        val version: String = "1",
        val type: String = "anime",
        val lang: String = "en",
        val file: String,
        val logo: String? = null,
        val nsfw: Boolean = false,
    )

    @Serializable
    data class ZgManifest(val name: String? = null, val description: String? = null, val sources: List<ZgSource> = emptyList())

    fun parseZangetsu(appId: AppId, url: String, body: ByteArray): RepoIndex {
        val m = try {
            json.decodeFromString(ZgManifest.serializer(), body.decodeToString())
        } catch (e: Exception) {
            throw RepoFormatException("Not a valid Zangetsu index.json: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        return RepoIndex(
            RepoFormat.ZANGETSU_INDEX, m.name, m.description,
            extensions = m.sources.map { s ->
                RemoteExtension(
                    appId = appId, id = s.id, name = s.name, versionName = s.version,
                    lang = s.lang, nsfw = s.nsfw, iconUrl = s.logo?.let { Urls.resolve(url, it) },
                    downloadUrl = Urls.resolve(url, s.file), kind = PackageKind.JS, contentTypes = listOf(s.type),
                )
            },
        )
    }

    // ---------------------------------------------------------------- Nekomo

    @Serializable
    data class NekomoEntry(val type: String, val name: String, val url: String, val description: String? = null)

    @Serializable
    data class NekomoManifest(
        val nekomoRepository: Int? = null,
        val name: String? = null,
        val description: String? = null,
        val version: Int = 1,
        val updated: String? = null,
        val repositories: List<NekomoEntry> = emptyList(),
        val apis: List<ApiInfo> = emptyList(),
    )

    fun parseNekomoManifest(body: ByteArray): NekomoManifest {
        val m = try {
            json.decodeFromString(NekomoManifest.serializer(), body.decodeToString())
        } catch (e: Exception) {
            throw RepoFormatException("Not a valid Nekomo repository: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        if (m.nekomoRepository == null) throw RepoFormatException("Missing \"nekomoRepository\" marker, so this is not a Nekomo repository manifest")
        return m
    }

    fun childFormat(type: String): RepoFormat? = when (type.lowercase()) {
        "cloudstream" -> RepoFormat.CLOUDSTREAM_REPO
        "zangetsu" -> RepoFormat.ZANGETSU_INDEX
        else -> null
    }
}
