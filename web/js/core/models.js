/**
 * 出卷引擎的基础模型 —— 与 App 的 domain/generate/Models.kt 一一对应。
 *
 * 这里只放「纯数据 + 纯函数」：科目、题型表、年级画像、随机数、去重哈希。
 * 排版层不认识这些，出题层不认识 DOM，两边各管一段。
 */

// ------------------------------------------------------------------ 科目

export const Subject = {
  MATH: 'MATH',
  CHINESE: 'CHINESE',
  ENGLISH: 'ENGLISH',
  SCIENCE: 'SCIENCE',
  PHYSICS: 'PHYSICS',
  CHEMISTRY: 'CHEMISTRY',
};

export const SUBJECT_LABEL = {
  MATH: '数学',
  CHINESE: '语文',
  ENGLISH: '英语',
  SCIENCE: '科学',
  PHYSICS: '物理',
  CHEMISTRY: '化学',
};

export const SUBJECT_ALL = ['MATH', 'CHINESE', 'ENGLISH', 'SCIENCE', 'PHYSICS', 'CHEMISTRY'];

/** 哪些年级开这门课（UI 用它把不可选的科目置灰） */
export function subjectAvailableFor(subject, grade) {
  switch (subject) {
    case Subject.MATH:
    case Subject.CHINESE:
      return grade >= 1 && grade <= 9;
    case Subject.ENGLISH:
      return grade >= 3 && grade <= 9;
    case Subject.SCIENCE:
      return grade >= 1 && grade <= 6;
    case Subject.PHYSICS:
      return grade >= 8 && grade <= 9;
    case Subject.CHEMISTRY:
      return grade === 9;
    default:
      return false;
  }
}

// ------------------------------------------------------------------ 题型表

const T = (key, label, columns = 2, answerLines = 0, instruction = '', padWithLines = true) => ({
  key, label, columns, answerLines, instruction, padWithLines,
});

/**
 * 题型定义表。order 就是 App 里枚举的声明顺序 —— 排序、序号、默认配比都靠它，
 * 所以不能用对象 key 顺序去猜（JS 对象会被数字键重排）。
 */
