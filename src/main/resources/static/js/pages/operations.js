(() => {
  'use strict';

  const payAllForm = document.querySelector('[data-pay-all-form]');
  if (payAllForm) {
    const items = [...document.querySelectorAll('[data-pay-all-item]')];
    const toggle = document.querySelector('[data-pay-all-toggle]');
    const submit = document.querySelector('[data-pay-all]');
    const supplier = payAllForm.querySelector('[data-pay-all-supplier]');
    const sync = () => {
      const checked = items.filter((item) => item.checked);
      const supplierIds = new Set(checked.map((item) => item.dataset.supplierId));
      if (supplierIds.size > 1) {
        const keep = checked[0]?.dataset.supplierId;
        checked.filter((item) => item.dataset.supplierId !== keep).forEach((item) => { item.checked = false; });
      }
      const selected = items.filter((item) => item.checked);
      if (supplier) supplier.value = selected[0]?.dataset.supplierId || '';
      if (submit) submit.disabled = selected.length === 0;
      if (toggle) toggle.checked = items.length > 0 && items.every((item) => item.checked);
    };
    items.forEach((item) => item.addEventListener('change', sync));
    toggle?.addEventListener('change', () => {
      const firstSupplier = items.find((item) => item.checked)?.dataset.supplierId || items[0]?.dataset.supplierId;
      items.forEach((item) => { item.checked = toggle.checked && item.dataset.supplierId === firstSupplier; });
      sync();
    });
    sync();
  }

  document.querySelectorAll('[data-disabled-workflow]').forEach((el) => {
    el.addEventListener('click', (event) => event.preventDefault());
  });

  const search = document.querySelector('[data-table-search]');
  const searchableRows = [...document.querySelectorAll('[data-search-row]')];
  search?.addEventListener('input', () => {
    const query = search.value.trim().toLowerCase();
    searchableRows.forEach((row) => {
      row.hidden = Boolean(query) && !row.innerText.toLowerCase().includes(query);
    });
  });

  document.querySelectorAll('[data-print-page]').forEach((button) => {
    button.addEventListener('click', () => window.print());
  });

  const purchaseForm = document.querySelector('[data-purchase-form]');
  if (purchaseForm) {
    const list = purchaseForm.querySelector('[data-purchase-items]');
    const template = document.querySelector('#purchaseItemTemplate');
    const addButton = purchaseForm.querySelector('[data-add-purchase-item]');
    const productSearch = purchaseForm.querySelector('[data-purchase-product-search]');
    const barcodeInput = purchaseForm.querySelector('[data-purchase-barcode]');
    const barcodeStatus = purchaseForm.querySelector('[data-barcode-status]');
    const csvInput = purchaseForm.querySelector('[data-purchase-csv]');
    const csvTrigger = purchaseForm.querySelector('[data-purchase-csv-trigger]');
    const csvStatus = purchaseForm.querySelector('[data-purchase-csv-status]');
    const createProductButton = purchaseForm.querySelector('[data-create-purchase-product]');

    const moneyScale = 10000;
    const roundMoney = (value) => Math.round((Number(value) + Number.EPSILON) * moneyScale) / moneyScale;
    const compactDecimal = (value) => {
      let text = String(value ?? '').trim();
      if (!text || !text.includes('.')) return text;
      text = text.replace(/0+$/, '').replace(/\.$/, '');
      return text || '0';
    };
    purchaseForm.querySelectorAll('input[type="number"]').forEach((field) => {
      if (field.value !== '') field.value = compactDecimal(field.value);
    });
    const numberValue = (selector) => {
      const field = purchaseForm.querySelector(selector);
      const value = Number.parseFloat(field?.value ?? '0');
      return Number.isFinite(value) ? roundMoney(value) : 0;
    };

    const rowCalculation = (row) => {
      const quantity = Number.parseFloat(row.querySelector('[data-quantity]')?.value ?? '0') || 0;
      const cost = Number.parseFloat(row.querySelector('[data-unit-cost]')?.value ?? '0') || 0;
      const discountField = row.querySelector('[data-item-discount]');
      const taxField = row.querySelector('[data-item-tax]');
      const variant = row.querySelector('[data-variant]');
      const option = variant?.selectedOptions?.[0];
      const rawAmount = roundMoney(Math.max(0, quantity * cost));
      const itemDiscount = roundMoney(Number.parseFloat(discountField?.value ?? '0') || 0);
      const discountedAmount = roundMoney(Math.max(0, rawAmount - itemDiscount));
      const taxRate = Number.parseFloat(option?.dataset?.taxRate ?? '');
      const taxMethod = String(option?.dataset?.taxMethod || '').trim().toUpperCase();
      const hasConfiguredTax = Number.isFinite(taxRate) && taxRate >= 0 && Boolean(option?.dataset?.taxRate);

      let itemTax = roundMoney(Number.parseFloat(taxField?.value ?? '0') || 0);
      let linePayable = roundMoney(discountedAmount + itemTax);

      if (taxField) {
        if (hasConfiguredTax) {
          taxField.readOnly = true;
          taxField.dataset.autoTax = 'true';
          if (taxRate === 0) {
            itemTax = 0;
            linePayable = discountedAmount;
            variant?.setCustomValidity('');
          } else if (taxMethod === 'EXCLUSIVE') {
            itemTax = roundMoney(discountedAmount * taxRate / 100);
            linePayable = roundMoney(discountedAmount + itemTax);
            variant?.setCustomValidity('');
          } else if (taxMethod === 'INCLUSIVE') {
            itemTax = roundMoney(discountedAmount * taxRate / (100 + taxRate));
            linePayable = discountedAmount;
            variant?.setCustomValidity('');
          } else {
            itemTax = 0;
            linePayable = discountedAmount;
            variant?.setCustomValidity('A taxable Product requires EXCLUSIVE or INCLUSIVE tax calculation method.');
          }
          taxField.value = compactDecimal(itemTax.toFixed(4));
          const taxName = option?.dataset?.taxName || 'Configured tax';
          taxField.title = `${taxName}: ${compactDecimal(taxRate)}% ${taxMethod || ''}`.trim();
        } else {
          variant?.setCustomValidity('');
          if (taxField.dataset.autoTax === 'true') taxField.value = '0';
          taxField.dataset.autoTax = 'false';
          taxField.readOnly = false;
          taxField.title = 'No product tax is set. Enter a tax amount only when needed.';
          itemTax = roundMoney(Number.parseFloat(taxField.value || '0') || 0);
          linePayable = roundMoney(discountedAmount + itemTax);
        }
      }

      if (discountField) {
        discountField.setCustomValidity(itemDiscount > rawAmount
          ? 'Item Discount Amount cannot exceed Quantity × Unit Cost.' : '');
      }

      return { rawAmount, itemTax, itemDiscount, linePayable: roundMoney(Math.max(0, linePayable)) };
    };

    const updatePreview = () => {
      let itemSubtotal = 0;
      let itemTaxTotal = 0;
      let itemDiscountTotal = 0;
      let linePayableTotal = 0;
      purchaseForm.querySelectorAll('[data-purchase-item]').forEach((row) => {
        const calculation = rowCalculation(row);
        itemSubtotal = roundMoney(itemSubtotal + calculation.rawAmount);
        itemTaxTotal = roundMoney(itemTaxTotal + calculation.itemTax);
        itemDiscountTotal = roundMoney(itemDiscountTotal + calculation.itemDiscount);
        linePayableTotal = roundMoney(linePayableTotal + calculation.linePayable);
      });

      const orderTax = numberValue('[data-order-tax]');
      const shipping = numberValue('[data-shipping]');
      const other = numberValue('[data-other-charges]');
      const purchaseDiscount = numberValue('[data-purchase-discount]');
      const paid = numberValue('[data-paid-amount]');
      const payable = roundMoney(Math.max(0, linePayableTotal + orderTax + shipping + other - purchaseDiscount));
      const due = roundMoney(Math.max(0, payable - paid));
      const paymentOption = purchaseForm.querySelector('[name="paymentMethodId"]')?.selectedOptions?.[0];
      const cashPayment = paymentOption?.dataset?.cash === 'true';
      const change = cashPayment ? roundMoney(Math.max(0, paid - payable)) : 0;
      const set = (selector, value) => {
        const element = purchaseForm.querySelector(selector);
        if (element) element.textContent = value.toFixed(2);
      };
      set('[data-preview-subtotal]', itemSubtotal);
      set('[data-preview-item-tax]', itemTaxTotal);
      set('[data-preview-item-discount]', itemDiscountTotal);
      set('[data-preview-order-tax]', orderTax);
      set('[data-preview-shipping]', shipping);
      set('[data-preview-other]', other);
      set('[data-preview-discount]', purchaseDiscount);
      set('[data-preview-payable]', payable);
      set('[data-preview-due]', due);
      set('[data-preview-change]', change);
      purchaseForm.dataset.previewPayable = String(payable);
    };

    const syncVariant = (row) => {
      const select = row.querySelector('[data-variant]');
      const option = select?.selectedOptions?.[0];
      const unitId = option?.dataset?.purchaseUnitId ?? '';
      const unitName = option?.dataset?.purchaseUnitName ?? '';
      const baseUnitName = option?.dataset?.baseUnitName ?? '';
      const baseUnitId = option?.dataset?.baseUnitId ?? '';
      const enteredUnit = row.querySelector('[data-entered-unit]');
      const unitLabel = row.querySelector('[data-unit-label]');
      const sellingPrice = row.querySelector('[data-selling-price]');
      const availableStock = row.querySelector('[data-available-stock]');
      const batchNumber = row.querySelector('[data-batch-number]');
      const preview = row.querySelector('[data-conversion-preview]');
      const trackExpiry = option?.dataset?.trackExpiry === 'true';
      const batchRequired = option?.dataset?.batchRequired === 'true';

      if (enteredUnit) enteredUnit.value = unitId;
      if (unitLabel) unitLabel.value = unitName;
      if (sellingPrice && !sellingPrice.value && option?.dataset?.sellingPrice) sellingPrice.value = compactDecimal(option.dataset.sellingPrice);
      if (availableStock) {
        const stock = option?.value ? compactDecimal(option?.dataset?.availableStock || '0') : '';
        availableStock.value = option?.value ? `${stock}${baseUnitName ? ` ${baseUnitName}` : ''}` : '';
      }
      if (batchNumber) {
        batchNumber.required = batchRequired;
        batchNumber.placeholder = batchRequired ? 'Batch number required' : 'Batch number (optional)';
      }
      if (preview) {
        if (!option?.value) {
          preview.textContent = 'Select a product to view how the purchase unit will be added to stock.';
        } else {
          const expiryCopy = trackExpiry ? 'Expiry tracking on.' : 'No expiry tracking.';
          const batchCopy = batchRequired ? 'Batch required.' : 'Batch optional.';
          const taxRate = option?.dataset?.taxRate;
          const taxCopy = taxRate !== undefined && taxRate !== ''
            ? `Tax: ${option?.dataset?.taxName || 'Product tax'} ${compactDecimal(taxRate)}% ${option?.dataset?.taxMethod || ''}.`
            : 'Enter tax only when needed.';
          preview.innerHTML = `<strong>${unitName || `Unit #${unitId}`}</strong> → <strong>${baseUnitName || `Unit #${baseUnitId}`}</strong>. ${taxCopy} ${expiryCopy} ${batchCopy}`;
        }
      }
      updatePreview();
    };

    const reindexRows = () => {
      const rows = [...purchaseForm.querySelectorAll('[data-purchase-item]')];
      rows.forEach((row, index) => {
        row.querySelector('[data-line-number]')?.replaceChildren(document.createTextNode(`Item ${index + 1}`));
        row.querySelectorAll('[data-field]').forEach((field) => {
          field.name = `items[${index}].${field.dataset.field}`;
        });
        row.querySelectorAll('[name^="items["]').forEach((field) => {
          const match = field.name.match(/^items\[\d+\]\.(.+)$/);
          if (match) field.name = `items[${index}].${match[1]}`;
        });
        const remove = row.querySelector('[data-remove-purchase-item]');
        if (remove) remove.disabled = rows.length === 1;
      });
    };

    const bindRow = (row) => {
      const variant = row.querySelector('[data-variant]');
      variant?.addEventListener('change', () => syncVariant(row));
      row.querySelector('[data-remove-purchase-item]')?.addEventListener('click', () => {
        if (purchaseForm.querySelectorAll('[data-purchase-item]').length <= 1) return;
        row.remove();
        reindexRows();
        updatePreview();
      });
      row.querySelectorAll('input[type="number"]').forEach((field) => field.addEventListener('input', updatePreview));
      syncVariant(row);
    };

    const addRow = () => {
      if (!template || !list) return null;
      const fragment = template.content.cloneNode(true);
      const row = fragment.querySelector('[data-purchase-item]');
      list.appendChild(fragment);
      bindRow(row);
      reindexRows();
      return row;
    };

    const availableRow = () => {
      const blank = [...purchaseForm.querySelectorAll('[data-purchase-item]')]
        .find((row) => !row.querySelector('[data-variant]')?.value);
      return blank || addRow();
    };

    const selectVariant = (row, variantId, unitOverrideId = '', unitOverrideName = '') => {
      const select = row?.querySelector('[data-variant]');
      if (!select) return false;
      const option = [...select.options].find((item) => String(item.value) === String(variantId));
      if (!option) return false;
      select.value = String(variantId);
      syncVariant(row);
      if (unitOverrideId) {
        const enteredUnit = row.querySelector('[data-entered-unit]');
        const label = row.querySelector('[data-unit-label]');
        if (enteredUnit) enteredUnit.value = String(unitOverrideId);
        if (label) label.value = unitOverrideName || `Unit #${unitOverrideId}`;
      }
      return true;
    };

    const findBarcodeOption = (barcode) => [...document.querySelectorAll('#purchaseBarcodeOptions option')]
      .find((option) => option.value.trim() === String(barcode || '').trim());

    const findVariantOptionBySku = (sku) => {
      const normalizedSku = String(sku || '').trim().toLowerCase();
      return [...purchaseForm.querySelectorAll('[data-variant] option')]
        .find((option) => (option.dataset.sku || '').trim().toLowerCase() === normalizedSku);
    };

    const applyBarcode = (rawBarcode) => {
      const value = String(rawBarcode || '').trim();
      if (!value) return false;
      const mapping = findBarcodeOption(value);
      if (!mapping) {
        if (barcodeStatus) barcodeStatus.textContent = 'Barcode not found for this branch.';
        return false;
      }
      const row = availableRow();
      const selected = selectVariant(row, mapping.dataset.variantId, mapping.dataset.barcodeUnitId, mapping.dataset.barcodeUnitName);
      if (!selected) {
        if (barcodeStatus) barcodeStatus.textContent = 'This barcode is not available for the current purchase.';
        return false;
      }
      if (barcodeStatus) barcodeStatus.textContent = `Barcode ${value} added to the Purchase.`;
      if (barcodeInput) barcodeInput.value = '';
      row.querySelector('[data-quantity]')?.focus();
      return true;
    };

    const parseCsv = (text) => {
      const rows = [];
      let row = [];
      let field = '';
      let quoted = false;
      for (let index = 0; index < text.length; index += 1) {
        const char = text[index];
        if (char === '"') {
          if (quoted && text[index + 1] === '"') {
            field += '"';
            index += 1;
          } else {
            quoted = !quoted;
          }
        } else if (char === ',' && !quoted) {
          row.push(field);
          field = '';
        } else if ((char === '\n' || char === '\r') && !quoted) {
          if (char === '\r' && text[index + 1] === '\n') index += 1;
          row.push(field);
          if (row.some((value) => value.trim() !== '')) rows.push(row);
          row = [];
          field = '';
        } else {
          field += char;
        }
      }
      row.push(field);
      if (row.some((value) => value.trim() !== '')) rows.push(row);
      return rows;
    };

    const normalizeHeader = (value) => String(value || '').trim().toLowerCase().replace(/[\s-]+/g, '_');

    const validateCsvRows = (matrix) => {
      if (matrix.length < 2) throw new Error('The CSV must contain a header row and at least one purchase item.');
      const headers = matrix[0].map(normalizeHeader);
      const headerIndex = Object.fromEntries(headers.map((name, index) => [name, index]));
      if (headerIndex.quantity === undefined || headerIndex.unit_cost === undefined) {
        throw new Error('The CSV is missing the required quantity or unit cost column.');
      }
      if (headerIndex.barcode === undefined && headerIndex.sku === undefined) {
        throw new Error('The CSV needs either a barcode or SKU column.');
      }
      const value = (cells, name) => headerIndex[name] === undefined ? '' : String(cells[headerIndex[name]] || '').trim();
      return matrix.slice(1).map((cells, rowIndex) => {
        const barcode = value(cells, 'barcode');
        const sku = value(cells, 'sku');
        const quantity = value(cells, 'quantity');
        const unitCost = value(cells, 'unit_cost');
        const quantityNumber = Number.parseFloat(quantity);
        const costNumber = Number.parseFloat(unitCost);
        const manufacturingDate = value(cells, 'manufacturing_date');
        const expiryDate = value(cells, 'expiry_date');
        const isoDate = /^\d{4}-\d{2}-\d{2}$/;
        const validIsoDate = (dateValue) => {
          if (!isoDate.test(dateValue)) return false;
          const parsed = new Date(`${dateValue}T00:00:00Z`);
          return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === dateValue;
        };
        if (!barcode && !sku) throw new Error(`CSV row ${rowIndex + 2}: add a barcode or SKU.`);
        if (!Number.isFinite(quantityNumber) || quantityNumber <= 0) throw new Error(`CSV row ${rowIndex + 2}: quantity must be greater than zero.`);
        if (!Number.isFinite(costNumber) || costNumber < 0) throw new Error(`CSV row ${rowIndex + 2}: unit cost must be zero or greater.`);
        if (manufacturingDate && !validIsoDate(manufacturingDate)) throw new Error(`CSV row ${rowIndex + 2}: manufacturing date must use YYYY-MM-DD.`);
        if (expiryDate && !validIsoDate(expiryDate)) throw new Error(`CSV row ${rowIndex + 2}: expiry date must use YYYY-MM-DD.`);

        let variantId = '';
        let unitId = '';
        let unitName = '';
        if (barcode) {
          const barcodeOption = findBarcodeOption(barcode);
          if (!barcodeOption) throw new Error(`CSV row ${rowIndex + 2}: barcode ${barcode} is not active for this branch.`);
          variantId = barcodeOption.dataset.variantId || '';
          unitId = barcodeOption.dataset.barcodeUnitId || '';
          unitName = barcodeOption.dataset.barcodeUnitName || '';
        } else {
          const variantOption = findVariantOptionBySku(sku);
          if (!variantOption) throw new Error(`CSV row ${rowIndex + 2}: SKU ${sku} is not active for this branch.`);
          variantId = variantOption.value;
        }
        return {
          variantId, unitId, unitName, quantity, unitCost,
          sellingPrice: value(cells, 'selling_price'),
          itemTax: value(cells, 'item_tax'),
          itemDiscount: value(cells, 'item_discount'),
          batchNumber: value(cells, 'batch_number'),
          manufacturingDate,
          expiryDate
        };
      });
    };

    const setRowValue = (row, selector, value) => {
      const field = row.querySelector(selector);
      if (field && value !== undefined && value !== null && value !== '') {
        field.value = field.type === 'number' ? compactDecimal(value) : value;
      }
    };

    const restorePurchaseItems = () => {
      const savedItems = window.falconPurchaseFormState?.items;
      if (!Array.isArray(savedItems) || savedItems.length === 0 || !list) return;
      const initialRows = [...purchaseForm.querySelectorAll('[data-purchase-item]')];
      initialRows.slice(1).forEach((row) => row.remove());
      savedItems.forEach((item, index) => {
        const row = index === 0 ? initialRows[0] : addRow();
        if (!row) return;
        selectVariant(row, item.productVariantId);
        setRowValue(row, '[data-entered-unit]', item.enteredUnitId);
        setRowValue(row, '[data-quantity]', item.enteredQuantity);
        setRowValue(row, '[data-unit-cost]', item.unitCost);
        setRowValue(row, '[data-selling-price]', item.intendedSellingPrice);
        setRowValue(row, '[data-item-tax]', item.itemTax ?? '0');
        setRowValue(row, '[data-item-discount]', item.itemDiscount ?? '0');
        setRowValue(row, '[data-batch-number]', item.batchNumber);
        setRowValue(row, '[name$=".manufacturingDate"],[data-field="manufacturingDate"]', item.manufacturingDate);
        setRowValue(row, '[name$=".expiryDate"],[data-field="expiryDate"]', item.expiryDate);
      });
      reindexRows();
    };

    purchaseForm.querySelectorAll('[data-purchase-item]').forEach(bindRow);
    restorePurchaseItems();
    reindexRows();
    updatePreview();

    addButton?.addEventListener('click', () => {
      const row = addRow();
      row?.querySelector('[data-variant]')?.focus();
    });

    const applyProductSearch = () => {
      const query = productSearch?.value.trim().toLowerCase() || '';
      purchaseForm.querySelectorAll('[data-variant]').forEach((select) => {
        [...select.options].forEach((option, index) => {
          if (index === 0) return;
          option.hidden = Boolean(query) && !option.text.toLowerCase().includes(query);
        });
      });
    };

    productSearch?.addEventListener('input', applyProductSearch);

    const preferredProduct = window.falconPreferredPurchaseProduct || {};
    if (preferredProduct.id && productSearch && !productSearch.value) {
      productSearch.value = preferredProduct.name || '';
      applyProductSearch();
      const firstRow = purchaseForm.querySelector('[data-purchase-item]');
      const select = firstRow?.querySelector('[data-variant]');
      if (select && !select.value) {
        const matches = [...select.options].filter((option) => String(option.dataset.productId || '') === String(preferredProduct.id));
        if (matches.length === 1) selectVariant(firstRow, matches[0].value);
      }
    }

    barcodeInput?.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter') return;
      event.preventDefault();
      applyBarcode(barcodeInput.value);
    });
    barcodeInput?.addEventListener('change', () => {
      if (barcodeInput.value.trim()) applyBarcode(barcodeInput.value);
    });

    csvTrigger?.addEventListener('click', () => csvInput?.click());
    csvInput?.addEventListener('change', async () => {
      const file = csvInput.files?.[0];
      if (!file) return;
      try {
        const matrix = parseCsv(await file.text());
        const items = validateCsvRows(matrix);
        items.forEach((item) => {
          const row = availableRow();
          if (!selectVariant(row, item.variantId, item.unitId, item.unitName)) {
            throw new Error('An imported product is no longer available. Refresh the page and try again.');
          }
          setRowValue(row, '[data-quantity]', item.quantity);
          setRowValue(row, '[data-unit-cost]', item.unitCost);
          setRowValue(row, '[data-selling-price]', item.sellingPrice);
          setRowValue(row, '[data-item-tax]', item.itemTax || '0');
          setRowValue(row, '[data-item-discount]', item.itemDiscount || '0');
          setRowValue(row, '[data-batch-number]', item.batchNumber);
          setRowValue(row, '[name$=".manufacturingDate"],[data-field="manufacturingDate"]', item.manufacturingDate);
          setRowValue(row, '[name$=".expiryDate"],[data-field="expiryDate"]', item.expiryDate);
        });
        reindexRows();
        updatePreview();
        if (csvStatus) csvStatus.textContent = `${items.length} purchase item${items.length === 1 ? '' : 's'} imported.`;
      } catch (error) {
        if (csvStatus) csvStatus.textContent = error instanceof Error ? error.message : 'CSV import could not be completed.';
      } finally {
        csvInput.value = '';
      }
    });

    createProductButton?.addEventListener('click', () => {
      const supplierId = purchaseForm.querySelector('[name="supplierId"]')?.value || '';
      const params = new URLSearchParams({ returnTo: 'purchase' });
      const returnBranchId = purchaseForm.querySelector('[name="branchId"]')?.value || '';
      if (supplierId) params.set('supplierId', supplierId);
      if (returnBranchId) params.set('returnBranchId', returnBranchId);
      const popup = window.open(`/owner/products/new?${params.toString()}`, 'falconPurchaseProduct', 'width=1180,height=850,resizable=yes,scrollbars=yes');
      if (!popup && barcodeStatus) barcodeStatus.textContent = 'Allow pop-ups for Falcon POS to create a Product without leaving this Purchase.';
      popup?.focus();
    });

    window.addEventListener('message', (event) => {
      if (event.origin !== window.location.origin || event.data?.type !== 'falcon:purchase-product-created') return;
      const product = event.data;
      if (!product.selectable) {
        if (barcodeStatus) barcodeStatus.textContent = 'Product was created but is inactive, so it was not added to this Purchase.';
        return;
      }
      const appendOption = (select) => {
        if (!select || [...select.options].some((option) => String(option.value) === String(product.variantId))) return;
        const option = document.createElement('option');
        option.value = product.variantId;
        option.textContent = `${product.productName} · ${product.variantName || ''} · ${product.sku || ''}`;
        option.dataset.sku = product.sku || '';
        option.dataset.productId = product.productId || '';
        option.dataset.productName = product.productName || '';
        option.dataset.taxRate = product.taxRate || '';
        option.dataset.taxName = product.taxName || '';
        option.dataset.taxMethod = product.taxMethod || '';
        option.dataset.variantName = product.variantName || '';
        option.dataset.purchaseUnitId = product.purchaseUnitId || product.baseUnitId || '';
        option.dataset.purchaseUnitName = product.purchaseUnitName || '';
        option.dataset.baseUnitId = product.baseUnitId || '';
        option.dataset.baseUnitName = product.baseUnitName || '';
        option.dataset.sellingPrice = product.sellingPrice || '';
        option.dataset.availableStock = '0';
        option.dataset.trackExpiry = String(Boolean(product.trackExpiry));
        option.dataset.batchRequired = String(Boolean(product.batchRequired));
        select.appendChild(option);
      };
      purchaseForm.querySelectorAll('[data-variant]').forEach(appendOption);
      appendOption(template?.content?.querySelector('[data-variant]'));
      const row = availableRow();
      if (selectVariant(row, product.variantId)) {
        row.querySelector('[data-quantity]')?.focus();
        if (barcodeStatus) barcodeStatus.textContent = `${product.productName} was created and selected in the Purchase.`;
      }
    });

    purchaseForm.querySelectorAll('[data-order-tax],[data-shipping],[data-other-charges],[data-purchase-discount],[data-paid-amount]')
      .forEach((field) => field.addEventListener('input', updatePreview));
    purchaseForm.querySelector('[name="paymentMethodId"]')?.addEventListener('change', updatePreview);

    purchaseForm.addEventListener('reset', () => {
      window.setTimeout(() => {
        const rows = [...purchaseForm.querySelectorAll('[data-purchase-item]')];
        rows.slice(1).forEach((row) => row.remove());
        reindexRows();
        rows[0] && syncVariant(rows[0]);
        updatePreview();
        if (barcodeStatus) barcodeStatus.textContent = 'Ready to scan.';
        if (csvStatus) csvStatus.textContent = 'No file selected.';
      }, 0);
    });
  }
})();

