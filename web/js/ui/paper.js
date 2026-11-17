/**
 * 把渲染结果（绘制原语）变成 DOM 页面。
 *
 * 为什么用绝对定位而不是让浏览器自己流式排版：
 * 分页是排版引擎算出来的（哪一栏、哪一页、y 多少），DOM 只负责「照着摆」。
 * 交给浏览器流式排版，翻页边界就由浏览器决定，和屏幕上预览的不是同一张纸。
 *
 * 长度单位一律 pt：CSS 的 pt 就是 1/72 英寸，和 A4 的 595×842pt 完全对应，
 * 打印时不用任何缩放，屏幕上看到的换行位置就是纸上的换行位置。
 */

import { figureSvg } from './figures.js';

function el(tag, style, inner) {
  const node = document.createElement(tag);
  if (style) node.setAttribute('style', style);
  if (inner != null) node.innerHTML = inner;
  return node;
}

function primitiveNode(p) {
  switch (p.k) {
    case 'text': {
      const weight = p.bold ? '700' : '400';
      const family = p.mono ? '"SFMono-Regular",Menlo,Consolas,monospace' : '"PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif';
      const node = el('div',
        `left:${p.x}pt;top:${p.y}pt;height:${p.h}pt;line-height:${p.h}pt;`
        + `font-size:${p.size}pt;color:${p.color};font-weight:${weight};font-family:${family};`
        + 'position:absolute;white-space:pre;');
      node.textContent = p.text;
      return node;
    }
    case 'line': {
      const horizontal = Math.abs(p.y2 - p.y1) < 0.01;
      if (horizontal) {
        return el('div', `left:${p.x1}pt;top:${p.y1 - p.width / 2}pt;width:${Math.abs(p.x2 - p.x1)}pt;`
          + `height:${p.width}pt;background:${p.color};position:absolute;`);
      }
      return el('div', `left:${p.x1 - p.width / 2}pt;top:${p.y1}pt;width:${p.width}pt;`
        + `height:${Math.abs(p.y2 - p.y1)}pt;background:${p.color};position:absolute;`);
    }
    case 'rect':
      return el('div', `left:${p.x}pt;top:${p.y}pt;width:${p.w}pt;height:${p.h}pt;`
        + `border:${p.width}pt solid ${p.stroke};position:absolute;box-sizing:border-box;`);
    case 'fig':
      return el('div', `left:${p.x}pt;top:${p.y}pt;width:${p.w}pt;height:${p.h}pt;position:absolute;`,
        figureSvg(p.spec, p.w, p.h));
    default:
      return el('div', 'display:none');
  }
}

/** 一页纸：宽高按几何算，所有内容绝对定位 */
export function pageNode(page) {
  const node = el('div', `width:${page.pageW}pt;height:${page.pageH}pt;`);
  node.className = 'paper-page';
  node.dataset.page = String(page.index + 1);
  page.nodes.forEach((p) => node.appendChild(primitiveNode(p)));
  return node;
}

/**
 * 渲染到容器。
 * @param {HTMLElement} container
 * @param {{pages:Array}} result renderPaper 的返回值
 * @param {number} zoom 缩放（1 = 实际大小）
 * @param {{selected?:string, onHit?:Function}|null} edit 传入则进入「可点选」状态：
 *        每道题上盖一层透明的点击热区，点一下就把 qid 交给 onHit。
 */
export function renderPages(container, result, zoom = 1, edit = null) {
  container.innerHTML = '';
  const stack = el('div', 'display:flex;flex-direction:column;align-items:center;gap:16px;');
  const offset = new Map(); // qid → 这一题所在页码
  result.pages.forEach((page, i) => {
    const wrap = el('div', `width:${page.pageW * zoom}pt;height:${page.pageH * zoom}pt;`);
    wrap.className = 'paper-wrap';
    wrap.dataset.page = String(i + 1);
    const inner = pageNode(page);
    inner.style.transform = `scale(${zoom})`;
    inner.style.transformOrigin = 'top left';
    if (edit && page.hits?.length) {
      page.hits.forEach((hit) => {
        offset.set(hit.qid, i + 1);
        const box = el('div',
          `left:${hit.x}pt;top:${hit.y}pt;width:${hit.w}pt;height:${Math.max(6, hit.h)}pt;`);
        box.className = `hit${hit.qid === edit.selected ? ' on' : ''}`;
        box.title = '点一下编辑这一题';
        box.onclick = (e) => { e.stopPropagation(); edit.onHit?.(hit.qid); };
        inner.appendChild(box);
      });
      inner.classList.add('hit-layer');
    }
    wrap.appendChild(inner);
    stack.appendChild(wrap);
  });
  container.appendChild(stack);
  return offset;
}

/** 工具栏用：页数 / 缩放 / 是否缩过字号 */
export function describe(result) {
  const scale = result.typeScale;
  const parts = [`共 ${result.pageCount} 页`];
  if (scale < 0.999) parts.push(`正文字号缩到 ${Math.round(scale * 100)}%`);
  return parts.join(' · ');
}

/** 打印前注入 @page：横版必须真的声明 landscape，否则驱动会把内容再缩放一次 */
function injectPageStyle(orientation) {
  let node = document.getElementById('page-style');
  if (!node) {
    node = document.createElement('style');
    node.id = 'page-style';
    document.head.appendChild(node);
  }
  node.textContent = `@page { size: A4 ${orientation === 'LANDSCAPE' ? 'landscape' : 'portrait'}; margin: 0; }`;
}

/** 通用打印：把一组页面节点塞进打印区，调起浏览器打印（可另存为 PDF） */
export function printNodes(nodes, orientation = 'PORTRAIT') {
  injectPageStyle(orientation);
  const holder = document.getElementById('print-root');
  holder.innerHTML = '';
  nodes.forEach((node) => holder.appendChild(node));
  document.body.classList.add('printing');
  const done = () => {
    document.body.classList.remove('printing');
    holder.innerHTML = '';
    window.removeEventListener('afterprint', done);
  };
  window.addEventListener('afterprint', done);
  window.print();
}

/** 打印一份卷子 */
export function printPaper(result) {
  const landscape = result.pages.length > 0 && result.pages[0].pageW > result.pages[0].pageH;
  printNodes(result.pages.map(pageNode), landscape ? 'LANDSCAPE' : 'PORTRAIT');
}