export const QUESTION_TYPES = [
  T('ORAL', '口算', 2),
  T('ORAL_MISSING', '求未知项', 3),
  T('ORAL_REMAINDER', '有余数除法', 2),
  T('VERTICAL', '竖式计算', 3, 3, '用竖式计算下面各题，把得数写在括号里。'),
  T('EXPRESSION', '脱式计算', 1, 2, '脱式计算，写出每一步的过程。'),
  T('FRACTION', '分数运算', 2),
  T('EQUATION', '解方程', 2, 1),
  T('WORD_PROBLEM', '应用题', 2, 3, '', false),

  T('RATIONAL', '有理数运算', 2),
  T('ALGEBRA', '整式与方程', 2),
  T('RADICAL', '二次根式', 2),

  T('PINYIN', '看拼音写词语', 2),
  T('HANZI', '生字注音', 4),
  T('DICTATION', '补全词语', 4),

  T('ENGLISH_WORD', '单词默写', 2),

  T('PHYSICS_CALC', '物理计算', 1, 3),
  T('CHEMISTRY_CALC', '化学计算', 2),

  T('KNOWLEDGE', '自编题库', 1, 1),

  T('FILL_BLANK', '填括号', 3),
  T('MAKE_TEN', '凑十练习', 3),
  T('CHAIN_CALC', '连加连减', 2),
  T('MIXED_CALC', '加减混合', 2),
  T('TENS_CALC', '整十数运算', 3),
  T('TWO_DIGIT', '两位数加减', 2),
  T('UNIT_CONVERT', '单位换算', 2),
  T('COMPARE', '比大小', 3),
  T('NUMBER_PATTERN', '找规律填数', 2),
  T('COUNTING', '数数与认数', 2),
  T('LETTER_ORDER', '字母顺序', 3),

  T('WORD_SCRAMBLE', '单词拼装', 2, 1, '把打乱的字母重新排好，写出正确的单词，拼写要完整。'),
  T('SENTENCE_ORDER', '连词成句', 1),
  T('CLOZE', '选词填空', 1),

  T('THEME_PINYIN', '主题·看拼音写词语', 2),
  T('THEME_SORT', '主题·找不同类', 1),
  T('THEME_GROUP', '主题·归类填空', 1),

  T('PHONETIC_PICK', '音标辨认', 1),
  T('PHONETIC_WRITE', '看音标写单词', 2),

  T('GRAMMAR', '语法选择', 1),

  T('TEXTBOOK_HANZI', '课本生字注音', 4),
  T('TEXTBOOK_WORD', '课本单词默写', 4),

  T('IDIOM_FILL', '成语填空', 3),
  T('IDIOM_MEANING', '成语释义', 1),
  T('POEM_FILL', '古诗接句', 1),
  T('POEM_MATCH', '古诗常识', 1),

  T('CURRIC_BLANK', '知识点填空', 1),
  T('CURRIC_CHOICE', '知识点选择', 1),

  T('SCIENCE_CHOICE', '科学选择', 1),
  T('SCIENCE_JUDGE', '科学判断', 2),
  T('SCIENCE_BLANK', '科学填空', 1),

  T('FIGURE_COUNT', '看图数一数', 1, 0, '先数一数每种图形各有几个，把数填在括号里。'),
  T('FIGURE_EXPR', '看图列式', 1, 0, '看图数一数一共有几个，把加法算式写在括号里。'),
  T('CLOCK_READ', '认识钟面', 1, 0, '看钟面，写出钟面上的时刻。'),
  T('BAR_CHART', '条形统计图', 1, 0, '看统计图回答问题，把答案填在括号里。'),

  // 自编题：内容由用户给，不进题型库（没有随机出题这回事），只复用排版规则
  T('CUSTOM', '自编题', 2, 1),
].map((t, i) => ({ ...t, order: i }));

export const TYPE = {};
QUESTION_TYPES.forEach((t) => { TYPE[t.key] = t; });

/** 题型大类：只用来做「题型倾向」的加权，不参与排版 */
const FAMILY_OF = {
  ORAL: 'CALC', ORAL_MISSING: 'CALC', ORAL_REMAINDER: 'CALC', VERTICAL: 'CALC',
  EXPRESSION: 'CALC', FRACTION: 'CALC', EQUATION: 'CALC', RATIONAL: 'CALC',
  ALGEBRA: 'CALC', RADICAL: 'CALC', MAKE_TEN: 'CALC', CHAIN_CALC: 'CALC',
  MIXED_CALC: 'CALC', TENS_CALC: 'CALC', TWO_DIGIT: 'CALC', COMPARE: 'CALC',
  COUNTING: 'CALC', FIGURE_EXPR: 'CALC',
  WORD_PROBLEM: 'APPLY', PHYSICS_CALC: 'APPLY', CHEMISTRY_CALC: 'APPLY', BAR_CHART: 'APPLY',
  SENTENCE_ORDER: 'READING', CLOZE: 'READING',
  SCIENCE_JUDGE: 'JUDGE',
  FIGURE_COUNT: 'BLANK', CLOCK_READ: 'BLANK',
  KNOWLEDGE: 'CHOICE', THEME_SORT: 'CHOICE', PHONETIC_PICK: 'CHOICE', GRAMMAR: 'CHOICE',
  IDIOM_MEANING: 'CHOICE', POEM_MATCH: 'CHOICE', CURRIC_CHOICE: 'CHOICE', SCIENCE_CHOICE: 'CHOICE',
  PINYIN: 'BLANK', HANZI: 'BLANK', DICTATION: 'BLANK', ENGLISH_WORD: 'BLANK',
  FILL_BLANK: 'BLANK', UNIT_CONVERT: 'BLANK', NUMBER_PATTERN: 'BLANK', LETTER_ORDER: 'BLANK',
  WORD_SCRAMBLE: 'BLANK', THEME_PINYIN: 'BLANK', THEME_GROUP: 'BLANK', PHONETIC_WRITE: 'BLANK',
  TEXTBOOK_HANZI: 'BLANK', TEXTBOOK_WORD: 'BLANK', IDIOM_FILL: 'BLANK', POEM_FILL: 'BLANK',
  CURRIC_BLANK: 'BLANK', SCIENCE_BLANK: 'BLANK', CUSTOM: 'BLANK',
};

