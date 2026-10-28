(function () {
  'use strict';

  // The token lives only in memory (not localStorage), so a script cannot read it from storage.
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
    if (text !== undefined && text !== null) { node.textContent = text; }   // textContent: never innerHTML
    if (className) { node.className = className; }
    return node;
  }

  /* ---------- sign in / out ---------- */
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
    $('app-view').hidden = true;
    $('whoami').hidden = true;
    $('login-view').hidden = false;
    $('rubric-list').textContent = '';
  });

  function showApp() {
    $('login-view').hidden = true;
    $('app-view').hidden = false;
    $('whoami').hidden = false;
    $('who').textContent = user.name + ' (' + user.role + ')';

    var isAdmin = user.role === 'ADMIN';
    $('create-section').hidden = !isAdmin;
    if (isAdmin) {
      loadCategories();
      if ($('criteria').children.length === 0) { addCriterionRow(); addCriterionRow(); }
      updateTotal();
    }
    loadRubrics();
  }

  /* ---------- categories and rubric list ---------- */
  function loadCategories() {
    api('/api/v1/categories').then(function (categories) {
      var select = $('category');
      select.textContent = '';
      categories.forEach(function (c) {
        var option = el('option', c.name);
        option.value = c.id;
        select.appendChild(option);
      });
    }).catch(function (err) { $('rubric-msg').textContent = err.message; });
  }

  function loadRubrics() {
    var list = $('rubric-list');
    list.textContent = '';
    $('list-note').textContent = '';
    if (user.role === 'PARTICIPANT') {
      $('list-note').textContent = 'Rubric templates are visible to judges and administrators.';
      return;
    }
    api('/api/v1/rubrics').then(function (rubrics) {
      if (rubrics.length === 0) { $('list-note').textContent = 'No templates yet.'; }
      rubrics.forEach(function (r) {
        var card = el('div', null, 'card');
        card.appendChild(el('h3', r.name));
        card.appendChild(el('p', r.categoryName + ' | created by ' + r.createdBy +
          ' | total weight ' + r.totalWeight, 'meta'));
        if (r.description) { card.appendChild(el('p', r.description, 'meta')); }
        var table = el('table');
        var head = el('tr');
        ['Criterion', 'Max score', 'Weight (%)'].forEach(function (h) { head.appendChild(el('th', h)); });
        table.appendChild(head);
        r.criteria.forEach(function (c) {
          var row = el('tr');
          row.appendChild(el('td', c.name));
          row.appendChild(el('td', c.maxScore));
          row.appendChild(el('td', c.weightage));
          table.appendChild(row);
        });
        card.appendChild(table);
        list.appendChild(card);
      });
    }).catch(function (err) { $('list-note').textContent = err.message; });
  }

  /* ---------- rubric form ---------- */
  function addCriterionRow() {
    var row = el('div', null, 'criterion-row');
    var name = el('input'); name.type = 'text'; name.placeholder = 'Criterion name'; name.maxLength = 150; name.required = true;
    var max = el('input'); max.type = 'number'; max.step = '0.01'; max.min = '0.01'; max.max = '100'; max.value = '10'; max.title = 'Maximum score'; max.required = true;
    var weight = el('input'); weight.type = 'number'; weight.step = '0.01'; weight.min = '0.01'; weight.max = '100'; weight.placeholder = 'Weight %'; weight.title = 'Weight (%)'; weight.required = true;
    var remove = el('button', 'Remove', 'secondary'); remove.type = 'button';
    remove.addEventListener('click', function () { row.remove(); updateTotal(); });
    weight.addEventListener('input', updateTotal);
    row.appendChild(name); row.appendChild(max); row.appendChild(weight); row.appendChild(remove);
    $('criteria').appendChild(row);
  }

  function currentTotal() {
    var total = 0;
    $('criteria').querySelectorAll('.criterion-row').forEach(function (row) {
      var value = parseFloat(row.children[2].value);
      if (!isNaN(value)) { total += value; }
    });
    return total;
  }

  function updateTotal() {
    var total = currentTotal();
    var node = $('weight-total');
    node.textContent = 'Total weight: ' + total.toFixed(2) + ' / 100';
    node.className = 'total ' + (Math.abs(total - 100) < 0.005 ? 'ok' : 'bad');
  }

  $('add-criterion').addEventListener('click', function () { addCriterionRow(); updateTotal(); });

  $('rubric-form').addEventListener('submit', function (event) {
    event.preventDefault();
    var msg = $('rubric-msg');
    msg.className = '';
    msg.textContent = '';
    var criteria = [];
    $('criteria').querySelectorAll('.criterion-row').forEach(function (row) {
      criteria.push({
        name: row.children[0].value,
        description: '',
        maxScore: parseFloat(row.children[1].value),
        weightage: parseFloat(row.children[2].value)
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
    }).then(function (created) {
      msg.className = 'total ok';
      msg.textContent = 'Saved "' + created.name + '" (id ' + created.id + ').';
      $('rubric-name').value = '';
      $('rubric-desc').value = '';
      $('criteria').textContent = '';
      addCriterionRow(); addCriterionRow(); updateTotal();
      loadRubrics();
    }).catch(function (err) {
      msg.className = 'error';
      msg.textContent = err.message;
    });
  });
})();
