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
import { TP } from '../core/measure.js';

function el(tag, style, inner) {
  const node = document.createElement(tag);
  if (style) node.setAttribute('style', style);
  if (inner != null) node.innerHTML = inner;
  return node;
}

function primitiveNode(p) {
  const node = buildNode(p);
  // 打上题目身份：编辑时要把「这道题原来的文字」藏起来，换成输入框
  if (p.q) node.dataset.q = p.q;
  // 答案页上的块用 a：改答案时只重画答案那一格，不碰正文
  if (p.a) node.dataset.a = p.a;
  return node;
}

function buildNode(p) {
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
 * 原地编辑框：直接盖在这道题的 slot 上，字号与行距跟纸面一模一样。
 *
 * 为什么不放在右侧面板里：用户的原话是「点击某处可以直接在原地方改」——
 * 看着卷子改卷子，眼睛不用在两个地方来回跳。输入过程中**不触发任何重排**
 * （onChange 只更新数据），提交后才由 renderer 把这一题重画回原来的 slot。
 */
function inlineEditor(hit, inline, page) {
  const fs = hit.size ?? 10;
  const lh = TP.lineHeight(fs);
  // 高度：内容多高框就多高（最多 10 行 / 到纸底为止），再长就在框里滚动。
  //
  // 试过「只占原格子」——格子之间本来就排得很紧，只剩一行高，写的字自己都看不见；
  // 也试过「长到下一题之前」——同样没留白可用。所以编辑时它就是一张**浮起来的卡片**，
  // 允许临时遮住下面几行；这不叫破坏布局：提交后整张卷的位置一个都不会变。
  const bottom = (page?.pageH ?? 842) - hit.y - 24;
  const maxH = Math.max(hit.h + 6, Math.min(lh * 10 + 12, bottom));
  const box = el('div', `left:${hit.x}pt;top:${hit.y}pt;width:${hit.w}pt;max-height:${maxH}pt;`);
  box.className = 'inline-editor';

  const ta = document.createElement('textarea');
  ta.className = 'inline-ta';
  ta.value = inline.value ?? '';
  ta.spellcheck = false;
  ta.setAttribute('style', `font-size:${fs}pt;line-height:${lh}pt;`);
  const grow = () => { ta.style.height = 'auto'; ta.style.height = `${ta.scrollHeight}px`; };

  let closed = false;
  const done = (commit) => {
    if (closed) return;
    closed = true;
    if (commit) inline.onCommit?.(ta.value, ans.value);
    else inline.onCancel?.();
  };

  // 答案一起改：改了题干而答案还是旧的，这张卷就没法批改了（用户报的正是这个）。
  // 算式类题型在 app.js 里会自动重算，这里只负责让人能手动改。
  const ansRow = el('div', '');
  ansRow.className = 'inline-ans';
  const ansTag = el('span', '');
  ansTag.className = 'inline-ans-label';
  ansTag.textContent = '答案';
  const ans = document.createElement('input');
  ans.className = 'inline-ans-input';
  ans.type = 'text';
  ans.value = inline.answer ?? '';
  ans.placeholder = '这一题的答案（可留空）';
  ans.spellcheck = false;
  ansRow.appendChild(ansTag);
  ansRow.appendChild(ans);

  // 失焦只在「焦点离开整个编辑框」时才提交：点到答案框不该把编辑框关掉
  const blurOut = (e) => { if (!box.contains(e.relatedTarget)) done(true); };

  ta.oninput = () => { grow(); inline.onChange?.(ta.value); };
  ta.onblur = blurOut;
  ta.onkeydown = (e) => {
    if (e.key === 'Escape') { e.preventDefault(); done(false); }
    else if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) { e.preventDefault(); done(true); }
  };
  ans.oninput = () => inline.onAnswerChange?.(ans.value);
  ans.onblur = blurOut;
  ans.onkeydown = (e) => {
    if (e.key === 'Escape') { e.preventDefault(); done(false); }
    else if (e.key === 'Enter') { e.preventDefault(); done(true); }
  };

  const bar = el('div', '');
  bar.className = 'inline-bar';
  const mk = (label, cls, fn) => {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = cls;
    b.textContent = label;
    b.onmousedown = (e) => e.preventDefault();   // 别让 textarea 先失焦，否则「还原」永远点不到
    b.onclick = fn;
    return b;
  };
  bar.appendChild(mk('✓ 完成', 'ok', () => done(true)));
  bar.appendChild(mk('↺ 还原', 'ghost', () => done(false)));
  box.appendChild(ta);
  box.appendChild(ansRow);
  box.appendChild(bar);

  setTimeout(() => {
    ta.focus();
    ta.setSelectionRange(ta.value.length, ta.value.length);
    grow();
  }, 0);
  return box;
}