QUESTION_TYPES.forEach((t) => { t.family = FAMILY_OF[t.key]; });

export const FAMILY_LABEL = {
  CHOICE: '选择', BLANK: '填空', JUDGE: '判断',
  CALC: '计算', APPLY: '应用', READING: '阅读',
};

// ------------------------------------------------------------------ 卷子选项

export const TILTS = [
  { key: 'BALANCED', label: '均衡搭配', hint: '按年级的常规配比，各类题型都来一点', prefer: null },
  { key: 'CHOICE', label: '选择题偏多', hint: '选择题占大头，批改快，适合快速过一遍知识点', prefer: 'CHOICE' },
  { key: 'BLANK', label: '填空题偏多', hint: '填空占大头，练书写与记忆', prefer: 'BLANK' },
  { key: 'JUDGE', label: '判断题偏多', hint: '判断题占大头，专门练概念辨析', prefer: 'JUDGE' },
  { key: 'CALC', label: '计算解答偏多', hint: '口算 / 竖式 / 脱式这类计算题占大头', prefer: 'CALC' },
  { key: 'APPLY', label: '应用综合偏多', hint: '应用题与综合题占大头，练审题与列式', prefer: 'APPLY' },
  { key: 'READING', label: '阅读理解偏多', hint: '连词成句 / 选词填空这类语英阅读题占大头', prefer: 'READING' },
];

/**
 * 卷型（试卷类型）—— 参照常见试卷库的栏目分类：
 * 单元测试 / 期中测试 / 期末测试 / 练习题 / 拼音 / 复习题 / 综合测试 / 竞赛 / 奥数 / 小升初。
 * 第一项「全部」= 不限定卷型，题型都来一点。
 *
 * 每一项都必须**真的改变出什么题**，否则又变成「点了有反应、反应是假的」：
 *   kinds     铺几种题型
 *   sizes     第 1、2、3… 种题型各出多少题（按顺序取，超出部分沿用最后一个）
 *   prefer    这种卷优先挑的题型（排到最前）；空 = 只看「题型倾向」
 *   boost     题库选题难度上调几档（和用户选的难度相加，1~3 封顶）
 *   only      只能按这个科目出（拼音 = 语文，奥数 = 数学）
 *   minGrade / maxGrade   这种卷有意义的年级区间（拼音只到小学；小升初从五年级起）
 */
const THINKING = [
  'NUMBER_PATTERN', 'COMPARE', 'FILL_BLANK', 'WORD_PROBLEM', 'CHAIN_CALC',
  'EXPRESSION', 'FRACTION', 'EQUATION', 'BAR_CHART', 'COUNTING',
];
const BASIC = [
  'ORAL', 'PINYIN', 'DICTATION', 'HANZI', 'ENGLISH_WORD', 'FILL_BLANK',
  'UNIT_CONVERT', 'KNOWLEDGE', 'CURRIC_BLANK', 'SCIENCE_JUDGE',
];
const PINYIN_TYPES = ['PINYIN', 'THEME_PINYIN', 'DICTATION', 'HANZI', 'TEXTBOOK_HANZI'];

