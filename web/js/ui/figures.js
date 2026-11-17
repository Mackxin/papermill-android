/**
 * 看图题的配图 —— 对应 App 的 core/pdf/figure/FigurePainter.kt。
 *
 * App 里是 Canvas 画圆/弧/路径，网页版换成内联 SVG：矢量、打印清晰、
 * 而且尺寸可以直接用 pt，和排版层算出来的高度一一对上。
 *
 * 五类图：数一数（按行排图形）、十格阵、钟面、条形统计图、两组物品。
 */

const SHAPE_SIZE = 14;
const ROW_H = 26;

const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

/** 四种图形：圆 / 三角 / 方 / 五角星 */
function shape(kind, cx, cy, r, fill = '#4a4a4a') {
  switch (kind % 4) {
    case 1:
      return `<polygon points="${cx},${cy - r} ${cx + r * 0.92},${cy + r * 0.72} ${cx - r * 0.92},${cy + r * 0.72}" fill="${fill}"/>`;
    case 2:
      return `<rect x="${cx - r * 0.86}" y="${cy - r * 0.86}" width="${r * 1.72}" height="${r * 1.72}" fill="${fill}"/>`;
    case 3: {
      const pts = [];
      for (let i = 0; i < 10; i++) {
        const rad = i % 2 === 0 ? r : r * 0.45;
        const a = (Math.PI / 5) * i - Math.PI / 2;
        pts.push(`${cx + rad * Math.cos(a)},${cy + rad * Math.sin(a)}`);
      }
      return `<polygon points="${pts.join(' ')}" fill="${fill}"/>`;
    }
    default:
      return `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${fill}"/>`;
  }
}

function counting(spec, w, h) {
  const gap = SHAPE_SIZE * 1.6;
  const fiveGap = gap * 1.8;
  const parts = [];
  (spec.groups ?? []).forEach((count, row) => {
    const kind = spec.shapes?.[row] ?? 0;
    const cy = row * ROW_H + SHAPE_SIZE / 2 + 3;
    let cx = SHAPE_SIZE / 2;
    for (let i = 0; i < Math.min(count, 20); i++) {
      parts.push(shape(kind, cx, cy, SHAPE_SIZE / 2));
      cx += i === 4 ? fiveGap : gap;
    }
  });
  return `<svg viewBox="0 0 ${w} ${h}" width="${w}pt" height="${h}pt">${parts.join('')}</svg>`;
}

function tenFrame(spec, w, h) {
  const n = Math.min(10, Math.max(0, spec.groups?.[0] ?? 0));
  const cell = w / 5;
  const parts = [`<rect x="0.4" y="0.4" width="${w - 0.8}" height="${h - 0.8}" fill="none" stroke="#6e6e6e" stroke-width="0.8"/>`];
  for (let i = 1; i < 5; i++) {
    parts.push(`<line x1="${i * cell}" y1="0" x2="${i * cell}" y2="${h}" stroke="#6e6e6e" stroke-width="0.8"/>`);
  }
  parts.push(`<line x1="0" y1="${h / 2}" x2="${w}" y2="${h / 2}" stroke="#6e6e6e" stroke-width="0.8"/>`);
  for (let i = 0; i < n; i++) {
    const r = Math.floor(i / 5);
    const c = i % 5;
    parts.push(`<circle cx="${c * cell + cell / 2}" cy="${r * (h / 2) + h / 4}" r="${Math.min(cell, h / 2) * 0.3}" fill="#4a4a4a"/>`);
  }
  return `<svg viewBox="0 0 ${w} ${h}" width="${w}pt" height="${h}pt">${parts.join('')}</svg>`;
}

function clock(spec) {
  const hour = spec.groups?.[0] ?? 3;
  const minute = spec.groups?.[1] ?? 0;
  const d = 100;
  const c = d / 2;
  const r = c - 4;
  const parts = [
    `<circle cx="${c}" cy="${c}" r="${r}" fill="#fff" stroke="#333" stroke-width="1"/>`,
  ];
  for (let i = 0; i < 60; i++) {
    const a = (Math.PI * 2 * i) / 60 - Math.PI / 2;
    const major = i % 5 === 0;
    const r1 = r - (major ? 5 : 2.5);
    const r2 = r - 0.5;
    parts.push(`<line x1="${c + r1 * Math.cos(a)}" y1="${c + r1 * Math.sin(a)}" x2="${c + r2 * Math.cos(a)}" y2="${c + r2 * Math.sin(a)}" stroke="${major ? '#333' : '#999'}" stroke-width="${major ? 0.9 : 0.5}"/>`);
  }
  for (let i = 1; i <= 12; i++) {
    const a = (Math.PI * 2 * i) / 12 - Math.PI / 2;
    const rr = r - 11;
    parts.push(`<text x="${c + rr * Math.cos(a)}" y="${c + rr * Math.sin(a)}" font-size="6.4" text-anchor="middle" dominant-baseline="central" fill="#333">${i}</text>`);
  }
  const hourA = ((hour % 12) + minute / 60) / 12 * Math.PI * 2 - Math.PI / 2;
  const minA = (minute / 60) * Math.PI * 2 - Math.PI / 2;
  parts.push(`<line x1="${c}" y1="${c}" x2="${c + r * 0.48 * Math.cos(hourA)}" y2="${c + r * 0.48 * Math.sin(hourA)}" stroke="#222" stroke-width="2.4" stroke-linecap="round"/>`);
  parts.push(`<line x1="${c}" y1="${c}" x2="${c + r * 0.72 * Math.cos(minA)}" y2="${c + r * 0.72 * Math.sin(minA)}" stroke="#222" stroke-width="1.5" stroke-linecap="round"/>`);
  parts.push(`<circle cx="${c}" cy="${c}" r="1.6" fill="#222"/>`);
  return `<svg viewBox="0 0 ${d} ${d}" width="${d}pt" height="${d}pt">${parts.join('')}</svg>`;
}

