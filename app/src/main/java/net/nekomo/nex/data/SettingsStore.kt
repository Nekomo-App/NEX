package net.nekomo.nex.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

enum class ThemeMode(val label: String) { DARK("Dark"), AMOLED("Pure black"), LIGHT("Light"), SYSTEM("Follow system") }

@Serializable
data class Settings(
    val theme: ThemeMode = ThemeMode.DARK,
    val startTab: String = "home",
    val notifications: Boolean = true,
    val autoCheck: Boolean = true,
    val checkIntervalHours: Int = 12,
    val wifiOnly: Boolean = false,
    val autoRefreshRepos: Boolean = true,
    val compatWarnings: Boolean = true,
    val showNsfw: Boolean = false,
    val allowHttp: Boolean = false,
    val blockUntrusted: Boolean = false,
    val confirmInstalls: Boolean = true,
    val debugLogging: Boolean = false,
    val timeoutSeconds: Int = 30,
    val legalAckVersion: Int = 0,
    val lastCheck: Long = 0,
)

private val Context.dataStore by preferencesDataStore("nex_settings")

class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val startTab = stringPreferencesKey("start_tab")
        val notifications = booleanPreferencesKey("notifications")
        val autoCheck = booleanPreferencesKey("auto_check")
        val interval = intPreferencesKey("check_interval_hours")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val autoRefresh = booleanPreferencesKey("auto_refresh_repos")
        val compat = booleanPreferencesKey("compat_warnings")
        val nsfw = booleanPreferencesKey("show_nsfw")
        val http = booleanPreferencesKey("allow_http")
        val blockUntrusted = booleanPreferencesKey("block_untrusted")
        val confirm = booleanPreferencesKey("confirm_installs")
        val debug = booleanPreferencesKey("debug_logging")
        val timeout = intPreferencesKey("timeout_seconds")
        val legal = intPreferencesKey("legal_ack_version")
        val lastCheck = longPreferencesKey("last_check")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = flow.first()

    private fun Preferences.toSettings() = Settings(
        theme = runCatching { ThemeMode.valueOf(this[K.theme] ?: "DARK") }.getOrDefault(ThemeMode.DARK),
        startTab = this[K.startTab] ?: "home",
        notifications = this[K.notifications] ?: true,
        autoCheck = this[K.autoCheck] ?: true,
        checkIntervalHours = this[K.interval] ?: 12,
        wifiOnly = this[K.wifiOnly] ?: false,
        autoRefreshRepos = this[K.autoRefresh] ?: true,
        compatWarnings = this[K.compat] ?: true,
        showNsfw = this[K.nsfw] ?: false,
        allowHttp = this[K.http] ?: false,
        blockUntrusted = this[K.blockUntrusted] ?: false,
        confirmInstalls = this[K.confirm] ?: true,
        debugLogging = this[K.debug] ?: false,
        timeoutSeconds = this[K.timeout] ?: 30,
        legalAckVersion = this[K.legal] ?: 0,
        lastCheck = this[K.lastCheck] ?: 0,
    )

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.theme] = s.theme.name
            p[K.startTab] = s.startTab
            p[K.notifications] = s.notifications
            p[K.autoCheck] = s.autoCheck
            p[K.interval] = s.checkIntervalHours
            p[K.wifiOnly] = s.wifiOnly
            p[K.autoRefresh] = s.autoRefreshRepos
            p[K.compat] = s.compatWarnings
            p[K.nsfw] = s.showNsfw
            p[K.http] = s.allowHttp
            p[K.blockUntrusted] = s.blockUntrusted
            p[K.confirm] = s.confirmInstalls
            p[K.debug] = s.debugLogging
            p[K.timeout] = s.timeoutSeconds
            p[K.legal] = s.legalAckVersion
            p[K.lastCheck] = s.lastCheck
        }
    }

    suspend fun reset() {
        val keep = current()
        context.dataStore.edit { it.clear() }
        update { it.copy(legalAckVersion = keep.legalAckVersion) }
    }
}
