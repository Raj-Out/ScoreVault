(function () {
  'use strict';

  var TOKEN_KEY = 'sv_token';
  var USER_KEY = 'sv_user';
  var page = document.body.dataset.page || '';
  var $ = function (id) { return document.getElementById(id); };

  function currentUser() {
    try { return JSON.parse(sessionStorage.getItem(USER_KEY) || 'null'); }
    catch (error) { return null; }
  }

  function clearSession() {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    updateAccount();
  }

  function updateAccount() {
    var user = currentUser();
    var name = $('account-name');
    var signIn = $('sign-in-link');
    var logout = $('logout-button');
    if (name) { name.textContent = user ? (user.fullName || user.email || 'Signed in') + ' · ' + user.role : ''; }
    if (signIn) { signIn.hidden = Boolean(user); }
    if (logout) { logout.hidden = !user; }
  }

  async function api(path, options) {
    options = options || {};
    var headers = {};
    var token = sessionStorage.getItem(TOKEN_KEY);
    if (options.body !== undefined) { headers['Content-Type'] = 'application/json'; }
    if (token) { headers.Authorization = 'Bearer ' + token; }
    var response = await fetch(path, {
      method: options.method || 'GET',
      headers: headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body)
    });
    var data = await response.json().catch(function () { return {}; });
    if (!response.ok) {
      var error = new Error(data.error || 'Request failed (' + response.status + ').');
      error.status = response.status;
      if (response.status === 401 && token && path.indexOf('/auth/login') === -1) {
        clearSession();
        error.message = 'Your session expired. Please sign in again.';
      }
      throw error;
    }
    return data;
  }

  function status(id, message, kind) {
    var element = $(id);
    if (!element) { return; }
    element.textContent = message || '';
    element.className = 'form-message' + (kind ? ' ' + kind : '');
  }

  function option(select, value, label) {
    var item = document.createElement('option');
    item.value = value;
    item.textContent = label;
    select.appendChild(item);
    return item;
  }

  function makeCell(row, value) {
    var cell = document.createElement('td');
    cell.textContent = value === null || value === undefined || value === '' ? '—' : String(value);
    row.appendChild(cell);
  }

  function makeTable(headers, rows) {
    var table = document.createElement('table');
    var thead = document.createElement('thead');
    var headRow = document.createElement('tr');
    headers.forEach(function (label) {
      var cell = document.createElement('th');
      cell.scope = 'col';
      cell.textContent = label;
      headRow.appendChild(cell);
    });
    thead.appendChild(headRow);
    table.appendChild(thead);
    var body = document.createElement('tbody');
    rows.forEach(function (values) {
      var row = document.createElement('tr');
      values.forEach(function (value) { makeCell(row, value); });
      body.appendChild(row);
    });
    table.appendChild(body);
    return table;
  }

  function loginLink() {
    var link = document.createElement('a');
    link.className = 'button';
    link.href = '/login.html';
    link.textContent = 'Sign in';
    return link;
  }

  function requireRole(role, noticeId, workspaceId) {
    var user = currentUser();
    var notice = $(noticeId);
    var workspace = $(workspaceId);
    if (user && user.role === role) {
      workspace.hidden = false;
      return true;
    }
    if (notice) {
      notice.hidden = false;
      notice.replaceChildren();
      notice.textContent = user
        ? 'This page is for ' + role.toLowerCase() + ' accounts. Sign in with an account that has the required role.'
        : 'Sign in with your ' + role.toLowerCase() + ' account to use this workspace. ';
      if (!user) { notice.appendChild(loginLink()); }
    }
    return false;
  }

  function initHeader() {
    updateAccount();
    var logout = $('logout-button');
    if (logout) {
      logout.addEventListener('click', function () {
        clearSession();
        window.location.href = '/login.html';
      });
    }
  }

  function initHome() {
    api('/api/v1/rounds').then(function (rounds) {
      var element = $('home-status');
      if (element) { element.textContent = rounds.length + (rounds.length === 1 ? ' round is' : ' rounds are') + ' available.'; }
    }).catch(function () {
      var element = $('home-status');
      if (element) { element.textContent = 'Round data is temporarily unavailable.'; }
    });
  }

  function initLogin() {
    var form = $('login-form');
    if (!form) { return; }
    var pendingGoogleCredential = null;
    var profileForm = $('google-profile-form');
    function finishLogin(result) {
      sessionStorage.setItem(TOKEN_KEY, result.token);
      sessionStorage.setItem(USER_KEY, JSON.stringify(result.user));
      updateAccount();
      if (result.user.role === 'ADMIN') { window.location.href = '/admin.html'; }
      else if (result.user.role === 'JUDGE') { window.location.href = '/judging.html'; }
      else { window.location.href = '/submissions.html'; }
    }
    async function googleLogin(credential, affiliation) {
      var result = await api('/api/v1/auth/google', {
        method: 'POST', body: { credential: credential, affiliation: affiliation || '' }
      });
      pendingGoogleCredential = null;
      finishLogin(result);
    }
    async function setupGoogleSignIn() {
      var buttonContainer = $('google-button');
      if (!buttonContainer) { return; }
      try {
        var config = await api('/api/v1/auth/google/config');
        if (!config.enabled) {
          status('google-status', 'Google sign-in is not configured yet.');
          return;
        }
        var attempts = 0;
        var timer = window.setInterval(function () {
          attempts += 1;
          if (window.google && window.google.accounts && window.google.accounts.id) {
            window.clearInterval(timer);
            window.google.accounts.id.initialize({
              client_id: config.clientId,
              callback: function (response) {
                pendingGoogleCredential = response.credential;
                if (profileForm) { profileForm.hidden = true; }
                status('google-status', 'Verifying your Google account…');
                googleLogin(pendingGoogleCredential, '').catch(function (error) {
                  if (error.status === 428) {
                    if (profileForm) {
                      profileForm.hidden = false;
                      $('google-affiliation').required = true;
                    }
                    status('google-status', 'Add your school or organization to finish creating your participant account.');
                    return;
                  }
                  status('google-status', error.message, 'error');
                });
              }
            });
            window.google.accounts.id.renderButton(buttonContainer, {
              type: 'standard', theme: 'outline', size: 'large', text: 'continue_with', shape: 'rectangular'
            });
          } else if (attempts >= 40) {
            window.clearInterval(timer);
            status('google-status', 'Google sign-in could not load. Check your connection or browser settings.', 'error');
          }
        }, 250);
      } catch (error) {
        status('google-status', error.message, 'error');
      }
    }
    setupGoogleSignIn();
    if (profileForm) {
      profileForm.addEventListener('submit', async function (event) {
        event.preventDefault();
        if (!pendingGoogleCredential) {
          status('google-status', 'Please choose Continue with Google again.', 'error');
          profileForm.hidden = true;
          return;
        }
        var button = $('google-profile-submit');
        button.disabled = true;
        status('google-status', 'Creating your participant account…');
        try {
          await googleLogin(pendingGoogleCredential, $('google-affiliation').value.trim());
        } catch (error) {
          status('google-status', error.message, 'error');
        } finally {
          button.disabled = false;
        }
      });
    }
    var password = $('password');
    var passwordToggle = document.querySelector('.password-toggle');
    if (passwordToggle) {
      passwordToggle.addEventListener('click', function () {
        var visible = password.type === 'password';
        password.type = visible ? 'text' : 'password';
        passwordToggle.textContent = visible ? 'Hide' : 'Show';
        passwordToggle.setAttribute('aria-pressed', String(visible));
      });
    }
    form.addEventListener('submit', async function (event) {
      event.preventDefault();
      var button = $('login-button');
      status('login-status', 'Signing in…');
      button.disabled = true;
      try {
        var result = await api('/api/v1/auth/login', {
          method: 'POST',
          body: { email: $('email').value.trim(), password: $('password').value }
        });
        $('password').value = '';
        finishLogin(result);
      } catch (error) {
        status('login-status', error.message, 'error');
        button.disabled = false;
      }
    });
  }

  function initLeaderboard() {
    var select = $('leaderboard-round');
    var container = $('leaderboard-table-container');
    if (!select || !container) { return; }

    async function loadRows() {
      var roundId = select.value;
      if (!roundId) { return; }
      container.textContent = 'Loading results…';
      status('leaderboard-status', '');
      try {
        var rows = await api('/api/v1/leaderboard/' + encodeURIComponent(roundId));
        container.replaceChildren();
        if (!rows.length) {
          var empty = document.createElement('p');
          empty.className = 'muted';
          empty.textContent = 'No published scores are available for this round yet.';
          container.appendChild(empty);
          status('leaderboard-status', 'No results yet.');
          return;
        }
        container.appendChild(makeTable(
          ['Rank', 'Team', 'Team lead', 'Institution', 'Judges', 'Weighted score', 'Tie-break score'],
          rows.map(function (row) {
            return [row.leaderboardRank, row.teamName, row.teamLead, row.institution,
              row.judgesEvaluatedCount, row.finalWeightedAverageScore === null ? '—' : Number(row.finalWeightedAverageScore).toFixed(2),
              row.priorityCriterionScore === null ? '—' : Number(row.priorityCriterionScore).toFixed(2)];
          })
        ));
        status('leaderboard-status', rows.length + ' results');
      } catch (error) {
        container.textContent = error.message;
        status('leaderboard-status', 'Could not load results.', 'error');
      }
    }

    api('/api/v1/rounds').then(function (rounds) {
      select.replaceChildren();
      if (!rounds.length) {
        option(select, '', 'No rounds available');
        container.textContent = 'There are no rounds to display.';
        return;
      }
      rounds.forEach(function (round) {
        option(select, round.roundId, round.eventTitle + ' · ' + round.roundName);
      });
      loadRows();
    }).catch(function (error) {
      select.replaceChildren();
      option(select, '', 'Rounds unavailable');
      container.textContent = error.message;
      status('leaderboard-status', 'Could not load rounds.', 'error');
    });
    select.addEventListener('change', loadRows);
    $('refresh-leaderboard').addEventListener('click', loadRows);
  }

  function initAdmin() {
    if (!requireRole('ADMIN', 'admin-access', 'admin-workspace')) { return; }

    var adminTabs = Array.from(document.querySelectorAll('[data-admin-tab]'));
    adminTabs.forEach(function (tab, index) {
      function activate(focus) {
        adminTabs.forEach(function (item) {
          var selected = item === tab;
          item.classList.toggle('is-active', selected);
          item.setAttribute('aria-selected', String(selected));
          item.tabIndex = selected ? 0 : -1;
          var panel = $(item.getAttribute('data-admin-tab'));
          if (panel) { panel.hidden = !selected; }
        });
        if (focus) { tab.focus(); }
      }
      tab.addEventListener('click', function () { activate(false); });
      tab.addEventListener('keydown', function (event) {
        var nextIndex = index;
        if (event.key === 'ArrowRight') { nextIndex = (index + 1) % adminTabs.length; }
        else if (event.key === 'ArrowLeft') { nextIndex = (index - 1 + adminTabs.length) % adminTabs.length; }
        else if (event.key === 'Home') { nextIndex = 0; }
        else if (event.key === 'End') { nextIndex = adminTabs.length - 1; }
        else { return; }
        event.preventDefault();
        adminTabs[nextIndex].click();
        adminTabs[nextIndex].focus();
      });
    });

    var categorySelect = $('category');
    var roundEventSelect = $('round-event');
    var roundSelect = $('assign-round');
    var judgeSelect = $('assign-judge');
    var teamSelect = $('assign-team');
    var criteriaList = $('criteria-list');

    api('/api/v1/admin/events').then(function (events) {
      roundEventSelect.replaceChildren();
      events.forEach(function (event) {
        option(roundEventSelect, event.eventId, event.title + ' · ' + event.categoryName + ' · ' + event.status);
      });
      if (!events.length) { option(roundEventSelect, '', 'No events available'); }
    }).catch(function (error) { status('round-status', error.message, 'error'); });

    function loadRounds(selectedId) {
      api('/api/v1/rounds').then(function (rounds) {
        roundSelect.replaceChildren();
        rounds.forEach(function (round) { option(roundSelect, round.roundId, round.eventTitle + ' · ' + round.roundName); });
        if (selectedId) { roundSelect.value = String(selectedId); }
        loadTeams();
      }).catch(function (error) { status('assign-status', error.message, 'error'); });
    }

    $('round-form').addEventListener('submit', async function (event) {
      event.preventDefault();
      var button = $('save-round');
      button.disabled = true;
      status('round-status', 'Adding round…');
      try {
        var result = await api('/api/v1/admin/rounds', {
          method: 'POST',
          body: {
            eventId: Number(roundEventSelect.value),
            roundNumber: Number($('round-number').value),
            roundName: $('round-name').value.trim(),
            isFinal: $('round-final').checked
          }
        });
        status('round-status', 'Round added. You can now assign judges to it.', 'success');
        $('round-form').reset();
        await loadRounds(result.roundId);
      } catch (error) { status('round-status', error.message, 'error'); }
      finally { button.disabled = false; }
    });

    function updateWeightTotal() {
      var total = Array.from(criteriaList.querySelectorAll('[name="weightage"]'))
        .reduce(function (sum, input) { return sum + (parseFloat(input.value) || 0); }, 0);
      var label = $('weight-total');
      label.textContent = 'Total weight: ' + total.toFixed(2) + '% (must equal 100%)';
      label.className = total === 100 ? 'form-message success' : 'form-message';
    }

    function addCriterion() {
      var row = document.createElement('div');
      row.className = 'criterion-row';
      [
        { name: 'criterion', label: 'Criterion', placeholder: 'e.g. Originality', type: 'text' },
        { name: 'maxScore', label: 'Max score', placeholder: '10', type: 'number', value: '10' },
        { name: 'weightage', label: 'Weight %', placeholder: '25', type: 'number', value: '' }
      ].forEach(function (field) {
        var wrapper = document.createElement('div');
        wrapper.className = 'criterion-field';
        var label = document.createElement('label');
        label.textContent = field.label;
        var input = document.createElement('input');
        input.name = field.name;
        input.type = field.type;
        input.placeholder = field.placeholder;
        input.required = true;
        if (field.value) { input.value = field.value; }
        if (field.type === 'number') { input.min = '0.01'; input.max = '100'; input.step = '0.01'; }
        if (field.name === 'weightage') { input.addEventListener('input', updateWeightTotal); }
        wrapper.append(label, input);
        row.appendChild(wrapper);
      });
      var remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'button button-small button-secondary remove-criterion';
      remove.textContent = 'Remove';
      remove.addEventListener('click', function () { row.remove(); updateWeightTotal(); });
      row.appendChild(remove);
      criteriaList.appendChild(row);
      updateWeightTotal();
    }

    $('add-criterion').addEventListener('click', addCriterion);
    addCriterion();

    api('/api/v1/categories').then(function (categories) {
      categorySelect.replaceChildren();
      categories.forEach(function (category) { option(categorySelect, category.id, category.name); });
    }).catch(function (error) { status('rubric-status', error.message, 'error'); });

    loadRounds();

    api('/api/v1/judging/judges').then(function (judges) {
      judgeSelect.replaceChildren();
      judges.forEach(function (judge) { option(judgeSelect, judge.userId, judge.fullName + ' · ' + judge.affiliation); });
    }).catch(function (error) { status('assign-status', error.message, 'error'); });

    async function loadTeams() {
      if (!roundSelect.value) { return; }
      try {
        var teams = await api('/api/v1/judging/teams/' + encodeURIComponent(roundSelect.value));
        teamSelect.replaceChildren();
        teams.forEach(function (team) { option(teamSelect, team.teamId, team.teamName + ' · ' + team.leadName); });
        if (!teams.length) { option(teamSelect, '', 'No teams in this round'); }
      } catch (error) { status('assign-status', error.message, 'error'); }
    }
    roundSelect.addEventListener('change', loadTeams);

    async function loadAssignments() {
      var container = $('assignments-list-container');
      container.textContent = 'Loading assignments…';
      try {
        var assignments = await api('/api/v1/judging/assignments');
        container.replaceChildren();
        if (!assignments.length) {
          var empty = document.createElement('p');
          empty.className = 'muted';
          empty.textContent = 'No judge assignments yet.';
          container.appendChild(empty);
          return;
        }
        container.appendChild(makeTable(['Round', 'Judge', 'Institution', 'Team', 'Status'], assignments.map(function (item) {
          return [item.eventTitle + ' · ' + item.roundName, item.judgeName, item.judgeAffiliation, item.teamName, item.status];
        })));
      } catch (error) { container.textContent = error.message; }
    }
    $('refresh-assignments').addEventListener('click', loadAssignments);
    loadAssignments();

    async function loadUsers() {
      var container = $('users-list');
      container.textContent = 'Loading accounts…';
      try {
        var users = await api('/api/v1/admin/users');
        container.replaceChildren();
        if (!users.length) {
          var empty = document.createElement('p');
          empty.className = 'muted';
          empty.textContent = 'No accounts found.';
          container.appendChild(empty);
          return;
        }
        container.appendChild(makeTable(['Name', 'Email', 'Institution', 'Role', 'Status'], users.map(function (item) {
          return [item.fullName, item.email, item.affiliation, item.role, item.active ? 'Active' : 'Inactive'];
        })));
      } catch (error) { container.textContent = error.message; }
    }
    $('refresh-users').addEventListener('click', loadUsers);
    loadUsers();

    $('user-form').addEventListener('submit', async function (event) {
      event.preventDefault();
      var button = $('create-user');
      button.disabled = true;
      status('user-status', 'Creating account…');
      try {
        var user = await api('/api/v1/admin/users', {
          method: 'POST',
          body: {
            fullName: $('user-name').value.trim(),
            email: $('user-email').value.trim(),
            affiliation: $('user-affiliation').value.trim(),
            role: $('user-role').value,
            initialPassword: $('user-password').value
          }
        });
        $('user-form').reset();
        status('user-status', user.role + ' account created for ' + user.email + '.', 'success');
        await loadUsers();
      } catch (error) { status('user-status', error.message, 'error'); }
      finally { button.disabled = false; }
    });

    api('/api/v1/rubrics').then(function (rubrics) {
      var list = $('rubric-list');
      list.replaceChildren();
      if (!rubrics.length) {
        var empty = document.createElement('p');
        empty.className = 'muted';
        empty.textContent = 'No saved rubrics yet.';
        list.appendChild(empty);
        return;
      }
      rubrics.forEach(function (rubric) {
        var card = document.createElement('article');
        card.className = 'task-card';
        var title = document.createElement('h3');
        title.textContent = rubric.name + ' · ' + rubric.categoryName;
        card.appendChild(title);
        if (rubric.description) {
          var description = document.createElement('p');
          description.className = 'muted';
          description.textContent = rubric.description;
          card.appendChild(description);
        }
        card.appendChild(makeTable(['Criterion', 'Max score', 'Weight'], rubric.criteria.map(function (criterion) {
          return [criterion.name, criterion.maxScore, criterion.weightage + '%'];
        })));
        list.appendChild(card);
      });
    }).catch(function (error) { $('rubric-list').textContent = error.message; });

    $('rubric-form').addEventListener('submit', async function (event) {
      event.preventDefault();
      status('rubric-status', 'Saving rubric…');
      var criteria = Array.from(criteriaList.querySelectorAll('.criterion-row')).map(function (row) {
        return {
          name: row.querySelector('[name="criterion"]').value.trim(),
          maxScore: Number(row.querySelector('[name="maxScore"]').value),
          weightage: Number(row.querySelector('[name="weightage"]').value)
        };
      });
      var total = criteria.reduce(function (sum, criterion) { return sum + criterion.weightage; }, 0);
      if (Math.abs(total - 100) > 0.0001) {
        status('rubric-status', 'Criterion weights must total 100%.', 'error');
        return;
      }
      var button = $('save-rubric');
      button.disabled = true;
      try {
        await api('/api/v1/rubrics', {
          method: 'POST',
          body: {
            categoryId: Number(categorySelect.value),
            name: $('rubric-name').value.trim(),
            description: $('rubric-desc').value.trim(),
            criteria: criteria
          }
        });
        status('rubric-status', 'Rubric saved.', 'success');
        $('rubric-form').reset();
        criteriaList.replaceChildren();
        addCriterion();
        var saved = await api('/api/v1/rubrics');
        $('rubric-list').replaceChildren();
        saved.forEach(function (rubric) {
          var p = document.createElement('p');
          p.className = 'muted';
          p.textContent = rubric.name + ' · ' + rubric.categoryName + ' (' + rubric.criteria.length + ' criteria)';
          $('rubric-list').appendChild(p);
        });
      } catch (error) { status('rubric-status', error.message, 'error'); }
      finally { button.disabled = false; }
    });

    $('assign-form').addEventListener('submit', async function (event) {
      event.preventDefault();
      status('assign-status', 'Checking for conflicts…');
      var button = $('assign-submit');
      button.disabled = true;
      try {
        var assignment = await api('/api/v1/judging/assign', {
          method: 'POST',
          body: {
            roundId: Number(roundSelect.value),
            judgeUserId: Number(judgeSelect.value),
            teamId: Number(teamSelect.value)
          }
        });
        status('assign-status', 'Assigned ' + assignment.judgeName + ' to ' + assignment.teamName + '.', 'success');
        await loadAssignments();
      } catch (error) { status('assign-status', error.message, 'error'); }
      finally { button.disabled = false; }
    });
  }

  function initTeams() {
    if (!requireRole('PARTICIPANT', 'team-access', 'team-workspace')) { return; }
    var eventSelect = $('team-event');
    var list = $('my-teams');

    async function loadMine() {
      list.textContent = 'Loading teams…';
      try {
        var teams = await api('/api/v1/teams/mine');
        list.replaceChildren();
        if (!teams.length) {
          var empty = document.createElement('p');
          empty.className = 'muted';
          empty.textContent = 'You have not joined a team yet.';
          list.appendChild(empty);
          return;
        }
        teams.forEach(function (team) {
          var card = document.createElement('article');
          card.className = 'task-card';
          var heading = document.createElement('h3');
          heading.textContent = team.teamName;
          var details = document.createElement('p');
          details.className = 'muted';
          details.textContent = team.eventTitle + ' · Team lead: ' + team.leadName;
          card.append(heading, details);
          if (team.members.length) {
            card.appendChild(makeTable(['Member', 'Email', 'Institution'], team.members.map(function (member) {
              return [member.fullName, member.email, member.affiliation];
            })));
          } else {
            var solo = document.createElement('p');
            solo.className = 'muted';
            solo.textContent = 'No additional members.';
            card.appendChild(solo);
          }
          list.appendChild(card);
        });
      } catch (error) { list.textContent = error.message; }
    }

    api('/api/v1/teams/events').then(function (events) {
      eventSelect.replaceChildren();
      events.forEach(function (event) { option(eventSelect, event.eventId, event.title + ' · ' + event.categoryName); });
      if (!events.length) { option(eventSelect, '', 'No open events available'); }
    }).catch(function (error) { status('team-status', error.message, 'error'); });

    $('team-form').addEventListener('submit', async function (event) {
      event.preventDefault();
      var emails = $('team-members').value.split(/[\n,;]+/).map(function (email) { return email.trim(); }).filter(Boolean);
      if (emails.length > 10) { status('team-status', 'A team can have at most 10 additional members.', 'error'); return; }
      var button = $('create-team');
      button.disabled = true;
      status('team-status', 'Creating team…');
      try {
        var team = await api('/api/v1/teams', {
          method: 'POST',
          body: { eventId: Number(eventSelect.value), teamName: $('team-name').value.trim(), memberEmails: emails }
        });
        status('team-status', 'Team created successfully.', 'success');
        $('team-form').reset();
        await loadMine();
      } catch (error) { status('team-status', error.message, 'error'); }
      finally { button.disabled = false; }
    });
    $('refresh-teams').addEventListener('click', loadMine);
    loadMine();
  }

  function initJudging() {
    if (!requireRole('JUDGE', 'judging-access', 'judge-tasks-container')) { return; }
    var container = $('judge-tasks-container');

    async function loadTasks() {
      container.textContent = 'Loading assigned evaluations…';
      try {
        var tasks = await api('/api/v1/scores/my-tasks');
        container.replaceChildren();
        if (!tasks.length) {
          var empty = document.createElement('p');
          empty.className = 'muted';
          empty.textContent = 'You have no assigned entries to score.';
          container.appendChild(empty);
          return;
        }
        tasks.forEach(function (task) {
          var card = document.createElement('article');
          card.className = 'task-card';
          var title = document.createElement('h2');
          title.textContent = task.teamName;
          card.appendChild(title);
          var meta = document.createElement('p');
          meta.className = 'task-meta';
          meta.textContent = task.eventTitle + ' · ' + task.roundName + ' · ' + task.teamLead + ' · ' + task.status;
          card.appendChild(meta);
          if (task.submissionBlobUrl) {
            var artifact = document.createElement('a');
            artifact.href = task.submissionBlobUrl;
            artifact.target = '_blank';
            artifact.rel = 'noopener noreferrer';
            artifact.textContent = task.submissionFileName || 'Open submission';
            card.appendChild(artifact);
          } else if (task.submissionNotes) {
            var notes = document.createElement('p');
            notes.className = 'muted';
            notes.textContent = task.submissionNotes;
            card.appendChild(notes);
          }
          if (task.isLocked) {
            var locked = document.createElement('p');
            locked.className = 'success';
            locked.textContent = 'Scores submitted and locked.';
            card.appendChild(locked);
            container.appendChild(card);
            return;
          }
          var form = document.createElement('form');
          form.className = 'form-stack';
          var controls = [];
          task.criteria.forEach(function (criterion) {
            var line = document.createElement('label');
            line.className = 'score-control';
            var name = document.createElement('span');
            name.textContent = criterion.name + ' · ' + Number(criterion.weightagePercent).toFixed(1) + '% weight';
            var input = document.createElement('input');
            input.type = 'range';
            input.min = '0';
            input.max = criterion.maxScore;
            input.step = '0.5';
            input.value = criterion.existingRawScore === null ? '0' : criterion.existingRawScore;
            var value = document.createElement('span');
            value.className = 'score-value';
            value.textContent = input.value + ' / ' + criterion.maxScore;
            input.addEventListener('input', function () { value.textContent = input.value + ' / ' + criterion.maxScore; });
            line.append(name, input, value);
            form.appendChild(line);
            controls.push({ criterionId: criterion.criterionId, input: input });
          });
          var feedbackLabel = document.createElement('label');
          feedbackLabel.textContent = 'Feedback (optional)';
          var feedback = document.createElement('textarea');
          feedback.maxLength = 1000;
          feedback.rows = 2;
          form.append(feedbackLabel, feedback);
          var submit = document.createElement('button');
          submit.type = 'submit';
          submit.className = 'button';
          submit.textContent = 'Submit and lock scores';
          form.appendChild(submit);
          var message = document.createElement('p');
          message.className = 'form-message';
          message.setAttribute('role', 'status');
          form.appendChild(message);
          form.addEventListener('submit', async function (event) {
            event.preventDefault();
            if (!window.confirm('Submitting locks these scores. Continue?')) { return; }
            submit.disabled = true;
            message.textContent = 'Submitting…';
            try {
              var scores = controls.map(function (item) {
                return { criterionId: item.criterionId, rawScore: Number(item.input.value) };
              });
              await api('/api/v1/scores/submit', {
                method: 'POST',
                body: { assignmentId: task.assignmentId, criteriaScores: scores, feedback: feedback.value.trim() }
              });
              message.textContent = 'Scores submitted and locked.';
              message.className = 'form-message success';
              await loadTasks();
            } catch (error) {
              message.textContent = error.message;
              message.className = 'form-message error';
              submit.disabled = false;
            }
          });
          card.appendChild(form);
          container.appendChild(card);
        });
      } catch (error) {
        container.textContent = error.message;
      }
    }
    $('refresh-tasks').addEventListener('click', loadTasks);
    loadTasks();
  }

  document.addEventListener('DOMContentLoaded', function () {
    initHeader();
    if (page === 'home') { initHome(); }
    if (page === 'login') { initLogin(); }
    if (page === 'leaderboard') { initLeaderboard(); }
    if (page === 'admin') { initAdmin(); }
    if (page === 'judging') { initJudging(); }
    if (page === 'submissions') { initTeams(); }
  });
})();
