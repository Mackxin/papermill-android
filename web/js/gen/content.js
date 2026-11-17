/**
 * 内置内容生成器：音标 / 语法 / 课本同步。
 *
 * 对应 Kotlin 的 ContentGenerators.kt（含 FinitePoolGenerator 基类）。
 *
 * ============================================================
 * 为什么要一套「有限题源」逻辑，不能直接照搬随机造题
 * ============================================================
 * 这些题源是**有限集合**（20 个元音 / 48 个音标 / 180 个课本生字），
 * 如果按「随机抽 + 题干去重」来出题会踩两个坑：
 *
 *  1. 同题重复：语法题「They ___ in the classroom.」随机抽两次时
 *     选项打乱顺序不同 → 题干不同 → 去重失效，同一张卷出现两道一样的题。
 *     这里靠「整池洗牌、逐条消费」保证一张卷里同一条素材只用一次。
 *  2. 答案歧义：音标辨认只问**元音**，且干扰项只取同类元音。
 *     若问「bag 含有哪个音标」，/b/ 和 /ɡ/ 都对，题目就废了。
 *
 * ============================================================
 * 关于 ctx.allowReuse 的放宽
 * ============================================================
 * Kotlin 的 FinitePoolGenerator 是「洗完只走一遍，取完就停」，
 * 不做第二轮 —— 第二轮会把同一道题再印一遍，比少给几道更糟。
 *
 * 但网页版框架会跨卷传 blocked（最近几张卷的历史题干），
 * 题库小的时候整池可能被历史题挡光，整个大题一块消失。
 * 所以这里保留一条克制的放宽：**只有当一道题都出不来**（out 为空）
 * 且 ctx.allowReuse 为 true 时，才忽略 blocked 再走一遍，
 * 并且仍然用局部 Set 保证这张卷内部不重复。
 * 只要出得来哪怕一道，就不放宽 —— 宁可少题，也不重复。
 */

import { Subject, stableHash } from '../core/models.js';
import { DATA } from '../data/store.js';

// ------------------------------------------------------------------ 公共小工具

const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F'];

/** Fisher-Yates，用 ctx.rng 洗牌，保证同 seed 可复现 */
function shuffled(src, rng) {
  return rng.shuffle(src);
}

function letterOf(index) {
  return LETTERS[index] ?? String.fromCharCode(65 + index);
}

/**
 * 把「正确内容 + 干扰内容」洗成 A/B/C/D。
 *
 * 先 distinct 再洗牌：干扰项里若混进正确答案，会多出一个同样正确的选项。
 * 答案字母是「洗牌后正确答案的下标」，所以选项顺序怎么变都不会错位。
 */
function buildOptions(correct, distractors, rng) {
  const contents = [...new Set([correct, ...distractors])];
  const order = shuffled(contents.map((_, i) => i), rng);
  const opts = order.map((idx, i) => `${letterOf(i)}. ${contents[idx]}`);
  const answer = order.indexOf(0) >= 0 ? letterOf(order.indexOf(0)) : 'A';
  return [opts.join('　'), answer];
}

// ------------------------------------------------------------------ 有限题源

/**
 * FinitePoolGenerator 的等价实现。
 *
 * 入参都是「函数」而非数据：poolFor / render 在每次 generate 时才取，
 * 这样 DATA 里的 JSON 什么时候加载完都不影响（生成器模块可以提前 import）。
 */
function finitePool({ type, subject, minGrade = 1, maxGrade = 9, topic, poolFor, render }) {
  return {
    type,
    subject,
    minGrade,
    maxGrade,
    topic,
    generate(ctx) {
      const pool = poolFor(ctx.grade);
      if (!pool || pool.length === 0) return [];

      const out = [];
      // 第一轮：整池洗牌后逐条消费，取够 count 就停，绝不开第二轮
      for (const item of shuffled(pool, ctx.rng)) {
        if (out.length >= ctx.count) break;
        const pair = safeRender(render, item, ctx);
        if (!pair) continue;
        const stem = String(pair[0] ?? '').trim();
        const answer = String(pair[1] ?? '').trim();
        if (!stem || !answer) continue;
        const hash = stableHash(stem);
        if (ctx.blocked.has(hash)) continue;
        ctx.blocked.add(hash);
        out.push({ stem, answer, topic });
      }

      // 整块被历史题挡光时，才允许放宽（见文件头说明）
      if (out.length === 0 && ctx.allowReuse) {
        const seen = new Set();
        for (const item of shuffled(pool, ctx.rng)) {
          if (out.length >= ctx.count) break;
          const pair = safeRender(render, item, ctx);
          if (!pair) continue;
          const stem = String(pair[0] ?? '').trim();
          const answer = String(pair[1] ?? '').trim();
          if (!stem || !answer) continue;
          const hash = stableHash(stem);
          if (seen.has(hash)) continue;
          seen.add(hash);
          ctx.blocked.add(hash);
          out.push({ stem, answer, topic });
        }
      }

      return out;
    },
  };
}

/** 单个素材渲染失败（数据缺字段之类）只丢这一条，不让整张卷崩掉 */
function safeRender(render, item, ctx) {
  try {
    return render(item, ctx);
  } catch (_e) {
    return null;
  }
}

