/**
 * 小学数学知识点专项出题器 —— 逐条对照 shijuan.on2on.cn 的 15 个知识点页面移植。
 *
 * 那个站每个知识点一页，页面里的 generate() 就是它全部的出题规则；这里把
 * **数字范围、进位/退位条件、去重方式**原样搬过来，只把「画线」换成我们自己的
 * 「（　　）」写法，并把答案一起算出来（它那站是生成完再显示答案，同一套数）。
 *
 * 一条硬约定：每个函数只造一道题，造不出来返回 null 让框架重试 ——
 * 「20以内退位减法」这种带条件的范围，硬塞不满足条件的数就变成错题。
 */

import { stableHash } from '../core/models.js';

const r = (rng, from, to) => rng.nextInt(from, to);

function gcd(a, b) {
  let x = Math.abs(a);
  let y = Math.abs(b);
  while (y) {
    const t = x % y;
    x = y;
    y = t;
  }
  return x === 0 ? 1 : x;
}

/** 同分母分数约分：分母约成 1 时直接印整数（3/3 印成 1，不印 1/1） */
function simplify(num, den) {
  const g = gcd(num, den);
  const n = Math.trunc(num / g);
  const d = Math.trunc(den / g);
  return d === 1 ? `${n}` : `${n}/${d}`;
}

// ------------------------------------------------------------------ 一年级

/** 10以内数的比较：两个 0~10 的数填 ＞ ＜ ＝ */
function compare10(rng) {
  const a = r(rng, 0, 10);
  const b = r(rng, 0, 10);
  const sym = a > b ? '＞' : a < b ? '＜' : '＝';
  return [`${a} ○ ${b}（填＞＜＝）`, sym];
}

/** 10以内加法：和不超过 10 */
function add10(rng) {
  const a = r(rng, 0, 10);
  const b = r(rng, 0, 10 - a);
  return [`${a}+${b}=（　　）`, `${a + b}`];
}

/** 10以内减法：被减数不超过 10，不会减出负数 */
function sub10(rng) {
  const a = r(rng, 0, 10);
  const b = r(rng, 0, a);
  return [`${a}-${b}=（　　）`, `${a - b}`];
}

/** 20以内退位减法：只出个位不够减、必须退位的那种 */
function sub20(rng) {
  const a = r(rng, 11, 20);
  const b = r(rng, 2, 9);
  // 个位够减就不是退位题了 —— 这条判断是「20以内退位减法」和「20以内减法」的区别
  if (a % 10 >= b) return null;
  return [`${a}-${b}=（　　）`, `${a - b}`];
}

/** 100以内不进退位加减法：个位、十位都不进位也不退位 */
function addSub100(rng) {
  if (rng.nextBoolean()) {
    const aT = r(rng, 1, 9);
    const aU = r(rng, 0, 9);
    const bT = r(rng, 1, 9);
    const bU = r(rng, 0, 9);
    // 个位进、十位也进就不叫「不进退位」了；两条一起卡，出来的题才是纯口算
    if (aU + bU >= 10) return null;
    if (aT + bT >= 10) return null;
    return [`${aT * 10 + aU}+${bT * 10 + bU}=（　　）`, `${aT * 10 + aU + bT * 10 + bU}`];
  }
  const aT = r(rng, 2, 9);
  const aU = r(rng, 1, 9);
  const bT = r(rng, 1, aT);
  const bU = r(rng, 0, aU);
  const a = aT * 10 + aU;
  const b = bT * 10 + bU;
  if (b === 0) return null;
  return [`${a}-${b}=（　　）`, `${a - b}`];
}

// ------------------------------------------------------------------ 二年级

/** 100以内进退位加减法：加要进位、减要退位 */
function addSub100Adv(rng) {
  if (rng.nextBoolean()) {
    const a = r(rng, 11, 89);
    const b = r(rng, 2, 99 - a);
    if (a % 10 + (b % 10) < 10) return null;
    return [`${a}+${b}=（　　）`, `${a + b}`];
  }
  const a = r(rng, 12, 99);
  const b = r(rng, 2, a);
  if (a % 10 >= b % 10) return null;
  return [`${a}-${b}=（　　）`, `${a - b}`];
}

