/**
 * 出卷编排 —— 对应 App 的 PaperComposer + CustomSheet。
 *
 * 种子三层，保证可复现：
 *   卷子 seed → 每个大题 seed = paperSeed xor 常数×序号 → 每题顺序推进同一个 Rng。
 * 同 spec + 同 seed 出同一张卷；「换一套」只换 seed。
 */

import {
  DEFAULT_SPEC, TYPE, QUESTION_TYPES, Subject, SUBJECT_LABEL, Term,
  GradeProfiles, effectiveSubjects, makeContext,
} from './models.js';
import { Rng, sectionSeed } from './models.js';
import { availableTypes, generatorOf, isCrossSubject, runGenerator, subjectOf, generatorCount } from './registry.js';

const CN_NUM = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十'];
const POINTS_PER_QUESTION = 2;

/** 大题标题的统一格式：「一、口算（每题 2 分，共 60 分）」 */
export function titleFor(name, points, count, ordinal) {
  const cn = CN_NUM[ordinal - 1] ?? String(ordinal);
  return `${cn}、${name}（每题 ${points} 分，共 ${count * points} 分）`;
}

export function sectionTitle(type, count, ordinal, prefix = '') {
  return titleFor(`${prefix}${TYPE[type].label}`, POINTS_PER_QUESTION, count, ordinal);
}

/**
 * 用一组现成题目拼出一道大题：序号、标题、栏数、答题线、说明全按题型规则来。
 * 「加一题」也要走这里 —— 两处各写一遍就会出现「每题 2 分共 6 分」底下印 4 道题。
 */
export function sectionOf(spec, type, questions, ordinal, instruction = TYPE[type].instruction, prefix = '') {
  const t = TYPE[type];
  return {
    index: ordinal,
    title: sectionTitle(type, questions.length, ordinal, prefix),
    name: `${prefix}${t.label}`,
    points: POINTS_PER_QUESTION,
    type,
    questions,
    // 栏数：题型推荐栏数与用户选的全局栏数取较大者；
    // 带答题线的题型不跟全局走（线是给人写的，栏越窄越没法用）
    columns: t.columns <= 1 ? 1 : t.answerLines > 0 ? t.columns : Math.max(t.columns, spec.columns),
    answerLines: t.answerLines,
    instruction,
    padWithLines: t.padWithLines !== false,
  };
}

export function subjectPrefixFor(spec, type) {
  if (effectiveSubjects(spec).length <= 1) return '';
  if (isCrossSubject(type)) return '';
  const s = subjectOf(type);
  return s ? `${SUBJECT_LABEL[s]}·` : '';
}

/**
 * 把「每道题都重复一遍的做题指令」抽出来，作为大题说明只印一次。
 * 判据是最长公共前缀里最后一个冒号；三条保守限制见 Kotlin 注释。
 */
function commonPrefix(a, b) {
  const n = Math.min(a.length, b.length);
  let i = 0;
  while (i < n && a[i] === b[i]) i++;
  return a.slice(0, i);
}

function hoistInstruction(questions) {
  if (questions.length < 3) return [null, questions];
  let prefix = questions[0].stem;
  questions.forEach((q) => { prefix = commonPrefix(prefix, q.stem); });
  const colon = Math.max(prefix.lastIndexOf('：'), prefix.lastIndexOf(':'));
  if (colon < 3) return [null, questions];
  const head = prefix.slice(0, colon + 1);
  const letters = [...head].filter((c) => /\p{L}/u.test(c)).length;
  if (letters < 3) return [null, questions];
  const rest = questions.map((q) => q.stem.slice(head.length).trim());
  if (rest.some((r) => r === '')) return [null, questions];
  return [head, questions.map((q, i) => ({ ...q, stem: rest[i] }))];
}

