package net.nekomo.nex.data

import kotlinx.serialization.Serializable

/** Applications NEX knows how to manage. New integrations add an entry here and an [net.nekomo.nex.integrations.Integration]. */
@Serializable
enum class AppId(val displayName: String) {
    NEKOMO("Nekomo"),
    MIHON("Mihon"),
    ANIYOMI("Aniyomi"),
    CLOUDSTREAM("CloudStream"),
    ZANGETSU("Zangetsu");

    companion object {
        fun of(name: String?): AppId? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/** On-the-wire repository formats. A repository always has exactly one format. */
@Serializable
enum class RepoFormat(val label: String) {
    NEKOMO_MANIFEST("Nekomo repository manifest"),
    TACHIYOMI_INDEX("Mihon/Tachiyomi index.min.json"),
    ANIYOMI_INDEX("Aniyomi index.min.json"),
    CLOUDSTREAM_REPO("CloudStream repo.json"),
    ZANGETSU_INDEX("Zangetsu index.json"),
}

/** What kind of package an extension is. Decides how (and whether) NEX can install it. */
@Serializable
enum class PackageKind(val label: String) {
    APK("Android APK"),
    CS3("CloudStream plugin (.cs3)"),
    JS("JavaScript source (.js)"),
    REPOSITORY("Repository reference"),
}

enum class Compatibility { COMPATIBLE, OUTDATED_LIB, TOO_NEW, UNKNOWN, BROKEN }

/** A single extension, plugin or source as advertised by a repository. */
@Serializable
data class RemoteExtension(
    val appId: AppId,
    val id: String,
    val name: String,
    val versionName: String,
    val versionCode: Long = 0,
    val lang: String? = null,
    val nsfw: Boolean = false,
    val description: String? = null,
    val iconUrl: String? = null,
    val downloadUrl: String? = null,
    val kind: PackageKind,
    val sources: List<String> = emptyList(),
    val authors: List<String> = emptyList(),
    val contentTypes: List<String> = emptyList(),
    val status: Int? = null,
    val apiVersion: Int? = null,
    val fileSize: Long? = null,
    val sha256: String? = null,
    val homepage: String? = null,
)

/** Parsed repository index. */
data class RepoIndex(
    val format: RepoFormat,
    val name: String?,
    val description: String?,
    val version: String? = null,
    val extensions: List<RemoteExtension>,
    /** Repositories referenced by a manifest (Nekomo manifests point at CloudStream / Zangetsu repos). */
    val children: List<ChildRepo> = emptyList(),
    val apis: List<ApiInfo> = emptyList(),
    val warnings: List<String> = emptyList(),
)

data class ChildRepo(val appId: AppId, val format: RepoFormat, val name: String, val url: String, val description: String?)

@Serializable
data class ApiInfo(val id: String, val name: String, val description: String? = null, val url: String? = null)

/** An extension that is installed on the device, as seen by NEX. */
data class InstalledExtension(
    val appId: AppId,
    val id: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val lang: String? = null,
    val nsfw: Boolean = false,
    val kind: PackageKind = PackageKind.APK,
    val compatibility: Compatibility = Compatibility.UNKNOWN,
    val compatibilityNote: String? = null,
    val enabled: Boolean = true,
    val installerPackage: String? = null,
    val signatureSha256: String? = null,
)

/** A supported application detected on the device. */
data class AppInstall(
    val appId: AppId,
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val channel: String? = null,
)
