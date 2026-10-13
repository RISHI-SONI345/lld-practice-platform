(function () {
  // Polls the status endpoint every two seconds while the AI review runs, then reloads the page once it ends.
  var root = document.getElementById('attempt');
  if (root) {
    var id = root.getAttribute('data-attempt-id');
    var status = root.getAttribute('data-status');
    if (status === 'SUBMITTED' || status === 'EVALUATING') {
      var timer = setInterval(function () {
        fetch('/attempts/' + id + '/status', { headers: { 'Accept': 'application/json' } })
          .then(function (r) { return r.ok ? r.json() : null; })
          .then(function (s) {
            if (s && s.done) { clearInterval(timer); window.location.reload(); }
          })
          .catch(function () { /* keep polling; the server may be busy */ });
      }, 2000);
    }
  }

  // Render the generated class diagram. The source is read as text, never as HTML. It is redrawn when the theme
  // changes, because Mermaid bakes its colours into the SVG.
  var renderCount = 0;
  window.lldRenderDiagram = function () {
    var src = document.getElementById('mermaid-src');
    var target = document.getElementById('diagram');
    if (!src || !target || !window.mermaid) return;
    var fail = function () {
      target.textContent = 'The diagram could not be drawn. The Mermaid source is shown below.';
    };
    try {
      var dark = window.lldResolvedTheme ? window.lldResolvedTheme() === 'dark'
        : window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
      window.mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: dark ? 'dark' : 'default' });
      renderCount += 1;
      window.mermaid.render('class-diagram-' + renderCount, src.textContent).then(function (out) {
        target.innerHTML = out.svg;
      }).catch(fail);
    } catch (e) {
      fail();
    }
  };
  window.lldRenderDiagram();

  // Show how long the AI review has been running, so the wait never looks like a hang.
  var elapsed = document.getElementById('elapsed');
  if (elapsed) {
    var started = Date.now();
    setInterval(function () {
      var s = Math.round((Date.now() - started) / 1000);
      elapsed.textContent = ' (' + s + ' s)';
    }, 1000);
  }

  // Give instant feedback on slow actions: disable the buttons and show a spinner on the one that was pressed.
  document.querySelectorAll('form[data-busy]').forEach(function (form) {
    form.addEventListener('submit', function (ev) {
      var pressed = ev.submitter;
      var label = form.getAttribute('data-busy');
      // Let the browser finish building the request before the buttons are disabled.
      setTimeout(function () {
        form.querySelectorAll('button').forEach(function (b) { b.disabled = true; });
        if (pressed && pressed.getAttribute('formaction') == null) {
          pressed.textContent = '';
          var sp = document.createElement('span');
          sp.className = 'spinner small';
          sp.setAttribute('aria-hidden', 'true');
          pressed.appendChild(sp);
          pressed.appendChild(document.createTextNode(label));
        }
      }, 0);
    });
  });
})();
