/**
 * 分栏分页器 —— 对应 App 的 core/pdf/layout/FlowLayout.kt，整个排版引擎的地基。
 *
 * 三条不能改的规则：
 *  1. 整卷只调一次 layout()：普通块按栏填充，整宽块（大题标题、应用题、表格）
 *     占满整行并开启新段。段与段在同一页上下相接，真的装不下才翻页。
 *  2. 多栏用「贪心最矮栏」而不是填满左栏再换右栏 —— 否则 20 道口算全挤在左半页。
 *  3. 首页和后续页可用高度不同（首页有卷头、姓名栏），所以 cap 分两个传。
 */

export class FlowLayout {
  constructor({ contentW, contentHFirst, contentHOther, columns = 1, columnGap = 20, startsOnFirstPage = true }) {
    this.contentW = contentW;
    this.contentHFirst = contentHFirst;
    this.contentHOther = contentHOther;
    this.columns = columns;
    this.columnGap = columnGap;
    this.startsOnFirstPage = startsOnFirstPage;
  }

  capFor(pageIndex) {
    return this.startsOnFirstPage && pageIndex === 0 ? this.contentHFirst : this.contentHOther;
  }

  pickColumn(colUsed, segTop, cap, h) {
    let best = -1;
    let bestUsed = Number.POSITIVE_INFINITY;
    for (let c = 0; c < colUsed.length; c++) {
      const fits = segTop + colUsed[c] + h <= cap + 0.5;
      if (fits && colUsed[c] < bestUsed) { best = c; bestUsed = colUsed[c]; }
    }
    return best;
  }

  /** 「不许单独留在页尾」的链条高度：连续 keepWithNext 的块 + 第一个普通块 */
  keepChainHeight(queue, tp, colW, contentW) {
    let sum = 0;
    for (const b of queue) {
      sum += b.measure(tp, b.fullWidth ? contentW : colW);
      if (!b.keepWithNext) break;
    }
    return sum;
  }

  splitToFit(block, tp, width, avail) {
    if (!block.splittable || avail <= 0) return null;
    const parts = block.split(tp, width, avail);
    if (!parts) return null;
    if (parts[0].measure(tp, width) <= 0) return null;
    return parts;
  }

  layout(blocks, tp) {
    if (!blocks.length) return [{ items: [] }];

    let cols = Math.max(this.columns, 1);
    let gap = this.columnGap;
    let colW = (this.contentW - gap * (cols - 1)) / cols;
    const pages = [];

    let items = [];
    let pageNo = 0;
    let cap = this.capFor(pageNo);
    let y = 0;
    let segTop = 0;
    let colUsed = new Array(cols).fill(0);

    const flushPage = () => {
      pages.push({ items });
      items = [];
      pageNo++;
      cap = this.capFor(pageNo);
      y = 0;
      segTop = 0;
      colUsed = new Array(cols).fill(0);
    };

    /**
     * 换段几何：栏数 / 列间距。整宽块刚把 colUsed 清零，此刻换最安全。
     *
     * 每个大题可以有自己的列间距（「这一个大题的两道题挨近点」），
     * 所以列宽不能只算一次 —— 但它一改，本段之后的 x 坐标全部重算，
     * 只能在大题标题（整宽块）那里换，不能夹在题目中间换。
     */
    const applyGeometry = (columns, columnGap) => {
      const nextCols = columns && columns > 0 ? Math.max(columns, 1) : cols;
      const nextGap = columnGap && columnGap > 0 ? columnGap : this.columnGap;
      if (nextCols === cols && Math.abs(nextGap - gap) < 0.01) return;
      cols = nextCols;
      gap = nextGap;
      colW = (this.contentW - gap * (cols - 1)) / cols;
      colUsed = new Array(cols).fill(0);
    };

    const queue = blocks.slice();

    while (queue.length) {
      const block = queue.shift();

      if (block.fullWidth) {
        const h = block.measure(tp, this.contentW);
        if (y + h > cap + 0.5) {
          // 当前页放不下整块：先问能不能按剩余高度切一段，避免页尾留白
          const parts = this.splitToFit(block, tp, this.contentW, cap - y);
          if (parts) {
            items.push({ block: parts[0], x: 0, y, w: this.contentW });
            queue.unshift(parts[1]);
            flushPage();
            continue;
          }
          // 切不动（一道题、标题）才按老规则：只有本页已有内容才翻页
          if (items.length) flushPage();
        }
        // 标题不许单独留在页尾
        if (block.keepWithNext && items.length) {
          const chain = this.keepChainHeight(queue, tp, colW, this.contentW);
          if (y + h + chain > cap + 0.5) {
            queue.unshift(block);
            flushPage();
            continue;
          }
        }
        items.push({ block, x: 0, y, w: this.contentW });
        y += h;
        segTop = y;
        colUsed = colUsed.map(() => 0);
        applyGeometry(block.columnHint, block.columnGapHint);
        continue;
      }

      const h = block.measure(tp, colW);
      let col = this.pickColumn(colUsed, segTop, cap, h);
      if (col < 0 && items.length) {
        flushPage();
        col = this.pickColumn(colUsed, segTop, cap, h);
      }
      if (col < 0) col = 0; // 单块比整页还高：硬放第 0 栏，保证不死循环

      items.push({ block, x: col * (colW + gap), y: segTop + colUsed[col], w: colW });
      colUsed[col] += h;
      y = Math.max(y, segTop + colUsed[col]);
    }

    if (items.length) pages.push({ items });
    return pages.length ? pages : [{ items: [] }];
  }
}
