/**
 * v0.5.0 主题识字题型：看拼音写词语 / 找不同类 / 归类填空。
 *
 * 对应 Kotlin 的 ThemeGenerators.kt。题目全部由 ThemeBank（网页版在
 * DATA.content.themes，6 个生活场景约 110 个词）现场组装。
 *
 * 关键约定：答案必须在组装选项时**同步算出**。
 * 选项洗完再说「正确答案是 A」是最常见的错题来源 ——
 * 这里一律先拿「内容 + 是否正确」的数组，洗牌后按标记找下标，
 * 所以「选项换了、答案没换」这类错不可能出现。
 */

import { Subject } from '../core/models.js';
import { DATA } from '../data/store.js';

// ------------------------------------------------------------------ 数据视图

/**
 * 把 JSON 里的 {person:[[拼音,词]], item:[], place:[]} 摊平成 words 列表，
 * 对应 Kotlin 的 Theme.words（人物 + 物品 + 设施，顺序不变）。
 *
 * 结果按 DATA.content 引用缓存：数据只加载一次，没必要每次出题都重建。
 */
let cacheSource = null;
let cacheThemes = null;
let cacheUnique = null;

function bank() {
  const src = DATA.content;
  if (!src) return { themes: [], uniqueWords: [] };
  if (cacheSource === src && cacheThemes) return { themes: cacheThemes, uniqueWords: cacheUnique };

  const themes = (src.themes ?? []).map((t) => ({
    id: t.id,
    name: t.name,
    words: [
      ...(t.person ?? []).map(([pinyin, word]) => ({ pinyin, word })),
      ...(t.item ?? []).map(([pinyin, word]) => ({ pinyin, word })),
      ...(t.place ?? []).map(([pinyin, word]) => ({ pinyin, word })),
    ],
  }));

  // 只保留「归属唯一」的词：「水池」同时属于公园和动物园，
  // 拿它出「最可能出现在哪里」会有两个正确答案，这类词必须剔除。
  const byWord = new Map();
  for (const theme of themes) {
    for (const w of theme.words) {
      if (!byWord.has(w.word)) byWord.set(w.word, []);
      byWord.get(w.word).push({ word: w.word, theme });
    }
  }
  const uniqueWords = [...byWord.values()]
    .filter((group) => new Set(group.map((g) => g.theme.id)).size === 1)
    .map((group) => group[0]);

  cacheSource = src;
  cacheThemes = themes;
  cacheUnique = uniqueWords;
  return { themes, uniqueWords };
}

/** Fisher-Yates，走 ctx.rng，保证同 seed 出同一张卷 */
function shuffled(src, rng) {
  return rng.shuffle(src);
}

function letterOf(index) {
  return String.fromCharCode(65 + index);
}

// ------------------------------------------------------------------ 看拼音写词语

/** 只用来避免「相邻两题考同一个词」，不参与题干哈希，跨卷无副作用 */
let themePinyinLastWord = '';

export const themePinyinGenerator = {
  type: 'THEME_PINYIN',
  subject: Subject.CHINESE,
  minGrade: 1,
  maxGrade: 6,
  topic: '主题识字',
  make(ctx) {
    const { themes } = bank();
    if (!themes.length) return null;
    const group = themes[ctx.rng.nextInt(themes.length)];
    if (!group.words.length) return null;
    let w = group.words[ctx.rng.nextInt(group.words.length)];
    // 最多重抽 5 次，抽不到别的就算了 —— 绝不死循环
    for (let i = 0; i < 5 && w.word === themePinyinLastWord; i++) {
      w = group.words[ctx.rng.nextInt(group.words.length)];
    }
    themePinyinLastWord = w.word;
    const stem = [
      `【${group.name}】看拼音写词语：${w.pinyin} →（　　）`,
      `【${group.name}】${w.pinyin}，写出词语（　　）`,
      `读拼音写词语（${group.name}）：${w.pinyin}（　　）`,
    ][ctx.rng.nextInt(3)];
    return [stem, w.word];
  },
};

// ------------------------------------------------------------------ 找不同类

/**
 * 找不同类：同一主题取 2 个词 + 别的主题取 1 个词，问哪个不属于这里。
 *
 * 干扰词要同时排除「已选的两个同类词」和「本主题的全部词」：
 * 否则挑出本主题第三个词当异类，题目本身就错了。
 */
export const themeSortGenerator = {
  type: 'THEME_SORT',
  subject: Subject.CHINESE,
  minGrade: 1,
  maxGrade: 6,
  topic: '词语归类',
  make(ctx) {
    const { themes } = bank();
    if (themes.length < 2) return null;
    const rng = ctx.rng;
    const group = themes[rng.nextInt(themes.length)];
    const words = group.words.map((w) => w.word);
    const same = shuffled(words, rng).slice(0, 2);
    if (same.length < 2) return null;
    const groupWords = new Set(words);

    const others = [];
    for (const t of themes) {
      if (t.id === group.id) continue;
      for (const w of t.words) {
        if (same.includes(w.word) || groupWords.has(w.word) || others.includes(w.word)) continue;
        others.push(w.word);
      }
    }
    if (!others.length) return null;
    const odd = others[rng.nextInt(others.length)];

    // 选项洗牌，正确答案的字母按洗牌后的下标换算
    const items = shuffled(
      [[same[0], false], [same[1], false], [odd, true]],
      rng,
    );
    const options = items.map(([word], i) => `${letterOf(i)}. ${word}`);
    const answer = letterOf(items.findIndex(([, correct]) => correct));
    const stem = `【${group.name}】下面哪个词不属于这里？　${options.join('　')}（　　）`;
    return [stem, answer];
  },
};

// ------------------------------------------------------------------ 归类填空

/** 同上：同一页连出两次「长椅」会显得敷衍 */
let themeGroupLastWord = '';

export const themeGroupGenerator = {
  type: 'THEME_GROUP',
  subject: Subject.CHINESE,
  minGrade: 1,
  maxGrade: 6,
  topic: '场景归类',
  make(ctx) {
    const { themes, uniqueWords } = bank();
    if (!themes.length || !uniqueWords.length) return null;
    const rng = ctx.rng;

    let pick = uniqueWords[rng.nextInt(uniqueWords.length)];
    for (let i = 0; i < 5 && pick.word === themeGroupLastWord; i++) {
      pick = uniqueWords[rng.nextInt(uniqueWords.length)];
    }
    const { word, theme: correctTheme } = pick;
    themeGroupLastWord = word;

    const wrong = shuffled(
      themes.filter((t) => t.id !== correctTheme.id).map((t) => t.name),
      rng,
    ).slice(0, 2);
    if (!wrong.length) return null;

    const items = shuffled(
      [[correctTheme.name, true], ...wrong.map((name) => [name, false])],
      rng,
    );
    const options = items.map(([name], i) => `${letterOf(i)}. ${name}`);
    const answer = letterOf(items.findIndex(([, correct]) => correct));
    const stem = `「${word}」最可能出现在哪里？　${options.join('　')}（　　）`;
    return [stem, answer];
  },
};

// ------------------------------------------------------------------ 汇总

export const generators = [
  themePinyinGenerator,
  themeSortGenerator,
  themeGroupGenerator,
];
