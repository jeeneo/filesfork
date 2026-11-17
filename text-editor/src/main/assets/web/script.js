const API = window.location.pathname.replace(/\/$/, '');
let currentPath = null;
let dirty = false;
let previewOn = true;
let isMarkdown = false;

const treeRoot = document.getElementById('tree-root');
const sourceEl = document.getElementById('source');
const sourceWrapEl = document.getElementById('source-wrap');
const previewEl = document.getElementById('preview');
const panesEl = document.getElementById('panes');
const emptyEl = document.getElementById('empty-state');
const toolsEl = document.getElementById('editor-tools');
const statusEl = document.getElementById('status');
const pathLabel = document.getElementById('current-path');
const newFileBtn = document.getElementById('new-file-btn');
const newFolderBtn = document.getElementById('new-folder-btn');
const uploadBtn = document.getElementById('upload-btn');
const uploadInput = document.getElementById('upload-input');
const previewToggle = document.getElementById('preview-toggle');
previewToggle.hidden = true;
const engineStyle = document.createElement('style');
engineStyle.textContent = renderMarkdown.css;
document.head.appendChild(engineStyle);
let currentDir = '';
const expandedDirs = new Set();

let remoteChangeDialogOpen = false;
const editorId = window.crypto?.randomUUID?.()
  || `${Date.now()}-${Math.random().toString(36).slice(2)}`;

// ---- SSE refresh events ----
const evtSource = new EventSource(API + '/events');
evtSource.addEventListener('init', () => {});
evtSource.addEventListener('refresh-dirs', async () => {
  await loadTree('', treeRoot);
});
evtSource.addEventListener('refresh-file', async (e) => {
  const eventData = JSON.parse(e.data);
  const changedPath = eventData.path;
  if (eventData.sourceId === 'WEB_UI' && eventData.clientId === editorId) return;
  if (changedPath !== currentPath || remoteChangeDialogOpen) return;
  const sourceLabel = eventData.sourceId === 'APP'
    ? '{{source_app}}'
    : '{{source_web_ui}}';
  remoteChangeDialogOpen = true;
  try {
    const result = await showDialog({
      title: '{{sse_file_changed_title}}',
      message: '{{sse_file_changed_body}}'.replace('%1$s', sourceLabel),
      okText: '{{reload_text}}',
      cancelText: '{{keep_editing_text}}',
      danger: false,
    });
    if (result && changedPath === currentPath) {
      await openFile(changedPath, true);
    }
  } finally {
    remoteChangeDialogOpen = false;
  }
});

sourceEl.addEventListener('input', () => {
  dirty = true;
  updatePreview();
});

async function api(path, opts) {
  const res = await fetch(API + path, opts);
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new Error(text || res.statusText);
  }
  return res;
}

function setStatus(text, ms) {
  statusEl.textContent = text;
  if (ms) setTimeout(() => { if (statusEl.textContent === text) statusEl.textContent = ''; }, ms);
}

// ---- dialog ----
const dialogBackdrop = document.getElementById('dialog-backdrop');
const dialogTitle = document.getElementById('dialog-title');
const dialogMessage = document.getElementById('dialog-message');
const dialogInput = document.getElementById('dialog-input');
const dialogCancelBtn = document.getElementById('dialog-cancel');
const dialogOkBtn = document.getElementById('dialog-ok');
let dialogResolve = null;

function showDialog(opts) {
  return new Promise((resolve) => {
    dialogResolve = resolve;
    dialogTitle.textContent = opts.title || '';
    dialogMessage.textContent = opts.message || '';
    dialogMessage.style.display = opts.message ? '' : 'none';
    dialogInput.hidden = !opts.input;
    dialogInput.value = opts.value || '';
    dialogOkBtn.textContent = opts.okText || 'OK';
    dialogCancelBtn.textContent = opts.cancelText || 'Cancel';
    dialogOkBtn.className = opts.danger ? 'danger' : 'primary';
    dialogCancelBtn.hidden = !opts.cancelText;
    dialogBackdrop.hidden = false;
    if (opts.input) { dialogInput.focus(); dialogInput.select(); }
    else { dialogOkBtn.focus(); }
  });
}

