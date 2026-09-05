(() => {
  'use strict';

  const main = document.querySelector('main.operations-page');
  if (main) {
    main.id ||= 'cash-main';
    main.setAttribute('tabindex', '-1');
    if (!document.querySelector('.cash-skip-link')) {
      const skipLink = document.createElement('a');
      skipLink.className = 'cash-skip-link';
      skipLink.href = `#${main.id}`;
      skipLink.textContent = 'Skip to cash operations';
      document.body.prepend(skipLink);
    }
  }

  document.querySelectorAll('.ops-table-wrap').forEach((wrapper, index) => {
    const heading = wrapper.closest('.ops-card')?.querySelector('.ops-card-head h2');
    wrapper.setAttribute('role', 'region');
    wrapper.setAttribute('tabindex', '0');
    wrapper.setAttribute('aria-label', `${heading?.textContent.trim() || `Cash records ${index + 1}`} — scroll horizontally for more columns`);
  });

  document.querySelectorAll('[data-close-summary-counted], [data-close-summary-variance], [data-close-summary-result], [data-variance-preview], [data-variance-result]')
    .forEach((output) => output.setAttribute('aria-live', 'polite'));

  const closeAllMenus = (except = null, restoreFocus = false) => {
    document.querySelectorAll('[data-cash-menu-panel].is-open').forEach((panel) => {
      if (panel !== except) {
        panel.classList.remove('is-open');
        const trigger = panel.parentElement?.querySelector('[data-cash-menu-toggle]');
        trigger?.setAttribute('aria-expanded', 'false');
        if (restoreFocus) trigger?.focus();
      }
    });
  };

  document.querySelectorAll('[data-cash-menu-toggle]').forEach((button) => {
    const panel = button.parentElement?.querySelector('[data-cash-menu-panel]');
    if (!panel) return;
    if (!panel.id) panel.id = `cash-menu-${Math.random().toString(36).slice(2, 9)}`;
    button.setAttribute('aria-controls', panel.id);
    button.setAttribute('aria-haspopup', 'menu');
    panel.setAttribute('role', 'menu');
    panel.querySelectorAll('a, button').forEach((item) => item.setAttribute('role', 'menuitem'));
    button.addEventListener('click', (event) => {
      event.preventDefault();
      event.stopPropagation();
      const nextOpen = !panel.classList.contains('is-open');
      closeAllMenus(panel);
      panel.classList.toggle('is-open', nextOpen);
      button.setAttribute('aria-expanded', String(nextOpen));
      if (nextOpen) panel.querySelector('a, button')?.focus();
    });
    panel.addEventListener('keydown', (event) => {
      const items = [...panel.querySelectorAll('a, button')];
      const index = items.indexOf(document.activeElement);
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        const step = event.key === 'ArrowDown' ? 1 : -1;
        items[(index + step + items.length) % items.length]?.focus();
      }
      if (event.key === 'Home') { event.preventDefault(); items[0]?.focus(); }
      if (event.key === 'End') { event.preventDefault(); items.at(-1)?.focus(); }
    });
  });

  document.addEventListener('click', () => closeAllMenus());

  document.querySelectorAll('[data-cash-filter-toggle]').forEach((button) => {
    const targetId = button.getAttribute('aria-controls');
    const panel = targetId ? document.getElementById(targetId) : null;
    if (!panel) return;
    button.addEventListener('click', () => {
      const open = !panel.classList.contains('is-open');
      panel.classList.toggle('is-open', open);
      button.setAttribute('aria-expanded', String(open));
    });
  });

  const search = document.querySelector('[data-cash-search-shortcut]');
  document.addEventListener('keydown', (event) => {
    if (event.key === '/' && search && !/input|textarea|select/i.test(document.activeElement?.tagName || '')) {
      event.preventDefault();
      search.focus();
    }
    if (event.key === 'Escape') {
      closeAllMenus(null, true);
      document.querySelectorAll('.cash-advanced-filters.is-open').forEach((panel) => {
        panel.classList.remove('is-open');
        const trigger = document.querySelector(`[data-cash-filter-toggle][aria-controls="${panel.id}"]`);
        trigger?.setAttribute('aria-expanded', 'false');
        trigger?.focus();
      });
    }
  });
})();

/* Cash Management date filters: familiar DD/MM/YYYY typing + calendar picker.
   The visible text input is presentation/keyboard UX only. The original native
   date input keeps the existing name, ISO yyyy-MM-dd value and backend contract. */
