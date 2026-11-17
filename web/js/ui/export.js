/**
 * 导出：PDF（图片型，双击就能打印）+ PNG（单页 / 全部页）。
 *
 * 为什么不上 pdf-lib / jsPDF：整个网页版是「纯静态、零依赖」，加一个 1MB 的库
 * 就为了拼一个「每页一张图」的 PDF 不划算。PDF 的文件结构本身很简单：
 * 目录树 + 每页一个内容流 + 一张图片对象，几十行就能拼出来。
 *
 * 为什么是图片型而不是矢量文字：矢量 PDF 要嵌中文子集字体，一个字体文件 3~8MB，
 * 还得处理 GB2312 字符集与 ToUnicode 映射。考试卷子打印用 200dpi 位图完全够，
 * 所以这里选「先在 Canvas 上按 pt 坐标重画一遍，再塞进 PDF」。
 */

import { figureSvg } from './figures.js';

const SANS = '"PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif';
const MONO = '"SFMono-Regular",Menlo,Consolas,monospace';

/** 每英寸像素数 → 每 pt 像素数 */
const scaleOf = (dpi) => dpi / 72;

function svgImage(svg) {
  return new Promise((resolve) => {
    const blob = new Blob([svg], { type: 'image/svg+xml;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const img = new Image();
    img.onload = () => { URL.revokeObjectURL(url); resolve(img); };
    img.onerror = () => { URL.revokeObjectURL(url); resolve(null); };
    img.src = url;
  });
}

/**
 * 把一页的绘制原语画到 Canvas 上。
 * 坐标系仍是 pt：先 ctx.scale(每 pt 多少像素)，之后所有坐标、字号直接用 pt。
 */
export async function pageCanvas(page, dpi = 200) {
  const k = scaleOf(dpi);
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(page.pageW * k);
  canvas.height = Math.round(page.pageH * k);
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#fff';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.scale(k, k);
  ctx.textBaseline = 'middle';

  for (const p of page.nodes) {
    if (p.k === 'text') {
      // 字号写 px：CTM 已经是「1 单位 = 1pt」，写 11pt 会被再乘 4/3
      ctx.font = `${p.bold ? '700 ' : '400 '}${p.size}px ${p.mono ? MONO : SANS}`;
      ctx.fillStyle = p.color;
      ctx.fillText(p.text, p.x, p.y + p.h / 2);
    } else if (p.k === 'line') {
      ctx.strokeStyle = p.color;
      ctx.lineWidth = p.width;
      ctx.beginPath();
      ctx.moveTo(p.x1, p.y1);
      ctx.lineTo(p.x2, p.y2);
      ctx.stroke();
    } else if (p.k === 'rect') {
      // DOM 里 border 是画在盒子内侧的，这里也往里缩半个线宽
      const half = p.width / 2;
      ctx.strokeStyle = p.stroke;
      ctx.lineWidth = p.width;
      ctx.strokeRect(p.x + half, p.y + half, Math.max(0, p.w - p.width), Math.max(0, p.h - p.width));
    } else if (p.k === 'fig') {
      const img = await svgImage(figureSvg(p.spec, p.w, p.h));
      if (img) ctx.drawImage(img, p.x, p.y, p.w, p.h);
    }
  }
  return canvas;
}

function canvasToJpeg(canvas, quality = 0.92) {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (!blob) { reject(new Error('画布太大，导出失败（把清晰度调低一档再试）')); return; }
      blob.arrayBuffer().then((buf) => resolve({ bytes: new Uint8Array(buf), w: canvas.width, h: canvas.height }));
    }, 'image/jpeg', quality);
  });
}

function download(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 6000);
}