/** 出卷主流程：spec → 带题号与分值的卷子 */
export function compose(spec, historyHashes = new Set()) {
  const blocked = new Set(historyHashes);
  const sections = [];
  let nextNo = 1;

  const ordered = Object.entries(spec.counts)
    .filter(([k, v]) => v > 0 && TYPE[k])
    .sort((a, b) => TYPE[a[0]].order - TYPE[b[0]].order);

  ordered.forEach(([type, rawCount], index) => {
    const count = Math.min(Math.max(rawCount, 1), 200);
    const gen = generatorOf(type, spec.grade);
    if (!gen) return;
    const rng = new Rng(sectionSeed(spec.seed, index));
    const ctx = makeContext({
      grade: spec.grade,
      count,
      difficulty: spec.difficulty,
      rng,
      blocked,
      subjects: effectiveSubjects(spec),
      topicIds: spec.topicIds ?? [],
      term: spec.term ?? 0,
    });
    const drawn = runGenerator(gen, ctx).map((q, i) => ({ ...q, no: nextNo + i }));
    nextNo += drawn.length;
    if (!drawn.length) return;

    const prefix = subjectPrefixFor(spec, type);
    const [hoisted, trimmed] = hoistInstruction(drawn);
    const instruction = [TYPE[type].instruction, hoisted].filter((x) => x && x.trim()).join('　');
    sections.push(sectionOf(spec, type, trimmed, sections.length + 1, instruction, prefix));
  });

  // 自编题：用户自己写的内容，原样接在常规大题后面（不走随机出题，但排版规则一模一样）
  (spec.customSheets ?? []).forEach((sheet) => {
    if (!sheet.raw || !sheet.raw.trim()) return;
    const rng = new Rng(sectionSeed(spec.seed, 900 + sections.length));
    const built = buildCustomSheet(sheet, rng);
    if (!built.questions.length) return;
    const qs = built.questions.map((q, i) => ({ ...q, no: nextNo + i }));
    nextNo += qs.length;
    const ordinal = sections.length + 1;
    const name = sheet.title?.trim() || CUSTOM_MODES[sheet.mode]?.label || TYPE.CUSTOM.label;
    sections.push({
      index: ordinal,
      title: titleFor(name, sheet.points ?? 2, qs.length, ordinal),
      name,
      points: sheet.points ?? 2,
      type: 'CUSTOM',
      questions: qs,
      columns: Math.min(Math.max(sheet.columns ?? 2, 1), 4),
      answerLines: Math.min(Math.max(sheet.answerLines ?? 1, 0), 6),
      instruction: '',
      padWithLines: true,
    });
  });

  return {
    spec,
    sections,
    questions: sections.flatMap((s) => s.questions),
    createdAt: Date.now(),
  };
}

// ------------------------------------------------------------------ 默认规格

function countFor(t, index, purpose) {
  const table = purpose === 'DAILY' ? [30, 10, 8, 8] : purpose === 'UNIT' ? [18, 10, 8, 6] : [16, 10, 8, 6];
  const want = table[Math.min(Math.max(index, 0), table.length - 1)];
  const al = TYPE[t].answerLines;
  const cap = al >= 3 ? 8 : al === 2 ? 10 : al === 1 ? 16 : Number.MAX_SAFE_INTEGER;
  return Math.min(want, cap);
}

function orderByTilt(types, tilt) {
  const prefer = TILTS_PREFER[tilt];
  if (!prefer) return types;
  const label = { CHOICE: '选择', BLANK: '填空', JUDGE: '判断', CALC: '计算', APPLY: '应用', READING: '阅读' }[prefer];
  return types
    .map((t) => ({
      t,
      score: TYPE[t].label.includes(label) ? 2 : TYPE[t].family === prefer ? 1 : 0,
    }))
    .sort((a, b) => b.score - a.score)
    .map((x) => x.t);
}

const TILTS_PREFER = {
  BALANCED: null, CHOICE: 'CHOICE', BLANK: 'BLANK', JUDGE: 'JUDGE',
  CALC: 'CALC', APPLY: 'APPLY', READING: 'READING',
};

const PURPOSE_KINDS = { DAILY: 1, UNIT: 3, REVIEW: 4 };

/** 科学卷的三块配比：题库每级每型只有 12 道，上限就是 12 */
function scienceCounts(tilt, purpose) {
  const prefer = TILTS_PREFER[tilt];
  const main = prefer === 'CHOICE' ? 'SCIENCE_CHOICE'
    : prefer === 'BLANK' ? 'SCIENCE_BLANK' : 'SCIENCE_JUDGE';
  const others = ['SCIENCE_CHOICE', 'SCIENCE_JUDGE', 'SCIENCE_BLANK'].filter((t) => t !== main);
  const out = {};
  if (purpose === 'DAILY') {
    out[main] = 12;
    out[others[0]] = 8;
  } else if (purpose === 'UNIT') {
    out[main] = 12;
    others.forEach((t) => { out[t] = 8; });
  } else {
    out[main] = 12;
    others.forEach((t) => { out[t] = 10; });
  }
  return out;
}

