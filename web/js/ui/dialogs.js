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
  const row = document.createElement('label');
  row.className = 'field';
  const span = document.createElement('span');
  span.textContent = label;
  row.append(span, control);
  return row;
}

export function select(options, value, onChange) {
  const el = document.createElement('select');
  options.forEach((o) => {
    const opt = document.createElement('option');
    opt.value = o.value;
    opt.textContent = o.label;
    if (String(o.value) === String(value)) opt.selected = true;
    el.appendChild(opt);
  });
  el.onchange = () => onChange(el.value);
  return el;
}

export function input(type, value, onChange, attrs = {}) {
  const el = document.createElement('input');
  el.type = type;
  el.value = value ?? '';
  Object.entries(attrs).forEach(([k, v]) => el.setAttribute(k, v));
  el.oninput = () => onChange(el.value);
  return el;
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
