(() => {
  'use strict';

  const $$ = (selector, scope = document) => [...scope.querySelectorAll(selector)];

  $$('[data-print]').forEach((button) => button.addEventListener('click', () => window.print()));

  $$('[data-confirm-message]').forEach((form) => {
    form.addEventListener('submit', (event) => {
      if (!window.confirm(form.dataset.confirmMessage || 'Continue with this action?')) event.preventDefault();
    });
  });

  $$('[data-client-search]').forEach((input) => {
    const group = input.dataset.clientSearch;
    const rows = $$(`[data-search-group="${group}"] [data-search-row]`);
    input.addEventListener('input', () => {
      const query = input.value.trim().toLowerCase();
      rows.forEach((row) => {
        row.dataset.searchVisible = String(!query || row.innerText.toLowerCase().includes(query));
      });
      document.dispatchEvent(new CustomEvent('falcon:table-filter-changed', { detail: { group } }));
    });
  });

  const slugify = (value) => value
    .trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  $$('[data-category-form]').forEach((form) => {
    const name = form.querySelector('[data-category-name]');
    const slug = form.querySelector('[data-category-slug]');
    if (!name || !slug) return;
    let manuallyEdited = Boolean(slug.value.trim());
    slug.addEventListener('input', () => { manuallyEdited = Boolean(slug.value.trim()); });
    name.addEventListener('input', () => { if (!manuallyEdited) slug.value = slugify(name.value); });
  });

  const selectedPaymentIsCash = (select) => {
    const option = select?.selectedOptions?.[0];
    return option?.dataset?.cash === 'true';
  };

  const syncCashContext = (scope) => {
    const payment = scope.querySelector('[data-payment-method]');
    const context = scope.querySelector('[data-cash-context]');
    if (!payment || !context) return;
    const cash = selectedPaymentIsCash(payment);
    context.hidden = !cash;
    $$('[data-cash-required]', context).forEach((field) => {
      field.required = cash;
      if (!cash) field.removeAttribute('aria-invalid');
    });
  };

  $$('form').forEach((form) => {
    if (!form.querySelector('[data-payment-method]')) return;
    form.querySelector('[data-payment-method]').addEventListener('change', () => syncCashContext(form));
    syncCashContext(form);
  });

  const syncClassification = (form) => {
    const selected = form.querySelector('input[name="classification"]:checked')?.value
      || form.querySelector('[data-classification-select]')?.value;
    const recoverable = selected === 'RECOVERABLE_DEPOSIT_ADVANCE';
    $$('[data-operating-only]', form).forEach((element) => element.hidden = recoverable);
    $$('[data-recoverable-only]', form).forEach((element) => element.hidden = !recoverable);
    const category = form.querySelector('[data-category-select]');
    if (category) {
      category.required = !recoverable;
      if (recoverable) category.value = '';
    }
    $$('.classification-choice', form).forEach((label) => {
      label.classList.toggle('is-selected', Boolean(label.querySelector('input:checked')));
    });

    const operatingEffect = document.querySelector('[data-effect-operating]');
    const recoverableEffect = document.querySelector('[data-effect-recoverable]');
    const profitEffect = document.querySelector('[data-effect-profit]');
    if (operatingEffect) operatingEffect.textContent = recoverable ? 'Excluded' : 'Included after posting';
    if (recoverableEffect) recoverableEffect.textContent = recoverable ? 'Increased after posting' : 'Not affected';
    if (profitEffect) profitEffect.textContent = recoverable ? 'Not reduced' : 'Reduced after posting';
  };

  $$('[data-expense-form]').forEach((form) => {
    $$('input[name="classification"]', form).forEach((input) => input.addEventListener('change', () => syncClassification(form)));
    form.querySelector('[data-classification-select]')?.addEventListener('change', () => syncClassification(form));
    syncClassification(form);
    form.addEventListener('reset', () => window.setTimeout(() => {
      syncClassification(form);
      syncCashContext(form);
    }, 0));
  });

  const effectCash = document.querySelector('[data-effect-cash]');
  const primaryPayment = document.querySelector('[data-expense-form] [data-payment-method]');
  const updateEffectCash = () => {
    if (!effectCash || !primaryPayment) return;
    if (!primaryPayment.value) effectCash.textContent = 'Depends on payment method';
    else effectCash.textContent = selectedPaymentIsCash(primaryPayment) ? 'Cash outflow when Posted' : 'Non-cash reconciliation';
  };
  primaryPayment?.addEventListener('change', updateEffectCash);
  updateEffectCash();

  const categoryList = document.querySelector('[data-category-list]');
  if (categoryList) {
    const group = 'expense-categories';
    const filters = $$('[data-category-filter]', categoryList);
    const apply = () => {
      const rows = $$(`[data-search-group="${group}"] [data-search-row]`, categoryList);
      rows.forEach((row) => {
        const matches = filters.every((filter) => {
          if (!filter.value) return true;
          const key = filter.dataset.categoryFilter;
          if (key === 'date') return (row.dataset.categoryDate || '').startsWith(filter.value);
          return row.dataset[`category${key[0].toUpperCase()}${key.slice(1)}`] === filter.value;
        });
        row.dataset.filterVisible = String(matches);
      });
      document.dispatchEvent(new CustomEvent('falcon:table-filter-changed', { detail: { group } }));
    };
    filters.forEach((filter) => filter.addEventListener('change', apply));
    apply();
  }

  const setupPagination = (table) => {
    const group = table.dataset.paginatedTable;
    const card = table.closest('.module-card');
    const pager = card?.querySelector('[data-client-pagination]');
    const sizeSelect = card?.querySelector('[data-page-size]');
    if (!pager || !sizeSelect) return;
    let page = 1;

    const visibleRows = () => $$('[data-page-row]', table).filter((row) =>
      row.dataset.searchVisible !== 'false' && row.dataset.filterVisible !== 'false');

    const render = () => {
      const size = Math.max(1, Number(sizeSelect.value) || 25);
      const visible = visibleRows();
      const totalPages = Math.max(1, Math.ceil(visible.length / size));
      page = Math.min(page, totalPages);
      $$('[data-page-row]', table).forEach((row) => row.hidden = true);
      visible.slice((page - 1) * size, page * size).forEach((row) => row.hidden = false);
      pager.hidden = visible.length <= size;
      const meta = pager.querySelector('[data-page-meta]');
      const current = pager.querySelector('[data-page-current]');
      const prev = pager.querySelector('[data-page-prev]');
      const next = pager.querySelector('[data-page-next]');
      const start = visible.length ? ((page - 1) * size) + 1 : 0;
      const end = Math.min(page * size, visible.length);
      if (meta) meta.textContent = `Showing ${start}–${end} of ${visible.length}`;
      if (current) current.textContent = String(page);
      if (prev) prev.disabled = page <= 1;
      if (next) next.disabled = page >= totalPages;
    };

    sizeSelect.addEventListener('change', () => { page = 1; render(); });
    pager.querySelector('[data-page-prev]')?.addEventListener('click', () => { page = Math.max(1, page - 1); render(); });
    pager.querySelector('[data-page-next]')?.addEventListener('click', () => { page += 1; render(); });
    document.addEventListener('falcon:table-filter-changed', (event) => {
      if (event.detail?.group !== group) return;
      page = 1;
      render();
    });
    render();
  };
  $$('[data-paginated-table]').forEach(setupPagination);

  const summaryForm = document.querySelector('[data-summary-filter]');
  if (summaryForm) {
    const from = summaryForm.querySelector('[data-summary-from]');
    const to = summaryForm.querySelector('[data-summary-to]');
    const iso = (date) => {
      const y = date.getFullYear();
      const m = String(date.getMonth() + 1).padStart(2, '0');
      const d = String(date.getDate()).padStart(2, '0');
      return `${y}-${m}-${d}`;
    };
    $$('[data-date-preset]', summaryForm).forEach((button) => button.addEventListener('click', () => {
      const now = new Date();
      const start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      if (button.dataset.datePreset === 'week') {
        const mondayOffset = (start.getDay() + 6) % 7;
        start.setDate(start.getDate() - mondayOffset);
      } else if (button.dataset.datePreset === 'month') {
        start.setDate(1);
      } else if (button.dataset.datePreset === 'year') {
        start.setMonth(0, 1);
      }
      from.value = iso(start);
      to.value = iso(now);
      summaryForm.requestSubmit();
    }));
  }


  $$('[data-expense-filter-toolbar]').forEach((toolbar) => {
    const toggle = toolbar.querySelector('[data-expense-more-filters]');
    const advanced = $$('[data-expense-advanced-filter]', toolbar);
    if (!toggle || !advanced.length) return;

    const hasAppliedAdvancedFilter = advanced.some((field) => {
      const control = field.querySelector('input, select, textarea');
      if (!control || ['size', 'sort', 'branchId'].includes(control.name)) return false;
      return Boolean(String(control.value || '').trim());
    });

    let expanded = hasAppliedAdvancedFilter;
    const render = () => {
      advanced.forEach((field) => { field.hidden = !expanded; });
      toggle.setAttribute('aria-expanded', String(expanded));
      toggle.textContent = expanded ? 'Fewer filters' : 'More filters';
    };

    toggle.addEventListener('click', () => {
      expanded = !expanded;
      render();
      if (expanded) advanced.find((field) => field.querySelector('input, select, textarea'))
        ?.querySelector('input, select, textarea')?.focus();
    });
    render();
  });


  const lockSubmittingForms = () => {
    $$('form').forEach((form) => {
      form.addEventListener('submit', (event) => {
        if (event.defaultPrevented || !form.checkValidity()) return;
        if (form.dataset.submitting === 'true') {
          event.preventDefault();
          return;
        }
        form.dataset.submitting = 'true';
        form.setAttribute('aria-busy', 'true');
        $$('button:not([type="button"]):not([type="reset"]), input[type="submit"]', form).forEach((control) => {
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
