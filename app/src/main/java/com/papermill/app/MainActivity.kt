package com.papermill.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.papermill.app.core.design.PaperMillTheme
import com.papermill.app.domain.content.RefBank
import com.papermill.app.feature.AppState
import com.papermill.app.feature.PaperMillRoot

/**
 * 单 Activity 入口。
 * 所有页面（出卷 / 参数 / 预览 / 错题本 / 试卷库 / 打印帮助）都在 Compose 里切换。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 参考资料库（3,652 词表 / 873 英语单词）先在后台读：
        // 出卷时会 ensure 同步等它，这里只是把那几十毫秒挪到用户还没点「生成」之前。
        // 放在 debug 体检分支之前，几种 dump 出来的卷子才会跟正式出卷用同一套词表。
        RefBank.warmUp(this)
        // 纸张批量体检（仅 debug）：不解 UI，直接把所有内置纸张渲成 PNG 落盘
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("dumpSheets", false) == true) {
            DebugSheetDump.dump(this)
            return
        }
        // 题型抽样：核对题干与答案是否配套（选择题选项洗牌后答案字母最容易错位）
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("dumpQuestions", false) == true) {
            DebugSheetDump.dumpQuestions(this)
            return
        }
        // 真卷体检：按课标题库出一张卷，渲成 PNG 看排版
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("dumpPaper", false) == true) {
            DebugSheetDump.dumpPaper(this)
            return
        }
        // 配比体检：倾向 × 用途 × 年级 × 科目 全组合，只打题型与题量
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("dumpRatios", false) == true) {
            DebugSheetDump.dumpRatios(this)
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
