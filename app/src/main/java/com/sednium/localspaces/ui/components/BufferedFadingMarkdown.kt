package com.sednium.localspaces.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.sednium.localspaces.markdown.MarkdownView
import kotlinx.coroutines.delay

/**
 * Drop-in replacement for calling `MarkdownView(content, isDark)` directly
 * inside a message bubble that may be actively streaming.
 *
 * Two real problems with feeding `msg.content` straight into MarkdownView
 * while it's growing token-by-token:
 *
 * 1. Every single character appended re-runs `parseMarkdown()` on the WHOLE
 *    accumulated string and recomposes the WHOLE block tree on every
 *    recomposition pass — this is the actual source of the visible jitter,
 *    not just "raw token appended" cosmetically.
 * 2. There's no visual cue that text is actively arriving, the way the
 *    Gemini app / Gallery's fade-in chunks read.
 *
 * Fix:
 * - While `isStreaming`, only commit the latest `content` into the state
 *   that actually feeds MarkdownView on a fixed ~70ms cadence instead of on
 *   every recomposition. This caps the expensive re-parse/re-render to
 *   roughly 14 times/sec regardless of how fast tokens are actually
 *   arriving from the network, which is what removes the jank.
 * - Draw a soft vertical fade over the trailing ~28dp of the block while
 *   streaming, so newly committed text visually emerges out of a "mist"
 *   instead of popping in fully-opaque — this is the actual fade-in cue,
 *   applied once per render batch rather than per character.
 * - The moment `isStreaming` flips to false, snap immediately to the final
 *   `content` (no lag, no fade) so the settled message is never stuck a few
 *   characters behind.
 */
@Composable
fun BufferedFadingMarkdown(
    content: String,
    isDark: Boolean,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    var displayed by remember { mutableStateOf(content) }

    LaunchedEffect(isStreaming) {
        if (!isStreaming) {
            displayed = content
        }
    }

    LaunchedEffect(content, isStreaming) {
        if (isStreaming) {
            // Step towards the latest content on a fixed cadence rather than
            // mirroring it instantly on every change.
            while (displayed.length < content.length) {
                displayed = content
                delay(70)
            }
        } else {
            displayed = content
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isStreaming) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val fadeHeight = 28.dp.toPx()
                            if (size.height > fadeHeight) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black),
                                        startY = size.height - fadeHeight,
                                        endY = size.height
                                    ),
                                    blendMode = BlendMode.DstOut,
                                    topLeft = Offset(0f, size.height - fadeHeight),
                                    size = Size(size.width, fadeHeight)
                                )
                            }
                        }
                } else {
                    Modifier
                }
            )
    ) {
        MarkdownView(content = displayed, isDark = isDark)
    }
}