function closeDialog(result) {
  dialogBackdrop.hidden = true;
  if (dialogResolve) { dialogResolve(result); dialogResolve = null; }
}

dialogOkBtn.addEventListener('click', () => closeDialog(dialogInput.hidden ? true : dialogInput.value));
dialogCancelBtn.addEventListener('click', () => closeDialog(dialogInput.hidden ? false : null));
dialogInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') closeDialog(dialogInput.value);
});
dialogBackdrop.addEventListener('click', (e) => {
  if (e.target === dialogBackdrop) closeDialog(null);
});
dialogBackdrop.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') closeDialog(null);
});

// ---- context menu ----
const ctxMenu = document.getElementById('ctx-menu');
const ctxRenameBtn = document.getElementById('ctx-rename');
const ctxDeleteBtn = document.getElementById('ctx-delete');
let ctxPath = null;

function showCtxMenu(path, x, y) {
  ctxPath = path;
  ctxMenu.style.left = x + 'px';
  ctxMenu.style.top = y + 'px';
  ctxMenu.hidden = false;
}
function hideCtxMenu() { ctxMenu.hidden = true; ctxPath = null; }

ctxRenameBtn.addEventListener('click', async () => {
const path = ctxPath;
hideCtxMenu();
if (!path) return;
const newName = await showDialog({
title: '{{button_rename}}',
input: true,
value: path.split('/').pop(),
okText: '{{button_rename}}',
});
if (!newName) return;
try {
const res = await api('/api/rename?path=' + encodeURIComponent(path) + '&new_name=' + encodeURIComponent(newName), { method: 'POST' });
    const data = await res.json();
    await loadTree('', treeRoot);
    openFile(data.path);
  } catch (e) { showDialog({ title: '{{alert_rename_failed}}', message: e.message }); }
});

ctxDeleteBtn.addEventListener('click', async () => {
const path = ctxPath;
hideCtxMenu();
if (!path) return;
const ok = await showDialog({
title: '{{button_delete}}',
message: '{{confirm_delete}}'.replace('%1$s', path),
okText: '{{button_delete}}',
cancelText: '{{cancel_text}}',
danger: true,
});
if (!ok) return;
try {
await api('/api/delete?path=' + encodeURIComponent(path), { method: 'POST' });
if (currentPath === path) { closeFile(); }
    await loadTree('', treeRoot);
  } catch (e) { showDialog({ title: '{{alert_delete_failed}}', message: e.message }); }
});

document.addEventListener('click', () => hideCtxMenu());
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') hideCtxMenu(); });

const PREVIEW_OPTIONS = { breaks: true, highlight: window.markdownHighlight };

function updatePreview() {
  if (isMarkdown && previewOn) {
    previewEl.innerHTML = renderMarkdown(sourceEl.value, PREVIEW_OPTIONS);
  }
}

function applyPreview() {
  previewToggle.hidden = !isMarkdown;
  previewToggle.textContent = previewOn ? '{{label_preview_on}}' : '{{label_preview_off}}';
  const showPreview = isMarkdown && previewOn;
  previewEl.style.display = showPreview ? '' : 'none';
  sourceWrapEl.style.borderRight = showPreview ? '1px solid var(--border)' : 'none';
  if (showPreview) updatePreview();
}

