(function (global) {
  'use strict';

  function highlightMarkdownCode(code, language) {
    if (!global.hljs) return null;
    try {
      if (language && global.hljs.getLanguage(language)) {
        return global.hljs.highlight(code, {
          language: language,
          ignoreIllegals: true,
        }).value;
      }
      return global.hljs.highlightAuto(code).value;
    } catch (_) {
      return null;
    }
  }

  global.markdownHighlight = highlightMarkdownCode;
})(typeof globalThis !== 'undefined' ? globalThis : this);
