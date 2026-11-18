/**
 * 语文 / 英语 / 理化 的「随机造一题」生成器。
 *
 * 对应 Kotlin 的 LanguageScienceGenerators.kt：
 *   PinyinGenerator / HanziGenerator / DictationGenerator /
 *   EnglishWordGenerator / PhysicsGenerator / ChemistryGenerator
 *
 * 这些题源是**无限题源**（词表虽是有限个词，但每题有 3~4 种题干模板，
 * 同一个词能出成不同问法），所以只实现 make()，由框架负责
 * 循环 count 次、stableHash 去重、丢弃空题干。
 *
 * 为什么把词表内联在这里：App 的 WordBank.kt 只存在于 Kotlin 端，
 * 网页版 data/content.json 没有导出字词表。为了不改别的文件、
 * 也不额外拉一次网络请求，这里按原表逐条搬过来（词条与 minGrade 完全一致）。
 */

import { Subject } from '../core/models.js';

// ------------------------------------------------------------------ 字词表

/**
 * 对应 Kotlin CnWord(word, pinyin, minGrade)。
 * 顺序必须与 WordBank.chinese 完全一致 —— chineseFor 的轮换偏移是
 * 用「本年级第一个新词」在**原表里的下标**算出来的，改顺序会改变题目分布。
 */
const CN_WORDS = [
  ['太阳', 'tài yáng', 1], ['月亮', 'yuè liang', 1], ['花朵', 'huā duǒ', 1],
  ['小鸟', 'xiǎo niǎo', 1], ['白云', 'bái yún', 1], ['同学', 'tóng xué', 1],
  ['老师', 'lǎo shī', 1], ['书本', 'shū běn', 1],
  ['朋友', 'péng you', 2], ['春天', 'chūn tiān', 2], ['河流', 'hé liú', 2],
  ['森林', 'sēn lín', 2], ['城市', 'chéng shì', 2], ['天空', 'tiān kōng', 2],
  ['观察', 'guān chá', 3], ['记录', 'jì lù', 3], ['实验', 'shí yàn', 3],
  ['准备', 'zhǔn bèi', 3],
  ['骄傲', 'jiāo ào', 4], ['谦虚', 'qiān xū', 4], ['敏捷', 'mǐn jié', 4],
  ['灿烂', 'càn làn', 4],
  ['徘徊', 'pái huái', 5], ['慷慨', 'kāng kǎi', 5], ['究竟', 'jiū jìng', 5],
  ['蜿蜒', 'wān yán', 6], ['静谧', 'jìng mì', 6],
  ['憧憬', 'chōng jǐng', 7], ['彷徨', 'páng huáng', 7],
  ['睿智', 'ruì zhì', 8], ['磅礴', 'páng bó', 8],
  ['缜密', 'zhěn mì', 9], ['砥砺', 'dǐ lì', 9],
  ['清晨', 'qīng chén', 2], ['教室', 'jiào shì', 2],
  ['海洋', 'hǎi yáng', 3], ['温暖', 'wēn nuǎn', 3],
  ['勇敢', 'yǒng gǎn', 4], ['诚实', 'chéng shí', 4],
  ['辽阔', 'liáo kuò', 5], ['朴素', 'pǔ sù', 5],
  ['凝视', 'níng shì', 6], ['丰盛', 'fēng shèng', 6],
  ['浩瀚', 'hào hàn', 7],
  ['沉淀', 'chén diàn', 8],
  ['严谨', 'yán jǐn', 9],
  // ---- 低年级补充词（v0.20.0）----
  // 拼音题改成按「素材」去重之后，一年级原本只有 8 个词，要 30 道就只能出 8 道；
  // 补到 30 个才够出满一大题。**一律追加在末尾**：前面所有下标不变，
  // rotatedPool 的轮换偏移与 Kotlin 端仍然一一对应。
  ['大地', 'dà dì', 1],
  ['山水', 'shān shuǐ', 1],
  ['火车', 'huǒ chē', 1],
  ['汽车', 'qì chē', 1],
  ['水果', 'shuǐ guǒ', 1],
  ['苹果', 'píng guǒ', 1],
  ['西瓜', 'xī guā', 1],
  ['铅笔', 'qiān bǐ', 1],
  ['书包', 'shū bāo', 1],
  ['黑板', 'hēi bǎn', 1],
  ['爸爸', 'bà ba', 1],
  ['妈妈', 'mā ma', 1],
  ['眼睛', 'yǎn jing', 1],
  ['小鸡', 'xiǎo jī', 1],
  ['小鱼', 'xiǎo yú', 1],
  ['树叶', 'shù yè', 1],
  ['雨点', 'yǔ diǎn', 1],
  ['雪花', 'xuě huā', 1],
  ['早上', 'zǎo shang', 1],
  ['中午', 'zhōng wǔ', 1],
  ['种子', 'zhǒng zi', 1],
  ['田野', 'tián yě', 1],
  ['公园', 'gōng yuán', 2],
  ['校园', 'xiào yuán', 2],
  ['操场', 'cāo chǎng', 2],
  ['故事', 'gù shi', 2],
  ['图画', 'tú huà', 2],
  ['音乐', 'yīn yuè', 2],
  ['游戏', 'yóu xì', 2],
  ['家乡', 'jiā xiāng', 2],
  ['农村', 'nóng cūn', 2],
  ['麦苗', 'mài miáo', 2],
  ['稻谷', 'dào gǔ', 2],
].map(([word, pinyin, minGrade]) => ({ word, pinyin, minGrade }));

