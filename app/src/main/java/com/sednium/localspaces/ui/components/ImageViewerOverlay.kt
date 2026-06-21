package com.sednium.localspaces.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sednium.localspaces.ui.theme.SedniumMotion

/**
 * Port of the `fixed inset-0 z-[100] bg-black/90 backdrop-blur-sm ...`
 * lightbox at the bottom of App.tsx. Tapping the scrim (but not the
 * image) dismisses it — `animate-fade-in` on the scrim, `animate-slide-up`
 * on the image itself.
 */
@Composable
fun ImageViewerOverlay(
    imageUrl: String?,
    onDismiss: () -> Unit,
    content: @Composable (String) -> Unit // inject Coil's AsyncImage / painterResource here
) {
    AnimatedVisibility(
        visible = imageUrl != null,
        enter = fadeIn(tween(SedniumMotion.FADE)),
        exit = fadeOut(tween(SedniumMotion.FAST))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }

            AnimatedVisibility(
                visible = imageUrl != null,
                enter = slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(SedniumMotion.SLIDE, easing = SedniumMotion.SlideUpEasing)
                ) + fadeIn()
            ) {
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .clickable(enabled = false) {} // swallow taps so they don't dismiss
                ) {
                    imageUrl?.let { content(it) }
                }
            }
        }
    }
}