function barChart(spec, w) {
  const BAR_H = 96;
  const h = BAR_H + 16;
  const padL = 16;
  const padB = 12;
  const plotW = w - padL - 6;
  const plotH = BAR_H - padB;
  const values = spec.values ?? [];
  const labels = spec.labels ?? [];
  const max = Math.max(1, ...values);
  const step = plotW / Math.max(1, values.length);
  const barW = Math.min(step * 0.5, 22);
  const parts = [
    `<line x1="${padL}" y1="${plotH}" x2="${w - 4}" y2="${plotH}" stroke="#555" stroke-width="0.8"/>`,
    `<line x1="${padL}" y1="4" x2="${padL}" y2="${plotH}" stroke="#555" stroke-width="0.8"/>`,
  ];
  for (let i = 0; i < 5; i++) {
    const y = plotH - (plotH / 4) * i;
    parts.push(`<line x1="${padL}" y1="${y}" x2="${w - 4}" y2="${y}" stroke="#ddd" stroke-width="0.4"/>`);
    parts.push(`<text x="${padL - 2}" y="${y}" font-size="5.4" text-anchor="end" dominant-baseline="central" fill="#666">${Math.round((max / 4) * i)}</text>`);
  }
  values.forEach((v, i) => {
    const bh = (v / max) * (plotH - 6);
    const x = padL + step * i + (step - barW) / 2;
    parts.push(`<rect x="${x}" y="${plotH - bh}" width="${barW}" height="${bh}" fill="#7d8ba1"/>`);
    parts.push(`<text x="${x + barW / 2}" y="${plotH - bh - 4}" font-size="6" text-anchor="middle" fill="#333">${v}</text>`);
    parts.push(`<text x="${padL + step * i + step / 2}" y="${plotH + 7}" font-size="6" text-anchor="middle" fill="#333">${esc(labels[i] ?? '')}</text>`);
  });
  if (spec.unit) {
    parts.push(`<text x="${w - 4}" y="8" font-size="5.6" text-anchor="end" fill="#666">单位：${esc(spec.unit)}</text>`);
  }
  return `<svg viewBox="0 0 ${w} ${h}" width="${w}pt" height="${h}pt">${parts.join('')}</svg>`;
}

function twoGroups(spec, w, h) {
  const gap = SHAPE_SIZE * 1.5;
  const [left, right] = [spec.groups?.[0] ?? 0, spec.groups?.[1] ?? 0];
  const kind = spec.shapes?.[0] ?? 0;
  const cy = h / 2;
  const parts = [];
  const drawGroup = (n, startX) => {
    for (let i = 0; i < Math.min(n, 12); i++) {
      parts.push(shape(kind, startX + i * gap + SHAPE_SIZE / 2, cy, SHAPE_SIZE / 2));
    }
    return startX + Math.min(n, 12) * gap;
  };
  const leftEnd = drawGroup(left, 4);
  const mid = Math.min(w - 20, leftEnd + 14);
  parts.push(`<line x1="${mid}" y1="${cy - 14}" x2="${mid}" y2="${cy + 14}" stroke="#999" stroke-width="0.8" stroke-dasharray="3 3"/>`);
  drawGroup(right, mid + 14);
  return `<svg viewBox="0 0 ${w} ${h}" width="${w}pt" height="${h}pt">${parts.join('')}</svg>`;
}

/** 按图形数据画一张 SVG，尺寸由排版层给（w/h 都是 pt） */
export function figureSvg(spec, w, h) {
  if (!spec) return '';
  switch (spec.kind) {
    case 'COUNTING': return counting(spec, w, h);
    case 'TEN_FRAME': return tenFrame(spec, w, h);
    case 'CLOCK': return clock(spec);
    case 'BAR_CHART': return barChart(spec, w);
    case 'TWO_GROUPS': return twoGroups(spec, w, h);
    default: return '';
  }
}
