(() => {
  'use strict';

  const productRoot = document.querySelector('.product-ui');
  if (!productRoot) return;

  // Product List is server-filtered/paginated. Prevent the legacy optional client-table helper from re-sorting or truncating a server page.
  document.querySelectorAll('[data-client-table][data-server-driven-table]').forEach((tableRoot) => tableRoot.removeAttribute('data-client-table'));

  const categoryName = document.querySelector('[data-category-name]');
  const categorySlug = document.querySelector('[data-category-slug]');
  let categorySlugEdited = Boolean(categorySlug?.value.trim());
  categorySlug?.addEventListener('input', () => { categorySlugEdited = categorySlug.value.trim().length > 0; });
  categoryName?.addEventListener('input', () => {
    if (!categorySlug || categorySlugEdited) return;
    categorySlug.value = categoryName.value.trim().toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
  });

  document.querySelectorAll('[data-product-filters]').forEach((form) => {
    const advanced = [...form.querySelectorAll('[data-advanced-filter]')];
    if (!advanced.length) return;
    const panel = document.createElement('div');
    panel.className = 'product-advanced-filters';
    panel.id = `product-advanced-filters-${Math.random().toString(36).slice(2, 8)}`;
    advanced[0].before(panel);
    advanced.forEach((node) => panel.append(node));

    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'export-btn product-filter-toggle';
    toggle.setAttribute('aria-expanded', 'false');
    toggle.setAttribute('aria-controls', panel.id);
    toggle.textContent = 'More filters';
    panel.before(toggle);
    toggle.addEventListener('click', () => {
      const open = !panel.classList.contains('is-open');
      panel.classList.toggle('is-open', open);
      toggle.setAttribute('aria-expanded', String(open));
      toggle.textContent = open ? 'Hide filters' : 'More filters';
    });

    const hasAdvancedValue = advanced.some((node) => {
      const value = node.value || '';
      if (!node.matches('select')) return Boolean(value);
      if (node.name === 'archived') return value === 'true';
      if (node.name === 'sortDir') return value === 'desc';
      if (node.name === 'sortBy') return !['', 'name', 'displayOrder'].includes(value);
      if (node.name === 'size') return value !== '' && value !== '10';
      return Boolean(value);
    });
    if (hasAdvancedValue) {
      panel.classList.add('is-open');
      toggle.setAttribute('aria-expanded', 'true');
      toggle.textContent = 'Hide filters';
    }
  });

  document.querySelectorAll('[data-form-tabs]').forEach((tabList) => {
    const tabs = [...tabList.querySelectorAll('[data-form-tab]')];
    if (!tabs.length) return;
    tabs.forEach((tab, index) => {
      tab.tabIndex = tab.classList.contains('is-active') ? 0 : -1;
      tab.addEventListener('keydown', (event) => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
        event.preventDefault();
        let next = index;
        if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
        if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
        if (event.key === 'Home') next = 0;
        if (event.key === 'End') next = tabs.length - 1;
        tabs[next].focus();
        tabs[next].click();
      });
      tab.addEventListener('click', () => tabs.forEach((candidate) => {
        candidate.tabIndex = candidate === tab ? 0 : -1;
      }));
    });
  });

  document.querySelectorAll('[data-form-panel]').forEach((panel) => {
    const hasVisibleError = [...panel.querySelectorAll('.field-error')].some((error) => error.textContent.trim().length > 0);
    if (!hasVisibleError) return;
    const key = panel.dataset.formPanel;
    document.querySelector(`[data-form-tab="${key}"]`)?.click();
  });

  document.querySelectorAll('form').forEach((form) => {
    form.addEventListener('submit', () => {
      const button = form.querySelector('button[type="submit"]:not([disabled])');
      if (!button || button.dataset.keepLabel === 'true') return;
      button.dataset.originalLabel = button.textContent;
      button.setAttribute('aria-busy', 'true');
      button.classList.add('is-submitting');
    });
  });

  document.querySelectorAll('[data-product-section-nav] a').forEach((link) => {
    link.addEventListener('click', (event) => {
      const id = link.getAttribute('href');
      if (!id?.startsWith('#')) return;
      const target = document.querySelector(id);
      if (!target) return;
      event.preventDefault();
      target.scrollIntoView({behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start'});
      history.replaceState(null, '', id);
    });
  });

  document.querySelectorAll('[data-confirm-message]').forEach((form) => {
    form.addEventListener('submit', (event) => {
      if (form.dataset.confirmed === 'true') return;
      const message = form.dataset.confirmMessage;
      if (message && !window.confirm(message)) {
        event.preventDefault();
        const button = form.querySelector('button[type="submit"]');
        button?.classList.remove('is-submitting');
        button?.removeAttribute('aria-busy');
      }
    });
  });
})();
