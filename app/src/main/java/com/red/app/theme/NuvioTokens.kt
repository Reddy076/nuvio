package com.red.app.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 1:1 Design Tokens ported from Nuvio Core UI Design System.
 */
object NuvioTokens {
    object Space {
        val none: Dp = 0.dp
        val hairline: Dp = 0.5.dp
        val s1: Dp = 1.dp
        val s2: Dp = 2.dp
        val s3: Dp = 3.dp
        val s4: Dp = 4.dp
        val s5: Dp = 5.dp
        val s6: Dp = 6.dp
        val s7: Dp = 7.dp
        val s8: Dp = 8.dp
        val s10: Dp = 10.dp
        val s12: Dp = 12.dp
        val s14: Dp = 14.dp
        val s16: Dp = 16.dp
        val s18: Dp = 18.dp
        val s20: Dp = 20.dp
        val s22: Dp = 22.dp
        val s24: Dp = 24.dp
        val s28: Dp = 28.dp
        val s32: Dp = 32.dp
        val s36: Dp = 36.dp
        val s40: Dp = 40.dp
        val s48: Dp = 48.dp
        val s56: Dp = 56.dp
        val s64: Dp = 64.dp
        val s72: Dp = 72.dp
        val s80: Dp = 80.dp
        val s96: Dp = 96.dp
    }

    object Radius {
        val none: Dp = Space.none
        val xs: Dp = Space.s4
        val sm: Dp = Space.s6
        val md: Dp = Space.s8
        val lg: Dp = Space.s12
        val xl: Dp = Space.s16
        val xxl: Dp = Space.s24
        val full: Dp = 999.dp

        val card: Dp = xxl
        val compactCard: Dp = lg
        val sheet: Dp = xxl
        val dialog: Dp = Space.s28
        val button: Dp = xl
        val chip: Dp = full
        val poster: Dp = lg
        val avatar: Dp = full
        val playerPanel: Dp = xxl
    }

    object Border {
        val hairline: Dp = Space.hairline
        val thin: Dp = Space.s1
        val medium: Dp = Space.s2
    }

    object Elevation {
        val none: Dp = Space.none
        val raised: Dp = Space.s2
        val modal: Dp = Space.s8
        val overlay: Dp = Space.s12
        val playerControls: Dp = Space.s4
    }

    object Opacity {
        const val invisible: Float = 0f
        const val disabled: Float = 0.38f
        const val secondary: Float = 0.70f
        const val muted: Float = 0.60f
        const val selected: Float = 0.15f
        const val hover: Float = 0.08f
        const val pressed: Float = 0.12f
        const val subtle: Float = 0.06f
        const val medium: Float = 0.52f
        const val strong: Float = 0.75f
        const val overlayLight: Float = 0.35f
        const val overlayMedium: Float = 0.56f
        const val overlayHeavy: Float = 0.82f
        const val visible: Float = 1f
    }

    object Colors {
        // True Nuvio Cinema Dark
        val background = Color(0xFF0B0C0E)
        val backgroundAmoled = Color(0xFF000000)
        val surface = Color(0xFF16181D)
        val surfaceCard = Color(0xFF1A1D24)
        val surfaceElevated = Color(0xFF222630)
        val surfaceGlass = Color(0xFF1C1C1E)
        val borderDefault = Color(0x1AFFFFFF)
        val borderSubtle = Color(0x0DFFFFFF)
        val borderSelected = Color(0x66FFFFFF)

        // Nuvio Accent Colors
        val accent = Color(0xFFFFFFFF)
        val accentMuted = Color(0xB3FFFFFF)
        val accentRed = Color(0xFFE50914)
        val accentBlue = Color(0xFF38BDF8)
        val accentAmber = Color(0xFFF59E0B)

        // Typography Colors
        val textPrimary = Color(0xFFFFFFFF)
        val textSecondary = Color(0xFF94A3B8)
        val textMuted = Color(0xFF64748B)
        val textDisabled = Color(0xFF475569)

        // Compatibility Aliases for components & screens
        val primary = accentRed
        val primaryVariant = Color(0xFFFF2E4D)
        val primaryGlow = Color(0x66E50914)
        val cyanAccent = accentBlue
        val border = borderDefault
        val shimmer = surfaceElevated
        val playerOverlay = Color(0xCC000000)
        val playerHud = Color(0xDD0E0F16)
        val playerProgressActive = accentRed
        val playerProgressInactive = Color(0x40FFFFFF)

        // Glass Specular
        val glassSurface = Color(0xFF1C1C1E)
        val glassEdgeLight = Color(0x45FFFFFF)
        val glassEdgeDark = Color(0x05FFFFFF)
    }

    object Motion {
        const val instantMillis = 0
        const val fastMillis = 150
        const val normalMillis = 220
        const val sheetEnterMillis = 300
        const val sheetExitMillis = 250
        const val slowMillis = 400
        const val cinematicMillis = 700

        val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
        val accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    }

    object Type {
        val labelXs = 11.sp
        val labelSm = 12.sp
        val bodySm = 13.sp
        val bodyMd = 14.sp
        val bodyApp = 15.sp
        val bodyLg = 16.sp
        val titleSm = 18.sp
        val headline = 26.sp
        val titleMd = 22.sp
        val titleLg = 28.sp
        val displaySm = 32.sp
        val pageDisplay = 38.sp
        val displayMd = 48.sp
    }

    object LineHeight {
        val labelXs = 14.sp
        val labelSm = 15.sp
        val bodySm = 18.sp
        val bodyMd = 20.sp
        val bodyApp = 22.sp
        val bodyLg = 22.sp
        val titleSm = 22.sp
        val headline = 30.sp
        val titleMd = 26.sp
        val titleLg = 32.sp
        val displaySm = 36.sp
        val pageDisplay = 42.sp
        val displayMd = 52.sp
    }
}
