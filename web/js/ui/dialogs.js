/**
 * 弹窗 / 提示条。
 *
 * 只做一件事：把内容塞进一个居中的卡片，并保证**底部按钮永远可见** ——
 * App 里踩过的坑：滚动容器先拿到无限高度约束，按钮被挤到屏幕外，
 * 用户看不到出口就等于弹窗坏了。这里用 flex 布局把按钮钉在卡片底部。
 */

let activeModal = null;

export function openModal({ title, body, actions = [], wide = false }) {
  closeModal();
  const mask = document.createElement('div');
  mask.className = 'modal-mask';

  const card = document.createElement('div');
  card.className = `modal-card${wide ? ' wide' : ''}`;

  const head = document.createElement('div');
  head.className = 'modal-head';
  head.innerHTML = `<h3>${title}</h3>`;
  const close = document.createElement('button');
  close.className = 'icon-btn';
  close.textContent = '✕';
  close.onclick = closeModal;
  head.appendChild(close);

  const content = document.createElement('div');
  content.className = 'modal-body';
  content.appendChild(body);

  const foot = document.createElement('div');
  foot.className = 'modal-foot';
  actions.forEach((a) => {
    const btn = document.createElement('button');
    btn.className = `btn ${a.kind ?? ''}`;
    btn.textContent = a.label;
    btn.onclick = () => { if (a.onClick) a.onClick(); if (a.close !== false) closeModal(); };
    foot.appendChild(btn);
  });

  card.append(head, content, foot);
  mask.appendChild(card);
  mask.addEventListener('click', (e) => { if (e.target === mask) closeModal(); });
  document.body.appendChild(mask);
  activeModal = mask;
  return content;
}

export function closeModal() {
  if (activeModal) {
    activeModal.remove();
    activeModal = null;
  }
}

export function toast(message, ms = 2200) {
  const node = document.createElement('div');
  node.className = 'toast';
  node.textContent = message;
  document.body.appendChild(node);
  setTimeout(() => node.classList.add('out'), ms);
  setTimeout(() => node.remove(), ms + 300);
}

export function confirmDialog(title, message, onOk, okLabel = '确定') {
  const body = document.createElement('div');
  body.className = 'confirm-body';
  body.textContent = message;
  openModal({
    title,
    body,
    actions: [
      { label: '取消', kind: 'ghost' },
      { label: okLabel, kind: 'danger', onClick: onOk },
    ],
  });
}

/** 表单小工具：一行「标签 + 控件」 */
export function field(label, control) {
  // 用 div 而不是 label：自定义下拉里包着按钮，label 会把点击转给按钮、连点两次
  const row = document.createElement('div');
  row.className = 'field';
  const span = document.createElement('span');
  span.textContent = label;
  row.append(span, control);
  return row;
}

/**
 * 下拉选择 —— 不要用原生 <select>。
 *
 * 原生的两个毛病（用户截图点名）：
 *  1. macOS 上是一块灰扑扑的小方框，跟整个界面不是一个世界；
 *  2. 弹出来的列表是系统绘制的蓝条 + ✓，页面里怎么改 CSS 都改不动。
 * 所以自己画：一个按钮 + 一层浮层。里面仍放一个真的 <select>（视觉隐藏），
 * 好处是 value / change 事件照旧、自动化测试和读屏都能用。
 */
