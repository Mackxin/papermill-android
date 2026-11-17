import com.papermill.app.domain.content.Grammar
import com.papermill.app.domain.content.Idioms
import com.papermill.app.domain.content.Phonics
import com.papermill.app.domain.content.Poems
import com.papermill.app.domain.content.Textbook
import com.papermill.app.domain.generate.ThemeBank
import com.papermill.app.domain.reference.PracticeData
import com.papermill.app.domain.reference.ReferenceData
import java.io.File

/**
 * 把 Kotlin 里的内置内容一次性导成 JSON，给网页版复用。
 *
 * 为什么不手抄：Grammar / Idioms / Poems / Phonics / Textbook / ThemeWords
 * 加起来一千多条中文数据，抄一遍必然出错字 —— 而这些内容是要印给孩子看的。
 * 直接编译原始 Kotlin 跑一遍，出来的一定和 App 里的一模一样。
 */

private fun sb(): StringBuilder = StringBuilder()

private fun StringBuilder.q(s: String?): StringBuilder {
    if (s == null) { append("null"); return this }
    append('"')
    for (ch in s) {
        when (ch) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
        }
    }
    append('"')
    return this
}

private fun StringBuilder.strArr(items: List<String>): StringBuilder {
    append('[')
    items.forEachIndexed { i, s -> if (i > 0) append(','); q(s) }
    append(']')
    return this
}

private fun StringBuilder.intArr(items: List<Int>): StringBuilder {
    append('['); append(items.joinToString(",")); append(']')
    return this
}

private fun StringBuilder.floatArr(items: List<Float>): StringBuilder {
    append('['); append(items.joinToString(",")); append(']')
    return this
}

private fun StringBuilder.pairs(items: List<Pair<String, String>>): StringBuilder {
    append('[')
    items.forEachIndexed { i, p ->
        if (i > 0) append(',')
        append('['); q(p.first); append(','); q(p.second); append(']')
    }
    append(']')
    return this
}

