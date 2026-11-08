(function () {
  'use strict';

  var engineStyle = document.createElement('style');
  engineStyle.textContent = renderMarkdown.css;
  document.head.appendChild(engineStyle);

  var RENDER_OPTIONS = { breaks: true };

  var previewEl = document.getElementById('preview');

  var baseUrl = window.location.href;

  function showMarkdown(md, label, base) {
    baseUrl = base || window.location.href;
    var doc = renderMarkdown.parse(md, RENDER_OPTIONS);
    previewEl.innerHTML = doc.html;
    resolveImageSources();
    document.title = (doc.meta.title || label || 'Markdown preview') + ' — Markdown preview';
    window.scrollTo(0, 0);
  }

  function absolute(url) {
    if (typeof URL !== 'function') return null;
    try { return new URL(url, baseUrl).href; } catch (e) { return null; }
  }

  function hasScheme(url) {
    return /^[a-z][a-z0-9+.-]*:/i.test(url);
  }

  function resolveImageSources() {
    var imgs = previewEl.querySelectorAll('img[src]');
    for (var i = 0; i < imgs.length; i++) {
      var img = imgs[i];
      var src = img.getAttribute('src');
      if (!src || hasScheme(src) || src.indexOf('//') === 0 || src.charAt(0) === '#') continue;
      var abs = absolute(src);
      if (abs) img.src = abs;
    }
  }

  window.previewMarkdown = function (source, label, base) {
    showMarkdown(String(source == null ? '' : source), label || '', base || null);
  };
})();
