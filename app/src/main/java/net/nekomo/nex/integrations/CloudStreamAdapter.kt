package net.nekomo.nex.integrations

import android.content.Context
import android.content.Intent
import android.net.Uri
import net.nekomo.nex.data.AppId
import net.nekomo.nex.data.AppInstall
import net.nekomo.nex.data.Compatibility
import net.nekomo.nex.data.RemoteExtension
import net.nekomo.nex.data.RepoFormat
import net.nekomo.nex.data.RepoIndex
import net.nekomo.nex.pkg.Packages

class CloudStreamAdapter : IntegrationAdapter {
    override val appId = AppId.CLOUDSTREAM
    override val description = "Streaming provider plugins (.cs3) and repositories"
    override val packageNames = listOf("com.lagradost.cloudstream3", "com.lagradost.cloudstream3.prerelease", "com.lagradost.cloudstream3.debug")
    override val repoFormats = listOf(RepoFormat.CLOUDSTREAM_REPO)
    override val capabilities = setOf(Capability.ADD_REPO_INTENT)
    override val homepage = "https://recloudstream.github.io"
    override val limitations = listOf(
        "CloudStream keeps .cs3 plugins in its private storage, so Android does not let other apps install, list or update them.",
        "NEX browses repositories, tracks plugin versions and status, and hands repositories to CloudStream with its cloudstreamrepo:// link. Install the plugins from CloudStream's own repository screen.",
    )

    override fun detect(context: Context) = packageNames.mapNotNull { pkg ->
        Packages.info(context, pkg)?.let { AppInstall(appId, pkg, Packages.label(context, it), it.versionName ?: "?", Packages.versionCode(it), if (pkg.endsWith("prerelease")) "pre-release" else null) }
    }

    override fun normalizeRepoUrl(input: String) = Formats.normalizeCloudStreamUrl(input)

    override suspend fun parseIndex(url: String, body: ByteArray): RepoIndex = Formats.loadCloudStream(appId, url, body)

    override fun compatibilityOf(ext: RemoteExtension): Pair<Compatibility, String?> = when (ext.status) {
        0 -> Compatibility.BROKEN to "Marked as down by the repository"
        2 -> Compatibility.COMPATIBLE to "Slow (per repository)"
        3 -> Compatibility.BROKEN to "Beta only (per repository)"
        else -> Compatibility.COMPATIBLE to null
    }

    override fun repoHandoff(context: Context, url: String): RepoHandoff {
        val target = detect(context).firstOrNull()?.packageName
            ?: return RepoHandoff.CopyOrShare(url, "CloudStream is not installed. Install it, then add this URL in Settings → Extensions → Add repository.")
        val link = "cloudstreamrepo://" + url.removePrefix("https://").removePrefix("http://")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return RepoHandoff.OpenIntent(intent, "Opens CloudStream's repository dialog with this URL.")
    }
}
