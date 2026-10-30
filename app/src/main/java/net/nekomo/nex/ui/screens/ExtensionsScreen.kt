package net.nekomo.nex.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState as rs
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.nekomo.nex.data.AppId
import net.nekomo.nex.data.Compatibility
import net.nekomo.nex.data.ExtensionRow
import net.nekomo.nex.data.PackageKind
import net.nekomo.nex.integrations.Capability
import net.nekomo.nex.pkg.InstallOutcome
import net.nekomo.nex.pkg.Packages
import net.nekomo.nex.ui.EmptyState
import net.nekomo.nex.ui.ExtIcon
import net.nekomo.nex.ui.ListRow
import net.nekomo.nex.ui.NexBad
import net.nekomo.nex.ui.NexGood
import net.nekomo.nex.ui.NexPurpleSoft
import net.nekomo.nex.ui.NexViewModel
import net.nekomo.nex.ui.NexWarn
import net.nekomo.nex.ui.Pill
import net.nekomo.nex.ui.ScreenIntro
import net.nekomo.nex.ui.formatBytes
import net.nekomo.nex.ui.icon

private enum class Filter(val label: String) { ALL("All"), INSTALLED("Installed"), UPDATES("Updates"), AVAILABLE("Available"), PROBLEMS("Problems") }
private enum class Sort(val label: String) { NAME("Name"), APP("App"), LANG("Language") }

fun ExtensionRow.problem(compatWarnings: Boolean = true): String? {
    val c = installed?.compatibility?.takeIf { compatWarnings }
    return when {
        c == Compatibility.OUTDATED_LIB -> installed?.compatibilityNote ?: "Built for an older extension library"
        c == Compatibility.TOO_NEW -> installed?.compatibilityNote ?: "Needs a newer app version"
        c == Compatibility.BROKEN -> installed?.compatibilityNote ?: "Broken"
        installed != null && offers.isEmpty() -> "Not found in any enabled repository"
        duplicateRepos -> "Offered by ${offers.map { it.repoId }.distinct().size} repositories"
        else -> null
    }
}

@Composable
fun ExtensionsScreen(vm: NexViewModel) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val compat = vm.settings.collectAsStateWithLifecycle().value?.compatWarnings ?: true
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var app by rememberSaveable { mutableStateOf<AppId?>(null) }
    var sort by rememberSaveable { mutableStateOf(Sort.NAME) }
    var open by remember { mutableStateOf<ExtensionRow?>(null) }

    val shown = rows.asSequence()
        .filter { app == null || it.appId == app }
        .filter {
            when (filter) {
                Filter.ALL -> true
                Filter.INSTALLED -> it.installed != null
                Filter.UPDATES -> it.hasUpdate
                Filter.AVAILABLE -> it.installed == null
                Filter.PROBLEMS -> it.problem(compat) != null
            }
        }
        .filter { query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true) || (it.latest?.lang ?: it.installed?.lang).orEmpty().equals(query, true) }
        .sortedWith(
            when (sort) {
                Sort.NAME -> compareBy { it.name.lowercase() }
                Sort.APP -> compareBy<ExtensionRow> { it.appId.ordinal }.thenBy { it.name.lowercase() }
                Sort.LANG -> compareBy<ExtensionRow> { it.latest?.lang ?: it.installed?.lang ?: "~" }.thenBy { it.name.lowercase() }
            }
        ).toList()

    Column {
        ScreenIntro("Extensions", "${rows.count { it.installed != null }} installed · ${rows.size} known")
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
            placeholder = { Text("Search name, package or language") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Filter.entries.forEach { f -> FilterChip(filter == f, { filter = f }, { Text(f.label) }) }
        }
        Row(Modifier.horizontalScroll(rs()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(app == null, { app = null }, { Text("All apps") })
            AppId.entries.forEach { a -> FilterChip(app == a, { app = if (app == a) null else a }, { Text(a.displayName) }) }
            Sort.entries.forEach { o -> FilterChip(sort == o, { sort = o }, { Text("Sort: ${o.label}") }) }
        }
        if (shown.isEmpty()) {
            EmptyState(
                if (rows.isEmpty()) "No extensions yet" else "Nothing matches",
                if (rows.isEmpty()) "Add a repository under Sources. Installed Mihon and Aniyomi extensions appear here automatically." else "Try another filter or search.",
            )
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { "${it.appId}:${it.id}" }) { r ->
                val p = r.problem(compat)
                ListRow(
                    title = r.name,
                    subtitle = listOfNotNull(
                        r.appId.displayName,
                        (r.latest?.lang ?: r.installed?.lang)?.takeIf { it.isNotBlank() },
                        r.installed?.let { "v${it.versionName}" } ?: r.latest?.let { "v${it.versionName}" },
                        p,
                    ).joinToString(" · "),
                    leading = { ExtIcon(r.latest?.iconUrl, r.appId.icon()) },
                    trailing = {
                        when {
                            r.hasUpdate -> Pill("Update", NexPurpleSoft)
                            compat && p != null && r.installed?.compatibility in setOf(Compatibility.OUTDATED_LIB, Compatibility.TOO_NEW, Compatibility.BROKEN) -> Pill("Incompatible", NexBad)
                            r.installed != null -> Pill("Installed", NexGood)
                            (r.latest?.nsfw == true) -> Pill("18+", NexWarn)
                            else -> Pill(r.latest?.kind?.let { PackageKind.valueOf(it).name } ?: "")
                        }
                    },
                    onClick = { open = r },
                )
            }
        }
    }
    open?.let { r -> ExtensionDetail(vm, rows.firstOrNull { it.appId == r.appId && it.id == r.id } ?: r) { open = null } }
}