// ---- tree ----
async function loadTree(path, container) {
  const res = await api('/api/tree?path=' + encodeURIComponent(path));
  const entries = await res.json();
  container.innerHTML = '';
  for (const entry of entries) {
    const li = document.createElement('li');
    const node = document.createElement('div');
    const full = path ? path + '/' + entry.name : entry.name;
    node.className = 'node ' + entry.type + (entry.type === 'file' && !entry.editable ? ' nonedit' : '');
    node.dataset.path = full;
    const icon = document.createElement('span');
    icon.className = 'icon';
    icon.textContent = entry.type === 'dir' ? '\u25b8' : '\u2013';
    const label = document.createElement('span');
    label.className = 'label';
    label.textContent = entry.name;
    node.appendChild(icon); node.appendChild(label);
    li.appendChild(node);

    if (entry.type === 'dir') {
      const childUl = document.createElement('ul');
      childUl.className = 'tree';
      childUl.style.display = 'none';
      li.appendChild(childUl);
      let loaded = false;
      node.addEventListener('click', async () => {
        currentDir = full;
        const open = childUl.style.display !== 'none';
        if (open) {
          childUl.style.display = 'none'; icon.textContent = '\u25b8'; expandedDirs.delete(full);
        } else {
          if (!loaded) { await loadTree(full, childUl); loaded = true; }
          childUl.style.display = ''; icon.textContent = '\u25be'; expandedDirs.add(full);
        }
      });
      if (expandedDirs.has(full)) {
        icon.textContent = '\u25be';
        childUl.style.display = '';
        await loadTree(full, childUl);
        loaded = true;
      }
    } else {
      node.addEventListener('click', () => {
        if (!entry.editable) { setStatus('{{status_not_text_file}}', 1500); return; }
        openFile(full);
      });
      node.addEventListener('contextmenu', (e) => {
        e.preventDefault();
        if (!entry.editable) return;
        showCtxMenu(full, e.clientX, e.clientY);
      });
    }
    container.appendChild(li);
  }
}

function markActive(path) {
  document.querySelectorAll('.node.active').forEach(n => n.classList.remove('active'));
  const el = document.querySelector('.node[data-path="' + CSS.escape(path) + '"]');
  if (el) el.classList.add('active');
}

function expandAncestors(path) {
  const segments = path.split('/');
  segments.pop();
  let prefix = '';
  for (const segment of segments) {
    prefix = prefix ? prefix + '/' + segment : segment;
    expandedDirs.add(prefix);
  }
}

function closeFile() {
  currentPath = null;
  dirty = false;
  isMarkdown = false;
  previewOn = false;
  panesEl.style.display = 'none';
  toolsEl.style.display = 'none';
  emptyEl.style.display = 'flex';
  pathLabel.textContent = '{{label_no_file_open}}';
  previewToggle.hidden = true;
  previewEl.style.display = 'none';
}

async function openFile(path, skipSave = false) {
  if (!skipSave && dirty && !(await saveCurrent())) return;
  const res = await api('/api/file?path=' + encodeURIComponent(path));
  const data = await res.json();
  currentPath = path;
  currentDir = path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : '';
  expandAncestors(path);
  const binary = atob(data.content);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  sourceEl.value = new TextDecoder().decode(bytes);
  dirty = false;
  isMarkdown = data.markdown === true;
  previewOn = isMarkdown;
  pathLabel.textContent = path;
  emptyEl.style.display = 'none';
  panesEl.style.display = 'flex';
  toolsEl.style.display = 'flex';
  markActive(path);
  applyPreview();
}

async function saveCurrent() {
  if (!currentPath) return false;
  const path = currentPath;
  const content = sourceEl.value;
  setStatus('{{status_saving}}');
  try {
    await api('/api/file?path=' + encodeURIComponent(path), {
      method: 'PUT',
      headers: {
        'Content-Type': 'text/plain',
        'X-Editor-Id': editorId,
      },
      body: content,
    });
    if (currentPath !== path || sourceEl.value !== content) return false;
    dirty = false;
    setStatus('{{status_saved}}', 1500);
    return true;
  } catch (e) {
    setStatus('{{status_save_failed}}', 3000);
    console.error(e);
    return false;
  }
}

// ---- scroll sync (proportional) ----
let syncingScroll = false;

function syncScroll(from, to) {
  if (syncingScroll) return;
  syncingScroll = true;
  const fromMax = from.scrollHeight - from.clientHeight;
  const toMax = to.scrollHeight - to.clientHeight;
  if (fromMax > 0 && toMax > 0) {
    to.scrollTop = (from.scrollTop / fromMax) * toMax;
  }
  syncingScroll = false;
}

sourceEl.addEventListener('scroll', () => syncScroll(sourceEl, previewEl));
previewEl.addEventListener('scroll', () => syncScroll(previewEl, sourceEl));