(() => {
  'use strict';

  const normalize = (value) => String(value || '').trim().toLowerCase().replace(/\s+/g, '_');

  // Keep status meaning legible without changing backend enums or template bindings.
  document.querySelectorAll('.ops-status').forEach((badge) => {
    const state = normalize(badge.textContent);
    const semantic = {
      paid: 'paid', confirmed: 'confirmed', posted: 'posted', active: 'active', received: 'received', balanced: 'balanced', success: 'success',
      partially_paid: 'partially_paid', partially_returned: 'partial', submitted: 'submitted', approved: 'approved', dispatched: 'dispatched', open: 'open', pending: 'warning', under_review: 'warning',
      failed: 'failed', rejected: 'rejected', cancelled: 'cancelled', voided: 'voided', reversed: 'reversed', expired: 'expired', shortage: 'shortage',
      draft: 'draft', closed: 'closed', returned: 'returned', inactive: 'inactive'
    }[state];
    if (semantic) badge.classList.add(semantic);
  });

  // Client-side filters are used only on explicitly marked, already-loaded real rows.
  document.querySelectorAll('[data-current-page-filter-root]').forEach((root) => {
    const apply = () => {
      const rows = [...root.querySelectorAll('[data-search-row]')];
      const query = (root.querySelector('[data-table-search]')?.value || '').trim().toLowerCase();
      const filters = [...root.querySelectorAll('[data-current-page-filter]')];
      rows.forEach((row) => {
        const textMatch = !query || row.innerText.toLowerCase().includes(query);
        const filtersMatch = filters.every((filter) => {
          const value = normalize(filter.value);
          if (!value) return true;
          const field = filter.dataset.currentPageFilter;
          return normalize(row.dataset[field]) === value;
        });
        row.hidden = !(textMatch && filtersMatch);
      });
    };
    root.querySelector('[data-table-search]')?.addEventListener('input', apply);
    root.querySelectorAll('[data-current-page-filter]').forEach((control) => {
      control.addEventListener(control.tagName === 'INPUT' ? 'input' : 'change', apply);
    });
  });

  const purchaseForm = document.querySelector('[data-purchase-form]');
  if (purchaseForm) {
    const validatePurchaseDates = () => {
      const purchaseDate = purchaseForm.querySelector('[name="purchaseDate"]')?.value || '';
      purchaseForm.querySelectorAll('[data-purchase-item]').forEach((row) => {
        const manufacturing = row.querySelector('[name$=".manufacturingDate"]');
        const expiry = row.querySelector('[name$=".expiryDate"]');
        if (!expiry) return;
        let message = '';
        if (expiry.value && manufacturing?.value && expiry.value <= manufacturing.value) {
          message = 'Expiry / best-before date must be later than the manufacturing date.';
        } else if (expiry.value && purchaseDate && expiry.value <= purchaseDate) {
          message = 'Expiry / best-before date must be later than the purchase date.';
        }
        expiry.setCustomValidity(message);
      });
    };

    const validatePaymentContext = () => {
      const paidField = purchaseForm.querySelector('[name="paidAmount"]');
      const paid = Number.parseFloat(paidField?.value || '0') || 0;
      const method = purchaseForm.querySelector('[name="paymentMethodId"]');
      const selected = method?.selectedOptions?.[0];
      const payable = Number.parseFloat(purchaseForm.dataset.previewPayable || '0') || 0;
      const isCash = selected?.dataset?.cash === 'true';
      if (method) method.setCustomValidity(paid > 0 && !method.value ? 'Select a payment method when Paid Amount is greater than zero.' : '');
      if (paidField) paidField.setCustomValidity(paid > payable && paid > 0 && method?.value && !isCash
        ? 'Non-cash Paid Amount cannot exceed the Purchase Total Payable.' : '');
    };

    purchaseForm.addEventListener('input', (event) => {
      if (event.target.matches('[name="purchaseDate"],[name$=".manufacturingDate"],[name$=".expiryDate"]')) validatePurchaseDates();
      if (event.target.matches('[name="paidAmount"],[name="paymentMethodId"]')) validatePaymentContext();
    });
    purchaseForm.addEventListener('change', (event) => {
      if (event.target.matches('[name="purchaseDate"],[name$=".manufacturingDate"],[name$=".expiryDate"]')) validatePurchaseDates();
      if (event.target.matches('[name="paidAmount"],[name="paymentMethodId"]')) validatePaymentContext();
    });
    purchaseForm.addEventListener('submit', () => {
      validatePurchaseDates();
      validatePaymentContext();
    });
  }

  // Prevent accidental duplicate POSTs while preserving native validation and later confirmation handlers.
  document.querySelectorAll('form').forEach((form) => {
    if ((form.getAttribute('method') || 'get').toLowerCase() !== 'post') return;
    form.addEventListener('submit', (event) => {
      window.setTimeout(() => {
        if (event.defaultPrevented || !form.checkValidity()) return;
        form.querySelectorAll('button[type="submit"],input[type="submit"]').forEach((button) => {
          if (button.disabled) return;
          button.disabled = true;
          button.setAttribute('aria-disabled', 'true');
          button.classList.add('ops-submitting');
        });
      }, 0);
    });
  });
})();

