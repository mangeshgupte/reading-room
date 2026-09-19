package com.mangesh.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mangesh.reader.BuildConfig
import com.mangesh.reader.updater.Updater
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: QueueViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.store.state.collectAsState()
    var url by remember { mutableStateOf(vm.settings.serverUrl) }
    var token by remember { mutableStateOf(vm.settings.token) }
    var updateStatus by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    val p = LocalPalette.current

    fun save() { vm.settings.serverUrl = url; vm.settings.token = token }

    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                   .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 32.dp),
               verticalArrangement = Arrangement.spacedBy(32.dp)) {

            Text("Settings", style = Type.screenTitle, color = p.fgColor)

            Section("Mac") {
                Field("Server (this app's namespace)") {
                    HairlineField(value = url, onValueChange = { url = it }, placeholder = "http://…")
                }
                Field("Token") {
                    HairlineField(value = token, onValueChange = { token = it }, placeholder = "Paste the token",
                                  visualTransformation = PasswordVisualTransformation())
                }
                Note("Token: on the Mac, `python3 ~/vibes/server/serve.py --token reader`. A .local name is found over Bonjour" +
                     (if (vm.settings.discoveredUrl.isNotEmpty()) " — last seen at ${vm.settings.discoveredUrl}." else "; nothing found yet.") +
                     " Type the Mac's IP instead to skip discovery.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlineButton("Save", onClick = { save() })
                    OutlineButton(if (vm.syncing) "Syncing…" else "Sync now", onClick = { save(); vm.sync(manual = true) },
                                  primary = true, enabled = !vm.syncing)
                }
                Note(when {
                    vm.syncing -> vm.syncProgress.ifEmpty { "syncing…" }
                    state.lastError.isNotEmpty() -> "Last attempt: ${state.lastError}" +
                        (if (state.lastSync > 0) "\nLast success: ${ago(state.lastSync)} · ${state.lastSyncNote}" else "")
                    state.lastSync > 0 -> "Last sync: ${ago(state.lastSync)} · ${state.lastSyncNote}"
                    else -> "Never synced."
                } + (if (state.outbox.isNotEmpty()) "\n${state.outbox.size} action(s) waiting to sync" else ""))
            }

            if (state.refused.isNotEmpty()) {
                Section("Needs attention · ${state.refused.size}") {
                    // hairline rows, as in the queue; no cards
                    Column {
                        for (r in state.refused) {
                            val a = r.action
                            val title = state.entries.firstOrNull { it.id == a.id }?.title ?: "#${a.id}"
                            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                MetaLabel(a.type)
                                Text(title, style = Type.rowTitle, color = p.fgColor)
                                if (a.type == "comment") Text("“${a.text}”", style = Type.excerpt, color = p.fg2Color)
                                Text(r.error, style = Type.figure, color = p.accentColor)
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    if (a.type == "comment") {
                                        TextAction("Re-place", { vm.replaceUuid = a.uuid; vm.open(a.id) })
                                    } else {
                                        TextAction("Retry", { vm.store.retryRefused(a.uuid) })
                                    }
                                    TextAction("Dismiss", { vm.store.dismissRefused(a.uuid) })
                                }
                            }
                            Hairline()
                        }
                    }
                }
            }

            Section("Reading") {
                ReadingControls(vm, withSystemTheme = true)
                Note("Swipe up or tap low on the page for the next page, swipe down or tap high for the previous; tap the middle " +
                     "for the bars. Pinch in the reader changes the text size too.")
            }

            Section("Storage") {
                val downloaded = state.entries.count { it.downloaded }
                Note("$downloaded of ${state.entries.size} reports downloaded · ${"%.1f".format(vm.store.cacheBytes() / 1e6)} MB")
                Row { OutlineButton("Clear cache", onClick = { vm.store.clearCache() }) }
            }

            Section("App") {
                Note("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                Row {
                    OutlineButton("Check for updates", enabled = !checking, onClick = {
                        checking = true
                        updateStatus = "Checking…"
                        scope.launch {
                            save()
                            updateStatus = when (val r = Updater(context).checkAndInstall(vm.resolveBase(), vm.settings.token)) {
                                is Updater.Result.UpToDate -> "Already up to date."
                                is Updater.Result.UpdateStarted -> "Installing ${r.versionName}…"
                                is Updater.Result.Unreachable -> r.detail
                            }
                            checking = false
                        }
                    })
                }
                if (updateStatus.isNotBlank()) Note(updateStatus)
            }
        }
    }
}

/** A settings section: meta label over a hairline, then its controls. */
@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            MetaLabel(label)
            Spacer(Modifier.height(10.dp))
            Hairline()
        }
        content()
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = Type.figure, color = LocalPalette.current.mutedColor)
        content()
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = Type.figure.copy(lineHeight = Type.excerpt.lineHeight), color = LocalPalette.current.mutedColor)
}
