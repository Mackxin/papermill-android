package com.papermill.app.domain.profile

import com.papermill.app.domain.generate.GradeProfiles
import com.papermill.app.domain.generate.Subject
import com.papermill.app.domain.generate.Term

/**
 * 学生档案（v0.19.0）。
 *
 * ============================================================
 * 为什么要档案，而不是「年级 + 科目」两个 chip
 * ============================================================
 * 一个家里往往两三个孩子、各自年级不同、还在不同学期。原来的参数页只记「当前年级」，
 * 每次换孩子都要重新点一遍年级、科目、题型；更麻烦的是「这学期学的是哪册」——
 * 课标题库里上册下册是分开的，选错了抽出来的就是还没学的单元。
 *
 * 档案把这几件事钉在一起：点一下名字 = 年级 + 学期 + 科目一次就位，直接出卷。
 *
 * 只存姓名、年级、学期、性别、科目 —— 不收集生日、学校、班级这些信息，
 * 数据也只留在本机（`students.json`），不上传、不联网。
 */
data class Student(
    val id: String,
    val name: String,
    val grade: Int = 3,
    /** 1 上册 / 2 下册 / 0 全学年 */
    val term: Int = Term.FIRST,
    val gender: Gender = Gender.UNSET,
    /** 常用科目：点名字时一起切过去 */
    val subjects: List<Subject> = listOf(Subject.MATH),
    val note: String = "",
) {
    /** 「三年级 · 上册」；学前没有学期，只写「学前」 */
    val gradeLabel: String
        get() = if (grade == 0) GradeProfiles.gradeLabel(0)
        else "${GradeProfiles.gradeLabel(grade)} · ${Term.label(term)}"

    /** 卡片右上角的短标签：「三上」 */
    val shortLabel: String get() = Term.short(grade, term)

    val subjectLabel: String get() = subjects.joinToString("+") { it.label }

    /** 头像用的那个字：取姓名第一个字，够用又不占地方 */
    val initial: String get() = name.trim().take(1).ifEmpty { "学" }
}

enum class Gender(val label: String) {
    MALE("男"),
    FEMALE("女"),
    UNSET("未填"),
    ;

    companion object {
        fun of(name: String?): Gender = entries.firstOrNull { it.name == name } ?: UNSET
    }
}
