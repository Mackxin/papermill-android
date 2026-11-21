package com.papermill.app.domain.generate

import com.papermill.app.domain.content.RefBank

/**
 * 参考资料库驱动的语文题型（v0.29.0）—— 量词填空 / 写反义词。
 *
 * 对应网页版的 web/js/gen/refs.js，两端同一个数据源（assets/refs 下的 JSON，
 * 与 web/data/refs 下那份逐字节一致）。
 *
 * 注意：Kotlin 的块注释可以嵌套，这里**不能写**「refs 目录 + 斜杠 + 星号 + .json」
 * 那种路径通配写法 —— 里面的斜杠星号会再开一层注释，整个文件报 Unclosed comment。
 *
 * ============================================================
 * 为什么只有这两个题型
 * ============================================================
 * 判据只有一条：**答案唯一**。填空题只能印一个答案，印出别的就判错。
 *  · 量词：1,185 个名词里只有 4 个「一名多量」（烟／根·缕），导入时整批剔掉；
 *  · 反义词：只留单字对。双字对里「春天／秋天」「渐渐／逐渐」这类对义与近义
 *    占两成以上，已整批不要 —— 详见 tools/import-zx-refs.py 的 clean_antonyms。
 *
 * 数据现在还很小（量词 255 条、反义词 159 条），因为人工逐条核过一遍。
 * 宁可只有 400 条对的，也不要把 4,000 条一半错的印到卷子上。
 */

// ------------------------------------------------------------------ 量词填空

object ClassifierFillGenerator : BaseGenerator() {
    override val type = QuestionType.CLASSIFIER_FILL
    override val subject = Subject.CHINESE
    override val minGrade = 1
    // 五六年级不考量词了，硬塞进去只会把卷子拉成低年级水平
    override val maxGrade = 5
    override val topic = "量词搭配"

    override fun make(ctx: GenContext): Pair<String, String> {
        val pool = RefBank.classifiers.filter { it.grade <= ctx.grade }
        // 池子太小时直接放弃，别让同一卷里出现两个「一（　）书」
        check(pool.size >= 8) { "量词库不足" }
        val it = freshWord(ctx, "CLASSIFIER", pool) { it.noun }
        // 三栏排版单栏只有 ~161pt，「填量词：」四个字要留着，
        // 否则「一（　）书」孤零零一个短语，看不出是让填什么
        val stem = if (ctx.rng.nextInt(3) == 0) {
            "一（　）${it.noun}"
        } else {
            "填量词：一（　）${it.noun}"
        }
        return stem to it.classifier
    }
}

// ------------------------------------------------------------------ 写反义词

object AntonymGenerator : BaseGenerator() {
    override val type = QuestionType.ANTONYM
    override val subject = Subject.CHINESE
    override val minGrade = 1
    // 单字反义词是低年级的题；高年级该考双字（勤奋／懒惰），可那双字库质量不行
    override val maxGrade = 4
    override val topic = "反义词"

    override fun make(ctx: GenContext): Pair<String, String> {
        val pool = RefBank.antonyms.filter { it.grade <= ctx.grade }
        check(pool.size >= 8) { "反义词库不足" }
        val it = freshWord(ctx, "ANTONYM", pool) { it.a }
        val stem = if (ctx.rng.nextInt(2) == 0) {
            "反义词：${it.a}——（　）"
        } else {
            "写出反义词：${it.a}——（　）"
        }
        return stem to it.b
    }
}
