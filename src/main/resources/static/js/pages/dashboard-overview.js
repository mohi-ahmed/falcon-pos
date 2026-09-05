(() => {
  'use strict';

  const root = document.querySelector('.dashboard-overview');
  if (!root) return;

  const locale = document.documentElement.lang || 'en';
  const money = new Intl.NumberFormat(locale, { maximumFractionDigits: 2, minimumFractionDigits: 0 });
  const compactMoney = new Intl.NumberFormat(locale, { notation: 'compact', maximumFractionDigits: 1 });
  const integerFormat = new Intl.NumberFormat(locale, { maximumFractionDigits: 0 });
  const dayLabel = new Intl.DateTimeFormat(locale, { day: '2-digit', month: 'short' });
  const fullDate = new Intl.DateTimeFormat(locale, { dateStyle: 'medium' });

  initializeRefreshTime();
  initializeRevealAnimations();
  initializeQuickActionIcons();
  initializeCountUpAnimations();
  initializeSalesChart();
  initializePaymentMix();

  function initializeRefreshTime() {
    const refreshTime = document.querySelector('[data-refreshed-at]');
    if (!refreshTime) return;
    const parsed = new Date(refreshTime.getAttribute('datetime') || refreshTime.textContent);
    if (Number.isNaN(parsed.getTime())) return;
    refreshTime.textContent = new Intl.DateTimeFormat(locale, { hour: 'numeric', minute: '2-digit' }).format(parsed);
    refreshTime.title = new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(parsed);
  }

  function initializeRevealAnimations() {
    const revealTargets = [...root.querySelectorAll([
      '.dashboard-hero',
      '.period-bar',
      '.dashboard-kpi',
      '.sales-chart-card',
      '.attention-card',
      '.quick-access-card',
      '.section-heading-row',
      '.metric-panel',
      '.ranking-card',
      '.financial-breakdown',
      '.payment-mix-card',
      '.shifts-card',
      '.dashboard-no-access'
    ].join(','))];

    revealTargets.forEach((target, index) => {
      target.classList.add('reveal-on-scroll');
      target.style.setProperty('--reveal-delay', `${Math.min(index * 36, 320)}ms`);
    });

    const revealObserver = new IntersectionObserver((entries, observer) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('is-visible');
        observer.unobserve(entry.target);
      });
    }, { threshold: 0.14, rootMargin: '0px 0px -24px 0px' });

    revealTargets.forEach((target) => revealObserver.observe(target));
  }

  function initializeQuickActionIcons() {
    const iconLibrary = {
      open_pos: '<path d="M6 4h10l2 3v11a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4Zm0 4h12"/><path d="M10 13h4M10 17h4"/>',
      add_product: '<path d="m4 7 8-4 8 4-8 4-8-4Zm0 0v10l8 4 8-4V7"/><path d="M12 11v9M9 14h6"/>',
      create_purchase: '<path d="M3 6h2l2 10h10l2-7H7"/><path d="M9 20h.01M17 20h.01"/><path d="M12 10v4M10 12h4"/>',
      add_supplier: '<path d="M16 20v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2"/><circle cx="9.5" cy="7" r="4"/><path d="M18 8h4M20 6v4"/>',
      record_expense: '<path d="M12 2v20M17 6.5c0-1.4-1.8-2.5-4.5-2.5S8 5.1 8 6.8c0 4.2 9 2.3 9 7 0 1.8-1.8 3.2-4.8 3.2S7 15.6 7 14"/>',
      pay_supplier_due: '<path d="M4 7h16v10H4z"/><path d="M4 10h16"/><path d="M8 15h3"/>',
      start_stock_count: '<path d="M5 5h14v14H5z"/><path d="M9 9h6M9 13h6M9 17h4"/><path d="m4 12 2 2 3-4"/>',
      create_stock_transfer: '<path d="M7 7h10v4H7z"/><path d="M7 13h10v4H7z"/><path d="m11 9 2 0M13 15h-2"/><path d="m15 9 3 3-3 3"/><path d="m9 15-3-3 3-3"/>',
      view_expiring: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
      open_analytics: '<path d="M4 19V9M10 19V5M16 19v-7M22 19H2"/>',
      receive_customer_due: '<path d="M16 20v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2"/><circle cx="9.5" cy="7" r="4"/><path d="M18 12h4"/><path d="M20 10v4"/>'
    };

    document.querySelectorAll('.quick-action').forEach((link) => {
      const code = String(link.dataset.quickCode || '').trim().toLowerCase();
      const label = link.querySelector('strong')?.textContent?.trim().toLowerCase() || '';
      const iconHost = link.querySelector('.quick-icon svg');
      if (!iconHost) return;

      const selected = iconLibrary[code] || chooseFallbackIcon(label, iconLibrary);
      if (selected) iconHost.innerHTML = selected;
    });

    function chooseFallbackIcon(label, library) {
      if (label.includes('pos')) return library.open_pos;
      if (label.includes('product')) return library.add_product;
      if (label.includes('purchase')) return library.create_purchase;
      if (label.includes('supplier')) return library.add_supplier;
      if (label.includes('expense')) return library.record_expense;
      if (label.includes('stock count')) return library.start_stock_count;
      if (label.includes('transfer')) return library.create_stock_transfer;
      if (label.includes('expir')) return library.view_expiring;
      if (label.includes('analytic') || label.includes('report')) return library.open_analytics;
      if (label.includes('customer due')) return library.receive_customer_due;
      if (label.includes('supplier due')) return library.pay_supplier_due;
      return null;
    }
  }

  function initializeCountUpAnimations() {
    const selectors = [
      '.dashboard-kpi > strong',
      '.financial-grid strong',
      '.metric-list strong',
      '.rank-value strong',
      '.payment-mix-list strong',
      '[data-payment-total]',
      '.shift-cash',
      '.alert-count'
    ].join(',');

    const nodes = [...root.querySelectorAll(selectors)].filter((node) => parseDisplayValue(node.textContent).isNumeric);
    if (!nodes.length) return;

    const observer = new IntersectionObserver((entries, obs) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        animateDisplayValue(entry.target);
        obs.unobserve(entry.target);
      });
    }, { threshold: 0.45, rootMargin: '0px 0px -24px 0px' });

    nodes.forEach((node) => observer.observe(node));
  }

  function animateDisplayValue(node) {
    if (!node || node.dataset.countAnimated === 'true') return;
    const parsed = parseDisplayValue(node.textContent);
    if (!parsed.isNumeric) return;

    node.dataset.countAnimated = 'true';
    const startValue = parsed.value < 0 ? parsed.value * 0.18 : 0;
    const duration = Math.min(1500, Math.max(700, 520 + String(Math.abs(parsed.value)).length * 80));
    const startedAt = performance.now();

    const tick = (time) => {
      const progress = Math.min(1, (time - startedAt) / duration);
      const eased = easeOutCubic(progress);
      const current = startValue + ((parsed.value - startValue) * eased);
      node.textContent = `${parsed.prefix}${formatNumber(current, parsed.decimals)}${parsed.suffix}`;
      if (progress < 1) requestAnimationFrame(tick);
      else node.textContent = parsed.original;
    };

    requestAnimationFrame(tick);
  }

  function initializeSalesChart() {
    const canvas = document.getElementById('dashboardSalesChart');
    const seriesContainer = document.querySelector('[data-dashboard-series]');
    const emptyState = document.querySelector('[data-chart-empty]');
    const tooltip = document.querySelector('[data-chart-tooltip]');
    const chartMode = document.querySelector('[data-chart-mode]');
    const chartWrap = canvas?.closest('.sales-chart-wrap');
    const currency = chartWrap?.dataset.chartCurrency?.trim() || document.querySelector('.chart-currency')?.textContent?.trim() || '';
    if (!canvas || !seriesContainer || !chartWrap) return;

    const points = [...seriesContainer.children].map((node) => ({
      date: node.dataset.date,
      sales: Number(node.dataset.sales || 0),
      expense: Number(node.dataset.expense || 0)
    })).filter((point) => Number.isFinite(point.sales) && Number.isFinite(point.expense));

    let hoverIndex = -1;
    let drawFrame = 0;
    let animationFrame = 0;
    let currentProgress = 1;
    let regions = [];

    const render = (progress = currentProgress) => {
      currentProgress = progress;
      cancelAnimationFrame(drawFrame);
      drawFrame = requestAnimationFrame(() => drawSalesChart(canvas, points, progress));
    };

    const animateChart = () => {
      cancelAnimationFrame(animationFrame);
      const start = performance.now();
      const duration = points.length === 1 ? 920 : 1180;

      const frame = (time) => {
        const progress = Math.min(1, (time - start) / duration);
        render(easeOutCubic(progress));
        if (progress < 1) animationFrame = requestAnimationFrame(frame);
      };

      render(0.001);
      animationFrame = requestAnimationFrame(frame);
    };

    const setupCanvas = (target) => {
      const rect = target.getBoundingClientRect();
      if (!rect.width) return null;
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const width = Math.max(320, Math.round(rect.width));
      const height = Math.max(250, Math.round(rect.height));
      target.width = Math.round(width * dpr);
      target.height = Math.round(height * dpr);
      const ctx = target.getContext('2d');
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, width, height);
      return { ctx, width, height };
    };

    const drawSalesChart = (target, data, progress) => {
      const surface = setupCanvas(target);
      if (!surface) return;
      const { ctx, width, height } = surface;
      const hasValue = data.some((point) => point.sales !== 0 || point.expense !== 0);

      if (!data.length || !hasValue) {
        if (emptyState) emptyState.hidden = false;
        if (chartMode) chartMode.textContent = 'No activity';
        regions = [];
        return;
      }

      if (emptyState) emptyState.hidden = true;
      if (data.length === 1) {
        if (chartMode) chartMode.textContent = 'Selected day';
        drawSinglePeriodChart(ctx, width, height, data[0], progress);
      } else {
        if (chartMode) chartMode.textContent = data.length <= 7 ? 'Daily trend' : `${data.length}-day trend`;
        drawTrendChart(ctx, width, height, data, progress);
      }
    };

    const drawGrid = (ctx, width, height, left, right, top, bottom, max) => {
      const plotH = height - top - bottom;
      ctx.save();
      ctx.font = '600 10.5px Inter, system-ui, sans-serif';
      ctx.textBaseline = 'middle';
      ctx.lineWidth = 1;

      for (let step = 0; step <= 4; step += 1) {
        const yy = top + (plotH * step / 4);
        const value = max * (1 - step / 4);
        ctx.strokeStyle = step === 4 ? '#d9e5df' : '#eaf1ed';
        ctx.setLineDash(step === 4 ? [] : [4, 5]);
        ctx.beginPath();
        ctx.moveTo(left, yy);
        ctx.lineTo(width - right, yy);
        ctx.stroke();
        ctx.setLineDash([]);
        ctx.fillStyle = '#86978f';
        ctx.textAlign = 'right';
        ctx.fillText(compactMoney.format(value), left - 10, yy);
      }

      ctx.restore();
    };

    const drawSinglePeriodChart = (ctx, width, height, point, progress) => {
      const left = width < 520 ? 54 : 68;
      const right = 22;
      const top = 28;
      const bottom = 56;
      const plotW = width - left - right;
      const plotH = height - top - bottom;
      const max = niceScaleMax(Math.max(point.sales, point.expense, 1));
      const baseline = top + plotH;
      const center = left + plotW / 2;
      const gap = Math.min(50, plotW * 0.12);
      const barWidth = Math.min(104, Math.max(56, plotW * 0.18));
      const values = [
        { key: 'sales', label: 'Net sales', value: Math.max(0, point.sales), primary: '#0b8b69', secondary: '#5fc4a4', soft: '#dff2ea' },
        { key: 'expense', label: 'Operating expense', value: Math.max(0, point.expense), primary: '#f0a13c', secondary: '#f6c270', soft: '#fbe9d0' }
      ];

      drawGrid(ctx, width, height, left, right, top, bottom, max);
      regions = [];

      values.forEach((item, index) => {
        const originX = center + (index === 0 ? -(barWidth + gap / 2) : gap / 2);
        const fullHeight = Math.max(0, (item.value / max) * plotH);
        const barProgress = clamp((progress - index * 0.16) / 0.7, 0, 1);
        const heightNow = item.value > 0 ? Math.max(6, fullHeight * easeOutBack(barProgress)) : 4;
        const y = baseline - heightNow;
        const radius = Math.min(18, barWidth / 3);

        ctx.save();
        roundedRect(ctx, originX, y, barWidth, heightNow, radius);
        const fill = ctx.createLinearGradient(0, y, 0, baseline);
        fill.addColorStop(0, item.primary);
        fill.addColorStop(1, item.secondary);
        ctx.fillStyle = item.value > 0 ? fill : item.soft;
        ctx.shadowColor = item.key === 'sales' ? 'rgba(12, 128, 98, .16)' : 'rgba(218, 155, 66, .14)';
        ctx.shadowBlur = 20;
        ctx.shadowOffsetY = 10;
        ctx.fill();
        ctx.restore();

        const bubbleY = Math.max(top + 14, y - 26);
        ctx.save();
        ctx.globalAlpha = clamp((progress - 0.28) / 0.72, 0, 1);
        roundedRect(ctx, originX + barWidth / 2 - 42, bubbleY, 84, 24, 12);
        ctx.fillStyle = '#ffffff';
        ctx.strokeStyle = '#dbe7e1';
        ctx.lineWidth = 1;
        ctx.fill();
        ctx.stroke();
        ctx.fillStyle = '#25453a';
        ctx.font = '750 11.5px Inter, system-ui, sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(compactMoney.format(item.value), originX + barWidth / 2, bubbleY + 12);
        ctx.restore();

        ctx.save();
        ctx.fillStyle = '#6d7f77';
        ctx.font = '650 10.5px Inter, system-ui, sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'top';
        ctx.fillText(item.label, originX + barWidth / 2, baseline + 13);
        ctx.restore();

        regions.push({
          point,
          x: originX + barWidth / 2,
          hit(mx) {
            return mx >= originX - 10 && mx <= originX + barWidth + 10;
          }
        });
      });

      ctx.save();
      ctx.fillStyle = '#8b9992';
      ctx.font = '600 10px Inter, system-ui, sans-serif';
      ctx.textAlign = 'right';
      ctx.textBaseline = 'top';
      const date = parseDate(point.date);
      if (date) ctx.fillText(fullDate.format(date), width - right, 8);
      ctx.restore();
    };

    const drawTrendChart = (ctx, width, height, data, progress) => {
      const left = width < 520 ? 52 : 66;
      const right = 20;
      const top = 28;
      const bottom = 42;
      const plotW = width - left - right;
      const plotH = height - top - bottom;
      const max = niceScaleMax(Math.max(...data.flatMap((point) => [point.sales, point.expense]), 1));
      const baseline = top + plotH;
      const x = (index) => left + (plotW * index / Math.max(1, data.length - 1));
      const y = (value) => top + plotH - (Math.max(0, value) / max) * plotH;
      const barSpace = plotW / Math.max(data.length, 1);
      const barWidth = clamp(barSpace * 0.44, 14, 28);
      const labelEvery = data.length <= 8 ? 1 : data.length <= 16 ? 2 : Math.ceil(data.length / 7);
      const lineReveal = clamp((progress - 0.14) / 0.86, 0, 1);

      drawGrid(ctx, width, height, left, right, top, bottom, max);

      // Sales bars
      regions = data.map((point, index) => ({
        point,
        x: x(index),
        hit(mx) {
          const previous = index === 0 ? left : (x(index - 1) + x(index)) / 2;
          const next = index === data.length - 1 ? width - right : (x(index) + x(index + 1)) / 2;
          return mx >= previous && mx <= next;
        }
      }));

      data.forEach((point, index) => {
        const barProgress = clamp((progress - index * 0.035) / 0.62, 0, 1);
        const fullHeight = Math.max(0, baseline - y(point.sales));
        const drawHeight = fullHeight * easeOutCubic(barProgress);
        const barX = x(index) - barWidth / 2;
        const barY = baseline - drawHeight;
        const radius = Math.min(14, barWidth / 2);

        ctx.save();
        roundedRect(ctx, barX, barY, barWidth, Math.max(3, drawHeight), radius);
        const fill = ctx.createLinearGradient(0, barY, 0, baseline);
        fill.addColorStop(0, '#0b8b69');
        fill.addColorStop(1, '#63c7a7');
        ctx.fillStyle = fill;
        ctx.shadowColor = 'rgba(8, 120, 91, .16)';
        ctx.shadowBlur = 14;
        ctx.shadowOffsetY = 7;
        ctx.fill();
        ctx.restore();
      });

      // Sales value label for the currently hovered index.
      if (hoverIndex >= 0 && hoverIndex < data.length) {
        const bubbleX = x(hoverIndex);
        const bubbleY = y(data[hoverIndex].sales) - 28;
        ctx.save();
        roundedRect(ctx, bubbleX - 38, bubbleY, 76, 22, 11);
        ctx.fillStyle = '#ffffff';
        ctx.strokeStyle = '#dbe7e1';
        ctx.lineWidth = 1;
        ctx.fill();
        ctx.stroke();
        ctx.font = '750 10.5px Inter, system-ui, sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillStyle = '#234439';
        ctx.fillText(compactMoney.format(data[hoverIndex].sales), bubbleX, bubbleY + 11);
        ctx.restore();
      }

      // Expense line with reveal clip.
      const expensePoints = data.map((point, index) => ({ x: x(index), y: y(point.expense) }));
      const salesPoints = data.map((point, index) => ({ x: x(index), y: y(point.sales) }));

      ctx.save();
      ctx.beginPath();
      ctx.rect(left - 4, top - 20, Math.max(6, plotW * lineReveal + 10), plotH + bottom + 30);
      ctx.clip();

      const salesArea = ctx.createLinearGradient(0, top, 0, baseline);
      salesArea.addColorStop(0, 'rgba(11, 139, 105, .16)');
      salesArea.addColorStop(0.55, 'rgba(11, 139, 105, .06)');
      salesArea.addColorStop(1, 'rgba(11, 139, 105, 0)');
      ctx.beginPath();
      buildSmoothPath(ctx, salesPoints);
      ctx.lineTo(x(data.length - 1), baseline);
      ctx.lineTo(x(0), baseline);
      ctx.closePath();
      ctx.fillStyle = salesArea;
      ctx.fill();

      drawSmoothLine(ctx, expensePoints, '#eca443', 2.4, true, 'rgba(240, 161, 60, .12)');
      if (data.length <= 18) {
        expensePoints.forEach((coordinate, index) => {
          const pointProgress = clamp((progress - 0.25 - index * 0.015) / 0.55, 0, 1);
          if (pointProgress > 0) drawPoint(ctx, coordinate.x, coordinate.y, '#eca443', 2.4 + (pointProgress * 1.1));
        });
      }
      ctx.restore();

      if (hoverIndex >= 0 && hoverIndex < data.length) {
        const xx = x(hoverIndex);
        ctx.save();
        ctx.strokeStyle = 'rgba(34, 72, 57, .18)';
        ctx.lineWidth = 1;
        ctx.setLineDash([4, 5]);
        ctx.beginPath();
        ctx.moveTo(xx, top);
        ctx.lineTo(xx, baseline);
        ctx.stroke();
        ctx.restore();

        drawPoint(ctx, xx, y(data[hoverIndex].sales), '#0b8b69', 4.7);
        if (data[hoverIndex].expense > 0) drawPoint(ctx, xx, y(data[hoverIndex].expense), '#eca443', 4.1);
      }

      ctx.save();
      ctx.font = '600 10px Inter, system-ui, sans-serif';
      ctx.fillStyle = '#87978f';
      ctx.textBaseline = 'top';
      data.forEach((point, index) => {
        if (index % labelEvery !== 0 && index !== data.length - 1) return;
        const date = parseDate(point.date);
        if (!date) return;
        ctx.textAlign = index === 0 ? 'left' : index === data.length - 1 ? 'right' : 'center';
        ctx.fillText(dayLabel.format(date), x(index), height - 24);
      });
      ctx.restore();
    };

    const buildSmoothPath = (ctx, coordinates) => {
      if (!coordinates.length) return;
      ctx.moveTo(coordinates[0].x, coordinates[0].y);
      for (let index = 0; index < coordinates.length - 1; index += 1) {
        const current = coordinates[index];
        const next = coordinates[index + 1];
        const midX = (current.x + next.x) / 2;
        ctx.bezierCurveTo(midX, current.y, midX, next.y, next.x, next.y);
      }
    };

    const drawSmoothLine = (ctx, coordinates, color, lineWidth, dashed, shadowColor) => {
      if (coordinates.length < 2) return;
      ctx.save();
      ctx.beginPath();
      buildSmoothPath(ctx, coordinates);
      ctx.strokeStyle = color;
      ctx.lineWidth = lineWidth;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      if (dashed) ctx.setLineDash([7, 6]);
      ctx.shadowColor = shadowColor || 'transparent';
      ctx.shadowBlur = shadowColor ? 14 : 0;
      ctx.stroke();
      ctx.restore();
    };

    const drawPoint = (ctx, px, py, color, radius) => {
      ctx.save();
      ctx.beginPath();
      ctx.arc(px, py, radius, 0, Math.PI * 2);
      ctx.fillStyle = '#ffffff';
      ctx.fill();
      ctx.strokeStyle = color;
      ctx.lineWidth = radius >= 4 ? 2.2 : 1.7;
      ctx.stroke();
      ctx.restore();
    };

    const roundedRect = (ctx, x, y, width, height, radius) => {
      const r = Math.min(radius, width / 2, height / 2);
      ctx.beginPath();
      ctx.moveTo(x + r, y);
      ctx.lineTo(x + width - r, y);
      ctx.quadraticCurveTo(x + width, y, x + width, y + r);
      ctx.lineTo(x + width, y + height - r);
      ctx.quadraticCurveTo(x + width, y + height, x + width - r, y + height);
      ctx.lineTo(x + r, y + height);
      ctx.quadraticCurveTo(x, y + height, x, y + height - r);
      ctx.lineTo(x, y + r);
      ctx.quadraticCurveTo(x, y, x + r, y);
      ctx.closePath();
    };

    const showTooltip = (event) => {
      if (!tooltip || !regions.length) return;
      const rect = canvas.getBoundingClientRect();
      const mx = event.clientX - rect.left;
      const regionIndex = regions.findIndex((region) => region.hit(mx));
      const region = regionIndex >= 0 ? regions[regionIndex] : null;
      if (!region) {
        tooltip.hidden = true;
        if (hoverIndex !== -1) {
          hoverIndex = -1;
          render();
        }
        return;
      }

      if (points.length > 1 && hoverIndex !== regionIndex) {
        hoverIndex = regionIndex;
        render();
      }

      const date = parseDate(region.point.date);
      tooltip.innerHTML = `<strong>${date ? fullDate.format(date) : escapeHtml(region.point.date)}</strong>` +
        `<div><span>Net sales</span><b>${escapeHtml(currency)} ${money.format(region.point.sales)}</b></div>` +
        `<div><span>Operating expense</span><b>${escapeHtml(currency)} ${money.format(region.point.expense)}</b></div>`;
      tooltip.hidden = false;
      const wrapRect = chartWrap.getBoundingClientRect();
      const preferredLeft = event.clientX - wrapRect.left + 14;
      const preferredTop = event.clientY - wrapRect.top - 30;
      const maxLeft = wrapRect.width - tooltip.offsetWidth - 10;
      tooltip.style.left = `${Math.max(10, Math.min(preferredLeft, maxLeft))}px`;
      tooltip.style.top = `${Math.max(12, preferredTop)}px`;
    };

    canvas.addEventListener('mousemove', showTooltip);
    canvas.addEventListener('mouseleave', () => {
      if (tooltip) tooltip.hidden = true;
      if (hoverIndex !== -1) {
        hoverIndex = -1;
        render();
      }
    });

    const chartObserver = new IntersectionObserver((entries, observer) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        animateChart();
        observer.unobserve(entry.target);
      });
    }, { threshold: 0.3 });

    chartObserver.observe(chartWrap.closest('.sales-chart-card') || chartWrap);
    window.addEventListener('resize', () => render(), { passive: true });
  }

  function initializePaymentMix() {
    const donut = document.querySelector('[data-payment-donut]');
    const paymentList = document.querySelector('[data-payment-mix]');
    const paymentTotal = document.querySelector('[data-payment-total]');
    if (!donut || !paymentList) return;

    const rows = [...paymentList.children].map((row, index) => ({
      row,
      index,
      label: String(row.dataset.label || row.querySelector('span > span')?.textContent || '').trim(),
      amount: Math.max(0, Number(row.dataset.amount || 0))
    }));
    const total = rows.reduce((sum, item) => sum + item.amount, 0);

    const paymentColor = (label, index) => {
      const normalized = String(label || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
      if (normalized.includes('cash')) return '#0b8b69';
      if (normalized.includes('card') || normalized.includes('visa') || normalized.includes('mastercard')) return '#3b82f6';
      if (normalized.includes('mobile') || normalized.includes('bkash') || normalized.includes('nagad') || normalized.includes('rocket')) return '#8b5cf6';
      if (normalized.includes('bank') || normalized.includes('transfer')) return '#0f9f9a';
      if (normalized.includes('wallet')) return '#e69a2d';
      if (normalized.includes('credit') || normalized.includes('due')) return '#d2675d';
      const fallback = ['#64748b', '#2563eb', '#7c3aed', '#0f766e', '#b7791f', '#5b6470'];
      return fallback[index % fallback.length];
    };

    rows.forEach((item) => {
      const percentage = total > 0 ? (item.amount / total) * 100 : 0;
      const color = paymentColor(item.label, item.index);
      const dot = item.row.querySelector('i');
      if (dot) dot.style.background = color;
      item.row.style.setProperty('--payment-accent', color);

      const labelHost = item.row.querySelector('span > span');
      if (labelHost && !item.row.querySelector('.payment-share')) {
        const share = document.createElement('small');
        share.className = 'payment-share';
        share.textContent = `${percentage.toFixed(percentage >= 10 ? 0 : 1)}%`;
        labelHost.insertAdjacentElement('afterend', share);
      }
    });

    const renderDonut = (progress) => {
      let cursor = 0;
      const segments = [];
      rows.forEach((item) => {
        const percentage = total > 0 ? (item.amount / total) * 100 * progress : 0;
        const color = paymentColor(item.label, item.index);
        if (percentage > 0) {
          segments.push(`${color} ${cursor}% ${cursor + percentage}%`);
          cursor += percentage;
        }
      });

      if (cursor < 100) segments.push(`#e8efec ${cursor}% 100%`);
      donut.style.background = segments.length ? `conic-gradient(${segments.join(',')})` : '#edf2ef';
      donut.setAttribute('aria-label', `Payment mix total ${currencyText()} ${money.format(total)}`.trim());
      if (paymentTotal) paymentTotal.textContent = `${currencyText()} ${compactMoney.format(total * progress)}`.trim();
    };

    const animateDonut = () => {
      const start = performance.now();
      const duration = 1050;
      const step = (time) => {
        const progress = easeOutCubic(Math.min(1, (time - start) / duration));
        renderDonut(progress);
        if (progress < 1) requestAnimationFrame(step);
        else if (paymentTotal) paymentTotal.textContent = `${currencyText()} ${compactMoney.format(total)}`.trim();
      };
      renderDonut(0.02);
      requestAnimationFrame(step);
    };

    const observer = new IntersectionObserver((entries, obs) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        animateDonut();
        obs.unobserve(entry.target);
      });
    }, { threshold: 0.35 });

    observer.observe(donut.closest('.payment-mix-card') || donut);

    function currencyText() {
      return document.querySelector('.chart-currency')?.textContent?.trim() || '';
    }
  }

  function parseDisplayValue(text) {
    const original = String(text || '').trim();
    const match = original.match(/-?\d[\d,]*(?:\.\d+)?/);
    if (!match) return { isNumeric: false, original };

    const prefix = original.slice(0, match.index);
    const suffix = original.slice((match.index || 0) + match[0].length);
    const value = Number(match[0].replace(/,/g, ''));
    const decimals = match[0].includes('.') ? match[0].split('.')[1].length : 0;
    return {
      isNumeric: Number.isFinite(value),
      original,
      prefix,
      suffix,
      value,
      decimals
    };
  }

  function formatNumber(value, decimals) {
    const options = { minimumFractionDigits: decimals, maximumFractionDigits: decimals };
    const formatter = decimals === 0 ? integerFormat : new Intl.NumberFormat(locale, options);
    return formatter.format(value);
  }

  function parseDate(value) {
    const date = new Date(`${value}T00:00:00`);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  function niceScaleMax(value) {
    const target = Math.max(value * 1.14, 1);
    const magnitude = Math.pow(10, Math.floor(Math.log10(target)));
    const normalized = target / magnitude;
    const nice = normalized <= 1.2 ? 1.2 : normalized <= 1.5 ? 1.5 : normalized <= 2 ? 2 : normalized <= 2.5 ? 2.5 : normalized <= 4 ? 4 : normalized <= 5 ? 5 : normalized <= 8 ? 8 : 10;
    return nice * magnitude;
  }

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  function easeOutCubic(value) {
    return 1 - Math.pow(1 - value, 3);
  }

  function easeOutBack(value) {
    const c1 = 1.70158;
    const c3 = c1 + 1;
    return 1 + (c3 * Math.pow(value - 1, 3)) + (c1 * Math.pow(value - 1, 2));
  }

  function escapeHtml(value) {
    return String(value || '').replace(/[&<>'"]/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[char]));
  }
})();