/** 表内乘法：1~9 的口诀 */
function mulTable(rng) {
  const a = r(rng, 2, 9);
  const b = r(rng, 2, 9);
  return [`${a}×${b}=（　　）`, `${a * b}`];
}

/** 表内除法：用口诀求商，一定除得尽 */
function divTable(rng) {
  const b = r(rng, 2, 9);
  const q = r(rng, 2, 9);
  return [`${b * q}÷${b}=（　　）`, `${q}`];
}

/** 有余数的除法：余数一定小于除数，商与余数分两格写 */
function divRemain(rng) {
  const b = r(rng, 2, 9);
  const q = r(rng, 1, 9);
  const rem = r(rng, 1, b - 1);
  const a = b * q + rem;
  if (a > 99) return null;
  return [`${a}÷${b}=（　）……（　）`, `${q}……${rem}`];
}

/** 乘加 / 乘减：先乘后加减，结果不出 100 */
function mulAddSub(rng) {
  const a = r(rng, 2, 9);
  const b = r(rng, 2, 9);
  const p = a * b;
  if (rng.nextBoolean()) {
    const cMax = Math.min(20, 100 - p);
    if (cMax < 1) return null;
    const c = r(rng, 1, cMax);
    return [`${a}×${b}+${c}=（　　）`, `${p + c}`];
  }
  const c = r(rng, 1, Math.min(20, p - 1));
  return [`${a}×${b}-${c}=（　　）`, `${p - c}`];
}

// ------------------------------------------------------------------ 三年级

/** 万以内加减法（笔算）：留竖式空，加的和、减的差都不超过 9999 */
function addSubWan(rng) {
  if (rng.nextBoolean()) {
    const a = r(rng, 100, 9899);
    const b = r(rng, 100, 9999 - a);
    return [`${a}+${b}＝（　　）`, `${a + b}`];
  }
  const a = r(rng, 200, 9999);
  const b = r(rng, 100, a - 1);
  return [`${a}-${b}＝（　　）`, `${a - b}`];
}

/** 多位数乘一位数：两位数、三位数各占一半 */
function mulMore(rng) {
  const a = rng.nextBoolean() ? r(rng, 10, 99) : r(rng, 100, 999);
  const b = r(rng, 2, 9);
  const p = a * b;
  if (p > 10000) return null;
  return [`${a}×${b}＝（　　）`, `${p}`];
}

/** 长方形正方形周长：用文字给条件，孩子自己列式 */
function perimeter(rng) {
  if (rng.nextBoolean()) {
    const len = r(rng, 6, 40);
    const wid = r(rng, 4, len);
    const ans = 2 * (len + wid);
    if (ans > 200) return null;
    return [
      `一个长方形，长 ${len} 厘米，宽 ${wid} 厘米，周长是多少厘米？`,
      `${ans} 厘米`,
    ];
  }
  const side = r(rng, 4, 50);
  const ans = 4 * side;
  if (ans > 200) return null;
  return [`一个正方形，边长 ${side} 厘米，周长是多少厘米？`, `${ans} 厘米`];
}

/** 分数简单加减法：同分母，结果约成最简 */
function fractionAddSub(rng) {
  const den = r(rng, 3, 10);
  if (rng.nextBoolean()) {
    const a = r(rng, 1, den - 2);
    const c = r(rng, 1, den - a - 1);
    return [`${a}/${den} + ${c}/${den}＝（　　）`, simplify(a + c, den)];
  }
  const a = r(rng, 2, den - 1);
  const c = r(rng, 1, a - 1);
  return [`${a}/${den} - ${c}/${den}＝（　　）`, simplify(a - c, den)];
}

/** 两位数乘两位数：积不超过 9999 */
function mulTwo(rng) {
  const a = r(rng, 11, 99);
  const b = r(rng, 11, 99);
  const p = a * b;
  if (p > 9999) return null;
  return [`${a}×${b}＝（　　）`, `${p}`];
}

// ------------------------------------------------------------------ 四年级

/** 三位数乘两位数：积控制在五位数以内，够一个竖式的书写量 */
function mulThree(rng) {
  const a = r(rng, 102, 998);
  const b = r(rng, 11, 98);
  return [`${a}×${b}＝（　　）`, `${a * b}`];
}

/** 除数是两位数的除法：一定除得尽，商是两位数 */
function divTwo(rng) {
  const b = r(rng, 11, 89);
  const q = r(rng, 10, Math.min(99, Math.floor(9999 / b)));
  if (q < 10) return null;
  return [`${b * q}÷${b}＝（　　）`, `${q}`];
}

