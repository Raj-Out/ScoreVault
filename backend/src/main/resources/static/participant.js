(function () {
  'use strict';

  // The token lives only in memory (not localStorage), so a page refresh signs you out.
  var token = null;
  var user = null;

  function $(id) { return document.getElementById(id); }

  function el(tag, text, className) {
    var node = document.createElement(tag);
    if (text !== undefined && text !== null) { node.textContent = text; } // textContent: no HTML injection
    if (className) { node.className = className; }
    return node;
  }

  function api(path, options) {
    options = options || {};
    var headers = { 'Content-Type': 'application/json' };
    if (token) { headers['Authorization'] = 'Bearer ' + token; }
    return fetch(path, {
      method: options.method || 'GET',
      headers: headers,
      body: options.body ? JSON.stringify(options.body) : undefined
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (data) {
        if (!res.ok) {
          var msg = data.error || data.message || ('Request failed (' + res.status + ')');
          if (res.status === 401) { msg = token ? 'Session expired. Please sign in again.' : 'Invalid email or password.'; }
          if (res.status === 403) { msg = 'This page is for participant accounts only.'; }
          if (res.status === 409) { msg = data.error || data.message || 'That is already registered (conflict).'; }
          throw new Error(msg);
        }
        return data;
      });
    });
  }

  /* ---------- Sign in / out ---------- */
  $('login-form').addEventListener('submit', function (e) {
    e.preventDefault();
    $('login-error').textContent = '';
    api('/api/v1/auth/login', {
      method: 'POST',
      body: { email: $('email').value, password: $('password').value }
    }).then(function (data) {
      if (!data.user || data.user.role !== 'PARTICIPANT') {
        token = null;
        $('login-error').textContent = 'Please sign in with a participant account.';
        return;
      }
      token = data.token;
      user = data.user;
      $('password').value = '';
      showApp();
    }).catch(function (err) {
      $('login-error').textContent = err.message;
    });
  });

  $('logout').addEventListener('click', function () {
    token = null;
    user = null;
    $('app-view').hidden = true;
    $('login-view').hidden = false;
  });

  function showApp() {
    $('login-view').hidden = true;
    $('app-view').hidden = false;
    $('who').textContent = (user.name || user.fullName || 'Participant') + ' (' + user.role + ')';
    loadEvents();
    loadTeams();
  }

  /* ---------- Tabs ---------- */
  document.querySelectorAll('.tab-btn').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var target = btn.getAttribute('data-tab');
      document.querySelectorAll('.tab-btn').forEach(function (b) { b.classList.remove('active'); });
      btn.classList.add('active');
      document.querySelectorAll('.tab-content').forEach(function (tc) { tc.hidden = tc.id !== target; });
      if (target === 'tab-results') { loadResults(); }
      if (target === 'tab-teams') { loadTeams(); }
      if (target === 'tab-submissions') { loadSubmissionsTab(); }
    });
  });

  /* ---------- My Teams ---------- */
  function loadEvents() {
    // Events are taken from the public rounds list (one entry per event).
    api('/api/v1/rounds').then(function (rounds) {
      var sel = $('team-event');
      sel.innerHTML = '';
      var seen = {};
      rounds.forEach(function (r) {
        if (seen[r.eventId]) { return; }
        seen[r.eventId] = true;
        var opt = el('option', r.eventTitle);
        opt.value = r.eventId;
        sel.appendChild(opt);
      });
    });
  }

  $('team-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var msg = $('team-msg');
    msg.textContent = '';
    api('/api/v1/participant/teams', {
      method: 'POST',
      body: { eventId: parseInt($('team-event').value, 10), teamName: $('team-name').value }
    }).then(function (team) {
      msg.textContent = 'Team "' + team.teamName + '" created.';
      msg.className = 'success';
      $('team-name').value = '';
      loadTeams();
    }).catch(function (err) {
      msg.textContent = err.message;
      msg.className = 'error';
    });
  });

  function loadTeams() {
    var container = $('teams-list');
    container.textContent = 'Loading your teams...';
    api('/api/v1/participant/teams').then(function (teams) {
      container.innerHTML = '';
      if (!teams || teams.length === 0) {
        container.appendChild(el('p', 'You are not in any team yet. Register one above.', 'meta'));
        return;
      }
      teams.forEach(function (t) {
        var card = el('div', null, 'card');
        card.appendChild(el('h3', t.teamName + ' · ' + t.eventTitle));
        if (t.lead) { card.appendChild(el('span', 'Team lead', 'badge')); }
        card.appendChild(el('p', 'Members:', 'meta'));
        var ul = el('ul');
        t.members.forEach(function (m) { ul.appendChild(el('li', m)); });
        card.appendChild(ul);

        if (t.lead) {
          var form = el('form');
          var row = el('div', null, 'row');
          var email = el('input');
          email.type = 'email'; email.placeholder = 'Member email'; email.required = true;
          var btn = el('button', 'Add member');
          btn.type = 'submit';
          var note = el('p', null, 'meta');
          row.appendChild(email); row.appendChild(btn);
          form.appendChild(row); form.appendChild(note);
          form.addEventListener('submit', function (ev) {
            ev.preventDefault();
            api('/api/v1/participant/teams/' + t.teamId + '/members', {
              method: 'POST', body: { email: email.value }
            }).then(function () { loadTeams(); })
              .catch(function (err) { note.textContent = err.message; note.className = 'error'; });
          });
          card.appendChild(form);
        }
        container.appendChild(card);
      });
    }).catch(function (err) {
      container.textContent = 'Could not load teams: ' + err.message;
    });
  }

  /* ---------- My Results ---------- */
  function loadResults() {
    var container = $('results-list');
    container.textContent = 'Loading results...';
    api('/api/v1/participant/results').then(function (results) {
      container.innerHTML = '';
      if (!results || results.length === 0) {
        container.appendChild(el('p', 'No results yet. Scores appear here only after the organiser finalizes a round.', 'meta'));
        return;
      }
      results.forEach(function (r) {
        var card = el('div', null, 'card');
        card.appendChild(el('h3', r.eventTitle + ' · ' + r.roundName));
        card.appendChild(el('p', 'Team: ' + r.teamName, 'meta'));
        var rank = (r.rank !== null && r.rank !== undefined) ? '#' + r.rank : 'Not ranked';
        var score = (r.finalScore !== null && r.finalScore !== undefined) ? Number(r.finalScore).toFixed(2) + ' / 100' : '-';
        card.appendChild(el('p', 'Rank: ' + rank + '   ·   Final score: ' + score + '   ·   Judges: ' + r.judgesEvaluated));

        if (r.criteria && r.criteria.length > 0) {
          var table = el('table');
          var trh = el('tr');
          ['Criterion', 'Max', 'Weight %', 'Avg raw', 'Avg weighted'].forEach(function (h) { trh.appendChild(el('th', h)); });
          var thead = el('thead'); thead.appendChild(trh); table.appendChild(thead);
          var tbody = el('tbody');
          r.criteria.forEach(function (c) {
            var tr = el('tr');
            tr.appendChild(el('td', c.criterion));
            tr.appendChild(el('td', String(c.maxScore)));
            tr.appendChild(el('td', String(c.weightPercent)));
            tr.appendChild(el('td', String(c.avgRawScore)));
            tr.appendChild(el('td', String(c.avgWeightedScore)));
            tbody.appendChild(tr);
          });
          table.appendChild(tbody);
          card.appendChild(table);
        }
        container.appendChild(card);
      });
    }).catch(function (err) {
      container.textContent = 'Could not load results: ' + err.message;
    });
  }

  /* ---------- My Submissions (upload) ---------- */
  var myTeams = [];
  var allRounds = [];

  function loadSubmissionsTab() {
    Promise.all([api('/api/v1/participant/teams'), api('/api/v1/rounds')]).then(function (res) {
      myTeams = res[0];
      allRounds = res[1];
      var teamSel = $('sub-team');
      teamSel.innerHTML = '';
      myTeams.forEach(function (t) {
        var opt = el('option', t.teamName + ' · ' + t.eventTitle);
        opt.value = t.teamId;
        teamSel.appendChild(opt);
      });
      fillRounds();
      loadMySubmissions();
    }).catch(function (err) { $('sub-msg').textContent = err.message; });
  }

  function fillRounds() {
    var team = myTeams.filter(function (t) { return String(t.teamId) === $('sub-team').value; })[0];
    var sel = $('sub-round');
    sel.innerHTML = '';
    if (!team) { return; }
    allRounds.filter(function (r) {
      return r.eventId === team.eventId && (r.status === 'SCHEDULED' || r.status === 'IN_PROGRESS');
    }).forEach(function (r) {
      var o = el('option', r.roundName + ' (' + r.status + ')');
      o.value = r.roundId;
      sel.appendChild(o);
    });
  }
  $('sub-team').addEventListener('change', fillRounds);

  $('sub-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var msg = $('sub-msg');
    var file = $('sub-file').files[0];
    if (!file) { msg.textContent = 'Choose a file first.'; msg.className = 'error'; return; }
    var fd = new FormData();
    fd.append('teamId', $('sub-team').value);
    fd.append('roundId', $('sub-round').value);
    fd.append('file', file);
    msg.textContent = 'Uploading...';
    msg.className = 'meta';
    fetch('/api/v1/participant/submissions', {
      method: 'POST', headers: { 'Authorization': 'Bearer ' + token }, body: fd
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (data) {
        if (!res.ok) { throw new Error(data.error || data.message || ('Upload failed (' + res.status + ')')); }
        return data;
      });
    }).then(function (s) {
      msg.textContent = 'Uploaded ' + s.fileName + ' (SHA-256 ' + s.sha256.substring(0, 12) + '...)';
      msg.className = 'success';
      $('sub-file').value = '';
      loadMySubmissions();
    }).catch(function (err) {
      msg.textContent = err.message;
      msg.className = 'error';
    });
  });

  function loadMySubmissions() {
    var container = $('sub-list');
    api('/api/v1/participant/submissions').then(function (list) {
      container.innerHTML = '';
      if (!list || list.length === 0) {
        container.appendChild(el('p', 'No submissions yet.', 'meta'));
        return;
      }
      var table = el('table');
      var trh = el('tr');
      ['Event / Round', 'Team', 'File', 'Size (KB)', 'SHA-256', 'Submitted'].forEach(function (h) { trh.appendChild(el('th', h)); });
      var thead = el('thead'); thead.appendChild(trh); table.appendChild(thead);
      var tbody = el('tbody');
      list.forEach(function (s) {
        var tr = el('tr');
        tr.appendChild(el('td', s.eventTitle + ' · ' + s.roundName));
        tr.appendChild(el('td', s.teamName));
        tr.appendChild(el('td', s.fileName));
        tr.appendChild(el('td', String(Math.round(s.sizeBytes / 1024))));
        tr.appendChild(el('td', s.sha256.substring(0, 12) + '...'));
        tr.appendChild(el('td', s.submittedAt.replace('T', ' ').substring(0, 19)));
        tbody.appendChild(tr);
      });
      table.appendChild(tbody);
      container.innerHTML = '';
      container.appendChild(table);
    }).catch(function (err) { container.textContent = 'Could not load submissions: ' + err.message; });
  }
})();