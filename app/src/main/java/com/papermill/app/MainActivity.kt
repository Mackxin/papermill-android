package com.papermill.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.papermill.app.core.design.PaperMillTheme
import com.papermill.app.feature.AppState
import com.papermill.app.feature.PaperMillRoot

/**
 * 单 Activity 入口。
 * 所有页面（出卷 / 参数 / 预览 / 错题本 / 试卷库 / 打印帮助）都在 Compose 里切换。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 纸张批量体检（仅 debug）：不解 UI，直接把所有内置纸张渲成 PNG 落盘
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("dumpSheets", false) == true) {
            DebugSheetDump.dump(this)
            return
        }
        setContent {
            PaperMillTheme {
                val state = remember { AppState(this@MainActivity) }
                PaperMillRoot(state)
            }
        }
    }
}
