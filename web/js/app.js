/**
 * 网页版试卷工坊 —— 应用外壳：状态、存储、路由、各个页面。
 *
 * 与 App 的对应关系：
 *   出卷页   ↔ feature/Screens.kt 的 CreateScreen
 *   预览页   ↔ PreviewScreen
 *   错题本   ↔ 批改记错题
 *   试卷库   ↔ 本地历史卷
 *   课本     ↔ TextbookScreen（课标单元 / 教材链接）
 *   打印纸   ↔ ReferenceData + PracticeData（内置速查表与格纸）
 *   更多     ↔ 数据统计 / 打印帮助 / 关于
 */

import {
  DEFAULT_SPEC, SUBJECT_ALL, SUBJECT_LABEL, QUESTION_TYPES, TYPE, TILTS, PURPOSES,
  ANSWER_MODES, ORIENTATIONS, LINE_GAPS, FOOTER_SIZES, TERM, Term, GradeProfiles,
  subjectAvailableFor, effectiveSubjects, stableHash, Rng,
} from './core/models.js';
import {
  compose, defaultSpec, templatesFor, customValidCount, buildCustomSheet,
  CUSTOM_MODES, newCustomSheet, tiltFits, sectionTitle, titleFor,
} from './core/composer.js';
import { availableTypes, generatorOf, runGenerator, generatorCount } from './core/registry.js';
import { renderPaper, assemble } from './core/renderer.js';
import { renderPages, printPaper, describe, printNodes } from './ui/paper.js';
import { referenceSheet, practiceSheet } from './ui/sheets.js';
import { DATA, loadCoreData, loadCurriculumFor, curricUnits, CURRIC_META } from './data/store.js';
import {
  openModal, closeModal, toast, confirmDialog, field, select, input, textarea, chips,
} from './ui/dialogs.js';

const STORE_KEY = 'papermill.web.v2';

const state = {
  tab: 'create',
  spec: null,
  students: [],
  customSheets: [],
  history: [],
  mistakes: [],
  zoom: 1,
  // 默认「按宽度铺满」；用户点过 ＋/－ 之后就按他设的来
  zoomAuto: true,
  // 当前预览
  paper: null,
  result: null,
  previewKind: 'paper',   // paper | sheet
  sheetNode: null,
  sheetTab: 'ref',
  studentFilter: '',
};

// ------------------------------------------------------------------ 存储

function save() {
  try {
    localStorage.setItem(STORE_KEY, JSON.stringify({
      spec: state.spec,
      students: state.students,
      customSheets: state.customSheets,
      history: state.history,
      mistakes: state.mistakes,
      zoom: state.zoom,
      zoomAuto: state.zoomAuto,
      tab: state.tab,
    }));
  } catch (err) {
    console.warn('保存失败（可能超配额）', err);
  }
}

function load() {
  try {
    const raw = localStorage.getItem(STORE_KEY);
    if (!raw) return false;
    const data = JSON.parse(raw);
    state.spec = data.spec ?? null;
    state.students = data.students ?? [];
    state.customSheets = data.customSheets ?? [];
    state.history = data.history ?? [];
    state.mistakes = data.mistakes ?? [];
    state.zoom = data.zoom ?? 1;
    state.zoomAuto = data.zoomAuto ?? true;
    return true;
  } catch (err) {
    console.warn('读取本地数据失败', err);
    return false;
  }
}

// ------------------------------------------------------------------ 小工具

const $ = (sel) => document.querySelector(sel);

function h(tag, attrs = {}, children = []) {
  const node = document.createElement(tag);
  Object.entries(attrs).forEach(([k, v]) => {
    if (k === 'class') node.className = v;
    else if (k === 'html') node.innerHTML = v;
    else if (k === 'text') node.textContent = v;
    else if (k.startsWith('on')) node.addEventListener(k.slice(2).toLowerCase(), v);
    else if (v !== false && v != null) node.setAttribute(k, v);
  });
  (Array.isArray(children) ? children : [children]).forEach((c) => {
    if (c == null) return;
    node.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
  });
  return node;
}

const card = (title, ...children) => h('section', { class: 'card' }, [
  title ? h('h2', { text: title }) : null,
  ...children,
]);

const row = (...children) => h('div', { class: 'row' }, children);

function button(label, kind, onClick) {
  return h('button', { class: `btn ${kind ?? ''}`, text: label, onclick: onClick });
}

function primarySubjects() {
  return effectiveSubjects(state.spec);
}

/** 年级/科目/学期变了才重算题量 —— 不要把用户手动调的题量冲掉 */
function recomputeCounts() {
  const spec = state.spec;
  const auto = defaultSpec(spec.grade, primarySubjects(), spec.tilt, spec.purpose, spec.term);
  spec.counts = auto.counts;
  if (!spec.title || /^(小学|初中) · /.test(spec.title)) spec.title = auto.title;
  spec.subtitle = auto.subtitle;
}

function ensureSpec() {
  if (!state.spec) {
    state.spec = defaultSpec(3, ['MATH'], 'BALANCED', 'DAILY', TERM.FIRST);
  }
  if (!state.spec.counts) state.spec.counts = {};
  if (!state.spec.subjects) state.spec.subjects = [state.spec.subject];
  if (state.spec.customSheets === undefined) state.spec.customSheets = [];
  return state.spec;
}

const totalCount = (counts) => Object.values(counts ?? {}).reduce((a, b) => a + (b || 0), 0);

// ------------------------------------------------------------------ 出卷

async function generatePaper(spec = ensureSpec()) {
  const subs = effectiveSubjects(spec);
  // 课标题库是按科目分文件懒加载的：要用到哪些科目就先读进来
  const needCurric = Object.keys(spec.counts).some((k) => k.startsWith('CURRIC_'));
  if (needCurric) await loadCurriculumFor(subs);
  const paper = compose(spec);
  if (!paper.questions.length) {
    toast('这个组合一道题都没出出来，换个题型试试');
    return null;
  }
  const doc = assemble(spec, paper);
  const result = renderPaper(doc);
  state.paper = paper;
  state.result = result;
  state.previewKind = 'paper';
  history.lastSpec = spec;
  return { paper, result };
}

async function openPreview() {
  state.tab = 'preview';
  render();
}

// ------------------------------------------------------------------ 页面：出卷

