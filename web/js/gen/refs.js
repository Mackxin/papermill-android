/**
 * 参考资料库驱动的语文题型（v0.29.0）—— 量词填空 / 写反义词。
 *
 * 对应 Kotlin 的 RefGenerators.kt。数据来自 zx-study（MIT）的 ref_classifier /
 * ref_antonym 两张表，由 tools/import-zx-refs.py 导成 refs/*.json，
 * 网页与 App 两端读同一份文件（md5 一致）。
 *
 * ============================================================
 * 为什么这两个题型能做，歇后语却做不了
 * ============================================================
 * 判据只有一条：**答案唯一**。填空题只能印一个答案，印错了就是坏题。
 *  · 量词：1,185 个名词里只有 4 个「一名多量」（烟／根·缕），导入时整批剔掉；
 *  · 反义词：一词多反义的（高→低/矮）和反向不唯一的（亮→暗，可「暗」还有「明」）
 *    也在导入时剔掉 —— 学生写的那一个其实对，判他错就是卷子的问题；
 *  · 歇后语：答案天生不唯一（做贼的说梦话——想偷／做贼心虚），而且 zx 那张表
 *    是民间大全，抽样 45 条就有 2 处错别字，所以这一版不印。详见导入脚本注释。
 *
 * ============================================================
 * 年级怎么定
 * ============================================================
 * 每条数据都带 grade =「两侧里最难的那个字在几年级学」。学生要同时认出名词、
 * 写出量词，所以按 grade 过滤，而不是按题目难度另编一套。
 */

import { Subject } from '../core/models.js';
import { DATA } from '../data/store.js';
import { nextFreshWord } from './language.js';

// ------------------------------------------------------------------ 量词填空

export const classifierGenerator = {
  type: 'CLASSIFIER_FILL',
  subject: Subject.CHINESE,
  minGrade: 1,
  // 五六年级不考量词了，硬塞进去只会把卷子拉成低年级水平
  maxGrade: 5,
  topic: '量词搭配',
  make(ctx) {
    const pool = (DATA.refs?.classifiers ?? []).filter((it) => (it.grade ?? 3) <= ctx.grade);
    // 池子太小时直接放弃，别让同一卷里出现两个「一（　）书」
    if (pool.length < 8) return null;

    const it = nextFreshWord(ctx, pool, (w) => w.noun);
    if (!it) return null;

    // 三栏排版单栏只有 ~161pt，「填量词：」四个字要留着，
    // 否则「一（　）书」孤零零一个短语，看不出是让填什么
    const stem = ctx.rng.nextInt(3) === 0
      ? `一（　）${it.noun}`
      : `填量词：一（　）${it.noun}`;
    return [stem, it.classifier];
  },
};

// ------------------------------------------------------------------ 写反义词

export const antonymGenerator = {
  type: 'ANTONYM',
  subject: Subject.CHINESE,
  minGrade: 1,
  // 单字反义词是低年级的题；高年级该考双字（勤奋／懒惰），可那双字库质量不行
  maxGrade: 4,
  topic: '反义词',
  make(ctx) {
    const pool = (DATA.refs?.antonyms ?? []).filter((it) => (it.grade ?? 3) <= ctx.grade);
    if (pool.length < 8) return null;

    const it = nextFreshWord(ctx, pool, (w) => w.a);
    if (!it) return null;

    const stem = ctx.rng.nextInt(2) === 0
      ? `反义词：${it.a}——（　）`
      : `写出反义词：${it.a}——（　）`;
    return [stem, it.b];
  },
};

// ------------------------------------------------------------------ 汇总

export const generators = [
  classifierGenerator,
  antonymGenerator,
];
