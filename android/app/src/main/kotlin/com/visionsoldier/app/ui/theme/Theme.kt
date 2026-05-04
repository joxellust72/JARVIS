package com.visionsoldier.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Colores estáticos (no cambian con la paleta) ──────────────
val JarvisGold    = Color(0xFFFFB800)
val JarvisDanger  = Color(0xFFFF3860)

// ── Backward-compat vals (paleta Jarvis por defecto) ─────────
val JarvisBlue     = Color(0xFF00E5FF)
val JarvisAccent   = Color(0xFF00FFD1)
val JarvisBg       = Color(0xFF050A14)
val JarvisBgCard   = Color(0xFF0A1628)
val JarvisTextDim  = Color(0xFF607D8B)
val JarvisBlueDeep = Color(0xFF0040A0)

// ── Paleta de colores del tema ────────────────────────────────
data class VisionPalette(
    val id: String,
    val displayName: String,
    val primary: Color,
    val accent: Color,
    val background: Color,
    val bgCard: Color,
    val textDim: Color,
    val orbGrad1: Color,
    val orbGrad2: Color,
    val orbGrad3: Color,
    val swatchGrad: List<Color>,      // para el swatch en Perfil
    val isLight: Boolean = false,
)

val Palettes: Map<String, VisionPalette> = linkedMapOf(
    "jarvis" to VisionPalette(
        id = "jarvis", displayName = "Jarvis Blue",
        primary   = Color(0xFF00E5FF),
        accent    = Color(0xFF00FFD1),
        background = Color(0xFF050A14),
        bgCard    = Color(0xFF0A1628),
        textDim   = Color(0xFF607D8B),
        orbGrad1  = Color(0xFF64C8FF),
        orbGrad2  = Color(0xFF00A8FF),
        orbGrad3  = Color(0xFF0064C8),
        swatchGrad = listOf(Color(0xFF00E5FF), Color(0xFF0040A0)),
        isLight = false,
    ),
    "light" to VisionPalette(
        id = "light", displayName = "Light Mode",
        primary   = Color(0xFF0066FF),
        accent    = Color(0xFF00A8FF),
        background = Color(0xFFF4F7F9),
        bgCard    = Color(0xFFFFFFFF),
        textDim   = Color(0xFF607D8B),
        orbGrad1  = Color(0xFF80D8FF),
        orbGrad2  = Color(0xFF007BFF),
        orbGrad3  = Color(0xFF0040A0),
        swatchGrad = listOf(Color(0xFFFFFFFF), Color(0xFFE0E0E0)),
        isLight = true,
    ),
    "phantom" to VisionPalette(
        id = "phantom", displayName = "Phantom Red",
        primary   = Color(0xFFFF2850),
        accent    = Color(0xFFFF8C00),
        background = Color(0xFF120508),
        bgCard    = Color(0xFF1C0810),
        textDim   = Color(0xFF8A4A5A),
        orbGrad1  = Color(0xFFFF6478),
        orbGrad2  = Color(0xFFFF2850),
        orbGrad3  = Color(0xFFB41432),
        swatchGrad = listOf(Color(0xFFFF6080), Color(0xFF800020)),
    ),
    "matrix" to VisionPalette(
        id = "matrix", displayName = "Emerald Matrix",
        primary   = Color(0xFF00FF64),
        accent    = Color(0xFFAAFF44),
        background = Color(0xFF030D08),
        bgCard    = Color(0xFF051508),
        textDim   = Color(0xFF3A6A4A),
        orbGrad1  = Color(0xFF64FFA0),
        orbGrad2  = Color(0xFF00FF64),
        orbGrad3  = Color(0xFF00A03C),
        swatchGrad = listOf(Color(0xFF60FF90), Color(0xFF005020)),
    ),
    "void" to VisionPalette(
        id = "void", displayName = "Void Purple",
        primary   = Color(0xFFA050FF),
        accent    = Color(0xFFFF50C8),
        background = Color(0xFF08050F),
        bgCard    = Color(0xFF100818),
        textDim   = Color(0xFF6A4A8A),
        orbGrad1  = Color(0xFFC88CFF),
        orbGrad2  = Color(0xFFA050FF),
        orbGrad3  = Color(0xFF5014B4),
        swatchGrad = listOf(Color(0xFFC080FF), Color(0xFF300060)),
    ),
    "solar" to VisionPalette(
        id = "solar", displayName = "Solar Gold",
        primary   = Color(0xFFFFB800),
        accent    = Color(0xFFFF6A00),
        background = Color(0xFF0F0A02),
        bgCard    = Color(0xFF180E03),
        textDim   = Color(0xFF8A6A30),
        orbGrad1  = Color(0xFFFFE678),
        orbGrad2  = Color(0xFFFFB800),
        orbGrad3  = Color(0xFFC86400),
        swatchGrad = listOf(Color(0xFFFFD060), Color(0xFF802000)),
        isLight = false,
    ),
)

val LocalVisionPalette = staticCompositionLocalOf { Palettes["light"]!! }

@Composable
fun VisionTheme(
    palette: VisionPalette = Palettes["light"]!!,
    content: @Composable () -> Unit,
) {
    val isLight = palette.isLight
    val colorScheme = if (isLight) {
        androidx.compose.material3.lightColorScheme(
            primary          = palette.primary,
            onPrimary        = Color.White,
            primaryContainer = palette.orbGrad3,
            secondary        = palette.accent,
            onSecondary      = Color.White,
            tertiary         = JarvisGold,
            background       = palette.background,
            surface          = palette.bgCard,
            onBackground     = Color(0xFF1E293B),
            onSurface        = Color(0xFF334155),
            error            = JarvisDanger,
        )
    } else {
        androidx.compose.material3.darkColorScheme(
            primary          = palette.primary,
            onPrimary        = palette.background,
            primaryContainer = palette.orbGrad3,
            secondary        = palette.accent,
            onSecondary      = palette.background,
            tertiary         = JarvisGold,
            background       = palette.background,
            surface          = palette.bgCard,
            onBackground     = Color(0xFFE0F7FA),
            onSurface        = Color(0xFFB0BEC5),
            error            = JarvisDanger,
        )
    }
    CompositionLocalProvider(LocalVisionPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
