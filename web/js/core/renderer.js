/**
 * 试卷渲染器 —— 对应 App 的 core/pdf/render/PaperRenderer.kt。
 *
 * 两阶段：
 *  阶段一：所有块交给 FlowLayout 算出「哪一页、哪一栏、哪个 y」；
 *  阶段二：知道总页数之后，逐页画卷头 / 页眉 / 块 / 页脚（页脚要写「第 2/4 页」）。
 *
 * 中间那几个「试排」不是过度设计，每一个都对应一次真实的排版事故：
 *  - 单页速查表铺满：九九乘法表只占上半张，打印出来下半张全白；
 *  - 页数上限缩字号：用户设「最多 2 页」，题多只能整体缩；
 *  - 余量转答题线：纸尾空 200pt 不如多给两条书写线；
 *  - 按高度挑栏数：15 道单词拼装只占半张纸；
 *  - 撑开题间距：二分到「不增页的最大间距」，用**最差那一页**评分。
 */

import { TP } from './measure.js';
import { questionBlock, sectionHeader, noteBlock, tableBlock, fullWidth, figureLayout } from './blocks.js';
import { FlowLayout } from './flow.js';
import { LINE_GAPS, FOOTER_SIZES, ORIENTATIONS } from './models.js';

const COLUMN_GAP = 20;
const ANSWER_GAP = 4;
const MIN_ANSWER_COL_W = 108;
const MAX_ANSWER_GAP = 26;
const MAX_BODY_COLUMNS = 6;
const BODY_SIZE = 11;
const SECTION_SIZE = 11.5;
const NOTE_SIZE = 9;
const MIN_TYPE_SCALE = 0.72;
const ANSWER_MIN_FILL = 0.86;
const BODY_FILL_TARGET = 0.96;

const TITLE_SIZE = 17;
const TITLE_H = 26;
const SUB_SIZE = 9;
const SUB_H = 17;
const NAME_ROW_H = 26;
const COVER_RULE_H = 10;
const RUNNING_H = 24;

const COLOR_INK = '#1a1a1a';
const COLOR_GRAY = '#555';
const COLOR_LIGHT = '#999';

/** 一行文字的基线估算：让卷头/页脚这些「按基线对齐」的元素落得准 */
const baselineOffset = (size, boxH) => (boxH - size) / 2 + size * 0.86;
const atBaseline = (x, baseline, str, size, opts = {}) => ({
  k: 'text', x, y: baseline - size * 0.86, text: str, size,
  bold: opts.bold ?? false, mono: false,
  color: opts.color ?? COLOR_INK, h: size * 1.2,
});

export function geometryOf(orientation) {
  const o = ORIENTATIONS.find((x) => x.key === orientation) ?? ORIENTATIONS[0];
  return { pageW: o.w, pageH: o.h, margin: 40, contentW: o.w - 80, contentBottom: o.h - 40 };
}

function fillColumns(widths, contentW, gap, floor, ceiling, strict = false) {
  if (!widths.length) return floor;
  const sorted = [...widths].sort((a, b) => a - b);
  const target = strict ? sorted[sorted.length - 1] : sorted[Math.floor((sorted.length - 1) * 0.8)];
  const need = Math.max(target, 48) + 8;
  let best = Math.min(Math.max(floor, 1), ceiling);
  for (let c = best; c <= ceiling; c++) {
    const colW = (contentW - gap * (c - 1)) / c;
    if (colW >= need) best = c; else break;
  }
  return best;
}

/**
 * 业务卷子 → 渲染文档（对应 PaperAssembler）。
 * 卷头题数用「实际生成出来的题数」：某些题型受去重/题库容量限制会少给。
 */
