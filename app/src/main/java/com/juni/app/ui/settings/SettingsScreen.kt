package com.juni.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juni.app.data.prefs.ProviderId
import com.juni.app.ui.terminal.TermBox
import com.juni.app.ui.terminal.TermButton
import com.juni.app.ui.terminal.TermColor
import com.juni.app.ui.terminal.TermDivider
import com.juni.app.ui.terminal.TermInput
import com.juni.app.ui.terminal.TermText

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsState()
    val settings = ui.settings

    val context = LocalContext.current
    val pickVault = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            vm.setVaultUri(uri.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            TermButton(label = "back", onClick = onBack)
            TermText(text = "settings", color = TermColor.Accent, bold = true)
        }
        TermDivider()

        if (settings == null) {
            TermText(text = "loading…", color = TermColor.Dim)
            return@Column
        }

        TermText(text = "ai provider", color = TermColor.Accent)
        TermBox {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ProviderId.entries.forEach { provider ->
                    val selected = provider == settings.providerId
                    val marker = if (selected) "(•)" else "( )"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TermButton(
                            label = "$marker ${provider.label}",
                            color = if (selected) TermColor.Accent else TermColor.Fg,
                            onClick = { vm.selectProvider(provider) },
                        )
                    }
                }
            }
        }

        ProviderId.entries.forEach { provider ->
            ProviderConfigBox(
                provider = provider,
                model = settings.modelByProvider[provider].orEmpty(),
                apiKey = ui.apiKeyByProvider[provider].orEmpty(),
                ollamaBaseUrl = settings.ollamaBaseUrl,
                onModelChange = { vm.setModel(provider, it) },
                onApiKeyChange = { vm.setApiKey(provider, it) },
                onOllamaUrlChange = { vm.setOllamaBaseUrl(it) },
            )
        }

        Spacer(Modifier.height(8.dp))
        TermText(text = "system prompt", color = TermColor.Accent)
        SystemPromptBox(
            value = settings.systemPrompt,
            onChange = vm::setSystemPrompt,
            onReset = vm::resetSystemPrompt,
        )

        Spacer(Modifier.height(8.dp))
        TermText(text = "obsidian vault", color = TermColor.Accent)
        TermBox(title = "vault folder") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val display = settings.vaultUri
                    ?.let { Uri.decode(it) }
                    ?.substringAfterLast("/document/")
                    ?: "not set"
                TermText(
                    text = "path: $display",
                    color = if (settings.vaultUri == null) TermColor.Muted else TermColor.Fg,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TermButton(label = "pick folder", onClick = { pickVault.launch(null) })
                    if (settings.vaultUri != null) {
                        TermButton(
                            label = "clear",
                            color = TermColor.Red,
                            onClick = { vm.setVaultUri(null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderConfigBox(
    provider: ProviderId,
    model: String,
    apiKey: String,
    ollamaBaseUrl: String,
    onModelChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onOllamaUrlChange: (String) -> Unit,
) {
    var modelDraft by remember(provider, model) { mutableStateOf(model) }
    var apiKeyDraft by remember(provider, apiKey) { mutableStateOf(apiKey) }
    var ollamaDraft by remember(provider, ollamaBaseUrl) { mutableStateOf(ollamaBaseUrl) }
    var revealKey by remember(provider) { mutableStateOf(false) }

    TermBox(title = "${provider.label} config") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TermText(text = "model:", color = TermColor.Dim)
            TermInput(
                value = modelDraft,
                onValueChange = {
                    modelDraft = it
                    onModelChange(it)
                },
                prompt = "  ",
                placeholder = "model name…",
                singleLine = true,
            )

            if (provider == ProviderId.OLLAMA) {
                TermText(text = "base url:", color = TermColor.Dim)
                TermInput(
                    value = ollamaDraft,
                    onValueChange = {
                        ollamaDraft = it
                        onOllamaUrlChange(it)
                    },
                    prompt = "  ",
                    placeholder = "http://host:11434",
                    singleLine = true,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TermText(text = "api key:", color = TermColor.Dim)
                    TermButton(
                        label = if (revealKey) "hide" else "show",
                        onClick = { revealKey = !revealKey },
                    )
                    if (apiKeyDraft.isNotEmpty()) {
                        TermButton(
                            label = "clear",
                            color = TermColor.Red,
                            onClick = {
                                apiKeyDraft = ""
                                onApiKeyChange("")
                            },
                        )
                    }
                }
                TermInput(
                    value = apiKeyDraft,
                    onValueChange = {
                        apiKeyDraft = it
                        onApiKeyChange(it)
                    },
                    prompt = "  ",
                    placeholder = "paste your key…",
                    singleLine = true,
                    visualTransformation = if (revealKey) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation('•')
                    },
                )
            }
        }
    }
}

@Composable
private fun SystemPromptBox(
    value: String,
    onChange: (String) -> Unit,
    onReset: () -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    val pristine = draft == value

    TermBox(title = "agent behavior") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TermText(
                text = "Goes in the system message on every turn. Tune carefully — small changes matter.",
                color = TermColor.Dim,
            )
            TermInput(
                value = draft,
                onValueChange = { draft = it },
                prompt = "  ",
                placeholder = "system prompt…",
                singleLine = false,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TermButton(
                    label = if (pristine) "saved" else "save",
                    color = if (pristine) TermColor.Muted else TermColor.Green,
                    enabled = !pristine,
                    onClick = { onChange(draft) },
                )
                TermButton(
                    label = "reset to default",
                    color = TermColor.Red,
                    onClick = onReset,
                )
            }
        }
    }
}
