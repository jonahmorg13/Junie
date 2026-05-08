package com.juni.app.ui.conversations

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juni.app.R
import com.juni.app.data.db.ConversationEntity
import com.juni.app.ui.terminal.TermButton
import com.juni.app.ui.terminal.TermColor
import com.juni.app.ui.terminal.TermConfirm
import com.juni.app.ui.terminal.TermDivider
import com.juni.app.ui.terminal.TermPromptDialog
import com.juni.app.ui.terminal.TermText
import com.juni.app.ui.theme.LocalPalette
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ConversationsScreen(
    onOpenConversation: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: ConversationsViewModel = viewModel()
    val list by vm.conversations.collectAsState()
    val selectedIds by vm.selectedIds.collectAsState()
    val selectionMode = selectedIds.isNotEmpty()

    var pendingDelete by remember { mutableStateOf<ConversationEntity?>(null) }
    var pendingRename by remember { mutableStateOf<ConversationEntity?>(null) }
    var pendingBulkDelete by remember { mutableStateOf(false) }

    BackHandler(enabled = selectionMode) { vm.clearSelection() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TermText(text = "junie", color = TermColor.Accent, bold = true)
                TermButton(label = "settings", onClick = onOpenSettings)
                TermButton(
                    label = "+ new chat",
                    color = TermColor.Green,
                    onClick = { vm.createNew(onOpenConversation) },
                )
            }
            Spacer(Modifier.height(6.dp))
            TermDivider()
            Spacer(Modifier.height(6.dp))

            if (list.isEmpty()) {
                TermText(
                    text = "no conversations yet. tap + new chat to begin.",
                    color = TermColor.Dim,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    // Reserve room at the bottom so the last row can scroll above
                    // the floating selection panel.
                    contentPadding = PaddingValues(
                        bottom = if (selectionMode) 80.dp else 0.dp,
                    ),
                ) {
                    items(list, key = { it.id }) { conversation ->
                        SwipeToRevealRow(
                            enabled = !selectionMode,
                            onDeleteRequested = { pendingDelete = conversation },
                        ) { isRevealed, closeSwipe ->
                            ConversationRow(
                                conversation = conversation,
                                selectionMode = selectionMode,
                                selected = selectedIds.contains(conversation.id),
                                onTap = {
                                    when {
                                        isRevealed -> closeSwipe()
                                        selectionMode -> vm.toggleSelection(conversation.id)
                                        else -> onOpenConversation(conversation.id)
                                    }
                                },
                                onLongPress = { vm.toggleSelection(conversation.id) },
                                onRename = { pendingRename = conversation },
                            )
                        }
                    }
                }
            }
        }

        if (selectionMode) {
            SelectionFloatingPanel(
                count = selectedIds.size,
                allSelected = selectedIds.size == list.size && list.isNotEmpty(),
                onSelectAll = { vm.selectAll() },
                onDelete = { pendingBulkDelete = true },
                onCancel = { vm.clearSelection() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }

    pendingDelete?.let { target ->
        TermConfirm(
            title = "delete chat",
            message = "delete \"${target.title}\"? this cannot be undone.",
            confirmLabel = "delete",
            onConfirm = {
                vm.delete(target.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
    pendingRename?.let { target ->
        TermPromptDialog(
            title = "rename chat",
            initialValue = target.title,
            placeholder = "new title…",
            onConfirm = { newTitle ->
                if (newTitle.isNotEmpty()) vm.rename(target.id, newTitle)
                pendingRename = null
            },
            onDismiss = { pendingRename = null },
        )
    }
    if (pendingBulkDelete) {
        val n = selectedIds.size
        TermConfirm(
            title = if (n == 1) "delete chat" else "delete $n chats",
            message = if (n == 1) "delete the selected chat? this cannot be undone."
                else "delete $n selected chats? this cannot be undone.",
            confirmLabel = "delete",
            onConfirm = {
                vm.deleteSelected()
                pendingBulkDelete = false
            },
            onDismiss = { pendingBulkDelete = false },
        )
    }
}

/**
 * Floating action panel shown bottom-right while selection mode is active.
 * Wraps content width so it sits as a small toolbar rather than a full bar;
 * surface bg + border match the dialog/toast aesthetic so it pops against
 * the chat list underneath.
 */
@Composable
private fun SelectionFloatingPanel(
    count: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .wrapContentWidth()
            .clip(shape)
            .background(palette.surface)
            .border(width = 1.dp, color = palette.muted, shape = shape)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TermText(
            text = "$count selected",
            color = TermColor.Accent,
            bold = true,
        )
        if (!allSelected) {
            TermButton(label = "select all", onClick = onSelectAll)
        }
        TermButton(label = "delete", color = TermColor.Red, onClick = onDelete)
        TermButton(label = "cancel", onClick = onCancel)
    }
}

/**
 * Wraps a row so it can be swiped left to expose a destructive action.
 *
 * The bordered surface stays put; only the foreground content slides left,
 * uncovering a stationary trash target underneath. The content layer paints
 * its own opaque background so the trash is hidden when at rest.
 */
@Composable
private fun SwipeToRevealRow(
    enabled: Boolean,
    onDeleteRequested: () -> Unit,
    content: @Composable (isRevealed: Boolean, closeSwipe: () -> Unit) -> Unit,
) {
    val revealWidthDp = 72.dp
    val revealWidthPx = with(LocalDensity.current) { revealWidthDp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val palette = LocalPalette.current
    val cornerDp = 8.dp
    val shape = RoundedCornerShape(cornerDp)

    val closeSwipe: () -> Unit = { scope.launch { offset.animateTo(0f) } }
    val isRevealed by remember {
        derivedStateOf { offset.value < -revealWidthPx / 2f }
    }

    LaunchedEffect(enabled) {
        if (!enabled) offset.snapTo(0f)
    }

    // Outer box is the stationary bordered surface. clip(shape) keeps the trash
    // tucked inside the rounded outline; border draws on top of children so the
    // sliding content layer can't paint over it.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(width = 1.dp, color = palette.muted, shape = shape),
    ) {
        // Underlay — the trash, right-aligned, fills row height. Stationary.
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(revealWidthDp)
                    .fillMaxHeight()
                    .background(palette.red)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            onDeleteRequested()
                            scope.launch { offset.animateTo(0f) }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_trash),
                    contentDescription = "delete",
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        // Foreground — the row content, slides via offset, opaque so it hides
        // the trash when at rest.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(palette.bg)
                .draggable(
                    enabled = enabled,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offset.snapTo(
                                (offset.value + delta).coerceIn(-revealWidthPx, 0f),
                            )
                        }
                    },
                    onDragStopped = { velocity ->
                        val current = offset.value
                        val flickOpen = velocity < -800f
                        val flickClose = velocity > 800f
                        val target = when {
                            flickOpen -> -revealWidthPx
                            flickClose -> 0f
                            current < -revealWidthPx / 2f -> -revealWidthPx
                            else -> 0f
                        }
                        offset.animateTo(target)
                    },
                ),
        ) {
            content(isRevealed, closeSwipe)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: ConversationEntity,
    selectionMode: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onRename: () -> Unit,
) {
    val timestamp = remember(conversation.updatedAt) { formatRelative(conversation.updatedAt) }
    val rowClick = remember { MutableInteractionSource() }

    val marker = when {
        selectionMode && selected -> "[x]"
        selectionMode -> "[ ]"
        else -> "▸"
    }
    val titleColor = if (selected) TermColor.Accent else TermColor.Fg

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = rowClick,
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column {
            TermText(text = "$marker ${conversation.title}", color = titleColor)
            TermText(
                text = timestamp,
                color = TermColor.Muted,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
        if (!selectionMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TermButton(label = "rename", onClick = onRename)
            }
        }
    }
}

private fun formatRelative(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val diffSec = (now - epochMs) / 1000
    return when {
        diffSec < 60 -> "just now"
        diffSec < 3600 -> "${diffSec / 60}m ago"
        diffSec < 86400 -> "${diffSec / 3600}h ago"
        diffSec < 86400 * 7 -> "${diffSec / 86400}d ago"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(epochMs))
    }
}