fun main(args: Array<String>) {
    val out = sb()
    out.append("{\n")

    // ---------------- 语法 ----------------
    out.append("\"grammar\":{\"points\":[")
    Grammar.points.forEachIndexed { i, p ->
        if (i > 0) out.append(',')
        out.append("{\"name\":"); out.q(p.name)
        out.append(",\"rule\":"); out.q(p.rule)
        out.append(",\"example\":"); out.q(p.example)
        out.append(",\"gradeFrom\":"); out.append(p.gradeFrom)
        out.append('}')
    }
    out.append("],\"items\":[")
    Grammar.items.forEachIndexed { i, it ->
        if (i > 0) out.append(',')
        out.append("{\"point\":"); out.q(it.point)
        out.append(",\"gradeFrom\":"); out.append(it.gradeFrom)
        out.append(",\"stem\":"); out.q(it.stem)
        out.append(",\"options\":"); out.strArr(it.options)
        out.append(",\"answer\":"); out.q(it.answer)
        out.append(",\"explain\":"); out.q(it.explain)
        out.append('}')
    }
    out.append("]},\n")

    // ---------------- 音标 ----------------
    out.append("\"phonics\":{\"kindOrder\":[")
    Phonics.kindOrder.forEachIndexed { i, k ->
        if (i > 0) out.append(',')
        out.append("{\"key\":"); out.q(k.name); out.append(",\"label\":"); out.q(k.label)
        out.append(",\"vowel\":"); out.append(if (k.isVowel) "true" else "false")
        out.append('}')
    }
    out.append("],\"all\":[")
    Phonics.all.forEachIndexed { i, p ->
        if (i > 0) out.append(',')
        out.append("{\"ipa\":"); out.q(p.ipa)
        out.append(",\"kind\":"); out.q(p.kind.name)
        out.append(",\"examples\":"); out.strArr(p.examples)
        out.append(",\"zh\":"); out.q(p.zh)
        out.append(",\"hint\":"); out.q(p.hint)
        out.append('}')
    }
    out.append("]},\n")

    // ---------------- 成语 ----------------
    out.append("\"idioms\":[")
    Idioms.items.forEachIndexed { i, it ->
        if (i > 0) out.append(',')
        out.append("{\"word\":"); out.q(it.word)
        out.append(",\"meaning\":"); out.q(it.meaning)
        out.append(",\"tag\":"); out.q(it.tag)
        out.append(",\"grade\":"); out.append(it.grade)
        out.append('}')
    }
    out.append("],\n")

    // ---------------- 古诗 ----------------
    out.append("\"poems\":[")
    Poems.items.forEachIndexed { i, p ->
        if (i > 0) out.append(',')
        out.append("{\"title\":"); out.q(p.title)
        out.append(",\"author\":"); out.q(p.author)
        out.append(",\"dynasty\":"); out.q(p.dynasty)
        out.append(",\"grade\":"); out.append(p.grade)
        out.append(",\"lines\":"); out.strArr(p.lines)
        out.append('}')
    }
    out.append("],\n")

    // ---------------- 课本同步 ----------------
    out.append("\"textbook\":{\"hanzi\":[")
    Textbook.hanzi.forEachIndexed { i, it ->
        if (i > 0) out.append(',')
        out.append("{\"hanzi\":"); out.q(it.hanzi)
        out.append(",\"pinyin\":"); out.q(it.pinyin)
        out.append(",\"grade\":"); out.append(it.grade)
        out.append('}')
    }
    out.append("],\"words\":[")
    Textbook.words.forEachIndexed { i, it ->
        if (i > 0) out.append(',')
        out.append("{\"en\":"); out.q(it.en)
        out.append(",\"zh\":"); out.q(it.zh)
        out.append(",\"grade\":"); out.append(it.grade)
        out.append('}')
    }
    out.append("],\"mathPoints\":[")
    Textbook.mathPoints.forEachIndexed { i, it ->
        if (i > 0) out.append(',')
        out.append("{\"point\":"); out.q(it.point)
        out.append(",\"grade\":"); out.append(it.grade)
        out.append('}')
    }
    out.append("]},\n")

    // ---------------- 主题识字 ----------------
    out.append("\"themes\":[")
    ThemeBank.themes.forEachIndexed { i, t ->
        if (i > 0) out.append(',')
        out.append("{\"id\":"); out.q(t.id)
        out.append(",\"name\":"); out.q(t.name)
        out.append(",\"person\":"); out.pairs(t.person)
        out.append(",\"item\":"); out.pairs(t.item)
        out.append(",\"place\":"); out.pairs(t.place)
        out.append('}')
    }
    out.append("],\n")

    // ---------------- 内置打印纸（表格） ----------------
    out.append("\"references\":[")
    ReferenceData.sheets.forEachIndexed { i, s ->
        if (i > 0) out.append(',')
        out.append("{\"id\":"); out.q(s.id)
        out.append(",\"title\":"); out.q(s.title)
        out.append(",\"subtitle\":"); out.q(s.subtitle)
        out.append(",\"group\":"); out.q(s.group)
        out.append(",\"table\":{\"headers\":"); out.strArr(s.table.headers)
        out.append(",\"weights\":"); out.floatArr(s.table.weights)
        out.append(",\"rows\":[")
        s.table.rows.forEachIndexed { r, row ->
            if (r > 0) out.append(',')
            out.strArr(row)
        }
        out.append("]}}")
    }
    out.append("],\n")

    // ---------------- 练习纸（格纸） ----------------
    out.append("\"practice\":[")
    PracticeData.sheets.forEachIndexed { i, s ->
        if (i > 0) out.append(',')
        out.append("{\"id\":"); out.q(s.id)
        out.append(",\"title\":"); out.q(s.title)
        out.append(",\"subtitle\":"); out.q(s.subtitle)
        out.append(",\"group\":"); out.q(s.group)
        out.append(",\"orientation\":"); out.q(s.orientation.name)
        out.append(",\"footer\":"); out.q(s.footer)
        out.append(",\"grid\":{\"style\":"); out.q(s.grid.style.name)
        out.append(",\"styleLabel\":"); out.q(s.grid.style.label)
        out.append(",\"cell\":"); out.append(s.grid.cell)
        out.append(",\"hint\":"); out.q(s.grid.hint)
        out.append(",\"themeCards\":"); out.pairs(s.grid.themeCards)
        out.append(",\"showThemeWords\":"); out.append(s.grid.showThemeWords)
        out.append("}}")
    }
    out.append("]\n}\n")

    val target = if (args.isNotEmpty()) File(args[0]) else File("content.json")
    target.parentFile?.mkdirs()
    target.writeText(out.toString())
    println("wrote ${target.absolutePath} (${target.length() / 1024}KB)")
    println("grammar=${Grammar.points.size}/${Grammar.items.size} phonemes=${Phonics.all.size} " +
        "idioms=${Idioms.items.size} poems=${Poems.items.size} hanzi=${Textbook.hanzi.size} " +
        "words=${Textbook.words.size} themes=${ThemeBank.themes.size} " +
        "references=${ReferenceData.sheets.size} practice=${PracticeData.sheets.size}")
}
