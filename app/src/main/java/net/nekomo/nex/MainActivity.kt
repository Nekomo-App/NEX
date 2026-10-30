package net.nekomo.nex

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.nekomo.nex.data.ThemeMode
import net.nekomo.nex.ui.NexApp
import net.nekomo.nex.ui.NexTheme
import net.nekomo.nex.ui.NexViewModel
import net.nekomo.nex.ui.isTv

class MainActivity : ComponentActivity() {
    private val vm: NexViewModel by viewModels()
    private val pendingRepoUrl = mutableStateOf<String?>(null)
    private val pendingTab = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        val tv = isTv(this)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            NexTheme(settings?.theme ?: ThemeMode.DARK, tv) {
                NexApp(vm, tv, pendingRepoUrl, pendingTab)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.rescan()
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_TAB)?.let { pendingTab.value = it }
        val data = intent.data
        if (data?.scheme == "nex" && data.host == "add-repo") data.getQueryParameter("url")?.let { pendingRepoUrl.value = it }
    }

    companion object {
        const val EXTRA_TAB = "nex_tab"
    }
}
