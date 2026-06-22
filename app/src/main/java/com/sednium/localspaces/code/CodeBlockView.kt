package com.sednium.localspaces.code

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The actual "code block viewer" — fenced ``` blocks no longer render as
 * an undifferentiated gray slab. Header shows the declared language and
 * a copy button; the body is line-numbered, horizontally scrollable (so
 * long lines don't wrap and destroy indentation), and tokenized via
 * SyntaxHighlighter. Text is also selectable directly, independent of
 * the copy button, via SelectionContainer.
 */
@Composable
fun CodeBlockView(
    language: String?,
    code: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var justCopied by remember { mutableStateOf(false) }

    val highlighted = remember(code, language) { SyntaxHighlighter.highlight(code, language, isDark) }
    val lineCount = remember(code) { code.count { it == '\n' } + 1 }
    val lineNumberWidth = remember(lineCount) { (lineCount.toString().length * 9 + 12).dp }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0D1117)) // near-black surface the SyntaxHighlighter palette is tuned against
    ) {
        // --- header: language pill + copy button ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = (language?.takeIf { it.isNotBlank() } ?: "text").uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8B949E),
                fontFamily = FontFamily.Monospace
            )
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    justCopied = true
                    scope.launch { delay(1500); justCopied = false }
                },
                modifier = Modifier.width(28.dp)
            ) {
                Icon(
                    if (justCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    contentDescription = if (justCopied) "Copied" else "Copy code",
                    tint = if (justCopied) Color(0xFF3FB950) else Color(0xFF8B949E),
                    modifier = Modifier.padding(2.dp)
                )
            }
        }

        HorizontalDivider(color = Color(0xFF21262D))

        // --- body: line numbers + highlighted, horizontally scrollable code ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 10.dp)
        ) {
            Column(
                modifier = Modifier.width(lineNumberWidth).padding(end = 8.dp, start = 12.dp),
            ) {
                repeat(lineCount) { idx ->
                    Text(
                        text = (idx + 1).toString(),
                        color = Color(0xFF6E7681),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            SelectionContainer {
                Text(
                    text = highlighted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }
    }
}
