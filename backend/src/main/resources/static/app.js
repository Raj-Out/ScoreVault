(function () {
  'use strict';

  var token = null;
  var user = null;

  function $(id) { return document.getElementById(id); }

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
          var message = data.error || ('Request failed (' + res.status + ')');
          if (res.status === 401 && token) { message = 'Your session has expired. Please sign in again.'; }
          var err = new Error(message);
          err.status = res.status;
          throw err;
        }
        return data;
      });
    });
  }

  function el(tag, text, className) {
    var node = document.createElement(tag);
    if (text !== undefined && text !== null) { node.textContent = text; }
    if (className) { node.className = className; }
    return node;
  }

  /* ---------- Tab Navigation ---------- */
  var tabButtons = document.querySelectorAll('.tab-btn');
  tabButtons.forEach(function (btn) {
    btn.addEventListener('click', function () {
      var target = btn.getAttribute('data-tab');
      tabButtons.forEach(function (b) { b.classList.remove('active'); });
      btn.classList.add('active');
      document.querySelectorAll('.tab-content').forEach(function (tc) {
        tc.hidden = tc.id !== target;
      });
      if (target === 'tab-leaderboard') { loadLeaderboard(); }
      if (target === 'tab-judging' && user && user.role === 'JUDGE') { loadJudgeTasks(); }
      if (target === 'tab-assign' && user && user.role === 'ADMIN') { loadAssignData(); }
      if (target === 'tab-rubrics' && token) { loadRubrics(); }
    });
  });

  /* ---------- Live Leaderboard (Public) ---------- */
  function loadRounds() {
    api('/api/v1/rounds').then(function (rounds) {
      var select = $('leaderboard-round');
      var assignSelect = $('assign-round');
      select.innerHTML = '';
      if (assignSelect) { assignSelect.innerHTML = ''; }

      rounds.forEach(function (r) {
        var opt = el('option', r.eventTitle + ' - ' + r.roundName + ' (' + r.status + ')');
        opt.value = r.roundId;
        select.appendChild(opt);

        if (assignSelect) {
          var opt2 = el('option', r.eventTitle + ' - ' + r.roundName);
          opt2.value = r.roundId;
          assignSelect.appendChild(opt2);
        }
      });

      if (rounds.length > 0) {
        loadLeaderboard(rounds[0].roundId);
      }
    }).catch(function (err) {
      $('leaderboard-meta').textContent = 'Could not load rounds: ' + err.message;
    });
  }

  $('leaderboard-round').addEventListener('change', function () {
    loadLeaderboard($('leaderboard-round').value);
  });
  $('refresh-leaderboard').addEventListener('click', function () {
    loadLeaderboard($('leaderboard-round').value);
  });

  function loadLeaderboard(roundId) {
    roundId = roundId || $('leaderboard-round').value;
    if (!roundId) { return; }
    var container = $('leaderboard-table-container');
    container.textContent = 'Loading standings...';

    api('/api/v1/leaderboard/' + roundId).then(function (rows) {
      container.innerHTML = '';
      if (!rows || rows.length === 0) {
        container.appendChild(el('p', 'No finalized scores for this round yet. Once assigned judges submit scores, rankings will automatically appear here.', 'meta'));
        return;
      }
      var table = el('table');
      var thead = el('thead');
      var trh = el('tr');
      ['Rank', 'Team Name', 'Lead / Contestant', 'Institution', 'Judges', 'Final Weighted Score', 'Tie-Break Priority'].forEach(function (h) {
        trh.appendChild(el('th', h));
      });
      thead.appendChild(trh);
      table.appendChild(thead);

      var tbody = el('tbody');
      rows.forEach(function (r) {
        var tr = el('tr');
        var rankCell = el('td', '#' + r.leaderboardRank);
        rankCell.style.fontWeight = '700';
        if (r.leaderboardRank === 1) { rankCell.style.color = '#b45309'; }
        tr.appendChild(rankCell);
        tr.appendChild(el('td', r.teamName));
        tr.appendChild(el('td', r.teamLead));
        tr.appendChild(el('td', r.institution));
        tr.appendChild(el('td', String(r.judgesEvaluatedCount)));
        var scoreTd = el('td', r.finalWeightedAverageScore !== null ? Number(r.finalWeightedAverageScore).toFixed(2) : '-');
        scoreTd.style.fontWeight = '600';
        tr.appendChild(scoreTd);
        tr.appendChild(el('td', r.priorityCriterionScore !== null ? Number(r.priorityCriterionScore).toFixed(2) : '-'));
        tbody.appendChild(tr);
      });
      table.appendChild(tbody);
      container.appendChild(table);
    }).catch(function (err) {
      container.textContent = 'Failed to load leaderboard: ' + err.message;
    });
  }

  /* ---------- Sign In & Auth ---------- */
  $('login-form').addEventListener('submit', function (event) {
    event.preventDefault();
    $('login-error').textContent = '';
    api('/api/v1/auth/login', {
      method: 'POST',
      body: { email: $('email').value, password: $('password').value }
    }).then(function (data) {
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
    $('whoami').hidden = true;
    $('login-view').hidden = false;
    $('rubric-app-view').hidden = true;
    $('btn-tab-judging').hidden = true;
    $('btn-tab-assign').hidden = true;
    document.querySelector('.tab-btn[data-tab="tab-leaderboard"]').click();
  });

  function showApp() {
    $('login-view').hidden = true;
    $('rubric-app-view').hidden = false;
    $('whoami').hidden = false;
    $('who').textContent = user.fullName + ' (' + user.role + ') · ' + (user.affiliation || '');

    var isJudge = user.role === 'JUDGE';
    var isAdmin = user.role === 'ADMIN';

    $('btn-tab-judging').hidden = !isJudge;
    $('btn-tab-assign').hidden = !isAdmin;
    $('create-section').hidden = !isAdmin;

    if (isJudge) {
      document.querySelector('.tab-btn[data-tab="tab-judging"]').click();
    } else if (isAdmin) {
      loadCategories();
      loadRubrics();
      document.querySelector('.tab-btn[data-tab="tab-rubrics"]').click();
    } else {
      document.querySelector('.tab-btn[data-tab="tab-leaderboard"]').click();
    }
  }

  /* ---------- Rubric Templates ---------- */
  function loadCategories() {
    api('/api/v1/categories').then(function (list) {
      var sel = $('category');
      sel.innerHTML = '';
      list.forEach(function (c) {
        var opt = el('option', c.name);
        opt.value = c.id;
        sel.appendChild(opt);
      });
    });
  }

  function addCriterionRow(name, maxScore, weight) {
    var row = el('div', null, 'criterion-row');
    var n = el('input'); n.placeholder = 'Criterion name'; n.required = true; n.value = name || '';
    var m = el('input'); m.type = 'number'; m.placeholder = 'Max (10)'; m.value = maxScore || 10; m.min = '1'; m.max = '100'; m.step = '1';
    var w = el('input'); w.type = 'number'; w.placeholder = 'Weight %'; w.value = weight || ''; w.min = '1'; w.max = '100'; w.step = '1';
    var del = el('button', '×', 'secondary'); del.type = 'button';
    del.addEventListener('click', function () { row.remove(); updateWeightTotal(); });
    [w].forEach(function (i) { i.addEventListener('input', updateWeightTotal); });
    row.appendChild(n); row.appendChild(m); row.appendChild(w); row.appendChild(del);
    $('criteria').appendChild(row);
    updateWeightTotal();
  }

  function updateWeightTotal() {
    var rows = document.querySelectorAll('.criterion-row');
    var sum = 0;
    rows.forEach(function (r) {
      var val = parseFloat(r.children[2].value);
      if (!isNaN(val)) { sum += val; }
    });
    var elTotal = $('weight-total');
    elTotal.textContent = 'Total weight: ' + sum + '% / 100%';
    elTotal.className = 'total ' + (sum === 100 ? 'ok' : 'bad');
  }

  $('add-criterion').addEventListener('click', function () { addCriterionRow(); });

  $('rubric-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var msg = $('rubric-msg');
    msg.textContent = '';
    var rows = document.querySelectorAll('.criterion-row');
    var criteria = [];
    rows.forEach(function (r) {
      criteria.push({
        name: r.children[0].value,
        maxScore: parseFloat(r.children[1].value) || 10,
        weightage: parseFloat(r.children[2].value) || 0
      });
    });
    api('/api/v1/rubrics', {
      method: 'POST',
      body: {
        categoryId: parseInt($('category').value, 10),
        name: $('rubric-name').value,
        description: $('rubric-desc').value,
        criteria: criteria
      }
    }).then(function () {
      msg.textContent = 'Rubric template saved successfully!';
      msg.className = 'success';
      $('rubric-form').reset();
      $('criteria').innerHTML = '';
      loadRubrics();
    }).catch(function (err) {
      msg.textContent = err.message;
      msg.className = 'error';
    });
  });

  function loadRubrics() {
    api('/api/v1/rubrics').then(function (list) {
      var container = $('rubric-list');
      container.innerHTML = '';
      list.forEach(function (r) {
        var card = el('div', null, 'score-box');
        card.appendChild(el('h3', r.name + ' (' + r.categoryName + ')'));
        if (r.description) { card.appendChild(el('p', r.description, 'meta')); }
        var ul = el('ul');
        r.criteria.forEach(function (c) {
          ul.appendChild(el('li', c.name + ' — Max: ' + c.maxScore + ', Weight: ' + c.weightage + '%'));
        });
        card.appendChild(ul);
        container.appendChild(card);
      });
    });
  }

  /* ---------- Judge Scoring Portal ---------- */
  function loadJudgeTasks() {
    var container = $('judge-tasks-container');
    container.textContent = 'Loading your assigned entries...';
    api('/api/v1/scores/my-tasks').then(function (tasks) {
      container.innerHTML = '';
      if (!tasks || tasks.length === 0) {
        container.appendChild(el('p', 'You have no assigned entries to score at this time.', 'meta'));
        return;
      }
      tasks.forEach(function (task) {
        var box = el('div', null, 'card');
        var headerRow = el('div');
        headerRow.style.display = 'flex';
        headerRow.style.justifyContent = 'space-between';
        headerRow.style.alignItems = 'center';
        headerRow.appendChild(el('h3', task.teamName + ' · ' + task.eventTitle + ' (' + task.roundName + ')'));

        var badge = el('span', task.status, 'badge ' + task.status);
        headerRow.appendChild(badge);
        box.appendChild(headerRow);

        box.appendChild(el('p', 'Team Lead: ' + task.teamLead, 'meta'));
        if (task.submissionBlobUrl) {
          var pSub = el('p', 'Submission: ', 'meta');
          var a = el('a', task.submissionFileName || 'View Attached Artifact');
          a.href = task.submissionBlobUrl;
          a.target = '_blank';
          pSub.appendChild(a);
          box.appendChild(pSub);
        }

        var form = el('form');
        var inputs = [];
        task.criteria.forEach(function (c) {
          var cRow = el('div', null, 'score-box');
          var top = el('div', null, 'score-row');
          top.appendChild(el('strong', c.name + ' (Weight: ' + c.weightagePercent + '%)'));
          var scoreDisplay = el('span', (c.existingRawScore !== null ? c.existingRawScore : 0) + ' / ' + c.maxScore);
          scoreDisplay.style.fontWeight = '700';
          top.appendChild(scoreDisplay);
          cRow.appendChild(top);

          if (c.description) { cRow.appendChild(el('p', c.description, 'meta')); }

          var slider = el('input', null, 'score-slider');
          slider.type = 'range';
          slider.min = '0';
          slider.max = String(c.maxScore);
          slider.step = '0.5';
          slider.value = c.existingRawScore !== null ? String(c.existingRawScore) : '0';
          slider.disabled = task.isLocked;

          slider.addEventListener('input', function () {
            scoreDisplay.textContent = slider.value + ' / ' + c.maxScore;
          });

          cRow.appendChild(slider);
          form.appendChild(cRow);
          inputs.push({ criterionId: c.criterionId, input: slider });
        });

        if (!task.isLocked) {
          var submitBtn = el('button', 'Submit & Lock Scores');
          submitBtn.type = 'submit';
          var statusMsg = el('p', null, 'meta');
          form.appendChild(submitBtn);
          form.appendChild(statusMsg);

          form.addEventListener('submit', function (e) {
            e.preventDefault();
            if (!confirm('Scores lock permanently once submitted. Do you confirm your evaluation?')) { return; }
            var scoresPayload = inputs.map(function (item) {
              return { criterionId: item.criterionId, rawScore: parseFloat(item.input.value) };
            });

            api('/api/v1/scores/submit', {
              method: 'POST',
              body: { assignmentId: task.assignmentId, criteriaScores: scoresPayload }
            }).then(function (res) {
              statusMsg.textContent = 'Scores submitted and locked! Total weighted score: ' + res.totalWeightedScore;
              statusMsg.className = 'success';
              loadJudgeTasks();
              loadLeaderboard();
            }).catch(function (err) {
              statusMsg.textContent = err.message;
              statusMsg.className = 'error';
            });
          });
        } else {
          box.appendChild(el('p', '✓ Scores have been submitted and locked into the immutable audit trail.', 'success'));
        }

        box.appendChild(form);
        container.appendChild(box);
      });
    }).catch(function (err) {
      container.textContent = 'Failed to load judging tasks: ' + err.message;
    });
  }

  /* ---------- Admin Judge Assignment & COI Check ---------- */
  function loadAssignData() {
    api('/api/v1/judging/judges').then(function (judges) {
      var sel = $('assign-judge');
      sel.innerHTML = '';
      judges.forEach(function (j) {
        var opt = el('option', j.fullName + ' (' + j.affiliation + ')');
        opt.value = j.userId;
        sel.appendChild(opt);
      });
    });

    $('assign-round').addEventListener('change', function () {
      loadTeamsForAssign($('assign-round').value);
    });

    if ($('assign-round').value) {
      loadTeamsForAssign($('assign-round').value);
    }
    loadCurrentAssignments();
  }

  function loadTeamsForAssign(roundId) {
    if (!roundId) { return; }
    api('/api/v1/judging/teams/' + roundId).then(function (teams) {
      var sel = $('assign-team');
      sel.innerHTML = '';
      teams.forEach(function (t) {
        var affs = t.memberAffiliations || t.leadAffiliation;
        var opt = el('option', t.teamName + ' · ' + t.leadName + ' (' + affs + ')');
        opt.value = t.teamId;
        sel.appendChild(opt);
      });
    });
  }

  $('assign-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var msg = $('assign-msg');
    msg.textContent = 'Verifying conflict of interest...';
    msg.className = 'meta';

    api('/api/v1/judging/assign', {
      method: 'POST',
      body: {
        roundId: parseInt($('assign-round').value, 10),
        judgeUserId: parseInt($('assign-judge').value, 10),
        teamId: parseInt($('assign-team').value, 10)
      }
    }).then(function (res) {
      msg.textContent = '✓ Successfully assigned ' + res.judgeName + ' to ' + res.teamName + ' (No conflict found).';
      msg.className = 'success';
      loadCurrentAssignments();
    }).catch(function (err) {
      msg.textContent = '✗ ' + err.message;
      msg.className = 'error';
    });
  });

  function loadCurrentAssignments() {
    api('/api/v1/judging/assignments').then(function (list) {
      var container = $('assignments-list-container');
      container.innerHTML = '';
      if (!list || list.length === 0) {
        container.appendChild(el('p', 'No assignments created yet.', 'meta'));
        return;
      }
      var table = el('table');
      var thead = el('thead');
      var trh = el('tr');
      ['ID', 'Round / Event', 'Judge', 'Judge Affiliation', 'Team', 'Status'].forEach(function (h) {
        trh.appendChild(el('th', h));
      });
      thead.appendChild(trh);
      table.appendChild(thead);

      var tbody = el('tbody');
      list.forEach(function (a) {
        var tr = el('tr');
        tr.appendChild(el('td', '#' + a.assignmentId));
        tr.appendChild(el('td', a.eventTitle + ' (' + a.roundName + ')'));
        tr.appendChild(el('td', a.judgeName));
        tr.appendChild(el('td', a.judgeAffiliation));
        tr.appendChild(el('td', a.teamName));
        var badgeTd = el('td');
        badgeTd.appendChild(el('span', a.status, 'badge ' + a.status));
        tr.appendChild(badgeTd);
        tbody.appendChild(tr);
      });
      table.appendChild(tbody);
      container.appendChild(table);
    });
  }

  // Initial load
  loadRounds();
})();