window.addEventListener('keydown', (e) => {
  if ((e.metaKey || e.ctrlKey) && e.key === 's') {
    e.preventDefault();
    saveCurrent();
  }
});
window.addEventListener('beforeunload', (e) => {
  if (dirty) { saveCurrent(); }
});

document.getElementById('save-btn').addEventListener('click', () => {
  saveCurrent();
});

previewToggle.addEventListener('click', () => {
  if (!isMarkdown) return;
  previewOn = !previewOn;
  applyPreview();
});

newFileBtn.addEventListener('click', async () => {
  const name = await showDialog({
    title: '{{button_new_file}}',
    input: true,
    okText: '{{button_new_file}}',
  });
  if (!name) return;
  try {
    await api('/api/newfile?path=' + encodeURIComponent(currentDir) + '&name=' + encodeURIComponent(name), { method: 'POST' });
    await loadTree('', treeRoot);
    openFile(currentDir ? currentDir + '/' + name : name);
  } catch (e) { showDialog({ title: '{{alert_create_file_failed}}', message: e.message }); }
});

newFolderBtn.addEventListener('click', async () => {
  const name = await showDialog({
    title: '{{button_new_folder}}',
    input: true,
    okText: '{{button_new_folder}}',
  });
  if (!name) return;
  try {
    await api('/api/newfolder?path=' + encodeURIComponent(currentDir) + '&name=' + encodeURIComponent(name), { method: 'POST' });
    await loadTree('', treeRoot);
  } catch (e) { showDialog({ title: '{{alert_create_folder_failed}}', message: e.message }); }
});

uploadBtn.addEventListener('click', () => uploadInput.click());

uploadInput.addEventListener('change', async () => {
  const files = Array.from(uploadInput.files || []);
  if (!files.length) return;
  uploadBtn.disabled = true;
  setStatus('{{status_uploading}}');
  try {
    const body = new FormData();
    for (const file of files) body.append('file', file, file.name);
    const res = await api('/api/upload?path=' + encodeURIComponent(currentDir), { method: 'POST', body: body });
    const data = await res.json();
    expandedDirs.add(currentDir);
    await loadTree('', treeRoot);
    const first = data.files && data.files[0];
    if (first) markActive(first.path);
    const uploadedCount = data.count || files.length;
    const uploadedTemplate = uploadedCount === 1 ? '{{status_uploaded_one}}' : '{{status_uploaded_other}}';
    setStatus(uploadedTemplate.replace('%1$d', uploadedCount), 2500);
  } catch (e) {
    setStatus('{{status_upload_failed}}', 3000);
    showDialog({ title: '{{alert_upload_failed}}', message: e.message });
  } finally {
    uploadInput.value = '';
    uploadBtn.disabled = false;
  }
});

document.getElementById('rename-btn').addEventListener('click', async () => {
  if (!currentPath) return;
  const newName = await showDialog({
    title: '{{button_rename}}',
    input: true,
    value: currentPath.split('/').pop(),
    okText: '{{button_rename}}',
  });
  if (!newName) return;
  try {
    const res = await api('/api/rename?path=' + encodeURIComponent(currentPath) + '&new_name=' + encodeURIComponent(newName), { method: 'POST' });
    const data = await res.json();
    await loadTree('', treeRoot);
    openFile(data.path);
  } catch (e) { showDialog({ title: '{{alert_rename_failed}}', message: e.message }); }
});

document.getElementById('delete-btn').addEventListener('click', async () => {
  if (!currentPath) return;
  const ok = await showDialog({
    title: '{{button_delete}}',
    message: '{{confirm_delete}}'.replace('%1$s', currentPath),
    okText: '{{button_delete}}',
    cancelText: 'Cancel',
    danger: true,
  });
  if (!ok) return;
  try {
    await api('/api/delete?path=' + encodeURIComponent(currentPath), { method: 'POST' });
    closeFile();
    await loadTree('', treeRoot);
  } catch (e) { showDialog({ title: '{{alert_delete_failed}}', message: e.message }); }
});

loadTree('', treeRoot);