function studentStrip() {
  const wrap = h('div', { class: 'student-strip' });
  const all = h('button', {
    class: `student-pill${state.spec.studentName ? '' : ' on'}`,
    text: '全班',
    onclick: () => {
      state.spec.studentName = '';
      save();
      render();
    },
  });
  wrap.appendChild(all);
  state.students.forEach((s) => {
    const on = state.spec.studentName === s.name;
    const pill = h('button', {
      class: `student-pill${on ? ' on' : ''}`,
      onclick: () => applyStudent(s),
    }, [
      h('b', { text: s.name }),
      h('span', { text: `${Term.short(s.grade, s.term)}` }),
    ]);
    wrap.appendChild(pill);
  });
  wrap.appendChild(h('button', {
    class: 'student-pill ghost', text: '＋ 学生', onclick: () => editStudent(null),
  }));
  return wrap;
}

async function applyStudent(student) {
  const spec = ensureSpec();
  const changed = spec.grade !== student.grade
    || (spec.subjects ?? []).join() !== (student.subjects ?? []).join()
    || spec.term !== student.term;
  spec.studentName = student.name;
  spec.grade = student.grade;
  spec.term = student.term;
  if (student.subjects?.length) {
    spec.subjects = student.subjects;
    spec.subject = student.subjects[0];
  }
  if (changed) recomputeCounts();
  save();
  // 点名字 = 直接出卷
  const built = await generatePaper(spec);
  if (built) {
    toast(`已按 ${student.name} 的档案出卷：${Term.short(student.grade, student.term)}`);
    openPreview();
  } else {
    render();
  }
}

function templateRow() {
  const tpls = templatesFor(state.spec.grade, primarySubjects());
  const wrap = h('div', { class: 'template-row' });
  tpls.forEach((t) => {
    wrap.appendChild(h('button', {
      class: 'template-chip',
      onclick: () => {
        state.spec.counts = { ...t.wanted };
        state.spec.subtitle = `共 ${totalCount(t.wanted)} 题　满分 ${totalCount(t.wanted) * 2} 分`;
        save();
        render();
        toast(`已套用「${t.label}」`);
      },
    }, [h('b', { text: t.label }), h('span', { text: t.desc })]));
  });
  return wrap;
}

function paramCard() {
  const spec = state.spec;
  const gradeOptions = Array.from({ length: 9 }, (_, i) => ({ value: i + 1, label: GradeProfiles.gradeLabel(i + 1) }));

  const subjectChips = h('div', { class: 'chips' });
  SUBJECT_ALL.forEach((s) => {
    const usable = subjectAvailableFor(s, spec.grade);
    const on = (spec.subjects ?? []).includes(s);
    subjectChips.appendChild(h('button', {
      class: `chip${on ? ' on' : ''}${usable ? '' : ' off'}`,
      text: SUBJECT_LABEL[s],
      disabled: !usable,
      onclick: () => {
        let list = [...(spec.subjects ?? [])];
        list = list.includes(s) ? list.filter((x) => x !== s) : [...list, s];
        if (!list.length) return;
        spec.subjects = list;
        spec.subject = list[0];
        recomputeCounts();
        save();
        render();
      },
    }));
  });

  const termChips = h('div', { class: 'chips' });
  [[TERM.FIRST, '上册'], [TERM.SECOND, '下册'], [TERM.BOTH, '全学年']].forEach(([v, label]) => {
    termChips.appendChild(h('button', {
      class: `chip${spec.term === v ? ' on' : ''}`,
      text: label,
      onclick: () => {
        spec.term = v;
        recomputeCounts();
        save();
        render();
        toast(v === TERM.BOTH ? '已切到全学年' : `已切到${GradeProfiles.gradeLabel(spec.grade)}${Term.label(v)}`);
      },
    }));
  });

  const purposeChips = chips(PURPOSES.map((p) => ({ value: p.key, label: p.label })), spec.purpose, (v) => {
    spec.purpose = v;
    recomputeCounts();
    save();
    render();
  });

  const tiltChips = h('div', { class: 'chips' });
  TILTS.forEach((t) => {
    const fits = tiltFits(spec.grade, primarySubjects(), t.key);
    tiltChips.appendChild(h('button', {
      class: `chip${spec.tilt === t.key ? ' on' : ''}${fits ? '' : ' off'}`,
      text: t.label,
      title: fits ? t.hint : '这个年级 + 科目没有这类题，选了也不会变',
      disabled: !fits,
      onclick: () => {
        spec.tilt = t.key;
        recomputeCounts();
        save();
        render();
      },
    }));
  });

  return card('出什么卷',
    field('年级', select(gradeOptions, spec.grade, (v) => {
      spec.grade = Number(v);
      const keep = (spec.subjects ?? []).filter((s) => subjectAvailableFor(s, spec.grade));
      spec.subjects = keep.length ? keep : ['MATH'];
      spec.subject = spec.subjects[0];
      recomputeCounts();
      save();
      render();
    })),
    h('div', { class: 'field' }, [h('span', { text: '科目（可多选）' }), subjectChips]),
    h('div', { class: 'field' }, [h('span', { text: '学期' }), termChips]),
    h('div', { class: 'field' }, [h('span', { text: '卷面用途' }), purposeChips]),
    h('div', { class: 'field' }, [h('span', { text: '题型倾向' }), tiltChips]),
    h('p', { class: 'hint', text: '学期只影响真带上下册数据的课标单元；口算、字词这些按年级分段，选上/下册不会变少。' }),
  );
}

function typesCard() {
  const spec = state.spec;
  const types = availableTypes(spec.grade, primarySubjects());
  const list = h('div', { class: 'type-list' });

  types.forEach((t) => {
    const def = TYPE[t];
    const n = spec.counts[t] ?? 0;
    const item = h('div', { class: `type-item${n > 0 ? ' on' : ''}` });
    item.appendChild(h('label', { class: 'type-name' }, [
      h('input', {
        type: 'checkbox',
        checked: n > 0,
        onchange: (e) => {
          spec.counts[t] = e.target.checked ? Math.max(1, defaultCountFor(t)) : 0;
          save();
          render();
        },
      }),
      h('span', { text: def.label }),
      def.answerLines > 0 ? h('em', { text: `${def.columns} 栏` }) : null,
    ]));
    item.appendChild(h('input', {
      class: 'num',
      type: 'number',
      min: '0',
      max: '200',
      value: String(n),
      onchange: (e) => {
        spec.counts[t] = Math.max(0, Math.min(200, Number(e.target.value) || 0));
        save();
        render();
      },
    }));
    list.appendChild(item);
  });

  return card(`题型与题量（已选 ${totalCount(spec.counts)} 题）`,
    h('div', { class: 'type-tools' }, [
      button('全选常用', 'ghost', () => {
        spec.counts = {};
        types.slice(0, 3).forEach((t) => { spec.counts[t] = defaultCountFor(t); });
        save();
        render();
      }),
      button('清空', 'ghost', () => { spec.counts = {}; save(); render(); }),
    ]),
    list,
  );
}