export const PAPER_KINDS = [
  {
    key: 'ALL', label: '全部', kinds: 4, sizes: [16, 10, 8, 6], prefer: [],
    hint: '不限定卷型：四类题型各来一点，按年级常规配比',
  },
  {
    key: 'UNIT', label: '单元测试', kinds: 3, sizes: [18, 10, 8], prefer: [],
    hint: '三四种题型均衡，覆盖一个单元，中等题量',
  },
  {
    key: 'MIDTERM', label: '期中测试', kinds: 4, sizes: [18, 10, 10, 8], prefer: [],
    hint: '四种题型均衡，覆盖前半册，题量偏大',
  },
  {
    key: 'FINAL', label: '期末测试', kinds: 5, sizes: [18, 12, 10, 8, 6], prefer: [],
    hint: '五种题型铺满全册，题量最大，适合期末总练',
  },
  {
    key: 'PRACTICE', label: '练习题', kinds: 1, sizes: [30, 12, 8], prefer: [],
    hint: '一两种题型大量练，一页盯住一个知识点',
  },
  {
    key: 'PINYIN', label: '拼音', kinds: 3, sizes: [20, 10, 8], only: 'CHINESE',
    prefer: PINYIN_TYPES, maxGrade: 6,
    hint: '语文拼音专项：看拼音写词语 / 生字注音 / 补全词语（自动切到语文）',
  },
  {
    key: 'REVIEW', label: '复习题', kinds: 4, sizes: [16, 12, 10, 8], prefer: BASIC,
    hint: '题型铺满但以基础题为主，考前查漏补缺',
  },
  {
    key: 'COMPREHENSIVE', label: '综合测试', kinds: 6, sizes: [16, 12, 10, 8, 6, 6], prefer: [],
    hint: '六种题型尽可能铺满，多题型混合检测',
  },
  {
    key: 'CONTEST', label: '竞赛', kinds: 4, sizes: [10, 6, 6, 4], prefer: THINKING, boost: 1,
    hint: '题量少而难：以找规律 / 应用题 / 巧算为主，题库难度上调一档',
  },
  {
    key: 'OLYMPIAD', label: '奥数', kinds: 3, sizes: [8, 6, 6], prefer: THINKING, boost: 1,
    only: 'MATH',
    hint: '数学思维专项：找规律、巧算、应用题（自动切到数学，难度上调一档）',
  },
  {
    key: 'GRADUATION', label: '小升初', kinds: 6, sizes: [16, 12, 10, 8, 6, 6],
    prefer: THINKING, boost: 1, minGrade: 5, maxGrade: 6,
    hint: '五六年级升学衔接：题型铺满、综合度最高，难度上调一档',
  },
];

export const KIND = {};
PAPER_KINDS.forEach((k) => { KIND[k.key] = k; });

/** 老存档里的 purpose（DAILY / UNIT / REVIEW）映射到新卷型，别让更新后的人打开就崩 */
const LEGACY_KIND = { DAILY: 'PRACTICE', UNIT: 'UNIT', REVIEW: 'REVIEW' };

export function kindOf(key) {
  return KIND[key] ?? KIND[LEGACY_KIND[key]] ?? KIND.ALL;
}

/** 这个年级有没有这种卷（拼音只到六年级、小升初从五年级起） */
export function kindFits(grade, key) {
  const k = kindOf(key);
  const lo = k.minGrade ?? 1;
  const hi = k.maxGrade ?? 9;
  return grade >= lo && grade <= hi;
}

// ------------------------------------------------------------------ 数学知识点

/**
 * 小学数学知识点专项 —— 照 shijuan.on2on.cn「知识点导航」那一页搬的，共 15 个。
 *
 * 那个网站的组织方式是「一个知识点一页、只出这一种算式，题量 5~50 自选」。
 * 我们把这一套搬进题库：选中一个或多个知识点，这份卷子就变成**专项卷**，
 * 每个知识点单独一道大题，题干和数字范围逐条对齐，不再被年级的通用配比稀释。
 *
 * render 用的是本项目已有的题型渲染规则（栏数 / 答题线 / 是否带括号），
 * 所以专项卷的纸面跟常规卷完全一致，只是「考什么」被锁死了。
 */