/**
 * 渲染到容器。
 * @param {HTMLElement} container
 * @param {{pages:Array}} result renderPaper 的返回值
 * @param {number} zoom 缩放（1 = 实际大小）
 * @param {{selected?:string, onHit?:Function, mode?:'click'|'dbl'}|null} edit 传入则盖热区：
 *        mode='click'（编辑排版的默认值）点一下就把 qid 交给 onHit；
 *        mode='dbl' 是预览页用的：单击不做事，**双击**才进原地编辑 ——
 *        平时在预览里读卷子，手一抖点到某道题不该弹出输入框。
 * @param {number} cols 一行排几页（电脑端宽屏用；手机端恒为 1）
 */
export function renderPages(container, result, zoom = 1, edit = null, cols = 1) {
  container.innerHTML = '';
  container.dataset.cols = String(cols);
  // 一列就竖着堆；多列用 grid —— 每页尺寸由 zoom 定死，grid 只负责摆位置，
  // 最后一行不满时自动居中，不靠手算偏移量。
  const stack = el('div', cols > 1
    ? `display:grid;grid-template-columns:repeat(${cols},max-content);justify-content:center;`
      + 'align-items:start;gap:18px;'
    : 'display:flex;flex-direction:column;align-items:center;gap:16px;');
  stack.className = 'paper-grid';
  const offset = new Map(); // qid → 这一题所在页码
  result.pages.forEach((page, i) => {
    const wrap = el('div', `width:${page.pageW * zoom}pt;height:${page.pageH * zoom}pt;`);
    wrap.className = 'paper-wrap';
    wrap.dataset.page = String(i + 1);
    const inner = pageNode(page);
    inner.style.transform = `scale(${zoom})`;
    inner.style.transformOrigin = 'top left';
    if (edit && page.hits?.length) {
      const inline = edit.inline?.qid ? edit.inline : null;
      const dbl = edit.mode === 'dbl';
      page.hits.forEach((hit) => {
        offset.set(hit.qid, i + 1);
        const editable = !!inline && inline.qid === hit.qid;
        const box = el('div',
          `left:${hit.x}pt;top:${hit.y}pt;width:${hit.w}pt;height:${Math.max(6, hit.h)}pt;`);
        box.className = `hit${dbl ? ' dbl' : ''}${hit.qid === edit.selected ? ' on' : ''}`
          + `${editable ? ' editing' : ''}`;
        box.title = dbl ? '双击这里，直接在原地改这道题' : '点一下就能在原地改';
        if (dbl) {
          box.ondblclick = (e) => { e.preventDefault(); e.stopPropagation(); edit.onHit?.(hit.qid); };
        } else {
          box.onclick = (e) => { e.stopPropagation(); edit.onHit?.(hit.qid); };
        }
        inner.appendChild(box);
        if (editable) {
          // 这道题原来的文字先藏起来，底下换成输入框 —— 不然两行字叠在一起看不清
          inner.querySelectorAll(`[data-q="${hit.qid}"]`)
            .forEach((n) => { n.style.visibility = 'hidden'; });
          inner.appendChild(inlineEditor(hit, inline, page));
        }
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
