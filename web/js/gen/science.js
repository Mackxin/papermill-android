/**
 * 小学科学题型（SCIENCE_CHOICE / SCIENCE_JUDGE / SCIENCE_BLANK）
 * —— 移植自 ScienceGenerators.kt + ScienceBank.draw()。
 *
 * 三个题型共用一条抽题流程，只是渲染方式不同：
 *  - 选择题洗牌选项、按新位置重算答案字母（答案错位是最严重的事故）；
 *  - 判断题题干后补一个括号，答案 √ / ×；
 *  - 填空题题干自带括号，答案直接是文字。
 * 判断题和填空题都没选项，答案原样带出，附一句解析。
 *
 * 数据源：DATA.science（216 道，1~6 年级 × 三题型 × 12 道）。
 */

import { Subject, stableHash } from '../core/models.js';
import { DATA } from '../data/store.js';

const LETTERS = ['A', 'B', 'C', 'D', 'E'];

function withExplain(answer, explain) {
  const e = String(explain ?? '').trim();
  return e ? `${answer}　解析：${e}` : answer;
}

/** 按 kind 过滤：choice / judge / blank，年级必须精确相等 */
function kindPool(grade, kind) {
  return DATA.science.filter((q) => q.grade === grade && q.kind === kind);
}

/**
 * 对应 ScienceBank.draw()：按「离所选难度的距离」就近取前 count 道。
 *
 * 每个年级每种题型只有 12 道，按难度精确筛会只剩 4 道、出 10 道凑不齐；
 * 就近取既尊重难度设置，也保证题量。稳定排序 = 同距离保持原顺序。
 */
function drawSource(grade, kind, difficulty, count) {
  const list = kindPool(grade, kind);
  if (!list.length || count <= 0) return [];

  const ordered = list.slice().sort(
    (a, b) => Math.abs(a.difficulty - difficulty) - Math.abs(b.difficulty - difficulty),
  );
  if (kind !== 'judge') return ordered.slice(0, count);

  // 判断题把 √ / × 对半分：随机抽 10 道容易抽成「八个√两个×」，蒙就能过
  const yes = ordered.filter((q) => String(q.answer).trim() === '√');
  const no = ordered.filter((q) => String(q.answer).trim() === '×');
  const half = Math.floor(count / 2);
  const out = [...yes.slice(0, count - half), ...no.slice(0, half)];
  if (out.length < count) {
    out.push(...ordered.filter((q) => !out.includes(q)).slice(0, count - out.length));
  }
  return out;
}

/** 选择题：选项洗牌 → 重新编 A/B/C → 答案字母按新位置算 */
function renderChoice(q, rng) {
  const key = String(q.answer ?? '').trim().toUpperCase();
  if (!key) return null;
  const correctIdx = key.charCodeAt(0) - 65; // 'A' 的码位
  if (correctIdx < 0 || correctIdx >= q.options.length) return null;

  const order = rng.shuffle(q.options.map((_, i) => i));
  const opts = order.map((origin, i) => `${LETTERS[i] ?? String.fromCharCode(65 + i)}. ${String(q.options[origin]).trim()}`);
  const newIdx = order.indexOf(correctIdx);
  // 答案把选项文字一起带上：家长拿答案页就能对，不用回头翻原卷
  const answer = `（${LETTERS[newIdx] ?? String.fromCharCode(65 + newIdx)}）${String(q.options[correctIdx]).trim()}`;
  return {
    stem: `【${q.topic}】${String(q.stem).trim()}\n${opts.join('　')}`,
    answer: withExplain(answer, q.explain),
  };
}

/** 判断题：题干后面留一个括号，答案 √ / × */
function renderJudge(q) {
  return {
    stem: `${String(q.stem).trim()}（　）`,
    answer: withExplain(String(q.answer).trim(), q.explain),
  };
}

/** 填空题：题干自带括号，答案直接是文字 */
function renderBlank(q) {
  return {
    stem: String(q.stem).trim(),
    answer: withExplain(String(q.answer).trim(), q.explain),
  };
}

/**
 * 抽题主流程：就近取 → 洗牌 → 逐条渲染，最多 4 轮。
 *
 * 为什么要分轮：科学题库每个年级每种题型只有 12 道，而 ctx.blocked 里装着最近几张
 * 历史卷的题干 hash。连着出两张同年级科学卷，第二张一抽就被历史避让挡光 ——
 * 实测要 8 道填空、池里 8 道、最后出 0 道，整块大题凭空消失。
 *
 * 所以规则定成：
 *  1. 同一道题在这张卷里只出现一次（paperSeen）—— 永远生效；
 *  2. 历史避让只在头两轮生效，第三轮起放行（allowReuse=true 时）——
 *     宁可两道旧题重印，也不能因为池子小就让一个大题整块不见。
 */
function drawScience(ctx, kind, type) {
  const picked = drawSource(ctx.grade, kind, ctx.difficulty, ctx.count);
  if (!picked.length) return [];

  const paperSeen = new Set();   // 纸内去重：按原题干
  const out = [];
  let round = 0;
  while (out.length < ctx.count && round < 4) {
    for (const q of ctx.rng.shuffle(picked)) {
      if (out.length >= ctx.count) break;
      const rendered = kind === 'choice' ? renderChoice(q, ctx.rng)
        : kind === 'judge' ? renderJudge(q) : renderBlank(q);
      if (!rendered) continue;

      const srcHash = stableHash(q.stem);
      if (paperSeen.has(srcHash)) continue;

      // 历史避让用**渲染后题干**的 hash：全站（历史卷、补题探针）都是这个口径
      const renderHash = stableHash(rendered.stem);
      const bypass = ctx.allowReuse && round >= 2;
      if (!bypass && ctx.blocked.has(renderHash)) continue;

      // 纸内去重只在「真的要印」时记账：若提前记，第 0 轮被历史挡掉的题第 2 轮就放不出来了
      paperSeen.add(srcHash);
      ctx.blocked.add(srcHash);
      ctx.blocked.add(renderHash);
      out.push({ type, stem: rendered.stem, answer: rendered.answer, topic: `科学·${q.topic}` });
    }
    round++;
  }
  return out;
}

export const generators = [
  {
    type: 'SCIENCE_CHOICE',
    subject: Subject.SCIENCE,
    // 科学不跨科：勾了科学才出现，走默认的 subject in subjects
    crossSubject: false,
    minGrade: 1,
    maxGrade: 6,

    generate(ctx) {
      return drawScience(ctx, 'choice', 'SCIENCE_CHOICE');
    },
  },
  {
    type: 'SCIENCE_JUDGE',
    subject: Subject.SCIENCE,
    crossSubject: false,
    minGrade: 1,
    maxGrade: 6,

    generate(ctx) {
      return drawScience(ctx, 'judge', 'SCIENCE_JUDGE');
    },
  },
  {
    type: 'SCIENCE_BLANK',
    subject: Subject.SCIENCE,
    crossSubject: false,
    minGrade: 1,
    maxGrade: 6,

    generate(ctx) {
      return drawScience(ctx, 'blank', 'SCIENCE_BLANK');
    },
  },
];