/** 这个「年级 + 科目」能不能真做出该倾向的题（不能就把倾向置灰，别做假开关） */
export function tiltFits(grade, subjects, tilt) {
  const prefer = TILTS_PREFER[tilt];
  if (!prefer) return true;
  const label = { CHOICE: '选择', BLANK: '填空', JUDGE: '判断', CALC: '计算', APPLY: '应用', READING: '阅读' }[prefer];
  return availableTypes(grade, subjects).some((t) => TYPE[t].family === prefer || TYPE[t].label.includes(label));
}

function pickForSubjects(types, subjects, tilt, purpose) {
  const out = [];
  const target = PURPOSE_KINDS[purpose] + Math.max(subjects.length - 1, 0);
  subjects.forEach((s) => {
    const mine = orderByTilt(types.filter((t) => subjectOf(t) === s), tilt);
    if (mine[0] && !out.includes(mine[0])) out.push(mine[0]);
  });
  let round = 0;
  while (out.length < target && round < 8) {
    let added = false;
    for (const s of subjects) {
      if (out.length >= target) break;
      const mine = orderByTilt(types.filter((t) => subjectOf(t) === s), tilt);
      const next = mine.find((t) => !out.includes(t));
      if (next) { out.push(next); added = true; }
    }
    if (!added) break;
    round++;
  }
  return out;
}

export function specWith({ grade, subject, counts, seed, title, isMistakePaper = false, subjects = [], tilt = 'BALANCED', purpose = 'DAILY', term }) {
  const total = Object.values(counts).reduce((a, b) => a + b, 0);
  const autoTitle = subjects.length > 1
    ? `${GradeProfiles.stageLabel(grade)} · ${GradeProfiles.gradeLabel(grade)} · 综合练习`
    : `${GradeProfiles.stageLabel(grade)} · ${GradeProfiles.gradeLabel(grade)} · ${SUBJECT_LABEL[subject] || ''}练习`;
  return {
    ...DEFAULT_SPEC,
    grade,
    subject,
    title: title ?? autoTitle,
    subtitle: `共 ${total} 题　满分 ${total * 2} 分`,
    counts,
    seed: seed ?? (Date.now() % 2147483647),
    isMistakePaper,
    subjects,
    tilt,
    purpose,
    term: term ?? Term.FIRST,
  };
}

/** 默认规格：打开就能直接出一份 */
export function defaultSpec(grade, subjects, tilt = 'BALANCED', purpose = 'DAILY', term = Term.FIRST) {
  const list = Array.isArray(subjects) ? subjects : [subjects];
  const primary = list[0] ?? Subject.MATH;
  const types = availableTypes(grade, list);
  const counts = {};
  if (list.length === 1 && primary === Subject.SCIENCE) {
    Object.assign(counts, scienceCounts(tilt, purpose));
  } else if (list.length > 1) {
    pickForSubjects(types, list, tilt, purpose).forEach((t, i) => { counts[t] = countFor(t, i, purpose); });
  } else {
    orderByTilt(types, tilt).slice(0, PURPOSE_KINDS[purpose]).forEach((t, i) => { counts[t] = countFor(t, i, purpose); });
  }
  return specWith({ grade, subject: primary, counts, subjects: list, tilt, purpose, term });
}

// ------------------------------------------------------------------ 自编题

export const CUSTOM_MODES = {
  CALC: {
    key: 'CALC', label: '算式', answerLines: 0,
    hint: '每行一个算式，自动算答案；除不尽会印成「商……余数」',
    sample: '24+18\n7×8\n96÷4\n7÷2',
  },
  BLANK: {
    key: 'BLANK', label: '挖空填空', answerLines: 1,
    hint: '用 [ ] 圈出要挖掉的词，其余照原样印',
    sample: '太阳从[东方]升起\n[春天]来了，燕子飞回来了',
  },
  WORD_EN: {
    key: 'WORD_EN', label: '单词默写', answerLines: 1,
    hint: '每行「英文 中文」，纸上印中文、孩子写英文',
    sample: 'apple 苹果\nbanana 香蕉\norange 橙子',
  },
  COPY: {
    key: 'COPY', label: '看词抄写', answerLines: 1,
    hint: '每行一个词，印出来并留一条书写线',
    sample: '春天\n柳树\n燕子\n小溪',
  },
  PLAIN: {
    key: 'PLAIN', label: '直接成题', answerLines: 1,
    hint: '每行一道完整题干；想附答案就写「题干 | 答案」',
    sample: '3 个苹果吃掉 1 个，还剩几个？|2 个',
  },
};