function defaultCountFor(t) {
  const al = TYPE[t].answerLines;
  return al >= 3 ? 6 : al === 2 ? 8 : al === 1 ? 10 : 20;
}

function layoutCard() {
  const spec = state.spec;
  const footerMode = spec.footerText === '' ? 'off' : spec.footerText == null ? 'default' : 'custom';

  return card('卷面定制',
    field('标题', input('text', spec.title, (v) => { spec.title = v; save(); })),
    field('副标题', input('text', spec.subtitle, (v) => { spec.subtitle = v; save(); })),
    field('姓名栏', chips([{ value: 'on', label: '印姓名/班级/日期' }, { value: 'off', label: '不印' }],
      spec.showNameRow ? 'on' : 'off', (v) => { spec.showNameRow = v === 'on'; save(); render(); })),
    field('标题一行', chips([{ value: 'off', label: '两行' }, { value: 'on', label: '标题+题数一行' }],
      spec.headerOneLine ? 'on' : 'off', (v) => { spec.headerOneLine = v === 'on'; save(); render(); })),
    field('页脚', chips([
      { value: 'default', label: '默认那句' },
      { value: 'custom', label: '自定义' },
      { value: 'off', label: '整条不印' },
    ], footerMode, (v) => {
      spec.footerText = v === 'off' ? '' : v === 'default' ? null : (spec.footerText || '卷子 · 家里自己出的练习');
      save();
      render();
    })),
    footerMode === 'custom'
      ? field('页脚文字', input('text', spec.footerText ?? '', (v) => { spec.footerText = v; save(); }))
      : null,
    field('页脚高度', chips(FOOTER_SIZES.map((f) => ({ value: f.key, label: f.label })), spec.footerSize, (v) => {
      spec.footerSize = v; save(); render();
    })),
    field('题间行距', chips(LINE_GAPS.map((g) => ({ value: g.key, label: g.label })), spec.lineGap, (v) => {
      spec.lineGap = v; save(); render();
    })),
    spec.lineGap === 'CUSTOM'
      ? field('自定义行距(pt)', input('number', spec.lineGapCustom, (v) => { spec.lineGapCustom = Number(v) || 0; save(); }, { min: '0', max: '24', step: '0.5' }))
      : null,
    field('栏数', chips([1, 2, 3, 4].map((n) => ({ value: n, label: `${n} 栏` })), spec.columns, (v) => {
      spec.columns = Number(v); save(); render();
    })),
    field('页数上限', chips([0, 1, 2, 3, 4].map((n) => ({ value: n, label: n === 0 ? '不限' : `${n} 页` })), spec.targetPages, (v) => {
      spec.targetPages = Number(v); save(); render();
    })),
    field('答案', chips(ANSWER_MODES.map((a) => ({ value: a.key, label: a.label })), spec.answerMode, (v) => {
      spec.answerMode = v; save(); render();
    })),
    field('纸张', chips(ORIENTATIONS.map((o) => ({ value: o.key, label: o.label })), spec.orientation, (v) => {
      spec.orientation = v; save(); render();
    })),
    h('p', { class: 'hint', text: '页数上限是「最多印几页」：题多会整体缩小正文字号压进去，压不下就老实多印一页（印一张看不清的卷子更糟）。' }),
  );
}

function customCard() {
  const spec = state.spec;
  const list = h('div', { class: 'custom-list' });
  const mine = state.customSheets.filter((s) => !s.studentId || s.studentId === spec.studentName);
  if (!mine.length) list.appendChild(h('p', { class: 'hint', text: '还没有自编题。可以把自己的词表、算式、句子贴进来，让程序替你排版。' }));
  mine.forEach((sheet) => {
    const n = customValidCount(sheet);
    const on = (spec.customSheets ?? []).some((s) => s.id === sheet.id);
    list.appendChild(h('div', { class: `custom-item${on ? ' on' : ''}` }, [
      h('label', {}, [
        h('input', {
          type: 'checkbox', checked: on,
          onchange: (e) => {
            spec.customSheets = e.target.checked
              ? [...(spec.customSheets ?? []), sheet]
              : (spec.customSheets ?? []).filter((s) => s.id !== sheet.id);
            save();
            render();
          },
        }),
        h('b', { text: sheet.title || CUSTOM_MODES[sheet.mode].label }),
        h('span', { text: `${CUSTOM_MODES[sheet.mode].label} · 会印 ${n} 道` }),
      ]),
      h('div', { class: 'custom-actions' }, [
        button('编辑', 'ghost', () => editCustomSheet(sheet)),
        button('删', 'ghost danger', () => {
          state.customSheets = state.customSheets.filter((s) => s.id !== sheet.id);
          spec.customSheets = (spec.customSheets ?? []).filter((s) => s.id !== sheet.id);
          save();
          render();
        }),
      ]),
    ]));
  });
  return card('自编题', list, button('＋ 新建自编题', 'ghost', () => editCustomSheet(null)));
}

// ------------------------------------------------------------------ 页面：预览

