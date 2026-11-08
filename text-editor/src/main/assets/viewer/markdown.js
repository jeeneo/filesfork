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
 */
(function (global) {
  'use strict';

  const DEFAULTS = {
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
    highlight: null,
  };

  const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const escRe = s => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const isBlank = s => !/\S/.test(s);
  const normKey = s => s.trim().replace(/\s+/g, ' ').toLowerCase();
  const indentOf = s => { let k = 0; while (s[k] === ' ') k++; return k; };

  const HR_RE = /^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$/;
  const LIST_RE = /^( {0,3})([-*+]|\d{1,9}[.)])( +|$)/;
  const DD_RE = /^ {0,3}:[ \t]+\S/;
  const TABLE_DELIM_RE = /^ {0,3}\|?[ \t]*:?-+:?[ \t]*(?:\|[ \t]*:?-+:?[ \t]*)*\|?[ \t]*$/;
  const HTML_BLOCK_RE = /^ {0,3}(?:<!--|<\/?(?:address|article|aside|blockquote|center|details|dialog|dd|div|dl|dt|fieldset|figcaption|figure|footer|form|h[1-6]|header|hr|iframe|li|main|nav|ol|p|pre|section|summary|table|tbody|td|tfoot|th|thead|tr|ul)(?:[\s/>]|$))/i;

  // GitHub: NOTE TIP IMPORTANT WARNING CAUTION. The rest are Obsidian extras.
  const CALLOUTS = {
    note: ['Note', 'info'], tip: ['Tip', 'bulb'], important: ['Important', 'report'],
    warning: ['Warning', 'alert'], caution: ['Caution', 'stop'],
    info: ['Info', 'info'], abstract: ['Abstract', 'info'], todo: ['Todo', 'check'],
    success: ['Success', 'check'], question: ['Question', 'info'], failure: ['Failure', 'stop'],
    danger: ['Danger', 'stop'], bug: ['Bug', 'stop'], example: ['Example', 'report'], quote: ['Quote', 'report'],
  };
  const ALIASES = {
    summary: 'abstract', tldr: 'abstract', hint: 'tip', check: 'success', done: 'success',
    help: 'question', faq: 'question', attention: 'warning', fail: 'failure', missing: 'failure',
    error: 'danger', cite: 'quote',
  };
  const GITHUB_TYPES = new Set(['note', 'tip', 'important', 'warning', 'caution']);
  const resolveCallout = t => { t = t.toLowerCase(); t = ALIASES[t] || t; return CALLOUTS[t] ? t : null; };

  const ICON_PATHS = {
    info: '<circle cx="8" cy="8" r="6.5"/><path d="M8 7.2v4M8 4.6v.1"/>',
    bulb: '<path d="M6 12.2h4M6.6 14.2h2.8M8 1.8a4.4 4.4 0 0 0-2.4 8.1v1.3h4.8V9.9A4.4 4.4 0 0 0 8 1.8z"/>',
    report: '<path d="M2 2.5h12v8.5H9.2L6 14v-3H2z"/><path d="M8 5v2.4M8 9v.1"/>',
    alert: '<path d="M8 1.8l6.5 11.7h-13z"/><path d="M8 6.3v3.4M8 11.6v.1"/>',
    stop: '<path d="M5.2 1.8h5.6l3.4 3.4v5.6l-3.4 3.4H5.2L1.8 10.8V5.2z"/><path d="M8 4.8v3.8M8 10.8v.1"/>',
    check: '<circle cx="8" cy="8" r="6.5"/><path d="M5.2 8.2l2 2 3.6-4"/>',
  };
  const icon = k => '<svg class="octicon" viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">' + ICON_PATHS[k] + '</svg>';

  function renderAlert(type, titleHtml, body, fold) {
    const cls = 'markdown-alert markdown-alert-' + type;
    const head = icon(CALLOUTS[type][1]) + titleHtml;
    if (fold) {
      return '<details class="' + cls + '"' + (fold === '+' ? ' open' : '') +
        '><summary class="markdown-alert-title">' + head + '</summary>' + body + '</details>';
    }
    return '<div class="' + cls + '" role="note"><p class="markdown-alert-title">' + head + '</p>' + body + '</div>';
  }

  const EMOJI = {
    smile: '😄', grin: '😁', joy: '😂', rofl: '🤣', wink: '😉', sunglasses: '😎', heart_eyes: '😍', sweat_smile: '😅',
    cry: '😢', sob: '😭', thinking: '🤔', eyes: '👀', wave: '👋', clap: '👏', pray: '🙏', muscle: '💪',
    '+1': '👍', thumbsup: '👍', '-1': '👎', thumbsdown: '👎', point_right: '👉', '100': '💯',
    heart: '❤️', fire: '🔥', star: '⭐', sparkles: '✨', tada: '🎉', rocket: '🚀', zap: '⚡', coffee: '☕',
    sunny: '☀️', warning: '⚠️', white_check_mark: '✅', heavy_check_mark: '✔️', x: '❌', question: '❓', exclamation: '❗',
    information_source: 'ℹ️', bulb: '💡', bug: '🐛', memo: '📝', book: '📖', books: '📚', lock: '🔒', key: '🔑',
    link: '🔗', pushpin: '📌', package: '📦', wrench: '🔧', hammer: '🔨', construction: '🚧', art: '🎨',
  };

  const SAFE_TAGS = new Set(('a abbr b blockquote br caption cite code col colgroup dd del details dfn div dl dt em figcaption figure ' +
    'h1 h2 h3 h4 h5 h6 hr i img ins kbd li mark ol p pre q rp rt ruby s samp small span strong sub summary sup table tbody td tfoot ' +
    'th thead time tr u ul var wbr').split(' '));
  const VOID_TAGS = new Set(['br', 'hr', 'img', 'wbr', 'col']);
  const SAFE_ATTRS = {
    '*': ['title', 'lang', 'dir', 'align'],
    a: ['href', 'name'], img: ['src', 'alt', 'width', 'height'],
    td: ['colspan', 'rowspan'], th: ['colspan', 'rowspan', 'scope'], ol: ['start', 'type'],
    details: ['open'], time: ['datetime'], col: ['span'],
  };
  const URL_ATTRS = new Set(['href', 'src']);

  function unsafeUrl(u) {
    const c = u.replace(/[\u0000-\u0020\u007f-\u009f]/g, '');
    return /^(?:javascript|vbscript|file):/i.test(c) || /^data:(?!image\/(?:png|gif|jpe?g|webp|avif);)/i.test(c);
  }
  const attrEsc = s => s.replace(/&(?!#?\w+;)/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');

  function sanitizeHtml(src) {
    return src.split(/(<!--[\s\S]*?-->|<\/?[A-Za-z][^<>]*>)/).map((part, idx) => {
      if (idx % 2 === 0) return part.replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/&(?!#?\w+;)/g, '&amp;');
      if (part.startsWith('<!--')) return '';
      const m = /^<(\/?)([A-Za-z][\w-]*)([\s\S]*?)\/?>$/.exec(part);
      if (!m) return esc(part);
      const tag = m[2].toLowerCase();
      if (!SAFE_TAGS.has(tag)) return esc(part);
      if (m[1]) return VOID_TAGS.has(tag) ? '' : '</' + tag + '>';
      const allowed = (SAFE_ATTRS['*']).concat(SAFE_ATTRS[tag] || []);
      let out = '<' + tag;
      const re = /([^\s"'<>\/=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?/g;
      let a;
      while ((a = re.exec(m[3]))) {
        const name = a[1].toLowerCase();
        if (!allowed.includes(name)) continue;
        let val = a[2] !== undefined ? a[2] : a[3] !== undefined ? a[3] : a[4];
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

  const DEST = String.raw`(<[^<>\n]*>|(?:[^\s()\\]|\\.|\((?:[^\s()\\]|\\.)*\))*)`;
  const TITLE = String.raw`(?:\s+("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\((?:[^()\\]|\\.)*\)))?`;
  const TXT = String.raw`((?:[^\[\]\\]|\\.|\[(?:[^\[\]\\]|\\.)*\])*)`;
  const TAIL = String.raw`\(\s*` + DEST + TITLE + String.raw`(?:\s+=(\d*)x(\d*))?\s*\)`;
  const IMG_RE = new RegExp('!\\[' + TXT + '\\]' + TAIL, 'g');
  const LINK_RE = new RegExp('\\[' + TXT + '\\]' + TAIL, 'g');

  function inline(src, env) {
    const ctx = { stash: [] };
    return restore(inl(src, env, ctx, false), ctx.stash);
  }
  function restore(s, stash) {
    return s.replace(/\u0000(\d+)\u0000/g, (_, k) => restore(stash[k][0], stash));
  }

  function inl(s, env, ctx, inLink) {
    const o = env.opts;
    const put = (html, raw) => '\u0000' + (ctx.stash.push([html, raw]) - 1) + '\u0000';
    const rawText = t => t.replace(/\u0000(\d+)\u0000/g, (_, k) => {
      const e = ctx.stash[k]; return e[1] !== undefined ? e[1] : e[0].replace(/<[^>]+>/g, '');
    });
    const mkLink = (dest, innerHtml, title) => {
      dest = rawText(dest).trim();
      if (dest[0] === '<' && dest[dest.length - 1] === '>') dest = dest.slice(1, -1);
      const href = safeUrl(dest, env);
      const t = title ? rawText(title).slice(1, -1) : '';
      const ext = /^(?:https?:)?\/\//i.test(dest);
      return '<a href="' + href + '"' + (t ? ' title="' + esc(t) + '"' : '') +
        (ext && o.externalLinks ? ' target="_blank" rel="noopener noreferrer"' : '') + '>' + innerHtml + '</a>';
    };
    const mkImg = (alt, dest, title, w, h) => {
      dest = rawText(dest).trim();
      if (dest[0] === '<' && dest[dest.length - 1] === '>') dest = dest.slice(1, -1);
      const t = title ? rawText(title).slice(1, -1) : '';
      return '<img src="' + safeUrl(dest, env) + '" alt="' + esc(rawText(alt)) + '"' + (t ? ' title="' + esc(t) + '"' : '') +
        (w ? ' width="' + w + '"' : '') + (h ? ' height="' + h + '"' : '') + ' loading="lazy">';
    };

    // 1. code spans
    s = s.replace(/(?<!`)(`+)(?!`)([\s\S]*?[^`])\1(?!`)/g, (_, t, c) => {
      c = c.replace(/\n/g, ' ');
      if (/^ .* $/.test(c) && /[^ ]/.test(c)) c = c.slice(1, -1);
      return put('<code>' + esc(c) + '</code>', c);
    });

    // 2. math ($$..$$ and $..$), before backslash escapes so \{ \\ survive
    s = s.replace(/(?<!\\)\$\$([^\n]+?)\$\$/g, (_, m) => put('<span class="math math-display">\\[' + esc(m.trim()) + '\\]</span>'));
    s = s.replace(/(?<![\\$\w])\$(?![\s$])((?:[^$\n\\]|\\.)+?)(?<![\s\\])\$(?![\w$])/g,
      (_, m) => put('<span class="math math-inline">\\(' + esc(m) + '\\)</span>'));

    // 3. backslash escapes
    s = s.replace(/\\([!-\/:-@\[-`{-~])/g, (_, c) => put(esc(c), c));

    // 4. <autolinks>
    s = s.replace(/<((?:https?|ftp):\/\/[^\s<>]+)>/gi, (_, u) => put(mkLink(u, esc(u))));
    s = s.replace(/<(?:mailto:)?([\w.+-]+@[\w-]+(?:\.[\w-]+)+)>/g, (_, e) => put('<a href="mailto:' + esc(e) + '">' + esc(e) + '</a>'));

    // 5. inline HTML
    if (o.html) s = s.replace(/<!--[\s\S]*?-->|<\/?[A-Za-z][^<>]*>/g, t => put(o.html === true ? t : sanitizeHtml(t)));

    // 6. images (inline, then reference-style)
    s = s.replace(IMG_RE, (_, alt, dest, title, w, h) => put(mkImg(alt, dest, title, w, h)));
    s = s.replace(/!\[([^\[\]]*)\](?:\[([^\[\]]*)\])?/g, (m, alt, ref) => {
      const d = env.refs[normKey(ref || alt)];
      return d ? put(mkImg(alt, d.url, d.title ? '"' + d.title + '"' : '', '', '')) : m;
    });

    // 7. footnote references [^id] and inline footnotes ^[text]
    s = s.replace(/\^\[((?:[^\[\]\\]|\\.|\[[^\]]*\])+)\]|\[\^([^\]\s]+)\]/g, (m, text, ref) => {
      if (text !== undefined) {
        const id = 'inline-' + (++env.inlineFn);
        env.fns[id] = [rawText(text)];
        return put(footnoteRef(id, env));
      }
      return env.fns[ref] ? put(footnoteRef(ref, env)) : m;
    });

    // 8. wiki links
    if (o.wikiLinks && !inLink) {
      s = s.replace(/\[\[([^\[\]|\n]+?)(?:\|([^\[\]\n]+?))?\]\]/g, (_, page, label) =>
        put('<a class="wikilink" href="' + esc(o.wikiLinkBase + encodeURI(rawText(page).trim())) + '">' + esc(rawText(label || page)) + '</a>'));
    }

    // 9. links: inline, reference, collapsed/shortcut
    s = s.replace(LINK_RE, (_, text, dest, title) => put(mkLink(dest, inl(text, env, ctx, true), title)));
    s = s.replace(/\[((?:[^\[\]\\]|\\.)+)\]\[([^\[\]]*)\]/g, (m, text, ref) => {
      const d = env.refs[normKey(ref || rawText(text))];
      return d ? put(mkLink(d.url, inl(text, env, ctx, true), d.title ? '"' + d.title + '"' : '')) : m;
    });
    s = s.replace(/\[((?:[^\[\]\\]|\\.)+)\](?!\()/g, (m, text) => {
      const d = env.refs[normKey(rawText(text))];
      return d ? put(mkLink(d.url, inl(text, env, ctx, true), d.title ? '"' + d.title + '"' : '')) : m;
    });

    // 10. bare URLs / e-mails (GFM extended autolinks)
    if (o.autolink && !inLink) {
      s = s.replace(/(^|[\s(>*_~])((?:https?:\/\/|www\.)[^\s<\u0000]+)/g, (m, pre, url) => {
        let tail = '';
        for (;;) {
          const c = url[url.length - 1];
          if (c && /[.,:;"'!?*_~]/.test(c)) { tail = c + tail; url = url.slice(0, -1); }
          else if (c === ')' && (url.match(/\(/g) || []).length < (url.match(/\)/g) || []).length) { tail = c + tail; url = url.slice(0, -1); }
          else break;
        }
        if (!/\w\./.test(url) && !/^https?:\/\/\w/.test(url)) return m;
        const href = /^www\./i.test(url) ? 'http://' + url : url;
        return pre + put(mkLink(href, esc(url))) + tail;
      });
      s = s.replace(/(^|[\s(])([\w.+-]+@[\w-]+(?:\.[\w-]+)*\.[A-Za-z]{2,})(?![\w@])/g,
        (_, pre, e) => pre + put('<a href="mailto:' + esc(e) + '">' + esc(e) + '</a>'));
    }

    // 11. escape what's left (keep valid entities)
    s = esc(s).replace(/&amp;(#\d+|#[xX][\da-fA-F]+|[A-Za-z][A-Za-z\d]*);/g, '&$1;');

    // 12. typographer
    if (o.typographer) {
      s = s.replace(/\(c\)/gi, '©').replace(/\(r\)/gi, '®').replace(/\(tm\)/gi, '™').replace(/\.{3}/g, '…')
        .replace(/(?<!-)---(?!-)/g, '—').replace(/(?<!-)--(?!-)/g, '–')
        .replace(/(^|[\s(\[{>])&quot;(?=\S)/g, '$1“').replace(/&quot;/g, '”')
        .replace(/(^|[\s(\[{>])'(?=\S)/g, '$1‘').replace(/'/g, '’');
    }

    // 13. emoji shortcodes
    if (o.emoji) s = s.replace(/:([a-z0-9_+-]+):/g, (m, k) => EMOJI[k] || m);

    // 14. abbreviations (*[HTML]: HyperText Markup Language)
    const abbrs = Object.keys(env.abbrs);
    if (abbrs.length) {
      const re = new RegExp('(?<![\\w&#\\u0000])(' + abbrs.sort((a, b) => b.length - a.length).map(escRe).join('|') + ')(?![\\w;\\u0000])', 'g');
      s = s.replace(re, (_, k) => put('<abbr title="' + esc(env.abbrs[k]) + '">' + esc(k) + '</abbr>'));
    }

    // 15. emphasis family
    s = s.replace(/~~(?=\S)([\s\S]*?\S)~~/g, '<del>$1</del>');
    s = s.replace(/(?<![\w=])==(?=\S)([\s\S]*?\S)==(?![\w=])/g, '<mark>$1</mark>');
    s = s.replace(/(?<![\w+])\+\+(?=\S)([\s\S]*?\S)\+\+(?![\w+])/g, '<ins>$1</ins>');
    if (o.subSup) {
      s = s.replace(/(?<![~\\])~([^\s~]+)~(?!~)/g, '<sub>$1</sub>');
      s = s.replace(/(?<!\^)\^([^\s^]+)\^/g, '<sup>$1</sup>');
    }
    s = s.replace(/(?<![\w*])\*\*\*(?=\S)([\s\S]*?\S)\*\*\*(?!\*)/g, '<strong><em>$1</em></strong>');
    s = s.replace(/(?<!\w)___(?=\S)([\s\S]*?\S)___(?!\w)/g, '<strong><em>$1</em></strong>');
    s = s.replace(/\*\*(?=\S)([\s\S]*?\S)\*\*(?!\*)/g, '<strong>$1</strong>');
    s = s.replace(/(?<!\w)__(?=\S)([\s\S]*?\S)__(?!\w)/g, '<strong>$1</strong>');
    s = s.replace(/\*(?=\S)([\s\S]*?\S)\*/g, '<em>$1</em>');
    s = s.replace(/(?<!\w)_(?=\S)([\s\S]*?\S)_(?!\w)/g, '<em>$1</em>');

    // 16. line breaks
    s = s.replace(/(?: {2,}|\\)\n/g, '<br>\n').replace(/[ \t]+\n/g, '\n');
    if (o.breaks) s = s.replace(/(?<!<br>)\n/g, '<br>\n');
    return s;
  }

  function footnoteRef(id, env) {
    if (!env.fnOrder.includes(id)) env.fnOrder.push(id);
    const num = env.fnOrder.indexOf(id) + 1;
    const cnt = env.fnRefs[id] = (env.fnRefs[id] || 0) + 1;
    const fid = encodeURIComponent(id);
    return '<sup class="footnote-ref"><a href="#fn-' + fid + '" id="fnref-' + fid + (cnt > 1 ? '-' + cnt : '') + '">' + num + '</a></sup>';
  }

  function interrupts(ln, env) {
    return /^ {0,3}(?:`{3,}|~{3,})/.test(ln) || /^ {0,3}#{1,6}(?:[ \t]|$)/.test(ln) || HR_RE.test(ln) ||
      /^ {0,3}>/.test(ln) || /^ {0,3}(?:[-*+]|1[.)])[ \t]+\S/.test(ln) || /^ {0,3}\$\$[ \t]*$/.test(ln) ||
      /^:{3,}[ \t]*[\w-]+/.test(ln) || (env.opts.html && HTML_BLOCK_RE.test(ln));
  }

  function slugify(html, env) {
    const base = html.replace(/<[^>]+>/g, '').replace(/&[#\w]+;/g, '').toLowerCase().trim()
      .replace(/[^\p{L}\p{N}\s_-]/gu, '').replace(/\s/g, '-') || 'section';
    if (env.slugs[base] === undefined) { env.slugs[base] = 0; return base; }
    return base + '-' + (++env.slugs[base]);
  }

  function heading(level, raw, env) {
    let id = '', classes = [];
    const am = /[ \t]*\{([^{}]*)\}[ \t]*$/.exec(raw);
    if (am && /^[\s#.\w:-]+$/.test(am[1]) && /[#.]/.test(am[1])) {
      am[1].trim().split(/\s+/).forEach(t => { if (t[0] === '#') id = t.slice(1); else if (t[0] === '.') classes.push(t.slice(1)); });
      raw = raw.slice(0, am.index);
    }
    const inner = inline(raw.trim(), env);
    if (!id && env.opts.headingIds) id = slugify(inner, env);
    if (id) env.toc.push({ level, id, text: inner });
    const anchor = id && env.opts.headingAnchors ? '<a class="anchor" href="#' + esc(id) + '" aria-hidden="true">#</a>' : '';
    return '<h' + level + (id ? ' id="' + esc(id) + '"' : '') + (classes.length ? ' class="' + esc(classes.join(' ')) + '"' : '') + '>' +
      anchor + inner + '</h' + level + '>';
  }

  function codeBlock(code, info, env) {
    const lang = (info.split(/\s+/)[0] || '').replace(/^\{?\.?|\}$/g, '');
    if (lang === 'mermaid') return '<pre class="mermaid">' + esc(code) + '</pre>';
    if (/^(math|latex|tex)$/i.test(lang)) return '<div class="math math-display">\\[' + esc(code) + '\\]</div>';
    let body = env.opts.highlight ? env.opts.highlight(code, lang) : null;
    if (body == null) body = esc(code);
    return '<pre><code' + (lang ? ' class="language-' + esc(lang) + '"' : '') + '>' + body + '\n</code></pre>';
  }

  function splitRow(s) {
    s = s.trim();
    if (s[0] === '|') s = s.slice(1);
    if (/(^|[^\\])\|$/.test(s)) s = s.slice(0, -1);
    const cells = []; let cur = '';
    for (let k = 0; k < s.length; k++) {
      if (s[k] === '\\' && s[k + 1] === '|') { cur += '|'; k++; }
      else if (s[k] === '|') { cells.push(cur.trim()); cur = ''; }
      else cur += s[k];
    }
    cells.push(cur.trim());
    return cells;
  }

  function parseList(lines, i, env) {
    const n = lines.length;
    const first = LIST_RE.exec(lines[i]);
    const ordered = /\d/.test(first[2]);
    const delim = first[2].slice(-1);
    const start = ordered ? parseInt(first[2], 10) : 1;
    const items = [];
    let loose = false, blankBefore = false;

    while (i < n) {
      const m = LIST_RE.exec(lines[i]);
      if (!m || /\d/.test(m[2]) !== ordered || m[2].slice(-1) !== delim || HR_RE.test(lines[i])) break;
      if (blankBefore && items.length) loose = true;
      blankBefore = false;
      let rest = lines[i].slice(m[0].length), spaces = m[3].length;
      if (isBlank(rest)) { spaces = 1; rest = ''; }
      else if (spaces > 4) { rest = ' '.repeat(spaces - 1) + rest; spaces = 1; }
      const ci = m[1].length + m[2].length + spaces;
      const body = [rest];
      i++;
      while (i < n) {
        const ln = lines[i];
        if (isBlank(ln)) {
          let j = i; while (j < n && isBlank(lines[j])) j++;
          if (j < n && indentOf(lines[j]) >= ci) { for (; i < j; i++) body.push(''); continue; }
          blankBefore = j < n; i = j; break;
        }
        if (indentOf(ln) >= ci) { body.push(ln.slice(ci)); i++; continue; }
        if (LIST_RE.test(ln)) break;
        const last = body[body.length - 1];
        if (last.trim() !== '' && !interrupts(ln, env)) { body.push(ln.trim()); i++; continue; }
        break;
      }
      items.push(body);
    }

    items.forEach(body => {
      let inFence = false;
      for (let k = 0; k < body.length - 1; k++) {
        if (/^ *(?:`{3,}|~{3,})/.test(body[k])) inFence = !inFence;
        if (!inFence && body[k] === '' && body[k + 1] !== '' && indentOf(body[k + 1]) === 0 && !LIST_RE.test(body[k + 1])) loose = true;
      }
    });

    let hasTask = false;
    const lis = items.map(body => {
      const tm = /^\[([ xX])\][ \t]+/.exec(body[0]);
      let task = '';
      if (tm) {
        hasTask = true; body[0] = body[0].slice(tm[0].length);
        task = '<input type="checkbox" disabled' + (tm[1] !== ' ' ? ' checked' : '') + '> ';
      }
      let inner = parseBlocks(body, env, { tight: !loose });
      if (task) inner = loose && inner.startsWith('<p>') ? inner.replace(/^<p>/, '<p>' + task) : task + inner;
      return '<li' + (tm ? ' class="task-list-item"' : '') + '>' + inner + '</li>';
    });

    const tag = ordered ? 'ol' : 'ul';
    const attrs = (ordered && start !== 1 ? ' start="' + start + '"' : '') + (hasTask ? ' class="contains-task-list"' : '');
    return ['<' + tag + attrs + '>\n' + lis.join('\n') + '\n</' + tag + '>', i];
  }

  function parseBlocks(lines, env, o) {
    o = o || {};
    const out = [];
    const n = lines.length;
    let i = 0;

    while (i < n) {
      const line = lines[i];
      if (isBlank(line)) { i++; continue; }
      let m;

      // fenced code
      if ((m = /^( {0,3})(`{3,}|~{3,})[ \t]*(.*)$/.exec(line)) && !(m[2][0] === '`' && m[3].includes('`'))) {
        const fence = m[2], ind = m[1].length, info = m[3].trim();
        const closeRe = new RegExp('^ {0,3}' + fence[0] + '{' + fence.length + ',}[ \\t]*$');
        const stripRe = new RegExp('^ {0,' + ind + '}');
        const body = []; i++;
        while (i < n && !closeRe.test(lines[i])) { body.push(lines[i].replace(stripRe, '')); i++; }
        i++;
        out.push(codeBlock(body.join('\n'), info, env));
        continue;
      }

      // indented code
      if (/^ {4}/.test(line)) {
        const body = [];
        while (i < n) {
          if (/^ {4}/.test(lines[i])) { body.push(lines[i].slice(4)); i++; }
          else if (isBlank(lines[i])) {
            let j = i; while (j < n && isBlank(lines[j])) j++;
            if (j < n && /^ {4}/.test(lines[j])) { for (; i < j; i++) body.push(''); } else break;
          } else break;
        }
        out.push(codeBlock(body.join('\n'), '', env));
        continue;
      }

      // ATX heading
      if ((m = /^ {0,3}(#{1,6})(?:[ \t]+(.*?))?(?:[ \t]+#+)?[ \t]*$/.exec(line))) {
        out.push(heading(m[1].length, m[2] || '', env)); i++; continue;
      }

      // horizontal rule
      if (HR_RE.test(line)) { out.push('<hr>'); i++; continue; }

      // display math block
      if ((m = /^ {0,3}\$\$(.*)$/.exec(line)) && (!m[1].includes('$$') || /^(.*?)\$\$[ \t]*$/.test(m[1]))) {
        const body = [];
        const one = /^(.*?)\$\$[ \t]*$/.exec(m[1]);
        i++;
        if (one) body.push(one[1]);
        else {
          if (m[1].trim()) body.push(m[1]);
          while (i < n) {
            const e = /^(.*?)\$\$[ \t]*$/.exec(lines[i]);
            if (e) { if (e[1].trim()) body.push(e[1]); i++; break; }
            body.push(lines[i]); i++;
          }
        }
        out.push('<div class="math math-display">\\[' + esc(body.join('\n').trim()) + '\\]</div>');
        continue;
      }

      // ::: containers (admonitions, details, generic divs)
      if ((m = /^:{3,}[ \t]*([\w-]+)[ \t]*(.*?)[ \t]*$/.exec(line))) {
        const type = m[1].toLowerCase(), title = m[2];
        const body = []; let depth = 1; i++;
        while (i < n) {
          if (/^:{3,}[ \t]*[\w-]+/.test(lines[i])) depth++;
          else if (/^:{3,}[ \t]*$/.test(lines[i]) && --depth === 0) break;
          body.push(lines[i]); i++;
        }
        i++;
        const inner = parseBlocks(body, env);
        const ct = resolveCallout(type);
        if (ct) out.push(renderAlert(ct, title ? inline(title, env) : CALLOUTS[ct][0], inner, ''));
        else if (type === 'details' || type === 'spoiler') out.push('<details><summary>' + inline(title || 'Details', env) + '</summary>' + inner + '</details>');
        else out.push('<div class="container container-' + esc(type) + '">' + (title ? '<p class="container-title">' + inline(title, env) + '</p>' : '') + inner + '</div>');
        continue;
      }

      // [TOC]
      if (/^[ \t]*(?:\[TOC\]|\[\[_?toc_?\]\])[ \t]*$/i.test(line)) { out.push('<!--TOC-->'); i++; continue; }

      // raw HTML block
      if (env.opts.html && HTML_BLOCK_RE.test(line)) {
        const buf = [];
        if (/^ {0,3}<!--/.test(line)) { while (i < n) { buf.push(lines[i]); if (/-->/.test(lines[i])) { i++; break; } i++; } }
        else if (/^ {0,3}<pre[\s>]/i.test(line)) { while (i < n) { buf.push(lines[i]); if (/<\/pre>/i.test(lines[i])) { i++; break; } i++; } }
        else { while (i < n && !isBlank(lines[i])) buf.push(lines[i++]); }
        const raw = buf.join('\n');
        out.push(env.opts.html === true ? raw : sanitizeHtml(raw));
        continue;
      }

      // blockquote / GitHub alerts / Obsidian callouts
      if (/^ {0,3}>/.test(line)) {
        const q = [];
        while (i < n) {
          const ln = lines[i];
          if (/^ {0,3}>/.test(ln)) { q.push(ln.replace(/^ {0,3}> ?/, '')); i++; }
          else if (!isBlank(ln) && q.length && !isBlank(q[q.length - 1]) && !/^(?:#{1,6}\s|```|~~~)/.test(q[q.length - 1]) && !interrupts(ln, env)) { q.push(ln); i++; }
          else break;
        }
        const am = /^\[!([A-Za-z]+)\]([+-]?)[ \t]*(.*)$/.exec(q[0]);
        const type = am && resolveCallout(am[1]);
        if (type) {
          const gh = GITHUB_TYPES.has(type) && !ALIASES[am[1].toLowerCase()] && !am[2];
          const body = q.slice(1);
          let title = CALLOUTS[type][0];
          if (am[3]) { if (gh) body.unshift(am[3]); else title = null; }
          out.push(renderAlert(type, title ? esc(title) : inline(am[3], env), parseBlocks(body, env), am[2]));
        } else {
          out.push('<blockquote>\n' + parseBlocks(q, env) + '\n</blockquote>');
        }
        continue;
      }

      // lists
      if (LIST_RE.test(line)) {
        const [html, ni] = parseList(lines, i, env);
        out.push(html); i = ni; continue;
      }

      // tables
      if (line.includes('|') && i + 1 < n && lines[i + 1].includes('|') && TABLE_DELIM_RE.test(lines[i + 1])) {
        const head = splitRow(line), delim = splitRow(lines[i + 1]);
        if (head.length === delim.length) {
          const align = delim.map(c => (c[0] === ':' && c.slice(-1) === ':') ? 'center' : c.slice(-1) === ':' ? 'right' : c[0] === ':' ? 'left' : '');
          const cell = (tag, txt, k) => '<' + tag + (align[k] ? ' style="text-align:' + align[k] + '"' : '') + '>' + inline(txt, env) + '</' + tag + '>';
          let html = '<table>\n<thead>\n<tr>' + head.map((c, k) => cell('th', c, k)).join('') + '</tr>\n</thead>\n';
          i += 2;
          const rows = [];
          while (i < n && !isBlank(lines[i]) && lines[i].includes('|') && !interrupts(lines[i], env)) {
            const cells = splitRow(lines[i]);
            while (cells.length < head.length) cells.push('');
            rows.push('<tr>' + cells.slice(0, head.length).map((c, k) => cell('td', c, k)).join('') + '</tr>');
            i++;
          }
          if (rows.length) html += '<tbody>\n' + rows.join('\n') + '\n</tbody>\n';
          out.push('<div class="table-wrapper">' + html + '</table></div>');
          continue;
        }
      }

      // definition lists
      if (i + 1 < n && DD_RE.test(lines[i + 1]) && !DD_RE.test(line)) {
        let html = '<dl>';
        while (i + 1 < n && DD_RE.test(lines[i + 1])) {
          html += '<dt>' + inline(lines[i].trim(), env) + '</dt>'; i++;
          while (i < n && DD_RE.test(lines[i])) {
            const d = [lines[i].replace(/^ {0,3}:[ \t]+/, '')]; i++;
            while (i < n && !isBlank(lines[i]) && !DD_RE.test(lines[i]) && /^ {2,}\S/.test(lines[i])) d.push(lines[i++].trim());
            html += '<dd>' + inline(d.join('\n'), env) + '</dd>';
          }
          let j = i; while (j < n && isBlank(lines[j])) j++;
          if (j + 1 < n && !DD_RE.test(lines[j]) && DD_RE.test(lines[j + 1])) i = j; else break;
        }
        out.push(html + '</dl>');
        continue;
      }

      // paragraph (+ setext headings)
      const buf = [line]; i++;
      let setext = 0;
      while (i < n) {
        const ln = lines[i];
        if (isBlank(ln)) break;
        if (/^ {0,3}=+[ \t]*$/.test(ln)) { setext = 1; i++; break; }
        if (/^ {0,3}-+[ \t]*$/.test(ln)) { setext = 2; i++; break; }
        if (interrupts(ln, env)) break;
        buf.push(ln); i++;
      }
      const text = buf.map(s => s.replace(/^\s+/, '')).join('\n').replace(/\s+$/, '');
      if (setext) out.push(heading(setext, text.replace(/\n/g, ' '), env));
      else { const h = inline(text, env); out.push(o.tight ? h : '<p>' + h + '</p>'); }
    }
    return out.join('\n');
  }

  function preprocess(md, env) {
    md = String(md).replace(/\r\n?/g, '\n').replace(/\u0000/g, '');
    const meta = {};
    const fm = /^---[ \t]*\n([\s\S]*?)\n(?:---|\.\.\.)[ \t]*(?:\n|$)/.exec(md);
    if (fm) {
      fm[1].split('\n').forEach(l => {
        const kv = /^([\w-]+):[ \t]*(.*)$/.exec(l);
        if (kv) meta[kv[1]] = kv[2].replace(/^(["'])(.*)\1$/, '$2');
      });
      md = md.slice(fm[0].length);
    }
    const src = md.split('\n').map(l => l.replace(/^[ \t]+/, ws => ws.replace(/\t/g, '    ')));
    const out = [];
    let fence = null;
    for (let i = 0; i < src.length; i++) {
      const l = src[i];
      const fm2 = /^ {0,3}(`{3,}|~{3,})/.exec(l);
      if (fence) {
        out.push(l);
        if (fm2 && fm2[1][0] === fence[0] && fm2[1].length >= fence.length && /^ {0,3}[`~]+[ \t]*$/.test(l)) fence = null;
        continue;
      }
      if (fm2) { fence = fm2[1]; out.push(l); continue; }
      let m;
      if ((m = /^ {0,3}\[\^([^\]\s]+)\]:[ \t]*(.*)$/.exec(l))) {
        const body = [m[2]];
        while (i + 1 < src.length) {
          const nx = src[i + 1];
          if (/^ {2,}\S/.test(nx)) { body.push(nx.replace(/^ {1,4}/, '')); i++; }
          else if (isBlank(nx) && i + 2 < src.length && /^ {2,}\S/.test(src[i + 2])) { body.push(''); i++; }
          else break;
        }
        env.fns[m[1]] = body;
        continue;
      }
      if ((m = /^\*\[([^\]]+)\]:[ \t]*(.+?)[ \t]*$/.exec(l))) { env.abbrs[m[1]] = m[2]; continue; }
      if ((m = /^ {0,3}\[((?:[^\]\\]|\\.)+)\]:[ \t]*(<[^>]*>|\S+)(?:[ \t]+("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\([^)]*\)))?[ \t]*$/.exec(l)) && m[1][0] !== '^') {
        const key = normKey(m[1]);
        if (!env.refs[key]) env.refs[key] = { url: m[2].replace(/^<|>$/g, ''), title: m[3] ? m[3].slice(1, -1) : '' };
        continue;
      }
      out.push(l);
    }
    return { lines: out, meta };
  }

  function buildToc(items) {
    if (!items.length) return '';
    const base = Math.min.apply(null, items.map(t => t.level));
    let out = '', depth = -1;
    for (const it of items) {
      const d = Math.min(Math.max(it.level - base, 0), depth + 1);
      if (d > depth) { out += '<ul>'; depth++; }
      else { while (depth > d) { out += '</li></ul>'; depth--; } out += '</li>'; }
      out += '<li><a href="#' + esc(it.id) + '">' + it.text.replace(/<a [^>]*>#<\/a>/g, '') + '</a>';
    }
    while (depth >= 0) { out += '</li></ul>'; depth--; }
    return '<nav class="toc">' + out + '</nav>';
  }

  function parseMarkdown(md, options) {
    const opts = Object.assign({}, DEFAULTS, options);
    const env = { opts, refs: {}, abbrs: {}, fns: {}, fnOrder: [], fnRefs: {}, toc: [], slugs: {}, inlineFn: 0 };
    const { lines, meta } = preprocess(md, env);
    let html = parseBlocks(lines, env);

    if (env.fnOrder.length) {
      let items = '';
      for (let k = 0; k < env.fnOrder.length; k++) {
        const id = env.fnOrder[k], fid = encodeURIComponent(id);
        let body = parseBlocks(env.fns[id], env);
        const back = '<a href="#fnref-' + fid + '" class="footnote-backref" aria-label="Back to reference ' + (k + 1) + '">↩</a>';
        body = /<\/p>\s*$/.test(body) ? body.replace(/<\/p>\s*$/, ' ' + back + '</p>') : body + ' ' + back;
        items += '<li id="fn-' + fid + '">' + body + '</li>\n';
      }
      html += '\n<section class="footnotes"><hr><ol>\n' + items + '</ol></section>';
    }
    const toc = buildToc(env.toc);
    html = html.replace(/<!--TOC-->/g, () => toc);
    return { html, toc: env.toc, meta };
  }

  function renderMarkdown(md, options) { return parseMarkdown(md, options).html; }
  renderMarkdown.parse = parseMarkdown;
  renderMarkdown.defaults = DEFAULTS;
  renderMarkdown.css = `
.markdown-alert{--c:#0969da;border-left:.25em solid var(--c);padding:.5rem 1rem;margin:0 0 1rem;border-radius:0 6px 6px 0}
.markdown-alert>:last-child{margin-bottom:0}
.markdown-alert-title{display:flex;align-items:center;gap:.5rem;margin:0 0 .5rem;font-weight:600;color:var(--c)}
details.markdown-alert>summary{cursor:pointer;margin-bottom:.25rem}
.markdown-alert-title svg{width:16px;height:16px;flex:none;fill:none;stroke:currentColor;stroke-width:1.5;stroke-linecap:round;stroke-linejoin:round}
.markdown-alert-tip,.markdown-alert-success,.markdown-alert-todo{--c:#1a7f37}
.markdown-alert-important,.markdown-alert-example{--c:#8250df}
.markdown-alert-warning,.markdown-alert-question{--c:#9a6700}
.markdown-alert-caution,.markdown-alert-danger,.markdown-alert-failure,.markdown-alert-bug{--c:#cf222e}
.markdown-alert-quote{--c:#6e7781}
@media (prefers-color-scheme:dark){
  .markdown-alert{--c:#4493f8}
  .markdown-alert-tip,.markdown-alert-success,.markdown-alert-todo{--c:#3fb950}
  .markdown-alert-important,.markdown-alert-example{--c:#ab7df8}
  .markdown-alert-warning,.markdown-alert-question{--c:#d29922}
  .markdown-alert-caution,.markdown-alert-danger,.markdown-alert-failure,.markdown-alert-bug{--c:#f85149}
  .markdown-alert-quote{--c:#9198a1}
}
.table-wrapper{overflow-x:auto;margin:0 0 1rem}
table{border-collapse:collapse}th,td{border:1px solid #8884;padding:.4em .75em}thead th{background:#8881}
.contains-task-list,.task-list-item{list-style:none}.task-list-item input{margin:0 .4em 0 -1.4em}
.footnotes{font-size:.9em}.footnote-backref{text-decoration:none}
.heading .anchor,h1 .anchor,h2 .anchor,h3 .anchor,h4 .anchor,h5 .anchor,h6 .anchor{margin-left:-1em;padding-right:.3em;opacity:0;text-decoration:none}
:is(h1,h2,h3,h4,h5,h6):hover .anchor{opacity:.6}
mark{padding:0 .15em}abbr[title]{text-decoration:underline dotted;cursor:help}
.toc ul{margin:0}.math-display{overflow-x:auto;text-align:center}
blockquote{margin:0 0 1rem;padding:0 1em;border-left:.25em solid #8886;opacity:.85}
pre{overflow-x:auto;padding:1rem;background:#8881;border-radius:6px}
`;

  if (typeof module !== 'undefined' && module.exports) module.exports = renderMarkdown;
  global.renderMarkdown = renderMarkdown;
})(typeof globalThis !== 'undefined' ? globalThis : this);
