/**
 * 内置内容加载器 —— 对应 App 的 CurriculumBank / ScienceBank / BankQuestionLoader。
 *
 * 数据全部是构建期导出的静态 JSON，网页版不开后端：
 *  - data/content.json    语法 / 音标 / 成语 / 古诗 / 课本生字词 / 主题识字 / 打印纸
 *  - data/taxonomy/*.json 课标题库五科（原始字段名压缩过，见下）
 *  - data/science/bank.json 小学科学 216 道
 *  - data/bank/builtin_questions.json 自编题库 74 道
 *  - data/textbook/catalog.json 教材链接（只存链接，不打包 PDF）
 *
 * 课标题库的字段压缩规则（照 App 的 parse）：
 *   topic    : i=id n=名称 g=年级 s=上/下 c=章节 d=说明 cr=掌握标准 dq/da=诊断题答案
 *   question : i=id ti=知识点id t=2 表示选择题 p=题干 o=[[key,文本]] a=答案 e=解析 f=难度 g=年级
 */

export const DATA = {
  content: null,
  curriculum: [],      // 五科题目合并后的平铺列表（懒加载后追加）
  topics: [],          // 五科知识点合并
  science: [],
  bank: [],
  catalog: [],
  examBank: [],        // 真题库：从下载的 docx 试卷切出来的题，见 tools/build-question-bank.py
  refs: { cnWords: [], enWords: [], classifiers: [], antonyms: [] },  // 参考资料库（v0.28.0 / v0.29.0），见 refs/*.json
  stats: null,
  loadedSubjects: new Set(),
};

const subjectKey = (s) => String(s).toLowerCase();

/**
 * 单文件版（双击 .html，走 file://）没有 http 服务，fetch 一律失败。
 * 构建脚本 tools/build-single.py 会把 data/ 下每个 JSON 塞成
 *   <script type="application/json" data-pm="data/content.json">{…}</script>
 * 这里按路径找标签、解析一次就缓存 —— 启动时一个字节都不解析，用到哪科解哪科。
 *
 * 大文件（真题库 11 MB）走的是压缩版：
 *   <script type="application/json-gz" data-pm="data/papers/questions.json">H4sI…</script>
 * gzip 后 1.88 MB、base64 后 2.4 MB，单文件体积掉一半还多。
 * 解压用浏览器自带的 DecompressionStream（Chrome 80+ / Safari 16.4+ / Firefox 113+），
 * 不引任何第三方库 —— 这个项目从头到尾没有运行时依赖。
 */
const inlineCache = new Map();

async function inlineJson(path) {
  const key = String(path).replace(/^\.\//, '');
  if (inlineCache.has(key)) return inlineCache.get(key);

  const sel = (type) => document.querySelector(`script[type="${type}"][data-pm="${key}"]`);
  const plain = sel('application/json');
  const packed = plain ? null : sel('application/json-gz');

  let value = null;
  if (plain) {
    try { value = JSON.parse(plain.textContent); } catch (err) {
      throw new Error(`内置数据 ${key} 解析失败：${err.message}`);
    }
  } else if (packed) {
    value = await gunzipJson(packed.textContent, key);
  }
  inlineCache.set(key, value);
  return value;
}

/** base64 → gzip 解压 → JSON。走流式，省掉「先解成字符串再 parse」那一次整拷。 */
async function gunzipJson(b64, key) {
  if (typeof DecompressionStream !== 'function') {
    throw new Error(
      `内置数据 ${key} 是压缩过的，这个浏览器不支持 DecompressionStream。` +
      '请用较新的 Chrome / Edge，或 Safari 16.4 以上打开。',
    );
  }
  const raw = atob(b64.trim());
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i += 1) bytes[i] = raw.charCodeAt(i);
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip'));
  return new Response(stream).json();
}

async function getJson(path) {
  const hit = await inlineJson(path);
  if (hit) return hit;
  const res = await fetch(new URL(path, document.baseURI));
  if (!res.ok) throw new Error(`读取 ${path} 失败：${res.status}`);
  return res.json();
}

export async function loadCoreData() {
  const [content, science, bank, catalog, stats, cnWords, enWords, classifiers, antonyms] =
    await Promise.all([
      getJson('data/content.json'),
      getJson('data/science/bank.json'),
      getJson('data/bank/builtin_questions.json'),
      getJson('data/textbook/catalog.json'),
      getJson('data/taxonomy/index.json'),
      // 参考资料库（词表 / 英语单词 / 量词 / 反义词）—— 和 App 的 assets/refs 是同一份文件
      getJson('data/refs/cn_words.json').catch(() => []),
      getJson('data/refs/en_words.json').catch(() => []),
      getJson('data/refs/classifiers.json').catch(() => []),
      getJson('data/refs/antonyms.json').catch(() => []),
    ]);
  DATA.content = content;
  DATA.science = science.questions ?? [];
  DATA.bank = bank.questions ?? [];
  DATA.catalog = catalog.books ?? [];
  DATA.stats = stats;
  DATA.refs = {
    cnWords: cnWords ?? [],
    enWords: enWords ?? [],
    classifiers: classifiers ?? [],
    antonyms: antonyms ?? [],
  };
}

/**
 * 真题库（从下载的 docx 试卷里切出来的题）。
 *
 * 为什么懒加载：题库有上万道题、好几 MB，首屏用不到，跟着 loadCoreData 一起拉
 * 会把打开应用的时间拖长一大截。用户真去点「真题」时才载。
 */
