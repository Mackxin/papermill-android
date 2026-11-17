/**
 * 数学计算类生成器 —— 从 App 的 MathGenerators.kt / OralExtraGenerators.kt 移植。
 *
 * 对应关系：口算、竖式、脱式、分数、方程、应用题、有理数、整式、二次根式、
 * 求未知项、有余数除法。每个生成器只负责「造一道题」，返回 [题干, 答案]；
 * 抽不到合适的数就返回 null，由框架丢弃这一道。
 *
 * 题干里的「（　　）」「＝」是全角，要原样印到纸上，别改成半角。
 * 数字范围全部走 GradeProfiles，和 App 的年级画像保持一致。
 */

import { Subject, GradeProfiles } from '../core/models.js';

// ------------------------------------------------------------------ 小工具

/** 闭区间随机：等价 Kotlin 的 r(rng, from, to)（to <= from 时直接取 from） */
function r(rng, from, to) {
  return rng.nextInt(from, to);
}

/** 整除（向零截断，和 Kotlin Int 除法一致） */
function idiv(a, b) {
  return Math.trunc(a / b);
}

function gcd(a, b) {
  let x = Math.abs(a);
  let y = Math.abs(b);
  while (y !== 0) {
    const t = x % y;
    x = y;
    y = t;
  }
  return x === 0 ? 1 : x;
}

/**
 * 答案自校验：把生成的算式重新求值，和答案比对。
 * 只要运算符写错或变量抄错就抛异常，该题被框架丢弃 —— 绝不带着错答案印到纸上。
 * 题干不是「数 运算符 数 =」的形式（如带括号的有理数）时直接跳过。
 */
function verifyArithmetic(stem, answer) {
  const m = /^(-?\d+)([+\-×÷])(-?\d+)=/.exec(stem);
  if (!m) return;
  const a = Number(m[1]);
  const b = Number(m[3]);
  let expected;
  switch (m[2]) {
    case '+':
      expected = a + b;
      break;
    case '-':
      expected = a - b;
      break;
    case '×':
      expected = a * b;
      break;
    case '÷':
      if (b !== 0 && a % b === 0) expected = a / b;
      else return;
      break;
    default:
      return;
  }
  if (!/^-?\d+$/.test(answer) || Number(answer) !== expected) {
    throw new Error(`算式与答案不一致：${stem} / ${answer}`);
  }
}

// ------------------------------------------------------------------ 小学口算

export const OralGenerator = {
  type: 'ORAL',
  subject: Subject.MATH,
  minGrade: 1,
  maxGrade: 6,
  topic: '四则运算',

  make(ctx) {
    const p = GradeProfiles.of(ctx.grade);
    const rng = ctx.rng;

    // 一年级不出现进位/退位、不出现乘除：a+b<=10，减法 a>=b
    if (ctx.grade === 1) {
      if (rng.nextBoolean()) {
        const a = r(rng, 1, 9);
        const b = r(rng, 1, 10 - a);
        const stem = `${a}+${b}=（　　）`;
        const ans = `${a + b}`;
        verifyArithmetic(stem, ans);
        return [stem, ans];
      }
      const a = r(rng, 2, 10);
      const b = r(rng, 1, a);
      const stem = `${a}-${b}=（　　）`;
      const ans = `${a - b}`;
      verifyArithmetic(stem, ans);
      return [stem, ans];
    }

    switch (r(rng, 0, 3)) {
      case 0: {
        const a = r(rng, 1, Math.max(2, idiv(p.addMax, 2)));
        const b = r(rng, 1, Math.max(1, p.addMax - a));
        const stem = `${a}+${b}=（　　）`;
        const ans = `${a + b}`;
        verifyArithmetic(stem, ans);
        return [stem, ans];
      }
      case 1: {
        const a = r(rng, 2, p.addMax);
        const b = r(rng, 1, a);
        const stem = `${a}-${b}=（　　）`;
        const ans = `${a - b}`;
        verifyArithmetic(stem, ans);
        return [stem, ans];
      }
      case 2: {
        const a = r(rng, 2, Math.max(3, p.mulMax));
        const b = r(rng, 2, Math.max(3, p.mulMax));
        const stem = `${a}×${b}=（　　）`;
        const ans = `${a * b}`;
        verifyArithmetic(stem, ans);
        return [stem, ans];
      }
      default: {
        const b = r(rng, 2, Math.max(3, p.divisorMax));
        const q = r(rng, 2, 9);
        const stem = `${b * q}÷${b}=（　　）`;
        const ans = `${q}`;
        verifyArithmetic(stem, ans);
        return [stem, ans];
      }
    }
  },
};

