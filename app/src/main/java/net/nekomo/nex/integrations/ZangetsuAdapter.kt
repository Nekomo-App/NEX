package net.nekomo.nex.integrations

import android.content.Context
import net.nekomo.nex.data.AppId
import net.nekomo.nex.data.AppInstall
import net.nekomo.nex.data.RepoFormat
import net.nekomo.nex.data.RepoIndex
import net.nekomo.nex.pkg.Packages

class ZangetsuAdapter : IntegrationAdapter {
    override val appId = AppId.ZANGETSU
    override val description = "JavaScript sources (index.json) for Zangetsu"
    override val packageNames = listOf("com.spyou.watch_app")
    override val repoFormats = listOf(RepoFormat.ZANGETSU_INDEX)
    override val capabilities = emptySet<Capability>()
    override val homepage = "https://github.com/Spyou/Zangetsu"
    override val limitations = listOf(
        "Zangetsu stores its sources privately and has no public add-repository link or API (checked against its Android manifest).",
        "NEX validates and monitors Zangetsu index.json repositories, tracks source versions, and lets you copy or share a repository URL to paste into Zangetsu.",
    )

    override fun detect(context: Context) = packageNames.mapNotNull { pkg ->
        Packages.info(context, pkg)?.let { AppInstall(appId, pkg, Packages.label(context, it), it.versionName ?: "?", Packages.versionCode(it)) }
    }

    override suspend fun parseIndex(url: String, body: ByteArray): RepoIndex = Formats.parseZangetsu(appId, url, body)

    override fun repoHandoff(context: Context, url: String) =
        RepoHandoff.CopyOrShare(url, "Copy the URL, then add it in Zangetsu's source repository settings.")
}
