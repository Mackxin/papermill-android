/**
 * 生成器注册表 —— 对应 App 的 GeneratorRegistry + BaseGenerator。
 *
 * 两层职责：
 *  1. 汇总各模块的生成器，回答「这个年级 + 这些科目能出哪些题型」；
 *  2. 提供统一的出题循环（重试上限、去重、丢弃坏题）。
 *
 * 出题循环的三条硬约定（照抄 Kotlin 的 BaseGenerator）：
 *  - 答案必须是生成过程的副产品（make 同时返回题干和答案），不是事后求值；
 *  - 题干 hash 做去重，重试上限 count*20，取不到就如实少给，绝不死循环；
 *  - 单个生成器抛异常就丢掉这道重来 —— 坏题绝不印到纸上。
 */

import { TYPE, subjectAvailableFor, stableHash } from './models.js';
import { generators as mathGens } from '../gen/math.js';
import { generators as worksheetGens } from '../gen/worksheet.js';
import { generators as languageGens } from '../gen/language.js';
import { generators as contentGens } from '../gen/content.js';
import { generators as themeGens } from '../gen/theme.js';
import { generators as englishGens } from '../gen/english.js';
import { generators as bankGens } from '../gen/bank.js';
import { generators as curricGens } from '../gen/curric.js';
import { generators as scienceGens } from '../gen/science.js';
import { generators as figureGens } from '../gen/figures.js';

/** 顺序即「默认卷取前 N 种」的顺序，和 App 一致：看图题排最后 */
export const ALL_GENERATORS = [
  ...mathGens,
  ...worksheetGens,
  ...languageGens,
  ...contentGens,
  ...themeGens,
  ...englishGens,
  ...bankGens,
  ...curricGens,
  ...scienceGens,
  ...figureGens,
];

const BY_TYPE = new Map();
ALL_GENERATORS.forEach((g) => BY_TYPE.set(g.type, g));

export const generatorCount = ALL_GENERATORS.length;

function supports(gen, grade) {
  const min = gen.minGrade ?? 1;
  const max = gen.maxGrade ?? 9;
  return subjectAvailableFor(gen.subject, grade) && grade >= min && grade <= max;
}

/** 勾了这些科目时，这个题型该不该出现在参数页 */
function usableIn(gen, subjects) {
  return gen.crossSubject === true || subjects.includes(gen.subject);
}

/**
 * 交叉学科：返回所有勾选科目能出的题型。
 *
 * 跨科题型（自编题库 / 课标题库）由生成器自己声明 crossSubject ——
 * 语文没有填空题，勾语文时「知识点填空」就不该摆在那儿让人点了没反应。
 */
export function availableTypes(grade, subjects) {
  const list = Array.isArray(subjects) ? subjects : [subjects];
  const seen = new Set();
  const out = [];
  ALL_GENERATORS.forEach((g) => {
    if (!supports(g, grade) || !usableIn(g, list)) return;
    if (seen.has(g.type)) return;
    seen.add(g.type);
    out.push(g.type);
  });
  return out;
}

export function generatorOf(type, grade) {
  const g = BY_TYPE.get(type);
  if (!g) return null;
  return supports(g, grade) ? g : null;
}

export function subjectOf(type) {
  return BY_TYPE.get(type)?.subject ?? null;
}

export function isCrossSubject(type) {
  return BY_TYPE.get(type)?.crossSubject === true;
}

/** 每个大题用独立种子，改动某一题的数量不会牵动其它大题的题目 */
export function sectionSeed(paperSeed, sectionIndex) {
  return ((paperSeed >>> 0) ^ Math.imul(sectionIndex + 1, 0x9e3779b1)) >>> 0;
}

/** 把配图数据压成可比较文本：只取「决定这道题是什么」的字段 */
function figureKey(spec) {
  if (!spec) return '';
  return [spec.kind, (spec.groups ?? []).join(','), (spec.shapes ?? []).join(','),
    (spec.labels ?? []).join(','), (spec.values ?? []).join(',')].join('|');
}

/**
 * 跑一个生成器，产出题目。
 *
 * 两种生成器：
 *  - make()：造一道，框架循环 count 次并去重（绝大多数题型）；
 *  - generate()：整批自己出（题库型 / 课标型，它们要自己控制池子和避让）。
 */
export function runGenerator(gen, ctx) {
  const out = [];
  if (typeof gen.generate === 'function') {
    let list = [];
    try {
      list = gen.generate(ctx) ?? [];
    } catch (err) {
      console.warn(`[${gen.type}] 整批出题失败`, err);
      return out;
    }
    list.forEach((q) => {
      if (!q) return;
      const stem = String(q.stem ?? '').trim();
      const answer = String(q.answer ?? '').trim();
      if (!stem || !answer) return;
      out.push({
        no: 0,
        type: gen.type,
        stem,
        answer,
        topic: q.topic ?? gen.topic ?? '',
        figure: q.figure ?? null,
      });
    });
    return out;
  }

  const maxTries = Math.max(ctx.count * 20, 60);
  let tries = 0;
  while (out.length < ctx.count && tries < maxTries) {
    tries++;
    let drawn = null;
    try {
      drawn = typeof gen.makeFigure === 'function' ? gen.makeFigure(ctx) : null;
      if (!drawn) {
        const pair = gen.make(ctx);
        if (!pair) continue;
        drawn = Array.isArray(pair)
          ? { stem: pair[0], answer: pair[1], figure: null }
          : pair;
      }
    } catch (err) {
      continue;
    }
    const stem = String(drawn.stem ?? '').trim();
    const answer = String(drawn.answer ?? '').trim();
    if (!stem || !answer) continue;
    const key = stableHash(stem + figureKey(drawn.figure));
    if (ctx.blocked.has(key)) continue;
    ctx.blocked.add(key);
    out.push({
      no: 0,
      type: gen.type,
      stem,
      answer,
      topic: drawn.topic ?? gen.topic ?? '',
      figure: drawn.figure ?? null,
    });
  }
  if (out.length === 0) {
    console.warn(`[${gen.type}] 一道题都没出出来（重试 ${tries} 次）`);
  }
  return out;
}

/** 题型是否存在（自编题 CUSTOM 没有生成器，单独判断） */
export function hasGenerator(type) {
  return BY_TYPE.has(type);
}

export { TYPE };