// ------------------------------------------------------------------ 竖式 / 脱式 / 分数 / 方程

export const VerticalGenerator = {
  type: 'VERTICAL',
  subject: Subject.MATH,
  minGrade: 2,
  maxGrade: 6,
  topic: '竖式计算',

  make(ctx) {
    const p = GradeProfiles.of(ctx.grade);
    const rng = ctx.rng;
    // 题干只写算式：「用竖式计算」这类指令由大题说明统一说一次，否则一页重复印 20 遍
    if (rng.nextBoolean()) {
      const a = r(rng, 20, p.addMax);
      const b = r(rng, 11, Math.max(12, idiv(p.addMax, 2)));
      const stem = `${a}+${b}＝（　　）`;
      const ans = `${a + b}`;
      verifyArithmetic(stem.replace('＝', '='), ans);
      return [stem, ans];
    }
    const a = r(rng, 30, p.addMax);
    const b = r(rng, 11, a - 1);
    const stem = `${a}-${b}＝（　　）`;
    const ans = `${a - b}`;
    verifyArithmetic(stem.replace('＝', '='), ans);
    return [stem, ans];
  },
};

export const ExpressionGenerator = {
  type: 'EXPRESSION',
  subject: Subject.MATH,
  minGrade: 3,
  maxGrade: 9,
  topic: '混合运算',

  make(ctx) {
    const rng = ctx.rng;
    if (rng.nextBoolean()) {
      // 先乘后加，不带括号也合法
      const a = r(rng, 5, 60);
      const b = r(rng, 2, 12);
      const c = r(rng, 2, 9);
      return [`${a}+${b}×${c}＝（　　）`, `${a + b * c}`];
    }
    const a = r(rng, 3, 25);
    const b = r(rng, 2, 20);
    const c = r(rng, 2, 8);
    return [`(${a}+${b})×${c}＝（　　）`, `${(a + b) * c}`];
  },
};

export const FractionGenerator = {
  type: 'FRACTION',
  subject: Subject.MATH,
  minGrade: 4,
  maxGrade: 9,
  topic: '分数运算',

  make(ctx) {
    const rng = ctx.rng;
    const d = r(rng, 4, Math.max(6, GradeProfiles.of(ctx.grade).fractionMaxDen));
    const a = r(rng, 1, d - 1);
    const b = r(rng, 1, d - a);
    const n = a + b;
    // 加法结果要约分；减法结果就是 b/d，另算一次约分
    const g = gcd(n, d);
    const rn = idiv(n, g);
    const rd = idiv(d, g);
    const addAns = rd === 1 ? `${rn}` : `${rn}/${rd}`;
    if (rng.nextBoolean()) {
      return [`${a}/${d} + ${b}/${d}＝（　　）`, addAns];
    }
    const stem = `${n}/${d} - ${a}/${d}＝（　　）`;
    const g2 = gcd(b, d);
    const ans = idiv(d, g2) === 1 ? `${idiv(b, g2)}` : `${idiv(b, g2)}/${idiv(d, g2)}`;
    return [stem, ans];
  },
};