export const MATH_POINTS = [
  { key: 'g1.compare', grade: 1, unit: 1, label: '10以内数的比较', render: 'COMPARE', count: 20,
    hint: '两个 0~10 的数比大小，填 ＞ ＜ ＝' },
  { key: 'g1.add10', grade: 1, unit: 2, label: '10以内加法', render: 'ORAL', count: 20,
    hint: '两个数相加不超过 10' },
  { key: 'g1.sub10', grade: 1, unit: 3, label: '10以内减法', render: 'ORAL', count: 20,
    hint: '被减数不超过 10，不会减出负数' },
  { key: 'g1.sub20', grade: 1, unit: 4, label: '20以内退位减法', render: 'ORAL', count: 20,
    hint: '只出要退位的：11~20 减一位数，个位不够减' },
  { key: 'g1.addSub100', grade: 1, unit: 5, label: '100以内不进退位加减法', render: 'ORAL', count: 20,
    hint: '两位数加减两位数，不出进位、不出退位' },
  { key: 'g2.addSub100Adv', grade: 2, unit: 1, label: '100以内进退位加减法', render: 'ORAL', count: 20,
    hint: '专练进位加与退位减，和不超过 100' },
  { key: 'g2.mul', grade: 2, unit: 2, label: '表内乘法', render: 'ORAL', count: 20,
    hint: '1~9 乘法口诀表内的算式' },
  { key: 'g2.div', grade: 2, unit: 3, label: '表内除法', render: 'ORAL', count: 20,
    hint: '用乘法口诀求商，全部除得尽' },
  { key: 'g2.divRemain', grade: 2, unit: 4, label: '有余数的除法', render: 'ORAL', count: 20,
    hint: '只出带余数的，商与余数分开写' },
  { key: 'g2.mulAddSub', grade: 2, unit: 5, label: '乘加 / 乘减混合运算', render: 'ORAL', count: 20,
    hint: '先乘后加减，加减数不超过 20' },
  { key: 'g3.addSubWan', grade: 3, unit: 1, label: '万以内加减法（笔算）', render: 'VERTICAL', count: 12,
    hint: '三位数或四位数相加减，结果不超过 9999，留竖式空' },
  { key: 'g3.mulMore', grade: 3, unit: 2, label: '多位数乘一位数', render: 'VERTICAL', count: 12,
    hint: '两位数 / 三位数乘一位数，积不超过 10000' },
  { key: 'g3.perimeter', grade: 3, unit: 3, label: '长方形正方形周长', render: 'WORD_PROBLEM', count: 10,
    hint: '给长宽算周长，边长与周长都不超过 200 厘米' },
  { key: 'g3.fractionAddSub', grade: 3, unit: 4, label: '分数简单加减法', render: 'FRACTION', count: 20,
    hint: '同分母分数加减，结果要约成最简' },
  { key: 'g3.mulTwo', grade: 3, unit: 5, label: '两位数乘两位数', render: 'VERTICAL', count: 12,
    hint: '两个两位数相乘，积不超过 9999' },
];

export const POINT_BY_KEY = {};
MATH_POINTS.forEach((p) => { POINT_BY_KEY[p.key] = p; });

/** 这份卷子选中的知识点（过滤掉不认识的 key，老存档里可能有已删除的点） */
export function pointsOf(spec) {
  return (spec.points ?? []).map((k) => POINT_BY_KEY[k]).filter(Boolean);
}

/** 专项卷的题量口径：每个知识点出几题（网站的范围是 5~50） */
export const POINT_COUNTS = [10, 20, 30, 50];

/** 专项卷一共多少题 */
export function pointTotal(spec) {
  const list = pointsOf(spec);
  const per = Math.min(Math.max(spec.pointCount ?? 20, 5), 50);
  return list.length * per;
}

