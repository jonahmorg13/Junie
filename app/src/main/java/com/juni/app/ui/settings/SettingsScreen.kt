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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.juni.app.data.prefs.FontPref
import com.juni.app.data.prefs.ProviderId
import com.juni.app.data.prefs.ThemePref
import com.juni.app.ui.terminal.TermBox
import com.juni.app.ui.terminal.TermButton
import com.juni.app.ui.terminal.TermColor
import com.juni.app.ui.terminal.TermConfirm
import com.juni.app.ui.terminal.TermDivider
import com.juni.app.ui.terminal.TermIconButton
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
            com.juni.app.ui.terminal.Toaster.success(
                "vault set: " + (Uri.decode(uri.toString())?.substringAfterLast("/document/")
                    ?: uri.toString()),
            )
        }
    }

    var pendingResetPrompt by remember { mutableStateOf(false) }
    var pendingClearVault by remember { mutableStateOf(false) }
    var pendingDeleteAll by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(SettingsSection.AI_PROVIDER) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                TermIconButton(glyph = "←", onClick = onBack, color = TermColor.Dim)
                TermText(
                    text = "settings",
                    color = TermColor.Accent,
                    bold = true,
                    style = com.juni.app.ui.theme.TermType.title,
                )
            }
        }
        item(key = "header-divider") { TermDivider() }
        item(key = "section-selector") {
            SectionSelector(current = section, onSelect = { section = it })
        }
        item(key = "selector-divider") { TermDivider() }

        if (settings == null) {
            item(key = "loading") { TermText(text = "loading…", color = TermColor.Dim) }
        } else {
            when (section) {
                SettingsSection.AI_PROVIDER -> {
                    item(key = "ai-active") {
                        ActiveProviderBox(
                            current = settings.providerId,
                            onSelect = vm::selectProvider,
                        )
                    }
                    items(
                        items = ProviderId.entries.toList(),
                        key = { provider -> "ai-config-${provider.key}" },
                    ) { provider ->
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
                }
                SettingsSection.APPEARANCE -> item(key = "appearance") {
                    AppearanceSection(
                        current = settings.theme,
                        onSelect = vm::setTheme,
                    )
                }
                SettingsSection.FONT -> item(key = "font") {
                    FontSection(
                        current = settings.font,
                        onSelect = vm::setFont,
                    )
                }
                SettingsSection.SYSTEM_PROMPT -> item(key = "system-prompt") {
                    SystemPromptSection(
                        value = settings.systemPrompt,
                        vm = vm,
                        onResetClicked = { pendingResetPrompt = true },
                    )
                }
                SettingsSection.VAULT -> item(key = "vault") {
                    VaultSection(
                        vaultUri = settings.vaultUri,
                        onPick = { pickVault.launch(null) },
                        onClearClicked = { pendingClearVault = true },
                    )
                }
                SettingsSection.DATA -> item(key = "data") {
                    DataSection(
                        onDeleteAllClicked = { pendingDeleteAll = true },
                    )
                }
            }
        }
    }

    if (pendingResetPrompt) {
        TermConfirm(
            title = "reset prompt",
            message = "discard your edits and restore the default system prompt?",
            confirmLabel = "reset",
            onConfirm = {
                vm.resetSystemPrompt()
                pendingResetPrompt = false
            },
            onDismiss = { pendingResetPrompt = false },
        )
    }
    if (pendingClearVault) {
        TermConfirm(
            title = "clear vault",
            message = "remove junie's access to your vault folder? you'll need to pick it again.",
            confirmLabel = "clear",
            onConfirm = {
                vm.setVaultUri(null)
                pendingClearVault = false
            },
            onDismiss = { pendingClearVault = false },
        )
    }
    if (pendingDeleteAll) {
        TermConfirm(
            title = "delete all chats",
            message = "permanently delete every conversation? this cannot be undone.",
            confirmLabel = "delete all",
            onConfirm = {
                vm.deleteAllConversations()
                pendingDeleteAll = false
            },
            onDismiss = { pendingDeleteAll = false },
        )
    }
}

private enum class SettingsSection(val label: String) {
    AI_PROVIDER("ai provider"),
    APPEARANCE("appearance"),
    FONT("font"),
    SYSTEM_PROMPT("system prompt"),
    VAULT("vault"),
    DATA("data"),
}

