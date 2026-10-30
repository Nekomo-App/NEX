package net.nekomo.nex.integrations

import android.content.Context
import android.content.Intent
import android.net.Uri
import net.nekomo.nex.data.AppInstall
import net.nekomo.nex.data.Compatibility
import net.nekomo.nex.data.InstalledExtension
import net.nekomo.nex.data.PackageKind
import net.nekomo.nex.data.RemoteExtension
import net.nekomo.nex.data.RepoFormat
import net.nekomo.nex.data.RepoIndex
import net.nekomo.nex.pkg.Packages
import net.nekomo.nex.util.Versions

/**
 * Shared logic for Mihon and Aniyomi. Both load extensions from installed APKs that declare a `<uses-feature>`
 * and carry their lib version in `versionName` (e.g. `1.4.12` -> lib 1.4); both accept `<scheme>://add-repo?url=`.
 */
abstract class TachiyomiFamilyAdapter : IntegrationAdapter {
    abstract val format: RepoFormat
    abstract override val extensionFeature: String
    abstract val nsfwMeta: String
    abstract val addRepoScheme: String
    /** Supported lib version range (inclusive), from the upstream ExtensionLoader. */
    abstract val libMin: Double
    abstract val libMax: Double

    override val repoFormats get() = listOf(format)
    override val capabilities = setOf(
        Capability.INSTALL_EXTENSIONS, Capability.UNINSTALL_EXTENSIONS,
        Capability.DETECT_INSTALLED_EXTENSIONS, Capability.ADD_REPO_INTENT,
    )

    override fun detect(context: Context): List<AppInstall> = packageNames.mapNotNull { pkg ->
        Packages.info(context, pkg)?.let { AppInstall(appId, pkg, Packages.label(context, it), it.versionName ?: "?", Packages.versionCode(it)) }
    }

    override fun normalizeRepoUrl(input: String) = Formats.normalizeTachiyomiUrl(input)

    override suspend fun parseIndex(url: String, body: ByteArray): RepoIndex = Formats.parseTachiyomi(appId, format, url, body)

    private fun lib(versionName: String): Double? =
        Versions.libMajorMinor(versionName)?.let { (a, b) -> "$a.$b".toDoubleOrNull() }

    fun compatibility(versionName: String): Pair<Compatibility, String?> {
        val l = lib(versionName) ?: return Compatibility.UNKNOWN to "Lib version not found in $versionName"
        return when {
            l < libMin -> Compatibility.OUTDATED_LIB to "Lib $l is older than the minimum $libMin supported by ${appId.displayName}"
            l > libMax -> Compatibility.TOO_NEW to "Lib $l needs a newer ${appId.displayName} (supports up to $libMax)"
            else -> Compatibility.COMPATIBLE to "Lib $l"
        }
    }

    override fun compatibilityOf(ext: RemoteExtension) = compatibility(ext.versionName)

    override fun installedExtensions(context: Context): List<InstalledExtension> =
        Packages.withFeature(context, extensionFeature).map { pi ->
            val vn = pi.versionName ?: ""
            val (compat, note) = compatibility(vn)
            val meta = pi.applicationInfo?.metaData
            val label = Packages.label(context, pi).removePrefix("Tachiyomi: ").removePrefix("Aniyomi: ")
            InstalledExtension(
                appId = appId, id = pi.packageName, name = label, versionName = vn, versionCode = Packages.versionCode(pi),
                lang = pi.packageName.split('.').getOrNull(pi.packageName.split('.').size - 2),
                nsfw = (meta?.getInt(nsfwMeta, 0) ?: 0) == 1,
                kind = PackageKind.APK, compatibility = compat, compatibilityNote = note,
                enabled = pi.applicationInfo?.enabled != false,
                installerPackage = Packages.installer(context, pi.packageName),
                signatureSha256 = Packages.signatureSha256(pi),
            )
        }

    override fun repoHandoff(context: Context, url: String): RepoHandoff {
        val target = detect(context).firstOrNull()?.packageName
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$addRepoScheme://add-repo?url=" + Uri.encode(url)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (target != null) setPackage(target) }
        return if (target != null) RepoHandoff.OpenIntent(intent, "Opens ${appId.displayName}'s own \"Add repository\" confirmation.")
        else RepoHandoff.CopyOrShare(url, "${appId.displayName} is not installed. Install it, then add this URL in Browse → Extension repos.")
    }
}

class MihonAdapter : TachiyomiFamilyAdapter() {
    override val appId = net.nekomo.nex.data.AppId.MIHON
    override val description = "Manga extensions and repositories (Tachiyomi extension APKs)"
    override val packageNames = listOf("app.mihon", "app.mihon.debug", "app.mihon.dev", "eu.kanade.tachiyomi.sy", "eu.kanade.tachiyomi.j2k", "eu.kanade.tachiyomi")
    override val format = RepoFormat.TACHIYOMI_INDEX
    override val extensionFeature = "tachiyomi.extension"
    override val nsfwMeta = "tachiyomi.extension.nsfw"
    override val addRepoScheme = "tachiyomi"
    override val libMin = 1.4
    override val libMax = 1.5
    override val homepage = "https://mihon.app"
    override val limitations = listOf(
        "Extensions are separate APKs: NEX installs and updates them through Android's installer, and Mihon picks them up automatically.",
        "Enabling or disabling individual sources is done inside Mihon (Browse → Sources); Mihon exposes no API for it.",
        "Protobuf indexes (index.pb) are not read yet; use index.min.json.",
    )
}

class AniyomiAdapter : TachiyomiFamilyAdapter() {
    override val appId = net.nekomo.nex.data.AppId.ANIYOMI
    override val description = "Anime extensions and repositories (Aniyomi extension APKs)"
    override val packageNames = listOf("xyz.jmir.tachiyomi.mi", "xyz.jmir.tachiyomi.mi.debug", "xyz.jmir.tachiyomi.mi.preview")
    override val format = RepoFormat.ANIYOMI_INDEX
    override val extensionFeature = "tachiyomi.animeextension"
    override val nsfwMeta = "tachiyomi.animeextension.nsfw"
    override val addRepoScheme = "aniyomi"
    override val libMin = 12.0
    override val libMax = 16.0
    override val homepage = "https://aniyomi.org"
    override val limitations = listOf(
        "Anime extensions are separate APKs: NEX installs and updates them through Android's installer.",
        "Aniyomi manga extensions use Mihon's format; add those repositories under Mihon.",
        "Per-source settings stay inside Aniyomi.",
    )
}