let examBankPromise = null;
export function loadExamBank() {
  if (!examBankPromise) {
    examBankPromise = getJson('data/papers/questions.json')
      .then((d) => {
        DATA.examBank = d?.items ?? [];
        return DATA.examBank;
      })
      // 缺文件不该让出卷页白屏（精简部署、旧存档都可能没有这份）
      .catch(() => {
        DATA.examBank = [];
        return [];
      });
  }
  return examBankPromise;
}

/** 按需加载某一科的课标题库（一科 2500 道题、约 0.8MB，五科全载没必要） */export async function loadCurriculum(subject) {
  const key = subjectKey(subject);
  if (DATA.loadedSubjects.has(key)) return;
  const root = await getJson(`data/taxonomy/${key}.json`);
  const topics = (root.topics ?? []).map((o) => ({
    id: o.i,
    name: o.n,
    grade: o.g,
    semester: o.s ?? '',
    chapter: o.c ?? '',
    desc: o.d ?? '',
    criteria: o.cr ?? [],
    diagnosticQuestion: o.dq ?? '',
    diagnosticAnswer: o.da ?? '',
    subject,
  }));
  const questions = (root.questions ?? []).map((o) => ({
    id: o.i,
    topicId: o.ti,
    grade: o.g,
    subject,
    choice: o.t === 2,
    prompt: o.p ?? '',
    options: (o.o ?? []).map((pair) => ({ key: pair[0], text: pair[1] })),
    answer: o.a ?? '',
    explain: o.e ?? '',
    difficulty: o.f ?? 2,
  }));
  DATA.topics.push(...topics);
  DATA.curriculum.push(...questions);
  DATA.loadedSubjects.add(key);
}

export async function loadCurriculumFor(subjects) {
  for (const s of subjects) await loadCurriculum(s);
}

const MIN_POOL = 16;

/** 知识点 id → 学期（"上" / "下" / "全"），学期筛选靠它 */
function semesterOf(topicId, subject) {
  const t = DATA.topics.find((x) => x.id === topicId && x.subject === subject);
  return t ? t.semester : '';
}

function byDifficulty(list, difficulty) {
  const same = list.filter((q) => q.difficulty === difficulty);
  return same.length >= MIN_POOL / 2 ? same : list;
}

/**
 * 某年级 + 勾选科目的题池（对应 CurriculumBank.pool）。
 *
 * 三层收窄：本年级 → 近两年 → 全部低年级；绝不取高年级的题。
 * 学期筛选只对「真带学期数据」的知识点生效，「全」册的照常出现；
 * 筛空了就退回不限学期，不让「选了上册一道题都出不来」变成坏卷子。
 */
export function curricPool({ subjects, grade, difficulty = 2, choice = null, topicIds = [], term = 0 }) {
  const targets = subjects && subjects.length ? subjects : ['MATH', 'CHINESE', 'ENGLISH', 'SCIENCE', 'PHYSICS', 'CHEMISTRY'];
  const wanted = term === 1 ? '上' : term === 2 ? '下' : '';
  const collected = [];
  for (const s of targets) {
    for (const q of DATA.curriculum) {
      if (q.subject !== s) continue;
      if (topicIds.length && !topicIds.includes(q.topicId)) continue;
      if (q.grade > grade) continue;
      if (choice !== null && q.choice !== choice) continue;
      if (wanted) {
        const sem = semesterOf(q.topicId, q.subject);
        if (sem && sem !== '全' && sem !== wanted) continue;
      }
      collected.push(q);
    }
  }
  if (!collected.length && wanted) {
    return curricPool({ subjects, grade, difficulty, choice, topicIds, term: 0 });
  }
  if (!collected.length) return collected;
  if (topicIds.length) return byDifficulty(collected, difficulty);

  const sameGrade = collected.filter((q) => q.grade === grade);
  if (sameGrade.length >= MIN_POOL) return byDifficulty(sameGrade, difficulty);
  const nearby = collected.filter((q) => q.grade >= grade - 2 && q.grade <= grade);
  if (nearby.length >= MIN_POOL) return byDifficulty(nearby, difficulty);
  return byDifficulty(collected, difficulty);
}

/** chapter → 印得出来的单元名（去掉「一年级上册」「七上」前缀与末尾小节号） */
export function unitName(chapter) {
  return String(chapter)
    .replace(/^[一二三四五六七八九]年级[上下]册\s*/, '')
    .replace(/^[七八九][上下]\s*/, '')
    .replace(/\s*\d+(\.\d+)+\s*$/, '')
    .trim();
}

/** 课本页：某科目某年级这一学年的教材单元（按 chapter 聚合，下册排后面） */
export function curricUnits(subject, grade) {
  const order = new Map();
  const semesters = new Map();
  for (const t of DATA.topics) {
    if (t.subject !== subject || t.grade !== grade) continue;
    const unit = unitName(t.chapter);
    if (!unit) continue;
    if (!order.has(unit)) order.set(unit, []);
    order.get(unit).push(t);
    if (!semesters.has(unit)) semesters.set(unit, t.semester);
  }
  return [...order.entries()]
    .map(([name, topics]) => ({ name, subject, grade, semester: semesters.get(name) ?? '', topics }))
    .sort((a, b) => (a.semester === '下' ? 1 : 0) - (b.semester === '下' ? 1 : 0));
}

/** 知识点 id → 名称（答案页 / 错题本按名称归类，不显示内部 id） */
export function topicName(topicId) {
  const t = DATA.topics.find((x) => x.id === topicId);
  return t ? t.name : '';
}

export const CURRIC_META = {
  source: 'https://github.com/xiaoyang111222/cn-math-taxonomy',
  license: 'CC BY-NC 4.0',
};