const EN_WORDS = [
  ['apple', '苹果', 3], ['book', '书', 3], ['cat', '猫', 3], ['dog', '狗', 3],
  ['red', '红色', 3], ['pen', '钢笔', 3],
  ['school', '学校', 4], ['teacher', '老师', 4], ['friend', '朋友', 4], ['water', '水', 4],
  ['family', '家庭', 5], ['weather', '天气', 5], ['holiday', '假期', 5], ['library', '图书馆', 5],
  ['because', '因为', 6], ['different', '不同的', 6], ['important', '重要的', 6], ['remember', '记得', 6],
  ['environment', '环境', 7], ['experience', '经历', 7], ['knowledge', '知识', 7], ['science', '科学', 7],
  ['attention', '注意力', 8], ['decision', '决定', 8], ['encourage', '鼓励', 8], ['progress', '进步', 8],
  ['opportunity', '机会', 9], ['responsible', '负责的', 9], ['technology', '技术', 9], ['challenge', '挑战', 9],
  ['window', '窗户', 3], ['orange', '橙子', 3],
  ['student', '学生', 4], ['morning', '早晨', 4],
  ['village', '村庄', 5], ['mountain', '山', 5],
  ['exercise', '练习', 6], ['question', '问题', 6],
  ['culture', '文化', 7], ['medicine', '药', 8], ['industry', '工业', 9],
].map(([en, zh, minGrade]) => ({ en, zh, minGrade }));

// ------------------------------------------------------------------ 取池

/**
 * 对应 WordBank.chineseFor / englishFor：
 * 取所有 minGrade <= 当前年级的词，再从「本年级第一个新词」处轮换。
 * 为什么轮换：高年级词池更大，但若总是从头取，随机抽题仍然只会撞到老词；
 * 从新词位置起步，能把本年级该学的词顶到前面。
 */
function rotatedPool(list, grade) {
  const pool = list.filter((w) => w.minGrade <= grade);
  if (!pool.length) return pool;
  const firstNew = list.findIndex((w) => w.minGrade === grade);
  const offset = firstNew > 0 ? firstNew % pool.length : 0;
  return pool.slice(offset).concat(pool.slice(0, offset));
}

/**
 * 从池子里取一个**本卷还没用过**的词。
 *
 * 为什么不能靠框架去重：框架按题干 hash 拦重复，而这几道题各有 3~4 种问法 ——
 * 「拼音 tóng xué，对应的词语是（　）」和「根据拼音写出词语：tóng xué（　）」
 * 题干不一样、hash 不一样，可学生一眼就看出是同一道题（用户截图 31 与 34 撞的就是这个）。
 * 所以这里按**素材本身**（词）记一份账：用过的踢出去；词用完了返回 null，
 * 宁可这一大题少出两题，也不在同一张卷子上印两遍同一个词。
 */
function nextFreshWord(ctx, pool, keyOf = (w) => w.word) {
  const used = (ctx.usedKeys ??= new Set());
  const fresh = pool.filter((w) => !used.has(keyOf(w)));
  if (!fresh.length) return null;
  const w = fresh[ctx.rng.nextInt(fresh.length)];
  used.add(keyOf(w));
  return w;
}

// ------------------------------------------------------------------ 语文

