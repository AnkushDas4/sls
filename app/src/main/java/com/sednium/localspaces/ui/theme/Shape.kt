package com.sednium.localspaces.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner-radius tokens lifted from the Tailwind utilities sprinkled across
 * the components: rounded-lg(8), rounded-xl(12), rounded-2xl(16),
 * rounded-[32px] (composer + user bubble), rounded-full (icon buttons).
 */
object SedniumRadii {
    val sm = 8.dp     // rounded-lg      -> attachment chips, file badges
    val md = 12.dp    // rounded-xl      -> drawer rows, buttons
    val lg = 16.dp     // rounded-2xl     -> avatar squares, preset menu
    val xl = 24.dp    // rounded-[24px]  -> larger cards
    val pill = 32.dp  // rounded-[32px]  -> composer container, user bubble
    val full = 999.dp // rounded-full    -> circular icon buttons
}

val SedniumShapes = Shapes(
    extraSmall = RoundedCornerShape(SedniumRadii.sm),
    small = RoundedCornerShape(SedniumRadii.md),
    medium = RoundedCornerShape(SedniumRadii.lg),
    large = RoundedCornerShape(SedniumRadii.pill),
    extraLarge = RoundedCornerShape(SedniumRadii.full)
)
