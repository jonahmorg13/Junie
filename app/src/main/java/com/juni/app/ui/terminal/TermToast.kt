package com.juni.app.ui.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juni.app.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest

enum class ToastKind { Info, Success, Error }

data class ToastMessage(val text: String, val kind: ToastKind = ToastKind.Info)

/**
 * Process-wide toast bus. Anywhere in the app can call [show] to surface a
 * transient bottom-of-screen notification; whichever [ToastHost] is currently
 * composed will pick it up and render it.
 */
object Toaster {
    private val _flow = MutableSharedFlow<ToastMessage>(extraBufferCapacity = 8)
    val flow: SharedFlow<ToastMessage> = _flow.asSharedFlow()

    fun show(text: String, kind: ToastKind = ToastKind.Info) {
        _flow.tryEmit(ToastMessage(text, kind))
    }

    fun success(text: String) = show(text, ToastKind.Success)
    fun error(text: String) = show(text, ToastKind.Error)
}

/**
 * Wraps the navigation graph and overlays toast notifications at the bottom.
 * A new toast immediately replaces any showing one (collectLatest), then auto-dismisses
 * after [durationMillis]. Tap-to-dismiss too.
 */
@Composable
fun ToastHost(
    durationMillis: Long = 2500,
    content: @Composable () -> Unit,
) {
    var current by remember { mutableStateOf<ToastMessage?>(null) }

    LaunchedEffect(Unit) {
        Toaster.flow.collectLatest { msg ->
            current = msg
            delay(durationMillis)
            current = null
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        current?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .widthIn(max = 280.dp)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { current = null },
                    ),
            ) {
                TermBox(background = LocalPalette.current.surface) {
                    TermText(
                        text = msg.text,
                        color = when (msg.kind) {
                            ToastKind.Info -> TermColor.Fg
                            ToastKind.Success -> TermColor.Green
                            ToastKind.Error -> TermColor.Red
                        },
                    )
                }
            }
        }
    }
}
