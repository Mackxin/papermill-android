/**
 * 打印纸 —— 对应 App 的 core/pdf/render/GridPainter.kt 与 ReferenceData。
 *
 * 两类：
 *  1. 速查表（九九乘法表、拼音表、公式表、音标表…）：内容来自 data/content.json 的 references，
 *     网页版用真表格渲染，能自动折行、能跨页，比 Canvas 画线更好维护；
 *  2. 练习格纸（田字格、四线三格、五线谱…）：线本身是版式，用 SVG 画。
 *
 * 页面上的一切都是 pt，和正文同一套坐标系，打印不缩放。
 */

const PAGE = {
  PORTRAIT: { w: 595, h: 842 },
  LANDSCAPE: { w: 842, h: 595 },
};
const MARGIN = 40;

const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

function pageShell(orientation) {
  const g = PAGE[orientation] ?? PAGE.PORTRAIT;
  const node = document.createElement('div');
  node.className = 'paper-page';
  node.setAttribute('style', `width:${g.w}pt;height:${g.h}pt;`);
  return { node, g };
}

function header(node, g, title, subtitle) {
  let y = MARGIN;
  const t = document.createElement('div');
  t.setAttribute('style', `position:absolute;left:0;top:${y}pt;width:${g.w}pt;text-align:center;`
    + 'font-size:17pt;font-weight:700;line-height:26pt;color:#1a1a1a;');
  t.textContent = title;
  node.appendChild(t);
  y += 26;
  if (subtitle) {
    const s = document.createElement('div');
    s.setAttribute('style', `position:absolute;left:${MARGIN}pt;top:${y}pt;width:${g.w - MARGIN * 2}pt;`
      + 'text-align:center;font-size:9pt;line-height:17pt;color:#555;');
    s.textContent = subtitle;
    node.appendChild(s);
    y += 17;
  }
  const rule = document.createElement('div');
  rule.setAttribute('style', `position:absolute;left:${MARGIN}pt;top:${y + 2}pt;width:${g.w - MARGIN * 2}pt;height:1pt;background:#222;`);
  node.appendChild(rule);
  return y + 12;
}

/** 速查表：真表格，列宽按 weights 分配 */
export function referenceSheet(sheet) {
  const { node, g } = pageShell('PORTRAIT');
  const top = header(node, g, sheet.title, sheet.subtitle);
  const contentW = g.w - MARGIN * 2;
  const wrap = document.createElement('div');
  wrap.setAttribute('style', `position:absolute;left:${MARGIN}pt;top:${top}pt;width:${contentW}pt;`);
  const weights = sheet.table.weights ?? [];
  const total = weights.reduce((a, b) => a + b, 0) || 1;
  const cols = `<colgroup>${sheet.table.headers.map((_, i) => `<col style="width:${((weights[i] ?? 1) / total) * 100}%">`).join('')}</colgroup>`;
  const head = `<thead><tr>${sheet.table.headers.map((h) => `<th>${esc(h)}</th>`).join('')}</tr></thead>`;
  const body = `<tbody>${sheet.table.rows.map((row) => `<tr>${row.map((c) => `<td>${esc(c)}</td>`).join('')}</tr>`).join('')}</tbody>`;
  wrap.innerHTML = `<table class="ref-table">${cols}${head}${body}</table>`;
  node.appendChild(wrap);

  // 一张纸必须装得下：行数多的表（音标 48 行、生字 180 条）按实际高度逐档缩字号。
  //
  // 两个坑（都踩过）：
  //  1. 量高度时节点必须已经在文档里。detached 节点的 getBoundingClientRect() 全是 0，
  //     循环第一轮 `0 <= availPx` 就成立、直接 break，缩字号永远不生效 —— 所以下面
  //     临时挂一个离屏容器，量完再摘下来。
  //  2. 字号要设在 <table> 上。设在 wrap 上会被 .ref-table { font-size: 9pt } 覆盖掉。
  const availPt = g.h - MARGIN - top - 8;
  const availPx = availPt * (96 / 72);
  const table = wrap.firstElementChild;
  const probe = document.createElement('div');
  probe.setAttribute('style', 'position:absolute;left:-99999px;top:0;visibility:hidden;');
  probe.appendChild(node);
  document.body.appendChild(probe);
  for (let size = 9; size >= 5.5; size -= 0.5) {
    table.style.fontSize = `${size}pt`;
    if (table.getBoundingClientRect().height <= availPx) break;
  }
  probe.removeChild(node);
  probe.remove();
  return node;
}