export function assemble(spec, paper) {
  const sections = paper.sections.map((s) => ({
    title: s.title,
    questions: s.questions.map((q) => ({ no: q.no, stem: q.stem, figure: q.figure ?? null })),
    columns: s.columns,
    answerLines: s.answerLines,
    instruction: s.instruction,
    padWithLines: s.padWithLines !== false,
    isAnswer: false,
  }));

  if (spec.answerMode === 'LAST_PAGE' && paper.questions.length) {
    sections.push({
      title: '参考答案（家长核对用）',
      questions: paper.questions.map((q) => ({ no: q.no, stem: q.answer, figure: null })),
      columns: 4,
      answerLines: 0,
      instruction: '',
      padWithLines: true,
      isAnswer: true,
    });
  }

  const actual = paper.questions.length;
  const subtitle = typeof spec.subtitle === 'string' && spec.subtitle.startsWith('共 ') && spec.subtitle.includes('题')
    ? `共 ${actual} 题　满分 ${actual * 2} 分`
    : spec.subtitle;

  const footerOff = spec.footerText === '';
  return {
    title: spec.isMistakePaper ? `${spec.title}（错题重练）` : spec.title,
    subtitle,
    subtitleInline: !!spec.headerOneLine,
    showNameRow: spec.showNameRow !== false,
    studentName: spec.studentName ?? '',
    sections,
    footerText: spec.footerText ?? '卷子 · 题目由本程序生成，非官方教材配套',
    footerStamp: spec.showCreatedAt && !footerOff ? `生成 ${stamp(paper.createdAt)}` : '',
    showPageNo: spec.showPageNo !== false && !footerOff,
    footerSize: spec.footerSize ?? 'NORMAL',
    lineGap: spec.lineGap ?? 'NORMAL',
    lineGapCustom: spec.lineGapCustom ?? 8,
    orientation: spec.orientation ?? 'PORTRAIT',
    targetPages: spec.targetPages ?? 0,
    createdAt: paper.createdAt,
  };
}

