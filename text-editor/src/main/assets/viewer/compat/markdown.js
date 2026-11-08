/*!
 *   html            'safe' (default: allow-listed tags only) | true (raw passthrough) | false (escape everything)
 *   breaks          false   treat single newlines as <br>
 *   headingIds      true    add slug ids to headings
 *   headingAnchors  false   add a "#" permalink inside headings
 *   externalLinks   true    target=_blank rel=noopener on http(s) links
 *   sanitizeUrls    true    neutralise javascript:/vbscript:/file:/non-image data: URLs
 *   emoji           true    :smile: shortcodes
 *   typographer     false   (c) (tm) ... -- --- and curly quotes
 *   subSup          true    H~2~O and E=mc^2^
 *   wikiLinks       false   [[Page]] / [[Page|label]]
 *   wikiLinkBase    ''      prefix for wiki links
 *   autolink        true    bare http(s)://, www. and e-mail autolinks
 *   highlight       null    (code, lang) => html | null   (plug in highlight.js / Prism / Shiki)
 *
 * Plain ES5 on purpose: this file is loaded by the Android WebView, which on
 * Android 5 is Chromium 37 and cannot parse arrow functions, let/const,
 * template literals, destructuring or regex lookbehind.
 */
(function (global) {
  'use strict';

  // --- ES5 helpers -------------------------------------------------------

  // Set is Chrome 38; a lookup table works everywhere.
  function setOf(list) {
    var map = {}, i;
    for (i = 0; i < list.length; i++) if (list[i]) map[list[i]] = true;
    return { has: function (k) { return Object.prototype.hasOwnProperty.call(map, k); } };
  }
  // Object.assign is Chrome 45.
  function extend(target) {
    for (var i = 1; i < arguments.length; i++) {
      var src = arguments[i];
      if (!src) continue;
      for (var k in src) if (Object.prototype.hasOwnProperty.call(src, k)) target[k] = src[k];
    }
    return target;
  }
  // String.prototype.repeat is Chrome 41.
  function repeat(str, n) {
    var out = '';
    for (var i = 0; i < n; i++) out += str;
    return out;
  }
  function startsWith(s, p) { return s.indexOf(p) === 0; }
  function includes(s, p) { return s.indexOf(p) !== -1; }

  var DEFAULTS = {
    html: 'safe',
    breaks: false,
    headingIds: true,
    headingAnchors: false,
    externalLinks: true,
    sanitizeUrls: true,
    emoji: true,
    typographer: false,
    subSup: true,
    wikiLinks: false,
    wikiLinkBase: '',
    autolink: true,
    highlight: null
  };

  var esc = function (s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  };
  var escRe = function (s) { return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); };
  var isBlank = function (s) { return !/\S/.test(s); };
  var normKey = function (s) { return s.trim().replace(/\s+/g, ' ').toLowerCase(); };
  var indentOf = function (s) { var k = 0; while (s.charAt(k) === ' ') k++; return k; };

  var HR_RE = /^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$/;
  var LIST_RE = /^( {0,3})([-*+]|\d{1,9}[.)])( +|$)/;
  var DD_RE = /^ {0,3}:[ \t]+\S/;
  var TABLE_DELIM_RE = /^ {0,3}\|?[ \t]*:?-+:?[ \t]*(?:\|[ \t]*:?-+:?[ \t]*)*\|?[ \t]*$/;
  var HTML_BLOCK_RE = /^ {0,3}(?:<!--|<\/(?:address|article|aside|blockquote|center|details|dialog|dd|div|dl|dt|fieldset|figcaption|figure|footer|form|h[1-6]|header|hr|iframe|li|main|nav|ol|p|pre|section|summary|table|tbody|td|tfoot|th|thead|tr|ul)(?:[\s/>]|$)|<(?:address|article|aside|blockquote|center|details|dialog|dd|div|dl|dt|fieldset|figcaption|figure|footer|form|h[1-6]|header|hr|iframe|li|main|nav|ol|p|pre|section|summary|table|tbody|td|tfoot|th|thead|tr|ul)(?:[\s/>]|$))/i;

  // GitHub: NOTE TIP IMPORTANT WARNING CAUTION. The rest are Obsidian extras.
  var CALLOUTS = {
    note: ['Note', 'info'], tip: ['Tip', 'bulb'], important: ['Important', 'report'],
    warning: ['Warning', 'alert'], caution: ['Caution', 'stop'],
    info: ['Info', 'info'], abstract: ['Abstract', 'info'], todo: ['Todo', 'check'],
    success: ['Success', 'check'], question: ['Question', 'info'], failure: ['Failure', 'stop'],
    danger: ['Danger', 'stop'], bug: ['Bug', 'stop'], example: ['Example', 'report'], quote: ['Quote', 'report']
  };
  var ALIASES = {
    summary: 'abstract', tldr: 'abstract', hint: 'tip', check: 'success', done: 'success',
    help: 'question', faq: 'question', attention: 'warning', fail: 'failure', missing: 'failure',
    error: 'danger', cite: 'quote'
  };
  var GITHUB_TYPES = setOf(['note', 'tip', 'important', 'warning', 'caution']);
  var resolveCallout = function (t) {
    t = t.toLowerCase();
    t = ALIASES[t] || t;
    return CALLOUTS[t] ? t : null;
  };

  var ICON_PATHS = {
    info: '<circle cx="8" cy="8" r="6.5"/><path d="M8 7.2v4M8 4.6v.1"/>',
    bulb: '<path d="M6 12.2h4M6.6 14.2h2.8M8 1.8a4.4 4.4 0 0 0-2.4 8.1v1.3h4.8V9.9A4.4 4.4 0 0 0 8 1.8z"/>',
    report: '<path d="M2 2.5h12v8.5H9.2L6 14v-3H2z"/><path d="M8 5v2.4M8 9v.1"/>',
    alert: '<path d="M8 1.8l6.5 11.7h-13z"/><path d="M8 6.3v3.4M8 11.6v.1"/>',
    stop: '<path d="M5.2 1.8h5.6l3.4 3.4v5.6l-3.4 3.4H5.2L1.8 10.8V5.2z"/><path d="M8 4.8v3.8M8 10.8v.1"/>',
    check: '<circle cx="8" cy="8" r="6.5"/><path d="M5.2 8.2l2 2 3.6-4"/>'
  };
  var icon = function (k) {
    return '<svg class="octicon" viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">' + ICON_PATHS[k] + '</svg>';
  };

  function renderAlert(type, titleHtml, body, fold) {
    var cls = 'markdown-alert markdown-alert-' + type;
    var head = icon(CALLOUTS[type][1]) + titleHtml;
    if (fold) {
      return '<details class="' + cls + '"' + (fold === '+' ? ' open' : '') +
        '><summary class="markdown-alert-title">' + head + '</summary>' + body + '</details>';
    }
    return '<div class="' + cls + '" role="note"><p class="markdown-alert-title">' + head + '</p>' + body + '</div>';
  }

  var EMOJI = {
    smile: '😄', grin: '😁', joy: '😂', rofl: '🤣', wink: '😉', sunglasses: '😎', heart_eyes: '😍', sweat_smile: '😅',
    cry: '😢', sob: '😭', thinking: '🤔', eyes: '👀', wave: '👋', clap: '👏', pray: '🙏', muscle: '💪',
    '+1': '👍', thumbsup: '👍', '-1': '👎', thumbsdown: '👎', point_right: '👉', '100': '💯',
    heart: '❤️', fire: '🔥', star: '⭐', sparkles: '✨', tada: '🎉', rocket: '🚀', zap: '⚡', coffee: '☕',
    sunny: '☀️', warning: '⚠️', white_check_mark: '✅', heavy_check_mark: '✔️', x: '❌', question: '❓', exclamation: '❗',
    information_source: 'ℹ️', bulb: '💡', bug: '🐛', memo: '📝', book: '📖', books: '📚', lock: '🔒', key: '🔑',
    link: '🔗', pushpin: '📌', package: '📦', wrench: '🔧', hammer: '🔨', construction: '🚧', art: '🎨'
  };

  var SAFE_TAGS = setOf(('a abbr b blockquote br caption cite code col colgroup dd del details dfn div dl dt em figcaption figure ' +
    'h1 h2 h3 h4 h5 h6 hr i img ins kbd li mark ol p pre q rp rt ruby s samp small span strong sub summary sup table tbody td tfoot ' +
    'th thead time tr u ul var wbr').split(' '));
  var VOID_TAGS = setOf(['br', 'hr', 'img', 'wbr', 'col']);
  var SAFE_ATTRS = {
    '*': ['title', 'lang', 'dir', 'align'],
    a: ['href', 'name'], img: ['src', 'alt', 'width', 'height'],
    td: ['colspan', 'rowspan'], th: ['colspan', 'rowspan', 'scope'], ol: ['start', 'type'],
    details: ['open'], time: ['datetime'], col: ['span']
  };
  var URL_ATTRS = setOf(['href', 'src']);

  function unsafeUrl(u) {
    var c = u.replace(/[\u0000-\u0020\u007f-\u009f]/g, '');
    return /^(?:javascript|vbscript|file):/i.test(c) || /^data:(?!image\/(?:png|gif|jpe?g|webp|avif);)/i.test(c);
  }
  var attrEsc = function (s) {
    return s.replace(/&(?!#?\w+;)/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
  };

  function sanitizeHtml(src) {
    return src.split(/(<!--[\s\S]*?-->|<\/?[A-Za-z][^<>]*>)/).map(function (part, idx) {
      if (idx % 2 === 0) {
        return part.replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/&(?!#?\w+;)/g, '&amp;');
      }
      if (startsWith(part, '<!--')) return '';
      var m = /^<(\/?)([A-Za-z][\w-]*)([\s\S]*?)\/?>$/.exec(part);
      if (!m) return esc(part);
      var tag = m[2].toLowerCase();
      if (!SAFE_TAGS.has(tag)) return esc(part);
      if (m[1]) return VOID_TAGS.has(tag) ? '' : '</' + tag + '>';
      var allowed = SAFE_ATTRS['*'].concat(SAFE_ATTRS[tag] || []);
      var out = '<' + tag;
      var re = /([^\s"'<>\/=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?/g;
      var a;
      while ((a = re.exec(m[3]))) {
        var name = a[1].toLowerCase();
        if (allowed.indexOf(name) === -1) continue;
        var val = a[2] !== undefined ? a[2] : a[3] !== undefined ? a[3] : a[4];
        if (val === undefined) { out += ' ' + name; continue; }
        if (URL_ATTRS.has(name) && unsafeUrl(val)) val = '#';
        out += ' ' + name + '="' + attrEsc(val) + '"';
      }
      if (tag === 'a') out += ' rel="noopener noreferrer"';
      return out + '>';
    }).join('');
  }

  function safeUrl(u, env) {
    u = u.trim().replace(/ /g, '%20');
    if (env.opts.sanitizeUrls && unsafeUrl(u)) return '#';
    return esc(u);
  }

  var DEST = '(<[^<>\\n]*>|(?:[^\\s()\\\\]|\\\\.|\\((?:[^\\s()\\\\]|\\\\.)*\\))*)';
  var TITLE = '(?:\\s+("(?:[^"\\\\]|\\\\.)*"|\'(?:[^\'\\\\]|\\\\.)*\'|\\((?:[^()\\\\]|\\\\.)*\\)))?';
  var TXT = '((?:[^\\[\\]\\\\]|\\\\.|\\[(?:[^\\[\\]\\\\]|\\\\.)*\\])*)';
  var TAIL = '\\(\\s*' + DEST + TITLE + '(?:\\s+=(\\d*)x(\\d*))?\\s*\\)';
  var IMG_RE = new RegExp('!\\[' + TXT + '\\]' + TAIL, 'g');
  var LINK_RE = new RegExp('\\[' + TXT + '\\]' + TAIL, 'g');

  function inline(src, env) {
    var ctx = { stash: [] };
    return restore(inl(src, env, ctx, false), ctx.stash);
  }
  function restore(s, stash) {
    return s.replace(/\u0000(\d+)\u0000/g, function (m, k) { return restore(stash[k][0], stash); });
  }

  function inl(s, env, ctx, inLink) {
    var o = env.opts;
    function put(html, raw) {
      ctx.stash.push([html, raw]);
      return '\u0000' + (ctx.stash.length - 1) + '\u0000';
    }
    function rawText(t) {
      return t.replace(/\u0000(\d+)\u0000/g, function (m, k) {
        var e = ctx.stash[k];
        return e[1] !== undefined ? e[1] : e[0].replace(/<[^>]+>/g, '');
      });
    }
    function mkLink(dest, innerHtml, title) {
      dest = rawText(dest).trim();
      if (dest.charAt(0) === '<' && dest.charAt(dest.length - 1) === '>') dest = dest.slice(1, -1);
      var href = safeUrl(dest, env);
      var t = title ? rawText(title).slice(1, -1) : '';
      var ext = /^(?:https?:)?\/\//i.test(dest);
      return '<a href="' + href + '"' + (t ? ' title="' + esc(t) + '"' : '') +
        (ext && o.externalLinks ? ' target="_blank" rel="noopener noreferrer"' : '') + '>' + innerHtml + '</a>';
    }
    function mkImg(alt, dest, title, w, h) {
      dest = rawText(dest).trim();
      if (dest.charAt(0) === '<' && dest.charAt(dest.length - 1) === '>') dest = dest.slice(1, -1);
      var t = title ? rawText(title).slice(1, -1) : '';
      return '<img src="' + safeUrl(dest, env) + '" alt="' + esc(rawText(alt)) + '"' + (t ? ' title="' + esc(t) + '"' : '') +
        (w ? ' width="' + w + '"' : '') + (h ? ' height="' + h + '"' : '') + ' loading="lazy">';
    }

    // 1. code spans (the leading group replaces a lookbehind on the backtick)
    s = s.replace(/(^|[^`])(`+)(?!`)([\s\S]*?[^`])\2(?!`)/g, function (m, pre, ticks, c) {
      c = c.replace(/\n/g, ' ');
      if (/^ .* $/.test(c) && /[^ ]/.test(c)) c = c.slice(1, -1);
      return pre + put('<code>' + esc(c) + '</code>', c);
    });

    // 2. math ($$..$$ and $..$), before backslash escapes so \{ \\ survive
    s = s.replace(/(^|[^\\])\$\$([^\n]+?)\$\$/g, function (m, pre, body) {
      return pre + put('<span class="math math-display">\\[' + esc(body.trim()) + '\\]</span>');
    });
    s = s.replace(/(^|[^\\$\w])\$(?![\s$])((?:[^$\n\\]|\\.)*?[^\s\\$])\$(?![\w$])/g, function (m, pre, body) {
      return pre + put('<span class="math math-inline">\\(' + esc(body) + '\\)</span>');
    });

    // 3. backslash escapes
    s = s.replace(/\\([!-\/:-@\[-`{-~])/g, function (m, c) { return put(esc(c), c); });

    // 4. <autolinks>
    s = s.replace(/<((?:https?|ftp):\/\/[^\s<>]+)>/gi, function (m, u) { return put(mkLink(u, esc(u))); });
    s = s.replace(/<(?:mailto:)?([\w.+-]+@[\w-]+(?:\.[\w-]+)+)>/g, function (m, e) {
      return put('<a href="mailto:' + esc(e) + '">' + esc(e) + '</a>');
    });

    // 5. inline HTML
    if (o.html) {
      s = s.replace(/<!--[\s\S]*?-->|<\/?[A-Za-z][^<>]*>/g, function (t) {
        return put(o.html === true ? t : sanitizeHtml(t));
      });
    }

    // 6. images (inline, then reference-style)
    s = s.replace(IMG_RE, function (m, alt, dest, title, w, h) { return put(mkImg(alt, dest, title, w, h)); });
    s = s.replace(/!\[([^\[\]]*)\](?:\[([^\[\]]*)\])?/g, function (m, alt, ref) {
      var d = env.refs[normKey(ref || alt)];
      return d ? put(mkImg(alt, d.url, d.title ? '"' + d.title + '"' : '', '', '')) : m;
    });

    // 7. footnote references [^id] and inline footnotes ^[text]
    s = s.replace(/\^\[((?:[^\[\]\\]|\\.|\[[^\]]*\])+)\]|\[\^([^\]\s]+)\]/g, function (m, text, ref) {
      if (text !== undefined) {
        var id = 'inline-' + (++env.inlineFn);
        env.fns[id] = [rawText(text)];
        return put(footnoteRef(id, env));
      }
      return env.fns[ref] ? put(footnoteRef(ref, env)) : m;
    });

    // 8. wiki links
    if (o.wikiLinks && !inLink) {
      s = s.replace(/\[\[([^\[\]|\n]+?)(?:\|([^\[\]\n]+?))?\]\]/g, function (m, page, label) {
        return put('<a class="wikilink" href="' + esc(o.wikiLinkBase + encodeURI(rawText(page).trim())) + '">' +
          esc(rawText(label || page)) + '</a>');
      });
    }

    // 9. links: inline, reference, collapsed/shortcut
    s = s.replace(LINK_RE, function (m, text, dest, title) {
      return put(mkLink(dest, inl(text, env, ctx, true), title));
    });
    s = s.replace(/\[((?:[^\[\]\\]|\\.)+)\]\[([^\[\]]*)\]/g, function (m, text, ref) {
      var d = env.refs[normKey(ref || rawText(text))];
      return d ? put(mkLink(d.url, inl(text, env, ctx, true), d.title ? '"' + d.title + '"' : '')) : m;
    });
    s = s.replace(/\[((?:[^\[\]\\]|\\.)+)\](?!\()/g, function (m, text) {
      var d = env.refs[normKey(rawText(text))];
      return d ? put(mkLink(d.url, inl(text, env, ctx, true), d.title ? '"' + d.title + '"' : '')) : m;
    });

    // 10. bare URLs / e-mails (GFM extended autolinks)
    if (o.autolink && !inLink) {
      s = s.replace(/(^|[\s(>*_~])((?:https?:\/\/|www\.)[^\s<\u0000]+)/g, function (m, pre, url) {
        var tail = '';
        for (;;) {
          var c = url.charAt(url.length - 1);
          if (c && /[.,:;"'!?*_~]/.test(c)) { tail = c + tail; url = url.slice(0, -1); }
          else if (c === ')' && (url.match(/\(/g) || []).length < (url.match(/\)/g) || []).length) {
            tail = c + tail; url = url.slice(0, -1);
          } else break;
        }
        if (!/\w\./.test(url) && !/^https?:\/\/\w/.test(url)) return m;
        var href = /^www\./i.test(url) ? 'http://' + url : url;
        return pre + put(mkLink(href, esc(url))) + tail;
      });
      s = s.replace(/(^|[\s(])([\w.+-]+@[\w-]+(?:\.[\w-]+)*\.[A-Za-z]{2,})(?![\w@])/g, function (m, pre, e) {
        return pre + put('<a href="mailto:' + esc(e) + '">' + esc(e) + '</a>');
      });
    }

    // 11. escape what's left (keep valid entities)
    s = esc(s).replace(/&amp;(#\d+|#[xX][\da-fA-F]+|[A-Za-z][A-Za-z\d]*);/g, '&$1;');

    // 12. typographer
    if (o.typographer) {
      s = s.replace(/\(c\)/gi, '©').replace(/\(r\)/gi, '®').replace(/\(tm\)/gi, '™').replace(/\.{3}/g, '…')
        .replace(/(^|[^-])---(?!-)/g, '$1—').replace(/(^|[^-])--(?!-)/g, '$1–')
        .replace(/(^|[\s(\[{>])&quot;(?=\S)/g, '$1“').replace(/&quot;/g, '”')
        .replace(/(^|[\s(\[{>])'(?=\S)/g, '$1‘').replace(/'/g, '’');
    }

    // 13. emoji shortcodes
    if (o.emoji) s = s.replace(/:([a-z0-9_+-]+):/g, function (m, k) { return EMOJI[k] || m; });

    // 14. abbreviations (*[HTML]: HyperText Markup Language)
    var abbrs = Object.keys(env.abbrs);
    if (abbrs.length) {
      var re = new RegExp('(^|[^\\w&#\\u0000])(' +
        abbrs.sort(function (a, b) { return b.length - a.length; }).map(escRe).join('|') +
        ')(?![\\w;\\u0000])', 'g');
      s = s.replace(re, function (m, pre, k) {
        return pre + put('<abbr title="' + esc(env.abbrs[k]) + '">' + esc(k) + '</abbr>');
      });
    }

    // 15. emphasis family
    s = s.replace(/~~(?=\S)([\s\S]*?\S)~~/g, '<del>$1</del>');
    s = s.replace(/(^|[^\w=])==(?=\S)([\s\S]*?\S)==(?![\w=])/g, '$1<mark>$2</mark>');
    s = s.replace(/(^|[^\w+])\+\+(?=\S)([\s\S]*?\S)\+\+(?![\w+])/g, '$1<ins>$2</ins>');
    if (o.subSup) {
      s = s.replace(/(^|[^~\\])~([^\s~]+)~(?!~)/g, '$1<sub>$2</sub>');
      s = s.replace(/(^|[^\^])\^([^\s^]+)\^(?!\^)/g, '$1<sup>$2</sup>');
    }
    s = s.replace(/(^|[^\w*])\*\*\*(?=\S)([\s\S]*?\S)\*\*\*(?!\*)/g, '$1<strong><em>$2</em></strong>');
    s = s.replace(/(^|[^\w])___(?=\S)([\s\S]*?\S)___(?!\w)/g, '$1<strong><em>$2</em></strong>');
    s = s.replace(/\*\*(?=\S)([\s\S]*?\S)\*\*(?!\*)/g, '<strong>$1</strong>');
    s = s.replace(/(^|[^\w])__(?=\S)([\s\S]*?\S)__(?!\w)/g, '$1<strong>$2</strong>');
    s = s.replace(/\*(?=\S)([\s\S]*?\S)\*/g, '<em>$1</em>');
    s = s.replace(/(^|[^\w])_(?=\S)([\s\S]*?\S)_(?!\w)/g, '$1<em>$2</em>');

    // 16. line breaks
    s = s.replace(/(?: {2,}|\\)\n/g, '<br>\n').replace(/[ \t]+\n/g, '\n');
    if (o.breaks) {
      s = s.replace(/(<br>)?\n/g, function (m, br) { return br ? m : '<br>\n'; });
    }
    return s;
  }

  function footnoteRef(id, env) {
    if (env.fnOrder.indexOf(id) === -1) env.fnOrder.push(id);
    var num = env.fnOrder.indexOf(id) + 1;
    var cnt = env.fnRefs[id] = (env.fnRefs[id] || 0) + 1;
    var fid = encodeURIComponent(id);
    return '<sup class="footnote-ref"><a href="#fn-' + fid + '" id="fnref-' + fid +
      (cnt > 1 ? '-' + cnt : '') + '">' + num + '</a></sup>';
  }

  function interrupts(ln, env) {
    return /^ {0,3}(?:`{3,}|~{3,})/.test(ln) || /^ {0,3}#{1,6}(?:[ \t]|$)/.test(ln) || HR_RE.test(ln) ||
      /^ {0,3}>/.test(ln) || /^ {0,3}(?:[-*+]|1[.)])[ \t]+\S/.test(ln) || /^ {0,3}\$\$[ \t]*$/.test(ln) ||
      /^:{3,}[ \t]*[\w-]+/.test(ln) || (env.opts.html && HTML_BLOCK_RE.test(ln));
  }

  function slugify(html, env) {
    // No \p{L}\p{N} (Chrome 64): \w keeps ASCII, the range keeps Latin-1/CJK.
    var base = html.replace(/<[^>]+>/g, '').replace(/&[#\w]+;/g, '').toLowerCase().trim()
      .replace(/[^\w\s\u00c0-\uffff-]/g, '').replace(/\s/g, '-') || 'section';
    if (env.slugs[base] === undefined) { env.slugs[base] = 0; return base; }
    return base + '-' + (++env.slugs[base]);
  }

  function heading(level, raw, env) {
    var id = '', classes = [];
    var am = /[ \t]*\{([^{}]*)\}[ \t]*$/.exec(raw);
    if (am && /^[\s#.\w:-]+$/.test(am[1]) && /[#.]/.test(am[1])) {
      am[1].trim().split(/\s+/).forEach(function (t) {
        if (t.charAt(0) === '#') id = t.slice(1);
        else if (t.charAt(0) === '.') classes.push(t.slice(1));
      });
      raw = raw.slice(0, am.index);
    }
    var inner = inline(raw.trim(), env);
    if (!id && env.opts.headingIds) id = slugify(inner, env);
    if (id) env.toc.push({ level: level, id: id, text: inner });
    var anchor = id && env.opts.headingAnchors
      ? '<a class="anchor" href="#' + esc(id) + '" aria-hidden="true">#</a>' : '';
    return '<h' + level + (id ? ' id="' + esc(id) + '"' : '') +
      (classes.length ? ' class="' + esc(classes.join(' ')) + '"' : '') + '>' +
      anchor + inner + '</h' + level + '>';
  }

  function codeBlock(code, info, env) {
    var lang = (info.split(/\s+/)[0] || '').replace(/^\{?\.?|\}$/g, '');
    if (lang === 'mermaid') return '<pre class="mermaid">' + esc(code) + '</pre>';
    if (/^(math|latex|tex)$/i.test(lang)) return '<div class="math math-display">\\[' + esc(code) + '\\]</div>';
    var body = env.opts.highlight ? env.opts.highlight(code, lang) : null;
    if (body == null) body = esc(code);
    return '<pre><code' + (lang ? ' class="language-' + esc(lang) + '"' : '') + '>' + body + '\n</code></pre>';
  }

  function splitRow(s) {
    s = s.trim();
    if (s.charAt(0) === '|') s = s.slice(1);
    if (/(^|[^\\])\|$/.test(s)) s = s.slice(0, -1);
    var cells = [], cur = '';
    for (var k = 0; k < s.length; k++) {
      var ch = s.charAt(k);
      if (ch === '\\' && s.charAt(k + 1) === '|') { cur += '|'; k++; }
      else if (ch === '|') { cells.push(cur.trim()); cur = ''; }
      else cur += ch;
    }
    cells.push(cur.trim());
    return cells;
  }

  function parseList(lines, i, env) {
    var n = lines.length;
    var first = LIST_RE.exec(lines[i]);
    var ordered = /\d/.test(first[2]);
    var delim = first[2].slice(-1);
    var start = ordered ? parseInt(first[2], 10) : 1;
    var items = [];
    var loose = false, blankBefore = false;

    while (i < n) {
      var m = LIST_RE.exec(lines[i]);
      if (!m || /\d/.test(m[2]) !== ordered || m[2].slice(-1) !== delim || HR_RE.test(lines[i])) break;
      if (blankBefore && items.length) loose = true;
      blankBefore = false;
      var rest = lines[i].slice(m[0].length), spaces = m[3].length;
      if (isBlank(rest)) { spaces = 1; rest = ''; }
      else if (spaces > 4) { rest = repeat(' ', spaces - 1) + rest; spaces = 1; }
      var ci = m[1].length + m[2].length + spaces;
      var body = [rest];
      i++;
      while (i < n) {
        var ln = lines[i];
        if (isBlank(ln)) {
          var j = i;
          while (j < n && isBlank(lines[j])) j++;
          if (j < n && indentOf(lines[j]) >= ci) { for (; i < j; i++) body.push(''); continue; }
          blankBefore = j < n;
          i = j;
          break;
        }
        if (indentOf(ln) >= ci) { body.push(ln.slice(ci)); i++; continue; }
        if (LIST_RE.test(ln)) break;
        var last = body[body.length - 1];
        if (last.trim() !== '' && !interrupts(ln, env)) { body.push(ln.trim()); i++; continue; }
        break;
      }
      items.push(body);
    }

    items.forEach(function (body) {
      var inFence = false;
      for (var k = 0; k < body.length - 1; k++) {
        if (/^ *(?:`{3,}|~{3,})/.test(body[k])) inFence = !inFence;
        if (!inFence && body[k] === '' && body[k + 1] !== '' && indentOf(body[k + 1]) === 0 && !LIST_RE.test(body[k + 1])) loose = true;
      }
    });

    var hasTask = false;
    var lis = items.map(function (body) {
      var tm = /^\[([ xX])\][ \t]+/.exec(body[0]);
      var task = '';
      if (tm) {
        hasTask = true;
        body[0] = body[0].slice(tm[0].length);
        task = '<input type="checkbox" disabled' + (tm[1] !== ' ' ? ' checked' : '') + '> ';
      }
      var inner = parseBlocks(body, env, { tight: !loose });
      if (task) inner = loose && startsWith(inner, '<p>') ? inner.replace(/^<p>/, '<p>' + task) : task + inner;
      return '<li' + (tm ? ' class="task-list-item"' : '') + '>' + inner + '</li>';
    });

    var tag = ordered ? 'ol' : 'ul';
    var attrs = (ordered && start !== 1 ? ' start="' + start + '"' : '') +
      (hasTask ? ' class="contains-task-list"' : '');
    return ['<' + tag + attrs + '>\n' + lis.join('\n') + '\n</' + tag + '>', i];
  }

  function parseBlocks(lines, env, o) {
    o = o || {};
    var out = [];
    var n = lines.length;
    var i = 0;

    while (i < n) {
      var line = lines[i];
      if (isBlank(line)) { i++; continue; }
      var m;

      // fenced code
      if ((m = /^( {0,3})(`{3,}|~{3,})[ \t]*(.*)$/.exec(line)) &&
          !(m[2].charAt(0) === '`' && includes(m[3], '`'))) {
        var fence = m[2], ind = m[1].length, info = m[3].trim();
        var closeRe = new RegExp('^ {0,3}' + fence.charAt(0) + '{' + fence.length + ',}[ \\t]*$');
        var stripRe = new RegExp('^ {0,' + ind + '}');
        var fenced = [];
        i++;
        while (i < n && !closeRe.test(lines[i])) { fenced.push(lines[i].replace(stripRe, '')); i++; }
        i++;
        out.push(codeBlock(fenced.join('\n'), info, env));
        continue;
      }

      // indented code
      if (/^ {4}/.test(line)) {
        var indented = [];
        while (i < n) {
          if (/^ {4}/.test(lines[i])) { indented.push(lines[i].slice(4)); i++; }
          else if (isBlank(lines[i])) {
            var jb = i;
            while (jb < n && isBlank(lines[jb])) jb++;
            if (jb < n && /^ {4}/.test(lines[jb])) { for (; i < jb; i++) indented.push(''); }
            else break;
          } else break;
        }
        out.push(codeBlock(indented.join('\n'), '', env));
        continue;
      }

      // ATX heading
      if ((m = /^ {0,3}(#{1,6})(?:[ \t]+(.*?))?(?:[ \t]+#+)?[ \t]*$/.exec(line))) {
        out.push(heading(m[1].length, m[2] || '', env));
        i++;
        continue;
      }

      // horizontal rule
      if (HR_RE.test(line)) { out.push('<hr>'); i++; continue; }

      // display math block
      if ((m = /^ {0,3}\$\$(.*)$/.exec(line)) && (!includes(m[1], '$$') || /^(.*?)\$\$[ \t]*$/.test(m[1]))) {
        var mathBody = [];
        var one = /^(.*?)\$\$[ \t]*$/.exec(m[1]);
        i++;
        if (one) mathBody.push(one[1]);
        else {
          if (m[1].trim()) mathBody.push(m[1]);
          while (i < n) {
            var e = /^(.*?)\$\$[ \t]*$/.exec(lines[i]);
            if (e) { if (e[1].trim()) mathBody.push(e[1]); i++; break; }
            mathBody.push(lines[i]);
            i++;
          }
        }
        out.push('<div class="math math-display">\\[' + esc(mathBody.join('\n').trim()) + '\\]</div>');
        continue;
      }

      // ::: containers (admonitions, details, generic divs)
      if ((m = /^:{3,}[ \t]*([\w-]+)[ \t]*(.*?)[ \t]*$/.exec(line))) {
        var type = m[1].toLowerCase(), title = m[2];
        var cbody = [];
        var depth = 1;
        i++;
        while (i < n) {
          if (/^:{3,}[ \t]*[\w-]+/.test(lines[i])) depth++;
          else if (/^:{3,}[ \t]*$/.test(lines[i]) && --depth === 0) break;
          cbody.push(lines[i]);
          i++;
        }
        i++;
        var cinner = parseBlocks(cbody, env);
        var ct = resolveCallout(type);
        if (ct) out.push(renderAlert(ct, title ? inline(title, env) : CALLOUTS[ct][0], cinner, ''));
        else if (type === 'details' || type === 'spoiler') {
          out.push('<details><summary>' + inline(title || 'Details', env) + '</summary>' + cinner + '</details>');
        } else {
          out.push('<div class="container container-' + esc(type) + '">' +
            (title ? '<p class="container-title">' + inline(title, env) + '</p>' : '') + cinner + '</div>');
        }
        continue;
      }

      // [TOC]
      if (/^[ \t]*(?:\[TOC\]|\[\[_?toc_?\]\])[ \t]*$/i.test(line)) { out.push('<!--TOC-->'); i++; continue; }

      // raw HTML block
      if (env.opts.html && HTML_BLOCK_RE.test(line)) {
        var buf = [];
        if (/^ {0,3}<!--/.test(line)) {
          while (i < n) { buf.push(lines[i]); if (/-->/.test(lines[i])) { i++; break; } i++; }
        } else if (/^ {0,3}<pre[\s>]/i.test(line)) {
          while (i < n) { buf.push(lines[i]); if (/<\/pre>/i.test(lines[i])) { i++; break; } i++; }
        } else {
          while (i < n && !isBlank(lines[i])) buf.push(lines[i++]);
        }
        var raw = buf.join('\n');
        out.push(env.opts.html === true ? raw : sanitizeHtml(raw));
        continue;
      }

      // blockquote / GitHub alerts / Obsidian callouts
      if (/^ {0,3}>/.test(line)) {
        var q = [];
        while (i < n) {
          var qln = lines[i];
          if (/^ {0,3}>/.test(qln)) { q.push(qln.replace(/^ {0,3}> ?/, '')); i++; }
          else if (!isBlank(qln) && q.length && !isBlank(q[q.length - 1]) &&
            !/^(?:#{1,6}\s|```|~~~)/.test(q[q.length - 1]) && !interrupts(qln, env)) { q.push(qln); i++; }
          else break;
        }
        var am = /^\[!([A-Za-z]+)\]([+-]?)[ \t]*(.*)$/.exec(q[0]);
        var btype = am && resolveCallout(am[1]);
        if (btype) {
          var gh = GITHUB_TYPES.has(btype) && !ALIASES[am[1].toLowerCase()] && !am[2];
          var bbody = q.slice(1);
          var btitle = CALLOUTS[btype][0];
          if (am[3]) { if (gh) bbody.unshift(am[3]); else btitle = null; }
          out.push(renderAlert(btype, btitle ? esc(btitle) : inline(am[3], env), parseBlocks(bbody, env), am[2]));
        } else {
          out.push('<blockquote>\n' + parseBlocks(q, env) + '\n</blockquote>');
        }
        continue;
      }

      // lists
      if (LIST_RE.test(line)) {
        var list = parseList(lines, i, env);
        out.push(list[0]);
        i = list[1];
        continue;
      }

      // tables
      if (includes(line, '|') && i + 1 < n && includes(lines[i + 1], '|') && TABLE_DELIM_RE.test(lines[i + 1])) {
        var head = splitRow(line), delim = splitRow(lines[i + 1]);
        if (head.length === delim.length) {
          var align = delim.map(function (c) {
            if (c.charAt(0) === ':' && c.charAt(c.length - 1) === ':') return 'center';
            if (c.charAt(c.length - 1) === ':') return 'right';
            if (c.charAt(0) === ':') return 'left';
            return '';
          });
          var cell = function (tag, txt, k) {
            return '<' + tag + (align[k] ? ' style="text-align:' + align[k] + '"' : '') + '>' +
              inline(txt, env) + '</' + tag + '>';
          };
          var thtml = '<table>\n<thead>\n<tr>' + head.map(function (c, k) { return cell('th', c, k); }).join('') +
            '</tr>\n</thead>\n';
          i += 2;
          var rows = [];
          while (i < n && !isBlank(lines[i]) && includes(lines[i], '|') && !interrupts(lines[i], env)) {
            var cells = splitRow(lines[i]);
            while (cells.length < head.length) cells.push('');
            rows.push('<tr>' + cells.slice(0, head.length).map(function (c, k) { return cell('td', c, k); }).join('') + '</tr>');
            i++;
          }
          if (rows.length) thtml += '<tbody>\n' + rows.join('\n') + '\n</tbody>\n';
          out.push('<div class="table-wrapper">' + thtml + '</table></div>');
          continue;
        }
      }

      // definition lists
      if (i + 1 < n && DD_RE.test(lines[i + 1]) && !DD_RE.test(line)) {
        var dl = '<dl>';
        while (i + 1 < n && DD_RE.test(lines[i + 1])) {
          dl += '<dt>' + inline(lines[i].trim(), env) + '</dt>';
          i++;
          while (i < n && DD_RE.test(lines[i])) {
            var dd = [lines[i].replace(/^ {0,3}:[ \t]+/, '')];
            i++;
            while (i < n && !isBlank(lines[i]) && !DD_RE.test(lines[i]) && /^ {2,}\S/.test(lines[i])) {
              dd.push(lines[i++].trim());
            }
            dl += '<dd>' + inline(dd.join('\n'), env) + '</dd>';
          }
          var jn = i;
          while (jn < n && isBlank(lines[jn])) jn++;
          if (jn + 1 < n && !DD_RE.test(lines[jn]) && DD_RE.test(lines[jn + 1])) i = jn;
          else break;
        }
        out.push(dl + '</dl>');
        continue;
      }

      // paragraph (+ setext headings)
      var pbuf = [line];
      i++;
      var setext = 0;
      while (i < n) {
        var pln = lines[i];
        if (isBlank(pln)) break;
        if (/^ {0,3}=+[ \t]*$/.test(pln)) { setext = 1; i++; break; }
        if (/^ {0,3}-+[ \t]*$/.test(pln)) { setext = 2; i++; break; }
        if (interrupts(pln, env)) break;
        pbuf.push(pln);
        i++;
      }
      var text = pbuf.map(function (s) { return s.replace(/^\s+/, ''); }).join('\n').replace(/\s+$/, '');
      if (setext) {
        out.push(heading(setext, text.replace(/\n/g, ' '), env));
      } else {
        var h = inline(text, env);
        out.push(o.tight ? h : '<p>' + h + '</p>');
      }
    }
    return out.join('\n');
  }

  function preprocess(md, env) {
    md = String(md).replace(/\r\n?/g, '\n').replace(/\u0000/g, '');
    var meta = {};
    var fm = /^---[ \t]*\n([\s\S]*?)\n(?:---|\.\.\.)[ \t]*(?:\n|$)/.exec(md);
    if (fm) {
      fm[1].split('\n').forEach(function (l) {
        var kv = /^([\w-]+):[ \t]*(.*)$/.exec(l);
        if (kv) meta[kv[1]] = kv[2].replace(/^(["'])(.*)\1$/, '$2');
      });
      md = md.slice(fm[0].length);
    }
    var src = md.split('\n').map(function (l) {
      return l.replace(/^[ \t]+/, function (ws) { return ws.replace(/\t/g, '    '); });
    });
    var out = [];
    var fence = null;
    for (var i = 0; i < src.length; i++) {
      var l = src[i];
      var fm2 = /^ {0,3}(`{3,}|~{3,})/.exec(l);
      if (fence) {
        out.push(l);
        if (fm2 && fm2[1].charAt(0) === fence.charAt(0) && fm2[1].length >= fence.length &&
          /^ {0,3}[`~]+[ \t]*$/.test(l)) fence = null;
        continue;
      }
      if (fm2) { fence = fm2[1]; out.push(l); continue; }
      var m;
      if ((m = /^ {0,3}\[\^([^\]\s]+)\]:[ \t]*(.*)$/.exec(l))) {
        var body = [m[2]];
        while (i + 1 < src.length) {
          var nx = src[i + 1];
          if (/^ {2,}\S/.test(nx)) { body.push(nx.replace(/^ {1,4}/, '')); i++; }
          else if (isBlank(nx) && i + 2 < src.length && /^ {2,}\S/.test(src[i + 2])) { body.push(''); i++; }
          else break;
        }
        env.fns[m[1]] = body;
        continue;
      }
      if ((m = /^\*\[([^\]]+)\]:[ \t]*(.+?)[ \t]*$/.exec(l))) { env.abbrs[m[1]] = m[2]; continue; }
      if ((m = /^ {0,3}\[((?:[^\]\\]|\\.)+)\]:[ \t]*(<[^>]*>|\S+)(?:[ \t]+("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\([^)]*\)))?[ \t]*$/.exec(l)) && m[1].charAt(0) !== '^') {
        var key = normKey(m[1]);
        if (!env.refs[key]) {
          env.refs[key] = { url: m[2].replace(/^<|>$/g, ''), title: m[3] ? m[3].slice(1, -1) : '' };
        }
        continue;
      }
      out.push(l);
    }
    return { lines: out, meta: meta };
  }

  function buildToc(items) {
    if (!items.length) return '';
    var base = Math.min.apply(null, items.map(function (t) { return t.level; }));
    var out = '', depth = -1;
    for (var idx = 0; idx < items.length; idx++) {
      var it = items[idx];
      var d = Math.min(Math.max(it.level - base, 0), depth + 1);
      if (d > depth) { out += '<ul>'; depth++; }
      else { while (depth > d) { out += '</li></ul>'; depth--; } out += '</li>'; }
      out += '<li><a href="#' + esc(it.id) + '">' + it.text.replace(/<a [^>]*>#<\/a>/g, '') + '</a>';
    }
    while (depth >= 0) { out += '</li></ul>'; depth--; }
    return '<nav class="toc">' + out + '</nav>';
  }

  function parseMarkdown(md, options) {
    var opts = extend({}, DEFAULTS, options);
    var env = {
      opts: opts, refs: {}, abbrs: {}, fns: {}, fnOrder: [], fnRefs: {}, toc: [], slugs: {}, inlineFn: 0
    };
    var pre = preprocess(md, env);
    var html = parseBlocks(pre.lines, env);

    if (env.fnOrder.length) {
      var items = '';
      for (var k = 0; k < env.fnOrder.length; k++) {
        var id = env.fnOrder[k], fid = encodeURIComponent(id);
        var body = parseBlocks(env.fns[id], env);
        var back = '<a href="#fnref-' + fid + '" class="footnote-backref" aria-label="Back to reference ' +
          (k + 1) + '">↩</a>';
        body = /<\/p>\s*$/.test(body) ? body.replace(/<\/p>\s*$/, ' ' + back + '</p>') : body + ' ' + back;
        items += '<li id="fn-' + fid + '">' + body + '</li>\n';
      }
      html += '\n<section class="footnotes"><hr><ol>\n' + items + '</ol></section>';
    }
    var toc = buildToc(env.toc);
    html = html.replace(/<!--TOC-->/g, function () { return toc; });
    return { html: html, toc: env.toc, meta: pre.meta };
  }

  function renderMarkdown(md, options) { return parseMarkdown(md, options).html; }
  renderMarkdown.parse = parseMarkdown;
  renderMarkdown.defaults = DEFAULTS;
  renderMarkdown.css = [
    '',
    '.markdown-alert{border-left:.25em solid #4493f8;padding:.5rem 1rem;margin:0 0 1rem;border-radius:0 6px 6px 0}',
    '.markdown-alert>:last-child{margin-bottom:0}',
    '.markdown-alert-title{display:flex;align-items:center;margin:0 0 .5rem;font-weight:600;color:#4493f8}',
    'details.markdown-alert>summary{cursor:pointer;margin-bottom:.25rem}',
    '.markdown-alert-title svg{width:16px;height:16px;flex:none;margin-right:.5rem;fill:none;stroke:currentColor;stroke-width:1.5;stroke-linecap:round;stroke-linejoin:round}',
    '.markdown-alert-tip,.markdown-alert-success,.markdown-alert-todo{border-left-color:#3fb950}',
    '.markdown-alert-tip>.markdown-alert-title,.markdown-alert-success>.markdown-alert-title,.markdown-alert-todo>.markdown-alert-title{color:#3fb950}',
    '.markdown-alert-important,.markdown-alert-example{border-left-color:#ab7df8}',
    '.markdown-alert-important>.markdown-alert-title,.markdown-alert-example>.markdown-alert-title{color:#ab7df8}',
    '.markdown-alert-warning,.markdown-alert-question{border-left-color:#d29922}',
    '.markdown-alert-warning>.markdown-alert-title,.markdown-alert-question>.markdown-alert-title{color:#d29922}',
    '.markdown-alert-caution,.markdown-alert-danger,.markdown-alert-failure,.markdown-alert-bug{border-left-color:#f85149}',
    '.markdown-alert-caution>.markdown-alert-title,.markdown-alert-danger>.markdown-alert-title,.markdown-alert-failure>.markdown-alert-title,.markdown-alert-bug>.markdown-alert-title{color:#f85149}',
    '.markdown-alert-quote{border-left-color:#9198a1}',
    '.markdown-alert-quote>.markdown-alert-title{color:#9198a1}',
    '.table-wrapper{overflow-x:auto;margin:0 0 1rem}',
    'table{border-collapse:collapse}th,td{border:1px solid rgba(136,136,136,.27);padding:.4em .75em}thead th{background:rgba(136,136,136,.07)}',
    '.contains-task-list,.task-list-item{list-style:none}.task-list-item input{margin:0 .4em 0 -1.4em}',
    '.footnotes{font-size:.9em}.footnote-backref{text-decoration:none}',
    '.heading .anchor,h1 .anchor,h2 .anchor,h3 .anchor,h4 .anchor,h5 .anchor,h6 .anchor{margin-left:-1em;padding-right:.3em;opacity:0;text-decoration:none}',
    'h1:hover .anchor,h2:hover .anchor,h3:hover .anchor,h4:hover .anchor,h5:hover .anchor,h6:hover .anchor{opacity:.6}',
    'mark{padding:0 .15em}abbr[title]{text-decoration:underline dotted;cursor:help}',
    '.toc ul{margin:0}.math-display{overflow-x:auto;text-align:center}',
    'blockquote{margin:0 0 1rem;padding:0 1em;border-left:.25em solid rgba(136,136,136,.4);opacity:.85}',
    'pre{overflow-x:auto;padding:1rem;background:rgba(136,136,136,.07);border-radius:6px}',
    ''
  ].join('\n');

  if (typeof module !== 'undefined' && module.exports) module.exports = renderMarkdown;
  global.renderMarkdown = renderMarkdown;
})(typeof globalThis !== 'undefined' ? globalThis : this);
