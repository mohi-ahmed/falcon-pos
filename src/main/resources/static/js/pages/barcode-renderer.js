(() => {
  const patterns = [
    '212222','222122','222221','121223','121322','131222','122213','122312','132212','221213','221312','231212','112232','122132','122231','113222','123122','123221','223211','221132','221231','213212','223112','312131','311222','321122','321221','312212','322112','322211','212123','212321','232121','111323','131123','131321','112313','132113','132311','211313','231113','231311','112133','112331','132131','113123','113321','133121','313121','211331','231131','213113','213311','213131','311123','311321','331121','312113','312311','332111','314111','221411','431111','111224','111422','121124','121421','141122','141221','112214','112412','122114','122411','142112','142211','241211','221114','413111','241112','134111','111242','121142','121241','114212','124112','124211','411212','421112','421211','212141','214121','412121','111143','111341','131141','114113','114311','411113','411311','113141','114131','311141','411131','211412','211214','211232','2331112'
  ];

  const code128Svg = (value) => {
    const text = String(value || '');
    if (!text || [...text].some((character) => character.charCodeAt(0) < 32 || character.charCodeAt(0) > 126)) return null;
    const values = [...text].map((character) => character.charCodeAt(0) - 32);
    let checksum = 104;
    values.forEach((number, index) => { checksum += number * (index + 1); });
    const sequence = [104, ...values, checksum % 103, 106];
    let x = 10;
    const bars = [];
    sequence.forEach((code) => {
      [...patterns[code]].forEach((widthText, index) => {
        const width = Number(widthText) * 1.45;
        if (index % 2 === 0) bars.push(`<rect x="${x.toFixed(2)}" y="2" width="${width.toFixed(2)}" height="46"/>`);
        x += width;
      });
    });
    return `<svg viewBox="0 0 ${(x + 10).toFixed(2)} 50" role="img" aria-label="Code 128 barcode ${text}" preserveAspectRatio="none">${bars.join('')}</svg>`;
  };
  const escapeHtml = (value) => String(value ?? '').replace(/[&<>'"]/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
  })[character]);

  document.addEventListener('DOMContentLoaded', () => {
    const choice = document.querySelector('[data-barcode-choice]');
    const quantity = document.querySelector('[data-barcode-label-count]');
    const stock = document.querySelector('[data-barcode-stock]');
    const error = document.querySelector('[data-barcode-error]');
    const generate = document.querySelector('[data-barcode-generate]');
    const sheet = document.querySelector('[data-barcode-sheet]');
    const empty = document.querySelector('[data-barcode-empty]');
    const print = document.querySelector('[data-barcode-print]');
    const reset = document.querySelector('[data-barcode-reset]');
    const addProduct = document.querySelector('[data-additional-barcode-add]');
    const productPicker = document.querySelector('[data-additional-barcode-product]');
    const productSet = document.querySelector('[data-barcode-product-set]');
    const syncStock = () => {
      const available = Number(choice?.selectedOptions?.[0]?.dataset.stock || 0);
      if (stock) stock.value = String(available);
      if (quantity) quantity.max = String(Math.max(1, Math.min(200, Math.floor(available))));
      if (error) error.textContent = '';
    };
    choice?.addEventListener('change', syncStock);
    addProduct?.addEventListener('click', () => {
      const productId = productPicker?.value;
      if (!productId || !productSet) return;
      const option = [...(choice?.options || [])].find((candidate) => candidate.dataset.productId === productId);
      const chip = productSet.querySelector(`[data-product-id="${productId}"]`);
      if (!option || !chip) return;
      chip.dataset.barcode = option.value;
      chip.dataset.optionIndex = String(option.index);
      if (!chip.querySelector('button')) {
        const remove = document.createElement('button');
        remove.type = 'button'; remove.className = 'table-action danger'; remove.textContent = 'Remove';
        remove.addEventListener('click', () => chip.remove()); chip.append(remove);
      }
    });
    generate?.addEventListener('click', () => {
      const option = choice?.selectedOptions?.[0];
      const requested = Number(quantity?.value || 0);
      if (!option?.value) return;
      const selected = [option, ...[...(productSet?.querySelectorAll('[data-option-index]') || [])]
        .map((chip) => choice.options[Number(chip.dataset.optionIndex)])]
        .filter(Boolean).filter((candidate, index, values) => values.findIndex((value) => value.dataset.productId === candidate.dataset.productId) === index);
      const incompatible = selected.find((candidate) => requested < 1 || requested > Number(candidate.dataset.stock || 0));
      if (incompatible) {
        sheet?.replaceChildren();
        if (print) print.disabled = true;
        if (error) error.textContent = `${incompatible.dataset.product || 'Product'} label quantity must be between 1 and available stock (${incompatible.dataset.stock || 0}).`;
        return;
      }
      if (error) error.textContent = '';
      const fields = {};
      document.querySelectorAll('[data-label-field]').forEach((input) => { fields[input.dataset.labelField] = input.checked; });
      sheet?.replaceChildren();
      selected.forEach((candidate) => {
        for (let index = 0; index < requested; index += 1) {
          const label = document.createElement('div'); label.className = 'barcode-label';
          const barcode = code128Svg(candidate.value);
          label.innerHTML = `${fields.business ? `<small>${escapeHtml(sheet?.dataset.business)}</small>` : ''}`
            + `${fields.image && candidate.dataset.image ? `<img src="${escapeHtml(candidate.dataset.image)}" alt="" style="width:28px;height:28px;object-fit:cover;margin:auto">` : ''}`
            + `${fields.product ? `<strong>${escapeHtml(candidate.dataset.product)} · ${escapeHtml(candidate.dataset.variant)}</strong>` : ''}`
            + `${fields.code ? `<small>${escapeHtml(candidate.dataset.reference)} · ${escapeHtml(candidate.dataset.sku)}</small>` : ''}`
            + `<div class="barcode-bars">${barcode || ''}</div><strong>${escapeHtml(candidate.value)}</strong>`
            + `${fields.price ? `<small>Price: ${escapeHtml(candidate.dataset.price || '—')}${fields.currency ? ` ${escapeHtml(candidate.dataset.currency || '')}` : ''}</small>` : ''}`
            + `${fields.category ? `<small>Category: ${escapeHtml(candidate.dataset.category || '—')}</small>` : ''}`
            + `${fields.unit ? `<small>${escapeHtml(candidate.dataset.unit)}</small>` : ''}`;
          sheet?.append(label);
        }
      });
      if (empty) empty.hidden = true;
      if (sheet) sheet.hidden = false;
      if (print) print.disabled = false;
    });
    print?.addEventListener('click', () => window.print());
    reset?.addEventListener('click', () => {
      if (choice) choice.selectedIndex = 0;
      if (quantity) quantity.value = '1';
      sheet?.replaceChildren();
      if (sheet) sheet.hidden = true;
      if (empty) empty.hidden = false;
      if (print) print.disabled = true;
      if (error) error.textContent = '';
      productSet?.querySelectorAll('[data-option-index]').forEach((chip) => chip.remove());
      syncStock();
    });
    syncStock();
  });
})();
