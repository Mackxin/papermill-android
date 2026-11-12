package com.papermill.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.papermill.app.core.design.PaperColors
import com.papermill.app.core.design.PaperMillTheme
import com.papermill.app.core.export.PdfExporter
import com.papermill.app.core.export.PdfSharer
import com.papermill.app.core.pdf.render.PaperRenderer
import com.papermill.app.core.pdf.render.SamplePaper
import com.papermill.app.core.print.PrintLauncher
import com.papermill.app.core.print.PrintServiceProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M0 验证页。
 *
 * 只有三个动作：生成 PDF / 系统打印 / 分享。
 * 跑通之后这些逻辑会被搬到真实的出卷流里，这个页面随即删掉。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaperMillTheme { M0Screen() }
        }
    }
}

@Composable
private fun M0Screen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("点下面任一按钮开始验证。") }
    var busy by remember { mutableStateOf(false) }
    val hasPrintService = remember { PrintServiceProbe.hasService(context) }

    fun launchTask(label: String, task: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            status = "$label…"
            status = try {
                task()
            } catch (t: Throwable) {
                "失败：${t.message ?: t.javaClass.simpleName}"
            }
            busy = false
        }
    }

    fun renderPdf() = PaperRenderer.render(SamplePaper.build())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("卷子", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = PaperColors.OnSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            "M0 闭环验证：一张写死的三年级口算卷 → A4 PDF → 系统打印 / 分享",
            fontSize = 13.sp,
            color = PaperColors.OnSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        if (!hasPrintService) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaperColors.PrimaryContainer, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Text("未检测到系统打印服务", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "装「奔图打印」App，或在 系统设置 → 连接与共享 → 打印 里启用 Mopria。\n" +
                        "（探测可能误判，仍可直接点「系统打印」试试）",
                    fontSize = 12.sp,
                    color = PaperColors.OnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { PrintServiceProbe.openSettings(context) }) {
                    Text("打开打印设置", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = {
                launchTask("生成 PDF") {
                    val pdf = withContext(Dispatchers.IO) { renderPdf() }
                    val file = withContext(Dispatchers.IO) {
                        PdfExporter.write(context, pdf.fileName, pdf.bytes)
                    }
                    "已生成 ${pdf.pageCount} 页 · ${pdf.bytes.size / 1024} KB\n${file.absolutePath}"
                }
            },
            enabled = !busy,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PaperColors.Primary),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text("生成并保存 PDF", fontSize = 15.sp)
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                val act = activity
                if (act == null) {
                    status = "拿不到 Activity，无法调起打印"
                } else {
                    launchTask("调起系统打印") {
                        val pdf = withContext(Dispatchers.IO) { renderPdf() }
                        PrintLauncher.print(act, "口算练习", pdf)
                        "已调起系统打印框：选「奔图 M1」，或选「保存为 PDF」验证排版。\n共 ${pdf.pageCount} 页"
                    }
                }
            },
            enabled = !busy,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PaperColors.Primary),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text("系统打印（局域网 · 奔图 M1）", fontSize = 15.sp)
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                launchTask("准备分享") {
                    val pdf = withContext(Dispatchers.IO) { renderPdf() }
                    val file = withContext(Dispatchers.IO) {
                        PdfExporter.write(context, pdf.fileName, pdf.bytes)
                    }
                    PdfSharer.share(context, file, "口算练习")
                    "已打开分享面板。发到微信 → 用「奔图云打印」小程序上传，即可异地打印。"
                }
            },
            enabled = !busy,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text("导出 PDF 并分享（远程打印用）", fontSize = 15.sp)
        }

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PaperColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, PaperColors.Outline, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("状态", fontSize = 12.sp, color = PaperColors.OnSurfaceVariant)
            Text(status, fontSize = 13.sp, color = PaperColors.OnSurface, lineHeight = 19.sp)
        }

        Spacer(Modifier.height(24.dp))
    }
}