@Composable
private fun ExtensionDetail(vm: NexViewModel, r: ExtensionRow, onDismiss: () -> Unit) {
    val busy by vm.repo.busy.collectAsStateWithLifecycle()
    val compat = vm.settings.collectAsStateWithLifecycle().value?.compatWarnings ?: true
    val repos by vm.repositories.collectAsStateWithLifecycle()
    val ad = vm.repo.adapter(r.appId)
    val latest = r.latest
    val kind = latest?.kind?.let { PackageKind.valueOf(it) } ?: r.installed?.kind ?: PackageKind.APK
    val canInstall = kind == PackageKind.APK && ad.has(Capability.INSTALL_EXTENSIONS) && latest?.downloadUrl != null
    val installing = latest != null && "install:${latest.extId}" in busy
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { ExtIcon(latest?.iconUrl, r.appId.icon(), 48) },
        title = { Text(r.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Kv("App", ad.displayName)
                Kv("Package / ID", r.id)
                Kv("Type", kind.label)
                r.installed?.let { Kv("Installed", "${it.versionName} (${it.versionCode})") }
                latest?.let { Kv("Latest", "${it.versionName} (${it.versionCode})") }
                (latest?.lang ?: r.installed?.lang)?.let { Kv("Language", it) }
                latest?.authors?.takeIf { it.isNotEmpty() }?.let { Kv("Authors", it.joinToString()) }
                latest?.fileSize?.let { Kv("Size", formatBytes(it)) }
                latest?.sha256?.let { Kv("SHA-256", it) }
                if (r.offers.isNotEmpty()) Kv("Repositories", r.offers.map { o -> repos.firstOrNull { it.id == o.repoId }?.name ?: "#${o.repoId}" }.distinct().joinToString())
                r.problem(compat)?.let { Text(it, color = NexWarn) }
                latest?.description?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (!canInstall && kind != PackageKind.APK) Text(ad.limitations.firstOrNull() ?: "Install this from inside ${ad.displayName}.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (installing) Text("Downloading and verifying…", color = NexPurpleSoft)
            }
        },
        confirmButton = {
            Row {
                if (canInstall && latest != null && (r.installed == null || r.hasUpdate)) {
                    TextButton(enabled = !installing, onClick = {
                        vm.launch {
                            val o = vm.repo.install(latest) { vm.intents.tryEmit(it) }
                            if (o is InstallOutcome.NeedsPermission) vm.intents.tryEmit(o.settingsIntent)
                            vm.say(vm.describe(o, r.name))
                        }
                    }) { Text(if (r.installed == null) "Install" else "Update") }
                }
                if (r.installed != null && kind == PackageKind.APK) {
                    TextButton(onClick = { vm.intents.tryEmit(Packages.uninstallIntent(r.id)); vm.launch { vm.repo.recordUninstall(r) } }) { Text("Uninstall") }
                    TextButton(onClick = { vm.intents.tryEmit(Packages.appDetailsIntent(r.id)) }) { Text("App info") }
                }
                if (!canInstall && latest?.homepage != null) TextButton(onClick = { vm.intents.tryEmit(Intent(Intent.ACTION_VIEW, Uri.parse(latest.homepage))) }) { Text("Website") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun Kv(k: String, v: String) {
    Column {
        Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodyMedium)
    }
}
