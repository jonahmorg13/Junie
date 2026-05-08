package com.juni.app.ui.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

private val SPINNER_FRAMES = charArrayOf(
    '⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏',
)

/**
 * Braille-spinner + gerund label, à la Claude Code. The label is whatever the
 * caller passes in; pair this with [randomThinkingWord] for the funny-words flavor.
 */
@Composable
fun TermSpinner(
    label: String,
    modifier: Modifier = Modifier,
    color: TermColor = TermColor.Accent,
    frameMillis: Long = 80,
) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(frameMillis)
            frame = (frame + 1) % SPINNER_FRAMES.size
        }
    }
    TermText(
        text = "${SPINNER_FRAMES[frame]} $label…",
        color = color,
        modifier = modifier,
    )
}

private val THINKING_WORDS = listOf(
    "thinking", "ruminating", "pondering", "cogitating", "deliberating",
    "musing", "contemplating", "computing", "brewing", "marinating",
    "percolating", "synthesizing", "untangling", "conjuring", "vibing",
    "calibrating", "ideating", "philosophizing", "concocting", "scheming",
    "spelunking", "noodling", "stewing", "incubating", "scrutinizing",
    "channeling", "divining", "interpolating", "wrangling", "untwisting",
    "deciphering", "unfurling", "kneading", "untangling-the-yarn",
    "consulting-the-oracle", "rolling-the-dice", "asking-the-clouds",
)

fun randomThinkingWord(): String = THINKING_WORDS.random()
