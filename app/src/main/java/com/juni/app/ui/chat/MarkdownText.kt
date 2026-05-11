package com.juni.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juni.app.ui.terminal.TermDivider
import com.juni.app.ui.theme.LocalPalette
import com.juni.app.ui.theme.Palette
import com.juni.app.ui.theme.TermFont
import com.juni.app.ui.theme.TermType

// Renders assistant markdown using terminal primitives only — JetBrains Mono,
// palette-driven colors, no Material. Permissive parser so streaming text
// renders cleanly while tokens are still arriving.
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { parseBlocks(text) }
    val palette = LocalPalette.current
    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(6.dp))
            when (block) {
                is MdBlock.Heading -> HeadingBlock(block, palette)
                is MdBlock.Paragraph -> ParagraphBlock(block.text, palette)
                is MdBlock.CodeBlock -> CodeBlock(block, palette)
                is MdBlock.Quote -> QuoteBlock(block.text, palette)
                is MdBlock.UnorderedList -> UnorderedListBlock(block.items, palette)
                is MdBlock.OrderedList -> OrderedListBlock(block.items, palette)
                MdBlock.Rule -> TermDivider()
            }
        }
    }
}

@Composable
private fun HeadingBlock(block: MdBlock.Heading, palette: Palette) {
    val size = when (block.level) {
        1 -> 18.sp
        2 -> 16.sp
        else -> 14.sp
    }
    val color = if (block.level <= 2) palette.accent else palette.fg
    val style = TermType.body.copy(
        fontWeight = FontWeight.Bold,
        fontSize = size,
        color = color,
    )
    BasicText(
        text = buildInline(block.text, palette, color),
        style = style,
    )
}

@Composable
private fun ParagraphBlock(text: String, palette: Palette) {
    BasicText(
        text = buildInline(text, palette, palette.fg),
        style = TermType.body,
    )
}

@Composable
private fun CodeBlock(block: MdBlock.CodeBlock, palette: Palette) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(palette.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        // Horizontal scroll so long lines aren't wrapped (code reads better unwrapped).
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            BasicText(
                text = block.code,
                style = TermType.small.copy(color = palette.fg),
            )
        }
    }
}

@Composable
private fun QuoteBlock(text: String, palette: Palette) {
    Row {
        Box(
            modifier = Modifier
                .width(3.dp)
                .background(palette.muted),
        ) { Spacer(Modifier.height(1.dp)) }
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = buildInline(text, palette, palette.dim),
            style = TermType.body.copy(
                color = palette.dim,
                fontStyle = FontStyle.Italic,
            ),
        )
    }
}

@Composable
private fun UnorderedListBlock(items: List<String>, palette: Palette) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { item ->
            Row {
                BasicText(
                    text = "• ",
                    style = TermType.body.copy(color = palette.accent),
                )
                BasicText(
                    text = buildInline(item, palette, palette.fg),
                    style = TermType.body,
                )
            }
        }
    }
}

@Composable
private fun OrderedListBlock(items: List<String>, palette: Palette) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { index, item ->
            Row {
                BasicText(
                    text = "${index + 1}. ",
                    style = TermType.body.copy(color = palette.accent),
                )
                BasicText(
                    text = buildInline(item, palette, palette.fg),
                    style = TermType.body,
                )
            }
        }
    }
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class CodeBlock(val lang: String?, val code: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class UnorderedList(val items: List<String>) : MdBlock
    data class OrderedList(val items: List<String>) : MdBlock
    data object Rule : MdBlock
}

private val HeadingRegex = Regex("""^\s{0,3}(#{1,6})\s+(.*)$""")
private val RuleRegex = Regex("""^\s{0,3}([-*_])\s*\1\s*\1[-*_\s]*$""")
private val UnorderedItemRegex = Regex("""^\s{0,3}[-*+]\s+(.*)$""")
private val OrderedItemRegex = Regex("""^\s{0,3}\d+\.\s+(.*)$""")
private val FenceRegex = Regex("""^\s{0,3}```(.*)$""")

