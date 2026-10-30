package net.nekomo.nex.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.nekomo.nex.R
import net.nekomo.nex.ui.screens.AddRepoDialog
import net.nekomo.nex.ui.screens.ExtensionsScreen
import net.nekomo.nex.ui.screens.HomeScreen
import net.nekomo.nex.ui.screens.RepositoriesScreen
import net.nekomo.nex.ui.screens.SettingsScreen
import net.nekomo.nex.ui.screens.UpdatesScreen

enum class Tab(val key: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Default.Home),
    Extensions("extensions", "Extensions", Icons.Default.Extension),
    Repositories("sources", "Sources", Icons.Default.Storage),
    Updates("updates", "Updates", Icons.Default.SystemUpdate),
    Settings("settings", "Settings", Icons.Default.Settings);

    companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } }
}

@Composable
fun NexApp(vm: NexViewModel, tv: Boolean, pendingRepoUrl: MutableState<String?>, pendingTab: MutableState<String?>) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.of(s.startTab) ?: Tab.Home) }
    val snack = remember { SnackbarHostState() }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val appUpdates by vm.repo.appUpdates.collectAsStateWithLifecycle()
    val updateCount = rows.count { it.hasUpdate } + appUpdates.size

    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
    LaunchedEffect(Unit) { vm.intents.collect { runCatching { context.startActivity(it) }.onFailure { vm.say("No app can handle this action") } } }
    LaunchedEffect(pendingTab.value) { Tab.of(pendingTab.value)?.let { tab = it; pendingTab.value = null } }

    CompositionLocalProvider(LocalIsTv provides tv) {
        if (s.legalAckVersion < LEGAL_VERSION) {
            LegalGate { vm.updateSettings { it.copy(legalAckVersion = LEGAL_VERSION) } }
            return@CompositionLocalProvider
        }
        NotificationPermissionPrompt(s.notifications) { granted -> if (!granted) vm.updateSettings { it.copy(notifications = false) } }
        pendingRepoUrl.value?.let { url -> AddRepoDialog(vm, initialUrl = url, onDismiss = { pendingRepoUrl.value = null }) }

        val content: @Composable (Modifier) -> Unit = { m ->
            Box(m.fillMaxSize()) {
                when (tab) {
                    Tab.Home -> HomeScreen(vm) { tab = it }
                    Tab.Extensions -> ExtensionsScreen(vm)
                    Tab.Repositories -> RepositoriesScreen(vm)
                    Tab.Updates -> UpdatesScreen(vm)
                    Tab.Settings -> SettingsScreen(vm)
                }
            }
        }
        if (tv) {
            Scaffold(snackbarHost = { SnackbarHost(snack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
                Row(Modifier.padding(pad).fillMaxSize()) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface, header = {
                        Image(painterResource(R.drawable.nex_logo), "NEX", Modifier.padding(vertical = 16.dp).size(48.dp))
                    }) {
                        Tab.entries.forEach { t ->
                            NavigationRailItem(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) },
                                icon = { TabIcon(t, if (t == Tab.Updates) updateCount else 0) })
                        }
                    }
                    content(Modifier.weight(1f).padding(horizontal = 24.dp, vertical = 12.dp))
                }
            }
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snack) },
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(selected = tab == t, onClick = { tab = t }, label = { Text(t.label, maxLines = 1) },
                                icon = { TabIcon(t, if (t == Tab.Updates) updateCount else 0) })
                        }
                    }
                },
            ) { pad -> content(Modifier.padding(pad)) }
        }
    }
}

@Composable
private fun TabIcon(t: Tab, badge: Int) {
    if (badge > 0) BadgedBox(badge = { Badge { Text("$badge") } }) { Icon(t.icon, null) } else Icon(t.icon, null)
}

/** Notifications default to on, so Android 13+ needs the runtime permission asked once after the legal gate. */
@Composable
private fun NotificationPermissionPrompt(enabled: Boolean, onResult: (Boolean) -> Unit) {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), onResult)
    LaunchedEffect(enabled) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (enabled && !granted) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun LegalGate(onAccept: () -> Unit) {
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.heightIn(min = 24.dp))
        Image(painterResource(R.drawable.nex_logo), "NEX logo", Modifier.size(96.dp))
        Spacer(Modifier.heightIn(min = 16.dp))
        Text("Before you start", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.heightIn(min = 12.dp))
        LegalText(Modifier.fillMaxWidth())
        Spacer(Modifier.heightIn(min = 20.dp))
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text("I understand and agree") }
        Spacer(Modifier.width(1.dp).heightIn(min = 24.dp))
    }
    }
}


@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
