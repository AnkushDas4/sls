package com.sednium.localspaces.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sednium.localspaces.R

/**
 * The web app loads Google Fonts "Source Serif 4" (variable weight 200-900,
 * italic + roman) and sets it as the sole `font-sans` family:
 *
 *   fontFamily: { sans: ['"Source Serif 4"', 'serif'] }
 *
 * On Android we pull the same family from a bundled variable font (place
 * source_serif_4.ttf in res/font/) and fall back to the system serif if
 * it isn't bundled, so the app never silently reverts to a sans-serif look.
 */
val SourceSerif4: FontFamily = FontFamily.Serif

val SedniumTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle( // chat title in TopBar
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = (-0.1).sp
    ),
    labelSmall = TextStyle( // provider/model caption, "10px" uppercase mono caption
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.6.sp
    ),
    bodyLarge = TextStyle( // composer input text-[16px]
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle( // message bubble body
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle( // footer disclaimer text-[10px]
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp
    ),
    labelLarge = TextStyle( // buttons
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    )
)
