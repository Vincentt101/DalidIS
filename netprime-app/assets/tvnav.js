// TV remote navigation: arrow keys move a highlight between links/buttons on the page.
// Injected by the app; the app calls __tvnav.move(dir) and __tvnav.target().
if (!window.__tvnav) (function () {
  var SEL = 'a[href],button,input:not([type=hidden]),select,textarea,summary,video,iframe,' +
    '[role=button],[role=link],[role=tab],[role=menuitem],[role=option],[onclick],' +
    '[tabindex]:not([tabindex="-1"])';
  var CLS = '__tvf';
  var cur = null, last = null;

  var css = document.createElement('style');
  css.textContent = '.' + CLS + '{outline:4px solid #e50914!important;outline-offset:2px!important;' +
    'box-shadow:0 0 0 7px rgba(229,9,20,.35)!important;transform:scale(1.04);' +
    'transition:transform .12s}' +
    'iframe.' + CLS + ',video.' + CLS + '{transform:none}';
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

  function items() {
    var v = vp(), big = v.w * v.h * 0.6, out = [];
    var all = document.querySelectorAll(SEL);
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (el.disabled) continue;
      var r = el.getBoundingClientRect();
      if (!shown(el, r)) continue;
      var media = el.tagName === 'IFRAME' || el.tagName === 'VIDEO';
      if (!media && r.width * r.height > big) continue; // page-wide wrappers
      out.push({ el: el, r: r });
    }
    // Keep only the outermost item (e.g. a movie card link, not the image inside it).
    var set = new Set(out.map(function (o) { return o.el; }));
    return out.filter(function (o) {
      for (var p = o.el.parentElement; p; p = p.parentElement) if (set.has(p)) return false;
      return true;
    });
  }

  function center(r) { return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; }

  function inView(r) {
    var v = vp();
    return r.bottom > 0 && r.right > 0 && r.top < v.h && r.left < v.w;
  }

  function select(el) {
    if (cur && cur !== el) cur.classList.remove(CLS);
    cur = el;
    el.classList.add(CLS);
    var r = el.getBoundingClientRect(), v = vp();
    if (r.top < 40 || r.bottom > v.h - 40 || r.left < 0 || r.right > v.w) {
      el.scrollIntoView({ block: 'center', inline: 'center', behavior: 'smooth' });
    }
    last = center(el.getBoundingClientRect());
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
        side = overlap > 0 ? 0 : Math.abs(bc.x - ac.x);
      } else {
        overlap = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
        side = overlap > 0 ? 0 : Math.abs(bc.y - ac.y);
      }
      var score = Math.max(primary, 0) + side * 2;
      if (score < bs) { bs = score; best = o.el; }
    });
    if (best) { select(best); return 'moved'; }
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
