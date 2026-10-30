package net.nekomo.nex.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.nekomo.nex.data.AddResult
import net.nekomo.nex.data.AppId
import net.nekomo.nex.data.NexRepository
import net.nekomo.nex.data.db.RepoStatus
import net.nekomo.nex.data.db.RepositoryEntity
import net.nekomo.nex.integrations.NekomoAdapter
import net.nekomo.nex.integrations.RepoHandoff
import net.nekomo.nex.ui.ConfirmDialog
import net.nekomo.nex.ui.EmptyState
import net.nekomo.nex.ui.ListRow
import net.nekomo.nex.ui.NexBad
import net.nekomo.nex.ui.NexGood
import net.nekomo.nex.ui.NexMuted
import net.nekomo.nex.ui.NexViewModel
import net.nekomo.nex.ui.NexWarn
import net.nekomo.nex.ui.Pill
import net.nekomo.nex.ui.ScreenIntro
import net.nekomo.nex.ui.formatTime
import net.nekomo.nex.ui.icon

@Composable
fun RepositoriesScreen(vm: NexViewModel) {
    val repos by vm.repositories.collectAsStateWithLifecycle()
    val busy by vm.repo.busy.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<RepositoryEntity?>(null) }
    val dupes = repos.groupBy { it.url.trimEnd('/').lowercase() }.filterValues { it.size > 1 }.keys

    Column {
        ScreenIntro("Sources", "${repos.size} repositories · ${repos.count { it.status == RepoStatus.ERROR }} unavailable") {
            IconButton(onClick = { vm.launch { val r = vm.repo.refreshAll(); vm.say("Refreshed ${r.size} repositories") } }, enabled = "refresh:all" !in busy) {
                if ("refresh:all" in busy) CircularProgressIndicator(Modifier.padding(4.dp)) else Icon(Icons.Default.Refresh, "Refresh all")
            }
            IconButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "Add repository") }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (repos.isEmpty()) item {
                EmptyState("No repositories", "NEX ships with no third-party repositories. Add one by URL, or add the official Nekomo manifest.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { adding = true }) { Text("Add by URL") }
                        TextButton(onClick = {
                            vm.launch {
                                val p = vm.repo.preview(NekomoAdapter.OFFICIAL_REPO, AppId.NEKOMO).getOrThrow()
                                when (val r = vm.repo.add(p, trusted = true)) {
                                    is AddResult.Added -> vm.say("Added ${r.repo.name}")
                                    is AddResult.Failed -> vm.say(r.message)
                                }
                            }
                        }) { Text("Add Nekomo official") }
                    }
                }
            }
            items(repos, key = { it.id }) { r ->
                val app = AppId.of(r.appId)
                ListRow(
                    title = r.name,
                    subtitle = listOfNotNull(
                        app?.displayName,
                        "${r.extensionCount} items",
                        r.statusMessage?.takeIf { r.status != RepoStatus.OK },
                        if (r.url.trimEnd('/').lowercase() in dupes) "Duplicate URL" else null,
                        "checked ${formatTime(r.lastChecked)}",
                    ).joinToString(" · "),
                    leading = { app?.let { Icon(it.icon(), null) } },
                    trailing = {
                        when {
                            "refresh:${r.id}" in busy -> CircularProgressIndicator(Modifier.padding(4.dp))
                            !r.enabled -> Pill("Off", NexMuted)
                            r.status == RepoStatus.ERROR -> Pill("Error", NexBad)
                            r.status == RepoStatus.WARNING -> Pill("Warning", NexWarn)
                            r.trusted -> Pill("Trusted", NexGood)
                            else -> Pill("Untrusted", NexWarn)
                        }
                    },
                    onClick = { open = r },
                )
            }
        }
    }
    if (adding) AddRepoDialog(vm, null) { adding = false }
    open?.let { o -> repos.firstOrNull { it.id == o.id }?.let { RepoDetail(vm, it) { open = null } } ?: run { open = null } }
}

