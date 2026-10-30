package net.nekomo.nex.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.nekomo.nex.MainActivity
import net.nekomo.nex.NexApplication
import net.nekomo.nex.R
import net.nekomo.nex.log.NexLog
import java.util.concurrent.TimeUnit

/** Periodic update check. Runs only with network (optionally unmetered) and battery not low. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as NexApplication
        val s = app.repo.settings.current()
        return try {
            val sum = app.repo.checkEverything(refreshRepos = s.autoRefreshRepos)
            val n = sum.extensionUpdates.size + sum.appUpdates.size
            app.repo.log("check", "Background check", "$n updates, ${sum.brokenRepos} unavailable repositories")
            if (s.notifications && (n > 0 || sum.brokenRepos > 0)) notify(applicationContext, n, sum.brokenRepos, sum.extensionUpdates.take(5).map { it.name } + sum.appUpdates.map { it.app.label })
            Result.success()
        } catch (t: Throwable) {
            NexLog.e("UpdateWorker", "check failed", t)
            Result.retry()
        }
    }

    companion object {
        const val CHANNEL = "updates"
        private const val NAME = "nex_update_check"

        fun schedule(context: Context, enabled: Boolean, hours: Int, wifiOnly: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(hours.coerceAtLeast(1).toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<UpdateWorker>().build())
        }

        fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        fun notify(context: Context, updates: Int, broken: Int, names: List<String>) {
            if (!canNotify(context)) return
            val pi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "updates").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val title = when {
                updates > 0 -> "$updates update${if (updates == 1) "" else "s"} available"
                else -> "$broken repositor${if (broken == 1) "y is" else "ies are"} unavailable"
            }
            val text = if (names.isNotEmpty()) names.joinToString(", ") else "Open NEX to review"
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(1001, n)
            } catch (e: SecurityException) {
                NexLog.w("UpdateWorker", "notification permission missing")
            }
        }
    }
}