/** 看拼音写词语 */
export const pinyinGenerator = {
  type: 'PINYIN',
  subject: Subject.CHINESE,
  minGrade: 1,
  maxGrade: 9,
  topic: '拼音与字词',
  make(ctx) {
    const pool = rotatedPool(CN_WORDS, ctx.grade);
    if (!pool.length) return null;
    const w = nextFreshWord(ctx, pool);
    if (!w) return null;
    // 三种问法只是措辞不同，素材还是同一个词 —— 靠 nextFreshWord 保证本卷只出现一次
    const stem = [
      // 题干刻意压短：两栏排版时单栏只有 ~247pt，「根据拼音写出词语：…，对应的词语是（　　）」
      // 这类长句会把结尾的「）」单独挤到下一行（用户当场指着 17、25 问为什么换行）。
      `看拼音写词 ${w.pinyin}（　　）`,
      `读拼音写词 ${w.pinyin}（　　）`,
      `拼音 ${w.pinyin}（　　）`,
    ][ctx.rng.nextInt(3)];
    return [stem, w.word];
  },
};

/** 生字注音：答案取词的第一个音节 */
export const hanziGenerator = {
  type: 'HANZI',
  subject: Subject.CHINESE,
  minGrade: 1,
  maxGrade: 9,
  topic: '字音',
  make(ctx) {
    const pool = rotatedPool(CN_WORDS, ctx.grade);
    if (!pool.length) return null;
    // 题干只露出首字（「给「太」注音」），所以按首字记账：小鸟 / 小鱼 都出「小」就是重复题
    const w = nextFreshWord(ctx, pool, (x) => x.word.charAt(0));
    if (!w) return null;
    // Kotlin substringBefore(' ') —— 拼音首音节，多音字也只看这一下
    const syllable = w.pinyin.split(' ')[0];
    const ch = w.word.charAt(0);
    const stem = [
      `给「${ch}」注音（　　）`,
      `写出「${ch}」的读音（　　）`,
      `「${ch}」的拼音是（　　）`,
    ][ctx.rng.nextInt(3)];
    return [stem, syllable];
  },
};

/** 补全词语：给出第一个字，写剩下的部分 */
export const dictationGenerator = {
  type: 'DICTATION',
  subject: Subject.CHINESE,
  minGrade: 1,
  maxGrade: 9,
  topic: '词语积累',
  make(ctx) {
    // 只留两个字的词以上：单字词挖掉首字就成空题干
    const pool = rotatedPool(CN_WORDS, ctx.grade).filter((w) => w.word.length >= 2);
    if (!pool.length) return null;
    // 同理：题干是「补全词语：太（　）」，首字相同的词会撞题
    const w = nextFreshWord(ctx, pool, (x) => x.word.charAt(0));
    if (!w) return null;
    const head = w.word.charAt(0);
    const stem = [
      `补全词语：${head}（　）`,
      `把词语补充完整：${head}＿（　）`,
      `填空：${head}（　）→ ${w.word.substring(0, 1)}？`,
    ][ctx.rng.nextInt(3)];
    return [stem, w.word.substring(1)];
  },
};

// ------------------------------------------------------------------ 英语

/** 单词默写：英译汉 / 汉译英 各两种问法 */
export const englishWordGenerator = {
  type: 'ENGLISH_WORD',
  subject: Subject.ENGLISH,
  minGrade: 3,
  maxGrade: 9,
  topic: '词汇',
  make(ctx) {
    const pool = rotatedPool(EN_WORDS, ctx.grade);
    if (!pool.length) return null;
    // 同一个词只出一次：英译汉 / 汉译英 是同一素材的两面，同时出现学生看着像重复
    const w = nextFreshWord(ctx, pool, (x) => x.en);
    if (!w) return null;
    switch (ctx.rng.nextInt(4)) {
      case 0: return [`英译汉：${w.en} →（　　）`, w.zh];
      case 1: return [`汉译英：${w.zh} →（　　）`, w.en];
      case 2: return [`写出 ${w.en} 的中文意思（　　）`, w.zh];
      default: return [`写出「${w.zh}」的英文单词（　　）`, w.en];
    }
  },
};

// ------------------------------------------------------------------ 物理

/** 闭区间整数随机，对应 Kotlin 的 r(rng, from, to) */
function r(rng, from, to) {
  return rng.nextInt(from, to);
}

/**
 * 物理计算：速度 / 密度 / 压强 / 欧姆定律 / 功 / 重力 六种模板。
 *
 * 数值一律「先定结果、再倒推出已知量」（如 v、t 先取，题干里写 v*t），
 * 保证答案永远是整数、批改不用考虑小数误差。
 */