@Composable
fun AddRepoDialog(vm: NexViewModel, initialUrl: String?, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(initialUrl.orEmpty()) }
    var app by remember { mutableStateOf<AppId?>(null) }
    var preview by remember { mutableStateOf<NexRepository.Preview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var trusted by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (preview == null) "Add repository" else "Confirm repository") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val p = preview
                if (p == null) {
                    OutlinedTextField(url, { url = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Repository URL") }, singleLine = true,
                        supportingText = { Text("index.min.json, repo.json, index.json or a Nekomo manifest. CloudStream shortcodes are accepted.") })
                    Text("App (optional — detected automatically)", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(app == null, { app = null }, { Text("Auto") })
                        AppId.entries.forEach { a -> FilterChip(app == a, { app = a }, { Text(a.displayName) }) }
                    }
                    if (loading) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(4.dp)); Text("Validating…") }
                } else {
                    Kv("Name", p.index.name ?: "Unnamed")
                    Kv("App", p.appId.displayName)
                    Kv("Format", p.format.label)
                    Kv("URL", p.url)
                    Kv("Contents", "${p.index.extensions.size} items" + if (p.index.children.isNotEmpty()) " · ${p.index.children.size} linked repositories" else "")
                    p.index.description?.let { Kv("Description", it) }
                    Kv("Response time", "${p.latencyMs} ms")
                    p.index.warnings.forEach { Text(it, color = NexWarn, style = MaterialTheme.typography.bodySmall) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(trusted, { trusted = it })
                        Text("I trust this repository")
                    }
                    Text("Only add repositories you trust. Their code runs inside the target app.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = NexBad) }
            }
        },
        confirmButton = {
            TextButton(enabled = !loading && url.isNotBlank(), onClick = {
                val p = preview
                loading = true
                vm.launch {
                    try {
                        if (p == null) {
                            vm.repo.preview(url, app).onSuccess { preview = it }.onFailure { error = it.message }
                        } else when (val r = vm.repo.add(p, trusted)) {
                            is AddResult.Added -> {
                                vm.say("Added ${r.repo.name}" + if (r.warnings.isNotEmpty()) " · ${r.warnings.first()}" else "")
                                onDismiss()
                            }
                            is AddResult.Failed -> error = r.message
                        }
                    } finally { loading = false }
                }
            }) { Text(if (preview == null) "Validate" else "Add") }
        },
        dismissButton = { TextButton(onClick = { if (preview != null) preview = null else onDismiss() }) { Text(if (preview != null) "Back" else "Cancel") } },
    )
}

@Composable
private fun RepoDetail(vm: NexViewModel, r: RepositoryEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = AppId.of(r.appId)
    var confirmDelete by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(r.name) }
    var handoff by remember { mutableStateOf<RepoHandoff.CopyOrShare?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(r.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(rename, { rename = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                Kv("App", app?.displayName ?: r.appId)
                Kv("Format", r.format)
                Kv("URL", r.url)
                Kv("Status", r.status.name.lowercase().replaceFirstChar { it.uppercase() } + (r.statusMessage?.let { " — $it" } ?: ""))
                Kv("Items", "${r.extensionCount}")
                r.latencyMs?.let { Kv("Response time", "$it ms") }
                r.version?.let { Kv("Version", it) }
                Kv("Last checked", formatTime(r.lastChecked))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Enabled", Modifier.weight(1f)); Switch(r.enabled, { vm.launch { vm.repo.setEnabled(r, it) } }) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Trusted", Modifier.weight(1f)); Switch(r.trusted, { vm.launch { vm.repo.setTrusted(r, it) } }) }
                handoff?.let { Text(it.instructions, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { vm.launch { val u = vm.repo.refresh(r); vm.say("${u.name}: ${u.status.name.lowercase()}") } }) { Text("Refresh") }
                    if (app != null) TextButton(onClick = {
                        when (val h = vm.repo.adapter(app).repoHandoff(context, r.url)) {
                            is RepoHandoff.OpenIntent -> vm.intents.tryEmit(h.intent)
                            is RepoHandoff.CopyOrShare -> { copy(context, h.text); handoff = h; vm.say("Repository URL copied") }
                        }
                    }) { Text("Add to ${app.displayName}") }
                    TextButton(onClick = {
                        vm.intents.tryEmit(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, r.url), "Share repository"))
                    }) { Text("Share") }
                    TextButton(onClick = { confirmDelete = true }) { Text("Remove", color = NexBad) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (rename != r.name) vm.launch { vm.repo.rename(r, rename) }; onDismiss() }) { Text("Done") } },
    )
    if (confirmDelete) ConfirmDialog("Remove ${r.name}?", "Its extension listings are removed from NEX. Installed extensions stay installed.", "Remove", { confirmDelete = false }) {
        vm.launch { vm.repo.remove(r) }
        onDismiss()
    }
}

fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("NEX", text))
}
