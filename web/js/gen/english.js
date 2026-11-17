/**
 * 英语三题型 + 语文成语古诗四题型 —— 从 App 的
 *   domain/generate/EnglishWorksheetGenerators.kt
 *   domain/generate/IdiomPoemGenerators.kt
 * 逐条移植。
 *
 * 数据来源（值一字未改，只换了承载形式）：
 *  - 英语单词：domain/generate/WordBank.kt 的 private val english（EnWord 列表）
 *  - 连词成句句表：EnglishWorksheetGenerators.kt 里的 object SentenceBank
 *  - 选词填空题库：EnglishWorksheetGenerators.kt 里的 object ClozeBank
 *  - 成语：web 侧已导出的 DATA.content.idioms（对应 domain/content/Idioms.kt 的 Idioms.items）
 *  - 古诗：web 侧已导出的 DATA.content.poems（对应 domain/content/Poems.kt 的 Poems.items）
 *
 * 为什么成语/古诗不在这里再抄一份：这两份库在网页版已经由构建脚本导出成
 * data/content.json，抄第二份只会让「改了一处、另一处还是老数据」。
 * 英语词表/句表在网页版没有对应导出，所以按 Kotlin 原样内置在本文件末尾。
 *
 * 契约：每个生成器只负责「造一道题」，返回 [题干, 答案]；抽不到就 return null。
 * 循环、去重（stableHash）、丢弃空题干都由框架管，这里一概不碰。
 */

import { Subject } from '../core/models.js';
import { DATA } from '../data/store.js';

// ------------------------------------------------------------------ 小工具

/** 与 Kotlin 的 shuffled() 同一套 Fisher–Yates，保证同 seed 同结果 */
function shuffled(src, rng) {
  const a = src.slice();
  for (let i = a.length - 1; i >= 1; i--) {
    const j = rng.nextInt(i + 1);
    const t = a[i];
    a[i] = a[j];
    a[j] = t;
  }
  return a;
}

/** 选项字母：0→A、1→B…… 与 Kotlin 的 letterAt 一致 */
function letterAt(index) {
  return String.fromCharCode('A'.charCodeAt(0) + index);
}

/** 「A. xxx　B. xxx」——分隔符是表意空格，与 Kotlin 的 optionsText 一致 */
function optionsText(options) {
  return options.map((s, i) => `${letterAt(i)}. ${s}`).join('　');
}

/** 数组逐项相等：Kotlin 里可以直接写 words == body，JS 必须先比长度再比项 */
function sameOrder(a, b) {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
  return true;
}

// ------------------------------------------------------------------ 词表 / 句表

/**
 * 英语词表（照抄 WordBank.kt 的 private val english，顺序也不能变 ——
 * englishFor 的轮换 offset 依赖它）。
 */
