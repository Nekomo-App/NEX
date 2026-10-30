package net.nekomo.nex.pkg

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import net.nekomo.nex.util.Hashes
import java.io.File

object Packages {
    fun info(context: Context, pkg: String, flags: Int = 0): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else @Suppress("DEPRECATION") context.packageManager.getPackageInfo(pkg, flags)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun isInstalled(context: Context, pkg: String) = info(context, pkg) != null

    fun versionCode(pi: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()

    fun label(context: Context, pi: PackageInfo): String =
        pi.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: pi.packageName

    @SuppressLint("QueryPermissionsNeeded")
    fun allInstalled(context: Context, flags: Int): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= 33) context.packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        else @Suppress("DEPRECATION") context.packageManager.getInstalledPackages(flags)

    /** Installed packages that declare `<uses-feature android:name="[feature]">`, which is how Mihon/Aniyomi extensions identify themselves. */
    fun withFeature(context: Context, feature: String): List<PackageInfo> =
        allInstalled(context, PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA or signingFlag())
            .filter { pi -> pi.reqFeatures.orEmpty().any { it.name == feature } }

    @Suppress("DEPRECATION")
    fun signingFlag(): Int = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    fun signatureSha256(pi: PackageInfo?): String? {
        pi ?: return null
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val s = pi.signingInfo ?: return null
            if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
        } else pi.signatures
        return sigs?.firstOrNull()?.toByteArray()?.let { Hashes.sha256(it) }
    }

    fun installer(context: Context, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) context.packageManager.getInstallSourceInfo(pkg).installingPackageName
        else @Suppress("DEPRECATION") context.packageManager.getInstallerPackageName(pkg)
    } catch (e: Exception) {
        null
    }

    fun archiveInfo(context: Context, file: File): PackageInfo? {
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS or signingFlag()
        val pi = if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else @Suppress("DEPRECATION") context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        pi?.applicationInfo?.let { it.sourceDir = file.absolutePath; it.publicSourceDir = file.absolutePath }
        return pi
    }

    fun uninstallIntent(pkg: String): Intent =
        @Suppress("DEPRECATION")
        Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appDetailsIntent(pkg: String): Intent =
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
