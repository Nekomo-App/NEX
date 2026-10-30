package net.nekomo.nex.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.nekomo.nex.NexApplication
import net.nekomo.nex.data.ExtensionRow
import net.nekomo.nex.data.Settings
import net.nekomo.nex.pkg.InstallOutcome

class NexViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as NexApplication).repo

    val settings: StateFlow<Settings?> = repo.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val repositories = repo.repositories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history = repo.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val activity = repo.activity.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val apis = repo.apis.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val rows: StateFlow<List<ExtensionRow>> = combine(repo.installed, repo.remoteExtensions, repo.settings.flow) { i, r, s ->
        repo.rows(i, r, s.showNsfw)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** One-shot messages for the snackbar. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Intents the activity must start (installer permission screen, etc.). */
    val intents = MutableSharedFlow<Intent>(extraBufferCapacity = 4)

    fun say(msg: String) { messages.tryEmit(msg) }

    fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            say(e.message ?: e.javaClass.simpleName)
        }
    }

    fun updateSettings(t: (Settings) -> Settings) = launch { repo.settings.update(t) }

    fun rescan() = launch { repo.scanDevice() }

    fun checkAll() = launch {
        val s = repo.checkEverything()
        val n = s.extensionUpdates.size + s.appUpdates.size
        say(if (n == 0) "Everything is up to date" + (if (s.brokenRepos > 0) " · ${s.brokenRepos} repositories unavailable" else "") else "$n update${if (n == 1) "" else "s"} available")
    }

    fun describe(o: InstallOutcome, name: String) = when (o) {
        InstallOutcome.Success -> "$name installed"
        InstallOutcome.Cancelled -> "Installation cancelled"
        is InstallOutcome.Failure -> "Couldn't install $name: ${o.message}"
        is InstallOutcome.NeedsPermission -> "Allow NEX to install apps, then try again"
    }
}