const EN_WORDS = [
  { en: 'apple', zh: '苹果', minGrade: 3 },
  { en: 'book', zh: '书', minGrade: 3 },
  { en: 'cat', zh: '猫', minGrade: 3 },
  { en: 'dog', zh: '狗', minGrade: 3 },
  { en: 'red', zh: '红色', minGrade: 3 },
  { en: 'pen', zh: '钢笔', minGrade: 3 },
  { en: 'school', zh: '学校', minGrade: 4 },
  { en: 'teacher', zh: '老师', minGrade: 4 },
  { en: 'friend', zh: '朋友', minGrade: 4 },
  { en: 'water', zh: '水', minGrade: 4 },
  { en: 'family', zh: '家庭', minGrade: 5 },
  { en: 'weather', zh: '天气', minGrade: 5 },
  { en: 'holiday', zh: '假期', minGrade: 5 },
  { en: 'library', zh: '图书馆', minGrade: 5 },
  { en: 'because', zh: '因为', minGrade: 6 },
  { en: 'different', zh: '不同的', minGrade: 6 },
  { en: 'important', zh: '重要的', minGrade: 6 },
  { en: 'remember', zh: '记得', minGrade: 6 },
  { en: 'environment', zh: '环境', minGrade: 7 },
  { en: 'experience', zh: '经历', minGrade: 7 },
  { en: 'knowledge', zh: '知识', minGrade: 7 },
  { en: 'science', zh: '科学', minGrade: 7 },
  { en: 'attention', zh: '注意力', minGrade: 8 },
  { en: 'decision', zh: '决定', minGrade: 8 },
  { en: 'encourage', zh: '鼓励', minGrade: 8 },
  { en: 'progress', zh: '进步', minGrade: 8 },
  { en: 'opportunity', zh: '机会', minGrade: 9 },
  { en: 'responsible', zh: '负责的', minGrade: 9 },
  { en: 'technology', zh: '技术', minGrade: 9 },
  { en: 'challenge', zh: '挑战', minGrade: 9 },
  { en: 'window', zh: '窗户', minGrade: 3 },
  { en: 'orange', zh: '橙子', minGrade: 3 },
  { en: 'student', zh: '学生', minGrade: 4 },
  { en: 'morning', zh: '早晨', minGrade: 4 },
  { en: 'village', zh: '村庄', minGrade: 5 },
  { en: 'mountain', zh: '山', minGrade: 5 },
  { en: 'exercise', zh: '练习', minGrade: 6 },
  { en: 'question', zh: '问题', minGrade: 6 },
  { en: 'culture', zh: '文化', minGrade: 7 },
  { en: 'medicine', zh: '药', minGrade: 8 },
  { en: 'industry', zh: '工业', minGrade: 9 },
];

/**
 * 与 WordBank.englishFor 完全一致：先按 minGrade 过滤，再把「本年级新词」
 * 开始的那一段转到开头。轮换不是装饰 —— 每个年级都用同一批词的开头，
 * 高年级的卷子会反复出现 apple / book 这几个最简单的词。
 */
function englishFor(grade) {
  const pool = EN_WORDS.filter((w) => w.minGrade <= grade);
  if (!pool.length) return [];
  let firstNew = -1;
  for (let i = 0; i < EN_WORDS.length; i++) {
    if (EN_WORDS[i].minGrade === grade) { firstNew = i; break; }
  }
  const offset = firstNew > 0 ? firstNew % pool.length : 0;
  return pool.slice(offset).concat(pool.slice(0, offset));
}

/** 照抄 SentenceBank.all（英文原句与中文释义都不能改） */
const SENTENCES = [
  { text: 'This is a cat.', zh: '这是一只猫。', minGrade: 3 },
  { text: 'I like apples.', zh: '我喜欢苹果。', minGrade: 3 },
  { text: 'The dog is big.', zh: '这只狗很大。', minGrade: 3 },
  { text: 'She has a pen.', zh: '她有一支钢笔。', minGrade: 3 },
  { text: 'We go to school.', zh: '我们去上学。', minGrade: 3 },
  { text: 'It is a red book.', zh: '这是一本红色的书。', minGrade: 3 },
  { text: 'My name is Tom.', zh: '我叫汤姆。', minGrade: 3 },
  // 三年级再补一组短句：连词成句靠模板洗牌，句子太少补题就补不动
  { text: 'I have a dog.', zh: '我有一只狗。', minGrade: 3 },
  { text: 'This is my bag.', zh: '这是我的书包。', minGrade: 3 },
  { text: 'The sun is bright.', zh: '太阳很明亮。', minGrade: 3 },
  { text: 'He is my friend.', zh: '他是我的朋友。', minGrade: 3 },
  { text: 'We like to read.', zh: '我们喜欢读书。', minGrade: 3 },
  { text: 'The bird can fly.', zh: '鸟会飞。', minGrade: 3 },
  { text: 'My bag is blue.', zh: '我的书包是蓝色的。', minGrade: 3 },
  { text: 'I see a big tree.', zh: '我看见一棵大树。', minGrade: 3 },
  { text: 'She has a red hat.', zh: '她有一顶红帽子。', minGrade: 3 },
  { text: 'The book is on the desk.', zh: '书在桌子上。', minGrade: 3 },
  { text: 'The cat is on the desk.', zh: '猫在桌子上。', minGrade: 4 },
  { text: 'My friend is at home.', zh: '我的朋友在家。', minGrade: 4 },
  { text: 'We play football after school.', zh: '放学后我们踢足球。', minGrade: 4 },
  { text: 'The weather is fine today.', zh: '今天天气很好。', minGrade: 4 },
  { text: 'There are two books on the table.', zh: '桌上有两本书。', minGrade: 5 },
  { text: 'I went to the library yesterday.', zh: '我昨天去了图书馆。', minGrade: 5 },
  { text: 'She is reading an interesting story.', zh: '她正在读一个有趣的故事。', minGrade: 5 },
  { text: 'My family will visit the village.', zh: '我们一家要去参观那个村庄。', minGrade: 6 },
  { text: 'Because it rained, we stayed home.', zh: '因为下雨，我们待在家里。', minGrade: 6 },
  { text: 'Please remember to bring your homework.', zh: '请记得带上作业。', minGrade: 6 },
  { text: 'I have finished my homework already.', zh: '我已经完成作业了。', minGrade: 7 },
  { text: 'The science lesson was very interesting.', zh: '那节科学课非常有意思。', minGrade: 7 },
  { text: 'Knowledge comes from practice.', zh: '知识来自实践。', minGrade: 7 },
  { text: 'If you work hard, you will make progress.', zh: '努力就会进步。', minGrade: 8 },
  { text: 'The teacher encouraged us to ask questions.', zh: '老师鼓励我们提问。', minGrade: 8 },
  { text: 'We should pay attention to the environment.', zh: '我们应该关注环境。', minGrade: 8 },
  { text: 'Technology has changed the way we learn.', zh: '科技改变了我们的学习方式。', minGrade: 9 },
  { text: 'It is important to make a careful decision.', zh: '谨慎做决定很重要。', minGrade: 9 },
];

