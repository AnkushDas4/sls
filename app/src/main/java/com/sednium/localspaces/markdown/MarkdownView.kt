package com.sednium.localspaces.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sednium.localspaces.code.CodeBlockView
import com.sednium.localspaces.ui.theme.SedRedAlpha
import com.sednium.localspaces.ui.theme.SedniumColors

/**
 * Top-level entry point — drop-in replacement for the old MarkdownText:
 * same (content, isDark) signature, real AST underneath.
 */
@Composable
fun MarkdownView(content: String, isDark: Boolean, modifier: Modifier = Modifier) {
    val blocks = remember(content) { parseMarkdown(content) }
    Column(modifier = modifier) {
        blocks.forEach { RenderBlock(it, isDark) }
    }
}

@Composable
private fun RenderBlock(block: Block, isDark: Boolean) {
    val textColor = if (isDark) SedniumColors.Gray200 else SedniumColors.SedRed
    val codeBg = if (isDark) SedniumColors.Gray800 else SedniumColors.Gray100
    val codeFg = if (isDark) SedniumColors.Gray200 else SedniumColors.Gray800
    val linkColor = if (isDark) SedniumColors.Blue400 else SedniumColors.Blue500

    when (block) {
        is Block.Heading -> {
            val (size, weight) = when (block.level) {
                1 -> 22.sp to FontWeight.Bold
                2 -> 19.sp to FontWeight.Bold
                3 -> 17.sp to FontWeight.Bold
                else -> 15.sp to FontWeight.Bold
            }
            Text(
                text = inlineToAnnotatedString(block.inline, textColor, codeBg, codeFg, linkColor),
                fontSize = size,
                fontWeight = weight,
                color = textColor,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
        }

        is Block.Paragraph -> {
            Text(
                text = inlineToAnnotatedString(block.inline, textColor, codeBg, codeFg, linkColor),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        is Block.CodeBlock -> {
            CodeBlockView(
                language = block.language,
                code = block.code,
                isDark = isDark,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        is Block.BlockQuote -> {
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .background(if (isDark) SedniumColors.Gray700 else SedRedAlpha.a30)
                )
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    block.children.forEach { RenderBlock(it, isDark) }
                }
            }
        }

        is Block.ListBlock -> {
            Column(modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
                block.items.forEachIndexed { idx, item ->
                    Row(modifier = Modifier.padding(vertical = 1.dp)) {
                        Text(
                            text = if (block.ordered) "${block.startNumber + idx}." else "•",
                            color = textColor.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp).width(if (block.ordered) 22.dp else 14.dp)
                        )
                        Column {
                            item.children.forEach { RenderBlock(it, isDark) }
                        }
                    }
                }
            }
        }

        is Block.ThematicBreak -> {
            HorizontalDivider(
                color = if (isDark) SedniumColors.Gray700 else SedRedAlpha.a20,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }

        is Block.Table -> RenderTable(block, isDark, textColor, codeBg, codeFg, linkColor)
    }
}

@Composable
private fun RenderTable(
    table: Block.Table,
    isDark: Boolean,
    textColor: Color,
    codeBg: Color,
    codeFg: Color,
    linkColor: Color
) {
    val borderColor = if (isDark) SedniumColors.Gray700 else SedRedAlpha.a20
    Column(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .background(if (isDark) SedniumColors.Gray800.copy(alpha = 0.3f) else SedRedAlpha.a05)
    ) {
        TableRow(table.header, borderColor, textColor, codeBg, codeFg, linkColor, isHeader = true)
        HorizontalDivider(color = borderColor)
        table.rows.forEachIndexed { idx, row ->
            TableRow(row, borderColor, textColor, codeBg, codeFg, linkColor, isHeader = false)
            if (idx != table.rows.lastIndex) HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun TableRow(
    cells: List<List<Inline>>,
    borderColor: Color,
    textColor: Color,
    codeBg: Color,
    codeFg: Color,
    linkColor: Color,
    isHeader: Boolean
) {
    Row {
        cells.forEach { cell ->
            Text(
                text = inlineToAnnotatedString(cell, textColor, codeBg, codeFg, linkColor),
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(8.dp)
            )
        }
    }
}

/**
 * Recursively renders an inline-node tree into one AnnotatedString.
 * Links use Compose's real LinkAnnotation so they're actually tappable
 * (opens via whatever UriHandler is in scope) rather than just colored
 * text.
 */
private fun inlineToAnnotatedString(
    nodes: List<Inline>,
    baseColor: Color,
    codeBg: Color,
    codeFg: Color,
    linkColor: Color
): AnnotatedString = buildAnnotatedString {
    appendInline(nodes, baseColor, codeBg, codeFg, linkColor)
}

private fun AnnotatedString.Builder.appendInline(
    nodes: List<Inline>,
    baseColor: Color,
    codeBg: Color,
    codeFg: Color,
    linkColor: Color
) {
    nodes.forEach { node ->
        when (node) {
            is Inline.Text -> withStyle(SpanStyle(color = baseColor)) { append(node.text) }

            is Inline.Bold -> withStyle(SpanStyle(color = baseColor, fontWeight = FontWeight.Bold)) {
                appendInline(node.children, baseColor, codeBg, codeFg, linkColor)
            }

            is Inline.Italic -> withStyle(SpanStyle(color = baseColor, fontStyle = FontStyle.Italic)) {
                appendInline(node.children, baseColor, codeBg, codeFg, linkColor)
            }

            is Inline.Strikethrough -> withStyle(SpanStyle(color = baseColor, textDecoration = TextDecoration.LineThrough)) {
                appendInline(node.children, baseColor, codeBg, codeFg, linkColor)
            }

            is Inline.Code -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = codeFg, fontSize = 13.sp)
            ) { append(' ' + node.text + ' ') }

            is Inline.Link -> withLink(
                LinkAnnotation.Url(
                    node.url,
                    TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                )
            ) {
                appendInline(node.children, linkColor, codeBg, codeFg, linkColor)
            }

            is Inline.LineBreak -> append(' ') // soft break: render as a space, matching default Markdown semantics
        }
    }
}
