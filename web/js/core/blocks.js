/**
 * 排版的基本单元 —— 对应 App 的 core/pdf/layout/blocks/*.kt。
 *
 * 每个块两个能力：
 *  - measure(tp, width)：这块在这个宽度下有多高（纯计算，可反复调）；
 *  - draw(tp, x, y, width)：产出一串「绘制原语」（文字 / 直线 / 配图），
 *    由 ui 层把它们变成绝对定位的 DOM。
 *
 * 为什么中间隔一层绘制原语：这样排版与渲染解耦 —— 分页算法只管高度，
 * 换渲染方式（DOM / Canvas / SVG）都不用动排版。
 */

// 正文字号、标题字号、说明字号（与 PaperRenderer 常量一致）
export const BODY_SIZE = 11;
export const SECTION_SIZE = 11.5;
export const NOTE_SIZE = 9;

const text = (x, y, str, size, opts = {}) => ({
  k: 'text', x, y, text: str, size,
  bold: opts.bold ?? false,
  mono: opts.mono ?? false,
  color: opts.color ?? '#1a1a1a',
  h: opts.h ?? size * 1.5,
});

const line = (x1, y1, x2, y2, width = 0.6, color = '#999') => ({ k: 'line', x1, y1, x2, y2, width, color });

/** 配图：只描述「占多大、画什么」，具体画法在 ui/figures.js */
const SHAPE_SIZE = 14;
const ROW_H = 26;
const BAR_H = 96;

export function figureLayout(spec, width) {
  switch (spec.kind) {
    case 'COUNTING':
      return { w: width, h: (spec.groups?.length ?? 0) * ROW_H + 4 };
    case 'TEN_FRAME': {
      const w = Math.min(width * 0.62, 210);
      return { w, h: (w / 5) * 2 + 6 };
    }
    case 'CLOCK': {
      const d = Math.min(width * 0.26, 112);
      return { w: d, h: d + 10 };
    }
    case 'BAR_CHART':
      return { w: Math.min(width * 0.66, 280), h: BAR_H + 16 };
    case 'TWO_GROUPS':
      return { w: width, h: 44 };
    default:
      return { w: width, h: 0 };
  }
}

// ------------------------------------------------------------------ 大题标题

export function sectionHeader(title, size = SECTION_SIZE, columnHint = 0) {
  const TOP_PAD = 10;
  const BOTTOM_PAD = 6;
  const lh = size * 1.5;
  return {
    type: 'header',
    fullWidth: true,
    keepWithNext: true,
    columnHint,
    measure() { return lh + TOP_PAD + BOTTOM_PAD; },
    // 注意签名要和其它块一致：(tp, x, y, width) —— 少了 tp 就会把 tp 当成 x
    draw(tp, x, y) {
      return [text(x, y + TOP_PAD, title, size, { bold: true, h: lh })];
    },
  };
}

// ------------------------------------------------------------------ 说明文字

export function noteBlock(str, size = NOTE_SIZE) {
  return {
    type: 'note',
    fullWidth: true,
    keepWithNext: true,
    columnHint: 0,
    _key: null,
    _lines: null,
    lines(tp, width) {
      if (this._key === width) return this._lines;
      this._lines = tp.wrap(str, size, width);
      this._key = width;
      return this._lines;
    },
    measure(tp, width) {
      return this.lines(tp, width).length * tp.lineHeight(size) * 1.2;
    },
    draw(tp, x, y, width) {
      const lh = tp.lineHeight(size) * 1.2;
      return this.lines(tp, width).map((l, i) => text(x, y + i * lh, l, size, { color: '#666', h: lh }));
    },
  };
}

// ------------------------------------------------------------------ 一道题

export function questionBlock({ no, uid = null, stem, answerLines = 0, size = BODY_SIZE, itemGap = 4, figure = null }) {
  const NUMBER_W = 20;
  const ANSWER_LINE_FACTOR = 1.55;
  const FIGURE_GAP = 5;
  let numW = -1;
  let key = null;
  let lines = null;

  const numberWidth = (tp) => {
    if (numW < 0) numW = Math.max(NUMBER_W, tp.measure(`${no}.`, size, false, true) + 3);
    return numW;
  };
  const linesFor = (tp, width) => {
    if (key === width) return lines;
    lines = tp.wrap(stem, size, Math.max(20, width - numberWidth(tp)));
    key = width;
    return lines;
  };

  const block = {
    type: 'question',
    fullWidth: false,
    keepWithNext: false,
    columnHint: 0,
    stem,
    no,
    /** 编辑器用：这道题在 paper.sections 里的稳定序号；答案页为 null */
    qid: uid,
    /* 下面四个只有编辑器重画单题时才读（排版本身不看），把「原件」留在块上，
       省得重画时再回头去 paper 里翻一遍参数、翻错了字号就和上下文对不上 */
    size,
    answerLines,
    itemGap,
    figure,
    /** 这道题不折行需要多宽：排版拿它定栏数 */
    singleLineWidth(tp) {
      return numberWidth(tp) + tp.measure(stem, size);
    },
    measure(tp, width) {
      const lh = tp.lineHeight(size);
      const textH = linesFor(tp, width).length * lh;
      const extra = answerLines > 0 ? answerLines * lh * ANSWER_LINE_FACTOR : 0;
      const figH = figure ? figureLayout(figure, Math.max(60, width - numberWidth(tp))).h : 0;
      return textH + (figure ? figH + FIGURE_GAP : 0) + extra + itemGap;
    },
    draw(tp, x, y, width) {
      const lh = tp.lineHeight(size);
      const out = [text(x, y, `${no}.`, size, { mono: true, h: lh })];
      let cursor = y;
      linesFor(tp, width).forEach((l) => {
        out.push(text(x + numberWidth(tp), cursor, l, size, { h: lh }));
        cursor += lh;
      });
      if (figure) {
        const fw = Math.max(60, width - numberWidth(tp));
        const box = figureLayout(figure, fw);
        out.push({ k: 'fig', x: x + numberWidth(tp), y: cursor + FIGURE_GAP, w: box.w, h: box.h, spec: figure });
        cursor += box.h + FIGURE_GAP;
      }
      if (answerLines > 0) {
        const step = lh * ANSWER_LINE_FACTOR;
        for (let i = 0; i < answerLines; i++) {
          const ly = cursor + step * (i + 1) - lh * 0.35;
          out.push(line(x + numberWidth(tp), ly, x + width, ly, 0.6, '#999'));
        }
      }
      return out;
    },
  };
  return block;
}

