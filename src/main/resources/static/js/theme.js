// Light / Dark / System theme. The choice is remembered in this browser only. It is applied in <head>, before the
// page paints, so there is no flash of the wrong colours.
(function () {
  var KEY = 'lld-theme';
  var root = document.documentElement;
  var NAMES = { system: 'System', light: 'Light', dark: 'Dark' };
  var NEXT = { system: 'light', light: 'dark', dark: 'system' };

  function read() {
    try {
      var v = window.localStorage.getItem(KEY);
      return v === 'light' || v === 'dark' ? v : 'system';
    } catch (e) {
      return 'system'; // storage can be blocked (private window); the page still works
    }
  }

  function write(mode) {
    try {
      if (mode === 'system') window.localStorage.removeItem(KEY);
      else window.localStorage.setItem(KEY, mode);
    } catch (e) { /* ignore */ }
  }

  function apply(mode) {
    if (mode === 'system') root.removeAttribute('data-theme');
    else root.setAttribute('data-theme', mode);
  }

  // What the page is actually showing right now: 'light' or 'dark'.
  window.lldResolvedTheme = function () {
    var t = root.getAttribute('data-theme');
    if (t) return t;
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  };

  apply(read());

  document.addEventListener('DOMContentLoaded', function () {
    var btn = document.getElementById('theme-toggle');
    if (!btn) return;
    function label() { btn.textContent = 'Theme: ' + NAMES[read()]; }
    label();
    btn.addEventListener('click', function () {
      var next = NEXT[read()];
      write(next);
      apply(next);
      label();
      if (window.lldRenderDiagram) window.lldRenderDiagram(); // redraw the diagram in the new colours
    });
    if (window.matchMedia) {
      var mq = window.matchMedia('(prefers-color-scheme: dark)');
      var onChange = function () { if (read() === 'system' && window.lldRenderDiagram) window.lldRenderDiagram(); };
      if (mq.addEventListener) mq.addEventListener('change', onChange);
    }
  });
})();