export const ANSWER_MODES = [
  { key: 'NONE', label: '不带答案' },
  { key: 'LAST_PAGE', label: '附答案页' },
];

export const ORIENTATIONS = [
  { key: 'PORTRAIT', label: 'A4 竖版', shortLabel: '竖版', w: 595, h: 842 },
  { key: 'LANDSCAPE', label: 'A4 横版', shortLabel: '横版', w: 842, h: 595 },
];

/** 题间距档位：base 是排版的起点，max 是「纸没满时最多能撑到多宽」 */
export const LINE_GAPS = [
  { key: 'TIGHT', label: '极窄', base: 0, max: 5 },
  { key: 'COMPACT', label: '紧凑', base: 1.5, max: 9 },
  { key: 'NORMAL', label: '标准', base: 3.5, max: 15 },
  { key: 'LOOSE', label: '宽松', base: 7, max: 26 },
  { key: 'CUSTOM', label: '自定义', base: 0, max: 0 },
];

export const FOOTER_SIZES = [
  { key: 'SHORT', label: '矮', height: 20, fontSize: 6.5 },
  { key: 'NORMAL', label: '标准', height: 26, fontSize: 7.5 },
  { key: 'TALL', label: '高', height: 32, fontSize: 9 },
];

/**
 * 页边距（pt）。
 *
 * 12pt ≈ 4.2mm，已经贴到家用打印机的不可打印边界附近（多数喷墨机四周留 3~5mm）。
 * 再往下就不给了：打出来缺一条边，用户会以为是软件把字裁掉了。
 */
export const MARGINS = [
  { key: 12, label: '极窄' },
  { key: 20, label: '窄' },
  { key: 28, label: '中' },
  { key: 40, label: '标准' },
];

/**
 * 正文字号（pt）。
 *
 * 9pt 是小学练习册小字注释的下限；再小家长第一反应就是「这卷子看不清」，
 * 所以不提供 8pt —— 想再省纸请缩页边距，别缩字。
 */
export const BODY_SIZES = [9, 10, 11, 12];

/**
 * 排版密度：一次同时改「页边距 + 正文字号 + 题间距」三个数。
 *
 * 三个数分开调是给微调用的；但用户心里的诉求是「我要一张更紧的卷子」，
 * 让他自己去凑三个滑杆太费事。预设点一下就把三个都摆好，之后仍可单独改 ——
 * 改到跟任何预设都不一致时，界面自动显示「自定义」，不会假装还停在某一档。
 */
export const DENSITIES = [
  { key: 'TIGHT', label: '极紧', margin: 12, bodySize: 10, lineGap: 'TIGHT', hint: '纸边 12pt、字 10pt、题挨着排 —— 一页塞最多，适合纯口算' },
  { key: 'COMPACT', label: '紧凑', margin: 20, bodySize: 10, lineGap: 'COMPACT', hint: '纸边 20pt、字 10pt、行距紧凑 —— 省纸又不挤眼' },
  { key: 'NORMAL', label: '标准', margin: 40, bodySize: 11, lineGap: 'COMPACT', hint: '纸边 40pt、字 11pt、行距紧凑 —— 与 App 出厂一致的那套' },
  { key: 'LOOSE', label: '宽松', margin: 40, bodySize: 12, lineGap: 'LOOSE', hint: '字 12pt、行距宽松 —— 低年级写字大，留白多一点' },
];

/** 当前这套参数落在哪个密度档上；都不匹配就是「自定义」 */
export function densityOf(spec) {
  const margin = spec.margin ?? 40;
  const bodySize = spec.bodySize ?? 11;
  const gap = spec.lineGap ?? 'NORMAL';
  const hit = DENSITIES.find((d) => d.margin === margin && d.bodySize === bodySize && d.lineGap === gap);
  return hit ? hit.key : 'CUSTOM';
}