// ------------------------------------------------------------------ 网格表格

export function tableBlock(headers, weights, rows, { showHeader = true, textSize = 10, rowScale = 1 } = {}) {
  const PAD_X = 3.5;
  const PAD_Y = 3.5;
  const LINE_FACTOR = 1.42;
  const MIN_ROW_H = textSize * 2;
  const BOTTOM_GAP = 10;

  let cacheWidth = -1;
  let cache = null;

  const colWidths = (width) => {
    const total = weights.reduce((a, b) => a + b, 0) || 1;
    const out = weights.map((w) => (w / total) * width);
    while (out.length < headers.length) out.push(width / headers.length);
    return out;
  };

  const layout = (tp, width) => {
    if (cacheWidth === width) return cache;
    const lineH = textSize * LINE_FACTOR;
    const cols = colWidths(width);
    const all = showHeader ? [headers, ...rows] : rows;
    cache = all.map((cells, index) => {
      const bold = showHeader && index === 0;
      const cellLines = cols.map((cw, c) => tp.wrap(
        cells[c] ?? '', textSize, Math.max(cw - PAD_X * 2, textSize * 2), bold,
      ));
      const maxLines = Math.max(1, ...cellLines.map((l) => l.length));
      const rawH = Math.max(maxLines * lineH + PAD_Y * 2, MIN_ROW_H);
      return { cells: cellLines, height: rawH * rowScale };
    });
    cacheWidth = width;
    return cache;
  };

  return {
    type: 'table',
    fullWidth: true,
    keepWithNext: false,
    columnHint: 0,
    splittable: true,
    measure(tp, width) {
      return layout(tp, width).reduce((a, r) => a + r.height, 0) + BOTTOM_GAP;
    },
    /** 按剩余高度切：前半留在原页、后半另起一页并重画表头 */
    split(tp, width, maxHeight) {
      if (rows.length <= 1) return null;
      const laid = layout(tp, width);
      const firstRowIndex = showHeader ? 1 : 0;
      let used = showHeader ? laid[0].height : 0;
      let take = 0;
      while (take < rows.length - 1) {
        const rowH = laid[firstRowIndex + take]?.height;
        if (rowH == null) break;
        if (used + rowH + BOTTOM_GAP > maxHeight) break;
        used += rowH;
        take++;
      }
      if (take <= 0) return null;
      return [
        tableBlock(headers, weights, rows.slice(0, take), { showHeader, textSize, rowScale }),
        tableBlock(headers, weights, rows.slice(take), { showHeader, textSize, rowScale }),
      ];
    },
    draw(tp, x, y, width) {
      const out = [];
      const cols = colWidths(width);
      const lineH = textSize * LINE_FACTOR;
      const laid = layout(tp, width);
      let cursor = y;
      laid.forEach((row, rowIndex) => {
        const isHeader = showHeader && rowIndex === 0;
        const top = cursor;
        const bottom = cursor + row.height;
        out.push(line(x, top, x + width, top, isHeader ? 0.7 : 0.4, isHeader ? '#888' : '#bbb'));
        let left = x;
        cols.forEach((cw, c) => {
          out.push(line(left, top, left, bottom, c === 0 ? 0.7 : 0.4, c === 0 ? '#888' : '#bbb'));
          const cellLines = row.cells[c] ?? [];
          if (cellLines.some((l) => l)) {
            let ty = top + (row.height - cellLines.length * lineH) / 2;
            cellLines.forEach((l) => {
              if (l) {
                const tx = isHeader ? left + (cw - tp.measure(l, textSize, isHeader)) / 2 : left + PAD_X;
                out.push(text(tx, ty, l, textSize, { bold: isHeader, h: lineH }));
              }
              ty += lineH;
            });
          }
          left += cw;
        });
        out.push(line(x + width, top, x + width, bottom, 0.7, '#888'));
        cursor = bottom;
      });
      out.push(line(x, cursor, x + width, cursor, 0.7, '#888'));
      return out;
    },
  };
}

/** 把块强制改成占整行（应用题 / 带图题要用） */
export function fullWidth(block) {
  return {
    type: block.type,
    fullWidth: true,
    keepWithNext: block.keepWithNext ?? false,
    columnHint: block.columnHint ?? 0,
    qid: block.qid ?? null,
    measure: (tp, w) => block.measure(tp, w),
    draw: (tp, x, y, w) => block.draw(tp, x, y, w),
  };
}

export function spacer(height) {
  return {
    type: 'spacer',
    fullWidth: false,
    keepWithNext: false,
    columnHint: 0,
    measure: () => height,
    draw: () => [],
  };
}

export { line as linePrimitive, text as textPrimitive };