@Composable
private fun SectionSelector(
    current: SettingsSection,
    onSelect: (SettingsSection) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsSection.entries.forEach { sec ->
            val selected = sec == current
            TermButton(
                label = (if (selected) "▸ " else "  ") + sec.label,
                color = if (selected) TermColor.Accent else TermColor.Fg,
                onClick = { onSelect(sec) },
            )
        }
    }
}

@Composable
private fun ActiveProviderBox(
    current: ProviderId,
    onSelect: (ProviderId) -> Unit,
) {
    TermBox(title = "active provider") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ProviderId.entries.forEach { provider ->
                val selected = provider == current
                TermButton(
                    label = provider.label,
                    color = if (selected) TermColor.Accent else TermColor.Fg,
                    onClick = { onSelect(provider) },
                )
            }
        }
    }
}

@Composable
private fun AppearanceSection(
    current: ThemePref,
    onSelect: (ThemePref) -> Unit,
) {
    TermBox(title = "theme") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TermText(
                text = "controls colors across the whole app. takes effect immediately.",
                color = TermColor.Dim,
            )
            Spacer(Modifier.height(4.dp))
            ThemePref.entries.forEach { pref ->
                val selected = pref == current
                TermButton(
                    label = pref.label,
                    color = if (selected) TermColor.Accent else TermColor.Fg,
                    onClick = { onSelect(pref) },
                )
            }
        }
    }
}

/**
 * Renders each font's label in the font itself, so the picker doubles as a
 * preview. The currently active font's button is filled with the accent color.
 */
@Composable
private fun FontSection(
    current: FontPref,
    onSelect: (FontPref) -> Unit,
) {
    TermBox(title = "font") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TermText(
                text = "applies app-wide. each option is rendered in its own face so the picker doubles as a preview.",
                color = TermColor.Dim,
            )
            Spacer(Modifier.height(4.dp))
            FontPref.entries.forEach { pref ->
                val selected = pref == current
                val sampleFont = com.juni.app.ui.theme.resolveAppFont(pref)
                TermButton(
                    label = pref.label,
                    color = if (selected) TermColor.Accent else TermColor.Fg,
                    onClick = { onSelect(pref) },
                    // Override the label's font so the user sees the actual glyphs.
                    labelFontFamily = sampleFont,
                )
            }
        }
    }
}

@Composable
private fun SystemPromptSection(
    value: String,
    vm: SettingsViewModel,
    onResetClicked: () -> Unit,
) {
    SystemPromptBox(
        value = value,
        onChange = vm::setSystemPrompt,
        onReset = onResetClicked,
    )
}

@Composable
private fun VaultSection(
    vaultUri: String?,
    onPick: () -> Unit,
    onClearClicked: () -> Unit,
) {
    TermBox(title = "vault folder") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val display = vaultUri
                ?.let { Uri.decode(it) }
                ?.substringAfterLast("/document/")
                ?: "not set"
            TermText(
                text = "path: $display",
                color = if (vaultUri == null) TermColor.Muted else TermColor.Fg,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TermButton(label = "pick folder", onClick = onPick)
                if (vaultUri != null) {
                    TermButton(
                        label = "clear",
                        color = TermColor.Red,
                        onClick = onClearClicked,
                    )
                }
            }
        }
    }
}

@Composable
private fun DataSection(
    onDeleteAllClicked: () -> Unit,
) {
    TermBox(title = "conversations") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TermText(
                text = "wipe all saved chats and their messages from this device. " +
                    "the vault contents junie already wrote are not affected.",
                color = TermColor.Dim,
            )
            TermButton(
                label = "delete all chats",
                color = TermColor.Red,
                onClick = onDeleteAllClicked,
            )
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
    // Key only on `provider` — keying on the persisted value too would cause
    // the local draft state to be discarded and reallocated on every keystroke
    // (DataStore re-emits → parent recomposes → new `model` arg → re-key →
    // new MutableState), which makes typing visibly chuggy.
    var modelDraft by remember(provider) { mutableStateOf(model) }
    var apiKeyDraft by remember(provider) { mutableStateOf(apiKey) }
    var ollamaDraft by remember(provider) { mutableStateOf(ollamaBaseUrl) }
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