(() => {
  'use strict';

  const pad2 = (value) => String(value).padStart(2, '0');

  const formatIsoDate = (value) => {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '')) return '';
    const [year, month, day] = value.split('-').map(Number);
    const probe = new Date(Date.UTC(year, month - 1, day));
    if (probe.getUTCFullYear() !== year || probe.getUTCMonth() !== month - 1 || probe.getUTCDate() !== day) return '';
    return `${pad2(day)}/${pad2(month)}/${year}`;
  };

  const parseDisplayDate = (value) => {
    const trimmed = (value || '').trim();
    if (!trimmed) return { iso: '', valid: true };
    const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(trimmed);
    if (!match) return { iso: '', valid: false };
    const day = Number(match[1]);
    const month = Number(match[2]);
    const year = Number(match[3]);
    const probe = new Date(Date.UTC(year, month - 1, day));
    const valid = probe.getUTCFullYear() === year && probe.getUTCMonth() === month - 1 && probe.getUTCDate() === day;
    return { iso: valid ? `${year}-${pad2(month)}-${pad2(day)}` : '', valid };
  };

  document.querySelectorAll('[data-cash-date-control]').forEach((control) => {
    const nativeInput = control.querySelector('[data-cash-date-native]');
    const displayInput = control.querySelector('[data-cash-date-display]');
    const pickerButton = control.querySelector('[data-cash-date-picker]');
    if (!nativeInput || !displayInput) return;

    const syncDisplayFromNative = () => {
      displayInput.value = formatIsoDate(nativeInput.value);
      displayInput.setCustomValidity('');
      displayInput.setAttribute('title', displayInput.value || 'Date format: DD/MM/YYYY');
    };

    const syncNativeFromDisplay = () => {
      const parsed = parseDisplayDate(displayInput.value);
      if (!parsed.valid) {
        displayInput.setCustomValidity('Enter a valid date in DD/MM/YYYY format.');
        return false;
      }
      displayInput.setCustomValidity('');
      nativeInput.value = parsed.iso;
      displayInput.setAttribute('title', displayInput.value || 'Date format: DD/MM/YYYY');
      return true;
    };

    displayInput.addEventListener('input', () => {
      displayInput.setCustomValidity('');
      const parsed = parseDisplayDate(displayInput.value);
      if (parsed.valid) nativeInput.value = parsed.iso;
    });
    displayInput.addEventListener('change', syncNativeFromDisplay);
    displayInput.addEventListener('blur', syncNativeFromDisplay);

    nativeInput.addEventListener('input', syncDisplayFromNative);
    nativeInput.addEventListener('change', syncDisplayFromNative);

    if (pickerButton) {
      pickerButton.addEventListener('click', () => {
        try {
          if (typeof nativeInput.showPicker === 'function') {
            nativeInput.showPicker();
          } else {
            nativeInput.focus({ preventScroll: true });
            nativeInput.click();
          }
        } catch (_) {
          nativeInput.focus({ preventScroll: true });
          nativeInput.click();
        }
      });
    }

    const form = nativeInput.form;
    if (form && !form.dataset.cashDateValidationBound) {
      form.dataset.cashDateValidationBound = 'true';
      form.addEventListener('submit', (event) => {
        let firstInvalid = null;
        form.querySelectorAll('[data-cash-date-display]').forEach((field) => {
          const wrapper = field.closest('[data-cash-date-control]');
          const hiddenDate = wrapper?.querySelector('[data-cash-date-native]');
          if (!hiddenDate) return;
          const parsed = parseDisplayDate(field.value);
          if (!parsed.valid) {
            field.setCustomValidity('Enter a valid date in DD/MM/YYYY format.');
            firstInvalid ||= field;
          } else {
            field.setCustomValidity('');
            hiddenDate.value = parsed.iso;
          }
        });
        const fromNative = form.querySelector('[data-cash-date-native][name="from"]');
        const toNative = form.querySelector('[data-cash-date-native][name="to"]');
        if (!firstInvalid && fromNative?.value && toNative?.value && fromNative.value > toNative.value) {
          const toDisplay = toNative.closest('[data-cash-date-control]')?.querySelector('[data-cash-date-display]');
          if (toDisplay) {
            toDisplay.setCustomValidity('To date must be the same as or after From date.');
            firstInvalid = toDisplay;
          }
        }
        if (firstInvalid) {
          event.preventDefault();
          firstInvalid.reportValidity();
          firstInvalid.focus();
        }
      });
    }

    syncDisplayFromNative();
  });
})();
