package com.juni.app.ui.terminal

import androidx.compose.foundation.layout.Row
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
 *
 * Split into two TermTexts so the per-tick state read is isolated to the small
 * one-character glyph: only [SpinnerGlyph] recomposes every 80ms, the label
 * stays stable, and the parent doesn't invalidate the surrounding LazyColumn item.
 */
@Composable
fun TermSpinner(
    label: String,
    modifier: Modifier = Modifier,
    color: TermColor = TermColor.Accent,
    frameMillis: Long = 80,
) {
    Row(modifier = modifier) {
        SpinnerGlyph(color = color, frameMillis = frameMillis)
        TermText(text = " $label…", color = color)
    }
}

@Composable
private fun SpinnerGlyph(color: TermColor, frameMillis: Long) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(frameMillis) {
        while (true) {
            delay(frameMillis)
            frame = (frame + 1) % SPINNER_FRAMES.size
        }
    }
    TermText(text = SPINNER_FRAMES[frame].toString(), color = color)
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
