/**
 * 文字测量与折行 —— 对应 App 的 core/pdf/TextPainter.kt。
 *
 * 为什么网页版还要自己折行，不交给浏览器：
 * 排版要把「这一块有多高」先算出来才能分页（哪一栏、哪一页、要不要翻页），
 * 而浏览器只有把元素塞进文档里量完才知道高度。先量后排 → 分页算法跑不起来。
 * 所以这里用 Canvas 的 measureText 复刻 Android 的 Paint.measureText，
 * 再把折好的每一行当成绝对定位的一行文字画出来 —— 量出来多少就是多少。
 *
 * 单位统一用 pt（CSS 的 pt 与 PDF 的 pt 是同一个 1/72 英寸），
 * 页面上写 left:120pt 就是纸面上的 120pt，打印不用再做换算。
 */

const NO_LINE_START = '，。、；：？！）〕］｝》」』】”’%,.;:?!)]}';

const isWordChar = (c) => /[A-Za-z0-9']/.test(c);

export class TextPainter {
  constructor() {
    this.canvas = document.createElement('canvas');
    this.ctx = this.canvas.getContext('2d');
    this.cache = new Map();
  }

  /** 字体串：只用 sans-serif / monospace，不打包字体文件（中文走系统回退） */
  font(size, bold = false, mono = false) {
    return `${bold ? 'bold ' : ''}${size}pt ${mono ? 'monospace' : 'sans-serif'}`;
  }

  measure(text, size, bold = false, mono = false) {
    const key = `${size}|${bold ? 1 : 0}|${mono ? 1 : 0}|${text}`;
    const hit = this.cache.get(key);
    if (hit !== undefined) return hit;
    this.ctx.font = this.font(size, bold, mono);
    const w = this.ctx.measureText(text).width;
    if (this.cache.size > 20000) this.cache.clear();
    this.cache.set(key, w);
    return w;
  }

  /** 单行行高：正文 1.5 倍行距，中文练习卷的通用呼吸感 */
  lineHeight(size, factor = 1.5) {
    return size * factor;
  }

  /**
   * 按宽度折行，逐字推进。三个必须处理的细节沿用 App：
   *  1. 避头尾：行首不能是「，。」这类标点，宁可多吞一个字；
   *  2. 英文不劈词：断点两侧都是字母/数字时往前退到词边界；
   *  3. 显式换行符强制断行。
   */
  wrap(text, size, maxWidth, bold = false, mono = false) {
    if (!text) return [''];
    if (maxWidth <= 0) return [text];
    const out = [];
    String(text).split('\n').forEach((para) => {
      if (para === '') { out.push(''); return; }
      let i = 0;
      while (i < para.length) {
        let len = 1;
        while (i + len <= para.length && this.measure(para.slice(i, i + len), size, bold, mono) <= maxWidth) {
          len++;
        }
        len = Math.max(len - 1, 1);

        // 英文不劈词
        while (len > 1 && i + len < para.length
          && isWordChar(para[i + len - 1]) && isWordChar(para[i + len])) {
          len--;
        }
        // 避头：行首不能是禁则标点
        while (i + len < para.length && NO_LINE_START.includes(para[i + len])) {
          len++;
        }
        len = Math.min(len, para.length - i);

        out.push(para.slice(i, i + len));
        i += len;
      }
    });
    return out.length ? out : [''];
  }
}

/** 全书共用一个测量器：canvas 上下文和缓存都是复用的 */
export const TP = new TextPainter();
