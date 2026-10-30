package net.nekomo.nex.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.nekomo.nex.data.AppUpdate
import net.nekomo.nex.data.PackageKind
import net.nekomo.nex.integrations.Capability
import net.nekomo.nex.pkg.InstallOutcome
import net.nekomo.nex.ui.EmptyState
import net.nekomo.nex.ui.ExtIcon
import net.nekomo.nex.ui.ListRow
import net.nekomo.nex.ui.NexBad
import net.nekomo.nex.ui.NexGood
import net.nekomo.nex.ui.NexPurpleSoft
import net.nekomo.nex.ui.NexViewModel
import net.nekomo.nex.ui.Pill
import net.nekomo.nex.ui.ScreenIntro
import net.nekomo.nex.ui.SectionTitle
import net.nekomo.nex.ui.formatBytes
import net.nekomo.nex.ui.formatTime
import net.nekomo.nex.ui.icon

@Composable
fun UpdatesScreen(vm: NexViewModel) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val apps by vm.repo.appUpdates.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val busy by vm.repo.busy.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var notes by remember { mutableStateOf<AppUpdate?>(null) }
    val updates = rows.filter { it.hasUpdate }
    val installable = updates.filter { r ->
        r.latest?.kind == PackageKind.APK.name && vm.repo.adapter(r.appId).has(Capability.INSTALL_EXTENSIONS)
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ScreenIntro("Updates", "Last check: ${formatTime(settings?.lastCheck)}", Modifier) {
                if ("check" in busy) CircularProgressIndicator()
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.checkAll() }, enabled = "check" !in busy) { Text("Check now") }
                if (installable.size > 1) OutlinedButton(onClick = {
                    vm.launch {
                        var ok = 0
                        for (r in installable) {
                            val o = vm.repo.install(r.latest!!) { vm.intents.tryEmit(it) }
                            if (o is InstallOutcome.NeedsPermission) break
                            if (o == InstallOutcome.Success) ok++
                        }
                        vm.say("Updated $ok of ${installable.size}")
                    }
                }) { Text("Update all (${installable.size})") }
            }
        }
        if (apps.isNotEmpty()) {
            item { SectionTitle("Apps") }
            items(apps, key = { "app:${it.app.packageName}" }) { u ->
                ListRow(
                    "${u.app.label} ${u.toVersion}",
                    "Installed ${u.app.versionName}" + (u.size?.let { " · ${formatBytes(it)}" } ?: "") + " · tap for changelog",
                    leading = { androidx.compose.material3.Icon(u.app.appId.icon(), null) },
                    trailing = {
                        TextButton(enabled = "install:${u.app.packageName}" !in busy, onClick = {
                            vm.launch {
                                val o = vm.repo.installAppUpdate(u) { vm.intents.tryEmit(it) }
                                vm.say(vm.describe(o, u.app.label))
                            }
                        }) { Text("Update") }
                    },
                    onClick = { notes = u },
                )
            }
        }
        item { SectionTitle("Extensions") }
        if (updates.isEmpty()) item { EmptyState("All up to date", "Updates appear here after a check finds newer versions in your repositories.") }
        items(updates, key = { "u:${it.appId}:${it.id}" }) { r ->
            val l = r.latest!!
            val can = r in installable
            ListRow(
                r.name,
                "${r.appId.displayName} · ${r.installed?.versionName} → ${l.versionName}" + if (!can) " · update inside ${r.appId.displayName}" else "",
                leading = { ExtIcon(l.iconUrl, r.appId.icon()) },
                trailing = {
                    if (can) TextButton(enabled = "install:${l.extId}" !in busy, onClick = {
                        vm.launch {
                            val o = vm.repo.install(l) { vm.intents.tryEmit(it) }
                            if (o is InstallOutcome.NeedsPermission) vm.intents.tryEmit(o.settingsIntent)
                            vm.say(vm.describe(o, r.name))
                        }
                    }) { Text(if ("install:${l.extId}" in busy) "…" else "Update") } else Pill("Manual", NexPurpleSoft)
                },
            )
        }
        if (history.isNotEmpty()) {
            item {
                Row {
                    SectionTitle("History", Modifier.weight(1f))
                    TextButton(onClick = { vm.launch { vm.repo.clearHistory() } }) { Text("Clear") }
                }
            }
            items(history.take(100), key = { "h${it.id}" }) { h ->
                ListRow(
                    "${h.action.replaceFirstChar { it.uppercase() }} ${h.name}",
                    listOfNotNull(
                        listOfNotNull(h.fromVersion, h.toVersion).joinToString(" → ").ifBlank { null },
                        h.message,
                        formatTime(h.time),
                    ).joinToString(" · "),
                    trailing = { if (h.ok) Pill("Done", NexGood) else Pill("Failed", NexBad) },
                )
            }
        }
    }
    notes?.let { u ->
        AlertDialog(onDismissRequest = { notes = null }, title = { Text("${u.app.label} ${u.toVersion}") },
            text = { Column { Text(u.notes?.take(4000) ?: "No changelog published.") } },
            confirmButton = { TextButton(onClick = { notes = null }) { Text("Close") } })
    }
}
