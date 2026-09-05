(() => {
  const qs = (s, c = document) => c.querySelector(s);
  const qsa = (s, c = document) => Array.from(c.querySelectorAll(s));

  qsa('[data-print-page]').forEach(btn => btn.addEventListener('click', () => window.print()));

  qsa('[data-client-filter]').forEach(root => {
    const rows = qsa('[data-filter-row]', root);
    const controls = qsa('[data-filter-control]', root);
    const apply = () => {
      rows.forEach(row => {
        const haystack = (row.dataset.search || row.textContent || '').toLowerCase();
        const visible = controls.every(control => {
          const value = (control.value || '').trim().toLowerCase();
          if (!value) return true;
          const field = control.dataset.filterField;
          if (!field) return haystack.includes(value);
          return (row.dataset[field] || '').toLowerCase() === value;
        });
        row.hidden = !visible;
      });
      const visibleCount = rows.filter(r => !r.hidden).length;
      const out = qs('[data-visible-count]', root);
      if (out) out.textContent = String(visibleCount);
    };
    controls.forEach(c => c.addEventListener(c.tagName === 'INPUT' ? 'input' : 'change', apply));
    apply();
  });

  const refreshLineNumbers = root => qsa('[data-line]', root).forEach((line, index) => {
    const badge = qs('[data-line-number]', line);
    if (badge) badge.textContent = `Item ${index + 1}`;
    qsa('[name]', line).forEach(input => input.name = input.name.replace(/\[\d+\]/, `[${index}]`));
  });

  qsa('[data-repeat-root]').forEach(root => {
    const list = qs('[data-repeat-list]', root);
    const template = qs('template[data-repeat-template]', root);
    const add = qs('[data-repeat-add]', root);
    if (!list || !template || !add) return;
    add.addEventListener('click', () => {
      const index = qsa('[data-line]', list).length;
      const html = template.innerHTML.replaceAll('__INDEX__', index);
      list.insertAdjacentHTML('beforeend', html);
      refreshLineNumbers(list);
      hydrateLine(qs('[data-line]:last-child', list));
    });
    list.addEventListener('click', e => {
      const remove = e.target.closest('[data-line-remove]');
      if (!remove) return;
      const lines = qsa('[data-line]', list);
      if (lines.length <= 1) return;
      remove.closest('[data-line]').remove();
      refreshLineNumbers(list);
    });
  });

  function hydrateLine(line) {
    if (!line) return;
    const variant = qs('[data-variant-select]', line);
    const batch = qs('[data-batch-select]', line);
    const unit = qs('[data-unit-select]', line);
    const qty = qs('[data-qty-input]', line);
    const preview = qs('[data-line-preview]', line);
    const update = () => {
      const v = variant?.selectedOptions?.[0];
      if (batch && variant) {
        qsa('option[data-variant-id]', batch).forEach(option => {
          const matches = !variant.value || option.dataset.variantId === variant.value;
          option.hidden = !matches;
          option.disabled = !matches;
        });
        if (batch.selectedOptions[0]?.disabled) batch.value = '';
      }
      if (!preview) return;
      const b = batch?.selectedOptions?.[0];
      const u = unit?.selectedOptions?.[0];
      const parts = [];
      if (v?.value) parts.push(v.dataset.label || v.textContent.trim());
      if (b?.value) parts.push(`Batch ${b.dataset.batchNumber || b.textContent.trim()}`);
      if (qty?.value) parts.push(`${qty.value} ${u?.dataset.code || u?.textContent.trim() || ''}`.trim());
      preview.textContent = parts.length ? parts.join(' · ') : 'Select an item to preview the inventory context.';
    };
    [variant, batch, unit].filter(Boolean).forEach(el => el.addEventListener('change', update));
    if (qty) qty.addEventListener('input', update);
    update();
  }
  qsa('[data-line]').forEach(hydrateLine);

  qsa('[data-scope-select]').forEach(select => {
    const root = select.closest('form') || document;
    const selections = qs('[data-scope-selections]', root);
    const update = () => {
      if (!selections) return;
      const branchWide = select.value === 'BRANCH';
      selections.classList.toggle('inv-hidden', branchWide);
      qsa('select,input', selections).forEach(el => el.disabled = branchWide);
    };
    select.addEventListener('change', update); update();
  });



  qsa('[data-context-variant]').forEach(variant => {
    const form = variant.closest('form') || document;
    const batch = qs('[data-context-batch]', form);
    const output = qs('[data-context-output]', form);
    const update = () => {
      const selected = variant.selectedOptions[0];
      if (batch) {
        qsa('option[data-variant-id]', batch).forEach(option => {
          const matches = !variant.value || option.dataset.variantId === variant.value;
          option.hidden = !matches; option.disabled = !matches;
        });
        if (batch.selectedOptions[0]?.disabled) batch.value = '';
      }
      if (!output || !selected) return;
      const stock = qs('[data-context-stock]', output);
      const wac = qs('[data-context-wac]', output);
      const unit = qs('[data-context-unit]', output);
      const tracking = qs('[data-context-tracking]', output);
      if (stock) stock.textContent = selected.dataset.stock || '—';
      if (wac) wac.textContent = selected.dataset.wac || '—';
      if (unit) unit.textContent = selected.dataset.baseUnit || '—';
      if (tracking) tracking.textContent = selected.dataset.tracking || '—';
    };
    variant.addEventListener('change', update); update();
  });

  qsa('[data-confirm]').forEach(form => form.addEventListener('submit', e => {
    const message = form.dataset.confirm || 'Continue with this action?';
    if (!window.confirm(message)) e.preventDefault();
  }));

  const requestedBatchId = new URLSearchParams(window.location.search).get('batchId');
  if (requestedBatchId) {
    const batchSelect = document.querySelector('[data-context-batch]');
    const batchOption = batchSelect?.querySelector(`option[value="${CSS.escape(requestedBatchId)}"]`);
    if (batchOption) {
      const variantSelect = document.querySelector('[data-context-variant]');
      if (variantSelect && batchOption.dataset.variantId) {
        variantSelect.value = batchOption.dataset.variantId;
        variantSelect.dispatchEvent(new Event('change', { bubbles: true }));
      }
      batchSelect.value = requestedBatchId;
      batchSelect.dispatchEvent(new Event('change', { bubbles: true }));
    }
  }


  const lockSubmittingForms = () => {
    qsa('form').forEach(form => {
      form.addEventListener('submit', event => {
        if (event.defaultPrevented || !form.checkValidity()) return;
        if (form.dataset.submitting === 'true') {
          event.preventDefault();
          return;
        }
        form.dataset.submitting = 'true';
        form.setAttribute('aria-busy', 'true');
        qsa('button:not([type="button"]):not([type="reset"]), input[type="submit"]', form).forEach(control => {
          if (!control.disabled) {
            control.dataset.submitLocked = 'true';
            control.disabled = true;
          }
        });
      });
    });
  };
  lockSubmittingForms();
})();
