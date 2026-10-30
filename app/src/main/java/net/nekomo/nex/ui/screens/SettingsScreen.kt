package net.nekomo.nex.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.nekomo.nex.BuildConfig
import net.nekomo.nex.data.AppId
import net.nekomo.nex.data.Settings
import net.nekomo.nex.data.ThemeMode
import net.nekomo.nex.data.db.ApiCategory
import net.nekomo.nex.data.db.ApiEntity
import net.nekomo.nex.integrations.IntegrationRegistry
import net.nekomo.nex.log.NexLog
import net.nekomo.nex.net.NexHttp
import net.nekomo.nex.pkg.ApkInstaller
import net.nekomo.nex.ui.ConfirmDialog
import net.nekomo.nex.ui.LegalText
import net.nekomo.nex.ui.ListRow
import net.nekomo.nex.ui.NexBad
import net.nekomo.nex.ui.NexGood
import net.nekomo.nex.ui.NexMuted
import net.nekomo.nex.ui.NexViewModel
import net.nekomo.nex.ui.NexWarn
import net.nekomo.nex.ui.Pill
import net.nekomo.nex.ui.ScreenIntro
import net.nekomo.nex.ui.SectionTitle
import net.nekomo.nex.ui.SettingSwitch
import net.nekomo.nex.ui.Tab
import net.nekomo.nex.ui.formatBytes
import net.nekomo.nex.ui.formatTime
import net.nekomo.nex.ui.icon

private enum class Page { MAIN, APIS, INTEGRATIONS, DIAGNOSTICS, LEGAL }

object Links {
    const val SOURCE = "https://github.com/Nekomo-App/NEX"
    const val ISSUES = "https://github.com/Nekomo-App/NEX/issues"
    const val ORG = "https://github.com/Nekomo-App"
    const val NEKOMO_SITE = "https://nekomoapp.netlify.app"
    const val DEV_GITHUB = "https://github.com/marshmeadow2772"
    const val DEV_SITE = "https://marshmeadow.netlify.app"
}

@Composable
fun SettingsScreen(vm: NexViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    var page by rememberSaveable { mutableStateOf(Page.MAIN) }
    androidx.activity.compose.BackHandler(page != Page.MAIN) { page = Page.MAIN }
    when (page) {
        Page.MAIN -> MainSettings(vm, s) { page = it }
        Page.APIS -> ApisPage(vm) { page = Page.MAIN }
        Page.INTEGRATIONS -> IntegrationsPage(vm) { page = Page.MAIN }
        Page.DIAGNOSTICS -> DiagnosticsPage(vm) { page = Page.MAIN }
        Page.LEGAL -> LegalPage { page = Page.MAIN }
    }
}

@Composable
private fun BackRow(title: String, onBack: () -> Unit) {
    ScreenIntro(title, "Settings") { TextButton(onClick = onBack) { Text("Back") } }
}