export function select(options, value, onChange) {
  const wrap = document.createElement('div');
  wrap.className = 'dd';

  const native = document.createElement('select');
  native.className = 'dd-native';
  native.tabIndex = -1;
  options.forEach((o) => {
    const opt = document.createElement('option');
    opt.value = o.value;
    opt.textContent = o.label;
    if (String(o.value) === String(value)) opt.selected = true;
    native.appendChild(opt);
  });

  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'dd-btn';
  const labelOf = (v) => options.find((o) => String(o.value) === String(v))?.label ?? options[0]?.label ?? '';
  const paint = () => {
    btn.innerHTML = '';
    btn.append(document.createTextNode(labelOf(native.value)));
    const caret = document.createElement('i');
    caret.className = 'dd-caret';
    btn.appendChild(caret);
  };

  const pop = document.createElement('div');
  pop.className = 'dd-pop';
  pop.setAttribute('role', 'listbox');

  const mark = () => {
    [...pop.children].forEach((row) => {
      const on = row.dataset.value === String(native.value);
      row.classList.toggle('on', on);
      row.setAttribute('aria-selected', on ? 'true' : 'false');
    });
  };

  options.forEach((o) => {
    const row = document.createElement('button');
    row.type = 'button';
    row.className = 'dd-opt';
    row.dataset.value = String(o.value);
    row.setAttribute('role', 'option');
    row.textContent = o.label;
    row.onclick = () => {
      native.value = String(o.value);
      paint();
      mark();
      close();
      onChange(o.value);
    };
    pop.appendChild(row);
  });

  const close = () => {
    wrap.classList.remove('open');
    document.removeEventListener('mousedown', onDocDown, true);
    document.removeEventListener('keydown', onKey, true);
  };
  const onDocDown = (e) => { if (!wrap.contains(e.target)) close(); };
  const onKey = (e) => {
    if (e.key === 'Escape') { close(); btn.focus(); return; }
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
    e.preventDefault();
    const list = [...pop.children];
    const i = list.findIndex((r) => r.dataset.value === String(native.value));
    const next = list[Math.max(0, Math.min(list.length - 1, i + (e.key === 'ArrowDown' ? 1 : -1)))];
    if (!next) return;
    native.value = next.dataset.value;
    paint();
    mark();
    onChange(native.value);
  };

  btn.onclick = () => {
    if (wrap.classList.contains('open')) { close(); return; }
    // 同时只开一个：先关掉别的
    document.querySelectorAll('.dd.open').forEach((w) => { if (w !== wrap) w.classList.remove('open'); });
    wrap.classList.add('open');
    mark();
    document.addEventListener('mousedown', onDocDown, true);
    document.addEventListener('keydown', onKey, true);
    pop.querySelector('.dd-opt.on')?.scrollIntoView({ block: 'nearest' });
  };

  // 测试 / 快捷脚本直接改隐藏 select 时也要生效
  native.onchange = () => { paint(); mark(); onChange(native.value); };

  paint();
  mark();
  wrap.append(native, btn, pop);
  return wrap;
}

// 点空白处关掉所有下拉
document.addEventListener('mousedown', (e) => {
  if (!e.target.closest?.('.dd')) {
    document.querySelectorAll('.dd.open').forEach((w) => w.classList.remove('open'));
  }
}, true);

export function input(type, value, onChange, attrs = {}) {
  const el = document.createElement('input');
  el.type = type;
  el.value = value ?? '';
  Object.entries(attrs).forEach(([k, v]) => el.setAttribute(k, v));
  el.oninput = () => onChange(el.value);
  return el;
}

/** 数字加减器：把原生 input 那对上下小箭头换成真正的 − / ＋（原生箭头在 macOS 上又小又糊） */
export function stepper(value, onChange, { min = 0, max = 200, step = 1 } = {}) {
  const wrap = document.createElement('div');
  wrap.className = 'stepper';

  const box = document.createElement('input');
  box.type = 'number';
  box.className = 'num';
  box.min = String(min);
  box.max = String(max);
  box.step = String(step);
  box.value = String(value ?? 0);

  const bump = (d) => {
    const next = Math.max(min, Math.min(max, (Number(box.value) || 0) + d));
    box.value = String(next);
    onChange(next);
  };

  const minus = document.createElement('button');
  minus.type = 'button';
  minus.className = 'step-btn';
  minus.textContent = '−';
  minus.tabIndex = -1;
  minus.onclick = () => bump(-step);

  const plus = document.createElement('button');
  plus.type = 'button';
  plus.className = 'step-btn';
  plus.textContent = '＋';
  plus.tabIndex = -1;
  plus.onclick = () => bump(step);

  box.onchange = () => {
    const next = Math.max(min, Math.min(max, Number(box.value) || 0));
    box.value = String(next);
    onChange(next);
  };

  wrap.append(minus, box, plus);
  return wrap;
}

/** 滑杆 + 当前值：排版微调用，拖着就能看到纸面重排 */
export function slider(value, onChange, { min = 0, max = 100, step = 1, unit = '', format } = {}) {
  const wrap = document.createElement('div');
  wrap.className = 'slider';

  const input = document.createElement('input');
  input.type = 'range';
  input.min = String(min);
  input.max = String(max);
  input.step = String(step);
  input.value = String(value ?? min);

  const out = document.createElement('b');
  const text = () => (format ? format(Number(input.value)) : `${input.value}${unit}`);
  out.textContent = text();

  input.oninput = () => {
    out.textContent = text();
    onChange(Number(input.value));
  };

  wrap.append(input, out);
  return wrap;
}

export function textarea(value, onChange, rows = 6) {
  const el = document.createElement('textarea');
  el.rows = rows;
  el.value = value ?? '';
  el.oninput = () => onChange(el.value);
  return el;
}

export function chips(options, value, onChange) {
  const wrap = document.createElement('div');
  wrap.className = 'chips';
  options.forEach((o) => {
    const b = document.createElement('button');
    b.className = `chip${String(o.value) === String(value) ? ' on' : ''}`;
    b.textContent = o.label;
    b.onclick = () => onChange(o.value);
    wrap.appendChild(b);
  });
  return wrap;
}
