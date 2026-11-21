package com.papermill.app.domain.content

import android.content.Context
import com.papermill.app.domain.generate.CnWord
import com.papermill.app.domain.generate.EnWord
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 一条量词搭配（v0.29.0 量词填空题）。
 *
 * [grade] = 名词与量词两侧里最难的那个字在几年级学。学生要同时认出名词、
 * 写出量词，所以按它过滤，而不是另编一套难度。
 */
data class NounClassifier(val noun: String, val classifier: String, val grade: Int)

/**
 * 一对反义词（v0.29.0 写反义词题）。
 *
 * 只有**单字对**（上／下、大／小），而且一字只对应一个反义词 ——
 * 双字对（春天／秋天、渐渐／逐渐）里对义与近义占两成以上，已由导入脚本整批剔除。
 */
data class AntonymPair(val a: String, val b: String, val grade: Int)

/**
 * 参考资料库（v0.28.0）—— 词表 / 英语单词 / 量词 / 反义词，来自 zx-study（MIT）的 ref_* 表。
 *
 * ============================================================
 * 为什么放在 assets 里，不写成 Kotlin 字面量
 * ============================================================
 * 3,652 个中文词 + 873 个英语单词。写成 `listOf("a" to 1, …)` 会有两个问题：
 *  1. 单个方法的字节码会被撑到 JVM 上限（64KB），`Method too large` 直接编译不过；
 *  2. 网页版要用的还是同一批数据 —— 抄一份到 JS 里必然会漂，而本项目的规矩是
 *     「同一份 spec + 同一个 seed，两端出同一张卷」。
 * 所以数据只存一份 JSON，web/ 与 app/assets 各放一份**逐字节相同**的文件
 * （由 tools/import-zx-refs.py 生成，md5 一致），两端各自读自己那份。
 *
 * ============================================================
 * 加载时机
 * ============================================================
 * [WordBank] 的取词函数是纯同步的，所以这里数据必须在**第一次出卷之前**就绪。
 * 两处触发，谁先到算谁：
 *  · MainActivity 启动时 [warmUp] 在后台线程读一次（不拖慢冷启动）；
 *  · AppState 每次出卷前 [ensure] 同步等一下（出卷本来就在 IO 线程上）。
 * 两份都便宜：cn_words 约 180 KB，org.json 解析几十毫秒。
 */
object RefBank {

    private val started = AtomicBoolean(false)
    private val lock = Any()

    @Volatile
    var ready: Boolean = false
        private set

    /** 中文词表（按年级、按词排序，与网页版同一个顺序） */
    @Volatile
    var cnWords: List<CnWord> = emptyList()
        private set

    @Volatile
    var enWords: List<EnWord> = emptyList()
        private set

    /** 名词 → 量词（「上衣」→「件」），量词搭配速查表与量词题用 */
    @Volatile
    var classifiers: List<NounClassifier> = emptyList()
        private set

    /** 反义词单字对（「高」→「矮」），写反义词题用 */
    @Volatile
    var antonyms: List<AntonymPair> = emptyList()
        private set

    /**
     * 后台预加载。重复调用无害；文件缺失（精简安装包、旧版本升级）只当作空库，
     * 词表退回 [WordBank] 里手写的那八十几个，不会崩。
     */
    fun warmUp(ctx: Context) {
        if (!started.compareAndSet(false, true)) return
        Thread {
            load(ctx)
        }.apply { isDaemon = true; name = "ref-bank" }.start()
    }

    /** 同步确保就绪。已经在读就读完再返回（同一个 lock，不会重复解析）。 */
    fun ensure(ctx: Context) {
        if (ready) return
        warmUp(ctx)
        synchronized(lock) {
            if (ready) return
            load(ctx)
        }
    }

    private fun load(ctx: Context) {
        synchronized(lock) {
            if (ready) return
            val app = ctx.applicationContext
            val cn = readArray(app, "refs/cn_words.json")
            cnWords = (0 until cn.length()).mapNotNull { i ->
                val o = cn.optJSONObject(i) ?: return@mapNotNull null
                val w = o.optString("word")
                val p = o.optString("pinyin")
                if (w.isEmpty() || p.isEmpty()) return@mapNotNull null
                CnWord(w, p, o.optInt("grade", 3).coerceIn(1, 9))
            }
            val en = readArray(app, "refs/en_words.json")
            enWords = (0 until en.length()).mapNotNull { i ->
                val o = en.optJSONObject(i) ?: return@mapNotNull null
                val w = o.optString("en")
                val zh = o.optString("zh")
                if (w.isEmpty() || zh.isEmpty()) return@mapNotNull null
                EnWord(w, zh, o.optInt("grade", 3).coerceIn(1, 9))
            }
            val cl = readArray(app, "refs/classifiers.json")
            classifiers = (0 until cl.length()).mapNotNull { i ->
                val o = cl.optJSONObject(i) ?: return@mapNotNull null
                val n = o.optString("noun")
                val c = o.optString("classifier")
                if (n.isEmpty() || c.isEmpty()) null
                else NounClassifier(n, c, o.optInt("grade", 3).coerceIn(1, 9))
            }
            val an = readArray(app, "refs/antonyms.json")
            antonyms = (0 until an.length()).mapNotNull { i ->
                val o = an.optJSONObject(i) ?: return@mapNotNull null
                val a = o.optString("a")
                val b = o.optString("b")
                if (a.isEmpty() || b.isEmpty()) null
                else AntonymPair(a, b, o.optInt("grade", 3).coerceIn(1, 9))
            }
            ready = true
        }
    }

    private fun readArray(ctx: Context, path: String): JSONArray = try {
        val raw = ctx.assets.open(path).bufferedReader().use { it.readText() }
        JSONArray(raw)
    } catch (e: Throwable) {
        // 缺文件不算错误：词表就退回手写的那批，别让出卷整个失败
        JSONArray()
    }
}