@Composable
private fun MainSettings(vm: NexViewModel, s: Settings, go: (Page) -> Unit) {
    val context = LocalContext.current
    fun set(t: (Settings) -> Settings) = vm.updateSettings(t)
    var timeout by remember { mutableStateOf(s.timeoutSeconds.toString()) }
    var confirmReset by remember { mutableStateOf(false) }
    var restoreText by remember { mutableStateOf<String?>(null) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (!ok) vm.say("Notifications are blocked in system settings") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        vm.launch {
            val text = vm.repo.exportBackup()
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
            vm.say("Backup saved (API keys are never included)")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        vm.launch { restoreText = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenIntro("Settings", "NEX ${BuildConfig.VERSION_NAME}") }

        item { SectionTitle("General") }
        item {
            Column {
                Text("Theme", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemeMode.entries.forEach { m -> FilterChip(s.theme == m, { set { it.copy(theme = m) } }, { Text(m.label) }) }
                }
                Text("Start screen", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tab.entries.forEach { t -> FilterChip(s.startTab == t.key, { set { it.copy(startTab = t.key) } }, { Text(t.label) }) }
                }
            }
        }
        item {
            SettingSwitch("Notifications", "Tell me when updates are found or repositories break", s.notifications) { on ->
                if (on && android.os.Build.VERSION.SDK_INT >= 33) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                set { it.copy(notifications = on) }
            }
        }

        item { SectionTitle("Updates") }
        item { SettingSwitch("Automatic checks", "Check in the background every ${s.checkIntervalHours} h", s.autoCheck) { on -> set { it.copy(autoCheck = on) } } }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(6, 12, 24, 48).forEach { h -> FilterChip(s.checkIntervalHours == h, { set { it.copy(checkIntervalHours = h) } }, { Text("Every $h h") }, enabled = s.autoCheck) }
            }
        }
        item { SettingSwitch("Wi-Fi only", "Skip background checks on metered networks", s.wifiOnly, s.autoCheck) { on -> set { it.copy(wifiOnly = on) } } }
        item { SettingSwitch("Refresh repositories during checks", null, s.autoRefreshRepos) { on -> set { it.copy(autoRefreshRepos = on) } } }

        item { SectionTitle("Extensions") }
        item { SettingSwitch("Compatibility warnings", "Flag extensions built for a different app library version", s.compatWarnings) { on -> set { it.copy(compatWarnings = on) } } }
        item { SettingSwitch("Show 18+ extensions", "Hidden by default", s.showNsfw) { on -> set { it.copy(showNsfw = on) } } }

        item { SectionTitle("Repositories & integrations") }
        item { ListRow("Integrations", "Detected apps, capabilities and known limitations", onClick = { go(Page.INTEGRATIONS) }) }
        item { ListRow("APIs & sources", "Metadata APIs and endpoints, with keys stored in the Android Keystore", onClick = { go(Page.APIS) }) }
        item { ListRow("Export backup", "Repositories, API settings and preferences as JSON", onClick = { exportLauncher.launch("nex-backup.json") }) }
        item { ListRow("Restore backup", "NEX backup or a plain list of repository URLs", onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) }

        item { SectionTitle("Security") }
        item { SettingSwitch("Block untrusted repositories", "Only install from repositories you marked as trusted", s.blockUntrusted) { on -> set { it.copy(blockUntrusted = on) } } }
        item { SettingSwitch("Allow plain HTTP", "Not recommended: unencrypted indexes can be tampered with", s.allowHttp) { on -> set { it.copy(allowHttp = on) } } }
        item {
            val can = ApkInstaller.canInstall(context)
            ListRow("Install permission", if (can) "NEX can open the system installer. Every install is still confirmed by you." else "Needed to install Mihon/Aniyomi extensions and Nekomo updates",
                trailing = { Pill(if (can) "Granted" else "Off", if (can) NexGood else NexWarn) },
                onClick = { vm.intents.tryEmit(ApkInstaller.permissionIntent(context)) })
        }

        item { SectionTitle("Advanced") }
        item {
            OutlinedTextField(timeout, { v -> timeout = v.filter(Char::isDigit).take(3); timeout.toIntOrNull()?.takeIf { it in 5..120 }?.let { n -> set { it.copy(timeoutSeconds = n) } } },
                Modifier.fillMaxWidth(), label = { Text("Network timeout (5–120 s)") }, singleLine = true)
        }
        item { SettingSwitch("Debug logging", "Write verbose logs (no keys or tokens are logged)", s.debugLogging) { on -> set { it.copy(debugLogging = on) } } }
        item { ListRow("Diagnostics & logs", "Storage use, network state and the NEX log", onClick = { go(Page.DIAGNOSTICS) }) }
        item { ListRow("Clear caches", "Cached indexes and downloaded APKs", onClick = { vm.launch { vm.repo.clearMetadataCache(); vm.say("Caches cleared") } }) }
        item { ListRow("Reset NEX data", "Removes repositories, APIs, keys and history", trailing = { Pill("Danger", NexBad) }, onClick = { confirmReset = true }) }

        item { SectionTitle("About & legal") }
        item { ListRow("Legal notice & DMCA", "Review the notice you accepted", onClick = { go(Page.LEGAL) }) }
        items(
            listOf(
                Triple("Source code", "Nekomo-App/NEX · Apache-2.0", Links.SOURCE),
                Triple("Report an issue", "GitHub issues", Links.ISSUES),
                Triple("Nekomo community", "github.com/Nekomo-App", Links.ORG),
                Triple("Nekomo website", "nekomoapp.netlify.app", Links.NEKOMO_SITE),
                Triple("Developer", "MarshMeadow · github.com/marshmeadow2772", Links.DEV_GITHUB),
                Triple("Developer website", "marshmeadow.netlify.app", Links.DEV_SITE),
            ),
        ) { (t, sub, url) -> ListRow(t, sub, onClick = { vm.intents.tryEmit(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) }
        item {
            Text("NEX ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · made by the Nekomo community. Not affiliated with Mihon, Aniyomi, CloudStream or Zangetsu.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (confirmReset) ConfirmDialog("Reset NEX?", "All repositories, API settings, stored keys and history are deleted. Installed extensions are not touched.", "Reset", { confirmReset = false }) {
        vm.launch { vm.repo.resetDatabase(); vm.say("NEX data reset") }
    }
    restoreText?.let { text ->
        var withSettings by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { restoreText = null },
            title = { Text("Restore backup") },
            text = {
                Column {
                    Text("Repositories and APIs that already exist are skipped. API keys must be re-entered.")
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Also restore preferences", Modifier.weight(1f)); Switch(withSettings, { withSettings = it }) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    restoreText = null
                    vm.launch {
                        val r = vm.repo.importBackup(text, withSettings)
                        vm.say("Restored ${r.repos} repositories, ${r.apis} APIs" + if (r.skipped > 0) " · ${r.skipped} skipped" else "")
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { restoreText = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun IntegrationsPage(vm: NexViewModel, onBack: () -> Unit) {
    val apps by vm.repo.apps.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { BackRow("Integrations", onBack) }
        items(IntegrationRegistry.adapters, key = { it.appId.name }) { ad ->
            val found = apps.filter { it.appId == ad.appId }
            ListRow(
                ad.displayName,
                buildString {
                    append(if (found.isEmpty()) "Not installed" else found.joinToString { "${it.label} ${it.versionName} (${it.packageName})" })
                    append("\nFormats: ").append(ad.repoFormats.joinToString { it.label })
                    append("\nNEX can: ").append(ad.capabilities.joinToString { it.name.lowercase().replace('_', ' ') }.ifBlank { "browse and validate repositories" })
                    ad.limitations.forEach { append("\n• ").append(it) }
                },
                leading = { androidx.compose.material3.Icon(ad.appId.icon(), null) },
                trailing = { Pill(if (found.isEmpty()) "Missing" else "Detected", if (found.isEmpty()) NexMuted else NexGood) },
                onClick = { vm.intents.tryEmit(Intent(Intent.ACTION_VIEW, Uri.parse(ad.homepage))) },
            )
        }
    }
}

@Composable
private fun ApisPage(vm: NexViewModel, onBack: () -> Unit) {
    val apis by vm.apis.collectAsStateWithLifecycle()
    val busy by vm.repo.busy.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ApiEntity?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { BackRow("APIs & sources", onBack) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { editing = ApiEntity(name = "", baseUrl = "https://") }) { Text("Add API") }
                TextButton(onClick = { vm.launch { vm.repo.testAllApis(); vm.say("Tested all enabled APIs") } }) { Text("Test all") }
            }
        }
        if (apis.isEmpty()) item {
            Text("No APIs yet. Metadata APIs listed by a Nekomo manifest are added automatically; you can also add your own.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ApiCategory.entries.forEach { cat ->
            val list = apis.filter { it.category == cat }
            if (list.isNotEmpty()) {
                item { SectionTitle(cat.label) }
                items(list, key = { it.id }) { a ->
                    ListRow(
                        a.name,
                        listOfNotNull(a.baseUrl, a.backupUrl?.let { "backup: $it" }, if (a.hasKey) "key stored" else null, a.lastStatus).joinToString(" · "),
                        trailing = {
                            when {
                                "api:${a.id}" in busy -> Text("…")
                                !a.enabled -> Pill("Off")
                                a.lastOk == true -> Pill("OK", NexGood)
                                a.lastOk == false -> Pill("Failing", NexBad)
                                else -> Pill("Untested")
                            }
                        },
                        onClick = { editing = a },
                    )
                }
            }
        }
    }
    editing?.let { ApiEditor(vm, it) { editing = null } }
}

@Composable
private fun ApiEditor(vm: NexViewModel, api: ApiEntity, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(api.name) }
    var base by remember { mutableStateOf(api.baseUrl) }
    var backup by remember { mutableStateOf(api.backupUrl.orEmpty()) }
    var path by remember { mutableStateOf(api.testPath) }
    var header by remember { mutableStateOf(api.keyHeader.orEmpty()) }
    var key by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(api.category) }
    var app by remember { mutableStateOf(api.appId?.let { AppId.of(it) }) }
    var enabled by remember { mutableStateOf(api.enabled) }
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (api.id == 0L) "Add API" else api.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ApiCategory.entries.forEach { c -> FilterChip(cat == c, { cat = c }, { Text(c.label) }) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(app == null, { app = null }, { Text("Any app") })
                    AppId.entries.forEach { a -> FilterChip(app == a, { app = a }, { Text(a.displayName) }) }
                }
                OutlinedTextField(base, { base = it }, label = { Text("Primary endpoint") }, singleLine = true)
                OutlinedTextField(backup, { backup = it }, label = { Text("Backup endpoint (optional)") }, singleLine = true)
                OutlinedTextField(path, { path = it }, label = { Text("Health-check path (optional)") }, singleLine = true)
                OutlinedTextField(header, { header = it }, label = { Text("Key header, e.g. Authorization or x-api-key") }, singleLine = true)
                OutlinedTextField(key, { key = it }, label = { Text(if (api.hasKey) "API key (stored — leave blank to keep)" else "API key (optional)") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation())
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Enabled", Modifier.weight(1f)); Switch(enabled, { enabled = it }) }
                api.lastStatus?.let { Text("Last test: $it", color = if (api.lastOk == true) NexGood else NexWarn, style = MaterialTheme.typography.bodySmall) }
                if (api.id != 0L) Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { vm.launch { val r = vm.repo.testApi(api); vm.say("${r.name}: ${r.lastStatus}") } }) { Text("Test") }
                    // No supported app accepts an API by intent, so copy the endpoint and open the app to paste it.
                    val target = app
                    val launchApp = target?.let { remember(it) { vm.repo.adapter(it).launchIntent(context) } }
                    if (target != null && launchApp != null) TextButton(onClick = {
                        copy(context, base.trim())
                        vm.say("Endpoint copied. Paste it in ${target.displayName}")
                        vm.intents.tryEmit(launchApp)
                    }) { Text("Open in ${target.displayName}") }
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = NexBad) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && base.startsWith("http"), onClick = {
                vm.launch {
                    val v = net.nekomo.nex.util.Urls.validate(base.trim(), vm.repo.settings.current().allowHttp)
                    if (v is net.nekomo.nex.util.Urls.Validation.Invalid) { vm.say(v.reason); return@launch }
                    vm.repo.saveApi(
                        api.copy(name = name.trim(), baseUrl = base.trim(), backupUrl = backup.trim().ifBlank { null }, testPath = path.trim(),
                            keyHeader = header.trim().ifBlank { null }, category = cat, appId = app?.name, enabled = enabled),
                        key.ifBlank { null },
                    )
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirmDelete) ConfirmDialog("Delete ${api.name}?", "Its stored key is deleted too.", "Delete", { confirmDelete = false }) {
        vm.launch { vm.repo.deleteApi(api) }
        onDismiss()
    }
}

@Composable
private fun DiagnosticsPage(vm: NexViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var log by remember { mutableStateOf(NexLog.read()) }
    val repos by vm.repositories.collectAsStateWithLifecycle()
    val apps by vm.repo.apps.collectAsStateWithLifecycle()
    val report = buildString {
        appendLine("NEX ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
        appendLine("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("Online: ${NexHttp.isOnline(context)} · metered: ${NexHttp.isMetered(context)}")
        appendLine("Apps: " + apps.joinToString { "${it.packageName} ${it.versionName}" }.ifBlank { "none" })
        appendLine("Repositories: ${repos.size} (${repos.count { it.status.name == "ERROR" }} errors)")
        appendLine("Cache ${formatBytes(NexHttp.cacheSizeBytes())} · DB ${formatBytes(vm.repo.databaseSizeBytes())} · log ${formatBytes(NexLog.sizeBytes())}")
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackRow("Diagnostics", onBack)
        Text(report, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { copy(context, report + "\n" + log.takeLast(20000)); vm.say("Copied") }) { Text("Copy report") }
            TextButton(onClick = {
                vm.intents.tryEmit(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report + "\n" + log.takeLast(50000)), "Share diagnostics"))
            }) { Text("Share") }
            TextButton(onClick = { NexLog.clear(); log = "" }) { Text("Clear log") }
        }
        SectionTitle("Log (last updated ${formatTime(System.currentTimeMillis())})")
        Text(log.takeLast(30000).ifBlank { "Empty" }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LegalPage(onBack: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        BackRow("Legal notice", onBack)
        LegalText()
    }
}
