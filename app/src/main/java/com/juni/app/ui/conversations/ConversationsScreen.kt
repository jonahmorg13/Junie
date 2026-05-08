package com.juni.app.ui.conversations

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juni.app.data.db.ConversationEntity
import com.juni.app.ui.terminal.TermBox
import com.juni.app.ui.terminal.TermButton
import com.juni.app.ui.terminal.TermColor
import com.juni.app.ui.terminal.TermConfirm
import com.juni.app.ui.terminal.TermDivider
import com.juni.app.ui.terminal.TermPromptDialog
import com.juni.app.ui.terminal.TermText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationsScreen(
    onOpenConversation: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: ConversationsViewModel = viewModel()
    val list by vm.conversations.collectAsState()
    var pendingDelete by remember { mutableStateOf<ConversationEntity?>(null) }
    var pendingRename by remember { mutableStateOf<ConversationEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TermText(text = "juni", color = TermColor.Accent, bold = true)
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
            ) {
                items(list, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        providerLabel = vm.providerLabel(conversation),
                        onTap = { onOpenConversation(conversation.id) },
                        onRename = { pendingRename = conversation },
                        onDelete = { pendingDelete = conversation },
                    )
                }
            }
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
}

@Composable
private fun ConversationRow(
    conversation: ConversationEntity,
    providerLabel: String,
    onTap: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val timestamp = remember(conversation.updatedAt) { formatRelative(conversation.updatedAt) }
    TermBox {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column {
                TermText(text = "▸ ${conversation.title}", color = TermColor.Fg)
                TermText(
                    text = "$timestamp · $providerLabel · ${conversation.modelId}",
                    color = TermColor.Muted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TermButton(label = "open", color = TermColor.Accent, onClick = onTap)
                TermButton(label = "rename", onClick = onRename)
                TermButton(label = "delete", color = TermColor.Red, onClick = onDelete)
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

