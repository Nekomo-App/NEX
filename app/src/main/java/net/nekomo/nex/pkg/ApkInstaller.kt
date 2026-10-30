package net.nekomo.nex.pkg

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import net.nekomo.nex.log.NexLog
import net.nekomo.nex.util.Hashes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

sealed interface InstallOutcome {
    data object Success : InstallOutcome
    data class Failure(val message: String) : InstallOutcome
    data object Cancelled : InstallOutcome
    /** Android 8+ requires the user to allow NEX to install apps. */
    data class NeedsPermission(val settingsIntent: Intent) : InstallOutcome
}

data class ApkExpectation(
    val packageName: String,
    val versionCode: Long? = null,
    val sha256: String? = null,
    /** Required features, e.g. "tachiyomi.extension"; installing an APK without it is refused. */
    val requiredFeature: String? = null,
)

/**
 * Installs APKs through [PackageInstaller]. The system always shows its own confirmation dialog; NEX never installs silently.
 * Before committing, the downloaded archive is checked against what the repository advertised (package name, version,
 * optional SHA-256, required extension feature) and, for updates, against the signature of the installed package.
 */
object ApkInstaller {
    private const val TAG = "ApkInstaller"
    internal const val ACTION = "net.nekomo.nex.INSTALL_RESULT"
    internal const val EXTRA_SESSION = "nex_session"
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<InstallOutcome>>()

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        else Intent(Settings.ACTION_SECURITY_SETTINGS)

    /** Returns null when [file] matches [expect], otherwise a human readable reason. */
    fun verify(context: Context, file: File, expect: ApkExpectation): String? {
        if (!file.exists() || file.length() == 0L) return "Download is empty"
        expect.sha256?.let { want ->
            val got = Hashes.sha256(file)
            if (!got.equals(want, ignoreCase = true)) return "Checksum mismatch (expected ${want.take(12)}…, got ${got.take(12)}…)"
        }
        val archive = Packages.archiveInfo(context, file) ?: return "File is not a valid Android package"
        if (archive.packageName != expect.packageName) return "Package name is ${archive.packageName}, expected ${expect.packageName}"
        expect.versionCode?.let { vc ->
            val got = Packages.versionCode(archive)
            if (vc > 0 && got != vc) return "Version code is $got, repository advertised $vc"
        }
        expect.requiredFeature?.let { feat ->
            if (archive.reqFeatures.orEmpty().none { it.name == feat }) return "Package is not a valid extension (missing $feat)"
        }
        val installed = Packages.info(context, expect.packageName, Packages.signingFlag())
        if (installed != null) {
            val a = Packages.signatureSha256(installed)
            val b = Packages.signatureSha256(archive)
            if (a != null && b != null && a != b) return "Signature differs from the installed version. Uninstall it first if you trust this repository."
        }
        return null
    }

    suspend fun install(context: Context, file: File, expect: ApkExpectation): InstallOutcome {
        if (!canInstall(context)) return InstallOutcome.NeedsPermission(permissionIntent(context))
        withContext(Dispatchers.IO) { verify(context, file, expect) }?.let { return InstallOutcome.Failure(it) }
        val deferred = CompletableDeferred<InstallOutcome>()
        val sessionId = withContext(Dispatchers.IO) {
            val pi = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(expect.packageName)
                setSize(file.length())
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
            val id = pi.createSession(params)
            pending[id] = deferred
            try {
                pi.openSession(id).use { s ->
                    file.inputStream().use { input -> s.openWrite("base.apk", 0, file.length()).use { out -> input.copyTo(out); s.fsync(out) } }
                    val intent = Intent(context, InstallResultReceiver::class.java).setAction(ACTION).putExtra(EXTRA_SESSION, id)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    s.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
                }
            } catch (t: Throwable) {
                pending.remove(id)
                runCatching { pi.abandonSession(id) }
                NexLog.e(TAG, "Session $id failed", t)
                deferred.complete(InstallOutcome.Failure(t.message ?: "Could not start installation"))
            }
            id
        }
        return deferred.await().also {
            pending.remove(sessionId)
            NexLog.i(TAG, "Install ${expect.packageName}: $it")
        }
    }

    internal fun onResult(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_SESSION, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val d = pending[id]
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                if (confirm == null) d?.complete(InstallOutcome.Failure("System installer unavailable"))
                else runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { d?.complete(InstallOutcome.Failure("Could not open the system installer")) }
            }
            PackageInstaller.STATUS_SUCCESS -> d?.complete(InstallOutcome.Success)
            PackageInstaller.STATUS_FAILURE_ABORTED -> d?.complete(InstallOutcome.Cancelled)
            else -> d?.complete(InstallOutcome.Failure(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Installation failed ($status)"))
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ApkInstaller.ACTION) ApkInstaller.onResult(context, intent)
    }
}