/** 拼 PDF：每页一张图，MediaBox 用纸的真实 pt 尺寸，打印时 1:1 */
export function pdfFromImages(pages, images) {
  const enc = new TextEncoder();
  const chunks = [];
  const offsets = [];
  let size = 0;
  const push = (u8) => { chunks.push(u8); size += u8.length; };
  const str = (s) => push(enc.encode(s));

  const writeObj = (id, parts) => {
    offsets[id] = size;
    str(`${id} 0 obj\n`);
    parts.forEach((part) => (typeof part === 'string' ? str(part) : push(part)));
    str('\nendobj\n');
  };

  const n = pages.length;
  const pageId = (i) => 3 + i * 3;
  const contentId = (i) => 4 + i * 3;
  const imageId = (i) => 5 + i * 3;

  str('%PDF-1.4\n');

  writeObj(1, ['<< /Type /Catalog /Pages 2 0 R >>']);
  writeObj(2, [`<< /Type /Pages /Count ${n} /Kids [${pages.map((_, i) => `${pageId(i)} 0 R`).join(' ')}] >>`]);

  for (let i = 0; i < n; i++) {
    const page = pages[i];
    const img = images[i];
    writeObj(pageId(i), [
      `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${page.pageW} ${page.pageH}] `
      + `/Resources << /XObject << /Im0 ${imageId(i)} 0 R >> >> /Contents ${contentId(i)} 0 R >>`,
    ]);

    const content = `q\n${page.pageW} 0 0 ${page.pageH} 0 0 cm\n/Im0 Do\nQ\n`;
    writeObj(contentId(i), [`<< /Length ${content.length} >>\nstream\n${content}endstream`]);

    writeObj(imageId(i), [
      `<< /Type /XObject /Subtype /Image /Width ${img.w} /Height ${img.h} `
      + `/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${img.bytes.length} >>\nstream\n`,
      img.bytes,
      '\nendstream',
    ]);
  }

  const total = 3 + n * 3;
  const xrefStart = size;
  str(`xref\n0 ${total}\n0000000000 65535 f \n`);
  for (let id = 1; id < total; id++) {
    str(`${String(offsets[id] ?? 0).padStart(10, '0')} 00000 n \n`);
  }
  str(`trailer\n<< /Size ${total} /Root 1 0 R >>\nstartxref\n${xrefStart}\n%%EOF\n`);

  return new Blob(chunks, { type: 'application/pdf' });
}

/**
 * 导出 PDF。
 * @param {Array} pages  result.pages
 * @param {{dpi?:number, name?:string, from?:number, to?:number, onProgress?:Function}} opts
 */
export async function buildPdfBlob(pages, { dpi = 200, from = 0, to = pages.length, onProgress } = {}) {
  const list = pages.slice(from, to);
  const images = [];
  for (let i = 0; i < list.length; i++) {
    onProgress?.(i + 1, list.length);
    const canvas = await pageCanvas(list[i], dpi);
    images.push(await canvasToJpeg(canvas));
    canvas.width = 0; // 主动放掉位图，多页高 DPI 时内存能省一半
    canvas.height = 0;
  }
  return pdfFromImages(list, images);
}

export async function exportPdf(pages, { dpi = 200, name = '试卷', from = 0, to = pages.length, onProgress } = {}) {
  const blob = await buildPdfBlob(pages, { dpi, from, to, onProgress });
  download(blob, `${name}.pdf`);
  return blob;
}

/** 导出 PNG：一页一张，逐页下载（浏览器不允许一次弹一堆下载，只能排队） */
export async function exportPngs(pages, { dpi = 200, name = '试卷', from = 0, to = pages.length, onProgress } = {}) {
  const list = pages.slice(from, to);
  for (let i = 0; i < list.length; i++) {
    onProgress?.(i + 1, list.length);
    const canvas = await pageCanvas(list[i], dpi);
    const blob = await new Promise((r) => canvas.toBlob(r, 'image/png'));
    const suffix = list.length > 1 ? `-第${from + i + 1}页` : '';
    download(blob, `${name}${suffix}.png`);
    await new Promise((r) => setTimeout(r, 260));
    canvas.width = 0;
    canvas.height = 0;
  }
}