export const EquationGenerator = {
  type: 'EQUATION',
  subject: Subject.MATH,
  minGrade: 5,
  maxGrade: 9,
  topic: '简易方程',

  make(ctx) {
    const rng = ctx.rng;
    switch (r(rng, 0, 2)) {
      case 0: {
        const x = r(rng, 2, 30);
        const a = r(rng, 2, 30);
        return [`解方程：x+${a}=${x + a}，x＝（　　）`, `${x}`];
      }
      case 1: {
        const x = r(rng, 2, 20);
        const a = r(rng, 2, 9);
        return [`解方程：${a}x=${a * x}，x＝（　　）`, `${x}`];
      }
      default: {
        const x = r(rng, 2, 20);
        const a = r(rng, 2, 9);
        const c = r(rng, 1, 20);
        return [`解方程：${a}x+${c}=${a * x + c}，x＝（　　）`, `${x}`];
      }
    }
  },
};

// ------------------------------------------------------------------ 应用题

export const WordProblemGenerator = {
  type: 'WORD_PROBLEM',
  subject: Subject.MATH,
  minGrade: 1,
  maxGrade: 9,
  topic: '应用题',

  make(ctx) {
    const rng = ctx.rng;
    // b 先被 a 卡住，保证「借走 b 本」「用去 b 米」这类减法不会出现负数
    const a = r(rng, 6, 48);
    const b = r(rng, 2, Math.min(a, 20));
    const c = r(rng, 2, 12);
    switch (r(rng, 0, 5)) {
      case 0:
        return [`小明有 ${a} 个苹果，又买了 ${b} 个，一共有多少个？`, `${a + b} 个`];
      case 1:
        return [`书架上有 ${a + b} 本书，借走 ${b} 本，还剩多少本？`, `${a} 本`];
      case 2:
        return [`每盒有 ${a} 支铅笔，${c} 盒一共有多少支？`, `${a * c} 支`];
      case 3:
        return [`把 ${a * c} 颗糖平均分给 ${c} 个小朋友，每人分到几颗？`, `${a} 颗`];
      case 4:
        return [`一根绳子长 ${a} 米，用去 ${b} 米，还剩多少米？`, `${a - b} 米`];
      default:
        return [`小红每天读 ${b} 页书，读了 ${c} 天，一共读了多少页？`, `${b * c} 页`];
    }
  },
};

// ------------------------------------------------------------------ 初中数学

export const RationalGenerator = {
  type: 'RATIONAL',
  subject: Subject.MATH,
  minGrade: 7,
  maxGrade: 9,
  topic: '有理数运算',

  make(ctx) {
    const rng = ctx.rng;
    const a = r(rng, 2, 30);
    const b = r(rng, 2, 30);
    switch (r(rng, 0, 3)) {
      case 0:
        return [`(-${a})+${b}＝（　　）`, `${-a + b}`];
      case 1:
        return [`(-${a})-${b}＝（　　）`, `${-a - b}`];
      case 2:
        return [`(-${a})×${b}＝（　　）`, `${-a * b}`];
      default: {
        // 被除数凑成 -a*q，保证除得尽且答案仍为负
        const q = r(rng, 2, 9);
        return [`(-${a * q})÷${a}＝（　　）`, `${-q}`];
      }
    }
  },
};

export const AlgebraGenerator = {
  type: 'ALGEBRA',
  subject: Subject.MATH,
  minGrade: 7,
  maxGrade: 9,
  topic: '整式与方程',

  make(ctx) {
    const rng = ctx.rng;
    if (rng.nextBoolean()) {
      const a = r(rng, 2, 9);
      const b = r(rng, 2, 9);
      // c 小于 a+b，保证合并后系数仍为正
      const c = r(rng, 1, a + b - 1);
      return [`化简：${a}x+${b}x-${c}x＝（　　）`, `${a + b - c}x`];
    }
    const x = r(rng, 2, 15);
    const a = r(rng, 2, 9);
    const c = r(rng, 1, 20);
    return [`解方程：${a}x+${c}=${a * x + c}，x＝（　　）`, `${x}`];
  },
};