/** 照抄 ClozeBank.all：选项本身自带「A. 」前缀，是题库的原始写法 */
const CLOZE_ITEMS = [
  { stem: '___ is my book.', options: ['A. This', 'B. These', 'C. Those'], answer: 'A', minGrade: 3 },
  { stem: 'I ___ a student.', options: ['A. am', 'B. is', 'C. are'], answer: 'A', minGrade: 3 },
  { stem: 'She ___ a new pen.', options: ['A. have', 'B. has', 'C. having'], answer: 'B', minGrade: 3 },
  { stem: 'The cat is ___ the box.', options: ['A. in', 'B. of', 'C. at'], answer: 'A', minGrade: 3 },
  // 低年级容量扩充：原题库只有 4 条，自动补题一补就撞去重上限
  { stem: '___ am a pupil.', options: ['A. I', 'B. Me', 'C. My'], answer: 'A', minGrade: 3 },
  { stem: 'This is ___ apple.', options: ['A. a', 'B. an', 'C. the'], answer: 'B', minGrade: 3 },
  { stem: 'I have two ___.', options: ['A. book', 'B. books', 'C. bookes'], answer: 'B', minGrade: 3 },
  { stem: '___ is your name?', options: ['A. What', 'B. Who', 'C. Where'], answer: 'A', minGrade: 3 },
  { stem: 'The dog ___ small.', options: ['A. am', 'B. is', 'C. are'], answer: 'B', minGrade: 3 },
  { stem: 'We ___ happy today.', options: ['A. is', 'B. am', 'C. are'], answer: 'C', minGrade: 3 },
  { stem: 'Look ___ the picture.', options: ['A. at', 'B. in', 'C. on'], answer: 'A', minGrade: 3 },
  { stem: 'I go to school ___ bike.', options: ['A. by', 'B. on', 'C. in'], answer: 'A', minGrade: 3 },
  { stem: '___ you have a ruler?', options: ['A. Do', 'B. Does', 'C. Are'], answer: 'A', minGrade: 3 },
  { stem: 'There ___ a book on the desk.', options: ['A. is', 'B. are', 'C. be'], answer: 'A', minGrade: 3 },
  { stem: '___ you like oranges?', options: ['A. Do', 'B. Does', 'C. Are'], answer: 'A', minGrade: 4 },
  { stem: 'There ___ two birds in the tree.', options: ['A. is', 'B. are', 'C. be'], answer: 'B', minGrade: 4 },
  { stem: 'He ___ to school every day.', options: ['A. go', 'B. goes', 'C. going'], answer: 'B', minGrade: 4 },
  { stem: 'This book is ___ than that one.', options: ['A. good', 'B. better', 'C. best'], answer: 'B', minGrade: 4 },
  { stem: 'We ___ football last Sunday.', options: ['A. play', 'B. plays', 'C. played'], answer: 'C', minGrade: 5 },
  { stem: 'She is ___ than her brother.', options: ['A. tall', 'B. taller', 'C. tallest'], answer: 'B', minGrade: 5 },
  { stem: 'I have ___ finished my homework.', options: ['A. yet', 'B. already', 'C. still'], answer: 'B', minGrade: 6 },
  { stem: '___ it rained, we stayed at home.', options: ['A. Because', 'B. Although', 'C. Unless'], answer: 'A', minGrade: 6 },
  { stem: 'He asked me ___ I liked the story.', options: ['A. that', 'B. if', 'C. what'], answer: 'B', minGrade: 6 },
  { stem: 'The room ___ by the students yesterday.', options: ['A. cleans', 'B. cleaned', 'C. was cleaned'], answer: 'C', minGrade: 7 },
  { stem: 'This is the book ___ I borrowed.', options: ['A. who', 'B. which', 'C. whom'], answer: 'B', minGrade: 7 },
  { stem: 'If it ___ tomorrow, we will stay home.', options: ['A. rain', 'B. rains', 'C. rained'], answer: 'B', minGrade: 8 },
  { stem: 'We should pay attention ___ the details.', options: ['A. to', 'B. on', 'C. for'], answer: 'A', minGrade: 8 },
  { stem: 'She speaks English ___ than I do.', options: ['A. fluent', 'B. fluently', 'C. more fluently'], answer: 'C', minGrade: 8 },
  { stem: 'Hard work leads ___ progress.', options: ['A. to', 'B. in', 'C. at'], answer: 'A', minGrade: 9 },
  { stem: 'Not only the students ___ the teacher was surprised.', options: ['A. and', 'B. but also', 'C. or'], answer: 'B', minGrade: 9 },
];

