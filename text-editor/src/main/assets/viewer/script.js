// Read-only Markdown preview for the Android editor's WebView.
// The host owns the title bar and pushes an in-memory document, so there is no
// file picker, drop target or status chrome here — only the renderer:
//   window.previewMarkdown(source, label, baseUrl)
// Rendering is delegated to markdown.js, the Markdown engine in this folder.

// The engine ships its own stylesheet (alerts, tables, footnotes, task lists, …).
const engineStyle = document.createElement('style');
engineStyle.textContent = renderMarkdown.css;
document.head.appendChild(engineStyle);

// Single newlines in the source render as line breaks, as the preview always did.
const RENDER_OPTIONS = { breaks: true };

const previewEl = document.getElementById('preview');

// URL that relative images in the current document resolve against.
let baseUrl = window.location.href;

function showMarkdown(md, label, base) {
  baseUrl = base || window.location.href;
  const doc = renderMarkdown.parse(md, RENDER_OPTIONS);
  previewEl.innerHTML = doc.html;
  resolveImageSources();
  document.title = (doc.meta.title || label || 'Markdown preview') + ' — Markdown preview';
  window.scrollTo(0, 0);
}

// Images in a rendered document are relative to the document, not to this page.
function resolveImageSources() {
  previewEl.querySelectorAll('img[src]').forEach((img) => {
    const src = img.getAttribute('src');
    if (!src || hasScheme(src) || src.startsWith('//') || src.startsWith('#')) return;
    try { img.src = new URL(src, baseUrl).href; } catch (_) { /* leave as written */ }
  });
}

function hasScheme(url) {
  return /^[a-z][a-z0-9+.-]*:/i.test(url);
}

window.previewMarkdown = function (source, label, base) {
  showMarkdown(String(source == null ? '' : source), label || '', base || null);
};