/**
 * 页脚左下角的默认那句：「学生名 · 每日一练」。
 *
 * 以前默认是空白（家长要干净纸面），但用户的实际用法是「每天给自家孩子打一张」，
 * 页脚署个名字正好能分清是谁的卷子、攒没攒够。选了学生档案就自动带上名字；
 * 用户在「页脚 → 自定义」里写过字，就以他写的为准（这里只是兜底）。
 */
export function defaultFooter(spec) {
  const name = String(spec?.studentName ?? '').trim();
  return name ? `${name} · 每日一练` : '每日一练';
}

export const TERM = { BOTH: 0, FIRST: 1, SECOND: 2 };
const TERM_CN = ['一', '二', '三', '四', '五', '六', '七', '八', '九'];

export const Term = {
  label(term) {
    return term === TERM.FIRST ? '上册' : term === TERM.SECOND ? '下册' : '全学年';
  },
  /** 卷标题 / 学生胶囊上的短后缀：「三上」「三下」 */
  short(grade, term) {
    const g = TERM_CN[grade - 1] ?? String(grade);
    return term === TERM.FIRST ? `${g}上` : term === TERM.SECOND ? `${g}下` : `${g}年级`;
  },
};

// ------------------------------------------------------------------ 年级画像

const GRADE_TABLE = [
  { grade: 1, addMax: 20, mulMax: 0, divisorMax: 0, allowCarry: false, allowBorrow: false, fractionMaxDen: 0, baseDifficulty: 1 },
  { grade: 2, addMax: 100, mulMax: 9, divisorMax: 9, allowCarry: true, allowBorrow: true, fractionMaxDen: 0, baseDifficulty: 1 },
  { grade: 3, addMax: 1000, mulMax: 9, divisorMax: 9, allowCarry: true, allowBorrow: true, fractionMaxDen: 10, baseDifficulty: 2 },
  { grade: 4, addMax: 10000, mulMax: 12, divisorMax: 12, allowCarry: true, allowBorrow: true, fractionMaxDen: 12, baseDifficulty: 2 },
  { grade: 5, addMax: 100000, mulMax: 12, divisorMax: 12, allowCarry: true, allowBorrow: true, fractionMaxDen: 16, baseDifficulty: 3 },
  { grade: 6, addMax: 1000000, mulMax: 12, divisorMax: 12, allowCarry: true, allowBorrow: true, fractionMaxDen: 20, baseDifficulty: 3 },
  { grade: 7, addMax: 1000, mulMax: 12, divisorMax: 12, allowCarry: true, allowBorrow: true, fractionMaxDen: 20, baseDifficulty: 3 },
  { grade: 8, addMax: 10000, mulMax: 15, divisorMax: 15, allowCarry: true, allowBorrow: true, fractionMaxDen: 24, baseDifficulty: 4 },
  { grade: 9, addMax: 100000, mulMax: 15, divisorMax: 15, allowCarry: true, allowBorrow: true, fractionMaxDen: 24, baseDifficulty: 4 },
];

export const GradeProfiles = {
  of(grade) {
    return GRADE_TABLE.find((g) => g.grade === grade) ?? GRADE_TABLE[2];
  },
  gradeLabel(grade) {
    return { 1: '一年级', 2: '二年级', 3: '三年级', 4: '四年级', 5: '五年级', 6: '六年级', 7: '初一', 8: '初二', 9: '初三' }[grade] ?? `${grade}年级`;
  },
  stageLabel(grade) {
    return grade <= 6 ? '小学' : '初中';
  },
};

// ------------------------------------------------------------------ 随机数

/**
 * 确定性伪随机（mulberry32）。接口照抄 java.util.Random 常用的三个方法，
 * 这样从 Kotlin 移植过来的生成器几乎不用改：nextInt(n) / nextInt(range) / nextBoolean()。
 *
 * 为什么不直接用 Math.random：同 spec + 同 seed 必须能重出同一张卷
 * （「再印一份一样的」和「错题重练」都依赖它）。
 */