export const physicsGenerator = {
  type: 'PHYSICS_CALC',
  subject: Subject.PHYSICS,
  minGrade: 8,
  maxGrade: 9,
  topic: '公式计算',
  make(ctx) {
    const rng = ctx.rng;
    const v = r(rng, 5, 120);
    const t = r(rng, 2, 8);
    const rho = r(rng, 2, 9);
    const vol = r(rng, 2, 12);
    const p = r(rng, 2, 12);
    const s = r(rng, 2, 8);
    const current = r(rng, 2, 9);
    const res = r(rng, 2, 6);
    const voltage = current * res;
    const force = r(rng, 5, 40);
    const dist = r(rng, 2, 12);
    const mass = r(rng, 2, 20);

    switch (r(rng, 0, 5)) {
      case 0: return [`一辆车 ${t} 小时行驶 ${v * t} 千米，平均速度是多少？`, `${v} 千米/时`];
      case 1: return [`体积 ${vol} cm³ 的物体质量 ${rho * vol} g，密度是多少？`, `${rho} g/cm³`];
      case 2: return [`压力 ${p * s} N 作用在 ${s} m² 的面积上，压强是多少？`, `${p} Pa`];
      case 3: return [`电压 ${voltage} V，电阻 ${res} Ω，通过的电流是多少？`, `${current} A`];
      case 4: return [`用 ${force} N 的力沿力的方向移动 ${dist} m，做了多少功？`, `${force * dist} J`];
      default: return [`质量 ${mass} kg 的物体受到的重力是多少？（g 取 10 N/kg）`, `${mass * 10} N`];
    }
  },
};

// ------------------------------------------------------------------ 化学

/**
 * 相对原子质量表。只收初中化学常见元素，
 * 与 Kotlin 的 atomicMass 完全一致（Cl 取 35.5，所以答案可能是 58.5 这种小数）。
 */
const ATOMIC_MASS = {
  H: 1.0, C: 12.0, O: 16.0, Na: 23.0,
  Cl: 35.5, Ca: 40.0, S: 32.0, Cu: 64.0,
  N: 14.0, Mg: 24.0,
};

/** 分子式 → 组成（元素, 原子个数） */
const FORMULAS = [
  ['H₂O', [['H', 2], ['O', 1]]],
  ['CO₂', [['C', 1], ['O', 2]]],
  ['O₂', [['O', 2]]],
  ['H₂', [['H', 2]]],
  ['CO', [['C', 1], ['O', 1]]],
  ['NaCl', [['Na', 1], ['Cl', 1]]],
  ['CaCO₃', [['Ca', 1], ['C', 1], ['O', 3]]],
  ['HCl', [['H', 1], ['Cl', 1]]],
  ['NaOH', [['Na', 1], ['O', 1], ['H', 1]]],
  ['H₂SO₄', [['H', 2], ['S', 1], ['O', 4]]],
  ['CH₄', [['C', 1], ['H', 4]]],
  ['NH₃', [['N', 1], ['H', 3]]],
  ['MgO', [['Mg', 1], ['O', 1]]],
];

/** 相对分子质量：44 印「44」，58.5 印「58.5」（JS 的 String 与 Kotlin 的 toString 对这类值一致） */
function mrText(mr) {
  return String(mr);
}

/**
 * 原子量提示里的数字格式。
 * Kotlin 里 atomicMass 是 Double，字符串模板会输出 "1.0"，不是 "1" ——
 * 这里照抄，保证网页版和 App 印出来的卷面完全一样。
 */
function massText(v) {
  return Number.isInteger(v) ? v.toFixed(1) : String(v);
}

export const chemistryGenerator = {
  type: 'CHEMISTRY_CALC',
  subject: Subject.CHEMISTRY,
  minGrade: 9,
  maxGrade: 9,
  topic: '相对分子质量',
  make(ctx) {
    const [formula, parts] = FORMULAS[ctx.rng.nextInt(FORMULAS.length)];
    const mr = parts.reduce((sum, [el, n]) => sum + (ATOMIC_MASS[el] ?? 0) * n, 0);
    const ans = mrText(mr);
    // 同一种元素只提示一次，H₂O 不会印成「H=1，H=1，O=16」
    const seen = new Set();
    const hint = parts
      .filter(([el]) => {
        if (seen.has(el)) return false;
        seen.add(el);
        return true;
      })
      .map(([el]) => `${el}=${massText(ATOMIC_MASS[el])}`)
      .join('，');
    const stem = ctx.rng.nextInt(2) === 0
      ? `计算 ${formula} 的相对分子质量（${hint}）＝（　　）`
      : `${formula} 的相对分子质量是多少？（${hint}）（　　）`;
    return [stem, ans];
  },
};

// ------------------------------------------------------------------ 汇总

export const generators = [
  pinyinGenerator,
  hanziGenerator,
  dictationGenerator,
  englishWordGenerator,
  physicsGenerator,
  chemistryGenerator,
];
