package com.example.fileexplorer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/** Palette modelled on the Windows 11 File Explorer (Fluent) look. */
@Immutable
data class WinColors(
    val window: Color,        // content area
    val chrome: Color,        // address bar + command bar strip
    val pane: Color,          // navigation pane
    val field: Color,         // address / search boxes
    val fieldBorder: Color,
    val divider: Color,
    val text: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val accent: Color,
    val selection: Color,     // selected rows / tiles
    val paneSelected: Color,  // selected item in the navigation pane
    val usageFill: Color,
    val usageFull: Color,
    val usageTrack: Color,
    val folderFront: Color,
    val folderBack: Color,
    val isDark: Boolean,
)

val DarkWinColors = WinColors(
    window = Color(0xFF191919),
    chrome = Color(0xFF202020),
    pane = Color(0xFF1C1C1C),
    field = Color(0xFF2B2B2B),
    fieldBorder = Color(0xFF3A3A3A),
    divider = Color(0xFF2F2F2F),
    text = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB4B4B4),
    textDisabled = Color(0xFF5E5E5E),
    accent = Color(0xFF4CC2FF),
    selection = Color(0xFF26384A),
    paneSelected = Color(0xFF2D2D2D),
    usageFill = Color(0xFF26A0DA),
    usageFull = Color(0xFFDA2626),
    usageTrack = Color(0xFFE6E6E6),
    folderFront = Color(0xFFFFCC4D),
    folderBack = Color(0xFFE6A817),
    isDark = true,
)

val LightWinColors = WinColors(
    window = Color(0xFFFFFFFF),
    chrome = Color(0xFFF3F3F3),
    pane = Color(0xFFF9F9F9),
    field = Color(0xFFFFFFFF),
    fieldBorder = Color(0xFFE0E0E0),
    divider = Color(0xFFE5E5E5),
    text = Color(0xFF1A1A1A),
    textSecondary = Color(0xFF5F5F5F),
    textDisabled = Color(0xFFB0B0B0),
    accent = Color(0xFF005FB8),
    selection = Color(0xFFCCE8FF),
    paneSelected = Color(0xFFEAEAEA),
    usageFill = Color(0xFF26A0DA),
    usageFull = Color(0xFFDA2626),
    usageTrack = Color(0xFFE6E6E6),
    folderFront = Color(0xFFFFCC4D),
    folderBack = Color(0xFFE6A817),
    isDark = false,
)

val LocalWinColors = staticCompositionLocalOf { DarkWinColors }

/** Shorthand: `win.text`, `win.accent`, ... */
val win: WinColors
    @Composable get() = LocalWinColors.current

private val WinTypography = Typography(
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp),
)

@Composable
fun FileExplorerTheme(content: @Composable () -> Unit) {
    val c = if (isSystemInDarkTheme()) DarkWinColors else LightWinColors
    val scheme = if (c.isDark) {
        darkColorScheme(
            primary = c.accent, onPrimary = Color.Black,
            background = c.window, onBackground = c.text,
            surface = c.window, onSurface = c.text, onSurfaceVariant = c.textSecondary,
            surfaceContainer = c.chrome, surfaceContainerHigh = c.chrome, surfaceContainerHighest = c.field,
            secondaryContainer = c.selection, outline = c.fieldBorder, outlineVariant = c.divider,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = Color.White,
            background = c.window, onBackground = c.text,
            surface = c.window, onSurface = c.text, onSurfaceVariant = c.textSecondary,
            surfaceContainer = c.chrome, surfaceContainerHigh = c.chrome, surfaceContainerHighest = c.field,
            secondaryContainer = c.selection, outline = c.fieldBorder, outlineVariant = c.divider,
        )
    }
    CompositionLocalProvider(LocalWinColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = WinTypography, content = content)
    }
}
