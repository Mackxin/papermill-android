package com.papermill.app.feature

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.papermill.app.core.design.PaperColors
import com.papermill.app.core.design.PaperDimens

// ------------------------------------------------------------------ 框架

/** 顶部只放「当前在哪」和一个可选返回键，版本号等次要信息收进「更多」页 */
@Composable
fun AppTopBar(
    title: String,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(PaperColors.Background)
            .padding(horizontal = PaperDimens.ScreenPad, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(PaperColors.Surface, RoundedCornerShape(9.dp))
                    .border(1.dp, PaperColors.Outline, RoundedCornerShape(9.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) { Text("‹", fontSize = 20.sp, color = PaperColors.Primary) }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = PaperDimens.TitleSp,
                fontWeight = FontWeight.SemiBold,
                color = PaperColors.OnSurface,
            )
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = PaperDimens.CaptionSp, color = PaperColors.OnSurfaceVariant)
            }
        }
    }
}

/** 底部固定主操作条：不管滚到哪一屏，「生成 / 打印」都在拇指够得到的位置 */
@Composable
fun StickyBar(content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(PaperColors.Outline))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = PaperDimens.ActionBarH)
                .background(PaperColors.Surface)
                .padding(horizontal = PaperDimens.ScreenPad, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(PaperDimens.GapSmall),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 4 个固定页签，替代上一版横向滚动的 5 个页签 */
@Composable
fun BottomNav(state: AppState) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(PaperColors.Outline))
        Row(
            Modifier
                .fillMaxWidth()
                .height(PaperDimens.NavH)
                .background(PaperColors.Surface),
        ) {
            NavItem("出卷", state.navTab == NavTab.HOME) { state.goTab(NavTab.HOME) }
            NavItem("速查表", state.navTab == NavTab.REFERENCE) { state.goTab(NavTab.REFERENCE) }
            NavItem("错题本", state.navTab == NavTab.MISTAKES) { state.goTab(NavTab.MISTAKES) }
            NavItem("更多", state.navTab == NavTab.MORE) { state.goTab(NavTab.MORE) }
        }
    }
}

@Composable
private fun RowScope.NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .width(22.dp)
                .height(3.dp)
                .background(
                    if (selected) PaperColors.Primary else Color.Transparent,
                    RoundedCornerShape(2.dp),
                )
        )
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            fontSize = PaperDimens.LabelSp,
            color = if (selected) PaperColors.Primary else PaperColors.OnSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ------------------------------------------------------------------ 基础件

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
            .background(PaperColors.Surface, RoundedCornerShape(PaperDimens.CardRadius))
            .border(1.dp, PaperColors.Outline, RoundedCornerShape(PaperDimens.CardRadius))
            .padding(PaperDimens.CardPad),
    ) {
        if (title.isNotEmpty()) {
            Text(
                title,
                fontSize = PaperDimens.SectionSp,
                fontWeight = FontWeight.SemiBold,
                color = PaperColors.OnSurface,
            )
        }
        if (hint.isNotEmpty()) {
            Text(
                hint,
                fontSize = PaperDimens.CaptionSp,
                color = PaperColors.OnSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (title.isNotEmpty() || hint.isNotEmpty()) Spacer(Modifier.height(10.dp))
        content()
    }
}

/** 分区标题：序号圆角块 + 标题 + 右侧说明，替代「① 选择年级」这种纯文字 */
@Composable
fun SectionLabel(no: String, text: String, right: String = "") {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .background(PaperColors.PrimaryContainer, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(no, fontSize = 10.sp, color = PaperColors.Primary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = PaperDimens.SectionSp,
            fontWeight = FontWeight.SemiBold,
            color = PaperColors.OnSurface,
        )
        Spacer(Modifier.weight(1f))
        if (right.isNotEmpty()) {
            Text(right, fontSize = PaperDimens.CaptionSp, color = PaperColors.OnSurfaceVariant)
        }
    }
}

@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> PaperColors.SurfaceAlt
        selected -> PaperColors.Primary
        else -> PaperColors.Surface
    }
    val fg = when {
        !enabled -> PaperColors.Muted
        selected -> Color.White
        else -> PaperColors.OnSurface
    }
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .background(bg, RoundedCornerShape(PaperDimens.ChipRadius))
            .border(
                1.dp,
                if (selected && enabled) PaperColors.Primary else PaperColors.Outline,
                RoundedCornerShape(PaperDimens.ChipRadius),
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = PaperDimens.LabelSp, color = fg)
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
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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