function previewScreen() {
  if (!state.result) {
    return card('还没有卷子', h('p', { class: 'hint', text: '回到「出卷」页生成一张，或者从试卷库打开一张历史卷。' }));
  }
  // A4 一张纸 595pt ≈ 793px，手机屏幕装不下 —— 横版更宽。
  // 所以默认按「铺满可用宽度」显示，不然用户进来只看见左边一角，还以为卷子坏了。
  // 可用宽度从纸面容器实际量，不能拿 window.innerWidth 硬算（电脑端左侧还有 208px 导航栏）。
  const pageW = state.result.pages[0]?.pageW ?? 595;
  const view = h('div', { class: 'preview-view' });
  const pages = h('div', { class: 'paper-stage' });
  const fitZoom = () => {
    // 留 32px 呼吸 —— 纸面顶到窗口边缘看着像被裁掉了
    const avail = (pages.clientWidth || Math.max(320, window.innerWidth - 64)) - 32;
    // 上限 1.35：超宽屏上别把 595pt 的纸拉成一张巨幅海报
    return Math.min(1.35, Math.max(0.35, avail / (pageW * (96 / 72))));
  };
  const zoomNow = () => (state.zoomAuto ? fitZoom() : state.zoom);
  const zoomLabel = h('span', { class: 'zoom-label', text: `${Math.round(zoomNow() * 100)}%` });

  const toolbar = h('div', { class: 'preview-toolbar' }, [
    button('← 返回', 'ghost', () => { state.tab = 'create'; render(); }),
    h('span', { class: 'grow', text: describe(state.result) }),
    button('－', 'ghost', () => {
      state.zoomAuto = false;
      state.zoom = Math.max(0.4, Math.round((zoomNow() - 0.1) * 100) / 100);
      save();
      render();
    }),
    zoomLabel,
    button('＋', 'ghost', () => {
      state.zoomAuto = false;
      state.zoom = Math.min(2, Math.round((zoomNow() + 0.1) * 100) / 100);
      save();
      render();
    }),
    button('打印 / 存 PDF', '', () => printPaper(state.result)),
    button('换一套', 'ghost', async () => {
      state.spec.seed = Math.floor(Math.random() * 2147483647);
      save();
      const built = await generatePaper(state.spec);
      if (built) render();
    }),
    button('加一题', 'ghost', () => addQuestionDialog()),
    button('批改', 'ghost', () => gradingDialog()),
    button('存进试卷库', 'ghost', () => saveToLibrary()),
  ]);
  view.appendChild(toolbar);
  view.appendChild(pages);
  // 挂进文档后才量得到真实宽度：量完再画，并把百分比刷成实际用的值
  setTimeout(() => {
    const z = zoomNow();
    zoomLabel.textContent = `${Math.round(z * 100)}%`;
    renderPages(pages, state.result, z);
  }, 0);
  return view;
}

function addQuestionDialog() {
  const spec = state.spec;
  const types = availableTypes(spec.grade, primarySubjects());
  let chosen = types[0];
  const count = { n: 5 };
  const body = h('div', {}, [
    field('题型', select(types.map((t) => ({ value: t, label: TYPE[t].label })), chosen, (v) => { chosen = v; })),
    field('题量', input('number', 5, (v) => { count.n = Number(v) || 1; }, { min: '1', max: '40' })),
  ]);
  openModal({
    title: '加一道大题',
    body,
    actions: [
      { label: '取消', kind: 'ghost' },
      {
        label: '加上去',
        onClick: async () => {
          await appendSection(chosen, count.n);
        },
      },
    ],
  });
}

async function appendSection(type, count) {
  const spec = state.spec;
  const gen = generatorOf(type, spec.grade);
  if (!gen) { toast('这个年级没有这个题型'); return; }
  const paper = state.paper;
  const ctx = {
    grade: spec.grade, count, difficulty: spec.difficulty,
    rng: new Rng(Date.now() % 2147483647), blocked: new Set(),
    subjects: new Set(effectiveSubjects(spec)), topicIds: new Set(spec.topicIds ?? []),
    term: spec.term ?? 0, allowReuse: true,
  };
  const drawn = runGenerator(gen, ctx);
  if (!drawn.length) { toast('这个题型没抽到题'); return; }
  const startNo = paper.questions.length + 1;
  const qs = drawn.map((q, i) => ({ ...q, no: startNo + i }));
  const ordinal = paper.sections.length + 1;
  const t = TYPE[type];
  paper.sections.push({
    index: ordinal,
    title: sectionTitle(type, qs.length, ordinal),
    name: t.label,
    points: 2,
    type,
    questions: qs,
    columns: t.columns <= 1 ? 1 : t.answerLines > 0 ? t.columns : Math.max(t.columns, spec.columns),
    answerLines: t.answerLines,
    instruction: t.instruction ?? '',
    padWithLines: t.padWithLines !== false,
  });
  paper.questions = paper.sections.flatMap((s) => s.questions);
  refreshPreview();
  toast(`已加：${t.label} ${qs.length} 道`);
}

/** 卷子改了（加题 / 删题）→ 重新装配 + 重新排版 */
function refreshPreview() {
  const spec = state.spec;
  const paper = state.paper;
  paper.sections.forEach((s, i) => {
    s.index = i + 1;
    s.title = titleFor(s.name ?? TYPE[s.type].label, s.points ?? 2, s.questions.length, i + 1);
    s.questions.forEach((q, j) => { q.no = paper.sections.slice(0, i).reduce((a, x) => a + x.questions.length, 0) + j + 1; });
  });
  state.paper.questions = paper.sections.flatMap((s) => s.questions);
  state.result = renderPaper(assemble(spec, paper));
  save();
  render();
}

function gradingDialog() {
  const paper = state.paper;
  if (!paper) return;
  const wrong = new Set();
  const body = h('div', { class: 'grading-list' });
  paper.questions.forEach((q) => {
    body.appendChild(h('label', { class: 'grading-row' }, [
      h('input', {
        type: 'checkbox',
        onchange: (e) => { if (e.target.checked) wrong.add(q); else wrong.delete(q); },
      }),
      h('div', {}, [
        h('b', { text: `${q.no}. ${q.stem}` }),
        h('span', { class: 'answer', text: `答案：${q.answer}` }),
      ]),
    ]));
  });
  openModal({
    title: `批改：勾出错题（共 ${paper.questions.length} 道）`,
    body,
    wide: true,
    actions: [
      { label: '取消', kind: 'ghost' },
      {
        label: '记进错题本',
        onClick: () => {
          if (!wrong.size) { toast('没有勾选任何题'); return; }
          addMistakes([...wrong]);
        },
      },
    ],
  });
}

function addMistakes(list) {
  const seen = new Set(state.mistakes.map((m) => m.hash));
  let added = 0;
  list.forEach((q) => {
    const hash = stableHash(q.stem);
    if (seen.has(hash)) return;
    seen.add(hash);
    state.mistakes.unshift({
      hash, stem: q.stem, answer: q.answer, topic: q.topic ?? '', type: q.type,
      date: new Date().toISOString().slice(0, 10),
    });
    added++;
  });
  save();
  toast(added ? `已记 ${added} 道错题（重复的没重复记）` : '这些题之前就记过了');
}