// ------------------------------------------------------------------ 练习格纸

const dashed = 'stroke="#9c9c9c" stroke-width="0.55" stroke-dasharray="2.4 2.4"';

function gridSvg(g, top, style, cell, themeCards) {
  const left = MARGIN;
  const width = g.w - MARGIN * 2;
  const height = g.h - MARGIN - top;
  const parts = [];
  const line = (x1, y1, x2, y2, stroke = '#b9b9b9', w = 0.5) => parts.push(`<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${stroke}" stroke-width="${w}"/>`);

  const cellOf = (def) => (cell > 0 ? cell : def);

  switch (style) {
    case 'TIANZI':
    case 'MIZI':
    case 'HUI_GONG':
    case 'JIU_GONG': {
      const s = cellOf(56);
      const cols = Math.floor(width / s);
      const rows = Math.floor(height / s);
      const x0 = left + (width - cols * s) / 2;
      for (let r = 0; r <= rows; r++) line(x0, top + r * s, x0 + cols * s, top + r * s, '#c9c9c9', 0.55);
      for (let c = 0; c <= cols; c++) line(x0 + c * s, top, x0 + c * s, top + rows * s, '#c9c9c9', 0.55);
      for (let r = 0; r < rows; r++) {
        for (let c = 0; c < cols; c++) {
          const x = x0 + c * s;
          const y = top + r * s;
          if (style === 'TIANZI' || style === 'HUI_GONG') {
            parts.push(`<line x1="${x + s / 2}" y1="${y}" x2="${x + s / 2}" y2="${y + s}" ${dashed}/>`);
            parts.push(`<line x1="${x}" y1="${y + s / 2}" x2="${x + s}" y2="${y + s / 2}" ${dashed}/>`);
          }
          if (style === 'MIZI') {
            parts.push(`<line x1="${x}" y1="${y}" x2="${x + s}" y2="${y + s}" ${dashed}/>`);
            parts.push(`<line x1="${x + s}" y1="${y}" x2="${x}" y2="${y + s}" ${dashed}/>`);
            parts.push(`<line x1="${x + s / 2}" y1="${y}" x2="${x + s / 2}" y2="${y + s}" ${dashed}/>`);
            parts.push(`<line x1="${x}" y1="${y + s / 2}" x2="${x + s}" y2="${y + s / 2}" ${dashed}/>`);
          }
          if (style === 'HUI_GONG') {
            const inset = s * 0.18;
            parts.push(`<rect x="${x + inset}" y="${y + inset}" width="${s - inset * 2}" height="${s - inset * 2}" fill="none" stroke="#c9c9c9" stroke-width="0.5"/>`);
          }
          if (style === 'JIU_GONG') {
            for (let i = 1; i < 3; i++) {
              parts.push(`<line x1="${x + (s / 3) * i}" y1="${y}" x2="${x + (s / 3) * i}" y2="${y + s}" stroke="#dcdcdc" stroke-width="0.4"/>`);
              parts.push(`<line x1="${x}" y1="${y + (s / 3) * i}" x2="${x + s}" y2="${y + (s / 3) * i}" stroke="#dcdcdc" stroke-width="0.4"/>`);
            }
          }
        }
      }
      break;
    }
    case 'PINYIN_TIANZI': {
      const s = cellOf(64);
      const rows = Math.floor(height / s);
      const cols = Math.floor(width / s);
      const x0 = left + (width - cols * s) / 2;
      for (let r = 0; r < rows; r++) {
        const y = top + r * s;
        // 上半：拼音三线（四线三格）
        const py = y;
        const ph = s * 0.42;
        for (let i = 0; i < 4; i++) {
          const ly = py + (ph / 3) * i;
          line(x0, ly, x0 + cols * s, ly, i === 0 || i === 3 ? '#b9b9b9' : '#d6d6d6', i === 0 || i === 3 ? 0.55 : 0.4);
        }
        // 下半：田字格
        const gy = y + ph;
        const gh = s - ph;
        const side = Math.min(gh, s * 0.58);
        for (let c = 0; c < cols; c++) {
          const x = x0 + c * s + (s - side) / 2;
          parts.push(`<rect x="${x}" y="${gy}" width="${side}" height="${side}" fill="none" stroke="#c9c9c9" stroke-width="0.55"/>`);
          parts.push(`<line x1="${x + side / 2}" y1="${gy}" x2="${x + side / 2}" y2="${gy + side}" ${dashed}/>`);
          parts.push(`<line x1="${x}" y1="${gy + side / 2}" x2="${x + side}" y2="${gy + side / 2}" ${dashed}/>`);
        }
      }
      break;
    }
    case 'LINED': {
      const s = cellOf(28);
      const rows = Math.floor(height / s);
      const x0 = left + width * 0.12;
      for (let r = 0; r <= rows; r++) line(x0, top + r * s, left + width, top + r * s, '#bdbdbd', 0.55);
      break;
    }
    case 'FOUR_LINE': {
      const groupH = cellOf(36);
      const groups = Math.floor(height / groupH);
      const gap = (groupH / 3);
      const x0 = left + width * 0.06;
      for (let gn = 0; gn < groups; gn++) {
        const y = top + gn * groupH;
        for (let i = 0; i < 4; i++) {
          const ly = y + gap * i + gap * 0.4;
          line(x0, ly, left + width, ly, i === 1 || i === 2 ? '#d8d8d8' : '#b0b0b0', i === 1 || i === 2 ? 0.4 : 0.6);
        }
      }
      break;
    }
    case 'DICTATION': {
      const s = cellOf(34);
      const rows = Math.floor(height / s);
      for (let r = 0; r < rows; r++) {
        const y = top + r * s + s * 0.75;
        parts.push(`<text x="${left}" y="${y}" font-size="9" fill="#666">${r + 1}.</text>`);
        line(left + 20, y, left + width, y, '#bdbdbd', 0.55);
      }
      break;
    }
    case 'SQUARE': {
      const s = cellOf(20);
      const cols = Math.floor(width / s);
      const rows = Math.floor(height / s);
      for (let r = 0; r <= rows; r++) line(left, top + r * s, left + cols * s, top + r * s, '#d2d2d2', 0.4);
      for (let c = 0; c <= cols; c++) line(left + c * s, top, left + c * s, top + rows * s, '#d2d2d2', 0.4);
      break;
    }
    case 'VERTICAL_CALC': {
      const s = cellOf(120);
      const cols = Math.floor(width / s);
      const rows = Math.floor(height / s);
      for (let r = 0; r < rows; r++) {
        for (let c = 0; c < cols; c++) {
          const x = left + c * s;
          const y = top + r * s;
          parts.push(`<rect x="${x}" y="${y}" width="${s}" height="${s}" fill="none" stroke="#c9c9c9" stroke-width="0.6"/>`);
          for (let i = 1; i <= 3; i++) {
            const ly = y + (s / 4) * i;
            line(x + 4, ly, x + s - 4, ly, '#e2e2e2', 0.4);
          }
        }
      }
      break;
    }
    case 'NUMBER_LINE': {
      const s = cellOf(60);
      const rows = Math.floor(height / s);
      const x0 = left + 12;
      const x1 = left + width - 12;
      for (let r = 0; r < rows; r++) {
        const y = top + r * s + s / 2;
        line(x0, y, x1, y, '#7a7a7a', 0.7);
        parts.push(`<polygon points="${x1},${y} ${x1 - 6},${y - 3} ${x1 - 6},${y + 3}" fill="#7a7a7a"/>`);
        for (let i = 0; i <= 10; i++) {
          const x = x0 + ((x1 - x0) / 10) * i;
          line(x, y - 3, x, y + 3, '#7a7a7a', 0.6);
        }
      }
      break;
    }
    case 'COORDINATE': {
      const s = cellOf(20);
      const cols = Math.floor(width / s);
      const rows = Math.floor(height / s);
      for (let r = 0; r <= rows; r++) line(left, top + r * s, left + cols * s, top + r * s, '#e0e0e0', 0.4);
      for (let c = 0; c <= cols; c++) line(left + c * s, top, left + c * s, top + rows * s, '#e0e0e0', 0.4);
      const cx = left + (cols / 2) * s;
      const cy = top + (rows / 2) * s;
      line(left, cy, left + cols * s, cy, '#555', 0.9);
      line(cx, top, cx, top + rows * s, '#555', 0.9);
      break;
    }
    case 'CORNELL': {
      const cueW = width * 0.28;
      const bottomY = top + height * 0.74;
      line(left + cueW, top, left + cueW, bottomY, '#9a9a9a', 0.7);
      line(left, bottomY, left + width, bottomY, '#9a9a9a', 0.7);
      const s = 26;
      for (let y = top + s; y < bottomY; y += s) line(left, y, left + width, y, '#dcdcdc', 0.4);
      for (let y = bottomY + s; y < top + height; y += s) line(left, y, left + width, y, '#dcdcdc', 0.4);
      parts.push(`<text x="${left + 4}" y="${top + 14}" font-size="8" fill="#888">线索栏</text>`);
      parts.push(`<text x="${left + cueW + 4}" y="${top + 14}" font-size="8" fill="#888">笔记区</text>`);
      parts.push(`<text x="${left + 4}" y="${bottomY + 14}" font-size="8" fill="#888">总结</text>`);
      break;
    }
    case 'MUSIC': {
      const s = cellOf(48);
      const rows = Math.floor(height / s);
      const x0 = left + 10;
      const x1 = left + width - 10;
      for (let r = 0; r < rows; r++) {
        const y = top + r * s;
        for (let i = 0; i < 5; i++) line(x0, y + i * (s / 7), x1, y + i * (s / 7), '#8a8a8a', 0.55);
      }
      break;
    }
    case 'DOT_GRID': {
      const s = cellOf(20);
      const cols = Math.floor(width / s);
      const rows = Math.floor(height / s);
      for (let r = 0; r <= rows; r++) {
        for (let c = 0; c <= cols; c++) {
          parts.push(`<circle cx="${left + c * s}" cy="${top + r * s}" r="0.7" fill="#b5b5b5"/>`);
        }
      }
      break;
    }
    case 'TIMETABLE': {
      const cols = 6;
      const headH = 26;
      const rowH = Math.min(56, (height - headH) / 8);
      const cw = width / cols;
      const labels = ['节次', '周一', '周二', '周三', '周四', '周五'];
      for (let r = 0; r <= 8; r++) line(left, top + headH + r * rowH, left + width, top + headH + r * rowH, '#9a9a9a', 0.6);
      for (let c = 0; c <= cols; c++) line(left + c * cw, top, left + c * cw, top + headH + 8 * rowH, '#9a9a9a', 0.6);
      labels.forEach((l, i) => {
        parts.push(`<text x="${left + cw * i + cw / 2}" y="${top + 17}" font-size="10" text-anchor="middle" fill="#333">${l}</text>`);
      });
      for (let r = 0; r < 8; r++) {
        parts.push(`<text x="${left + cw / 2}" y="${top + headH + r * rowH + rowH / 2 + 3}" font-size="9" text-anchor="middle" fill="#666">第${r + 1}节</text>`);
      }
      break;
    }
    case 'MISTAKE': {
      const blockH = Math.min(150, height / 3.2);
      const rows = Math.max(1, Math.floor(height / blockH));
      for (let r = 0; r < rows; r++) {
        const y = top + r * blockH;
        parts.push(`<rect x="${left}" y="${y}" width="${width}" height="${blockH - 6}" fill="none" stroke="#c9c9c9" stroke-width="0.6"/>`);
        parts.push(`<text x="${left + 6}" y="${y + 14}" font-size="9" fill="#666">题目：</text>`);
        line(left + 6, y + blockH * 0.45, left + width - 6, y + blockH * 0.45, '#dcdcdc', 0.4);
        parts.push(`<text x="${left + 6}" y="${y + blockH * 0.45 + 14}" font-size="9" fill="#666">错因：</text>`);
        line(left + 6, y + blockH * 0.72, left + width - 6, y + blockH * 0.72, '#dcdcdc', 0.4);
        parts.push(`<text x="${left + 6}" y="${y + blockH * 0.72 + 14}" font-size="9" fill="#666">正解：</text>`);
      }
      break;
    }
    case 'THEME_CARD': {
      const s = cellOf(72);
      const cols = Math.floor(width / s);
      const rows = Math.floor(height / s);
      const cards = themeCards ?? [];
      let i = 0;
      for (let r = 0; r < rows && i < cards.length; r++) {
        for (let c = 0; c < cols && i < cards.length; c++, i++) {
          const x = left + c * s;
          const y = top + r * s;
          const [pinyin, word] = cards[i] ?? ['', ''];
          parts.push(`<text x="${x + s / 2}" y="${y + 12}" font-size="8" text-anchor="middle" fill="#555">${esc(pinyin)}</text>`);
          parts.push(`<rect x="${x + 2}" y="${y + 16}" width="${s - 4}" height="${s - 20}" fill="none" stroke="#c9c9c9" stroke-width="0.55"/>`);
          parts.push(`<line x1="${x + s / 2}" y1="${y + 16}" x2="${x + s / 2}" y2="${y + s - 4}" ${dashed}/>`);
          parts.push(`<line x1="${x + 2}" y1="${y + 16 + (s - 20) / 2}" x2="${x + s - 2}" y2="${y + 16 + (s - 20) / 2}" ${dashed}/>`);
          parts.push(`<text x="${x + 6}" y="${y + s - 8}" font-size="8" fill="#bbb">${esc(word)}</text>`);
        }
      }
      break;
    }
    case 'MAZE': {
      const s = cellOf(26);
      const cols = Math.floor(width / s);
      const rows = Math.floor(height / s);
      const maze = buildMaze(cols, rows);
      const x0 = left + (width - cols * s) / 2;
      // 上边与左边实线，右边/下边按格
      line(x0, top, x0 + cols * s, top, '#555', 0.8);
      line(x0, top, x0, top + rows * s, '#555', 0.8);
      for (let r = 0; r < rows; r++) {
        for (let c = 0; c < cols; c++) {
          const cellV = maze[r][c];
          if (cellV & 2 && c === cols - 1) line(x0 + (c + 1) * s, top + r * s, x0 + (c + 1) * s, top + (r + 1) * s, '#555', 0.8);
          if (cellV & 4 && r === rows - 1) line(x0 + c * s, top + (r + 1) * s, x0 + (c + 1) * s, top + (r + 1) * s, '#555', 0.8);
          if (cellV & 2) line(x0 + (c + 1) * s, top + r * s, x0 + (c + 1) * s, top + (r + 1) * s, '#555', 0.8);
          if (cellV & 4) line(x0 + c * s, top + (r + 1) * s, x0 + (c + 1) * s, top + (r + 1) * s, '#555', 0.8);
        }
      }
      break;
    }
    default:
      break;
  }

  return `<svg viewBox="0 0 ${g.w} ${g.h}" width="${g.w}pt" height="${g.h}pt" style="position:absolute;left:0;top:0">${parts.join('')}</svg>`;
}

