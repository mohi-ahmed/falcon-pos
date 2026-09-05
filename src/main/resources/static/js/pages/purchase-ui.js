(function () {
  'use strict';

  var root = document.querySelector('.purchase-module');
  if (!root) return;

  var successStates = ['active','approved','balanced','completed','confirmed','paid','posted','ready','received','success','valid'];
  var warningStates = ['draft','near expiry','open','partial','partially paid','partially returned','pending','ready for confirmation','submitted','supplier credit'];
  var dangerStates = ['cancelled','depleted','expired','failed','invalid','rejected','reversed','shortage','voided'];

  function normalized(text) {
    return String(text || '').trim().toLowerCase().replace(/[_-]+/g, ' ').replace(/\s+/g, ' ');
  }

  root.querySelectorAll('.ops-status').forEach(function (badge) {
    var state = normalized(badge.textContent);
    if (!state) return;
    badge.dataset.state = state;
    if (dangerStates.some(function (item) { return state.indexOf(item) !== -1; })) {
      badge.classList.add('is-danger');
    } else if (warningStates.some(function (item) { return state.indexOf(item) !== -1; })) {
      badge.classList.add('is-warning');
    } else if (successStates.some(function (item) { return state.indexOf(item) !== -1; })) {
      badge.classList.add('is-success');
    } else {
      badge.classList.add('is-neutral');
    }
  });

  root.querySelectorAll('.flash-message').forEach(function (message) {
    if (message.classList.contains('error')) {
      message.setAttribute('role', 'alert');
      message.setAttribute('aria-live', 'assertive');
    } else {
      message.setAttribute('role', 'status');
      message.setAttribute('aria-live', 'polite');
    }
  });

  root.querySelectorAll('[data-purchase-filter-primary]').forEach(function (form) {
    var primaryCount = Number.parseInt(form.dataset.purchaseFilterPrimary || '0', 10);
    var fields = Array.prototype.slice.call(form.children).filter(function (child) { return child.classList && child.classList.contains('ops-field'); });
    if (!Number.isFinite(primaryCount) || primaryCount < 1 || fields.length <= primaryCount) return;

    var advancedFields = fields.slice(primaryCount);
    var details = document.createElement('details');
    details.className = 'purchase-more-filters';
    var summary = document.createElement('summary');
    summary.textContent = 'More filters';
    var grid = document.createElement('div');
    grid.className = 'purchase-more-filter-grid';
    details.appendChild(summary);
    details.appendChild(grid);

    var actions = form.querySelector('.ops-filter-actions');
    if (actions) actions.insertAdjacentElement('afterend', details);
    else form.appendChild(details);
    advancedFields.forEach(function (field) { grid.appendChild(field); });

    var params = new URLSearchParams(window.location.search);
    details.open = advancedFields.some(function (field) {
      var control = field.querySelector('[name]');
      return control && params.has(control.name) && !['size', 'sortBy', 'sortDir'].includes(control.name);
    });
  });

  root.querySelectorAll('.ops-table td').forEach(function (cell) {
    var text = String(cell.textContent || '').trim().replace(/\s+/g, ' ');
    if (text.length > 34 && !cell.hasAttribute('title') && !cell.querySelector('button,a,input,select,textarea')) {
      cell.setAttribute('title', text);
    }
  });
}());