/** 成语池：与 Idioms.pool 一致，只按「从几年级起」过滤，不取高年级的 */
function idiomPool(grade) {
  const items = DATA.content?.idioms ?? [];
  return items.filter((it) => it.grade <= grade);
}

/** 古诗池：与 Poems.pool 一致 */
function poemPool(grade) {
  const items = DATA.content?.poems ?? [];
  return items.filter((p) => p.grade <= grade);
}

// ------------------------------------------------------------------ 英语：单词拼装

const wordScramble = {
  type: 'WORD_SCRAMBLE',
  subject: Subject.ENGLISH,
  minGrade: 3,
  maxGrade: 9,
  topic: '单词拼装',
  make(ctx) {
    // 只挑 4～10 个字母的纯字母词：3 个字母的洗牌后还是原词的概率太高，
    // 更长的词一页排两列就写不下
    const pool = englishFor(ctx.grade)
      .filter((w) => w.en.length >= 4 && w.en.length <= 10 && /^[A-Za-z]+$/.test(w.en));
    if (!pool.length) return null;

    const w = pool[ctx.rng.nextInt(pool.length)];
    const letters = shuffled(w.en.split(''), ctx.rng);
    // 洗出来正好是原词时强行换头两位：题目必须真的「被打乱」才有练习价值
    if (letters.join('') === w.en && letters.length > 1) {
      const t = letters[0];
      letters[0] = letters[1];
      letters[1] = t;
    }
    const display = letters.join(' ');
    // 指令只在大题说明里印一次，题干只留字母；
    // 「给出中文意思」的变式把中文留着 —— 那是已知条件，不是重复
    return ctx.rng.nextInt(3) === 2
      ? [`「${w.zh}」${display} →`, w.en]
      : [`${display} →`, w.en];
  },
};

// ------------------------------------------------------------------ 英语：连词成句

