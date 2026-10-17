// TV remote navigation: arrow keys move a highlight between links/buttons on the page.
// Injected by the app; the app calls __tvnav.move(dir) and __tvnav.target().
if (!window.__tvnav) (function () {
  var SEL = 'a[href],button,input:not([type=hidden]),select,textarea,summary,video,iframe,' +
    '[role=button],[role=link],[role=tab],[role=menuitem],[role=option],[onclick],' +
    '[tabindex]:not([tabindex="-1"])';
  var CLS = '__tvf', SEEN = '__tvi', PILL = '__tvp';
  var cur = null, last = null;
  var rowMemory = new WeakMap(); // row -> last selected item in it (like Netflix)

  // Premium focus look.
  //  - Menu items, small buttons and text links get a glowing red pill.
  //  - Posters and cards get a white ring with a soft red glow that slowly breathes.
  // Only transform is transitioned (GPU); the glow animation repaints just the one item.
  var RING = '0 0 0 3px rgba(255,255,255,.95)';
  var GLOW_A = '0 0 22px 4px rgba(229,9,20,.55),0 0 48px 10px rgba(229,9,20,.22)';
  var GLOW_B = '0 0 30px 7px rgba(255,61,87,.75),0 0 64px 16px rgba(229,9,20,.32)';
  var DROP = '0 18px 40px rgba(0,0,0,.65)';
  var css = document.createElement('style');
  css.textContent =
    '.' + SEEN + '{transition:transform .24s cubic-bezier(.2,.8,.2,1)!important;' +
    'backface-visibility:hidden}' +
    '.' + CLS + '{outline:none!important;border-radius:12px!important;' +
    'transform:scale(1.08) translateZ(0)!important;z-index:10!important;will-change:transform;' +
    'box-shadow:' + RING + ',' + GLOW_A + ',' + DROP + '!important;' +
    'animation:__tvglow 2.6s ease-in-out infinite!important}' +
    '@keyframes __tvglow{0%,100%{box-shadow:' + RING + ',' + GLOW_A + ',' + DROP + '}' +
    '50%{box-shadow:' + RING + ',' + GLOW_B + ',' + DROP + '}}' +
    // pill version: solid red gradient pill with a soft glow (extra 7px of pill from the spread shadow)
    '.' + CLS + '.' + PILL + '{border-radius:999px!important;transform:scale(1.06) translateZ(0)!important;' +
    'background:linear-gradient(180deg,#FF3D57,#D10A1A)!important;color:#fff!important;' +
    'text-decoration:none!important;' +
    'box-shadow:0 0 0 7px #E0142A,0 0 0 8px rgba(255,255,255,.35),' + GLOW_A + '!important;' +
    'animation:__tvpill 2.6s ease-in-out infinite!important}' +
    '.' + CLS + '.' + PILL + ' *{color:#fff!important}' +
    '@keyframes __tvpill{0%,100%{box-shadow:0 0 0 7px #E0142A,0 0 0 8px rgba(255,255,255,.35),' + GLOW_A + '}' +
    '50%{box-shadow:0 0 0 7px #E81A30,0 0 0 8px rgba(255,255,255,.5),' + GLOW_B + '}}' +
    'iframe.' + CLS + ',video.' + CLS + '{transform:none!important}' +
    'html{scroll-behavior:smooth}';
  (document.head || document.documentElement).appendChild(css);

  function vp() {
    var v = window.visualViewport;
    return v ? { w: v.width, h: v.height, x: v.offsetLeft, y: v.offsetTop }
             : { w: innerWidth, h: innerHeight, x: 0, y: 0 };
  }

  function shown(el, r) {
    if (r.width < 6 || r.height < 6) return false;
    var s = getComputedStyle(el);
    return s.visibility !== 'hidden' && s.display !== 'none' && +s.opacity !== 0 &&
           s.pointerEvents !== 'none';
  }

  // ---- Finding selectable things ----
  // 1. Real links/buttons (SEL).
  // 2. Anything the site styles as clickable (cursor: pointer). Many movie sites build
  //    poster cards as plain boxes with click handlers, not links.
  // 3. Poster-sized images, as a last resort.
  // The expensive part (2) is cached and only redone after the page changes.
  var SKIP = { SCRIPT: 1, STYLE: 1, LINK: 1, META: 1, NOSCRIPT: 1, BR: 1, HTML: 1, BODY: 1,
               HEAD: 1, TITLE: 1, path: 1, g: 1, use: 1, defs: 1, svg: 1, SOURCE: 1, TRACK: 1 };
  var pointerCache = null, dirty = true, scannedAt = 0;
  try {
    new MutationObserver(function () { dirty = true; })
      .observe(document.documentElement, { childList: true, subtree: true, attributes: true,
                                           attributeFilter: ['class', 'style', 'hidden'] });
  } catch (e) {}

  // Clickable-looking elements: where the site's "pointer" cursor starts, plus
  // repeated same-size boxes inside a clickable area (cards in a clickable row).
  function pointerRoots() {
    var now = Date.now();
    if (pointerCache && (!dirty || now - scannedAt < 400)) return pointerCache;
    var out = [], all = document.body ? document.body.getElementsByTagName('*') : [];
    var isPtr = new Map();
    function ptr(el) {
      if (!el || el === document.body || el === document.documentElement) return false;
      var v = isPtr.get(el);
      if (v === undefined) { v = getComputedStyle(el).cursor === 'pointer'; isPtr.set(el, v); }
      return v;
    }
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (SKIP[el.tagName] || !ptr(el)) continue;
      if (!ptr(el.parentElement)) { out.push(el); continue; }
      if (looksLikeListItem(el)) out.push(el);
    }
    pointerCache = out; dirty = false; scannedAt = now;
    return out;
  }

  function looksLikeListItem(el) {
    var p = el.parentElement;
    if (!p || p.children.length < 2) return false;
    // Layout size (ignores the zoom we add to the selected item).
    var w = el.offsetWidth, h = el.offsetHeight;
    if (!(w >= 60 && h >= 60)) return false;
    for (var k = 0; k < p.children.length; k++) {
      var sib = p.children[k];
      if (sib === el) continue;
      if (Math.abs(sib.offsetWidth - w) < 4 && Math.abs(sib.offsetHeight - h) < 4) return true;
    }
    return false;
  }

  function items() {
    var v = vp(), big = v.w * v.h * 0.6, seen = new Set(), out = [];
    function add(el, minW, minH) {
      if (seen.has(el) || el.disabled) return;
      seen.add(el);
      var r = el.getBoundingClientRect();
      // Skip things far off screen before the (slower) style check.
      if (el !== cur && (r.bottom < -v.h * 2 || r.top > v.h * 3)) return;
      if (r.width < minW || r.height < minH || !shown(el, r)) return;
      var media = el.tagName === 'IFRAME' || el.tagName === 'VIDEO';
      if (!media && r.width * r.height > big) return; // page-wide wrappers
      out.push({ el: el, r: r });
    }
    var i, a = document.querySelectorAll(SEL);
    for (i = 0; i < a.length; i++) add(a[i], 6, 6);
    a = pointerRoots();
    for (i = 0; i < a.length; i++) add(a[i], 16, 16);
    a = document.images;
    for (i = 0; i < a.length; i++) add(a[i], 60, 60);

    // Nested items: a card that holds one thing keeps the card (e.g. poster card, not
    // its image); a box that holds several things is a group, so keep the things inside.
    var set = new Set(out.map(function (o) { return o.el; }));
    var inner = new Map();
    out.forEach(function (o) {
      for (var p = o.el.parentElement; p; p = p.parentElement) {
        if (set.has(p)) inner.set(p, (inner.get(p) || 0) + 1);
      }
    });
    return out.filter(function (o) {
      if ((inner.get(o.el) || 0) >= 2) return false;          // group: drop it
      for (var p = o.el.parentElement; p; p = p.parentElement) {
        if (set.has(p) && (inner.get(p) || 0) < 2) return false; // inside a single card
      }
      return true;
    });
  }

  function center(r) { return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; }

  function inView(r) {
    var v = vp();
    return r.bottom > 0 && r.right > 0 && r.top < v.h && r.left < v.w;
  }

  // The nearest sideways-scrolling row that contains el (a row of movie posters).
  function rowOf(el) {
    for (var p = el.parentElement; p && p !== document.body; p = p.parentElement) {
      if (p.scrollWidth > p.clientWidth + 4) {
        var o = getComputedStyle(p).overflowX;
        if (o === 'auto' || o === 'scroll' || o === 'hidden') return p;
      }
    }
    return null;
  }

  // Small text items (menu links, buttons) get the pill look; posters/cards get the ring.
  function isPill(el) {
    var r = el.getBoundingClientRect();
    if (r.height > 72 || r.width > 420) return false;
    if (el.tagName === 'IMG' || el.tagName === 'VIDEO' || el.tagName === 'IFRAME') return false;
    var img = el.querySelector && el.querySelector('img,video,picture,svg[width]');
    if (img && img.getBoundingClientRect().height > 40) return false;
    return true;
  }

  function select(el, dir) {
    if (cur && cur !== el) cur.classList.remove(CLS);
    cur = el;
    el.classList.add(SEEN);
    if (isPill(el)) el.classList.add(PILL);
    el.classList.add(CLS);
    var row = rowOf(el);
    if (row) rowMemory.set(row, el);

    var v = vp(), r = el.getBoundingClientRect();
    // Sideways: keep the selection near the left edge of its row.
    if (row) {
      var rr = row.getBoundingClientRect();
      var left = r.left - rr.left;
      if (dir === 'left' || dir === 'right' || left < 0 || r.right > rr.right) {
        row.scrollBy({ left: left - 48, behavior: 'smooth' });
      }
    } else if (r.left < 0 || r.right > v.w) {
      el.scrollIntoView({ block: 'nearest', inline: 'center', behavior: 'smooth' });
    }
    // Up/down: keep the selected row at the same spot, about a third down the screen.
    var vertical = dir === 'up' || dir === 'down';
    if (vertical || r.top < 0 || r.bottom > v.h) {
      var target = Math.min(v.h * 0.3, Math.max(v.h - r.height - 24, 24));
      var dy = r.top - target;
      if (!vertical) dy = r.top < 0 ? r.top - 24 : r.bottom - v.h + 24;
      if (Math.abs(dy) > 4) window.scrollBy({ top: dy, behavior: 'smooth' });
    }
    last = center(r);
  }

  function pickStart(list) {
    var ref = last || { x: 0, y: 0 }, best = null, bd = 1e12;
    list.forEach(function (o) {
      if (!inView(o.r)) return;
      var c = center(o.r), d = Math.pow(c.x - ref.x, 2) + Math.pow(c.y - ref.y, 2);
      if (d < bd) { bd = d; best = o.el; }
    });
    return best || (list[0] && list[0].el);
  }

  function move(dir) {
    var list = items();
    if (!list.length) { scrollPage(dir); return 'scroll'; }
    if (!cur || !cur.isConnected || !list.some(function (o) { return o.el === cur; })) {
      var s = pickStart(list);
      if (s) select(s);
      return 'start';
    }
    var a = cur.getBoundingClientRect(), ac = center(a), best = null, bs = 1e12;
    list.forEach(function (o) {
      if (o.el === cur) return;
      var b = o.r, bc = center(b), primary, side, overlap;
      if (dir === 'down')  { primary = b.top - a.bottom;  if (bc.y <= ac.y + 2) return; }
      if (dir === 'up')    { primary = a.top - b.bottom;  if (bc.y >= ac.y - 2) return; }
      if (dir === 'right') { primary = b.left - a.right;  if (bc.x <= ac.x + 2) return; }
      if (dir === 'left')  { primary = a.left - b.right;  if (bc.x >= ac.x - 2) return; }
      if (dir === 'up' || dir === 'down') {
        overlap = Math.min(a.right, b.right) - Math.max(a.left, b.left);
        side = Math.abs(bc.x - ac.x) * (overlap > 0 ? 0.5 : 2);
      } else {
        overlap = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
        side = Math.abs(bc.y - ac.y) * (overlap > 0 ? 0.5 : 2);
      }
      var score = Math.max(primary, 0) + side;
      if (score < bs) { bs = score; best = o.el; }
    });
    if (best && (dir === 'up' || dir === 'down')) {
      // Entering another row: go back to where you were in that row.
      var row = rowOf(best), mem = row && rowMemory.get(row);
      if (mem && mem.isConnected && row !== rowOf(cur) &&
          list.some(function (o) { return o.el === mem; })) best = mem;
    }
    if (best) { select(best, dir); return 'moved'; }
    scrollPage(dir);
    return 'scroll';
  }

  function scrollPage(dir) {
    var v = vp(), dy = dir === 'down' ? v.h * 0.6 : dir === 'up' ? -v.h * 0.6 : 0;
    if (dy) window.scrollBy({ top: dy, behavior: 'smooth' });
  }

  // Where to tap for the selected item, in screen fractions (0..1), or null.
  function target() {
    if (!cur || !cur.isConnected) return null;
    var r = cur.getBoundingClientRect(), v = vp();
    var c = center(r);
    c.x = Math.min(Math.max(c.x, 1), v.w - 1);
    c.y = Math.min(Math.max(c.y, 1), v.h - 1);
    last = center(r);
    return { fx: c.x / v.w, fy: c.y / v.h };
  }

  function clear() { if (cur) cur.classList.remove(CLS); cur = null; }

  // Video helpers for the remote's play/pause and seek keys (main page only).
  function video() { return document.querySelector('video'); }
  function playPause() {
    var v = video(); if (!v) return false;
    if (v.paused) v.play(); else v.pause();
    return true;
  }
  function seek(sec) {
    var v = video(); if (!v || !isFinite(v.duration)) return false;
    v.currentTime = Math.max(0, Math.min(v.duration, v.currentTime + sec));
    return true;
  }

  window.__tvnav = { move: move, target: target, clear: clear, playPause: playPause, seek: seek };
})();