function saveToLibrary() {
  const paper = state.paper;
  if (!paper) return;
  state.history.unshift({
    id: `p_${Date.now().toString(36)}`,
    title: state.spec.title,
    spec: { ...state.spec },
    createdAt: paper.createdAt,
    count: paper.questions.length,
    pages: state.result?.pageCount ?? 0,
    sections: paper.sections,
    questions: paper.questions,
  });
  state.history = state.history.slice(0, 40);
  save();
  toast('已存进试卷库');
}

// ------------------------------------------------------------------ 页面：错题本 / 试卷库 / 学生

function mistakesScreen() {
  const wrap = h('div', { class: 'page' });
  wrap.appendChild(h('div', { class: 'page-head' }, [
    h('h1', { text: '错题本' }),
    h('p', { class: 'hint', text: `共 ${state.mistakes.length} 道 · 同一个题干只记一次` }),
  ]));
  wrap.appendChild(h('div', { class: 'row' }, [
    button('用错题出卷', '', async () => {
      if (!state.mistakes.length) { toast('错题本是空的'); return; }
      state.spec = defaultSpec(state.spec?.grade ?? 3, primarySubjects(), 'BALANCED', 'DAILY', state.spec?.term ?? 1);
      state.spec.isMistakePaper = true;
      const questions = state.mistakes.slice(0, 30).map((m, i) => ({
        no: i + 1, type: m.type ?? 'CUSTOM', stem: m.stem, answer: m.answer, topic: m.topic, figure: null,
      }));
      const paper = {
        spec: state.spec,
        sections: [{
          index: 1, title: '一、错题重练（每题 5 分）', name: '错题重练', points: 5,
          type: 'CUSTOM', questions, columns: 1, answerLines: 2, instruction: '把上次做错的题再做一遍。', padWithLines: true,
        }],
        questions,
        createdAt: Date.now(),
      };
      state.paper = paper;
      state.result = renderPaper(assemble(state.spec, paper));
      state.tab = 'preview';
      render();
    }),
    button('清空错题本', 'ghost danger', () => {
      confirmDialog('清空错题本', '所有错题记录都会被删除，无法恢复。', () => {
        state.mistakes = [];
        save();
        render();
      }, '清空');
    }),
  ]));
  const list = h('div', { class: 'mistake-list' });
  if (!state.mistakes.length) list.appendChild(h('p', { class: 'hint', text: '还没有错题。在预览页点「批改」，勾出错题就会记到这里。' }));
  state.mistakes.forEach((m) => {
    list.appendChild(h('div', { class: 'mistake-item' }, [
      h('b', { text: m.stem }),
      h('span', { class: 'answer', text: `答案：${m.answer}` }),
      h('span', { class: 'meta', text: `${m.date}　${m.topic || ''}` }),
      button('删', 'ghost danger', () => {
        state.mistakes = state.mistakes.filter((x) => x.hash !== m.hash);
        save();
        render();
      }),
    ]));
  });
  wrap.appendChild(list);
  return wrap;
}

function libraryScreen() {
  const wrap = h('div', { class: 'page' });
  wrap.appendChild(h('div', { class: 'page-head' }, [
    h('h1', { text: '试卷库' }),
    h('p', { class: 'hint', text: `本地保存了 ${state.history.length} 张卷子（存在这台设备的浏览器里）` }),
  ]));
  const list = h('div', { class: 'lib-list' });
  if (!state.history.length) list.appendChild(h('p', { class: 'hint', text: '还没有存过卷子。预览页点「存进试卷库」就会出现在这里。' }));
  state.history.forEach((item) => {
    list.appendChild(h('div', { class: 'lib-item' }, [
      h('div', {}, [
        h('b', { text: item.title }),
        h('span', { class: 'meta', text: `${new Date(item.createdAt).toLocaleString('zh-CN')} · ${item.count} 题 · ${item.pages} 页` }),
      ]),
      h('div', { class: 'custom-actions' }, [
        button('打开', 'ghost', () => {
          state.spec = item.spec;
          state.paper = { spec: item.spec, sections: item.sections, questions: item.questions, createdAt: item.createdAt };
          state.result = renderPaper(assemble(item.spec, state.paper));
          state.tab = 'preview';
          render();
        }),
        button('删', 'ghost danger', () => {
          state.history = state.history.filter((x) => x.id !== item.id);
          save();
          render();
        }),
      ]),
    ]));
  });
  wrap.appendChild(list);
  return wrap;
}

function studentsScreen() {
  const wrap = h('div', { class: 'page' });
  wrap.appendChild(h('div', { class: 'page-head' }, [
    h('h1', { text: '学生档案' }),
    h('p', { class: 'hint', text: '姓名 + 年级 + 学期 + 科目。点名字就按他的档案出卷，卷头直接印名字。' }),
  ]));
  wrap.appendChild(button('＋ 新建学生', '', () => editStudent(null)));
  const list = h('div', { class: 'student-list' });
  if (!state.students.length) list.appendChild(h('p', { class: 'hint', text: '还没有学生档案。' }));
  state.students.forEach((s) => {
    list.appendChild(h('div', { class: 'student-item' }, [
      h('div', {}, [
        h('b', { text: s.name }),
        h('span', { class: 'meta', text: `${GradeProfiles.gradeLabel(s.grade)} · ${Term.label(s.term)} · ${(s.subjects ?? []).map((x) => SUBJECT_LABEL[x]).join('/')}` }),
      ]),
      h('div', { class: 'custom-actions' }, [
        button('出卷', 'ghost', () => applyStudent(s)),
        button('编辑', 'ghost', () => editStudent(s)),
        button('删', 'ghost danger', () => {
          confirmDialog('删除学生', `确定删除「${s.name}」的档案？`, () => {
            state.students = state.students.filter((x) => x.id !== s.id);
            save();
            render();
          }, '删除');
        }),
      ]),
    ]));
  });
  wrap.appendChild(list);
  return wrap;
}