/**
 * 一位小数加减法。
 *
 * 全部换算成「十分位上的整数」再算 —— 0.1+0.2 在 JS 里是 0.30000000000000004，
 * 直接拼进题干就是一串尾巴，家长看一眼就知道这软件不靠谱。
 */
function decimalAddSub(rng) {
  if (rng.nextBoolean()) {
    const a = r(rng, 1, 900);
    const b = r(rng, 1, 1000 - a);
    return [`${(a / 10).toFixed(1)}+${(b / 10).toFixed(1)}＝（　　）`, ((a + b) / 10).toFixed(1)];
  }
  const a = r(rng, 11, 999);
  const b = r(rng, 1, a - 1);
  return [`${(a / 10).toFixed(1)}-${(b / 10).toFixed(1)}＝（　　）`, ((a - b) / 10).toFixed(1)];
}

/** 整数四则混合：先乘除后加减、带括号的也要先算括号，四种骨架轮着出 */
function mixedCalc(rng) {
  const shape = rng.nextInt(0, 3);
  if (shape === 0) {
    const a = r(rng, 2, 9);
    const b = r(rng, 11, 60);
    const c = r(rng, 11, 40);
    return [`${a}×(${b}+${c})＝（　　）`, `${a * (b + c)}`];
  }
  if (shape === 1) {
    const a = r(rng, 2, 9);
    const b = r(rng, 11, 60);
    const c = r(rng, 11, a * b - 1);
    if (c >= a * b) return null;
    return [`${a}×${b}-${c}＝（　　）`, `${a * b - c}`];
  }
  if (shape === 2) {
    const d = r(rng, 12, 48);
    const q = r(rng, 2, 9);
    const add = r(rng, 11, 90);
    return [`${d * q}÷${d}+${add}＝（　　）`, `${q + add}`];
  }
  const b = r(rng, 2, 9);
  const c = r(rng, 11, 40);
  const a = r(rng, b * c + 10, 999);
  return [`${a}-${b}×${c}＝（　　）`, `${a - b * c}`];
}

/** 万以上数的比较：五位数与五位数、六位数与六位数混着来 */
function bigCompare(rng) {
  const digits = rng.nextBoolean() ? 5 : 6;
  const lo = digits === 5 ? 10000 : 100000;
  const hi = digits === 5 ? 99999 : 999999;
  const a = r(rng, lo, hi);
  const b = r(rng, lo, hi);
  const sym = a > b ? '＞' : a < b ? '＜' : '＝';
  return [`${a} ○ ${b}（填＞＜＝）`, sym];
}

// ------------------------------------------------------------------ 五年级

/** 小数乘整数：一位小数乘一位或两位数，积仍是一位小数 */
function decimalMul(rng) {
  const a = r(rng, 2, 99);           // 0.2 ~ 9.9
  const b = r(rng, 2, 99);
  return [`${(a / 10).toFixed(1)}×${b}＝（　　）`, ((a * b) / 10).toFixed(1)];
}

/** 小数除以整数：被除数一位小数，除得尽 */
function decimalDiv(rng) {
  const b = r(rng, 2, 9);
  const q = r(rng, 11, 99);          // 商 1.1 ~ 9.9
  return [`${((b * q) / 10).toFixed(1)}÷${b}＝（　　）`, (q / 10).toFixed(1)];
}

/** 最大公因数 / 最小公倍数：只出真能约的（公因数 ≥ 2），互质对没练头 */
function gcdLcm(rng) {
  const a = r(rng, 4, 48);
  const b = r(rng, 4, 48);
  const g = gcd(a, b);
  if (g < 2) return null;
  if (rng.nextBoolean()) return [`${a} 和 ${b} 的最大公因数是（　　）`, `${g}`];
  return [`${a} 和 ${b} 的最小公倍数是（　　）`, `${(a / g) * b}`];
}

/** 约分：分子分母都带公因数才出，答案按最简式印 */
function reduceFraction(rng) {
  const den = r(rng, 3, 9) * r(rng, 2, 8);
  const num = r(rng, 2, den - 1);
  if (num >= den) return null;
  if (gcd(num, den) < 2) return null;
  return [`${num}/${den}＝（　　）（约成最简分数）`, simplify(num, den)];
}

