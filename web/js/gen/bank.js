/**
 * 自编题库题型（KNOWLEDGE）—— 移植自 BankQuestionGenerator.kt。
 *
 * 为什么它不按「随机造一题、题干 hash 去重」的常规套路写：
 * 题库是**有限集合**（三年级数学可能只有 9 道可用），要 20 题时抽一遍只能出 9 题。
 * 所以这里改成「每轮把可用题整池洗一遍，并把选项顺序重排（答案字母同步换算）」，
 * 同一道题第二轮会变成不同的题干、答案仍然正确 —— 既凑够题量，
 * 同一张卷里也不会出现两道长得一样的题。
 *
 * 数据源：DATA.bank（build 时由 assets 同名 JSON 导出，74 道）。
 */

import { Subject, SUBJECT_LABEL, stableHash } from '../core/models.js';
import { DATA } from '../data/store.js';

const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F'];

/** 题库里数学 / 语文 / 英语 / 物理 / 化学都有：勾哪科就出哪科，所以是跨科题型 */
function pool(grade, subjects) {
  // 含低年级的复习题：minGrade <= 当前年级；subjects 为空表示不限制
  return DATA.bank.filter((q) => q.grade <= grade
    && (subjects.length === 0 || subjects.includes(q.subject)));
}

/**
 * 渲染一道题：去掉原选项字母 → 洗牌 → 重新编 A/B/C，并同步换算正确答案的字母。
 * 选项换了、答案字母没跟着换，是最严重的印错。
 */
function render(q, rng) {
  const body = `【${q.topic}】${q.stem}`;
  if (!q.options || q.options.length === 0) return { stem: `${body}（　）`, answer: q.answer };

  // 题库里的选项自带「A. 」前缀，先剥掉，编号由渲染层统一给
  const contents = q.options.map((s) => String(s).replace(/^[A-Fa-f][.、．:：]\s*/, ''));
  let correctIdx = -1;
  const first = String(q.answer ?? '').trim().charAt(0).toUpperCase();
  if (first) {
    const idx = first.charCodeAt(0) - 65; // 'A' 的码位
    if (idx >= 0 && idx < contents.length) correctIdx = idx;
  }

  const order = rng.shuffle(contents.map((_, i) => i));
  const opts = order.map((idx, i) => `${LETTERS[i] ?? String.fromCharCode(65 + i)}. ${contents[idx]}`);
  // 答案字母按「正确答案在洗牌后列表里的位置」重新算，认不出来就沿用原答案
  const answer = correctIdx >= 0
    ? (LETTERS[order.indexOf(correctIdx)] ?? 'A')
    : q.answer;

  return { stem: `${body}　${opts.join('　')}`, answer };
}

/** 题库没读到时的兜底常识题：点了不能没反应 */
const FALLBACK = [
  { stem: '【常识】一周有几天？（　）', answer: '7 天' },
  { stem: '【常识】一年有几个月？（　）', answer: '12 个月' },
  { stem: '【常识】一个三角形有几条边？（　）', answer: '3 条' },
  { stem: '【常识】1 米等于多少厘米？（　）', answer: '100 厘米' },
  { stem: '【常识】1 小时等于多少分钟？（　）', answer: '60 分钟' },
  { stem: '【常识】正方形的四个角都是什么角？（　）', answer: '直角' },
];

/** 对应 Kotlin 的 super.generate(ctx)：逐题随机 + 题干 hash 去重，取不到就少给 */
function drawFallback(ctx) {
  const out = [];
  const maxTries = Math.max(ctx.count * 20, 60);
  let tries = 0;
  while (out.length < ctx.count && tries < maxTries) {
    tries++;
    const q = FALLBACK[ctx.rng.nextInt(FALLBACK.length)];
    if (!q.stem || !q.answer) continue;
    if (ctx.blocked.has(stableHash(q.stem))) continue;
    ctx.blocked.add(stableHash(q.stem));
    out.push({ type: 'KNOWLEDGE', stem: q.stem, answer: q.answer, topic: '常识' });
  }
  return out;
}

export const generators = [
  {
    type: 'KNOWLEDGE',
    subject: Subject.MATH,
    // 题库各科混装，勾物理也要能出物理题，所以不按 subject 过滤
    crossSubject: true,
    minGrade: 1,
    maxGrade: 9,
    topic: '知识点综合',

    generate(ctx) {
      const subjects = [...ctx.subjects];
      const list = pool(ctx.grade, subjects);
      if (list.length === 0) return drawFallback(ctx);

      const out = [];
      let round = 0;
      // 上限 8 轮：每轮的选项顺序都不同，题干 hash 也就不同，够把小池子轮出题量
      while (out.length < ctx.count && round < 8) {
        for (const q of ctx.rng.shuffle(list)) {
          if (out.length >= ctx.count) break;
          const { stem, answer } = render(q, ctx.rng);
          if (!stem) continue;
          const h = stableHash(stem);
          if (ctx.blocked.has(h)) continue;
          ctx.blocked.add(h);
          out.push({
            type: 'KNOWLEDGE',
            stem,
            answer,
            // 知识点用「科目·知识点」，答案页与错题本按它归类
            topic: `${SUBJECT_LABEL[q.subject] ?? q.subject}·${q.topic}`,
          });
        }
        round++;
      }
      return out;
    },

    /** 单题版本：给需要「再来一道」的调用方用 */
    make(ctx) {
      const list = pool(ctx.grade, [...ctx.subjects]);
      if (list.length === 0) return FALLBACK[ctx.rng.nextInt(FALLBACK.length)];
      return render(list[ctx.rng.nextInt(list.length)], ctx.rng);
    },
  },
];