function editStudent(student) {
  const draft = student
    ? { ...student }
    : { id: `s_${Date.now().toString(36)}`, name: '', grade: 3, term: TERM.FIRST, gender: '', subjects: ['MATH'], note: '' };
  const body = h('div', {}, [
    field('姓名', input('text', draft.name, (v) => { draft.name = v; })),
    field('年级', select(Array.from({ length: 9 }, (_, i) => ({ value: i + 1, label: GradeProfiles.gradeLabel(i + 1) })), draft.grade, (v) => { draft.grade = Number(v); })),
    field('学期', chips([{ value: TERM.FIRST, label: '上册' }, { value: TERM.SECOND, label: '下册' }, { value: TERM.BOTH, label: '全学年' }], draft.term, (v) => { draft.term = Number(v); })),
    field('性别', chips([{ value: '', label: '不填' }, { value: '男', label: '男' }, { value: '女', label: '女' }], draft.gender, (v) => { draft.gender = v; })),
    h('div', { class: 'field' }, [
      h('span', { text: '科目' }),
      chips(SUBJECT_ALL.filter((s) => subjectAvailableFor(s, draft.grade)).map((s) => ({ value: s, label: SUBJECT_LABEL[s] })),
        draft.subjects[0], (v) => { draft.subjects = [v]; }),
    ]),
  ]);
  openModal({
    title: student ? '编辑学生' : '新建学生',
    body,
    actions: [
      { label: '取消', kind: 'ghost' },
      {
        label: '保存',
        onClick: () => {
          if (!draft.name.trim()) { toast('姓名不能为空'); return; }
          const i = state.students.findIndex((s) => s.id === draft.id);
          if (i >= 0) state.students[i] = draft; else state.students.push(draft);
          save();
          render();
          toast('已保存');
        },
      },
    ],
  });
}

// ------------------------------------------------------------------ 页面：自编题编辑

function editCustomSheet(sheet) {
  const draft = sheet ? JSON.parse(JSON.stringify(sheet)) : newCustomSheet();
  const preview = h('p', { class: 'hint' });
  const updatePreview = () => {
    const built = buildCustomSheet(draft, new Rng(0));
    const dup = built.duplicates;
    preview.textContent = `会印 ${built.questions.length} 道`
      + (dup > 0 ? `（去掉 ${dup} 条重复）` : '')
      + (draft.count > 0 ? ` · 只用前 ${draft.count} 条` : '');
  };
  const modeChips = h('div', { class: 'chips' });
  Object.values(CUSTOM_MODES).forEach((m) => {
    modeChips.appendChild(h('button', {
      class: `chip${draft.mode === m.key ? ' on' : ''}`,
      text: m.label,
      onclick: (e) => {
        draft.mode = m.key;
        draft.answerLines = m.answerLines;
        [...modeChips.children].forEach((c) => c.classList.remove('on'));
        e.target.classList.add('on');
        updatePreview();
      },
    }));
  });

  const body = h('div', {}, [
    field('大题名', input('text', draft.title, (v) => { draft.title = v; })),
    h('div', { class: 'field' }, [h('span', { text: '模式' }), modeChips]),
    field('内容（一行一道）', textarea(draft.raw, (v) => { draft.raw = v; updatePreview(); }, 8)),
    h('p', { class: 'hint', text: CUSTOM_MODES[draft.mode].hint }),
    field('栏数', chips([1, 2, 3, 4].map((n) => ({ value: n, label: `${n} 栏` })), draft.columns, (v) => { draft.columns = Number(v); })),
    field('每题答题线', chips([0, 1, 2, 3].map((n) => ({ value: n, label: `${n} 条` })), draft.answerLines, (v) => { draft.answerLines = Number(v); })),
    field('每题分值', input('number', draft.points, (v) => { draft.points = Number(v) || 2; }, { min: '1', max: '20' })),
    field('打乱顺序', chips([{ value: 'on', label: '打乱' }, { value: 'off', label: '按原文顺序' }], draft.shuffle ? 'on' : 'off', (v) => { draft.shuffle = v === 'on'; })),
    field('只用前 N 条', input('number', draft.count, (v) => { draft.count = Number(v) || 0; updatePreview(); }, { min: '0' })),
    preview,
  ]);
  updatePreview();

  openModal({
    title: sheet ? '编辑自编题' : '新建自编题',
    body,
    wide: true,
    actions: [
      { label: '取消', kind: 'ghost' },
      {
        label: '保存',
        onClick: () => {
          if (!draft.raw.trim()) { toast('内容不能为空'); return; }
          const i = state.customSheets.findIndex((s) => s.id === draft.id);
          if (i >= 0) state.customSheets[i] = draft; else state.customSheets.push(draft);
          save();
          render();
        },
      },
    ],
  });
}

// ------------------------------------------------------------------ 页面：课本

