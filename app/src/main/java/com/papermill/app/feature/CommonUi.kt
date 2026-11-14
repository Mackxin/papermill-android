package com.papermill.app.feature

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.papermill.app.core.design.PaperColors
import com.papermill.app.core.design.PaperDimens

/**
 * 公共组件 v3：现代卡片 + 胶囊 chip + 渐变主按钮 + 悬浮 dock。
 *
 * 和 v2 的区别不是「换个颜色」，是交互语言换了：
 *  - 底部不再是「贴着屏幕边、一条横线 + 四个文字」的老式 tab bar，
 *    改成悬浮胶囊 dock，选中项有淡紫胶囊和主色图标，和内容区有呼吸感；
 *  - 所有卡片去掉 1dp 描边，改大圆角 + 柔和投影，这是 2024 之后移动端的主流；
 *  - chip 一律胶囊形，选中是「淡紫底 + 主色字」而不是整块高饱和实心；
 *  - 主按钮带渐变和投影，全屏只有它一个强视觉，手指自然落上去。
 */
object Surface3 {
    val shape = RoundedCornerShape(PaperDimens.CardRadius)
    val cardShadow = 6.dp
}

// ------------------------------------------------------------------ 框架

/** 顶部：大标题 + 副标题 + 圆形返回键，不放任何次要信息 */
@Composable
fun AppTopBar(
    title: String,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = PaperDimens.ScreenPad,
                end = PaperDimens.ScreenPad,
                top = 10.dp,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(PaperColors.Surface, CircleShape)
                    .shadow(4.dp, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) { Text("‹", fontSize = 24.sp, color = PaperColors.OnSurface) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = PaperDimens.TitleSp,
                fontWeight = FontWeight.Bold,
                color = PaperColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    fontSize = PaperDimens.CaptionSp,
                    color = PaperColors.OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 底部主操作条：白底 + 上投影，和悬浮 dock 叠在一起也不打架 */
@Composable
fun StickyBar(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PaperDimens.ActionBarH)
            .background(PaperColors.Background)
            .padding(horizontal = PaperDimens.ScreenPad, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * 悬浮胶囊 dock：4 个页签，纯文字（不放图标），选中项淡紫胶囊 + 主色字。
 *
 * 用户明确要求「tab 栏不需要图标」——去掉图标后整条更矮更安静，
 * 选中态靠胶囊底色 + 字重变化表达，比图标+文字的双行更利落。
 */
@Composable
fun FloatingDock(state: AppState, onSelect: (NavTab) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(14.dp, shape)
            .clip(shape)
            .background(PaperColors.Surface)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockItem("出卷", state.navTab == NavTab.HOME) { onSelect(NavTab.HOME) }
        DockItem("打印纸", state.navTab == NavTab.REFERENCE) { onSelect(NavTab.REFERENCE) }
        DockItem("错题本", state.navTab == NavTab.MISTAKES) { onSelect(NavTab.MISTAKES) }
        DockItem("更多", state.navTab == NavTab.MORE) { onSelect(NavTab.MORE) }
    }
}

@Composable
private fun RowScope.DockItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t by animateFloatAsState(if (selected) 1f else 0f, label = "dock-$label")
    val bg = lerp(Color.Transparent, PaperColors.PrimaryContainer, t)
    val fg = lerp(PaperColors.OnSurfaceVariant, PaperColors.Primary, t)
    val weight = if (t > 0.5f) FontWeight.Bold else FontWeight.Normal

    Box(
        Modifier
            .weight(1f)
            .height(42.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.5.sp,
            color = fg,
            fontWeight = weight,
            maxLines = 1,
        )
    }
}

// ------------------------------------------------------------------ 基础件

/** 白卡片：大圆角 + 柔和投影，不再描边 */
@Composable
fun CardSection(
    title: String = "",
    hint: String = "",
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(Surface3.cardShadow, Surface3.shape)
            .clip(Surface3.shape)
            .background(PaperColors.Surface)
            .padding(PaperDimens.CardPad),
    ) {
        if (title.isNotEmpty()) {
            Text(
                title,
                fontSize = PaperDimens.SectionSp,
                fontWeight = FontWeight.Bold,
                color = PaperColors.OnSurface,
            )
        }
        if (hint.isNotEmpty()) {
            Text(
                hint,
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (title.isNotEmpty() || hint.isNotEmpty()) Spacer(Modifier.height(12.dp))
        content()
    }
}

/** 分区标题：渐变短竖条 + 标题 + 右侧说明（序号参数保留兼容，不再显示数字） */
@Composable
fun SectionLabel(no: String, text: String, right: String = "") {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(PaperColors.GradientStart, PaperColors.GradientEnd)))
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            fontSize = PaperDimens.SectionSp,
            fontWeight = FontWeight.Bold,
            color = PaperColors.OnSurface,
        )
        Spacer(Modifier.weight(1f))
        if (right.isNotEmpty()) {
            Text(
                right,
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** 胶囊 chip：选中淡紫底 + 主色字，未选中浅灰底 */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> PaperColors.SurfaceAlt
        selected -> PaperColors.PrimaryContainer
        else -> PaperColors.SurfaceAlt
    }
    val fg = when {
        !enabled -> PaperColors.Muted
        selected -> PaperColors.Primary
        else -> PaperColors.OnSurfaceVariant
    }
    Box(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(
                if (selected && enabled) {
                    Modifier.border(1.2.dp, PaperColors.Primary.copy(alpha = 0.45f), RoundedCornerShape(50))
                } else Modifier
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 15.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = PaperDimens.LabelSp,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/**
 * 输入框 v3：圆角填充底 + 无下划线。
 *
 * 不用 Material3 的 TextField，是因为它默认带一条 indicator 横线，
 * 放在「白卡 + 大圆角」的体系里像上个世代的控件；这里用 BasicTextField
 * 自己拼 decorationBox，视觉完全跟 chip 一致。
 */
@Composable
fun Field3(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    label: String = "",
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(
                label,
                fontSize = PaperDimens.TinySp,
                color = PaperColors.OnSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(
                fontSize = PaperDimens.BodySp,
                color = PaperColors.OnSurface,
            ),
            cursorBrush = SolidColor(PaperColors.Primary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(PaperColors.SurfaceAlt)
                        .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            leadingIcon,
                            contentDescription = null,
                            tint = PaperColors.Muted,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(9.dp))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                placeholder,
                                fontSize = PaperDimens.BodySp,
                                color = PaperColors.Muted,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                    if (value.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(PaperColors.Outline)
                                .clickable { onValueChange("") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "清除",
                                tint = PaperColors.OnSurfaceVariant,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}

/** 单行横向 chip：年级 / 科目不再换行占掉半屏 */
@Composable
fun <T> SingleRowChips(
    items: List<T>,
    label: (T) -> String,
    selected: (T) -> Boolean,
    enabled: (T) -> Boolean = { true },
    onClick: (T) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall),
    ) {
        items.forEach { item ->
            ChoiceChip(
                text = label(item),
                selected = selected(item),
                enabled = enabled(item),
                onClick = { onClick(item) },
            )
        }
    }
}

@Composable
fun CompactChip(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> PaperColors.SurfaceAlt
        selected -> PaperColors.PrimaryContainer
        else -> PaperColors.SurfaceAlt
    }
    val fg = when {
        !enabled -> PaperColors.Muted
        selected -> PaperColors.Primary
        else -> PaperColors.OnSurfaceVariant
    }
    Box(
        modifier = Modifier
            .height(38.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(
                if (selected && enabled) {
                    Modifier.border(1.2.dp, PaperColors.Primary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                } else Modifier
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = PaperDimens.CaptionSp,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
fun <T> CompactChipGrid(
    items: List<T>,
    perRow: Int,
    label: (T) -> String,
    selected: (T) -> Boolean,
    enabled: (T) -> Boolean = { true },
    onClick: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    Box(Modifier.weight(1f)) {
                        CompactChip(
                            text = label(item),
                            selected = selected(item),
                            enabled = enabled(item),
                            onClick = { onClick(item) },
                        )
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 等宽网格 chip，保留给需要整齐对齐的场景 */
@Composable
fun <T> ChipGrid(
    items: List<T>,
    perRow: Int = 5,
    label: (T) -> String,
    selected: (T) -> Boolean,
    enabled: (T) -> Boolean = { true },
    onClick: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall)) {
        items.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall)) {
                row.forEach { item ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        ChoiceChip(
                            text = label(item),
                            selected = selected(item),
                            enabled = enabled(item),
                            onClick = { onClick(item) },
                        )
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun StepperRow(
    label: String,
    value: Int,
    hint: String = "",
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onMinusMore: () -> Unit = {},
    onPlusMore: () -> Unit = {},
    /** 非空时右侧多一个「移除这种题型」的红色叉 */
    onRemove: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontSize = PaperDimens.BodySp,
                fontWeight = FontWeight.Medium,
                color = PaperColors.OnSurface,
            )
            if (hint.isNotEmpty()) {
                Text(hint, fontSize = PaperDimens.TinySp, color = PaperColors.OnSurfaceVariant)
            }
        }
        StepChip("−5") { onMinusMore() }
        Spacer(Modifier.width(5.dp))
        SmallBtn("−") { onMinus() }
        Text(
            "$value",
            fontSize = PaperDimens.BodySp,
            fontWeight = FontWeight.Bold,
            color = PaperColors.Primary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        SmallBtn("＋") { onPlus() }
        Spacer(Modifier.width(5.dp))
        StepChip("+5") { onPlusMore() }
        if (onRemove != null) {
            Spacer(Modifier.width(7.dp))
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(PaperColors.ErrorContainer)
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "移除",
                    tint = PaperColors.Error,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** 大步进（±5）：低调胶囊，视觉上让位给 ±1 的主色圆钮 */
@Composable
private fun StepChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(50))
            .background(PaperColors.SurfaceAlt)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 11.sp,
            color = PaperColors.OnSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SmallBtn(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(PaperColors.PrimaryContainer)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = PaperColors.Primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 分段控件：视图切换用。
 *
 * 之前那版有两个坑，截图里就露馅了（选中块变成直角白块被灰框圈住）：
 *  1. `Row` 忘了 `fillMaxWidth()`，内部又用 `weight(1f)` —— 无界宽度下 weight 退化，
 *     子项尺寸算错，选中块的圆角裁切也跟着错位；
 *  2. `shadow` 叠在 `background` 之后，阴影那一层会按矩形重新绘制，把圆角盖成直角。
 * 现在：外层定宽 + 内层纯色对比（白 / 透明），不再用阴影，靠色差表达选中。
 */
@Composable
fun Segmented(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PaperColors.SurfaceAlt)
            .padding(3.dp),
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) PaperColors.Surface else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = PaperDimens.LabelSp,
                    color = if (on) PaperColors.Primary else PaperColors.OnSurfaceVariant,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun StatChip(
    text: String,
    color: Color = PaperColors.Primary,
    bg: Color = PaperColors.PrimaryContainer,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, fontSize = PaperDimens.TinySp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** 首页快捷预设：白卡 + 顶部渐变细条 + 投影 */
@Composable
fun PresetCard(
    title: String,
    desc: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .shadow(5.dp, Surface3.shape)
            .clip(Surface3.shape)
            .background(PaperColors.Surface)
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Box(
            Modifier
                .width(26.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.horizontalGradient(listOf(PaperColors.GradientStart, PaperColors.GradientEnd)))
        )
        Spacer(Modifier.height(9.dp))
        Text(
            title,
            fontSize = PaperDimens.BodySp,
            fontWeight = FontWeight.Bold,
            color = PaperColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            desc,
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 16.sp,
            // 描述统一占两行：一行与两行的卡片高度才一致，同一行里「N 题 · 出卷」才对得齐
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        StatChip(meta)
    }
}

/** 「更多」页的列表项 */
@Composable
fun ListRow(
    title: String,
    subtitle: String = "",
    trailing: String = "›",
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = PaperDimens.BodySp,
                fontWeight = FontWeight.Medium,
                color = PaperColors.OnSurface,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    fontSize = PaperDimens.CaptionSp,
                    color = PaperColors.OnSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Text(trailing, fontSize = 18.sp, color = PaperColors.Primary)
    }
}

@Composable
fun MiniAction(
    text: String,
    color: Color = PaperColors.Primary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        text,
        fontSize = PaperDimens.CaptionSp,
        color = if (enabled) color else PaperColors.Muted,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) PaperColors.PrimaryContainer else PaperColors.SurfaceAlt)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** 主按钮：渐变 + 投影，整屏唯一强动作 */
@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .height(50.dp)
            .then(if (enabled) Modifier.shadow(8.dp, shape) else Modifier)
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(PaperColors.GradientStart, PaperColors.GradientEnd))
                } else {
                    Brush.horizontalGradient(listOf(PaperColors.Muted, PaperColors.Muted))
                }
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = PaperDimens.BodySp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .height(50.dp)
            .clip(shape)
            .background(PaperColors.SurfaceAlt)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = PaperDimens.BodySp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) PaperColors.Primary else PaperColors.Muted,
            maxLines = 1,
        )
    }
}

/** 提示条：圆角浮起，不再是全宽米黄条 */
@Composable
fun ToastBar(text: String, onDismiss: () -> Unit = {}) {
    if (text.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PaperDimens.ScreenPad, vertical = 4.dp)
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(PaperColors.AccentContainer)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurface,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "×",
            fontSize = 18.sp,
            color = PaperColors.OnSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onDismiss() }
                .padding(horizontal = 4.dp),
        )
    }
}
