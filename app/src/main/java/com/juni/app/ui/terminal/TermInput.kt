package com.juni.app.ui.terminal

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.juni.app.ui.theme.TermAccent
import com.juni.app.ui.theme.TermMuted
import com.juni.app.ui.theme.TermType

/**
 * Single-line or multiline text input wrapped in a thin rounded border so each
 * field is visually a small box. Pass an explicit [showBorder]=false for
 * places where the surrounding container already provides one.
 */
@Composable
fun TermInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    prompt: String = "> ",
    placeholder: String? = null,
    singleLine: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    showBorder: Boolean = true,
    onSubmit: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(6.dp)
    val borderModifier = if (showBorder) {
        Modifier.border(width = 1.dp, color = TermMuted, shape = shape)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(borderModifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        TermText(text = prompt, color = TermColor.Accent)
        Box(modifier = Modifier.fillMaxWidth()) {
            if (value.isEmpty() && placeholder != null) {
                TermText(text = placeholder, color = TermColor.Muted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TermType.body,
                cursorBrush = SolidColor(TermAccent),
                singleLine = singleLine,
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onSend = { onSubmit?.invoke() },
                    onDone = { onSubmit?.invoke() },
                    onGo = { onSubmit?.invoke() },
                ),
            )
        }
    }
}
