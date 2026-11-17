/**
 * 看图题（FIGURE_COUNT / FIGURE_EXPR / CLOCK_READ / BAR_CHART）
 * —— 移植自 FigureGenerators.kt。
 *
 * 为什么这几类题非有不可：一二年级课本里相当一部分题是「图 + 空」，
 * 考点就是**看图取信息**。换成一句文字描述反而把答案说了
 * （「时针在 8 和 9 中间、分针指 6」这句本身就等于答案）。
 *
 * 关键约定：随机数先算出来，数字**同时**进题干和图形数据 ——
 * 「图上画 8 个、答案写 6」这种错在结构上就不会发生。
 * 图形只给数据（FigureSpec），怎么画是排版层的事。
 */

import { Subject, stableHash } from '../core/models.js';

/** 图形代号：0 圆、1 三角、2 方块、3 五角星，下标就是 figure.shapes 里的值 */
const SHAPE_SYMBOLS = ['○', '△', '□', '☆'];

/** 闭区间随机整数（对应 Kotlin 的 rand 辅助函数） */
function rand(rng, from, to) {
  return to <= from ? from : rng.nextInt(from, to);
}

function spec(kind, { groups = [], shapes = [], labels = [], values = [], unit = '' } = {}) {
  return { kind, groups, shapes, labels, values, unit };
}

/**
 * 图形数据的去重键（对应 QuestionProvider 的 figureKey）。
 * 钟面题、统计图题的题干是固定句式，只有数字在图上 —— 只按题干去重会把后 7 道全挡住。
 * 只取真正决定「这道题是什么」的字段，不取展示性文案。
 */
function figureKey(f) {
  if (!f) return '';
  return `${f.kind}${f.groups.join(',')}|${f.shapes.join(',')}|${f.labels.join(',')}|${f.values.join(',')}`;
}

/**
 * 看图数一数（一、二年级）：三行不同图形各若干个，问各有几个。
 * 考的是分类计数 —— 一年级最典型的失分点是把别的图形也数进去。
 */
function makeCount(ctx) {
  const rng = ctx.rng;
  // 三种图形的出场顺序也打乱：总按「○△□」排，学生会背位置而不是数数
  const order = rng.shuffle([0, 1, 2, 3]).slice(0, 3);
  const counts = order.map(() => rand(rng, 3, 9));
  // 只说「哪几个图形、各几个空」——「数一数填在括号里」已印在大题标题下
  const stem = order.map((kind) => `${SHAPE_SYMBOLS[kind]}（　　）个`).join('　');
  // 答案按题干顺序给，家长一眼能对上
  const answer = order.map((kind, i) => `${SHAPE_SYMBOLS[kind]}${counts[i]}`).join('　');
  return {
    stem,
    answer,
    figure: spec('COUNTING', { groups: counts, shapes: order }),
  };
}

/**
 * 看图列式（一、二年级）：左边一堆、右边一堆，求一共多少。
 * 「加法是两个部分合并」这个概念最原始的样子。
 */
function makeExpr(ctx) {
  const rng = ctx.rng;
  const left = rand(rng, 3, 7);
  const right = rand(rng, 2, 5);
  const kind = rand(rng, 0, 3);
  return {
    stem: '（　　　　　）',
    answer: `${left}+${right}=${left + right}`,
    figure: spec('TWO_GROUPS', { groups: [left, right], shapes: [kind] }),
  };
}

/**
 * 认识钟面（一~三年级）：一年级只出整时 / 半时，高年级按 5 分一格。
 * 时针按真实角度画（跟着分钟走一点点），否则 8:30 的时针正对 8，读成「8 时」也算对。
 */
function makeClock(ctx) {
  const rng = ctx.rng;
  const hour = rand(rng, 1, 12);
  const minute = ctx.grade <= 1
    ? (rng.nextBoolean() ? 0 : 30)
    : rand(rng, 0, 11) * 5;
  // 题干只留空的形状：整句「看钟面写出时刻」印一次就够了
  const stem = minute === 0 ? '（　　　）时' : '（　　　）时（　　　）分';
  const answer = minute === 0 ? `${hour}时` : `${hour}时${minute}分`;
  return { stem, answer, figure: spec('CLOCK', { groups: [hour, minute] }) };
}

/** 四组固定题材：都是一年级能读懂的名词，不涉及任何教材原文 */
const BAR_LABELS = [
  ['苹果', '香蕉', '橘子', '梨'],
  ['故事书', '漫画书', '科普书', '绘本'],
  ['跳绳', '踢球', '跑步', '拍球'],
  ['晴天', '多云', '雨天', '阴天'],
];

/**
 * 条形统计图（二~四年级）：两小问 —— 最多的是哪一类（比较）、一共多少（求和）。
 */
function makeBar(ctx) {
  const rng = ctx.rng;
  const labels = rng.pick(BAR_LABELS);
  // 四个数必须互不相同：否则「最多的是哪个」有两个答案，题目本身就错了
  const values = [];
  while (values.length < 4) {
    const v = rand(rng, 2, 10);
    if (!values.includes(v)) values.push(v);
  }
  const maxIdx = values.indexOf(Math.max(...values));
  return {
    stem: '①（　　　　　）最多；② 一共有（　　　　）个。',
    answer: `${labels[maxIdx]}；${values.reduce((a, b) => a + b, 0)}`,
    figure: spec('BAR_CHART', { labels, values, unit: '个' }),
  };
}

/**
 * 看图题的出题循环（对应 BaseGenerator.generate）：
 * 逐题重试、按「题干 + 图形数据」去重，取不到就如实少给。
 */
function drawFigures(ctx, type, topic, make) {
  const out = [];
  const maxTries = Math.max(ctx.count * 20, 60);
  let tries = 0;
  while (out.length < ctx.count && tries < maxTries) {
    tries++;
    const q = make(ctx);
    if (!q) continue;
    const stem = String(q.stem ?? '').trim();
    const answer = String(q.answer ?? '').trim();
    if (!stem || !answer) continue;
    const h = stableHash(stem + figureKey(q.figure));
    if (ctx.blocked.has(h)) continue;
    ctx.blocked.add(h);
    out.push({ type, stem, answer, topic, figure: q.figure });
  }
  return out;
}

function figureGenerator(type, { minGrade = 1, maxGrade = 9, topic, make }) {
  return {
    type,
    subject: Subject.MATH,
    crossSubject: false,
    minGrade,
    maxGrade,
    topic,

    /** 单题：题干 / 答案 / 图形三件套必须来自同一次随机 */
    make,

    generate(ctx) {
      return drawFigures(ctx, type, topic, make);
    },
  };
}

export const generators = [
  figureGenerator('FIGURE_COUNT', {
    maxGrade: 2,
    topic: '数数与分类',
    make: makeCount,
  }),
  figureGenerator('FIGURE_EXPR', {
    maxGrade: 2,
    topic: '加法的意义',
    make: makeExpr,
  }),
  figureGenerator('CLOCK_READ', {
    maxGrade: 3,
    topic: '认识钟表',
    make: makeClock,
  }),
  figureGenerator('BAR_CHART', {
    minGrade: 2,
    maxGrade: 4,
    topic: '统计图表',
    make: makeBar,
  }),
];
