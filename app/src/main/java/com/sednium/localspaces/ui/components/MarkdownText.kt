package com.sednium.localspaces.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sednium.localspaces.ui.theme.OrangeAlpha
import com.sednium.localspaces.ui.theme.SedniumColors

/**
 * Lightweight Kotlin counterpart to MarkdownRenderer.tsx (which wraps
 * `react-markdown` + `react-syntax-highlighter`). A full Markdown engine
 * is out of scope for a UI port, so this renders the subset the chat
 * actually produces: paragraphs, **bold**, *italic*, `inline code`,
 * fenced ``` code blocks ``` and blockquotes ("> ").
 *
 * For production use, swap the inline parser below for a real library
 * (e.g. compose-markdown / Markwon-compose) while keeping this file's
 * color + spacing tokens.
 */
@Composable
fun MarkdownText(
    content: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val textColor = if (isDark) SedniumColors.Gray200 else SedniumColors.Orange
    val codeBg = if (isDark) SedniumColors.Gray800 else SedniumColors.Gray100
    val codeFg = if (isDark) SedniumColors.Gray200 else SedniumColors.Gray800
    val quoteBorder = if (isDark) SedniumColors.Gray700 else OrangeAlpha.a20

    Column(modifier = modifier.fillMaxWidth()) {
        val blocks = content.split("```")
        blocks.forEachIndexed { index, block ->
            val isCodeBlock = index % 2 == 1
            if (isCodeBlock) {
                val lines = block.trim().lines()
                val lang = lines.firstOrNull()?.takeIf { it.isNotBlank() && !it.contains(" ") } ?: ""
                val code = if (lang.isNotEmpty()) lines.drop(1).joinToString("\n") else block.trim()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) SedniumColors.Black else SedniumColors.Gray900)
                        .padding(12.dp)
                ) {
                    if (lang.isNotEmpty()) {
                        Text(lang, color = SedniumColors.Gray400, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        text = code,
                        color = SedniumColors.Gray100,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                block.trim('\n').split("\n").filter { it.isNotBlank() }.forEach { line ->
                    when {
                        line.startsWith(">") -> {
                            Text(
                                text = inlineMarkdown(line.removePrefix(">").trim(), textColor, codeBg, codeFg),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .background(quoteBorder.copy(alpha = 0.08f))
                                    .padding(start = 10.dp, top = 4.dp, bottom = 4.dp)
                            )
                        }
                        else -> {
                            Text(
                                text = inlineMarkdown(line, textColor, codeBg, codeFg),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Parses **bold**, *italic* and `inline code` into an AnnotatedString. */
private fun inlineMarkdown(
    raw: String,
    baseColor: Color,
    codeBg: Color,
    codeFg: Color
) = buildAnnotatedString {
    var i = 0
    pushStyle(SpanStyle(color = baseColor))
    while (i < raw.length) {
        when {
            raw.startsWith("**", i) -> {
                val end = raw.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                        append(raw.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(raw[i]); i++ }
            }
            raw.startsWith("`", i) -> {
                val end = raw.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = codeFg)) {
                        append(' ' + raw.substring(i + 1, end) + ' ')
                    }
                    i = end + 1
                } else { append(raw[i]); i++ }
            }
            raw.startsWith("*", i) -> {
                val end = raw.indexOf("*", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                        append(raw.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(raw[i]); i++ }
            }
            else -> { append(raw[i]); i++ }
        }
    }
    pop()
}
