package com.papermill.app.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.papermill.app.core.design.PaperColors
import com.papermill.app.core.design.PaperDimens
import kotlin.math.abs
import kotlin.math.roundToInt

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
    /**
     * 首页专用：标题居中 + 主色。
     * 其它页面有返回键、标题也长（「速查表与打印纸」），居中会和返回键打架，保持左对齐黑字。
     */
    centered: Boolean = false,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(
                start = PaperDimens.ScreenPad,
                end = PaperDimens.ScreenPad,
                top = 10.dp,
                bottom = 12.dp,
            ),
    ) {
        if (onBack != null) {
            // 触摸区 48dp、视觉圆形 38dp：原来点击热区只有一个 38dp 的圆，边缘容易点空。
            // 另外背景必须画在阴影之后 —— 顺序反了阴影会按矩形重画，圆形按钮会露出方角。
            val backInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .clickable(
                        interactionSource = backInteraction,
                        indication = null,
                    ) { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(PaperColors.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    // 之前用一个「‹」字符充当箭头，字体里的字形在圆形里天然偏上偏右，
                    // 换成矢量图标，居中由 Icon 自己保证。
                    Icon(
                        Icons.Rounded.ChevronLeft,
                        contentDescription = "返回",
                        tint = PaperColors.OnSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                // 有返回键时把标题挤到按钮右侧，避免长标题压住圆形按钮
                .padding(start = if (onBack != null && !centered) 56.dp else 0.dp),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Text(
                title,
                fontSize = PaperDimens.TitleSp,
                fontWeight = FontWeight.Bold,
                color = if (centered) PaperColors.Primary else PaperColors.OnSurface,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    fontSize = PaperDimens.CaptionSp,
                    color = PaperColors.OnSurfaceVariant,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
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
            .padding(horizontal = PaperDimens.ScreenPad, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** dock 里四个页签的顺序与文字（顺序必须和 Screens.TAB_ORDER 一致） */
private val DOCK_TABS = listOf(
    "出卷" to NavTab.HOME,
    "打印纸" to NavTab.REFERENCE,
    "错题本" to NavTab.MISTAKES,
    "更多" to NavTab.MORE,
)

private val DockItemH = 42.dp

/**
 * 悬浮胶囊 dock：4 个页签，纯文字（不放图标），选中项淡紫胶囊 + 主色字。
 *
 * 用户明确要求「tab 栏不需要图标」——去掉图标后整条更矮更安静，
 * 选中态靠胶囊底色 + 字重变化表达，比图标+文字的双行更利落。
 *
 * v0.7.4 起做成**悬浮条**：整条盖在内容之上（不再占布局位）。
 * v0.8.0 起默认**不透明实底**，玻璃半透明改成「更多 → 设置」里的开关：
 * 半透明会透出下面滚动的卡片文字，和 tab 文字抢视线；
 * 而且 Android 没有能模糊「背后内容」的公开 API（Modifier.blur 只模糊自己），
 * 真玻璃效果凑不出来，只能靠低透明度假装，索性默认关掉、想要的人自己开。
 *
 * v0.10.1 修「胶囊不跟手」：原来胶囊的存在与否挂在一个**离散**状态上
 * （`navTab` / `pager.currentPage`，页面越过半页才变），再套一段 160ms 淡入。
 * 页面已经拖了一半，胶囊还钉在原位，过线那一下才「啪」地跳过去 —— 看着就像没动。
 * 现在改成由调用方喂**连续页位置**（`currentPage + currentPageOffsetFraction`），
 * 胶囊按 `slot * position` 平移：手指拖多少，它就走多少；松手后的回弹/自动翻页动画
 * 也会被同一个公式带着走，不再有第二种节奏。
 */
@Composable
fun FloatingDock(
    state: AppState,
    modifier: Modifier = Modifier,
    /** 连续页位置：0f 第一页、1.5f 在一二页正中间、3f 最后一页。拖动时逐帧变 */
    position: () -> Float,
    onSelect: (NavTab) -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    val glass = state.dockGlass
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(18.dp, shape)
            .clip(shape)
            .background(
                // 玻璃态：上亮下暗的极浅渐变；实底：同一块不透明 Surface
                if (glass) {
                    Brush.verticalGradient(
                        listOf(
                            PaperColors.Surface.copy(alpha = 0.92f),
                            PaperColors.Surface.copy(alpha = 0.80f),
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(PaperColors.Surface, PaperColors.Surface)
                    )
                }
            )
            .then(
                // 高光描边只在玻璃态有意义，实底时描边会显得脏
                if (glass) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.9f),
                                Color.White.copy(alpha = 0.2f),
                            )
                        ),
                        shape = shape,
                    )
                } else {
                    Modifier.border(1.dp, PaperColors.Outline, shape)
                }
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        val slot = maxWidth / DOCK_TABS.size

        // 滑块：位置放在 Modifier.offset 的 lambda 里读 —— 那个 lambda 跑在**布局阶段**，
        // 拖动时只有这个节点的位置被重算，不会把整条 dock（含 4 段文字）逐帧重组。
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((slot.toPx() * position()).roundToInt(), 0) }
                .width(slot)
                .height(DockItemH)
                .clip(RoundedCornerShape(18.dp))
                .background(PaperColors.PrimaryContainer)
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DOCK_TABS.forEachIndexed { i, (label, tab) ->
                DockItem(
                    label = label,
                    // 三角权重：离哪页最近就亮到几分。拖到两页正中间时两个都是 0.5、
                    // 胶囊正压在中间，眼睛读到的是「正在过去」而不是「已经过去了」。
                    emphasis = { (1f - abs(position() - i)).coerceIn(0f, 1f) },
                ) { onSelect(tab) }
            }
        }
    }
}

@Composable
private fun RowScope.DockItem(
    label: String,
    emphasis: () -> Float,
    onClick: () -> Unit,
) {
    // 不再自己画底：底色统一由上面那块跟着滑的胶囊负责，
    // 两层叠着画会在胶囊跨在两格中间时出现双层半透明，看着发脏。
    val t = emphasis()
    val fg = lerp(PaperColors.OnSurfaceVariant, PaperColors.Primary, t)
    // 字重只在过半后切一次，避免每帧换字体尺寸触发重新排版
    val weight = if (t > 0.5f) FontWeight.Bold else FontWeight.Normal
    // 关掉 ripple：用户明确要求去掉「高亮之前先闪一层灰底」。
    // 选中态胶囊本身就是点击反馈，再叠一层灰反而脏。
    val interaction = remember { MutableInteractionSource() }

    Box(
        Modifier
            .weight(1f)
            .height(DockItemH)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onClick() },
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
            // 按短语断行：「整体认读」不会被拆成「整体认 / 读」，「9×11 格」不会只剩一个「格」
            style = TextStyle(lineBreak = LineBreak.Paragraph),
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
            .height(46.dp)
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
            .height(46.dp)
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
