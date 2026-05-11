package com.juni.app.ui.chat

import androidx.compose.foundation.Image
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juni.app.JuniApp
import com.juni.app.data.image.resizeAndRotate
import com.juni.app.ui.terminal.Toaster
import com.juni.app.domain.agent.ChatIntent
import com.juni.app.ui.terminal.TermBox
import com.juni.app.ui.terminal.TermButton
import com.juni.app.ui.terminal.TermColor
import com.juni.app.ui.terminal.TermDivider
import com.juni.app.ui.terminal.TermInput
import com.juni.app.ui.terminal.TermMenuItem
import com.juni.app.ui.terminal.TermMenuSheet
import com.juni.app.ui.terminal.TermPromptDialog
import com.juni.app.ui.terminal.TermSpinner
import com.juni.app.ui.terminal.TermText
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val vm: ChatViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    val pendingImages by vm.pendingImages.collectAsState()
    var draft by remember { mutableStateOf("") }
    var renameDialogOpen by remember { mutableStateOf(false) }
    var intentMenuOpen by remember { mutableStateOf(false) }
    var attachMenuOpen by remember { mutableStateOf(false) }
    val titleClickSource = remember { MutableInteractionSource() }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                    .mapCatching { raw -> raw?.resizeAndRotate() }
                    .onSuccess { processed ->
                        if (processed != null) {
                            JuniApp.get().addComposerImage(processed)
                            Toaster.success("image attached")
                        } else {
                            Toaster.error("couldn't read image")
                        }
                    }
                    .onFailure {
                        Log.e("juni-chat", "image attach failed", it)
                        Toaster.error("couldn't load image")
                    }
            }
        }
    }
    val obsidianHandler: ((String) -> Unit)? = ui.vaultUri?.let { vaultUri ->
        { path ->
            openInObsidian(context, vaultUri, path)
        }
    }

    // Scroll on substantive changes (item count, streaming start/stop, image attach).
    // Notably NOT keyed on `ui.streaming.length` — that fired per-token and pinned the
    // main thread to scrolling instead of letting the user actually use the screen.
    LaunchedEffect(ui.items.size, ui.isStreaming, pendingImages.size) {
        val target = listState.layoutInfo.totalItemsCount - 1
        if (target >= 0) listState.animateScrollToItem(target)
    }
    // While streaming, follow the growing text — debounced so we coalesce dozens of
    // token deltas into a single scroll per ~150ms quiet period.
    LaunchedEffect(Unit) {
        snapshotFlow { ui.streaming.length }
            .debounce(150)
            .collect {
                if (ui.isStreaming) {
                    val target = listState.layoutInfo.totalItemsCount - 1
                    if (target >= 0) listState.scrollToItem(target)
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TermButton(label = "back", onClick = onBack)
            TermText(
                text = ui.title.ifEmpty { "junie" },
                color = TermColor.Accent,
                bold = true,
                style = com.juni.app.ui.theme.TermType.title,
                modifier = Modifier.clickable(
                    interactionSource = titleClickSource,
                    indication = null,
                    onClick = { renameDialogOpen = true },
                ),
            )
        }
        TermText(text = ui.statusLine, color = TermColor.Dim)
        TermDivider()

        val vaultIsSet = ui.vaultUri != null
        if (!vaultIsSet) {
            Spacer(Modifier.height(6.dp))
            VaultRequiredBanner(onOpenSettings = onOpenSettings)
        }

        val awaitingApproval = ui.items.any {
            it is ChatItem.ToolCall && it.state is ToolState.Pending
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(ui.items) { item ->
                when (item) {
                    is ChatItem.UserMessage -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (item.imageCount > 0) {
                            TermText(
                                text = "[${item.imageCount} ${if (item.imageCount == 1) "image" else "images"}]",
                                color = TermColor.Muted,
                            )
                        }
                        if (item.text.isNotEmpty()) {
                            TermText(text = "❯ ${item.text}", color = TermColor.Accent)
                        }
                    }
                    is ChatItem.AssistantText ->
                        MarkdownText(text = item.text)
                    is ChatItem.ToolCall ->
                        ToolCallCard(
                            item = item,
                            onApprove = { vm.approve(it) },
                            onReject = { vm.reject(it) },
                            onOpenInObsidian = obsidianHandler,
                        )
                    is ChatItem.SystemError ->
                        TermBox(title = "error") { TermText(text = item.text, color = TermColor.Red) }
                }
            }
            if (ui.streaming.isNotEmpty()) {
                item(key = "streaming") {
                    MarkdownText(text = ui.streaming)
                }
            }
            // Spinner stays up until the agent finishes or asks for approval.
            if (ui.isStreaming && !awaitingApproval) {
                item(key = "spinner") {
                    TermSpinner(label = ui.thinkingWord)
                }
            }
        }

        TermDivider()

        if (pendingImages.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            PendingImageStrip(
                images = pendingImages,
                onRemove = { vm.removePendingImage(it) },
            )
        }

        ui.pendingIntent?.let { intent ->
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TermText(
                    text = "▸ ${intent.label}: ${intent.short}",
                    color = TermColor.Accent,
                )
                TermButton(label = "x", color = TermColor.Red, onClick = { vm.clearIntent() })
            }
        }

        Spacer(Modifier.height(6.dp))
        val composerEnabled = !ui.isStreaming && vaultIsSet
        Row(verticalAlignment = Alignment.CenterVertically) {
            TermButton(label = "+", onClick = { intentMenuOpen = true }, enabled = composerEnabled)
            TermInput(
                modifier = Modifier.weight(1f),
                value = draft,
                onValueChange = { draft = it },
                placeholder = when {
                    !vaultIsSet -> "set a vault to chat…"
                    ui.isStreaming -> "streaming…"
                    else -> "ask junie…"
                },
                singleLine = false,
                imeAction = ImeAction.Send,
                showBorder = false,
                onSubmit = {
                    val t = draft
                    if ((t.isNotBlank() || pendingImages.isNotEmpty()) && composerEnabled) {
                        draft = ""
                        vm.send(t)
                    }
                },
            )
            TermButton(
                label = "attach",
                onClick = { attachMenuOpen = true },
                enabled = composerEnabled,
            )
            Spacer(Modifier.width(8.dp))
            if (ui.isStreaming) {
                TermButton(label = "stop", color = TermColor.Red, onClick = { vm.stop() })
            } else {
                TermButton(
                    label = "send",
                    color = if (vaultIsSet) TermColor.Green else TermColor.Muted,
                    enabled = vaultIsSet,
                    onClick = {
                        val t = draft
                        if (t.isNotBlank() || pendingImages.isNotEmpty()) {
                            draft = ""
                            vm.send(t)
                        }
                    },
                )
            }
        }
    }

    if (renameDialogOpen) {
        TermPromptDialog(
            title = "rename chat",
            initialValue = ui.title,
            placeholder = "new title…",
            onConfirm = { newTitle ->
                if (newTitle.isNotEmpty()) vm.rename(newTitle)
                renameDialogOpen = false
            },
            onDismiss = { renameDialogOpen = false },
        )
    }
    if (intentMenuOpen) {
        TermMenuSheet(
            title = "send as…",
            items = ChatIntent.entries.map {
                TermMenuItem(key = it.name, label = it.label, description = it.short)
            },
            onPick = { key ->
                vm.setIntent(ChatIntent.valueOf(key))
                intentMenuOpen = false
            },
            onDismiss = { intentMenuOpen = false },
        )
    }
    if (attachMenuOpen) {
        TermMenuSheet(
            title = "attach…",
            items = listOf(
                TermMenuItem(
                    key = "camera",
                    label = "use camera",
                    description = "take a photo with the device camera",
                ),
                TermMenuItem(
                    key = "file",
                    label = "upload a file",
                    description = "attach an image from your phone",
                ),
            ),
            onPick = { key ->
                attachMenuOpen = false
                when (key) {
                    "camera" -> onOpenCamera()
                    "file" -> pickFileLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
            },
            onDismiss = { attachMenuOpen = false },
        )
    }
}

