package net.nekomo.nex.integrations

import android.content.Context
import android.content.Intent
import net.nekomo.nex.data.AppId
import net.nekomo.nex.data.AppInstall
import net.nekomo.nex.data.Compatibility
import net.nekomo.nex.data.InstalledExtension
import net.nekomo.nex.data.RemoteExtension
import net.nekomo.nex.data.RepoFormat
import net.nekomo.nex.data.RepoIndex

/** What an integration can do. The UI only shows controls for capabilities that are actually supported. */
enum class Capability {
    /** NEX can install/update extensions itself (system installer, always with user confirmation). */
    INSTALL_EXTENSIONS,
    /** NEX can uninstall extensions (system uninstall dialog). */
    UNINSTALL_EXTENSIONS,
    /** NEX can see which extensions are installed on the device. */
    DETECT_INSTALLED_EXTENSIONS,
    /** The target app accepts a documented "add repository" intent. */
    ADD_REPO_INTENT,
    /** NEX can check for and install updates of the app itself (verified APK). */
    APP_UPDATES,
}

/** How a repository URL is passed to the target app. */
sealed interface RepoHandoff {
    data class OpenIntent(val intent: Intent, val description: String) : RepoHandoff
    data class CopyOrShare(val text: String, val instructions: String) : RepoHandoff
}

/**
 * App-specific integration boundary.
 *
 * Implementations must use the target app's documented/supported mechanisms.
 * Do not assume extension formats or installation flows are interchangeable.
 */
interface IntegrationAdapter {
    val appId: AppId
    val displayName: String get() = appId.displayName
    val description: String
    /** Package names, in preference order. */
    val packageNames: List<String>
    val repoFormats: List<RepoFormat>
    val capabilities: Set<Capability>
    val homepage: String
    /** Honest, user-facing limitations of this integration. */
    val limitations: List<String>
    /** Android `<uses-feature>` an extension APK must declare, when extensions are APKs. */
    val extensionFeature: String? get() = null

    fun detect(context: Context): List<AppInstall>

    fun isInstalled(context: Context): Boolean = detect(context).isNotEmpty()

    /** Normalises a pasted URL to the index URL this app expects. */
    fun normalizeRepoUrl(input: String): String = input.trim()

    /** Parses a repository index. May fetch further documents (CloudStream plugin lists, Nekomo child repos). */
    suspend fun parseIndex(url: String, body: ByteArray): RepoIndex

    fun installedExtensions(context: Context): List<InstalledExtension> = emptyList()

    fun compatibilityOf(ext: RemoteExtension): Pair<Compatibility, String?> = Compatibility.UNKNOWN to null

    fun repoHandoff(context: Context, url: String): RepoHandoff

    fun launchIntent(context: Context): Intent? =
        detect(context).firstOrNull()?.let { context.packageManager.getLaunchIntentForPackage(it.packageName) }

    fun has(c: Capability) = c in capabilities
}
