package com.papermill.app.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 纸感设计系统 v2。
 *
 * 上一版的问题不是「不好看」，是**层级不清**：所有信息都是白卡片＋灰小字，
 * 蓝色 chip 到处都是，用户分不清哪个是主操作、哪个只是配置。
 * 这一版把职责写死：
 *   - 蓝 = 唯一的主操作 / 选中态
 *   - 暖赭 = 需要注意的提示（比如会浪费纸）
 *   - 灰 = 纯辅助信息，永远不做可点元素
 *   - 白卡片 = 内容分组；浅米色块 = 卡片内的次级区域
 */
object PaperColors {
    val Primary = Color(0xFF2F6B9F)
    val PrimaryDark = Color(0xFF24557F)
    val PrimaryContainer = Color(0xFFE9F1F8)
    val Accent = Color(0xFFC87A2C)
    val AccentContainer = Color(0xFFFBF0E4)
    val Success = Color(0xFF2E7D5B)
    val Background = Color(0xFFF6F4EF)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFFAF8F3)
    val Outline = Color(0xFFE5E0D6)
    val OutlineStrong = Color(0xFFD6D0C4)
    val OnSurface = Color(0xFF1C2126)
    val OnSurfaceVariant = Color(0xFF767B82)
    val Muted = Color(0xFFADB0B0)
    val Error = Color(0xFFC0392B)
    val ErrorContainer = Color(0xFFFBECEA)
}

/** 统一尺寸与字号，避免每个页面各写一套 dp/sp 导致对齐漂移 */
object PaperDimens {
    val ScreenPad = 18.dp
    val CardPad = 14.dp
    val CardRadius = 14.dp
    val ChipRadius = 10.dp
    val Gap = 12.dp
    val GapSmall = 8.dp
    val ActionBarH = 56.dp
    val NavH = 62.dp

    val TitleSp = 22.sp
    val HeroSp = 20.sp
    val SectionSp = 15.sp
    val BodySp = 13.sp
    val LabelSp = 12.sp
    val CaptionSp = 11.sp
    val TinySp = 10.sp
}

private val PaperMillScheme = lightColorScheme(
    primary = PaperColors.Primary,
    onPrimary = Color.White,
    primaryContainer = PaperColors.PrimaryContainer,
    onPrimaryContainer = PaperColors.PrimaryDark,
    secondary = PaperColors.Accent,
    onSecondary = Color.White,
    secondaryContainer = PaperColors.AccentContainer,
    background = PaperColors.Background,
    onBackground = PaperColors.OnSurface,
    surface = PaperColors.Surface,
    onSurface = PaperColors.OnSurface,
    surfaceVariant = PaperColors.PrimaryContainer,
    onSurfaceVariant = PaperColors.OnSurfaceVariant,
    outline = PaperColors.Outline,
    outlineVariant = PaperColors.OutlineStrong,
    error = PaperColors.Error,
)

@Composable
fun PaperMillTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PaperMillScheme, content = content)
}