/**
 * Build an `obsidian://open?vault=…&file=…` URI from the SAF tree URI and a
 * vault-relative path, then launch it. Obsidian must be installed and the
 * folder must already be registered as a vault inside Obsidian (which it
 * usually is, by folder name).
 */
private fun openInObsidian(
    context: android.content.Context,
    vaultUri: String,
    relativePath: String,
) {
    val vaultName = extractVaultName(vaultUri)
    if (vaultName.isNullOrEmpty()) {
        Toaster.error("Couldn't determine vault name")
        return
    }
    val withoutExt = if (relativePath.endsWith(".md")) relativePath.dropLast(3) else relativePath
    val url = "obsidian://open?vault=" + Uri.encode(vaultName) +
        "&file=" + Uri.encode(withoutExt)
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toaster.error("Obsidian not installed")
    }
}

private fun extractVaultName(treeUri: String): String? {
    val decoded = Uri.decode(treeUri) ?: return null
    return decoded.substringAfterLast('/').substringAfterLast(':').takeIf { it.isNotEmpty() }
}

/**
 * Banner shown inside the chat when this conversation has no vault attached.
 * The composer is disabled in that state because the agent has no tools to
 * call — Claude would otherwise narrate fake tool calls as text.
 */
@Composable
private fun VaultRequiredBanner(onOpenSettings: () -> Unit) {
    val palette = com.juni.app.ui.theme.LocalPalette.current
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface)
            .border(width = 1.dp, color = palette.red, shape = shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TermText(text = "no vault set", color = TermColor.Red, bold = true)
        TermText(
            text = "junie can't run tools without a vault folder. open settings to pick one.",
            color = TermColor.Dim,
        )
        TermButton(
            label = "open settings",
            color = TermColor.Accent,
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun PendingImageStrip(
    images: List<ByteArray>,
    onRemove: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.forEachIndexed { index, bytes ->
            // Decode off the main thread — a 1568px JPEG decode is 30-80ms inline,
            // visible as a hitch when adding/removing attachments.
            var bitmap by remember(bytes) {
                mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
            }
            LaunchedEffect(bytes) {
                bitmap = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }
            Box {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                    )
                } else {
                    TermText(text = "[img]", color = TermColor.Muted)
                }
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    TermButton(
                        label = "x",
                        color = TermColor.Red,
                        onClick = { onRemove(index) },
                    )
                }
            }
        }
    }
}