/**
 * 异分母分数加减。
 *
 * 分母固定成倍数关系（3 与 6、4 与 8 这种），通分只要变一个分母 ——
 * 随便挑两个互质的 7 和 9，通分出来分母 63，五年的孩子心算不了，那是为难人。
 */
function fractionAddSubAdv(rng) {
  const base = r(rng, 2, 6);
  const den2 = base * r(rng, 2, 3);
  const n1 = r(rng, 1, base - 1);
  const n2 = r(rng, 1, den2 - 1);
  if (rng.nextBoolean()) {
    const num = n1 * (den2 / base) + n2;
    if (num > den2 * 2) return null;                 // 和超过 2 就太长了
    return [`${n1}/${base} + ${n2}/${den2}＝（　　）`, simplify(num, den2)];
  }
  const num = n1 * (den2 / base) - n2;
  if (num <= 0) return null;                          // 不够减就不是五年级的题了
  return [`${n1}/${base} - ${n2}/${den2}＝（　　）`, simplify(num, den2)];
}

/** 平行四边形 / 三角形 / 梯形面积：给文字条件，孩子自己列式 */
function polygonArea(rng) {
  const shape = rng.nextInt(0, 2);
  if (shape === 0) {
    const base = r(rng, 4, 20);
    const h = r(rng, 3, 15);
    return [`一个平行四边形，底 ${base} 厘米，高 ${h} 厘米，面积是多少平方厘米？`, `${base * h} 平方厘米`];
  }
  if (shape === 1) {
    const base = r(rng, 4, 20);
    const h = r(rng, 2, 16);
    if ((base * h) % 2) return null;                  // 三角形面积要整数，别出 37.5
    return [`一个三角形，底 ${base} 厘米，高 ${h} 厘米，面积是多少平方厘米？`, `${(base * h) / 2} 平方厘米`];
  }
  const up = r(rng, 3, 12);
  const down = up + r(rng, 2, 10);
  const h = r(rng, 2, 12);
  const twice = (up + down) * h;
  if (twice % 2) return null;
  return [
    `一个梯形，上底 ${up} 厘米，下底 ${down} 厘米，高 ${h} 厘米，面积是多少平方厘米？`,
    `${twice / 2} 平方厘米`,
  ];
}

/** 长方体 / 正方体体积：正方体只出棱长 3~12（再大就成一串四位数了） */
function cuboidVolume(rng) {
  if (rng.nextBoolean()) {
    const a = r(rng, 3, 12);
    return [`一个正方体，棱长 ${a} 厘米，体积是多少立方厘米？`, `${a * a * a} 立方厘米`];
  }
  const l = r(rng, 3, 15);
  const w = r(rng, 3, 12);
  const h = r(rng, 2, 10);
  return [
    `一个长方体，长 ${l} 厘米，宽 ${w} 厘米，高 ${h} 厘米，体积是多少立方厘米？`,
    `${l * w * h} 立方厘米`,
  ];
}

/** 解简易方程：ax±b=c 与 ax=b 两种，解一定是整数 */
function simpleEquation(rng) {
  const a = r(rng, 2, 9);
  const x = r(rng, 2, 20);
  if (rng.nextBoolean()) {
    const b = r(rng, 1, 40);
    return [`${a}x + ${b} = ${a * x + b}`, `x = ${x}`];
  }
  const b = r(rng, 1, a * x - 1);
  return [`${a}x - ${b} = ${a * x - b}`, `x = ${x}`];
}

// ------------------------------------------------------------------ 六年级

/** 分数乘法：真分数相乘，结果约成最简 */
function fractionMul(rng) {
  const b = r(rng, 2, 9);
  const d = r(rng, 2, 9);
  const a = r(rng, 1, b - 1);
  const c = r(rng, 1, d - 1);
  return [`${a}/${b} × ${c}/${d}＝（　　）`, simplify(a * c, b * d)];
}

/** 分数除法：除以一个分数就是乘它的倒数 */
function fractionDiv(rng) {
  const b = r(rng, 2, 9);
  const d = r(rng, 2, 9);
  const a = r(rng, 1, b - 1);
  const c = r(rng, 1, d - 1);
  return [`${a}/${b} ÷ ${c}/${d}＝（　　）`, simplify(a * d, b * c)];
}