function textbookScreen() {
  const spec = ensureSpec();
  const subject = primarySubjects()[0];
  const grade = spec.grade;
  const wrap = h('div', { class: 'page page-cards' });
  wrap.appendChild(h('div', { class: 'page-head' }, [
    h('h1', { text: '课本' }),
    h('p', { class: 'hint', text: '按课标单元出卷：点一个单元，只从这套知识点里抽题。教材原文不收录，只给链接。' }),
  ]));

  wrap.appendChild(h('div', { class: 'row' }, [
    select(SUBJECT_ALL.filter((s) => subjectAvailableFor(s, grade)).map((s) => ({ value: s, label: SUBJECT_LABEL[s] })), subject, (v) => {
      spec.subjects = [v];
      spec.subject = v;
      recomputeCounts();
      save();
      render();
    }),
    select(Array.from({ length: 9 }, (_, i) => ({ value: i + 1, label: GradeProfiles.gradeLabel(i + 1) })), grade, (v) => {
      spec.grade = Number(v);
      recomputeCounts();
      save();
      render();
    }),
  ]));

  const unitsBox = h('div', { class: 'unit-list' }, [h('p', { class: 'hint', text: '正在读取课标单元…' })]);
  wrap.appendChild(card('教材单元（按课标知识点聚合）', unitsBox));

  if (!['MATH', 'CHINESE', 'ENGLISH', 'SCIENCE', 'PHYSICS', 'CHEMISTRY'].includes(subject)) {
    unitsBox.textContent = '这个科目没有课标单元';
  } else {
    loadCurriculumFor([subject]).then(() => {
      const units = curricUnits(subject, grade);
      unitsBox.innerHTML = '';
      if (!units.length) {
        unitsBox.appendChild(h('p', { class: 'hint', text: '这个年级在课标题库里没有单元（科学 / 语文低年级常见）。' }));
        return;
      }
      units.forEach((u) => {
        const blank = u.topics.length;
        unitsBox.appendChild(h('div', { class: 'unit-item' }, [
          h('div', {}, [
            h('b', { text: u.name }),
            h('span', { class: 'meta', text: `${u.semester || '全'}册 · ${blank} 个知识点` }),
          ]),
          button('按此单元出卷', 'ghost', async () => {
            const ids = u.topics.map((t) => t.id);
            spec.topicIds = ids;
            spec.subjects = [subject];
            spec.subject = subject;
            spec.counts = { CURRIC_BLANK: 10, CURRIC_CHOICE: 10 };
            await loadCurriculumFor([subject]);
            const built = await generatePaper(spec);
            if (built) openPreview();
          }),
        ]));
      });
    }).catch((err) => {
      unitsBox.textContent = `读取失败：${err.message}`;
    });
  }

  // 生字 / 单词清单：抽测用
  const tb = DATA.content?.textbook;
  if (tb) {
    const hanzi = tb.hanzi.filter((x) => x.grade === grade);
    const words = tb.words.filter((x) => x.grade === grade);
    wrap.appendChild(card('这个年级的字词清单',
      h('p', { class: 'hint', text: `生字 ${hanzi.length} 个 · 英语核心词 ${words.length} 个（抽测题型「课本生字注音 / 课本单词默写」用的就是它们）` }),
      h('div', { class: 'word-cloud' }, hanzi.slice(0, 40).map((x) => h('span', { class: 'word', text: `${x.hanzi} ${x.pinyin}` }))),
    ));
  }

  const books = DATA.catalog.filter((b) => b.g === grade);
  if (books.length) {
    wrap.appendChild(card('教材链接（只跳转，不分发内容）',
      h('div', { class: 'book-list' }, books.slice(0, 12).map((b) => h('a', {
        class: 'book', href: b.u, target: '_blank', rel: 'noreferrer', text: `${b.n}（${b.p}）`,
      }))),
    ));
  }
  return wrap;
}

// ------------------------------------------------------------------ 页面：打印纸

function sheetsScreen() {
  const wrap = h('div', { class: 'page' });
  wrap.appendChild(h('div', { class: 'page-head' }, [
    h('h1', { text: '打印纸' }),
    h('p', { class: 'hint', text: '速查表与练习格纸，点开就能打印。内容全在本机，不联网。' }),
  ]));
  const tabs = h('div', { class: 'chips' }, [
    h('button', {
      class: `chip${state.sheetTab === 'ref' ? ' on' : ''}`, text: '速查表',
      onclick: () => { state.sheetTab = 'ref'; render(); },
    }),
    h('button', {
      class: `chip${state.sheetTab === 'practice' ? ' on' : ''}`, text: '练习格纸',
      onclick: () => { state.sheetTab = 'practice'; render(); },
    }),
  ]);
  wrap.appendChild(tabs);

  const grid = h('div', { class: 'sheet-grid' });
  const refs = DATA.content?.references ?? [];
  const practical = DATA.content?.practice ?? [];
  const items = state.sheetTab === 'ref' ? refs : practical;
  items.forEach((item) => {
    grid.appendChild(h('button', {
      class: 'sheet-card',
      onclick: () => previewSheet(item, state.sheetTab),
    }, [
      h('b', { text: item.title }),
      h('span', { text: item.subtitle ?? '' }),
      h('em', { text: item.group ?? '' }),
    ]));
  });
  wrap.appendChild(grid);
  return wrap;
}

function previewSheet(item, kind) {
  const node = kind === 'ref' ? referenceSheet(item) : practiceSheet(item);
  const holder = h('div', { class: 'sheet-preview' });
  // 电脑端弹窗宽，纸面给它放大一点，别让一张 A4 缩成邮票
  const scale = window.innerWidth >= 900 ? 0.8 : 0.62;
  node.style.transform = `scale(${scale})`;
  node.style.transformOrigin = 'top left';
  holder.appendChild(node);
  holder.setAttribute('style', `width:${595 * scale}pt;height:${842 * scale}pt;overflow:hidden;position:relative;`);
  openModal({
    title: item.title,
    body: holder,
    wide: true,
    actions: [
      { label: '关闭', kind: 'ghost' },
      {
        label: '打印这一张',
        onClick: () => {
          printNodes([kind === 'ref' ? referenceSheet(item) : practiceSheet(item)], item.orientation ?? 'PORTRAIT');
        },
      },
    ],
  });
}

// ------------------------------------------------------------------ 页面：更多