function stamp(ts) {
  const d = new Date(ts ?? Date.now());
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

function gapOf(doc) {
  const g = LINE_GAPS.find((x) => x.key === doc.lineGap) ?? LINE_GAPS[2];
  return g.key === 'CUSTOM'
    ? { base: doc.lineGapCustom, max: doc.lineGapCustom }
    : { base: g.base, max: g.max };
}

/** 卷头占用高度（不含页边距） */
function coverHeight(doc) {
  let h = TITLE_H;
  if (doc.subtitle && !doc.subtitleInline) h += SUB_H;
  if (doc.showNameRow) h += NAME_ROW_H;
  return h + COVER_RULE_H;
}

/** 主渲染：返回可直接交给 DOM 的页面数组 */
export function renderPaper(doc, tp = TP) {
  const g = geometryOf(doc.orientation);
  const gap = gapOf(doc);
  const footerSize = FOOTER_SIZES.find((x) => x.key === doc.footerSize) ?? FOOTER_SIZES[1];
  const footerH = doc.hasFooter === false ? 0 : footerSize.height;
  const bottomSafe = g.contentBottom - footerH;
  const contentHFirst = bottomSafe - (g.margin + coverHeight(doc));
  const contentHOther = bottomSafe - (g.margin + RUNNING_H);

  let bodyColumns = 2;

  /** 正文块；typeScale 只缩正文（标题/说明/题号/题干），卷头与页脚不缩 */
  function buildBody({ scale = 1, itemGap = gap.base, extraLines = 0, forceColumns = 0, typeScale = 1 } = {}) {
    const blocks = [];
    let firstHint = 0;
    doc.sections.filter((s) => !s.isAnswer).forEach((section) => {
      const forceFullWidth = section.columns <= 1;
      let hint = 0;
      if (!forceFullWidth && section.questions.length) {
        if (forceColumns > 0) {
          hint = section.answerLines > 0 ? section.columns : forceColumns;
        } else {
          hint = fillColumns(
            section.questions.map((q) => questionBlock({ no: q.no, stem: q.stem, size: BODY_SIZE * typeScale }).singleLineWidth(tp)),
            g.contentW, COLUMN_GAP, Math.max(1, section.columns),
            section.answerLines > 0 ? Math.min(Math.max(section.columns, 2), MAX_BODY_COLUMNS) : MAX_BODY_COLUMNS,
          );
        }
      }
      if (firstHint === 0 && hint > 0) firstHint = hint;

      blocks.push(sectionHeader(section.title, SECTION_SIZE * typeScale, hint));
      if (section.instruction && section.instruction.trim()) {
        blocks.push(noteBlock(section.instruction, NOTE_SIZE * typeScale));
      }
      section.questions.forEach((q) => {
        const lines = section.answerLines > 0
          ? section.answerLines + (section.padWithLines ? extraLines : 0)
          : 0;
        const qb = questionBlock({
          no: q.no, stem: q.stem, answerLines: lines,
          size: BODY_SIZE * typeScale, itemGap, figure: q.figure,
        });
        blocks.push((forceFullWidth || q.figure) ? fullWidth(qb) : qb);
      });
      bodyColumns = Math.max(bodyColumns, section.columns);
    });
    if (firstHint > 0) bodyColumns = forceColumns > 0 ? forceColumns : firstHint;
    return blocks;
  }

  function layoutBody(blocks) {
    if (!blocks.length) return [];
    return new FlowLayout({
      contentW: g.contentW, contentHFirst, contentHOther,
      columns: bodyColumns, columnGap: COLUMN_GAP, startsOnFirstPage: true,
    }).layout(blocks, tp);
  }

  const pageBottom = (page) => page.items.reduce((m, p) => Math.max(m, p.y + p.block.measure(tp, p.w)), 0);
  const bodyFillOf = (list) => {
    if (!list.length) return 1;
    const cap = list.length === 1 ? contentHFirst : contentHOther;
    return cap <= 0 ? 1 : Math.min(1, pageBottom(list[list.length - 1]) / cap);
  };
  const worstFillOf = (list) => {
    if (!list.length) return 1;
    let worst = 1;
    list.forEach((page, i) => {
      const cap = i === 0 ? contentHFirst : contentHOther;
      if (cap > 0) worst = Math.min(worst, Math.min(1, pageBottom(page) / cap));
    });
    return worst;
  };

  let pages = layoutBody(buildBody({}));

  // ---- 单页速查表铺满整页（乘法表 / 语法表只占上半张的问题） ----
  const bodySections = doc.sections.filter((s) => !s.isAnswer);
  if (bodySections.length === 1 && bodySections[0].table && pages.length === 1) {
    const used = pageBottom(pages[0]);
    if (used > 0 && used < contentHFirst * 0.74) {
      const scale = Math.min(3.2, Math.max(1, (contentHFirst * 0.93) / used));
      const taller = layoutBody(buildBody({ scale }));
      if (taller.length === 1) pages = taller;
    }
  }

  // ---- 页数上限：整体缩字号，二分 6 次 ----
  // 注意「最多印几页」指的是**印出来**的页数，答案页是在后面单独追加的，
  // 所以正文的预算要先把答案页那一页扣掉。不扣就会出现「限 2 页却印了 3 页」。
  const hasAnswerPage = doc.sections.some((s) => s.isAnswer && s.questions.length);
  const bodyBudget = doc.targetPages > 0
    ? Math.max(1, doc.targetPages - (hasAnswerPage ? 1 : 0))
    : 0;
  let typeScale = 1;
  if (bodyBudget > 0 && pages.length > bodyBudget) {
    let lo = MIN_TYPE_SCALE;
    let hi = 1;
    let best = null;
    let bestScale = 1;
    for (let i = 0; i < 6; i++) {
      const mid = (lo + hi) / 2;
      const trial = layoutBody(buildBody({ typeScale: mid }));
      if (trial.length <= bodyBudget) {
        best = trial; bestScale = mid; lo = mid;
      } else {
        hi = mid;
      }
    }
    // 缩到 0.72 还塞不下：保持原字号、老实多印一页
    if (best) { pages = best; typeScale = bestScale; }
  }

  // ---- 余量转答题线（正文刚好一页时） ----
  function fillWithAnswerLines(from, cols) {
    if (from.length !== 1) return from;
    if (!doc.sections.some((s) => !s.isAnswer && s.answerLines > 0 && s.padWithLines)) return from;
    if (bodyFillOf(from) >= 0.9) return from;
    let best = from;
    let lines = 0;
    while (lines < 3) {
      const trial = layoutBody(buildBody({ extraLines: lines + 1, forceColumns: cols, typeScale }));
      if (trial.length !== from.length) break;
      best = trial;
      lines++;
      if (bodyFillOf(trial) >= BODY_FILL_TARGET) break;
    }
    return best;
  }

  // ---- 按高度挑栏数（正文刚好一页时试排 1..6 栏，取填充率最高的） ----
  let chosenColumns = 0;
  if (pages.length === 1) {
    const candidates = doc.sections.filter((s) => !s.isAnswer && s.columns > 1 && s.questions.length);
    const minCols = candidates.length ? Math.max(...candidates.map((s) => s.columns)) : 1;
    const widthCeil = candidates.length
      ? Math.min(...candidates.map((s) => fillColumns(
        s.questions.map((q) => questionBlock({ no: q.no, stem: q.stem, size: BODY_SIZE * typeScale }).singleLineWidth(tp)),
        g.contentW, COLUMN_GAP, 1, MAX_BODY_COLUMNS, true,
      )))
      : MAX_BODY_COLUMNS;
    let bestCols = 0;
    let bestScore = bodyFillOf(pages);
    let bestPages = pages;
    const hi = Math.min(MAX_BODY_COLUMNS, widthCeil);
    const lo = Math.min(minCols, hi);
    for (let cols = lo; cols <= hi; cols++) {
      if (cols === bodyColumns) continue;
      const trial = layoutBody(buildBody({ forceColumns: cols, typeScale }));
      if (trial.length !== 1) continue;
      const score = bodyFillOf(trial);
      if (score > bestScore) { bestScore = score; bestCols = cols; bestPages = trial; }
    }
    if (bestCols > 0) { chosenColumns = bestCols; pages = bestPages; }
  }

  // 先加答题线，再微调行距（顺序反了会把填满的页又撑出去）
  pages = fillWithAnswerLines(pages, chosenColumns);

  if (bodyFillOf(pages) < BODY_FILL_TARGET && gap.max > gap.base) {
    pages = stretchGap(pages, gap.base, gap.max, layoutBody, buildBody, worstFillOf, typeScale, chosenColumns);
  }

  // ---- 答案页：自适应栏数 + 撑满整页 ----
  const answerSections = doc.sections.filter((s) => s.isAnswer);
  let firstAnswerPage = -1;
  if (answerSections.some((s) => s.questions.length)) {
    firstAnswerPage = pages.length;
    const answerCount = answerSections.reduce((a, s) => a + s.questions.length, 0);

    const answerFlow = (columns, gapV) => {
      const blocks = [];
      answerSections.forEach((section) => {
        blocks.push(sectionHeader(section.title));
        section.questions.forEach((q) => {
          blocks.push(questionBlock({
            no: q.no, stem: q.stem, answerLines: section.answerLines, itemGap: gapV,
          }));
        });
      });
      return new FlowLayout({
        contentW: g.contentW, contentHFirst, contentHOther,
        columns, columnGap: COLUMN_GAP, startsOnFirstPage: false,
      }).layout(blocks, tp);
    };

    const maxCols = Math.min(6, Math.max(2, Math.floor(g.contentW / MIN_ANSWER_COL_W)));
    let bestPages = null;
    let bestFill = -1;
    for (let c = maxCols; c >= 2; c--) {
      const laid = answerFlow(c, ANSWER_GAP);
      if (laid.length !== 1) continue;
      const used = pageBottom(laid[0]);
      const rows = Math.max(1, Math.ceil(answerCount / c));
      const slack = Math.min(MAX_ANSWER_GAP, Math.max(0, (contentHOther * 0.94 - used) / Math.max(1, rows - 1)));
      const filled = answerFlow(c, ANSWER_GAP + slack);
      if (filled.length !== 1) continue;
      const fill = Math.min(1, Math.max(0, (used + slack * (rows - 1)) / contentHOther));
      if (fill >= ANSWER_MIN_FILL) { bestPages = filled; break; }
      if (fill > bestFill) { bestFill = fill; bestPages = filled; }
    }
    pages = pages.concat(bestPages ?? answerFlow(2, ANSWER_GAP));
  }

  // ---- 逐页组装绘制原语 ----
  const total = pages.length;
  const fillRatios = pages.map((page, i) => {
    const cap = i === 0 ? contentHFirst : contentHOther;
    return cap <= 0 ? 0 : Math.min(1, pageBottom(page) / cap);
  });

  const outPages = pages.map((page, index) => {
    const nodes = [];
    const contentTop = index === 0 ? drawCover(nodes, doc, g, tp) : drawRunning(nodes, doc, g);
    page.items.forEach((placed) => {
      const prims = placed.block.draw(tp, g.margin + placed.x, contentTop + placed.y, placed.w);
      nodes.push(...prims);
    });
    drawFooter(nodes, doc, g, footerSize, index + 1, total, tp);
    return {
      index, pageW: g.pageW, pageH: g.pageH, margin: g.margin,
      cover: index === 0, nodes,
    };
  });

  return {
    pages: outPages,
    pageCount: total,
    contentPageCount: firstAnswerPage > 0 ? firstAnswerPage : total,
    typeScale,
    fillRatios,
    geometry: g,
  };
}

/** 正文撑开：在「不增页」前提下二分题间距，用最差那一页评分 */
function stretchGap(from, lo, hi, layoutBody, buildBody, scoreFn, typeScale, chosenColumns) {
  let best = from;
  let bestScore = scoreFn(from);
  let loV = lo;
  let hiV = hi;
  for (let i = 0; i < 6; i++) {
    const mid = (loV + hiV) / 2;
    const trial = layoutBody(buildBody({ itemGap: mid, forceColumns: chosenColumns, typeScale }));
    if (trial.length !== from.length) {
      hiV = mid;
    } else {
      loV = mid;
      const score = scoreFn(trial);
      if (score > bestScore) { best = trial; bestScore = score; }
    }
  }
  return best;
}

// ------------------------------------------------------------------ 卷头

function drawCover(nodes, doc, g, tp) {
  let y = g.margin;
  const titlePaint = { size: TITLE_SIZE, bold: true };

  if (doc.subtitle && doc.subtitleInline) {
    const titleW = tp.measure(doc.title, TITLE_SIZE, true);
    const subW = tp.measure(doc.subtitle, SUB_SIZE);
    const gap = 12;
    const start = (g.pageW - (titleW + gap + subW)) / 2;
    const baseline = y + baselineOffset(TITLE_SIZE, TITLE_H);
    nodes.push(atBaseline(start, baseline, doc.title, TITLE_SIZE, { bold: true }));
    nodes.push(atBaseline(start + titleW + gap, baseline, doc.subtitle, SUB_SIZE, { color: COLOR_GRAY }));
    y += TITLE_H;
  } else {
    const titleW = tp.measure(doc.title, TITLE_SIZE, true);
    nodes.push(atBaseline((g.pageW - titleW) / 2, y + baselineOffset(TITLE_SIZE, TITLE_H), doc.title, TITLE_SIZE, { bold: true }));
    y += TITLE_H;
    if (doc.subtitle) {
      const subW = tp.measure(doc.subtitle, SUB_SIZE);
      nodes.push(atBaseline((g.pageW - subW) / 2, y + baselineOffset(SUB_SIZE, SUB_H), doc.subtitle, SUB_SIZE, { color: COLOR_GRAY }));
      y += SUB_H;
    }
  }

  if (doc.showNameRow) {
    const labelSize = 10.5;
    const baseline = y + baselineOffset(labelSize, NAME_ROW_H);
    let x = g.margin;

    const field = (label, lineW, value = '') => {
      nodes.push(atBaseline(x, baseline, label, labelSize));
      x += tp.measure(label, labelSize) + 3;
      let remain = lineW;
      if (value) {
        const valueSize = 11;
        const room = lineW * 0.75;
        let shown = value;
        while (shown.length > 1 && tp.measure(shown, valueSize) > room) {
          shown = `${shown.slice(0, -2)}…`;
        }
        nodes.push(atBaseline(x + 1, baseline, shown, valueSize, { color: COLOR_INK }));
        const vw = tp.measure(shown, valueSize);
        x += vw + 4;
        remain = Math.max(14, lineW - vw - 4);
      }
      nodes.push({ k: 'line', x1: x, y1: baseline + 2, x2: x + remain, y2: baseline + 2, width: 0.8, color: '#333' });
      x += remain + 16;
    };

    field('姓名', 90, doc.studentName);
    field('班级', 52);
    field('日期', 56);

    nodes.push(atBaseline(x, baseline, '得分', labelSize));
    x += tp.measure('得分', labelSize) + 6;
    for (let i = 0; i < 3; i++) {
      nodes.push({ k: 'rect', x, y: baseline - 10, w: 11, h: 12, stroke: '#333', width: 0.8 });
      x += 13;
    }
    y += NAME_ROW_H;
  }

  // 双线：粗线 + 细线，国内试卷的卷头分界
  nodes.push({ k: 'line', x1: g.margin, y1: y + 2, x2: g.pageW - g.margin, y2: y + 2, width: 1, color: '#222' });
  nodes.push({ k: 'line', x1: g.margin, y1: y + 5, x2: g.pageW - g.margin, y2: y + 5, width: 0.5, color: '#aaa' });
  y += COVER_RULE_H;
  return y;
}

// ------------------------------------------------------------------ 页眉页脚

function drawRunning(nodes, doc, g) {
  const size = 8.5;
  const baseline = g.margin + baselineOffset(size, RUNNING_H);
  nodes.push(atBaseline(g.margin, baseline, doc.title, size, { color: COLOR_GRAY }));
  nodes.push({ k: 'line', x1: g.margin, y1: g.margin + RUNNING_H - 5, x2: g.pageW - g.margin, y2: g.margin + RUNNING_H - 5, width: 0.5, color: '#bbb' });
  return g.margin + RUNNING_H;
}

function drawFooter(nodes, doc, g, footerSize, pageNo, total, tp) {
  if (doc.hasFooter === false) return;
  const right = [doc.footerStamp, doc.showPageNo ? `第 ${pageNo}/${total} 页` : ''].filter(Boolean).join('　·　');
  // 左右都没字（用户选了「整条不印」）就别留一条孤零零的线了
  if (!doc.footerText && !right) return;

  const h = footerSize.height;
  const bottom = g.pageH - g.margin;
  nodes.push({ k: 'line', x1: g.margin, y1: bottom - h + 5, x2: g.pageW - g.margin, y2: bottom - h + 5, width: 0.5, color: '#ddd' });

  const size = footerSize.fontSize;
  const baseline = bottom - 3;
  if (doc.footerText) nodes.push(atBaseline(g.margin, baseline, doc.footerText, size, { color: COLOR_LIGHT }));
  if (right) {
    const w = tp.measure(right, size);
    nodes.push(atBaseline(g.pageW - g.margin - w, baseline, right, size, { color: COLOR_LIGHT }));
  }
}

export { figureLayout };
