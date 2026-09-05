(() => {
  'use strict';
  const root = document.querySelector('[data-pos-workspace]');
  if (!root) return;

  const cartForm = document.querySelector('[data-pos-form]');
  const productSearch = root.querySelector('[data-product-search]');
  const customerDrawer = root.querySelector('[data-customer-drawer]');
  const soundEnabled = root.dataset.posSoundEnabled === 'true';
  const recalculateSubmit = root.querySelector('[data-recalculate-submit]');
  const barcodeInput = root.querySelector('[data-barcode-input]');
  const barcodeSubmit = root.querySelector('[data-barcode-submit]');
  const scannerStatus = root.querySelector('[data-scanner-status]');

  const moneyFormatter = new Intl.NumberFormat('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2});
  const quantityFormatter = new Intl.NumberFormat('en-US', {minimumFractionDigits: 0, maximumFractionDigits: 3});
  const formatMoney = (value) => moneyFormatter.format(Number.isFinite(Number(value)) ? Number(value) : 0);
  const formatQuantity = (value) => quantityFormatter.format(Number.isFinite(Number(value)) ? Number(value) : 0);
  const itemsHost = () => root.querySelector('[data-pos-items-host]');
  const cartCount = () => root.querySelector('.cart-count');
  const totalPayableNode = () => root.querySelector('[data-total-payable]');

  const formatStaticNumbers = () => {
    root.querySelectorAll('[data-money-display]').forEach((node) => {
      const value = node.dataset.value ?? node.textContent;
      node.textContent = formatMoney(value);
    });
    root.querySelectorAll('[data-quantity-display]').forEach((node) => {
      const value = node.dataset.value ?? node.textContent;
      node.textContent = formatQuantity(value);
    });
    root.querySelectorAll('[data-stock-display]').forEach((node) => {
      node.textContent = `Stock ${formatQuantity(node.dataset.stockValue)}`;
    });
  };

  const setScannerState = (active) => {
    if (!scannerStatus) return;
    scannerStatus.classList.toggle('is-active', active);
    scannerStatus.lastChild.textContent = active ? ' Scanner active' : ' Scanner ready';
  };

  const playFeedback = () => {
    if (!soundEnabled) return;
    try {
      const AudioContext = window.AudioContext || window.webkitAudioContext;
      if (!AudioContext) return;
      const context = new AudioContext();
      const oscillator = context.createOscillator();
      const gain = context.createGain();
      oscillator.frequency.value = 880;
      gain.gain.value = 0.035;
      oscillator.connect(gain); gain.connect(context.destination);
      oscillator.start(); oscillator.stop(context.currentTime + 0.06);
      oscillator.addEventListener('ended', () => context.close());
    } catch (_) {
      // Optional feedback must never interrupt a sale.
    }
  };

  const reindex = () => {
    const host = itemsHost();
    if (!host) return;
    [...host.querySelectorAll('[data-cart-line]')].forEach((row, index) => {
      row.querySelectorAll('[data-cart-field]').forEach((field) => {
        field.name = `items[${index}].${field.dataset.cartField}`;
      });
    });
  };

  const replaceCartFromDocument = (doc) => {
    const nextHost = doc.querySelector('[data-pos-items-host]');
    const currentHost = itemsHost();
    if (nextHost && currentHost) currentHost.innerHTML = nextHost.innerHTML;

    const nextTotals = doc.querySelector('.cart-totals');
    const currentTotals = root.querySelector('.cart-totals');
    if (nextTotals && currentTotals) currentTotals.innerHTML = nextTotals.innerHTML;

    const nextCount = doc.querySelector('.cart-count');
    const currentCount = cartCount();
    if (nextCount && currentCount) currentCount.textContent = nextCount.textContent;

    const nextFeedback = doc.querySelector('[data-pos-feedback]');
    const currentFeedback = root.querySelector('[data-pos-feedback]');
    if (nextFeedback && currentFeedback) currentFeedback.innerHTML = nextFeedback.innerHTML;

    const nextBarcode = doc.querySelector('[data-barcode-input]');
    if (nextBarcode && barcodeInput) barcodeInput.value = nextBarcode.value || '';
  };

  let cartRequestInFlight = false;
  const submitCartAsync = async (action, {focusBarcode = false, successSound = false} = {}) => {
    if (!cartForm || cartRequestInFlight) return;
    reindex();
    if (!window.fetch || !window.DOMParser) {
      const submitter = action === barcodeSubmit?.formAction ? barcodeSubmit : recalculateSubmit;
      if (submitter) cartForm.requestSubmit(submitter);
      return;
    }
    cartRequestInFlight = true;
    root.classList.add('is-cart-updating');
    try {
      const response = await fetch(action || cartForm.action, {
        method: 'POST', body: new FormData(cartForm), credentials: 'same-origin', headers: {'Accept': 'text/html'}
      });
      if (!response.ok) throw new Error('Cart update failed');
      const html = await response.text();
      const doc = new DOMParser().parseFromString(html, 'text/html');
      replaceCartFromDocument(doc);
      formatStaticNumbers();
      bindCartControls();
      syncPaymentUi();
      if (successSound && !doc.querySelector('.pos-alert.error')) playFeedback();
      if (focusBarcode) window.requestAnimationFrame(() => barcodeInput?.focus());
    } catch (_) {
      // Preserve the server-rendered form workflow as a regression-safe fallback.
      if (recalculateSubmit) cartForm.requestSubmit(recalculateSubmit);
    } finally {
      cartRequestInFlight = false;
      root.classList.remove('is-cart-updating');
    }
  };

  const renderEmptyCart = () => {
    const host = itemsHost();
    if (host) host.innerHTML = '<div class="cart-empty"><div><strong>Cart is empty</strong><span>Scan a barcode or select Add on a product to begin the sale.</span></div></div>';
    const count = cartCount();
    if (count) count.textContent = '0';
    root.querySelectorAll('.cart-totals [data-money-display], .cart-totals [data-total-payable]').forEach((node) => {
      node.dataset.value = '0';
      if (node.hasAttribute('data-item-payable')) node.dataset.itemPayable = '0';
      node.textContent = '0.00';
    });
    syncPaymentUi();
  };

  const confirmCartRemoval = (row) => new Promise((resolve) => {
    const previousFocus = document.activeElement;
    const productName = row?.querySelector('strong')?.textContent?.trim() || 'this item';
    const overlay = document.createElement('div');
    overlay.className = 'pos-confirm-overlay';
    overlay.innerHTML = `
      <div class="pos-confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="posRemoveTitle" aria-describedby="posRemoveDescription">
        <div class="pos-confirm-icon" aria-hidden="true">−</div>
        <div class="pos-confirm-copy">
          <h3 id="posRemoveTitle">Remove item?</h3>
          <p id="posRemoveDescription">Remove <strong data-remove-product-name></strong> from the current cart?</p>
        </div>
        <div class="pos-confirm-actions">
          <button type="button" class="checkout-btn" data-remove-cancel>Keep item</button>
          <button type="button" class="checkout-btn pos-confirm-remove" data-remove-confirm>Remove</button>
        </div>
      </div>`;
    overlay.querySelector('[data-remove-product-name]').textContent = productName;
    document.body.appendChild(overlay);

    const cancelButton = overlay.querySelector('[data-remove-cancel]');
    const removeButton = overlay.querySelector('[data-remove-confirm]');
    let finished = false;
    const finish = (confirmed) => {
      if (finished) return;
      finished = true;
      document.removeEventListener('keydown', onKeydown);
      overlay.classList.remove('is-visible');
      window.setTimeout(() => {
        overlay.remove();
        if (previousFocus instanceof HTMLElement && document.contains(previousFocus)) previousFocus.focus();
        resolve(confirmed);
      }, 140);
    };
    const onKeydown = (event) => {
      if (event.key === 'Escape') { event.preventDefault(); finish(false); }
    };

    cancelButton?.addEventListener('click', () => finish(false));
    removeButton?.addEventListener('click', () => finish(true));
    overlay.addEventListener('click', (event) => { if (event.target === overlay) finish(false); });
    document.addEventListener('keydown', onKeydown);
    window.requestAnimationFrame(() => {
      overlay.classList.add('is-visible');
      cancelButton?.focus();
    });
  });

  const bindCartControls = () => {
    const host = itemsHost();
    if (!host) return;
    host.querySelectorAll('[data-quantity]').forEach(normalizeQuantityText);
    host.querySelectorAll('[data-qty-minus],[data-qty-plus]').forEach((button) => {
      if (button.dataset.posBound === 'true') return;
      button.dataset.posBound = 'true';
      button.addEventListener('click', async () => {
        const row = button.closest('[data-cart-line]');
        const input = row?.querySelector('[data-quantity]');
        if (!input) return;
        const current = Number.parseFloat(input.value || '0') || 0;
        const next = button.hasAttribute('data-qty-plus') ? current + 1 : Math.max(0, current - 1);
        if (next <= 0) {
          if (!await confirmCartRemoval(row)) return;
          row.remove();
          if (!host.querySelector('[data-cart-line]')) { renderEmptyCart(); return; }
        } else {
          input.value = next.toString();
        }
        await submitCartAsync(cartForm.action);
      });
    });
    host.querySelectorAll('[data-quantity],[data-line-discount]').forEach((input) => {
      if (input.dataset.posBound === 'true') return;
      input.dataset.posBound = 'true';
      input.addEventListener('change', () => submitCartAsync(cartForm.action));
    });
  };

  root.querySelectorAll('[data-add-product]').forEach((button) => {
    button.addEventListener('click', async () => {
      const host = itemsHost();
      if (!host || !cartForm || button.disabled) return;
      const variantId = button.dataset.variantId;
      const unitId = button.dataset.unitId;
      const existing = [...host.querySelectorAll('[data-cart-line]')].find((row) =>
        row.querySelector('[data-variant-id]')?.value === variantId && row.querySelector('[data-unit-id]')?.value === unitId);
      if (existing) {
        const qty = existing.querySelector('[data-quantity]');
        qty.value = (Number.parseFloat(qty.value || '0') + 1).toString();
      } else {
        const row = document.createElement('div');
        row.dataset.cartLine = ''; row.hidden = true;
        row.innerHTML = `<input type="hidden" data-cart-field="productVariantId" data-variant-id value="${variantId}"><input type="hidden" data-cart-field="unitId" data-unit-id value="${unitId}"><input type="hidden" data-cart-field="quantity" data-quantity value="1"><input type="hidden" data-cart-field="discount" value="0">`;
        host.appendChild(row);
      }
      button.setAttribute('aria-busy', 'true');
      await submitCartAsync(cartForm.action, {successSound: true});
      button.removeAttribute('aria-busy');
    });
  });

  root.querySelectorAll('[data-product-tile]').forEach((tile) => {
    const addButton = tile.querySelector('[data-add-product]');
    const unavailable = !addButton || addButton.disabled;
    tile.classList.toggle('is-unavailable', unavailable);
    if (unavailable) return;
    tile.addEventListener('click', (event) => {
      if (event.target.closest('[data-add-product]')) return;
      addButton.click();
    });
  });

  productSearch?.addEventListener('input', () => {
    const q = productSearch.value.trim().toLowerCase();
    root.querySelectorAll('[data-product-tile]').forEach((tile) => {
      tile.hidden = Boolean(q) && !tile.innerText.toLowerCase().includes(q);
    });
  });

  root.querySelector('[data-toggle-customer]')?.addEventListener('click', () => customerDrawer?.classList.toggle('is-open'));
  root.querySelector('[data-close-customer]')?.addEventListener('click', () => customerDrawer?.classList.remove('is-open'));
  root.querySelector('[data-reset-customer]')?.addEventListener('click', () => {
    customerDrawer?.querySelectorAll('input, textarea, select').forEach((field) => {
      if (field.matches('select[name=active]')) field.value = 'true'; else field.value = '';
    });
    const duplicatePanel = root.querySelector('[data-pos-duplicate-panel]');
    if (duplicatePanel) { duplicatePanel.hidden = true; duplicatePanel.innerHTML = ''; }
  });

  const moneyValue = (field) => {
    const value = Number.parseFloat(field?.value || '0');
    return Number.isFinite(value) ? value : 0;
  };

  const orderDiscountInput = root.querySelector('[data-order-discount]');
  const shippingChargeInput = root.querySelector('[data-shipping-charge]');
  const otherChargeInput = root.querySelector('[data-other-charge]');
  const adjustmentsDetails = root.querySelector('[data-checkout-more]');
  const notesInput = root.querySelector('textarea[name="notes"]');
  const paidNowInput = root.querySelector('[data-paid-now]');
  const paidNowLabel = root.querySelector('[data-paid-now-label]');
  const paidNowHelp = root.querySelector('[data-paid-now-help]');
  const paymentMethodInput = root.querySelector('[data-payment-method]');
  // Cart totals are replaced after every AJAX cart refresh. Resolve settlement nodes lazily
  // so live payment updates always target the current server-rendered DOM.
  const paymentSummaryNode = () => root.querySelector('[data-payment-summary]');
  const tenderedNode = () => root.querySelector('[data-cart-tendered]');
  const remainingDueNode = () => root.querySelector('[data-remaining-due]');
  const changeNode = () => root.querySelector('[data-change]');
  const paymentStatusNode = () => root.querySelector('[data-payment-status]');
  const customerSelect = root.querySelector('[data-customer-select]');
  const dueCustomerWarning = root.querySelector('[data-due-customer-warning]');
  const transactionReferenceField = root.querySelector('[data-transaction-reference-field]');
  const transactionReferenceInput = root.querySelector('[data-transaction-reference]');
  const accountReferenceField = root.querySelector('[data-account-reference-field]');
  const accountReferenceInput = root.querySelector('[data-account-reference]');
  const routingPanel = root.querySelector('[data-payment-routing]');
  const routingValue = root.querySelector('[data-payment-routing-value]');
  const cashContext = root.querySelector('[data-cash-context]');
  const cashierShiftInput = root.querySelector('[data-cashier-shift]');
  const cashLocationInput = root.querySelector('[data-cash-location]');
  const registerIdInput = root.querySelector('[data-register-id]');
  const registerNameNode = root.querySelector('[data-register-name]');
  const checkoutSubmit = root.querySelector('[data-checkout-submit]');
  const holdSubmit = root.querySelector('[data-hold-submit]');
  let paidNowAutoFilled = false;

  if (adjustmentsDetails && (moneyValue(orderDiscountInput) > 0 || moneyValue(shippingChargeInput) > 0 || moneyValue(otherChargeInput) > 0 || (notesInput?.value || '').trim())) adjustmentsDetails.open = true;

  const currentTotalPayable = () => {
    const totalNode = totalPayableNode();
    const itemPayable = Number.parseFloat(totalNode?.dataset.itemPayable || '0') || 0;
    return Math.max(0, itemPayable + moneyValue(shippingChargeInput) + moneyValue(otherChargeInput) - moneyValue(orderDiscountInput));
  };

  const syncSelectTitle = (select) => {
    if (!select) return;
    const label = select.selectedOptions?.[0]?.textContent?.trim() || '';
    select.title = label;
  };

  const normalizeQuantityText = (input) => {
    if (!input || !input.value.includes('.')) return;
    const normalized = input.value.replace(/(\.\d*?[1-9])0+$|\.0+$/, '$1');
    if (normalized) input.value = normalized;
  };

  const updateRegisterFromShift = () => {
    if (!cashierShiftInput) return;
    const option = cashierShiftInput.selectedOptions?.[0];
    if (registerIdInput) registerIdInput.value = option?.dataset.registerId || '';
    if (registerNameNode) {
      const registerName = option?.dataset.registerName || 'Derived from selected shift';
      registerNameNode.textContent = registerName;
      registerNameNode.title = registerName;
    }
    syncSelectTitle(cashierShiftInput);
    syncSelectTitle(cashLocationInput);
  };

  const updatePaymentSummary = () => {
    const total = currentTotalPayable();
    const totalNode = totalPayableNode();
    if (totalNode) { totalNode.textContent = formatMoney(total); totalNode.dataset.value = total.toString(); }
    if (paidNowAutoFilled && paidNowInput) paidNowInput.value = total.toFixed(2);
    const tendered = Math.max(0, moneyValue(paidNowInput));
    const applied = Math.min(tendered, total);
    const remaining = Math.max(0, total - applied);
    const change = Math.max(0, tendered - applied);
    const tenderedDisplay = tenderedNode();
    const remainingDueDisplay = remainingDueNode();
    const changeDisplay = changeNode();
    const paymentSummary = paymentSummaryNode();
    const paymentStatusDisplay = paymentStatusNode();
    if (tenderedDisplay) tenderedDisplay.textContent = formatMoney(tendered);
    if (remainingDueDisplay) remainingDueDisplay.textContent = formatMoney(remaining);
    if (changeDisplay) changeDisplay.textContent = formatMoney(change);
    if (paymentSummary) {
      paymentSummary.classList.toggle('has-payment', tendered > 0.000001);
      paymentSummary.classList.toggle('has-due', remaining > 0.000001);
      paymentSummary.classList.toggle('has-change', change > 0.000001);
    }
    if (paymentStatusDisplay) {
      const status = applied <= 0 ? 'UNPAID' : (remaining > 0 ? 'PARTIALLY PAID' : 'PAID');
      paymentStatusDisplay.textContent = status;
      paymentStatusDisplay.dataset.status = status.replaceAll(' ', '_');
    }
  };

  const syncPaymentUi = ({methodChanged = false} = {}) => {
    const option = paymentMethodInput?.selectedOptions?.[0];
    const hasMethod = Boolean(paymentMethodInput?.value);
    const isCash = hasMethod && option?.dataset.cash === 'true';
    const referenceRequired = hasMethod && option?.dataset.referenceRequired === 'true';
    const configuredRouting = (option?.dataset.accountReference || '').trim() || (option?.dataset.channelReference || '').trim();
    if (paidNowLabel) paidNowLabel.textContent = isCash ? 'Cash Received' : 'Paid Now';
    if (paidNowHelp) {
      paidNowHelp.textContent = !hasMethod
        ? 'Select a payment method. Cash received is entered manually; non-cash payments use the exact payable amount.'
        : (isCash
          ? 'Enter the actual cash received from the customer. Change to return is calculated automatically.'
          : 'The exact payable amount is filled automatically for this non-cash payment.');
    }
    if (paidNowInput) {
      paidNowInput.placeholder = isCash ? 'Enter cash received' : '0.00';
      paidNowInput.inputMode = 'decimal';
    }
    if (methodChanged && paidNowInput) {
      if (!hasMethod) {
        paidNowInput.value = '0';
        paidNowAutoFilled = false;
      } else if (isCash) {
        // Cash is tendered by the customer, so never assume the payable amount was received.
        // The cashier must enter the actual cash handed over (e.g. 500 for a 320 bill).
        paidNowInput.value = '';
        paidNowAutoFilled = false;
        window.requestAnimationFrame(() => paidNowInput.focus());
      } else {
        // Card/mobile/bank payments normally charge the exact payable amount.
        paidNowInput.value = currentTotalPayable().toFixed(2);
        paidNowAutoFilled = true;
      }
    }
    const hasAppliedPayment = moneyValue(paidNowInput) > 0;
    if (transactionReferenceField) transactionReferenceField.hidden = !(hasAppliedPayment && (referenceRequired || !isCash));
    if (transactionReferenceInput) transactionReferenceInput.required = referenceRequired && hasAppliedPayment;
    if (!hasMethod && transactionReferenceInput) transactionReferenceInput.value = '';
    if (!hasMethod || isCash) {
      if (routingPanel) routingPanel.hidden = true;
      if (accountReferenceField) accountReferenceField.hidden = true;
      if (accountReferenceInput) accountReferenceInput.value = '';
    } else if (configuredRouting) {
      if (accountReferenceInput) accountReferenceInput.value = configuredRouting;
      if (accountReferenceField) accountReferenceField.hidden = true;
      if (routingPanel) routingPanel.hidden = false;
      if (routingValue) routingValue.textContent = configuredRouting;
    } else {
      if (routingPanel) routingPanel.hidden = true;
      if (accountReferenceField) accountReferenceField.hidden = false;
    }
    if (cashContext) cashContext.hidden = !(isCash && hasAppliedPayment);
    updateRegisterFromShift(); updatePaymentSummary();
    const total = currentTotalPayable();
    const applied = Math.min(Math.max(0, moneyValue(paidNowInput)), total);
    const hasRemainingDue = Math.max(0, total - applied) > 0.000001;
    const missingDueCustomer = hasRemainingDue && !customerSelect?.value;
    if (dueCustomerWarning) dueCustomerWarning.hidden = !missingDueCustomer;
    const hasCartItems = (Number.parseInt(cartCount()?.textContent || '0', 10) || 0) > 0;
    if (holdSubmit) {
      holdSubmit.disabled = !hasCartItems; holdSubmit.setAttribute('aria-disabled', hasCartItems ? 'false' : 'true');
      holdSubmit.title = hasCartItems ? '' : 'Add at least one product before holding a sale.';
    }
    if (checkoutSubmit) {
      const missingCashContext = isCash && hasAppliedPayment && (!cashierShiftInput?.value || !cashLocationInput?.value || !registerIdInput?.value);
      const blocked = !hasCartItems || missingCashContext || missingDueCustomer;
      checkoutSubmit.disabled = blocked; checkoutSubmit.setAttribute('aria-disabled', blocked ? 'true' : 'false');
      checkoutSubmit.title = !hasCartItems ? 'Add at least one product before completing a sale.' : (missingCashContext ? 'Open/select a cashier shift and cash location before accepting cash.' : (missingDueCustomer ? 'Select an active customer for a due or partial-payment sale.' : ''));
    }
  };

  paymentMethodInput?.addEventListener('change', () => syncPaymentUi({methodChanged: true}));
  cashierShiftInput?.addEventListener('change', () => { updateRegisterFromShift(); syncPaymentUi(); });
  cashLocationInput?.addEventListener('change', () => syncPaymentUi());
  customerSelect?.addEventListener('change', () => syncPaymentUi());
  paidNowInput?.addEventListener('input', () => { paidNowAutoFilled = false; syncPaymentUi(); });
  [orderDiscountInput, shippingChargeInput, otherChargeInput].forEach((input) => input?.addEventListener('input', () => syncPaymentUi()));

  barcodeInput?.addEventListener('focus', () => setScannerState(true));
  barcodeInput?.addEventListener('blur', () => setScannerState(false));
  barcodeInput?.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter') return;
    event.preventDefault();
    if (barcodeInput.value.trim()) submitCartAsync(barcodeSubmit?.formAction, {focusBarcode: true, successSound: true});
  });
  barcodeSubmit?.addEventListener('click', (event) => {
    if (!barcodeInput?.value.trim()) return;
    event.preventDefault();
    submitCartAsync(barcodeSubmit.formAction, {focusBarcode: true, successSound: true});
  });

  bindCartControls(); formatStaticNumbers(); updateRegisterFromShift(); syncPaymentUi();
  window.requestAnimationFrame(() => barcodeInput?.focus());

  cartForm?.addEventListener('submit', (event) => {
    if (event.defaultPrevented) return;
    const submitter = event.submitter;
    window.setTimeout(() => {
      if (event.defaultPrevented) return;
      root.querySelectorAll('button[type="submit"]').forEach((button) => {
        button.disabled = true; button.setAttribute('aria-disabled', 'true');
      });
      if (submitter) submitter.setAttribute('aria-busy', 'true');
    }, 0);
  });
})();
