package com.papermill.app.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.papermill.app.core.design.PaperColors

@Composable
fun AppHeader(state: AppState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("卷子", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = PaperColors.OnSurface)
            Text(
                "  ·  自己配卷，一键打印",
                fontSize = 12.sp,
                color = PaperColors.OnSurfaceVariant,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TabBtn("出卷", state.screen == Screen.HOME) { state.screen = Screen.HOME }
            TabBtn("错题本", state.screen == Screen.MISTAKES) { state.screen = Screen.MISTAKES }
            TabBtn("试卷库", state.screen == Screen.HISTORY) {
                state.refreshLists(); state.screen = Screen.HISTORY
            }
            TabBtn("打印帮助", state.screen == Screen.HELP) { state.screen = Screen.HELP }
        }
    }
}

@Composable
private fun TabBtn(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) PaperColors.Primary else PaperColors.Surface)
            .border(1.dp, if (selected) PaperColors.Primary else PaperColors.Outline, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (selected) Color.White else PaperColors.OnSurfaceVariant,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
fun CardSection(
    title: String,
    hint: String = "",
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(PaperColors.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, PaperColors.Outline, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = PaperColors.OnSurface)
        if (hint.isNotEmpty()) {
            Text(hint, fontSize = 11.sp, color = PaperColors.OnSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
        content()
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
        !enabled -> Color(0xFFF0EFEC)
        selected -> PaperColors.Primary
        else -> PaperColors.Surface
    }
    val fg = when {
        !enabled -> Color(0xFFB5B3AE)
        selected -> Color.White
        else -> PaperColors.OnSurface
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(20.dp))
            .border(1.dp, if (selected && enabled) PaperColors.Primary else PaperColors.Outline, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 9.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 12.sp, color = fg)
    }
}

/** 简易流式排布：按每行 [perRow] 个切成 Row，避开 FlowRow 的实验性 API */
@Composable
fun <T> ChipGrid(
    items: List<T>,
    perRow: Int = 5,
    label: (T) -> String,
    selected: (T) -> Boolean,
    enabled: (T) -> Boolean = { true },
    onClick: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(perRow).forEach { row ->
            // 每个 chip 占等宽的一格（而不是按文字宽度挤），否则最后一个会被压到换行，
            // 例如「五年级」会变成「五年 / 级」两行。
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                // 最后一行不足 perRow 个时用空位补齐，保持列宽一致
                repeat(perRow - row.size) {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
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
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = PaperColors.OnSurface)
            if (hint.isNotEmpty()) Text(hint, fontSize = 10.sp, color = PaperColors.OnSurfaceVariant)
        }
        SmallBtn("－") { onMinus() }
        Text(
            "$value",
            fontSize = 14.sp,
            color = PaperColors.OnSurface,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        SmallBtn("＋") { onPlus() }
    }
}

@Composable
private fun SmallBtn(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(PaperColors.PrimaryContainer, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 15.sp, color = PaperColors.Primary, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PaperColors.Primary),
        modifier = Modifier.fillMaxWidth().height(46.dp),
    ) {
        Text(text, fontSize = 14.sp)
    }
}

@Composable
fun SecondaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(46.dp),
    ) {
        Text(text, fontSize = 14.sp, color = PaperColors.Primary)
    }
}

@Composable
fun ToastBar(text: String) {
    if (text.isEmpty()) return
    Box(
        Modifier
            .fillMaxWidth()
            .background(PaperColors.PrimaryContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(text, fontSize = 12.sp, color = PaperColors.OnSurface, lineHeight = 17.sp)
    }
}