export class Rng {
  constructor(seed) {
    this.s = (seed >>> 0) || 0x9e3779b9;
  }

  next() {
    this.s = (this.s + 0x6d2b79f5) >>> 0;
    let t = this.s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  }

  /** nextInt(bound) 或 nextInt(from, to)（闭区间，和 App 里的 r() 一致） */
  nextInt(a, b) {
    if (b === undefined) return Math.floor(this.next() * a);
    if (b <= a) return a;
    return a + Math.floor(this.next() * (b - a + 1));
  }

  nextBoolean() {
    return this.next() < 0.5;
  }

  pick(arr) {
    return arr[this.nextInt(arr.length)];
  }

  shuffle(arr) {
    const out = arr.slice();
    for (let i = out.length - 1; i > 0; i--) {
      const j = this.nextInt(i + 1);
      [out[i], out[j]] = [out[j], out[i]];
    }
    return out;
  }
}

/** 每道大题用独立种子，改动某一题的数量不会牵动其它大题的题目 */
export function sectionSeed(paperSeed, sectionIndex) {
  return ((paperSeed >>> 0) ^ Math.imul(sectionIndex, 0x9e3779b1)) >>> 0;
}

// ------------------------------------------------------------------ 去重

/** FNV-1a 32 位：同卷去重、历史卷去重、错题去重都用它 */
export function stableHash(text) {
  const normalized = String(text).trim().replace(/\s+/g, '');
  let h = 0x811c9dc5;
  for (let i = 0; i < normalized.length; i++) {
    h ^= normalized.charCodeAt(i);
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return h;
}

// ------------------------------------------------------------------ 出题上下文

export function makeContext({ grade, count, difficulty = 2, rng, blocked, subjects = [], topicIds = [], term = 0, allowReuse = true }) {
  return {
    grade, count, difficulty, rng,
    blocked: blocked ?? new Set(),
    subjects: new Set(subjects),
    topicIds: new Set(topicIds),
    term,
    allowReuse,
  };
}

export const DEFAULT_SPEC = {
  grade: 3,
  subject: 'MATH',
  title: '',
  subtitle: '',
  counts: {},
  difficulty: 2,
  columns: 2,
  answerMode: 'LAST_PAGE',
  showNameRow: true,
  fontSize: 11,
  seed: 1,
  isMistakePaper: false,
  subjects: [],
  tilt: 'BALANCED',
  /** 卷型：全部 / 单元测试 / 期中测试 / 期末测试 / 练习题 / 拼音 / 复习题 / 综合测试 / 竞赛 / 奥数 / 小升初 */
  kind: 'ALL',
  orientation: 'PORTRAIT',
  topicIds: [],
  headerOneLine: false,
  footerText: null,
  showCreatedAt: true,
  showPageNo: true,
  footerSize: 'NORMAL',
  // 行距出厂用「紧凑」而不是「标准」：App 侧早就是这个默认值，
  // 两端同一张密度表才能让「标准」在两处是同一张纸
  lineGap: 'COMPACT',
  lineGapCustom: 8,
  targetPages: 0,
  /** 纸边留白（pt）：12 极窄 / 20 窄 / 28 中 / 40 标准 */
  margin: 40,
  /** 正文字号（pt）：9 / 10 / 11 / 12，题号题干一起变 */
  bodySize: 11,
  term: TERM.FIRST,
  studentName: '',
  customSheets: [],
  /** 选中的数学知识点（专项卷）：空 = 按常规配比出卷 */
  points: [],
  /** 专项卷里每个知识点出几题 */
  pointCount: 20,
};

/** 兼容单科旧数据：没勾选多学科时按主科目处理 */
export function effectiveSubjects(spec) {
  return spec.subjects && spec.subjects.length ? [...new Set(spec.subjects)] : [spec.subject];
}
