/**
 * 低年级短题生成器 —— 从 App 的 WorksheetGenerators.kt 移植。
 *
 * 填括号、凑十、连加连减、加减混合、整十数、两位数、单位换算、比大小、
 * 找规律、数数、字母顺序。这些题都短，一页能排三栏，专门用来把纸面排满。
 *
 * 每道题都由算法现场生成，答案直接由生成参数推出（不是事后猜的），
 * 所以可以离线无限出题。每个生成器返回 [题干, 答案]，抽不到就返回 null。
 */

import { Subject } from '../core/models.js';

// ------------------------------------------------------------------ 小工具

/** 闭区间随机：等价 Kotlin 的 r(rng, from, to) */
function r(rng, from, to) {
  return rng.nextInt(from, to);
}

/** 整除（向零截断，和 Kotlin Int 除法一致） */
function idiv(a, b) {
  return Math.trunc(a / b);
}

/** 按年级收紧数域：一年级 10 以内、二年级 20 以内、三年级 100 以内、再往上 1000 以内 */
function bound(grade) {
  if (grade <= 1) return 10;
  if (grade === 2) return 20;
  if (grade === 3) return 100;
  return 1000;
}

const LETTERS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('');

// ------------------------------------------------------------------ 填括号

/** 4－？＝1、？＋8＝13 —— 缺项填空，练逆运算 */
export const FillBlankGenerator = {
  type: 'FILL_BLANK',
  subject: Subject.MATH,
  minGrade: 1,
  maxGrade: 4,
  topic: '填括号',

  make(ctx) {
    const rng = ctx.rng;
    const max = bound(ctx.grade);
    switch (r(rng, 0, 3)) {
      case 0: {
        const c = r(rng, 3, max);
        const a = r(rng, 1, c - 1);
        return [`${a}＋？＝${c}（　　）`, `${c - a}`];
      }
      case 1: {
        const a = r(rng, 3, max);
        const c = r(rng, 0, a - 1);
        return [`${a}－？＝${c}（　　）`, `${a - c}`];
      }
      case 2: {
        const c = r(rng, 3, max);
        const b = r(rng, 1, c - 1);
        return [`？＋${b}＝${c}（　　）`, `${c - b}`];
      }
      default: {
        const b = r(rng, 1, Math.max(2, idiv(max, 2)));
        const c = r(rng, 0, Math.max(2, idiv(max, 2)));
        return [`？－${b}＝${c}（　　）`, `${b + c}`];
      }
    }
  },
};

// ------------------------------------------------------------------ 凑十

/** ？＋8＝10 —— 凑十 / 凑二十，进位加法的地基 */
export const MakeTenGenerator = {
  type: 'MAKE_TEN',
  subject: Subject.MATH,
  minGrade: 1,
  maxGrade: 2,
  topic: '凑十法',

  make(ctx) {
    const rng = ctx.rng;
    // 一年级只凑十，二年级再抽到二十
    const base = ctx.grade <= 1 || rng.nextBoolean() ? 10 : 20;
    const b = r(rng, 1, base - 1);
    if (rng.nextBoolean()) {
      return [`？＋${b}＝${base}（　　）`, `${base - b}`];
    }
    return [`${b}＋（　　）＝${base}`, `${base - b}`];
  },
};

// ------------------------------------------------------------------ 连加连减 / 加减混合

/** 2＋1＋5＝、15－4－7＝ —— 三个数连算，中间结果始终不为负 */
export const ChainCalcGenerator = {
  type: 'CHAIN_CALC',
  subject: Subject.MATH,
  minGrade: 1,
  maxGrade: 4,
  topic: '连加连减',

  make(ctx) {
    const rng = ctx.rng;
    const max = bound(ctx.grade);
    if (rng.nextBoolean()) {
      const a = r(rng, 1, Math.max(3, idiv(max, 3)));
      const b = r(rng, 1, Math.max(3, idiv(max, 3)));
      const c = r(rng, 1, Math.max(3, idiv(max, 3)));
      return [`${a}＋${b}＋${c}＝（　　）`, `${a + b + c}`];
    }
    // b、c 都从上一步剩下的数里取，保证不会减出负数
    const a = r(rng, 6, max);
    const b = r(rng, 1, Math.max(1, idiv(a, 2)));
    const c = r(rng, 1, Math.max(1, a - b));
    return [`${a}－${b}－${c}＝（　　）`, `${a - b - c}`];
  },
};