(() => {
  'use strict';

  const form = document.querySelector('[data-cash-transfer-form]');
  if (!form) return;

  const type = form.querySelector('[data-transfer-type]');
  const sourceShift = form.querySelector('[data-transfer-source-shift]');
  const destinationShift = form.querySelector('[data-transfer-destination-shift]');
  const sourceRegister = form.querySelector('[data-transfer-source-register-id]');
  const destinationRegister = form.querySelector('[data-transfer-destination-register-id]');
  const amount = form.querySelector('[data-transfer-amount]');
  const approval = form.querySelector('[data-transfer-approval]');
  const approvalWrap = form.querySelector('[data-transfer-approval-wrap]');
  const approvalNote = form.querySelector('[data-transfer-approval-note]');

  const rules = {
    SAFE_TO_REGISTER: ['source-location', 'destination-shift'],
    REGISTER_TO_SAFE: ['source-shift', 'destination-location'],
    REGISTER_TO_REGISTER_HANDOVER: ['source-shift', 'destination-shift'],
    CASH_DROP: ['source-shift', 'destination-location'],
    CASH_DEPOSIT_TO_BANK: ['source-location', 'external-account'],
    CASH_WITHDRAWAL_FROM_BANK: ['destination-location', 'external-account']
  };

  const syncShiftRegister = (select, hidden) => {
    if (!hidden) return;
    const selected = select?.selectedOptions?.[0];
    hidden.value = selected?.dataset?.registerId || '';
  };

  const syncType = () => {
    const active = new Set(rules[type?.value] || []);
    form.querySelectorAll('[data-transfer-field]').forEach((group) => {
      const enabled = active.has(group.dataset.transferField);
      group.hidden = !enabled;
      group.querySelectorAll('input,select,textarea').forEach((field) => {
        field.disabled = !enabled;
        field.required = enabled;
        if (!enabled) field.value = '';
      });
    });
    if (!active.has('source-shift') && sourceRegister) sourceRegister.value = '';
    if (!active.has('destination-shift') && destinationRegister) destinationRegister.value = '';
    syncShiftRegister(sourceShift, sourceRegister);
    syncShiftRegister(destinationShift, destinationRegister);
  };

  const syncApproval = () => {
    const thresholdRaw = approvalWrap?.dataset?.approvalThreshold || '';
    const threshold = Number.parseFloat(thresholdRaw);
    const value = Number.parseFloat(amount?.value || '0');
    const required = Number.isFinite(threshold) && threshold >= 0 && Number.isFinite(value) && value > threshold;
    if (approval) {
      approval.required = required;
      approval.disabled = !required;
      if (!required) approval.checked = false;
    }
    if (approvalWrap) approvalWrap.hidden = !required && thresholdRaw === '';
    if (approvalNote) {
      approvalNote.textContent = required
        ? `Owner approval is required because this amount exceeds ${thresholdRaw}.`
        : (thresholdRaw === '' ? 'No high-value transfer approval threshold is configured for this branch.' : `Owner approval applies above ${thresholdRaw}.`);
    }
  };

  type?.addEventListener('change', syncType);
  sourceShift?.addEventListener('change', () => syncShiftRegister(sourceShift, sourceRegister));
  destinationShift?.addEventListener('change', () => syncShiftRegister(destinationShift, destinationRegister));
  amount?.addEventListener('input', syncApproval);
  form.addEventListener('submit', () => {
    syncShiftRegister(sourceShift, sourceRegister);
    syncShiftRegister(destinationShift, destinationRegister);
    syncApproval();
  });

  syncType();
  syncApproval();
})();
