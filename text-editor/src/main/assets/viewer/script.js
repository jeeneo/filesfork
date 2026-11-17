const engineStyle = document.createElement('style');
engineStyle.textContent = renderMarkdown.css;
document.head.appendChild(engineStyle);
const RENDER_OPTIONS = { breaks: true, highlight: window.markdownHighlight };
const previewEl = document.getElementById('preview');
let baseUrl = window.location.href;

function showMarkdown(md, label, base) {
  baseUrl = base || window.location.href;
  const doc = renderMarkdown.parse(md, RENDER_OPTIONS);
  previewEl.innerHTML = doc.html;
  resolveImageSources();
  document.title = (doc.meta.title || label || 'Markdown preview') + ' — Markdown preview';
  window.scrollTo(0, 0);
}

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
