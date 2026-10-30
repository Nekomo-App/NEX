package net.nekomo.nex.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.nekomo.nex.R
import net.nekomo.nex.data.db.RepoStatus
import net.nekomo.nex.integrations.IntegrationRegistry
import net.nekomo.nex.ui.ListRow
import net.nekomo.nex.ui.NexCard
import net.nekomo.nex.ui.NexGood
import net.nekomo.nex.ui.NexMuted
import net.nekomo.nex.ui.NexViewModel
import net.nekomo.nex.ui.NexWarn
import net.nekomo.nex.ui.NexBad
import net.nekomo.nex.ui.Pill
import net.nekomo.nex.ui.SectionTitle
import net.nekomo.nex.ui.Tab
import net.nekomo.nex.ui.formatTime
import net.nekomo.nex.ui.icon

@Composable
fun HomeScreen(vm: NexViewModel, go: (Tab) -> Unit) {
    val context = LocalContext.current
    val apps by vm.repo.apps.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val repos by vm.repositories.collectAsStateWithLifecycle()
    val apis by vm.apis.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val appUpdates by vm.repo.appUpdates.collectAsStateWithLifecycle()
    val busy by vm.repo.busy.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val installed = rows.count { it.installed != null }
    val updates = rows.count { it.hasUpdate } + appUpdates.size
    val broken = repos.count { it.status == RepoStatus.ERROR }
    val apiDown = apis.count { it.enabled && it.lastOk == false }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.nex_logo), null, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("NEX", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Nekomo Extension Exchange", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { vm.checkAll() }, enabled = "check" !in busy) { Icon(Icons.Default.Refresh, "Check for updates") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Installed", "$installed", Modifier.weight(1f)) { go(Tab.Extensions) }
                Stat("Updates", "$updates", Modifier.weight(1f)) { go(Tab.Updates) }
                Stat("Repositories", "${repos.size}", Modifier.weight(1f)) { go(Tab.Repositories) }
            }
        }
        item {
            Text(
                if ("check" in busy) "Checking…" else "Last check: ${formatTime(settings?.lastCheck)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            )
        }
        if (broken > 0 || apiDown > 0) item {
            SectionTitle("Needs attention")
            if (broken > 0) ListRow("$broken repositor${if (broken == 1) "y" else "ies"} unavailable", "Open Sources to see the error and retry", trailing = { Pill("Error", NexBad) }, onClick = { go(Tab.Repositories) })
            if (apiDown > 0) ListRow("$apiDown API${if (apiDown == 1) "" else "s"} failing", "Settings → APIs & sources", trailing = { Pill("Down", NexWarn) }, onClick = { go(Tab.Settings) })
        }
        item { SectionTitle("Supported apps") }
        items(IntegrationRegistry.adapters, key = { it.appId.name }) { ad ->
            val found = apps.filter { it.appId == ad.appId }
            val ext = rows.count { it.appId == ad.appId && it.installed != null }
            ListRow(
                title = ad.displayName,
                subtitle = if (found.isEmpty()) ad.description
                else found.joinToString(" · ") { "${it.label} ${it.versionName}" } + if (ad.extensionFeature != null) " · $ext extensions" else "",
                leading = { Icon(ad.appId.icon(), null) },
                trailing = { if (found.isEmpty()) Pill("Not installed", NexMuted) else Pill("Detected", NexGood) },
                onClick = {
                    val launch = ad.launchIntent(context)
                    if (launch != null) vm.intents.tryEmit(launch)
                    else vm.intents.tryEmit(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(ad.homepage)))
                },
            )
        }
        if (activity.isNotEmpty()) {
            item { SectionTitle("Recent activity") }
            items(activity.take(8), key = { "a${it.id}" }) { a ->
                ListRow(a.title, listOfNotNull(a.detail, formatTime(a.time)).joinToString(" · "), trailing = { if (!a.ok) Pill("Failed", NexBad) })
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    NexCard(modifier, onClick = onClick) {
        Column(Modifier.padding(14.dp).fillMaxWidth()) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}
