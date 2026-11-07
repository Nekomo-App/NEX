package net.nekomo.nex.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

/**
 * NEX is a developer-oriented tool. While Settings > "Developer warning on every launch" is on (the default) this reminder
 * appears each time the app opens. "Don't show again" turns that setting off; it can be turned back on in Settings.
 */
@Composable
fun DevWarningDialog(enabled: Boolean, onDisable: () -> Unit) {
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (!enabled || dismissed) return
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Built for developers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NEX manages repositories, extensions, plugins and API settings for other apps. It is aimed at developers and advanced users.")
                Text("Be careful with any setting you change or toggle: repositories and extensions are third-party code, and wrong settings can break the apps NEX manages.")
                Text("Only add repositories you trust, and read each confirmation before you continue.")
            }
        },
        confirmButton = { TextButton(onClick = { dismissed = true }) { Text("I understand") } },
        dismissButton = { TextButton(onClick = { onDisable(); dismissed = true }) { Text("Don't show again") } },
    )
}