const sentenceOrder = {
  type: 'SENTENCE_ORDER',
  subject: Subject.ENGLISH,
  minGrade: 3,
  maxGrade: 9,
  topic: '连词成句',
  make(ctx) {
    const pool = SENTENCES.filter((s) => s.minGrade <= ctx.grade);
    if (!pool.length) return null;

    const s = pool[ctx.rng.nextInt(pool.length)];
    // removeSuffix(".") 再去掉句号、按空格切词；末尾的「 .」统一另补
    const body = s.text.endsWith('.') ? s.text.slice(0, -1) : s.text;
    const words = body.split(' ');
    const mixed = shuffled(words, ctx.rng);
    if (sameOrder(mixed, words) && mixed.length > 1) {
      const t = mixed[0];
      mixed[0] = mixed[1];
      mixed[1] = t;
    }
    const display = `${mixed.join(' / ')} .`;
    const stem = ctx.rng.nextInt(2) === 0
      ? `连词成句：${display}（　　）`
      : `连词成句（${s.zh}）：${display}（　　）`;
    return [stem, s.text];
  },
};

// ------------------------------------------------------------------ 英语：选词填空

const cloze = {
  type: 'CLOZE',
  subject: Subject.ENGLISH,
  minGrade: 3,
  maxGrade: 9,
  topic: '语法选择',
  make(ctx) {
    const pool = CLOZE_ITEMS.filter((it) => it.minGrade <= ctx.grade);
    if (!pool.length) return null;

    const item = pool[ctx.rng.nextInt(pool.length)];
    // 选项一个没打乱：题库里 A/B/C 的分布是刻意安排过的，答案直接取原字母
    const options = item.options.join('　');
    const stem = ctx.rng.nextInt(2) === 0
      ? `选择正确答案：${item.stem}　${options}（　　）`
      : `单项选择：${item.stem}　${options}（　　）`;
    return [stem, item.answer];
  },
};

// ------------------------------------------------------------------ 语文：成语填空

const idiomFill = {
  type: 'IDIOM_FILL',
  subject: Subject.CHINESE,
  minGrade: 3,
  maxGrade: 6,
  topic: '成语积累',
  make(ctx) {
    const pool = idiomPool(ctx.grade);
    // Kotlin 用 check 直接抛异常，网页版按契约返回 null 让框架换题
    if (pool.length < 8) return null;

    const idiom = pool[ctx.rng.nextInt(pool.length)];
    const chars = idiom.word.split('');
    // 不挖第一个字：四字成语的首字往往是辨识点，挖掉以后基本只能靠蒙
    const idx = 1 + ctx.rng.nextInt(chars.length - 1);
    const missing = chars[idx];
    chars[idx] = '＿'; // 全角下划线，与 Kotlin 的 '＿' 同一个字符
    const stem = `把成语补充完整：${chars.join('')}（　）`;
    return [stem, `${missing}（${idiom.word}：${idiom.meaning}）`];
  },
};

// ------------------------------------------------------------------ 语文：成语释义

const idiomMeaning = {
  type: 'IDIOM_MEANING',
  subject: Subject.CHINESE,
  minGrade: 3,
  maxGrade: 6,
  topic: '成语理解',
  make(ctx) {
    const pool = idiomPool(ctx.grade);
    if (pool.length < 16) return null;

    const idiom = pool[ctx.rng.nextInt(pool.length)];

    // 干扰项优先取「不同主题」的释义：同一主题里常有一对近义词，
    // 放一个题里会出现两个都对的情况，题就废了。
    // 先按「是否同主题」稳定降序排（异主题在前），再只取前半段打散 ——
    // 后半段同主题的条目只有在前面凑不够 3 条时才会被用到
    const others = pool
      .filter((it) => it.word !== idiom.word && it.meaning !== idiom.meaning)
      .slice()
      .sort((a, b) => (b.tag !== idiom.tag ? 1 : 0) - (a.tag !== idiom.tag ? 1 : 0));
    const head = shuffled(others.slice(0, Math.floor(others.length / 2)), ctx.rng);
    const meanings = [];
    for (const it of head) {
      if (!meanings.includes(it.meaning)) meanings.push(it.meaning);
      if (meanings.length === 3) break;
    }
    if (meanings.length < 3) return null;

    const options = shuffled(meanings.concat([idiom.meaning]), ctx.rng);
    const answer = letterAt(options.indexOf(idiom.meaning));
    return [`「${idiom.word}」的意思是（　）\n${optionsText(options)}`, answer];
  },
};

