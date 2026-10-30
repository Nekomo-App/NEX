package net.nekomo.nex

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.nekomo.nex.data.NexRepository
import net.nekomo.nex.data.SecureStore
import net.nekomo.nex.data.SettingsStore
import net.nekomo.nex.data.db.NexDatabase
import net.nekomo.nex.log.NexLog
import net.nekomo.nex.net.NexHttp
import net.nekomo.nex.work.UpdateWorker

class NexApplication : Application(), Configuration.Provider {
    lateinit var repo: NexRepository
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build()

    override fun onCreate() {
        super.onCreate()
        NexLog.init(this)
        NexHttp.init(this, "NEX/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})")
        val settings = SettingsStore(this)
        repo = NexRepository(this, NexDatabase.build(this), settings, SecureStore(this))
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(UpdateWorker.CHANNEL, "Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Extension, repository and app updates found by NEX"
                }
            )
        }
        scope.launch {
            settings.flow.map { Triple(it.autoCheck, it.checkIntervalHours, it.wifiOnly) }.distinctUntilChanged().collect { (auto, hours, wifi) ->
                UpdateWorker.schedule(this@NexApplication, auto, hours, wifi)
            }
        }
        scope.launch {
            settings.flow.collect {
                NexLog.debug = it.debugLogging
                NexHttp.timeoutSeconds = it.timeoutSeconds.toLong()
            }
        }
        scope.launch { repo.scanDevice() }
    }
}