/** 紧凑 chip：9 个年级分两行就能全部显示，不用横滑也不会裁半个字 */
@Composable
fun CompactChip(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> PaperColors.SurfaceAlt
        selected -> PaperColors.Primary
        else -> PaperColors.Surface
    }
    val fg = when {
        !enabled -> PaperColors.Muted
        selected -> Color.White
        else -> PaperColors.OnSurface
    }
    Box(
        modifier = Modifier
            .height(34.dp)
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(9.dp))
            .border(
                1.dp,
                if (selected && enabled) PaperColors.Primary else PaperColors.Outline,
                RoundedCornerShape(9.dp),
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 5.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = PaperDimens.CaptionSp, color = fg, maxLines = 1)
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = PaperDimens.BodySp, color = PaperColors.OnSurface)
            if (hint.isNotEmpty()) {
                Text(hint, fontSize = PaperDimens.TinySp, color = PaperColors.OnSurfaceVariant)
            }
        }
        SmallBtn("－5", 7) { onMinusMore() }
        SmallBtn("－", 9) { onMinus() }
        Text(
            "$value",
            fontSize = PaperDimens.BodySp,
            fontWeight = FontWeight.SemiBold,
            color = PaperColors.OnSurface,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        SmallBtn("＋", 9) { onPlus() }
        SmallBtn("＋5", 7) { onPlusMore() }
    }
}

@Composable
private fun SmallBtn(text: String, padH: Int = 12, onClick: () -> Unit) {
    Box(
        Modifier
            .background(PaperColors.PrimaryContainer, RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = padH.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = PaperDimens.LabelSp,
            color = PaperColors.Primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 分段控件：视图切换用，比一排 chip 更省地方 */
@Composable
fun Segmented(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .background(PaperColors.SurfaceAlt, RoundedCornerShape(10.dp))
            .border(1.dp, PaperColors.Outline, RoundedCornerShape(10.dp))
            .padding(3.dp),
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .background(
                        if (on) PaperColors.Surface else Color.Transparent,
                        RoundedCornerShape(8.dp),
                    )
                    .then(
                        if (on) Modifier.border(1.dp, PaperColors.Outline, RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = PaperDimens.LabelSp,
                    color = if (on) PaperColors.Primary else PaperColors.OnSurfaceVariant,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun StatChip(text: String, color: Color = PaperColors.Primary, bg: Color = PaperColors.PrimaryContainer) {
    Box(
        Modifier
            .background(bg, RoundedCornerShape(7.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(text, fontSize = PaperDimens.TinySp, color = color, fontWeight = FontWeight.Medium)
    }
}

/** 首页快捷预设：2 列大卡片，点一下直接出卷 */
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
            .background(PaperColors.Surface, RoundedCornerShape(PaperDimens.CardRadius))
            .border(1.dp, PaperColors.Outline, RoundedCornerShape(PaperDimens.CardRadius))
            .clickable { onClick() }
            .padding(10.dp),
    ) {
        Text(title, fontSize = PaperDimens.BodySp, fontWeight = FontWeight.SemiBold, color = PaperColors.OnSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            desc,
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurfaceVariant,
            lineHeight = 15.sp,
            maxLines = 2,
        )
        Spacer(Modifier.height(8.dp))
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
            .clickable { onClick() }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = PaperDimens.BodySp, color = PaperColors.OnSurface)
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
        modifier = Modifier
            .background(PaperColors.PrimaryContainer, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PaperColors.Primary),
        modifier = modifier.height(46.dp),
    ) {
        Text(text, fontSize = PaperDimens.BodySp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(46.dp),
    ) {
        Text(text, fontSize = PaperDimens.BodySp, color = PaperColors.Primary)
    }
}

/** 固定显示在操作条上方，不再埋在滚动内容的末尾 */
@Composable
fun ToastBar(text: String, onDismiss: () -> Unit = {}) {
    if (text.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .background(PaperColors.AccentContainer)
            .padding(horizontal = PaperDimens.ScreenPad, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = PaperDimens.CaptionSp,
            color = PaperColors.OnSurface,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "×",
            fontSize = 18.sp,
            color = PaperColors.OnSurfaceVariant,
            modifier = Modifier.clickable { onDismiss() },
        )
    }
}
