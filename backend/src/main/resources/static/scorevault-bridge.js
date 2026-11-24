// ScoreVault UI/UX Interactive Bridge
// Connects Google Stitch Design System to Spring Boot 3 REST APIs
(function () {
  'use strict';

  var TOKEN_KEY = 'sv_token'; // same keys that login.html writes
  var USER_KEY = 'sv_user';

  function getToken() { return sessionStorage.getItem(TOKEN_KEY); }
  function getUser() {
    try { return JSON.parse(sessionStorage.getItem(USER_KEY)); } catch (e) { return null; }
  }
  function setSession(token, user) {
    if (token) { sessionStorage.setItem(TOKEN_KEY, token); } else { sessionStorage.removeItem(TOKEN_KEY); }
    if (user) { sessionStorage.setItem(USER_KEY, JSON.stringify(user)); } else { sessionStorage.removeItem(USER_KEY); }
  }

  function api(path, options) {
    options = options || {};
    var headers = { 'Content-Type': 'application/json' };
    var token = getToken();
    if (token) { headers['Authorization'] = 'Bearer ' + token; }
    return fetch(path, {
      method: options.method || 'GET',
      headers: headers,
      body: options.body ? JSON.stringify(options.body) : undefined
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (data) {
        if (!res.ok) {
          var err = new Error(data.error || ('Request failed (' + res.status + ')'));
          err.status = res.status;
          if (res.status === 401) { signOut(); }
          throw err;
        }
        return data;
      });
    });
  }

  function signOut() {
    setSession(null, null);
    window.location.href = '/login.html';
  }

  // No auto-login: if there is no session, send the visitor to the login page.
  function ensureSession() {
    if (!getToken()) {
      window.location.href = '/login.html';
      return false;
    }
    updateHeaderUser();
    return true;
  }

  function updateHeaderUser() {
    var user = getUser();
    var nameEl = document.querySelector('header span.font-title-md');
    if (nameEl && user) {
      nameEl.textContent = user.name || user.fullName || '';
    }
    var badges = document.querySelectorAll('header span.font-label-mono-sm');
    if (badges && badges.length >= 2 && user) {
      badges[badges.length - 2].textContent = user.affiliation || 'ScoreVault';
      badges[badges.length - 1].textContent = user.role;
    }
  }

  // "Switch role" simply signs out and returns to the login page.
  // No passwords are stored in this file.
  function mountRoleSwitcher() {
    var headerRight = document.querySelector('header .flex.items-center.gap-space-md');
    if (!headerRight) return;

    var btn = document.createElement('button');
    btn.className = 'flex items-center gap-1 px-3 py-1 rounded-lg bg-surface-container-high text-primary hover:bg-surface-container-highest text-xs font-semibold transition-all border border-primary/20';
    btn.innerHTML = '<span class="material-symbols-outlined text-[16px]">logout</span><span>Sign out / Switch role</span>';
    btn.onclick = signOut;
    headerRight.prepend(btn);
  }

  // Dynamic Live Leaderboard wiring
  function initLeaderboard() {
    if (!window.location.pathname.includes('leaderboard') && !window.location.pathname.endsWith('/')) return;

    // Load actual backend standings if available
    api('/api/v1/rounds').then(function (rounds) {
      if (rounds && rounds.length > 0) {
        api('/api/v1/leaderboard/' + rounds[0].roundId).then(function (teams) {
          if (teams && teams.length > 0) {
            // Update top podium card if elements match
            var topCard = document.querySelector('.grid.grid-cols-1.md\\:grid-cols-3');
            if (topCard && teams[0]) {
              var t1Name = topCard.querySelector('.font-headline-lg');
              if (t1Name) t1Name.textContent = teams[0].teamName;
              var t1Inst = topCard.querySelector('.font-title-md.text-body-md');
              if (t1Inst) t1Inst.textContent = teams[0].institution;
              var t1Score = topCard.querySelector('.text-display-lg');
              if (t1Score) t1Score.textContent = Number(teams[0].finalWeightedAverageScore).toFixed(2);
            }
          }
        });
      }
    }).catch(function () {});
  }

  // Judging page: loads the judge's real assignment and submits real scores.
  function initJudging() {
    if (!window.location.pathname.includes('judging')) return;

    var lockBtn = document.getElementById('lock-btn');
    var totalEl = document.getElementById('total-score-display');
    var pctEl = document.getElementById('summary-percentage');
    var teamEl = document.querySelector('h1');
    var subtitleEl = teamEl ? teamEl.parentElement.querySelector('p') : null;
    var feedbackEl = document.getElementById('audit-feedback');
    var task = null;
    var rows = []; // { criterion, slider, valEl }

    function setLockState(enabled, label) {
      if (!lockBtn) return;
      lockBtn.disabled = !enabled;
      lockBtn.style.opacity = enabled ? '' : '0.5';
      if (label) { lockBtn.lastChild.textContent = ' ' + label; }
    }

    function recompute() {
      var total = 0;
      rows.forEach(function (r) {
        var raw = parseFloat(r.slider.value) || 0;
        var max = Number(r.criterion.maxScore) || 10;
        total += (raw / max) * Number(r.criterion.weightagePercent);
        r.valEl.textContent = raw.toFixed(2);
      });
      if (totalEl) totalEl.textContent = total.toFixed(2);
      if (pctEl) pctEl.textContent = Math.round(total) + '%';
    }

    function bindCard(i, c) {
      var valEl = document.getElementById('c' + (i + 1) + '-val');
      if (!valEl) return null;
      var card = valEl.closest('.rounded-xl');
      var slider = card.querySelector('input[type="range"]');
      var titles = card.querySelectorAll('span.font-title-md, span.font-body-sm');
      if (titles[0]) titles[0].textContent = (i + 1) + '. ' + c.name;
      if (titles[1]) titles[1].textContent = c.description || '';
      card.querySelectorAll('span').forEach(function (sp) {
        if (/^Weight /.test(sp.textContent)) { sp.textContent = 'Weight ' + Number(c.weightagePercent) + '%'; }
        if (/^\/ /.test(sp.textContent.trim())) { sp.textContent = '/ ' + Number(c.maxScore); }
      });
      slider.removeAttribute('oninput');
      slider.min = 0;
      slider.max = Number(c.maxScore);
      slider.step = 0.5;
      slider.value = Number(c.maxScore) / 2;
      slider.addEventListener('input', recompute);
      var btns = card.querySelectorAll('button');
      if (btns[0]) btns[0].onclick = function () { slider.value = Math.max(0, parseFloat(slider.value) - 0.5); recompute(); };
      if (btns[1]) btns[1].onclick = function () { slider.value = Math.min(Number(c.maxScore), parseFloat(slider.value) + 0.5); recompute(); };
      return { criterion: c, slider: slider, valEl: valEl, card: card };
    }

    function showNoTask(msg) {
      if (teamEl) teamEl.textContent = msg;
      if (subtitleEl) subtitleEl.textContent = 'Ask an administrator to assign you a team in a round (Admin > Assign Judge).';
      for (var k = 1; k <= 4; k++) {
        var v = document.getElementById('c' + k + '-val');
        var card = v && v.closest('.rounded-xl');
        if (card) card.style.display = 'none';
      }
      if (totalEl) totalEl.textContent = '0.00';
      if (pctEl) pctEl.textContent = '0%';
      if (feedbackEl) feedbackEl.value = '';
      setLockState(false);
    }

    function submit() {
      if (!task) return;
      if (!confirm('Once sealed, this score is locked permanently and written to the audit log. Proceed?')) return;
      setLockState(false);
      api('/api/v1/scores/submit', {
        method: 'POST',
        body: {
          assignmentId: task.assignmentId,
          criteriaScores: rows.map(function (r) {
            return { criterionId: r.criterion.criterionId, rawScore: parseFloat(r.slider.value) };
          }),
          feedback: feedbackEl ? feedbackEl.value : ''
        }
      }).then(function () {
        alert('Evaluation committed and locked.');
        window.location.href = '/leaderboard.html';
      }).catch(function (err) {
        alert('Could not commit: ' + err.message);
        setLockState(true);
      });
    }

    var user = getUser();
    if (!user || user.role !== 'JUDGE') {
      showNoTask('Judge account required');
      if (subtitleEl) subtitleEl.textContent = 'Sign out and log in as a judge to score entries.';
      return;
    }

    if (lockBtn) { lockBtn.onclick = submit; }
    api('/api/v1/scores/my-tasks').then(function (tasks) {
      task = (tasks || []).filter(function (t) { return !(t.isLocked || t.locked) && t.status !== 'SUBMITTED'; })[0];
      if (!task) { showNoTask('No open assignments'); return; }
      if (teamEl) teamEl.textContent = task.teamName;
      if (subtitleEl) subtitleEl.textContent = task.eventTitle + ' \u00b7 ' + task.roundName +
        (task.fileName ? ' \u00b7 Submission: ' + task.fileName : '');
      if (feedbackEl) { feedbackEl.value = ''; feedbackEl.placeholder = 'Written feedback for the team (optional)'; feedbackEl.maxLength = 1000; }
      for (var i = 0; i < 4; i++) {
        var c = task.criteria[i];
        if (c) { var r = bindCard(i, c); if (r) rows.push(r); }
        else {
          var v = document.getElementById('c' + (i + 1) + '-val');
          var card = v && v.closest('.rounded-xl');
          if (card) card.style.display = 'none';
        }
      }
      recompute();
      setLockState(true);
    }).catch(function (err) {
      showNoTask('Could not load assignments');
      if (subtitleEl) subtitleEl.textContent = err.message;
    });
  }

  // Dynamic file hash calculation on submissions.html
  function initSubmissions() {
    if (!window.location.pathname.includes('submissions')) return;

    var fileInput = document.querySelector('input[type="file"]');
    if (!fileInput) {
      fileInput = document.createElement('input');
      fileInput.type = 'file';
      fileInput.style.display = 'none';
      document.body.appendChild(fileInput);

      var dropzones = document.querySelectorAll('.border-dashed');
      dropzones.forEach(function (dz) {
        dz.style.cursor = 'pointer';
        dz.onclick = function () { fileInput.click(); };
      });
    }

    fileInput.onchange = function () {
      if (!fileInput.files.length) return;
      var file = fileInput.files[0];
      var reader = new FileReader();
      reader.onload = function (e) {
        crypto.subtle.digest('SHA-256', e.target.result).then(function (hashBuffer) {
          var hashArray = Array.from(new Uint8Array(hashBuffer));
          var hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
          alert('✓ File Analyzed: ' + file.name + ' (' + (file.size / 1024 / 1024).toFixed(2) + ' MB)\nSHA-256 Digest: ' + hashHex + '\nDirect Azure Blob URL staged.');
        });
      };
      reader.readAsArrayBuffer(file);
    };
  }

  // Initialize on page load
  document.addEventListener('DOMContentLoaded', function () {
    if (!ensureSession()) return;
    mountRoleSwitcher();
    initLeaderboard();
    initJudging();
    initSubmissions();
  });
})();
