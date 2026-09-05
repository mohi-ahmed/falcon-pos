(() => {
  'use strict';

  const number = (value) => {
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) ? parsed : 0;
  };
  const money = (value) => Math.max(0, number(value)).toFixed(4);

  document.querySelectorAll('[data-print]').forEach((button) => button.addEventListener('click', () => window.print()));

  document.querySelectorAll('.module-status').forEach((badge) => {
    const statusClass = badge.textContent.trim().toLowerCase().replaceAll('_', '-').replaceAll(' ', '-');
    if (statusClass) badge.classList.add(statusClass);
  });

  const validationSummary = document.querySelector('[data-validation-summary]');
  if (validationSummary) {
    validationSummary.focus({ preventScroll: true });
    validationSummary.scrollIntoView({ behavior: 'smooth', block: 'center' });
    const firstInvalidField = document.querySelector('.field:has(> small.error) input, .field:has(> small.error) select, .field:has(> small.error) textarea');
    if (firstInvalidField) firstInvalidField.setAttribute('aria-invalid', 'true');
  }

  document.querySelectorAll('[data-client-search]').forEach((input) => {
    const target = input.dataset.clientSearch;
    const rows = [...document.querySelectorAll(`[data-search-group="${target}"] [data-search-row]`)];
    input.addEventListener('input', () => {
      const q = input.value.trim().toLowerCase();
      rows.forEach((row) => row.hidden = Boolean(q) && !row.innerText.toLowerCase().includes(q));
    });
  });

  document.querySelectorAll('[data-confirm-message]').forEach((form) => {
    form.addEventListener('submit', (event) => {
      const message = form.dataset.confirmMessage || 'Continue with this action?';
      if (!window.confirm(message)) event.preventDefault();
    });
  });

  const allocationList = document.querySelector('[data-allocation-list]');
  const allocationTemplate = document.querySelector('#allocationTemplate');
  const addAllocation = document.querySelector('[data-add-allocation]');
  const paymentForm = document.querySelector('[data-payment-form]');
  const autoAllocation = paymentForm?.querySelector('[data-auto-allocation]');
  const addAllocationInitiallyDisabled = Boolean(addAllocation?.disabled);

  const allocationRows = () => allocationList ? [...allocationList.querySelectorAll('[data-allocation-row]')] : [];

  const reindex = () => {
    allocationRows().forEach((row, index) => {
      row.querySelectorAll('[data-allocation-field]').forEach((field) => {
        field.name = `allocations[${index}].${field.dataset.allocationField}`;
      });
      const remove = row.querySelector('[data-remove-allocation]');
      if (remove) remove.disabled = allocationRows().length <= 1 || Boolean(autoAllocation?.checked);
    });
  };

  const updatePreview = () => {
    if (!paymentForm) return;
    const amount = number(paymentForm.querySelector('[data-payment-amount]')?.value);
    const totalOutstanding = number(paymentForm.dataset.totalOutstanding);
    const automatic = Boolean(autoAllocation?.checked);
    let dueBefore = 0;
    let allocated = 0;
    let dueAfter = 0;
    let statusText = '—';

    if (automatic) {
      dueBefore = totalOutstanding;
      allocated = Math.min(amount, totalOutstanding);
      dueAfter = Math.max(0, totalOutstanding - allocated);
      statusText = allocated <= 0 ? '—' : (dueAfter === 0 ? 'Eligible invoices paid' : 'Oldest invoices paid / partially paid');
    } else {
      const selected = new Set();
      let fullyPaid = 0;
      let partiallyPaid = 0;
      allocationRows().forEach((row) => {
        const select = row.querySelector('[data-invoice-select]');
        const allocationInput = row.querySelector('[data-allocation-amount]');
        const option = select?.selectedOptions?.[0];
        if (!select?.value || !option || selected.has(select.value)) return;
        selected.add(select.value);
        const due = number(option.dataset.due);
        const lineAmount = Math.min(number(allocationInput?.value), due);
        dueBefore += due;
        allocated += lineAmount;
        dueAfter += Math.max(0, due - lineAmount);
        if (lineAmount > 0 && lineAmount >= due) fullyPaid += 1;
        else if (lineAmount > 0) partiallyPaid += 1;
      });
      if (fullyPaid || partiallyPaid) {
        const parts = [];
        if (fullyPaid) parts.push(`${fullyPaid} paid`);
        if (partiallyPaid) parts.push(`${partiallyPaid} partially paid`);
        statusText = parts.join(' · ');
      }
    }

    const unallocated = Math.max(0, amount - allocated);
    paymentForm.querySelector('[data-preview-due-before]')?.replaceChildren(document.createTextNode(money(dueBefore)));
    paymentForm.querySelector('[data-preview-allocated]')?.replaceChildren(document.createTextNode(money(allocated)));
    paymentForm.querySelector('[data-preview-unallocated]')?.replaceChildren(document.createTextNode(money(unallocated)));
    paymentForm.querySelector('[data-preview-due-after]')?.replaceChildren(document.createTextNode(money(dueAfter)));
    paymentForm.querySelector('[data-preview-status]')?.replaceChildren(document.createTextNode(statusText));

    const method = paymentForm.querySelector('[data-payment-method]');
    const methodOption = method?.selectedOptions?.[0];
    const isCash = methodOption?.dataset.cash === 'true';
    const effect = !method?.value ? 'Select method'
      : isCash
        ? (paymentForm.dataset.paymentForm === 'supplier' ? 'Cashbook debit + active shift' : 'Cashbook inflow + active shift')
        : 'Non-cash reconciliation';
    paymentForm.querySelector('[data-preview-effect]')?.replaceChildren(document.createTextNode(effect));
  };

  const syncPaymentMethod = () => {
    if (!paymentForm) return;
    const method = paymentForm.querySelector('[data-payment-method]');
    const option = method?.selectedOptions?.[0];
    const isCash = option?.dataset.cash === 'true';
    const referenceRequired = option?.dataset.referenceRequired === 'true';
    paymentForm.querySelectorAll('[data-cash-context]').forEach((field) => {
      field.disabled = Boolean(method?.value) && !isCash;
      field.required = Boolean(method?.value) && isCash;
      if (field.disabled) field.value = '';
    });
    const reference = paymentForm.querySelector('[data-transaction-reference]');
    if (reference) reference.required = Boolean(method?.value) && referenceRequired;
    updatePreview();
  };

  const syncAutomaticAllocation = () => {
    const automatic = Boolean(autoAllocation?.checked);
    allocationRows().forEach((row) => {
      row.classList.toggle('is-disabled', automatic);
      row.querySelectorAll('[data-allocation-field]').forEach((field) => field.disabled = automatic);
      const remove = row.querySelector('[data-remove-allocation]');
      if (remove) remove.disabled = automatic || allocationRows().length <= 1;
    });
    if (addAllocation) addAllocation.disabled = automatic || addAllocationInitiallyDisabled;
    updatePreview();
  };

  const bindAllocation = (row) => {
    const select = row.querySelector('[data-invoice-select]');
    const amountInput = row.querySelector('[data-allocation-amount]');
    select?.addEventListener('change', () => {
      const due = number(select.selectedOptions?.[0]?.dataset.due);
      if (due > 0 && !String(amountInput?.value ?? '').trim()) {
        amountInput.value = money(due);
        const paymentAmount = paymentForm?.querySelector('[data-payment-amount]');
        if (paymentAmount && allocationRows().length === 1 && !String(paymentAmount.value ?? '').trim()) {
          paymentAmount.value = money(due);
        }
      }
      updatePreview();
    });
    amountInput?.addEventListener('input', updatePreview);
    row.querySelector('[data-remove-allocation]')?.addEventListener('click', () => {
      if (allocationRows().length <= 1) return;
      row.remove();
      reindex();
      updatePreview();
    });
  };

  if (allocationList) {
    allocationRows().forEach(bindAllocation);
    reindex();
  }

  addAllocation?.addEventListener('click', () => {
    if (!allocationList || !allocationTemplate || autoAllocation?.checked) return;
    const fragment = allocationTemplate.content.cloneNode(true);
    const row = fragment.querySelector('[data-allocation-row]');
    allocationList.appendChild(fragment);
    bindAllocation(row);
    reindex();
    row.querySelector('select,input')?.focus();
    updatePreview();
  });

  autoAllocation?.addEventListener('change', syncAutomaticAllocation);
  paymentForm?.querySelector('[data-payment-amount]')?.addEventListener('input', updatePreview);
  paymentForm?.querySelector('[data-excess-settlement]')?.addEventListener('change', updatePreview);
  paymentForm?.querySelector('[data-payment-method]')?.addEventListener('change', syncPaymentMethod);

  if (paymentForm) {
    paymentForm.addEventListener('submit', () => {
      if (autoAllocation?.checked) {
        allocationRows().forEach((row) => row.querySelectorAll('[data-allocation-field]').forEach((field) => field.disabled = true));
        return;
      }
      let activeIndex = 0;
      allocationRows().forEach((row) => {
        const fields = [...row.querySelectorAll('[data-allocation-field]')];
        const hasAnyValue = fields.some((field) => String(field.value ?? '').trim() !== '');
        fields.forEach((field) => {
          if (!hasAnyValue) field.disabled = true;
          else {
            field.disabled = false;
            field.name = `allocations[${activeIndex}].${field.dataset.allocationField}`;
          }
        });
        if (hasAnyValue) activeIndex += 1;
      });
    });
    syncPaymentMethod();
    syncAutomaticAllocation();
    updatePreview();
  }

  // Prevent duplicate financial/status submissions after validation/confirmation handlers have run.
  document.querySelectorAll('form[method="post" i]').forEach((form) => {
    form.addEventListener('submit', (event) => {
      if (event.defaultPrevented) return;
      const submitter = event.submitter;
      window.setTimeout(() => {
        form.querySelectorAll('button[type="submit"],input[type="submit"]').forEach((control) => {
          control.disabled = true;
          control.setAttribute('aria-disabled', 'true');
        });
        if (submitter) submitter.setAttribute('aria-busy', 'true');
      }, 0);
    });
  });

  // Sales-return rows are opt-in. Only checked rows are submitted and indexes are compacted.
  // Refund payment/cash controls reuse the same backend IDs as POS, but expose named choices
  // instead of asking the user to type internal IDs.
  document.querySelectorAll('[data-sale-return-form]').forEach((form) => {
    const settlement = form.querySelector('[data-return-settlement]');
    const paymentMethod = form.querySelector('[data-return-payment-method]');
    const refundFields = [...form.querySelectorAll('[data-return-refund-field]')];
    const referenceField = form.querySelector('[data-return-reference-field]');
    const transactionReference = form.querySelector('[data-return-transaction-reference]');
    const accountReferenceField = form.querySelector('[data-return-account-reference-field]');
    const accountReference = form.querySelector('[data-return-account-reference]');
    const routingPanel = form.querySelector('[data-return-routing]');
    const routingValue = form.querySelector('[data-return-routing-value]');
    const cashContext = form.querySelector('[data-return-cash-context]');
    const cashierShift = form.querySelector('[data-return-cashier-shift]');
    const cashLocation = form.querySelector('[data-return-cash-location]');
    const registerId = form.querySelector('[data-return-register-id]');
    const registerName = form.querySelector('[data-return-register-name]');

    const syncReturnRegister = () => {
      const option = cashierShift?.selectedOptions?.[0];
      if (registerId) registerId.value = option?.dataset.registerId || '';
      if (registerName) registerName.textContent = option?.dataset.registerName || 'Derived from selected shift';
    };

    const setEnabled = (element, enabled) => {
      if (!element) return;
      element.disabled = !enabled;
      if (!enabled && element.matches('input,select,textarea') && element.type !== 'hidden') element.required = false;
    };

    const syncReturnSettlement = () => {
      const isRefund = settlement?.value === 'CUSTOMER_REFUND';
      refundFields.forEach((field) => { field.hidden = !isRefund; });
      setEnabled(paymentMethod, isRefund);
      const option = paymentMethod?.selectedOptions?.[0];
      const hasMethod = isRefund && Boolean(paymentMethod?.value);
      const isCash = hasMethod && option?.dataset.cash === 'true';
      const referenceRequired = hasMethod && option?.dataset.referenceRequired === 'true';
      const configuredRouting = (option?.dataset.accountReference || '').trim() || (option?.dataset.channelReference || '').trim();

      if (paymentMethod) paymentMethod.required = isRefund;
      if (referenceField) referenceField.hidden = !(hasMethod && (referenceRequired || !isCash));
      setEnabled(transactionReference, hasMethod && (referenceRequired || !isCash));
      if (transactionReference) transactionReference.required = referenceRequired;

      if (!hasMethod || isCash) {
        if (routingPanel) routingPanel.hidden = true;
        if (accountReferenceField) accountReferenceField.hidden = true;
        setEnabled(accountReference, false);
        if (accountReference) accountReference.value = '';
      } else if (configuredRouting) {
        if (accountReference) accountReference.value = configuredRouting;
        setEnabled(accountReference, true);
        if (accountReferenceField) accountReferenceField.hidden = true;
        if (routingPanel) routingPanel.hidden = false;
        if (routingValue) routingValue.textContent = configuredRouting;
      } else {
        if (routingPanel) routingPanel.hidden = true;
        if (accountReferenceField) accountReferenceField.hidden = false;
        setEnabled(accountReference, true);
      }

      if (cashContext) cashContext.hidden = !isCash;
      [cashierShift, cashLocation, registerId].forEach((field) => setEnabled(field, isCash));
      if (cashierShift) cashierShift.required = isCash;
      if (cashLocation) cashLocation.required = isCash;
      if (isCash) syncReturnRegister();
      else if (registerId) registerId.value = '';
    };

    settlement?.addEventListener('change', syncReturnSettlement);
    paymentMethod?.addEventListener('change', syncReturnSettlement);
    cashierShift?.addEventListener('change', () => { syncReturnRegister(); syncReturnSettlement(); });
    syncReturnRegister();
    syncReturnSettlement();

    const rows = [...form.querySelectorAll('[data-return-row]')];
    rows.forEach((row) => {
      const toggle = row.querySelector('[data-return-toggle]');
      const fields = [...row.querySelectorAll('[data-return-field]')];
      const sync = () => fields.forEach((field) => field.disabled = !toggle.checked);
      toggle?.addEventListener('change', sync);
      sync();
    });
    form.addEventListener('submit', (event) => {
      let index = 0;
      rows.forEach((row) => {
        const toggle = row.querySelector('[data-return-toggle]');
        const fields = [...row.querySelectorAll('[data-return-field]')];
        if (!toggle?.checked) {
          fields.forEach((field) => field.disabled = true);
          return;
        }
        fields.forEach((field) => {
          field.disabled = false;
          field.name = `items[${index}].${field.dataset.returnField}`;
        });
        index += 1;
      });
      if (index === 0) {
        event.preventDefault();
        window.alert('Select at least one item to return.');
      }
    });
  });

  document.querySelectorAll('form').forEach((form) => {
    const phone = form.querySelector('[name="phone"]');
    const email = form.querySelector('[name="email"]');
    if (!phone && !email) return;
    [phone, email].forEach((field) => field?.addEventListener('input', () => delete form.dataset.duplicateReviewed));
    form.addEventListener('submit', async (event) => {
      const submitter = event.submitter;
      const submittedAction = submitter?.formAction || form.action;
      const isPosCustomerSubmission = submittedAction.includes('/owner/pos/customer');
      const isCustomerSubmission = submittedAction.includes('/owner/customers') || isPosCustomerSubmission;
      if (!isCustomerSubmission || form.dataset.duplicateReviewed === 'true') return;
      const phoneValue = phone?.value?.trim() || '';
      const emailValue = email?.value?.trim() || '';
      if (!phoneValue && !emailValue) return;
      event.preventDefault();
      const editMatch = submittedAction.match(/\/owner\/customers\/(\d+)$/);
      const query = new URLSearchParams({phone: phoneValue, email: emailValue});
      if (editMatch) query.set('excludeCustomerId', editMatch[1]);
      try {
        const result = await fetch(`/owner/customers/duplicates?${query.toString()}`, {
          headers: {Accept: 'application/json'}, credentials: 'same-origin'
        });
        if (!result.ok) throw new Error('Duplicate check failed');
        const matches = await result.json();
        if (matches.length > 0 && isPosCustomerSubmission) {
          const panel = document.querySelector('[data-pos-duplicate-panel]');
          const customerSelect = document.querySelector('[data-customer-select]');
          if (panel) {
            panel.innerHTML = '';
            const heading = document.createElement('strong');
            heading.textContent = 'Possible existing customer found';
            const help = document.createElement('span');
            help.textContent = 'Select an existing active customer instead of creating a duplicate.';
            panel.append(heading, help);
            const list = document.createElement('div');
            list.className = 'pos-duplicate-list';
            matches.slice(0, 5).forEach((item) => {
              const row = document.createElement('div');
              row.className = 'pos-duplicate-item';
              const copy = document.createElement('div');
              const name = document.createElement('b');
              name.textContent = item.name || `Customer #${item.id}`;
              const meta = document.createElement('small');
              meta.textContent = [item.phone, item.email].filter(Boolean).join(' · ') || `ID ${item.id}`;
              copy.append(name, meta);
              const active = item.active !== false && item.archived !== true;
              const action = document.createElement('button');
              action.type = 'button';
              action.className = 'checkout-btn';
              action.textContent = active ? 'Select' : 'Archived';
              action.disabled = !active;
              if (active) action.addEventListener('click', () => {
                if (customerSelect) {
                  let option = [...customerSelect.options].find((value) => value.value === String(item.id));
                  if (!option) {
                    option = new Option(`${item.name || `Customer #${item.id}`}${item.phone ? ` · ${item.phone}` : ''}`, String(item.id));
                    customerSelect.add(option);
                  }
                  customerSelect.value = String(item.id);
                  customerSelect.dispatchEvent(new Event('change', {bubbles: true}));
                }
                panel.hidden = true;
                document.querySelector('[data-customer-drawer]')?.classList.remove('is-open');
              });
              row.append(copy, action); list.append(row);
            });
            panel.append(list); panel.hidden = false;
            document.querySelector('[data-customer-drawer]')?.classList.add('is-open');
            panel.scrollIntoView({block: 'nearest', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'});
          }
          return;
        }
        if (matches.length > 0) {
          const names = matches.slice(0, 3).map((item) => item.name || `Customer #${item.id}`).join(', ');
          if (!window.confirm(`Possible duplicate customer found: ${names}. Continue anyway?`)) return;
        }
        form.dataset.duplicateReviewed = 'true';
        if (submitter) form.requestSubmit(submitter); else form.requestSubmit();
      } catch (_) {
        window.alert('Customer duplicate check could not be completed. Please try again.');
      }
    });
  });

  document.querySelectorAll('[data-copy-table]').forEach((button) => {
    button.addEventListener('click', async () => {
      const table = document.querySelector(button.dataset.copyTable || '');
      if (!table) return;
      const rows = [...table.querySelectorAll('tr')].filter((row) => !row.closest('tfoot'));
      const text = rows.map((row) => [...row.querySelectorAll('th,td')]
        .map((cell) => cell.innerText.replace(/\s+/g, ' ').trim()).join('\t')).join('\n');
      try {
        await navigator.clipboard.writeText(text);
        const original = button.textContent;
        button.textContent = 'Copied';
        window.setTimeout(() => { button.textContent = original; }, 1400);
      } catch (_) {
        window.alert('Copy is not available in this browser context.');
      }
    });
  });

  document.querySelectorAll('form').forEach((form) => {
    form.addEventListener('submit', (event) => {
      queueMicrotask(() => {
        if (event.defaultPrevented) return;
        const submitter = event.submitter;
        if (!submitter || submitter.disabled) return;
        submitter.disabled = true;
        submitter.setAttribute('aria-busy', 'true');
      });
    });
  });
})();

/* Part E/F accessibility polish; DOM-only, no request contract changes. */
(() => {
  const root = document.querySelector('.falcon-ef-ui');
  if (!root) return;
  let sequence = 0;
  root.querySelectorAll('.field, .checkout-field').forEach((field) => {
    const label = field.querySelector(':scope > label, :scope > .checkout-label-row label');
    const control = field.querySelector('input:not([type="hidden"]), select, textarea');
    if (!label || !control) return;
    if (!control.id) control.id = `ef-field-${++sequence}`;
    if (!label.htmlFor) label.htmlFor = control.id;
  });
})();