function moreScreen() {
  const wrap = h('div', { class: 'page page-cards' });
  wrap.appendChild(h('div', { class: 'page-head' }, [
    h('h1', { text: '更多' }),
    h('p', { class: 'hint', text: '数据统计 · 打印帮助 · 关于' }),
  ]));

  const stats = DATA.stats?.subjects ?? {};
  const curricTotal = Object.values(stats).reduce((a, s) => a + (s.questions ?? 0), 0);
  wrap.appendChild(card('内置内容',
    h('p', { class: 'hint', text: '数字实时统计，随数据文件自动更新，不是写死的。' }),
    h('div', { class: 'stat-grid' }, [
      { v: generatorCount, l: '出卷题型' },
      { v: (DATA.bank?.length ?? 0), l: '自编题库（道）' },
      { v: curricTotal, l: '课标题目（道）' },
      { v: (DATA.science?.length ?? 0), l: '小学科学（道）' },
      { v: DATA.content?.phonics?.all?.length ?? 0, l: '英语音标（个）' },
      { v: DATA.content?.grammar?.points?.length ?? 0, l: '英语语法点' },
      { v: DATA.content?.idioms?.length ?? 0, l: '成语（条）' },
      { v: DATA.content?.poems?.length ?? 0, l: '古诗（首）' },
      { v: (DATA.content?.textbook?.hanzi?.length ?? 0), l: '课本生字' },
      { v: (DATA.content?.references?.length ?? 0) + (DATA.content?.practice?.length ?? 0), l: '可打印纸张' },
    ].map((s) => h('div', { class: 'stat' }, [h('b', { text: String(s.v) }), h('span', { text: s.l })]))),
  ));

  wrap.appendChild(card('打印帮助',
    h('ol', { class: 'help-list' }, [
      h('li', { text: '点「打印 / 存 PDF」→ 目标选「另存为 PDF」或直接选打印机。' }),
      h('li', { text: '缩放必须选「实际大小 / 100%」，不要用「适合页面」，否则 595pt 的纸会被再缩一次。' }),
      h('li', { text: '纸张选 A4；横版卷子记得在打印对话框里也切横版。' }),
      h('li', { text: '页边距选「无 / 默认」都行，本程序已经留了 40pt（约 14mm）的安全边距。' }),
      h('li', { text: '想给孩子反复用：打印后套进透明文件袋，用白板笔写，擦了能重写。' }),
    ]),
  ));

  wrap.appendChild(card('关于',
    h('p', { class: 'hint', text: '网页版与安卓 App 同一套出题与排版逻辑，数据存在这台设备的浏览器里，不上传任何内容。' }),
    h('p', { class: 'hint', html: `课标题库来自 <a href="${CURRIC_META.source}" target="_blank" rel="noreferrer">cn-math-taxonomy</a>，许可 ${CURRIC_META.license}（署名—非商业性使用）；一旦商业化必须下线该数据与相关题型。` }),
    h('p', { class: 'hint', text: '成语 / 古诗 / 音标 / 语法 / 生字词全部自编或取自语言事实，不收录任何教材原文。' }),
    h('div', { class: 'row' }, [
      button('学生档案', 'ghost', () => { state.tab = 'students'; render(); }),
      button('清空本地数据', 'ghost danger', () => {
        confirmDialog('清空本地数据', '会删掉试卷库、错题本、学生档案和自编题（设置会保留）。', () => {
          state.history = [];
          state.mistakes = [];
          state.students = [];
          state.customSheets = [];
          save();
          render();
        }, '清空');
      }),
    ]),
  ));
  return wrap;
}

// ------------------------------------------------------------------ 路由与外壳

const TABS = [
  { key: 'create', label: '出卷' },
  { key: 'sheets', label: '打印纸' },
  { key: 'mistakes', label: '错题本' },
  { key: 'library', label: '试卷库' },
  { key: 'textbook', label: '课本' },
  { key: 'more', label: '更多' },
];

function navBar() {
  const nav = h('nav', { class: 'dock' });
  // 电脑端侧栏才显示的小标题（手机端隐藏）
  nav.appendChild(h('div', { class: 'dock-title', text: '工作台' }));
  TABS.forEach((t) => {
    nav.appendChild(h('button', {
      class: `dock-btn${state.tab === t.key || (t.key === 'create' && state.tab === 'preview') ? ' on' : ''}`,
      text: t.label,
      onclick: () => { state.tab = t.key; save(); render(); },
    }));
  });
  return nav;
}

function render() {
  const view = $('#view');
  view.innerHTML = '';
  view.className = state.tab === 'create' ? 'tab-create' : '';
  const spec = ensureSpec();

  switch (state.tab) {
    case 'preview':
      view.appendChild(previewScreen());
      break;
    case 'mistakes':
      view.appendChild(mistakesScreen());
      break;
    case 'library':
      view.appendChild(libraryScreen());
      break;
    case 'textbook':
      view.appendChild(textbookScreen());
      break;
    case 'sheets':
      view.appendChild(sheetsScreen());
      break;
    case 'more':
      view.appendChild(moreScreen());
      break;
    case 'students':
      view.appendChild(studentsScreen());
      break;
    default: {
      view.appendChild(h('div', { class: 'page-head' }, [
        h('h1', { text: '出卷' }),
        h('p', { class: 'hint', text: `${GradeProfiles.gradeLabel(spec.grade)} · ${primarySubjects().map((s) => SUBJECT_LABEL[s]).join(' + ')} · ${Term.label(spec.term)} · 已选 ${totalCount(spec.counts)} 题` }),
      ]));
      view.appendChild(studentStrip());
      view.appendChild(templateRow());
      // 电脑端两列并排：左「出什么卷 + 题型」，右「自编题 + 卷面定制」；
      // 手机端 .create-cols 退化成块级，还是原来那样竖着堆。
      view.appendChild(h('div', { class: 'create-cols' }, [
        h('div', { class: 'col' }, [paramCard(), typesCard()]),
        h('div', { class: 'col' }, [customCard(), layoutCard()]),
      ]));
      view.appendChild(h('div', { class: 'create-actions' }, [
        button(`生成卷子（${totalCount(spec.counts)} 题）`, 'primary', async () => {
          if (!totalCount(spec.counts)) { toast('先勾几个题型'); return; }
          const built = await generatePaper(spec);
          if (built) {
            toast(describe(built.result));
            openPreview();
          }
        }),
      ]));
    }
  }

  const dock = $('#dock-holder');
  dock.innerHTML = '';
  dock.appendChild(navBar());
}

// ------------------------------------------------------------------ 启动

async function boot() {
  try {
    await loadCoreData();
  } catch (err) {
    document.getElementById('view').innerHTML = `<div class="card"><h2>数据没加载上</h2>
      <p class="hint">请用一个本地 http 服务打开这个目录（直接双击 index.html 读不到 data/*.json）。
      例如在 web 目录里执行 <code>python3 -m http.server 8778</code>，再访问 http://127.0.0.1:8778/</p>
      <p class="hint">${err.message}</p></div>`;
    return;
  }
  load();
  ensureSpec();
  if (state.tab === 'preview') state.tab = 'create';
  // 支持 #preview / #sheets 这类深链：刷新后还停在原来的页面
  const hash = (location.hash || '').replace('#', '');
  if (['preview', 'sheets', 'mistakes', 'library', 'textbook', 'more', 'students'].includes(hash)) {
    state.tab = hash;
  }
  render();

  // 首次进来先给一张默认卷子垫着，免得点了「保存/打印」发现是空的
  if (!state.result) {
    const built = await generatePaper(state.spec);
    if (built) render();
  }
}

// 窗口尺寸变了（拖动窗口 / 转屏 / 收起侧栏）：预览页重新按宽度铺满纸面
let resizeTimer = null;
window.addEventListener('resize', () => {
  if (state.tab !== 'preview') return;
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => { if (state.tab === 'preview') render(); }, 180);
});

boot();
