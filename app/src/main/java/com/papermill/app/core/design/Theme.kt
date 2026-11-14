package com.papermill.app.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计系统 v3「清爽现代」。
 *
 * v2 是「纸感文雅」：米白底 + 深蓝 + 细边框 + 小字，信息层级没错，但整体偏旧、
 * 偏工具感 —— 用户原话「有点老土」。v3 换成更年轻的现代卡片语言：
 *
 *  - 底色用冷调浅雾灰（#F5F6FB），卡片纯白 + 22dp 大圆角 + 柔和投影，不再描边；
 *  - 主色是亮蓝紫，主按钮用 6C7BFF→9A5CF6 渐变，是整屏唯一的「强动作」；
 *  - 薄荷绿只用于「排满了 / 成功」，珊瑚橙只用于「会浪费纸」这类提醒；
 *  - 字号整体上调一档（正文 14sp），行距更松，扫读更舒服；
 *  - chip 改成胶囊（未选中浅灰、选中淡紫 + 主色字），不再整块实心蓝。
 *
 * 颜色字段名沿用 v2，避免一次性改遍所有页面；新增 GradientStart / GradientEnd / Mint。
 */
object PaperColors {
    val Primary = Color(0xFF5B5BF0)
    val PrimaryDark = Color(0xFF3E3ECB)
    val PrimaryContainer = Color(0xFFEDEDFF)

    /** 渐变主色：主按钮、选中 dock 项、强调数字 */
    val GradientStart = Color(0xFF6C7BFF)
    val GradientEnd = Color(0xFF9A5CF6)

    val Accent = Color(0xFFFF7A45)
    val AccentContainer = Color(0xFFFFF0E8)

    /** 薄荷绿：排满一页 / 成功状态 */
    val Mint = Color(0xFF10C6A3)
    val MintContainer = Color(0xFFE3FBF5)
    val Success = Mint

    val Background = Color(0xFFF5F6FB)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFF2F3F9)

    val Outline = Color(0xFFEBEDF5)
    val OutlineStrong = Color(0xFFDDE0EC)

    val OnSurface = Color(0xFF14161C)
    val OnSurfaceVariant = Color(0xFF6E7385)
    val Muted = Color(0xFFA8ADBE)

    val Error = Color(0xFFE5484D)
    val ErrorContainer = Color(0xFFFFECEE)
}

/** 统一尺寸与字号，避免每个页面各写一套 dp/sp 导致对齐漂移 */
object PaperDimens {
    val ScreenPad = 20.dp
    val CardPad = 16.dp
    val CardRadius = 22.dp
    val ChipRadius = 14.dp
    val Gap = 14.dp
    val GapSmall = 10.dp
    val ActionBarH = 54.dp
    val NavH = 64.dp

    val TitleSp = 27.sp
    val HeroSp = 21.sp
    val SectionSp = 16.sp
    val BodySp = 14.sp
    val LabelSp = 13.sp
    val CaptionSp = 12.sp
    val TinySp = 11.sp
}

private val PaperMillScheme = lightColorScheme(
    primary = PaperColors.Primary,
    onPrimary = Color.White,
    primaryContainer = PaperColors.PrimaryContainer,
    onPrimaryContainer = PaperColors.PrimaryDark,
    secondary = PaperColors.Mint,
    onSecondary = Color.White,
    secondaryContainer = PaperColors.MintContainer,
    tertiary = PaperColors.Accent,
    background = PaperColors.Background,
    onBackground = PaperColors.OnSurface,
    surface = PaperColors.Surface,
    onSurface = PaperColors.OnSurface,
    surfaceVariant = PaperColors.SurfaceAlt,
    onSurfaceVariant = PaperColors.OnSurfaceVariant,
    outline = PaperColors.Outline,
    outlineVariant = PaperColors.OutlineStrong,
    error = PaperColors.Error,
)

@Composable
fun PaperMillTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PaperMillScheme, content = content)
}