export const RadicalGenerator = {
  type: 'RADICAL',
  subject: Subject.MATH,
  minGrade: 8,
  maxGrade: 9,
  topic: '二次根式',

  make(ctx) {
    const rng = ctx.rng;
    const a = r(rng, 2, 12);
    const b = r(rng, 2, 9);
    const a2 = a * a;
    const b2 = b * b;
    switch (r(rng, 0, 2)) {
      case 0:
        return [`√${a2} × √${b2}＝（　　）`, `${a * b}`];
      case 1:
        return [`√${a2} + √${b2}＝（　　）`, `${a + b}`];
      default: {
        const k = r(rng, 2, 6);
        return [`√${a2 * k * k} ÷ √${a2}＝（　　）`, `${k}`];
      }
    }
  },
};

// ------------------------------------------------------------------ 口算补两个题型

/** 求未知项：把等式里的一项挖空，答案就是那一项 —— 考的是逆运算 */
export const OralMissingGenerator = {
  type: 'ORAL_MISSING',
  subject: Subject.MATH,
  minGrade: 1,
  maxGrade: 6,
  topic: '四则运算',

  make(ctx) {
    const p = GradeProfiles.of(ctx.grade);
    const rng = ctx.rng;

    // 逆运算靠心算，数域必须跟着年级小步走：
    // 直接拿 addMax/2，四年级会蹦出「4013-（　）=2764」，那是笔算不是口算
    let max;
    switch (ctx.grade) {
      case 1: max = 10; break;
      case 2: max = 20; break;
      case 3: max = 50; break;
      case 4: max = 100; break;
      default: max = 200;
    }

    switch (rng.nextInt(p.mulMax > 0 ? 4 : 3)) {
      case 0: {
        // a + （ ）= c  → 答案 b
        const a = r(rng, 1, max);
        const b = r(rng, 1, max);
        return [`${a}+（　）=${a + b}`, `${b}`];
      }
      case 1: {
        // （ ）+ b = c  → 答案 a
        const a = r(rng, 1, max);
        const b = r(rng, 1, max);
        return [`（　）+${b}=${a + b}`, `${a}`];
      }
      case 2: {
        // c - （ ）= a  → 答案 b
        const a = r(rng, 1, max);
        const b = r(rng, 1, max);
        return [`${a + b}-（　）=${a}`, `${b}`];
      }
      default: {
        // a × （ ）= c  → 答案 b（二年级起 mulMax > 0 才会出现）
        const a = r(rng, 2, Math.max(2, p.mulMax));
        const b = r(rng, 2, Math.max(2, p.mulMax));
        return [`${a}×（　）=${a * b}`, `${b}`];
      }
    }
  },
};

/** 有余数除法：被除数 = 除数 × 商 + 余数，余数必须小于除数 */
export const OralRemainderGenerator = {
  type: 'ORAL_REMAINDER',
  subject: Subject.MATH,
  minGrade: 3,
  maxGrade: 6,
  topic: '除法与余数',

  make(ctx) {
    const rng = ctx.rng;
    const divisor = r(rng, 2, 9);
    const quotient = r(rng, 2, 9);
    // 余数 1..divisor-1：特意不取 0，整除题已经有「表内除法」，这里只练带余数
    const remainder = r(rng, 1, divisor - 1);
    const dividend = divisor * quotient + remainder;
    const stem = `${dividend}÷${divisor}=（　）……（　）`;
    return [stem, `${quotient}……${remainder}`];
  },
};

// ------------------------------------------------------------------ 导出

export const generators = [
  OralGenerator,
  VerticalGenerator,
  ExpressionGenerator,
  FractionGenerator,
  EquationGenerator,
  WordProblemGenerator,
  RationalGenerator,
  AlgebraGenerator,
  RadicalGenerator,
  OralMissingGenerator,
  OralRemainderGenerator,
];
