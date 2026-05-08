package com.juni.app.ui.vault

import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juni.app.JuniApp
import com.juni.app.data.prefs.AppSettings
import com.juni.app.data.vault.VaultEntry
import com.juni.app.data.vault.VaultRepository
import com.juni.app.ui.terminal.TermBox
import com.juni.app.ui.terminal.TermButton
import com.juni.app.ui.terminal.TermColor
import com.juni.app.ui.terminal.TermDivider
import com.juni.app.ui.terminal.TermText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VaultDebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember { JuniApp.get() }
    val settings by app.appSettings.flow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val vaultUri = settings?.vaultUri
    var entries by remember { mutableStateOf<List<VaultEntry>>(emptyList()) }
    var currentDir by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Pair<String, String>?>(null) }

    val repo: VaultRepository? = remember(vaultUri) {
        vaultUri?.let { runCatching { VaultRepository(context, Uri.parse(it)) }.getOrNull() }
    }

    LaunchedEffect(repo, currentDir) {
        if (repo == null) {
            entries = emptyList()
            return@LaunchedEffect
        }
        runCatching { repo.list(currentDir) }
            .onSuccess { entries = it; status = null }
            .onFailure { status = "list failed: ${it.message}" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            TermButton(label = "back", onClick = onBack)
            TermText(text = "vault debug", color = TermColor.Accent, bold = true)
        }
        TermDivider()

        if (repo == null) {
            TermText(
                text = "no vault selected. open Settings → pick folder.",
                color = TermColor.Dim,
            )
            return@Column
        }

        TermText(text = "dir: /${currentDir}", color = TermColor.Dim)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            TermButton(label = "root", onClick = { currentDir = "" })
            if (currentDir.isNotEmpty()) {
                TermButton(label = "..", onClick = {
                    currentDir = currentDir.substringBeforeLast('/', "")
                })
            }
            TermButton(
                label = "+ test note",
                color = TermColor.Green,
                onClick = {
                    scope.launch {
                        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                        val name = "juni-test-$ts.md"
                        val body = "# Hello from juni\n\nWritten at $ts via SAF.\n"
                        runCatching { repo.write(joinDir(currentDir, name), body) }
                            .onSuccess { status = "wrote $name"; entries = repo.list(currentDir) }
                            .onFailure { status = "write failed: ${it.message}" }
                    }
                },
            )
        }

        status?.let { TermText(text = it, color = TermColor.Accent) }

        Spacer(Modifier.height(4.dp))
        TermBox(title = "${entries.size} entries") {
            if (entries.isEmpty()) {
                TermText(text = "(empty)", color = TermColor.Muted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    entries.forEach { e ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val icon = if (e.isDirectory) "▸" else " "
                            val sizeStr = if (e.isDirectory) "<dir>" else "${e.sizeBytes}B"
                            TermButton(
                                label = "$icon ${e.name}",
                                onClick = {
                                    if (e.isDirectory) {
                                        currentDir = e.relativePath
                                    } else {
                                        scope.launch {
                                            val text = repo.read(e.relativePath) ?: "(empty / unreadable)"
                                            preview = e.relativePath to text.take(800)
                                        }
                                    }
                                },
                            )
                            TermText(text = sizeStr, color = TermColor.Muted)
                        }
                    }
                }
            }
        }

        preview?.let { (path, text) ->
            TermBox(title = path) {
                TermText(text = text, color = TermColor.Fg)
            }
        }
    }
}

private fun joinDir(dir: String, name: String): String =
    if (dir.isEmpty()) name else "$dir/$name"