export function newCustomSheet() {
  return {
    id: `cs_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`,
    studentId: '',
    title: '自编题',
    mode: 'PLAIN',
    raw: '',
    columns: 2,
    answerLines: 1,
    shuffle: true,
    count: 0,
    points: 2,
  };
}

function rawLines(sheet) {
  return String(sheet.raw ?? '').split(/[\n\r]/).map((s) => s.trim()).filter(Boolean);
}

/** 去重（英文不区分大小写），按出现顺序保留第一条 */
function dedupe(lines) {
  const seen = new Set();
  const out = [];
  lines.forEach((line) => {
    const key = line.toLowerCase().replace(/\s+/g, ' ');
    if (!seen.has(key)) { seen.add(key); out.push(line); }
  });
  return out;
}

const CALC_RE = /^(-?\d+(?:\.\d+)?)\s*([+\-×xX*÷/])\s*(-?\d+(?:\.\d+)?)$/;
const HOLE_RE = /[[【]([^\]】]+)[\]】]/g;

const isInt = (v) => Number.isInteger(v);

function fmtNum(v) {
  if (!Number.isFinite(v)) return String(v);
  if (isInt(v)) return String(v);
  return String(Math.round(v * 100) / 100);
}

/** 算式：支持 + - × ÷（x、*、/ 都认），末尾多写一个等号也不影响 */
function parseCalc(line) {
  const text = line.replace(/[=＝]$/, '').replace(/＝/g, '=').trim();
  const m = CALC_RE.exec(text.replace(/\s/g, ''));
  if (!m) return null;
  const a = Number(m[1]);
  const b = Number(m[3]);
  const op = m[2];
  const shown = ['*', 'x', 'X', '×'].includes(op) ? '×' : ['/', '÷'].includes(op) ? '÷' : op;
  const stem = `${fmtNum(a)} ${shown} ${fmtNum(b)} = （　　）`;
  if (op === '+') return [stem, fmtNum(a + b)];
  if (op === '-') return [stem, fmtNum(a - b)];
  if (['×', '*', 'x', 'X'].includes(op)) return [stem, fmtNum(a * b)];
  if (b === 0) return null;
  const q = a / b;
  if (isInt(q)) return [stem, fmtNum(q)];
  // 除不尽：整数除法按小学写法印「商……余数」；操作数带小数就给小数
  if (isInt(a) && isInt(b)) {
    const qi = Math.floor(q);
    return [stem, `${qi}……${a - qi * b}`];
  }
  return [stem, fmtNum(Math.round(q * 100) / 100)];
}

function parseBlank(line) {
  const holes = [...line.matchAll(HOLE_RE)];
  if (!holes.length) return null;
  return [line.replace(HOLE_RE, '＿＿＿'), holes.map((h) => h[1]).join('、')];
}

function parseWordEn(line) {
  const parts = line.split(/\s*[|｜=＝:：]\s*|\s+/).map((s) => s.trim()).filter(Boolean);
  const en = parts[0];
  if (!en) return null;
  // 只认字母开头的英文，避免把一整句中文当单词
  if (!/^[A-Za-z]/.test(en) || [...en].some((c) => c.charCodeAt(0) > 127)) return null;
  const zh = parts[1];
  const stem = zh ? `${zh}　＿＿＿＿＿＿` : `＿＿＿＿＿＿（首字母 ${en[0].toUpperCase()}）`;
  return [stem, en];
}

function parsePlain(line) {
  const idx = Math.max(line.lastIndexOf('|'), line.lastIndexOf('｜'));
  if (idx > 0 && idx < line.length - 1) {
    return [line.slice(0, idx).trim(), line.slice(idx + 1).trim()];
  }
  // 没写答案：题照样印，答案页标「见题面」——空着更像漏印
  return [line, '见题面'];
}

function toQuestion(mode, line) {
  switch (mode) {
    case 'CALC': return parseCalc(line);
    case 'BLANK': return parseBlank(line);
    case 'WORD_EN': return parseWordEn(line);
    case 'COPY': return [line, line];
    default: return parsePlain(line);
  }
}