// ------------------------------------------------------------------ 语文：古诗接句

const poemFill = {
  type: 'POEM_FILL',
  subject: Subject.CHINESE,
  minGrade: 1,
  maxGrade: 6,
  topic: '古诗积累',
  make(ctx) {
    // 只要至少两句的诗；单句节选没法出接句题
    const pool = poemPool(ctx.grade).filter((p) => p.lines.length >= 2);
    if (!pool.length) return null;

    const poem = pool[ctx.rng.nextInt(pool.length)];
    const i = ctx.rng.nextInt(poem.lines.length - 1);
    const prev = poem.lines[i];
    const next = poem.lines[i + 1];
    // 诗句自带标点，题干不用再补；答案就是下一整句
    return [`《${poem.title}》：${prev} 下一句是（　）`, next];
  },
};

// ------------------------------------------------------------------ 语文：古诗常识

/** 朝代选项池：只放古诗库里真出现过的，免得出现「夏朝」这种离谱干扰项 */
const DYNASTIES = ['汉', '三国', '南北朝', '唐', '宋', '元', '明', '清'];

const poemMatch = {
  type: 'POEM_MATCH',
  subject: Subject.CHINESE,
  minGrade: 2,
  maxGrade: 6,
  topic: '古诗常识',
  make(ctx) {
    const pool = poemPool(ctx.grade);
    if (!pool.length) return null;

    const poem = pool[ctx.rng.nextInt(pool.length)];
    // 从本诗里随便挑一句当线索，长短句都行
    const line = poem.lines[ctx.rng.nextInt(poem.lines.length)];

    const kind = ctx.rng.nextInt(3);

    if (kind === 0) {
      // 诗句 → 作者：干扰项从全库作者里取，保证四个选项互不相同
      const authors = (DATA.content?.poems ?? []).map((p) => p.author);
      const allAuthors = [];
      for (const a of authors) if (!allAuthors.includes(a)) allAuthors.push(a);
      const wrong = shuffled(allAuthors.filter((a) => a !== poem.author), ctx.rng).slice(0, 3);
      if (wrong.length < 3) return null;
      const options = shuffled(wrong.concat([poem.author]), ctx.rng);
      return [
        `「${line}」出自哪位诗人？（　）\n${optionsText(options)}`,
        `(${letterAt(options.indexOf(poem.author))}) ${poem.author}`,
      ];
    }

    if (kind === 1) {
      // 诗句 → 篇名：干扰项只取本年级可达的诗，避免出现没学过的篇名
      const titles = [];
      for (const p of pool) if (p.title !== poem.title && !titles.includes(p.title)) titles.push(p.title);
      const wrong = shuffled(titles, ctx.rng).slice(0, 3);
      if (wrong.length < 3) return null;
      const options = shuffled(wrong.concat([poem.title]), ctx.rng);
      return [
        `「${line}」出自哪一首？（　）\n${optionsText(options)}`,
        `(${letterAt(options.indexOf(poem.title))}) 《${poem.title}》`,
      ];
    }

    // 诗句 → 朝代：选项池是固定的八个朝代，取三个不等于本诗朝代的
    const wrong = shuffled(DYNASTIES.filter((d) => d !== poem.dynasty), ctx.rng).slice(0, 3);
    if (wrong.length < 3) return null;
    const options = shuffled(wrong.concat([poem.dynasty]), ctx.rng);
    return [
      `「${line}」（${poem.author}）写于哪个朝代？（　）\n${optionsText(options)}`,
      `(${letterAt(options.indexOf(poem.dynasty))}) ${poem.dynasty}`,
    ];
  },
};

// ------------------------------------------------------------------ 导出

export const generators = [
  wordScramble,
  sentenceOrder,
  cloze,
  idiomFill,
  idiomMeaning,
  poemFill,
  poemMatch,
];
