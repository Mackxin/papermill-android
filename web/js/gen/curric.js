/**
 * 课标题库题型（CURRIC_BLANK / CURRIC_CHOICE）—— 移植自 CurricBankGenerators.kt。
 *
 * 与自编题库的区别：这里是上游持续扩容的**真实知识点配套题**（cn-math-taxonomy，
 * CC BY-NC 4.0），选项本来就齐，不用造干扰项，只要洗牌并同步换算答案字母。
 *
 * 两个硬约束（照抄 Kotlin 的注释）：
 *  1. 同一张卷不能印两道一样的题 —— 用题干 hash 进 ctx.blocked；
 *  2. 答案必须跟着选项一起动 —— 洗牌后重新算正确字母。
 *
 * 题池不在本文件里算：用 store.js 的 curricPool()，它已经实现了
 * 「本年级 → 近两年 → 全部低年级」的三层收窄 + 学期 + 难度筛选。
 */

import { Subject, SUBJECT_LABEL, stableHash } from '../core/models.js';
import { curricPool, topicName } from '../data/store.js';

const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F'];

/** 题干结尾是不是已经有「写答案的地方」了（有就不再补括号） */
function hasWriteSlot(p) {
  return p.includes('＿') || p.includes('__') || p.includes('（　') || p.includes('（ ）')
    || p.includes('( )') || p.includes('(  )');
}

/** 答案后面缀一句解析（答案页用；没有解析就只给答案） */
function withExplain(answer, explain) {
  const e = String(explain ?? '').trim();
  return e ? `${answer}　解析：${e}` : answer;
}

/** 知识点名，不能是内部 id；查不到时退回「科目·知识点」 */
function topicLabel(q) {
  const name = topicName(q.topicId);
  return name ? `${SUBJECT_LABEL[q.subject] ?? q.subject}·${name}` : `${SUBJECT_LABEL[q.subject] ?? q.subject}·知识点`;
}

/** 填空题：题干照抄，末尾补一个写答案的括号；答案带解析 */
function renderBlank(q) {
  const prompt = String(q.prompt ?? '').trim();
  const answer = String(q.answer ?? '').trim();
  if (!prompt || !answer) return null;
  const stem = hasWriteSlot(prompt) ? prompt : `${prompt}（　　）`;
  return { stem, answer: withExplain(answer, q.explain) };
}

/** 选择题：选项洗牌 → 重新编 A/B/C → 同步换算正确字母 */
function renderChoice(q, rng) {
  if (!q.options || q.options.length < 2) return null;
  const key = String(q.answer ?? '').trim().toLowerCase();
  const correct = q.options.find((o) => String(o.key).toLowerCase() === key);
  if (!correct) return null;

  const order = rng.shuffle(q.options);
  const idx = order.findIndex((o) => o.key === correct.key);
  if (idx < 0) return null;

  const opts = order.map((o, i) => `${LETTERS[i] ?? String.fromCharCode(65 + i)}. ${String(o.text).trim()}`);
  const stem = `${String(q.prompt ?? '').trim()}\n${opts.join('　')}`;
  // 答案带上正确选项文字：家长拿答案页就能对，不用回头翻卷子
  return {
    stem,
    answer: withExplain(`（${LETTERS[idx] ?? String.fromCharCode(65 + idx)}）${String(correct.text).trim()}`, q.explain),
  };
}

/**
 * 抽题主流程（两个题型共用）。
 *
 * 洗牌后整池走一遍，走完还差就再洗一遍；但第二轮起几乎必然被 blocked 挡光，
 * 所以上限 4 轮，凑不够就如实少给 —— 绝不为了凑数把同一道题印两遍。
 *
 * allowReuse 的处理（对应 GenContext 的字段说明）：
 *  - true（正常出卷）：池子被**历史卷**的键挡光时，把开抽前就占着的本池键放行一轮，
 *    宁可重印两道旧题，也不让一个大题整块消失；
 *  - false（补题探针）：池子干了就返回空，让界面老实说「题已经全部用上了」。
 * 注意只放历史键，本卷刚印过的键始终留着，否则同卷去重就失效了。
 */
function drawCurric(ctx, choice) {
  const subjects = [...ctx.subjects];
  const topicIds = [...ctx.topicIds];
  const pool = curricPool({
    subjects,
    grade: ctx.grade,
    difficulty: ctx.difficulty,
    choice,
    topicIds,
    // 单元自带学期，按单元出卷时不要再按学期筛一次（会把单元筛空）
    term: topicIds.length === 0 ? ctx.term : 0,
  });
  if (!pool.length) return [];

  const type = choice ? 'CURRIC_CHOICE' : 'CURRIC_BLANK';

  // 开抽前就在 blocked 里的本池键 = 历史卷占用的，允许被放行；本卷新加的不会进这个集合
  const historyKeys = new Set();
  for (const q of pool) {
    const h = stableHash(q.prompt);
    if (ctx.blocked.has(h)) historyKeys.add(h);
  }
  let released = false;

  const out = [];
  let round = 0;
  while (out.length < ctx.count && round < 4) {
    const before = out.length;
    for (const q of ctx.rng.shuffle(pool)) {
      if (out.length >= ctx.count) break;
      const rendered = choice ? renderChoice(q, ctx.rng) : renderBlank(q);
      if (!rendered) continue;
      // 用题干（而不是含洗牌后选项的整段）去重，否则同一道题换个选项顺序就能重复进来
      const h = stableHash(q.prompt);
      if (ctx.blocked.has(h)) continue;
      ctx.blocked.add(h);
      out.push({ type, stem: rendered.stem, answer: rendered.answer, topic: topicLabel(q) });
    }
    round++;

    // 这一轮一个都没加：要么池子干了，要么全被挡了
    if (out.length === before) {
      if (ctx.allowReuse && !released && historyKeys.size) {
        for (const h of historyKeys) ctx.blocked.delete(h);
        released = true;
        continue;
      }
      break;
    }
  }
  return out;
}

export const generators = [
  {
    type: 'CURRIC_BLANK',
    subject: Subject.MATH,
    // 题库五科混装：勾了哪科就出哪科的填空题
    crossSubject: true,
    minGrade: 1,
    maxGrade: 9,

    generate(ctx) {
      return drawCurric(ctx, false);
    },
  },
  {
    type: 'CURRIC_CHOICE',
    subject: Subject.MATH,
    crossSubject: true,
    minGrade: 1,
    maxGrade: 9,

    generate(ctx) {
      return drawCurric(ctx, true);
    },
  },
];