/** 求一个数的百分之几：百分数只用能口算的那几个 */
const PERCENTS = [10, 20, 25, 40, 50, 60, 75, 80];

function percentOf(rng) {
  const p = PERCENTS[rng.nextInt(0, PERCENTS.length - 1)];
  const n = r(rng, 20, 200);
  const v = (n * p) / 100;
  if (!Number.isInteger(v)) return null;
  return [`${n} 的 ${p}% 是（　　）`, `${v}`];
}

/** 保留两位小数但要吃掉尾零：3.14 印「3.14」，1256 印「1256」而不是「1256.00」 */
function round2(n) {
  return `${Math.round(n * 100) / 100}`;
}

/** 圆的周长与面积：半径 / 直径 × 周长 / 面积 四种问法；π 统一取 3.14 */
function circle(rng) {
  const r0 = r(rng, 1, 20);
  const d = r0 * 2;
  const kind = rng.nextInt(0, 3);
  if (kind === 0) {
    return [`一个圆的半径是 ${r0} 厘米，周长是多少厘米？`, `${round2(2 * 3.14 * r0)} 厘米`];
  }
  if (kind === 1) {
    return [`一个圆的直径是 ${d} 厘米，周长是多少厘米？`, `${round2(3.14 * d)} 厘米`];
  }
  if (kind === 2) {
    return [`一个圆的半径是 ${r0} 厘米，面积是多少平方厘米？`, `${round2(3.14 * r0 * r0)} 平方厘米`];
  }
  return [`一个圆的直径是 ${d} 厘米，面积是多少平方厘米？`, `${round2(3.14 * r0 * r0)} 平方厘米`];
}

/** 知识点 key → 造一道题。key 必须和 models.js 的 MATH_POINTS 一一对应 */
export const POINT_MAKERS = {
  'g1.compare': compare10,
  'g1.add10': add10,
  'g1.sub10': sub10,
  'g1.sub20': sub20,
  'g1.addSub100': addSub100,
  'g2.addSub100Adv': addSub100Adv,
  'g2.mul': mulTable,
  'g2.div': divTable,
  'g2.divRemain': divRemain,
  'g2.mulAddSub': mulAddSub,
  'g3.addSubWan': addSubWan,
  'g3.mulMore': mulMore,
  'g3.perimeter': perimeter,
  'g3.fractionAddSub': fractionAddSub,
  'g3.mulTwo': mulTwo,
  'g4.mulThree': mulThree,
  'g4.divTwo': divTwo,
  'g4.decimalAddSub': decimalAddSub,
  'g4.mixedCalc': mixedCalc,
  'g4.bigCompare': bigCompare,
  'g5.decimalMul': decimalMul,
  'g5.decimalDiv': decimalDiv,
  'g5.gcdLcm': gcdLcm,
  'g5.reduce': reduceFraction,
  'g5.fractionAddSubAdv': fractionAddSubAdv,
  'g5.polygonArea': polygonArea,
  'g5.cuboidVolume': cuboidVolume,
  'g5.equation': simpleEquation,
  'g6.fractionMul': fractionMul,
  'g6.fractionDiv': fractionDiv,
  'g6.percent': percentOf,
  'g6.circle': circle,
};

/**
 * 一个知识点的整批题。
 *
 * 去重按题干：同一个算式在一页上出现两次，家长第一反应是「这软件在重复」。
 * 重试上限跟着题量走（每题库里能造出的不同题有上限，比如表内乘法 64 道），
 * 取不到就如实少给，绝不死循环。
 */
export function makePointItems(point, count, rng) {
  const make = POINT_MAKERS[point.key];
  if (!make) return [];
  const want = Math.min(Math.max(count, 1), 50);
  const seen = new Set();
  const out = [];
  const maxTries = want * 80 + 80;
  for (let i = 0; i < maxTries && out.length < want; i++) {
    let pair = null;
    try {
      pair = make(rng);
    } catch (err) {
      pair = null;
    }
    if (!pair) continue;
    const stem = String(pair[0] ?? '').trim();
    const answer = String(pair[1] ?? '').trim();
    if (!stem || !answer) continue;
    const key = stableHash(stem);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push({ no: 0, type: point.render, stem, answer, topic: point.label, figure: null });
  }
  return out;
}