private fun parseBlocks(text: String): List<MdBlock> {
    val lines = text.split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        // Fenced code block — consume until closing fence (or EOF, for streaming).
        val fence = FenceRegex.matchEntire(line)
        if (fence != null) {
            val lang = fence.groupValues[1].trim().ifEmpty { null }
            val code = mutableListOf<String>()
            i++
            while (i < lines.size && FenceRegex.matchEntire(lines[i]) == null) {
                code.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // skip closing fence
            blocks.add(MdBlock.CodeBlock(lang, code.joinToString("\n")))
            continue
        }

        // Horizontal rule
        if (RuleRegex.matchEntire(line) != null) {
            blocks.add(MdBlock.Rule)
            i++
            continue
        }

        // Heading
        val heading = HeadingRegex.matchEntire(line)
        if (heading != null) {
            val level = heading.groupValues[1].length
            val content = heading.groupValues[2].trimEnd(' ', '#').trimEnd()
            blocks.add(MdBlock.Heading(level, content))
            i++
            continue
        }

        // Blockquote — consume contiguous '>'-prefixed lines.
        if (line.trimStart().startsWith(">")) {
            val buf = mutableListOf<String>()
            while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                buf.add(lines[i].trimStart().removePrefix(">").trimStart())
                i++
            }
            blocks.add(MdBlock.Quote(buf.joinToString("\n")))
            continue
        }

        // Unordered list
        if (UnorderedItemRegex.matchEntire(line) != null) {
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val m = UnorderedItemRegex.matchEntire(lines[i]) ?: break
                items.add(m.groupValues[1])
                i++
            }
            blocks.add(MdBlock.UnorderedList(items))
            continue
        }

        // Ordered list
        if (OrderedItemRegex.matchEntire(line) != null) {
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val m = OrderedItemRegex.matchEntire(lines[i]) ?: break
                items.add(m.groupValues[1])
                i++
            }
            blocks.add(MdBlock.OrderedList(items))
            continue
        }

        // Blank line
        if (line.isBlank()) {
            i++
            continue
        }

        // Paragraph — gather consecutive non-blank lines that aren't another block.
        val buf = mutableListOf<String>()
        while (i < lines.size && !lines[i].isBlank() && !isBlockStart(lines[i])) {
            buf.add(lines[i])
            i++
        }
        blocks.add(MdBlock.Paragraph(buf.joinToString("\n")))
    }
    return blocks
}

private fun isBlockStart(line: String): Boolean =
    FenceRegex.matchEntire(line) != null ||
        HeadingRegex.matchEntire(line) != null ||
        RuleRegex.matchEntire(line) != null ||
        UnorderedItemRegex.matchEntire(line) != null ||
        OrderedItemRegex.matchEntire(line) != null ||
        line.trimStart().startsWith(">")

/**
 * Inline formatter. Single forward pass — when a delimiter doesn't close, the
 * opening character is emitted as literal text so streaming partials look fine.
 */
private fun buildInline(
    text: String,
    palette: Palette,
    baseColor: Color,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val c = text[i]

        // Bold **...**
        if (c == '*' && i + 1 < text.length && text[i + 1] == '*') {
            val close = text.indexOf("**", i + 2)
            if (close != -1) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(buildInline(text.substring(i + 2, close), palette, baseColor))
                pop()
                i = close + 2
                continue
            }
        }

        // Italic *...* or _..._  — require the inner first char to differ from the marker.
        if ((c == '*' || c == '_') && i + 1 < text.length && text[i + 1] != c) {
            val close = text.indexOf(c, i + 1)
            if (close != -1 && close > i + 1) {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(buildInline(text.substring(i + 1, close), palette, baseColor))
                pop()
                i = close + 1
                continue
            }
        }

        // Inline code `...`
        if (c == '`') {
            val close = text.indexOf('`', i + 1)
            if (close != -1) {
                pushStyle(
                    SpanStyle(
                        fontFamily = TermFont,
                        color = palette.accent,
                        background = palette.surface,
                    ),
                )
                append(text.substring(i + 1, close))
                pop()
                i = close + 1
                continue
            }
        }

        // Link [label](url)
        if (c == '[') {
            val rb = text.indexOf(']', i + 1)
            if (rb != -1 && rb + 1 < text.length && text[rb + 1] == '(') {
                val rp = text.indexOf(')', rb + 2)
                if (rp != -1) {
                    val label = text.substring(i + 1, rb)
                    val url = text.substring(rb + 2, rp)
                    pushStringAnnotation(tag = "URL", annotation = url)
                    pushStyle(
                        SpanStyle(
                            color = palette.accent,
                            textDecoration = TextDecoration.Underline,
                        ),
                    )
                    append(label)
                    pop()
                    pop()
                    i = rp + 1
                    continue
                }
            }
        }

        append(c)
        i++
    }
}
