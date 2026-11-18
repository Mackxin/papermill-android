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