/** 把原文变成题目。无效行（算式写错、挖空没标记）跳过，并如实回传去重了几条 */
export function buildCustomSheet(sheet, rng = new Rng(1)) {
  const all = rawLines(sheet);
  const lines = dedupe(all);
  const duplicates = all.length - lines.length;
  const parsed = lines.map((line) => toQuestion(sheet.mode, line)).filter(Boolean);
  const ordered = sheet.shuffle === false ? parsed : rng.shuffle(parsed);
  const capped = sheet.count > 0 ? ordered.slice(0, sheet.count) : ordered;
  return {
    duplicates,
    questions: capped.map(([stem, answer]) => ({
      no: 0, type: 'CUSTOM', stem, answer, topic: sheet.title ?? '', figure: null,
    })),
  };
}

/** 界面预览用：这批内容最终能出几道题 */
export function customValidCount(sheet) {
  return buildCustomSheet(sheet, new Rng(0)).questions.length;
}

// ------------------------------------------------------------------ 快捷模板

const TPL = {
  englishFun: { WORD_SCRAMBLE: 10, SENTENCE_ORDER: 5, CLOZE: 6 },
  themeLiteracy: { THEME_PINYIN: 12, THEME_SORT: 6, THEME_GROUP: 6 },
  ipaGrammar: { PHONETIC_PICK: 8, PHONETIC_WRITE: 6, GRAMMAR: 10 },
  textbook: { TEXTBOOK_HANZI: 14, TEXTBOOK_WORD: 14 },
};

export function templatesFor(grade, subjects) {
  const list = Array.isArray(subjects) ? subjects : [subjects];
  const available = new Set(availableTypes(grade, list));
  const raw = [
    { key: 'daily', label: '口算天天练', desc: '一页 30 题，5 分钟', wanted: { ORAL: 30 } },
    {
      key: 'variety', label: '计算题大全', desc: '填括号 + 凑十 + 连加连减 + 换算',
      wanted: { FILL_BLANK: 8, MAKE_TEN: 5, CHAIN_CALC: 6, TENS_CALC: 5, TWO_DIGIT: 5, UNIT_CONVERT: 5 },
    },
    {
      key: 'thinking', label: '思维小练', desc: '比大小 + 找规律 + 填括号',
      wanted: { COMPARE: 8, NUMBER_PATTERN: 6, FILL_BLANK: 6, COUNTING: 5 },
    },
    {
      key: 'calc', label: '计算专项', desc: '口算 + 竖式 + 脱式',
      wanted: { ORAL: 20, VERTICAL: 6, EXPRESSION: 6, RATIONAL: 20, ALGEBRA: 10 },
    },
    { key: 'unit', label: '单元综合', desc: '各题型混合，附答案页', wanted: {} },
    { key: 'english_fun', label: '英语趣味练', desc: '单词拼装 + 连词成句 + 选词填空', wanted: TPL.englishFun },
    { key: 'theme_literacy', label: '主题识字卷', desc: '超市 / 学校 / 交通…6 大生活场景', wanted: TPL.themeLiteracy },
    { key: 'ipa_grammar', label: '音标语法卷', desc: '音标辨认 + 看音标写单词 + 语法选择', wanted: TPL.ipaGrammar },
    { key: 'textbook', label: '课本同步卷', desc: '课本生字注音 + 课本单词默写', wanted: TPL.textbook },
    {
      key: 'words', label: '字词默写', desc: '看拼音写词语 + 补全',
      wanted: { PINYIN: 16, DICTATION: 12, HANZI: 8, ENGLISH_WORD: 24 },
    },
    { key: 'knowledge', label: '知识点题库', desc: '内置自编选择题，按所选科目抽题', wanted: { KNOWLEDGE: 20 } },
    { key: 'curric', label: '知识点闯关', desc: '课标知识点：填空 + 选择', wanted: { CURRIC_BLANK: 10, CURRIC_CHOICE: 10 } },
  ];
  return raw.map((p) => {
    let wanted = p.wanted;
    if (p.key === 'unit') {
      wanted = {};
      [...available].slice(0, 4).forEach((t, i) => {
        wanted[t] = TYPE[t].answerLines > 0 ? 3 : i === 0 ? 20 : 8;
      });
    }
    const usable = {};
    Object.entries(wanted).forEach(([k, v]) => { if (available.has(k)) usable[k] = v; });
    return Object.keys(usable).length ? { ...p, wanted: usable } : null;
  }).filter(Boolean);
}

export { generatorCount };
