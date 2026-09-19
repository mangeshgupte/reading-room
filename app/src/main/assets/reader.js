(function () {
  var A = window.Android;
  var body = document.body;
  var paged = body.classList.contains('paged');
  var viewport = document.getElementById('viewport');
  var cols = document.getElementById('cols');
  var content = document.getElementById('content');

  // TeX: the Mac passes $…$ through as class="tex" holding the source; KaTeX (loaded only on pages that
  // have some) typesets it here, before anything is measured. A formula it cannot parse stays as source.
  if (window.katex) {
    document.querySelectorAll('.tex').forEach(function (el) {
      try {
        window.katex.render(el.textContent, el, { displayMode: el.classList.contains('display'), throwOnError: false });
      } catch (e) { el.classList.add('tex-failed'); }
    });
  }

  // tables scroll sideways inside a wrapper, so the page never does
  document.querySelectorAll('.report table').forEach(function (t) {
    if (t.parentElement.classList.contains('tablewrap')) return;
    var w = document.createElement('div');
    w.className = 'tablewrap';
    t.parentNode.insertBefore(w, t);
    w.appendChild(t);
  });

  function esc(s) {
    return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; });
  }

  // ---------------------------------------------------------------- scroll mode
  var lastY = window.scrollY, timer = null;
  function scrollFraction() {
    var max = document.documentElement.scrollHeight - window.innerHeight;
    return max > 0 ? Math.min(1, Math.max(0, window.scrollY / max)) : 1;
  }
  if (!paged) {
    window.addEventListener('resize', scheduleSections);
    document.addEventListener('load', function (e) { if (e.target && e.target.tagName === 'IMG') scheduleSections(); }, true);
    window.addEventListener('scroll', function () {
      if (timer) return;
      timer = setTimeout(function () {
        timer = null;
        var y = window.scrollY, dy = y - lastY;
        lastY = y;
        if (A) A.onScroll(scrollFraction(), dy, y);
      }, 120);
    }, { passive: true });
  }

  // ---------------------------------------------------------------- paged mode
  // The article is laid out as viewport-wide CSS columns; page N is the column at
  // translateX(-N*W). Lines are never cut. The page never moves: a vertical drag
  // folds the near half over the midline (Flipboard-style) and releasing past the
  // edge completes the flip; taps and keys run the same fold.
  var page = 0, pages = 1, W = 0, H = 0, tx = 0;
  var PTOP = 20, PBOTTOM = 48, PAD = 24;   // PBOTTOM clears the app's progress footer
  var fold = null, spares = [], pendingRelayout = false, suppressClickUntil = 0;

  function sidePad() { return parseFloat(getComputedStyle(body).getPropertyValue('--pad')) || 24; }
  function measure() {
    W = window.innerWidth;
    H = window.innerHeight - PTOP - PBOTTOM;
    body.style.setProperty('--pageW', W + 'px');
    body.style.setProperty('--pageH', H + 'px');
    body.style.setProperty('--ptop', PTOP + 'px');
    PAD = sidePad();
  }
  function computePages() {
    pages = Math.max(1, Math.ceil((cols.scrollWidth - 1) / W));
  }
  function flowX(el) {  // x of an element's first fragment in the untransformed column flow
    var r = el.getClientRects()[0];
    if (!r) return null;
    return r.left - viewport.getBoundingClientRect().left - tx - PAD;
  }
  function anchorBlock() {  // first block visible on the current page, to keep our place across relayouts
    var x0 = page * W, blocks = content.querySelectorAll('[data-line]');
    for (var i = 0; i < blocks.length; i++) {
      var x = flowX(blocks[i]);
      if (x !== null && x >= x0 - 1) return blocks[i];
    }
    return null;
  }
  function pageOf(el) {
    var x = flowX(el);
    if (x === null) return page;
    return Math.max(0, Math.min(pages - 1, Math.floor((x + 1) / W)));
  }
  function report(byUser) {
    var f = pages > 1 ? page / (pages - 1) : 1;
    if (A && A.onPage) A.onPage(page, pages, f, !!byUser);
  }
  function goTo(n, byUser, silent) {
    page = Math.max(0, Math.min(pages - 1, n));
    tx = -page * W;
    cols.style.transform = 'translateX(' + tx + 'px)';
    if (!silent) report(byUser);
  }
  var relayoutTimer = null;
  function relayout() {
    if (fold) { pendingRelayout = true; return; }
    var anchor = anchorBlock();
    measure();
    computePages();
    if (anchor) { page = pageOf(anchor); }
    goTo(page);
    schedulePrepare();
    reportSections();
  }
  function scheduleRelayout() {
    if (!paged) return;
    clearTimeout(relayoutTimer);
    relayoutTimer = setTimeout(relayout, 60);
  }

  // --- the fold: two hidden copies of the current page, prepared in idle time ---
  function makeClone() {
    var c = viewport.cloneNode(true);
    c.removeAttribute('id');
    c.className = 'vp layer';
    c.querySelectorAll('[id]').forEach(function (n) { n.removeAttribute('id'); });
    var cc = c.firstElementChild;
    cc.style.transition = '';
    cc.style.transform = 'translateX(' + tx + 'px)';
    c.style.visibility = 'hidden';
    body.appendChild(c);
    return c;
  }
  function invalidateSpares() {
    spares.forEach(function (c) { if (c.parentNode) c.parentNode.removeChild(c); });
    spares = [];
  }
  function prepareSpares() { invalidateSpares(); spares = [makeClone(), makeClone()]; }
  function schedulePrepare() {
    var cb = function () { if (!fold) prepareSpares(); };
    if (window.requestIdleCallback) requestIdleCallback(cb, { timeout: 400 }); else setTimeout(cb, 40);
  }

  function beginFold(dir) {
    var target = page + dir;
    if (fold || target < 0 || target >= pages) return false;
    if (spares.length < 2) prepareSpares();
    var stat = spares[0], panel = spares[1];
    spares = [];
    var from = page;
    stat.style.clipPath = dir > 0 ? 'inset(0 0 50% 0)' : 'inset(50% 0 0 0)';   // the half that waits
    stat.style.zIndex = '2';
    panel.style.clipPath = dir > 0 ? 'inset(50% 0 0 0)' : 'inset(0 0 50% 0)';  // the half that folds
    panel.style.zIndex = '3';
    panel.style.transformOrigin = '50% 50%';
    var inner = panel.firstElementChild;
    inner.style.transformOrigin = '50% 50%';
    var shade = document.createElement('div');
    shade.className = 'shade';
    panel.appendChild(shade);
    goTo(target, false, true);   // the real page beneath becomes the target, silently
    stat.style.visibility = '';
    panel.style.visibility = '';
    fold = { dir: dir, from: from, target: target, stat: stat, panel: panel, inner: inner, shade: shade, theta: 0, back: false };
    setTheta(0);
    return true;
  }
  function setTheta(theta) {
    var f = fold;
    f.theta = theta;
    var back = theta >= 90;
    if (back !== f.back) {
      f.back = back;
      // past the edge the panel shows the target page's other half, mirrored so it lands upright
      f.inner.style.transform = back
        ? 'translateX(' + (-f.target * W) + 'px) rotateX(180deg)'
        : 'translateX(' + (-f.from * W) + 'px)';
    }
    f.panel.style.transform = 'rotateX(' + (f.dir > 0 ? theta : -theta) + 'deg)';
    f.shade.style.opacity = String(Math.sin(theta * Math.PI / 180) * 0.45);
  }
  function animateTheta(to, ms, done) {
    var f = fold, from = f.theta, t0 = performance.now();
    function step(now) {
      if (fold !== f) return;
      var p = Math.min(1, (now - t0) / ms), e = 1 - Math.pow(1 - p, 3);
      setTheta(from + (to - from) * e);
      if (p < 1) requestAnimationFrame(step); else done();
    }
    requestAnimationFrame(step);
  }
  function finishFold(complete, byUser) {
    var f = fold;
    if (!f) return;
    animateTheta(complete ? 180 : 0, complete ? 240 : 180, function () {
      if (!complete) goTo(f.from, false, true);
      if (f.stat.parentNode) f.stat.parentNode.removeChild(f.stat);
      if (f.panel.parentNode) f.panel.parentNode.removeChild(f.panel);
      fold = null;
      if (complete) report(byUser !== false);
      if (pendingRelayout) { pendingRelayout = false; relayout(); } else schedulePrepare();
    });
  }
  function flip(dir) {
    if (beginFold(dir)) finishFold(true);
  }

  if (paged) {
    measure();
    var drag = null;
    viewport.addEventListener('touchstart', function (e) {
      if (e.touches.length !== 1 || fold) { drag = null; return; }
      var t = e.touches[0];
      var inScroller = e.target.closest && e.target.closest('.tablewrap, .diagram, pre');
      drag = { y: t.clientY, x: t.clientX, t: Date.now(), moved: false, dead: false, native: !!inScroller, ly: t.clientY, lt: Date.now(), vy: 0 };
    }, { passive: true });
    viewport.addEventListener('touchmove', function (e) {
      if (!drag || e.touches.length !== 1 || drag.native) return;
      var t = e.touches[0], dy = t.clientY - drag.y, dx = t.clientX - drag.x, now = Date.now();
      if (!drag.moved) {
        if (Math.abs(dy) < 6 && Math.abs(dx) < 6) return;
        if (Math.abs(dx) > Math.abs(dy)) { drag = null; return; }
        drag.moved = true;
        drag.dir = dy < 0 ? 1 : -1;
        if (!beginFold(drag.dir)) drag.dead = true;
      }
      e.preventDefault();
      if (drag.dead) return;
      drag.vy = (t.clientY - drag.ly) / Math.max(1, now - drag.lt);
      drag.ly = t.clientY; drag.lt = now;
      var progress = (drag.dir > 0 ? -dy : dy) / (H * 0.55);
      setTheta(Math.max(0, Math.min(180, progress * 180)));
    }, { passive: false });
    function endDrag() {
      if (!drag) return;
      var d = drag; drag = null;
      if (!d.moved) return;
      suppressClickUntil = Date.now() + 400;
      if (d.dead || !fold) return;
      var fast = (d.dir > 0 ? -d.vy : d.vy) > 0.4;
      finishFold(fold.theta > 70 || fast);
    }
    viewport.addEventListener('touchend', endDrag, { passive: true });
    viewport.addEventListener('touchcancel', endDrag, { passive: true });

    // taps: lower part next, upper part previous, middle toggles the chrome
    viewport.addEventListener('click', function (e) {
      if (Date.now() < suppressClickUntil) return;
      if (e.target.closest && e.target.closest('a, button, .mcomment, .endrow, .nextrow, .hl')) return;
      var r = viewport.getBoundingClientRect(), y = (e.clientY - r.top) / r.height;
      if (y > 0.62) flip(1);
      else if (y < 0.22) flip(-1);
      else if (A && A.onTap) A.onTap();
    });
    // keyboard, for the desk and for tests
    document.addEventListener('keydown', function (e) {
      if (e.key === 'ArrowDown' || e.key === 'PageDown' || e.key === ' ') { e.preventDefault(); flip(1); }
      else if (e.key === 'ArrowUp' || e.key === 'PageUp') { e.preventDefault(); flip(-1); }
    });
    // anything that changes the flow (images, diagrams, comments, rotation) re-paginates in place
    window.addEventListener('resize', scheduleRelayout);
    new MutationObserver(scheduleRelayout).observe(content, { childList: true, subtree: true });
    document.addEventListener('load', function (e) {  // real content only: the fold's clones load their images too
      if (e.target && e.target.tagName === 'IMG' && content.contains(e.target)) scheduleRelayout();
    }, true);
  }

  // ---------------------------------------------------------------- shared API (called by the app)
  function fraction() { return paged ? (pages > 1 ? page / (pages - 1) : 1) : scrollFraction(); }
  window.setScroll = function (f) {
    if (paged) { computePages(); goTo(Math.round(f * (pages - 1))); return; }
    var max = document.documentElement.scrollHeight - window.innerHeight;
    window.scrollTo(0, Math.round(max * f));
    lastY = window.scrollY;
  };
  var revealed = false;
  window.reveal = function () {
    if (!paged) { revealed = true; body.classList.remove('loading'); scheduleSections(); return; }
    var done = function () { revealed = true; relayout(); body.classList.remove('loading'); };
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(done, done); else done();
  };

  // the look — palette, type scale, spacing, justification — applied live (no reload), place kept.
  // vars: CSS custom properties; classes: body classes to switch on or off.
  window.applyStyle = function (vars, classes) {
    var anchor = paged && revealed ? anchorBlock() : null, f = paged ? 0 : scrollFraction();
    var root = document.documentElement;
    Object.keys(vars || {}).forEach(function (k) {   // <html> too: the canvas outside the body takes its colour from there
      root.style.setProperty(k, vars[k]);
      body.style.setProperty(k, vars[k]);
    });
    Object.keys(classes || {}).forEach(function (c) { body.classList.toggle(c, !!classes[c]); });
    if (!revealed) return;   // still loading: setScroll() and reveal() place the page
    if (paged) {
      measure();
      computePages();
      goTo(anchor ? pageOf(anchor) : page);
      invalidateSpares();
      schedulePrepare();
      reportSections();
    } else {
      window.setScroll(f);
      scheduleSections();
    }
  };

  // ---------------------------------------------------------------- sections + scrubbing
  // The app's progress line is the page edge you thumb. It needs to know where the sections start
  // (its tick marks and the label over the thumb), and it drives the page while a finger is on it:
  // scrubStart(), scrubTo(fraction) as the finger moves, scrubEnd() when it lifts. Pages swap at
  // once with no fold during the drag; the release lands with one real fold, like the last page
  // of a riffle. None of it reports as the reader's own page turn.
  var lastSections = '', sectionsTimer = null, scrubFrom = -1;
  function sectionList() {
    var max = paged ? 0 : document.documentElement.scrollHeight - window.innerHeight;
    // in scroll mode a section's place is where its heading sits just under the nav row
    var inset = paged ? 0 : Math.max(0, (parseFloat(getComputedStyle(body).paddingTop) || 0) - 12);
    var out = [];
    content.querySelectorAll('.report h2, .report h3').forEach(function (h) {
      var f;
      if (paged) f = pages > 1 ? pageOf(h) / (pages - 1) : 0;
      else f = max > 0 ? Math.min(1, Math.max(0, (h.getBoundingClientRect().top + window.scrollY - inset) / max)) : 0;
      out.push({ t: (h.textContent || '').trim().slice(0, 120), f: Math.round(f * 10000) / 10000 });
    });
    return out;
  }
  function reportSections() {
    if (!revealed || !A || !A.onSections) return;
    var s = JSON.stringify(sectionList());
    if (s === lastSections) return;
    lastSections = s;
    A.onSections(s);
  }
  function scheduleSections() {
    clearTimeout(sectionsTimer);
    sectionsTimer = setTimeout(reportSections, 200);
  }
  function cancelFold() {
    if (!fold) return;
    var f = fold;
    fold = null;   // animateTheta() stops when the fold is no longer the current one
    if (f.stat.parentNode) f.stat.parentNode.removeChild(f.stat);
    if (f.panel.parentNode) f.panel.parentNode.removeChild(f.panel);
  }
  window.scrubStart = function () {
    if (!paged) return;
    cancelFold();
    scrubFrom = page;
  };
  window.scrubTo = function (f) {
    f = Math.min(1, Math.max(0, f));
    if (paged) {
      cancelFold();
      var n = Math.round(f * (pages - 1));
      if (n !== page) goTo(n, false, true);
      return;
    }
    var max = document.documentElement.scrollHeight - window.innerHeight;
    window.scrollTo(0, Math.round(max * f));
    lastY = window.scrollY;   // so the throttled scroll report sees no movement of the reader's own
  };
  window.scrubEnd = function () {
    if (!paged) {
      lastY = window.scrollY;
      if (A) A.onScroll(scrollFraction(), 0, window.scrollY);
      return;
    }
    var target = page, from = scrubFrom;
    scrubFrom = -1;
    invalidateSpares();   // they are copies of the page the scrub started on
    if (from >= 0 && target !== from) {
      var dir = target > from ? 1 : -1;
      goTo(target - dir, false, true);
      if (beginFold(dir)) { finishFold(true, false); return; }
      goTo(target, false, true);
    }
    report(false);
    schedulePrepare();
  };

  // pinch: two fingers step the text size. The app owns the scale and applies it back through applyStyle().
  var pinchBase = 0;
  function dist(t) { var dx = t[0].clientX - t[1].clientX, dy = t[0].clientY - t[1].clientY; return Math.sqrt(dx * dx + dy * dy); }
  document.addEventListener('touchstart', function (e) {
    if (e.touches.length === 2) pinchBase = dist(e.touches);
  }, { passive: true });
  document.addEventListener('touchmove', function (e) {
    if (e.touches.length !== 2 || !pinchBase) return;
    e.preventDefault();
    var r = dist(e.touches) / pinchBase;
    if (r > 1.18 || r < 1 / 1.18) {
      if (A && A.onTextStep) A.onTextStep(r > 1 ? 1 : -1);
      pinchBase = dist(e.touches);
    }
  }, { passive: false });
  document.addEventListener('touchend', function (e) {
    if (e.touches.length < 2) pinchBase = 0;
  }, { passive: true });

  // long-press a block to comment on it
  var hl = null;
  window.clearHighlight = function () { if (hl) { hl.classList.remove('hl'); hl = null; } };
  document.addEventListener('contextmenu', function (e) {
    var b = e.target.closest && e.target.closest('[data-line]');
    if (!b || e.target.closest('a') || e.target.closest('.mcomment')) return;
    e.preventDefault();
    window.clearHighlight();
    hl = b;
    b.classList.add('hl');
    if (A) A.onLongPress(parseInt(b.getAttribute('data-line'), 10), (b.textContent || '').trim().slice(0, 160));
  });

  // mermaid diagrams: the Mac marks ```mermaid fences as <pre class="mermaid">; render them to SVG here
  var blocks = document.querySelectorAll('pre.mermaid');
  if (blocks.length && window.mermaid) {
    var dark = body.classList.contains('dark');
    window.mermaid.initialize({ startOnLoad: false, theme: dark ? 'dark' : 'neutral', securityLevel: 'strict', fontFamily: 'Roboto, "Helvetica Neue", Arial, sans-serif', fontSize: 15 });
    blocks.forEach(function (pre, i) {
      var src = pre.textContent;
      window.mermaid.render('mmd' + i, src).then(function (r) {
        var d = document.createElement('div');
        d.className = 'diagram';
        d.setAttribute('data-line', pre.getAttribute('data-line') || '');
        d.innerHTML = r.svg;
        // natural size (capped at ~2 screens wide), scrolling sideways in its box: a diagram
        // squeezed to the column width is unreadable
        var svg = d.querySelector('svg');
        if (svg) {
          var vb = (svg.getAttribute('viewBox') || '').split(/[\s,]+/).map(Number);
          var natural = vb.length === 4 && vb[2] > 0 ? vb[2] : svg.getBoundingClientRect().width;
          var avail = window.innerWidth - 2 * sidePad();
          if (natural > avail) {
            var w = Math.min(natural, avail * 2.2);
            svg.style.maxWidth = 'none';
            svg.style.width = w + 'px';
            svg.style.height = 'auto';
            svg.removeAttribute('width');
          }
        }
        pre.replaceWith(d);
        scheduleRelayout();
      }).catch(function (err) {
        pre.classList.add('mermaid-failed');
        pre.setAttribute('title', String(err && err.message || err));
      });
    });
  }

  // the phone's own comments: pending until the Mac confirms, failed if it refused
  window.insertPending = function (items) {
    document.querySelectorAll('.mcomment.pending, .mcomment.failed').forEach(function (n) { n.remove(); });
    (items || []).forEach(function (it) {
      var b = document.querySelector('[data-line="' + it.line + '"]');
      if (!b) return;
      var d = document.createElement('div');
      d.className = 'mcomment ' + it.state;
      d.innerHTML = '<span class="mtag">M</span>' + esc(it.text) +
        (it.error ? '<span class="why">' + esc(it.error) + '</span>' : '');
      if (it.state === 'failed') d.addEventListener('click', function () { if (A) A.onReplace(it.uuid); });
      b.insertAdjacentElement('afterend', d);
    });
    scheduleRelayout();
    if (!paged) scheduleSections();
  };
})();