/** 19－3＋0＝、5－0＋1＝ —— 加减混合，两个方向都练到 */
export const MixedCalcGenerator = {
  type: 'MIXED_CALC',
  subject: Subject.MATH,
  minGrade: 1,
  maxGrade: 4,
  topic: '加减混合',

  make(ctx) {
    const rng = ctx.rng;
    const max = bound(ctx.grade);
    if (rng.nextBoolean()) {
      const a = r(rng, 5, max);
      const b = r(rng, 1, a);
      const c = r(rng, 1, Math.max(2, idiv(max, 2)));
      return [`${a}－${b}＋${c}＝（　　）`, `${a - b + c}`];
    }
    const a = r(rng, 1, Math.max(2, idiv(max, 2)));
    const b = r(rng, 1, Math.max(2, idiv(max, 2)));
    const c = r(rng, 1, a + b);
    return [`${a}＋${b}－${c}＝（　　）`, `${a + b - c}`];
  },
};

// ------------------------------------------------------------------ 整十数 / 两位数

/** 50＋3、80－10 —— 整十数加一位数、整十数加减整十数 */
export const TensCalcGenerator = {
  type: 'TENS_CALC',
  subject: Subject.MATH,
  minGrade: 1,
  maxGrade: 2,
  topic: '整十数运算',

  make(ctx) {
    const rng = ctx.rng;
    const t = r(rng, 1, 8) * 10;
    const one = r(rng, 1, 9);
    const ten = r(rng, 1, 9) * 10;
    switch (r(rng, 0, 3)) {
      case 0:
        return [`${t}＋${one}＝（　　）`, `${t + one}`];
      case 1:
        return [`${t}－${one}＝（　　）`, `${t - one}`];
      case 2:
        return [`${t}＋${ten}＝（　　）`, `${t + ten}`];
      default: {
        // 封顶 90，两个整十数相加再减回去，差始终是 t 且不为负
        const big = Math.min(t + ten, 90);
        return [`${big}－${ten}＝（　　）`, `${big - ten}`];
      }
    }
  },
};

/** 37＋7、37－20 —— 两位数加一位数 / 整十数 */
export const TwoDigitGenerator = {
  type: 'TWO_DIGIT',
  subject: Subject.MATH,
  minGrade: 1,
  maxGrade: 3,
  topic: '两位数加减',

  make(ctx) {
    const rng = ctx.rng;
    const a = r(rng, 21, 89);
    const b = rng.nextBoolean() ? r(rng, 2, 9) : r(rng, 1, 8) * 10;
    if (rng.nextBoolean()) {
      return [`${a}＋${b}＝（　　）`, `${a + b}`];
    }
    return [`${a}－${b}＝（　　）`, `${a - b}`];
  },
};

// ------------------------------------------------------------------ 单位换算

/** 6 角 9 分＝？分、3 米 5 厘米＝？厘米、2 时＝？分 —— 事实性换算 */
export const UnitConvertGenerator = {
  type: 'UNIT_CONVERT',
  subject: Subject.MATH,
  minGrade: 1,
  maxGrade: 5,
  topic: '单位换算',

  make(ctx) {
    const rng = ctx.rng;
    switch (r(rng, 0, 4)) {
      case 0: {
        const y = r(rng, 1, 9);
        const j = r(rng, 1, 9);
        return [`${y} 元 ${j} 角＝（　　）角`, `${y * 10 + j}`];
      }
      case 1: {
        const j = r(rng, 1, 9);
        const f = r(rng, 1, 9);
        return [`${j} 角 ${f} 分＝（　　）分`, `${j * 10 + f}`];
      }
      case 2: {
        const m = r(rng, 1, 9);
        const cm = r(rng, 1, 99);
        return [`${m} 米 ${cm} 厘米＝（　　）厘米`, `${m * 100 + cm}`];
      }
      case 3: {
        const h = r(rng, 1, 6);
        return [`${h} 时＝（　　）分`, `${h * 60}`];
      }
      default: {
        const kg = r(rng, 1, 9);
        return [`${kg} 千克＝（　　）克`, `${kg * 1000}`];
      }
    }
  },
};

// ------------------------------------------------------------------ 比大小 / 找规律

