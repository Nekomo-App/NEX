package net.nekomo.nex.integrations

import android.content.Context
import net.nekomo.nex.data.AppId
import net.nekomo.nex.data.AppInstall
import net.nekomo.nex.data.ChildRepo
import net.nekomo.nex.data.RemoteExtension
import net.nekomo.nex.data.RepoFormat
import net.nekomo.nex.data.RepoIndex
import net.nekomo.nex.net.FetchResult
import net.nekomo.nex.net.NexHttp
import net.nekomo.nex.net.describe
import net.nekomo.nex.pkg.Packages

/**
 * Nekomo (first-class). Formats and package names come from Nekomo-App/neko-source:
 *  - `repo/index.json`: the `nekomoRepository` manifest (`utils/repo/NekomoRepo.kt`), pointing at CloudStream/Zangetsu repos.
 *  - Nekomo also accepts CloudStream repo.json, Zangetsu index.json and Mihon/Aniyomi index.min.json directly.
 *  - `apks/latest.json`: version, size and SHA-256 of every Nekomo build, used for verified app updates.
 */
class NekomoAdapter : IntegrationAdapter {
    override val appId = AppId.NEKOMO
    override val description = "Nekomo repositories, sources, APIs and app updates"
    override val packageNames = PACKAGES.keys.toList()
    override val repoFormats = listOf(RepoFormat.NEKOMO_MANIFEST, RepoFormat.CLOUDSTREAM_REPO, RepoFormat.ZANGETSU_INDEX, RepoFormat.TACHIYOMI_INDEX, RepoFormat.ANIYOMI_INDEX)
    override val capabilities = setOf(Capability.APP_UPDATES)
    override val homepage = "https://nekomoapp.netlify.app"
    override val limitations = listOf(
        "Nekomo installs sources into its own private storage, so NEX can't list or install them in Nekomo directly.",
        "Repositories are handed to Nekomo with a nekomo-repo://add link; Nekomo asks you to confirm. Older Nekomo versions get the URL copied instead.",
        "NEX checks Nekomo app updates against neko-source apks/latest.json and installs them only after verifying the SHA-256.",
    )

    override fun detect(context: Context) = PACKAGES.mapNotNull { (pkg, channel) ->
        Packages.info(context, pkg)?.let { AppInstall(appId, pkg, Packages.label(context, it), it.versionName ?: "?", Packages.versionCode(it), channel) }
    }

    override suspend fun parseIndex(url: String, body: ByteArray): RepoIndex {
        return when (Formats.sniff(body)) {
            RepoFormat.CLOUDSTREAM_REPO -> Formats.loadCloudStream(appId, url, body)
            RepoFormat.ZANGETSU_INDEX -> Formats.parseZangetsu(appId, url, body)
            RepoFormat.TACHIYOMI_INDEX -> Formats.parseTachiyomi(appId, RepoFormat.TACHIYOMI_INDEX, url, body)
            RepoFormat.ANIYOMI_INDEX -> Formats.parseTachiyomi(appId, RepoFormat.ANIYOMI_INDEX, url, body)
            else -> parseManifest(body)
        }
    }

    private suspend fun parseManifest(body: ByteArray): RepoIndex {
        val m = Formats.parseNekomoManifest(body)
        val warnings = mutableListOf<String>()
        val children = mutableListOf<ChildRepo>()
        val extensions = mutableListOf<RemoteExtension>()
        for (e in m.repositories) {
            val f = Formats.childFormat(e.type)
            if (f == null) {
                warnings += "${e.name}: unsupported repository type \"${e.type}\" (Nekomo manifests only list cloudstream or zangetsu)"
                continue
            }
            children += ChildRepo(appId, f, e.name, e.url, e.description)
            when (val r = NexHttp.get(e.url)) {
                is FetchResult.Ok -> runCatching {
                    val child = if (f == RepoFormat.CLOUDSTREAM_REPO) Formats.loadCloudStream(appId, e.url, r.body)
                    else Formats.parseZangetsu(appId, e.url, r.body)
                    extensions += child.extensions
                    warnings += child.warnings.map { w -> "${e.name}: $w" }
                }.onFailure { warnings += "${e.name}: ${it.message}" }
                else -> warnings += "${e.name}: ${r.describe()}"
            }
        }
        return RepoIndex(RepoFormat.NEKOMO_MANIFEST, m.name, m.description, m.version.toString(), extensions, children, m.apis, warnings)
    }

    /** Nekomo 2.8.1+ handles `nekomo-repo://add?url=`: it opens its confirmation dialog, nothing is added without approval. */
    override fun repoHandoff(context: Context, url: String): RepoHandoff {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("nekomo-repo://add?url=" + android.net.Uri.encode(url)))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent.resolveActivity(context.packageManager) != null)
            RepoHandoff.OpenIntent(intent, "Opens Nekomo's Extensions manager, which asks you to confirm before anything is added.")
        else RepoHandoff.CopyOrShare(url, "Update Nekomo to a version with repository links, or open Settings → Extensions → Extensions manager and paste this URL.")
    }

    companion object {
        const val LATEST_URL = "https://raw.githubusercontent.com/Nekomo-App/neko-source/main/apks/latest.json"
        const val APK_BASE = "https://raw.githubusercontent.com/Nekomo-App/neko-source/main/apks/"
        const val RELEASES_API = "https://api.github.com/repos/Nekomo-App/Nekomo/releases?per_page=5"
        const val OFFICIAL_REPO = "https://raw.githubusercontent.com/Nekomo-App/neko-source/main/repo/index.json"

        /** Package -> channel label, from neko-source apks/README.md. */
        val PACKAGES = linkedMapOf(
            "app.nekomo" to "stable", "app.nekomo.tv" to "stable · TV",
            "app.nekomo.beta" to "beta", "app.nekomo.tv.beta" to "beta · TV",
            "app.nekomo.debug" to "alpha", "app.nekomo.tv.debug" to "alpha · TV",
        )

        /** The latest.json build entry (flavor, channel) matching an installed package. */
        fun buildKey(pkg: String): Pair<String, String> {
            val flavor = if (pkg.startsWith("app.nekomo.tv")) "tv" else "phone"
            val channel = when {
                pkg.endsWith(".beta") -> "beta"
                pkg.endsWith(".debug") -> "alpha"
                else -> "stable"
            }
            return flavor to channel
        }
    }
}
