package com.sednium.localspaces.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens — ported 1:1 from the original `tailwind.config` block in
 * index.html and the supplementary <style> block.
 *
 *   colors: {
 *     sedYellow: '#FDFBF7',
 *     sedRed:    '#EC5E27'
 *   }
 *
 * "sedRed" is the single accent/text/ink color of the whole app (it reads
 * as a burnt orange, not a true red — the name is a brand artifact, kept
 * as-is for fidelity).
 */
object SedniumColors {

    // ---- Brand tokens (identical in both themes) ----
    val Milk = Color(0xFFFDFBF7)   // primary light background / "paper"
    val Orange    = Color(0xFFEC5E27)   // primary accent / ink / CTA

    val SedYellow = Milk
    val SedRed = Orange

    // ---- Theme-aware surfaces ----
    val DarkBackground   = Color(0xFF121212)  // .dark body background-color
    val DarkSurfaceAlt   = Color(0xFF2A2B32)  // dark file-chip / card surface (MessageItem)
    val White            = Color(0xFFFFFFFF)
    val Black            = Color(0xFF000000)

    // ---- Neutral / gray scale (Tailwind "gray") used in markdown & message chrome ----
    val Gray100 = Color(0xFFF3F4F6)
    val Gray200 = Color(0xFFE5E7EB)
    val Gray300 = Color(0xFFD1D5DB)
    val Gray400 = Color(0xFF9CA3AF)
    val Gray500 = Color(0xFF6B7280)
    val Gray600 = Color(0xFF4B5563)   // also the dark-mode scrollbar thumb color
    val Gray700 = Color(0xFF374151)
    val Gray800 = Color(0xFF1F2937)
    val Gray900 = Color(0xFF111827)

    // ---- Status / semantic (Tailwind "red") used for error & destructive states ----
    val Red100 = Color(0xFFFEE2E2)
    val Red500 = Color(0xFFEF4444)
    val Red600 = Color(0xFFDC2626)
    val Red700 = Color(0xFFB91C1C)
    val Red800 = Color(0xFF991B1B)
    val Red900 = Color(0xFF7F1D1D)

    // ---- Accents used sparingly inside markdown code blocks / links ----
    val Blue50  = Color(0xFFEFF6FF)
    val Blue400 = Color(0xFF60A5FA)
    val Blue500 = Color(0xFF3B82F6)
    val Blue600 = Color(0xFF2563EB)
    val Green500  = Color(0xFF22C55E)
    val Orange500 = Color(0xFFF97316)
}

/**
 * Tailwind's `sedRed/NN` opacity-suffix utility (e.g. `bg-sedRed/10`,
 * `border-sedRed/30`) ported as a simple alpha helper so call-sites read
 * the same way the original class names did.
 *
 * Usage: SedniumColors.Orange.alpha(0.10f)   // == bg-sedRed/10
 */
fun Color.alpha(a: Float): Color = this.copy(alpha = a)

/** Common opacity steps the original CSS used repeatedly, pre-baked. */
object OrangeAlpha {
    val a05 = SedniumColors.Orange.alpha(0.05f)
    val a10 = SedniumColors.Orange.alpha(0.10f)
    val a20 = SedniumColors.Orange.alpha(0.20f)
    val a30 = SedniumColors.Orange.alpha(0.30f)
    val a40 = SedniumColors.Orange.alpha(0.40f)
    val a50 = SedniumColors.Orange.alpha(0.50f)
    val a60 = SedniumColors.Orange.alpha(0.60f)
    val a70 = SedniumColors.Orange.alpha(0.70f)
    val a90 = SedniumColors.Orange.alpha(0.90f)
}

val SedRedAlpha = OrangeAlpha