// ------------------------------------------------------------------ 数据取用

const content = () => DATA.content;
const phonicsAll = () => content()?.phonics?.all ?? [];

/**
 * 元音 = 音标总表里 kind 属于「元音类」的那些。
 * 元音类的判定不写死成三个常量，而是读 kindOrder 的 vowel 标记，
 * 这样以后内容表加了新的元音分类（比如三合元音）也不用改代码。
 */
function phonicsVowels() {
  const kinds = new Set(
    (content()?.phonics?.kindOrder ?? []).filter((k) => k.vowel).map((k) => k.key),
  );
  return phonicsAll().filter((p) => kinds.has(p.kind));
}

const grammarItems = () => content()?.grammar?.items ?? [];
const hanziAll = () => content()?.textbook?.hanzi ?? [];
const wordsAll = () => content()?.textbook?.words ?? [];

/** 课本字表按「当前年级及以下」累积；过滤空了就退回全表（低年级数据缺失时兜底） */
function accumulate(list, grade) {
  const pool = list.filter((it) => it.grade <= grade);
  return pool.length ? pool : list;
}

// ------------------------------------------------------------------ 音标

/**
 * 音标辨认：问「这个单词里的元音音标是哪个」。
 *
 * 干扰项只取同类元音（短配短、长配长、双元音配双元音），
 * 每条素材的正确答案唯一，可以放心自动判分。
 */
export const phoneticPickGenerator = finitePool({
  type: 'PHONETIC_PICK',
  subject: Subject.ENGLISH,
  minGrade: 3,
  maxGrade: 9,
  topic: '英语·音标',
  poolFor: () => phonicsVowels(),
  render(item, ctx) {
    const word = item.examples[ctx.rng.nextInt(item.examples.length)];
    const sameKind = phonicsVowels().filter((p) => p.kind === item.kind && p.ipa !== item.ipa);
    const distractors = shuffled(sameKind, ctx.rng).slice(0, 3).map((p) => p.ipa);
    const [opts, answer] = buildOptions(item.ipa, distractors, ctx.rng);
    return [`单词 ${word} 中的元音音标是（　）　${opts}`, answer];
  },
});

/**
 * 看音标写单词：给音标 + 中文提示，写出对应单词。
 *
 * 中文提示是必需的 —— 只给 /iː/ 的话 see / tea / green 都算对，
 * 自动判分就没法做了。答案取每条音素的第一个例词。
 */
export const phoneticWriteGenerator = finitePool({
  type: 'PHONETIC_WRITE',
  subject: Subject.ENGLISH,
  minGrade: 3,
  maxGrade: 9,
  topic: '英语·音标',
  poolFor: () => phonicsAll(),
  render(item) {
    return [`根据音标写出单词：${item.ipa}　（${item.zh}）`, item.examples[0]];
  },
});

// ------------------------------------------------------------------ 语法

/**
 * 语法选择：按年级取题（gradeFrom <= 当前年级），选项打乱成 4 选 1。
 *
 * 答案里带一句「为什么」 —— 语法题在答案页光给答案没有意义。
 */
export const grammarGenerator = finitePool({
  type: 'GRAMMAR',
  subject: Subject.ENGLISH,
  minGrade: 3,
  maxGrade: 9,
  topic: '英语·语法',
  poolFor: (grade) => grammarItems().filter((it) => it.gradeFrom <= grade),
  render(item, ctx) {
    const distractors = item.options.filter((o) => o !== item.answer);
    const [opts, letter] = buildOptions(item.answer, distractors, ctx.rng);
    return [
      `【${item.point}】${item.stem}　${opts}`,
      `${letter}. ${item.answer}（${item.explain}）`,
    ];
  },
});

// ------------------------------------------------------------------ 课本同步

/** 课本生字注音：给汉字写拼音 */
export const textbookHanziGenerator = finitePool({
  type: 'TEXTBOOK_HANZI',
  subject: Subject.CHINESE,
  minGrade: 1,
  maxGrade: 9,
  topic: '语文·课本生字',
  poolFor: (grade) => accumulate(hanziAll(), grade),
  // 大题标题已经写了「课本生字注音」，题干再重复一遍整页都是同一句话
  render(item) {
    return [`${item.hanzi}　（　　　）`, item.pinyin];
  },
});

/** 课本单词默写：给中文写英语单词 */
export const textbookWordGenerator = finitePool({
  type: 'TEXTBOOK_WORD',
  subject: Subject.ENGLISH,
  minGrade: 3,
  maxGrade: 9,
  topic: '英语·课本单词',
  poolFor: (grade) => accumulate(wordsAll(), grade),
  // 同上：指令放在大题标题里，题干只留中文，卷面才像真的默写纸
  render(item) {
    return [`${item.zh}　＿＿＿＿＿＿`, item.en];
  },
});

// ------------------------------------------------------------------ 汇总

export const generators = [
  phoneticPickGenerator,
  phoneticWriteGenerator,
  grammarGenerator,
  textbookHanziGenerator,
  textbookWordGenerator,
];