/** 迷宫：深度优先 + 回溯，一次生成一张（每次点都不同，和 App 一样） */
function buildMaze(cols, rows) {
  // 位掩码：1 上 2 右 4 下 8 左（= 有墙）
  const grid = Array.from({ length: rows }, () => Array.from({ length: cols }, () => 15));
  const visited = Array.from({ length: rows }, () => new Array(cols).fill(false));
  const stack = [[0, 0]];
  visited[0][0] = true;
  const dirs = [[0, -1, 1, 4], [1, 0, 2, 8], [0, 1, 4, 1], [-1, 0, 8, 2]];
  while (stack.length) {
    const [x, y] = stack[stack.length - 1];
    const options = dirs.filter(([dx, dy]) => {
      const nx = x + dx;
      const ny = y + dy;
      return nx >= 0 && ny >= 0 && nx < cols && ny < rows && !visited[ny][nx];
    });
    if (!options.length) { stack.pop(); continue; }
    const [dx, dy, wall, opposite] = options[Math.floor(Math.random() * options.length)];
    grid[y][x] &= ~wall;
    grid[y + dy][x + dx] &= ~opposite;
    visited[y + dy][x + dx] = true;
    stack.push([x + dx, y + dy]);
  }
  return grid;
}

export function practiceSheet(item) {
  const { node, g } = pageShell(item.orientation ?? 'PORTRAIT');
  const top = header(node, g, item.title, item.subtitle);
  const style = item.grid.style;
  const svg = gridSvg(g, top + 6, style, item.grid.cell, item.grid.themeCards);
  node.insertAdjacentHTML('beforeend', svg);
  if (item.grid.hint) {
    const hint = document.createElement('div');
    hint.setAttribute('style', `position:absolute;left:${MARGIN}pt;top:${top - 4}pt;font-size:8pt;color:#888;`);
    hint.textContent = item.grid.hint;
    node.appendChild(hint);
  }
  const foot = document.createElement('div');
  foot.setAttribute('style', `position:absolute;left:${MARGIN}pt;top:${g.h - MARGIN + 8}pt;width:${g.w - MARGIN * 2}pt;`
    + 'font-size:7.5pt;color:#999;');
  foot.textContent = item.footer || '纸张 · 由本程序自绘版式生成';
  node.appendChild(foot);
  return node;
}
