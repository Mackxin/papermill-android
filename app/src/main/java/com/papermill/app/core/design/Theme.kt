package com.papermill.app.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 配色：米白纸感底 + 沉稳蓝主色 + 极小面积暖赭点缀。
 * 面向国内家长，主诉求是「可信、干净」，所以不用渐变、不用高饱和、不用阴影。
 */
object PaperColors {
    val Primary = Color(0xFF3A6EA5)
    val PrimaryContainer = Color(0xFFE8F0F8)
    val Accent = Color(0xFFD98E3A)
    val Background = Color(0xFFF7F5F0)
    val Surface = Color(0xFFFFFFFF)
    val Outline = Color(0xFFE6E1D8)
    val OnSurface = Color(0xFF1F2328)
    val OnSurfaceVariant = Color(0xFF6B6F76)
    val Error = Color(0xFFC0392B)
}

private val PaperMillScheme = lightColorScheme(
    primary = PaperColors.Primary,
    onPrimary = Color.White,
    primaryContainer = PaperColors.PrimaryContainer,
    onPrimaryContainer = PaperColors.Primary,
    secondary = PaperColors.Accent,
    onSecondary = Color.White,
    background = PaperColors.Background,
    onBackground = PaperColors.OnSurface,
    surface = PaperColors.Surface,
    onSurface = PaperColors.OnSurface,
    surfaceVariant = PaperColors.PrimaryContainer,
    onSurfaceVariant = PaperColors.OnSurfaceVariant,
    outline = PaperColors.Outline,
    outlineVariant = PaperColors.Outline,
    error = PaperColors.Error,
)

@Composable
fun PaperMillTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PaperMillScheme, content = content)
}