/** 7＋2 ○ 10（填＞、＜或＝）—— 先算再比 */
export const CompareGenerator = {
  type: 'COMPARE',
  subject: Subject.MATH,
  minGrade: 1,
  maxGrade: 4,
  topic: '比大小',

  make(ctx) {
    const rng = ctx.rng;
    const max = Math.min(bound(ctx.grade), 50);
    const a = r(rng, 1, max);
    const b = r(rng, 1, max);
    const c = r(rng, 1, max);
    const d = r(rng, 1, max);
    const left = a + b;
    const right = c + d;
    const sym = left > right ? '＞' : left < right ? '＜' : '＝';

    if (rng.nextBoolean()) {
      return [`${a}＋${b} ○ ${c}＋${d}（填＞＜＝）`, sym];
    }
    // 第二种写法右边只有一个数，必须按实际印出来的式子重新比，否则答案对不上
    const sub = Math.min(a, b);
    const l = a - sub;
    const ans = l > c ? '＞' : l < c ? '＜' : '＝';
    return [`${a}－${sub} ○ ${c}（填＞＜＝）`, ans];
  },
};

/** 2，4，6，8，（　），12 —— 等差数列找规律 */
export const NumberPatternGenerator = {
  type: 'NUMBER_PATTERN',
  subject: Subject.MATH,
  minGrade: 1,
  maxGrade: 6,
  topic: '找规律',

  make(ctx) {
    const rng = ctx.rng;
    const start = r(rng, 1, 20);
    // 二年级以内步长压到 5 以内，不然孩子还没学到那么大的乘法
    const step = r(rng, 2, ctx.grade <= 2 ? 5 : 12);
    const seq = [];
    for (let i = 0; i <= 5; i++) seq.push(start + i * step);
    const hidden = r(rng, 2, 4);
    const parts = seq.map((v, i) => (i === hidden ? '（　　）' : `${v}`));
    return [`按规律填数：${parts.join('，')}`, `${seq[hidden]}`];
  },
};

/** ○○○ 一共有几个、从 3 数到 8 数了几个数 —— 低年级数数 */
export const CountingGenerator = {
  type: 'COUNTING',
  subject: Subject.MATH,
  minGrade: 1,
  maxGrade: 2,
  topic: '数数与认数',

  make(ctx) {
    const rng = ctx.rng;
    switch (r(rng, 0, 2)) {
      case 0: {
        const n = r(rng, 3, 9);
        return [`${'○'.repeat(n)} 一共有几个○？（　　）`, `${n}`];
      }
      case 1: {
        const a = r(rng, 1, 6);
        const b = a + r(rng, 2, 5);
        return [`从 ${a} 数到 ${b}，一共数了几个数？（　　）`, `${b - a + 1}`];
      }
      default: {
        const n = r(rng, 2, 19);
        return [`${n} 的后面一个数是（　　）`, `${n + 1}`];
      }
    }
  },
};

// ------------------------------------------------------------------ 英语字母

/** D 的前一个字母、g 的大写形式 —— 低年级字母顺序与大小写 */
export const LetterOrderGenerator = {
  type: 'LETTER_ORDER',
  subject: Subject.ENGLISH,
  minGrade: 3,
  maxGrade: 6,
  topic: '字母顺序',

  make(ctx) {
    const rng = ctx.rng;
    switch (r(rng, 0, 3)) {
      case 0: {
        const i = r(rng, 1, 25);
        return [`字母 ${LETTERS[i]} 的前一个字母是（　　）`, `${LETTERS[i - 1]}`];
      }
      case 1: {
        const i = r(rng, 0, 24);
        return [`字母 ${LETTERS[i]} 的后一个字母是（　　）`, `${LETTERS[i + 1]}`];
      }
      case 2: {
        const i = r(rng, 0, 25);
        return [`写出 ${LETTERS[i].toLowerCase()} 的大写形式（　　）`, `${LETTERS[i]}`];
      }
      default: {
        const i = r(rng, 0, 23);
        return [`按字母顺序，${LETTERS[i]} 后面第 2 个字母是（　　）`, `${LETTERS[i + 2]}`];
      }
    }
  },
};

// ------------------------------------------------------------------ 导出

export const generators = [
  FillBlankGenerator,
  MakeTenGenerator,
  ChainCalcGenerator,
  MixedCalcGenerator,
  TensCalcGenerator,
  TwoDigitGenerator,
  UnitConvertGenerator,
  CompareGenerator,
  NumberPatternGenerator,
  CountingGenerator,
  LetterOrderGenerator,
];
