(() => {
  const money = new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 });
  const integer = new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 });
  const percentFormat = new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 });
  const dateFormat = new Intl.DateTimeFormat(undefined, { day: '2-digit', month: 'short' });
  const palette = ['#08785b','#4f6f9c','#b9823e','#8e67a8','#4f8b8a','#b25b55','#6f7b73','#9a7b58'];
  const uiFont = 'Inter, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
  const root = document.querySelector('[data-analytics-root]');
  const currency = root?.dataset.currency || '';

  const formatAmount = value => `${currency ? `${currency} ` : ''}${money.format(Number(value || 0))}`;
  const formatDate = value => {
    if (!value) return 'Date';
    const parsed = new Date(`${value}T00:00:00`);
    return Number.isNaN(parsed.getTime()) ? value : dateFormat.format(parsed);
  };

  const fitCanvas = (canvas, cssHeight) => {
    if (!canvas) return null;
    const ratio = Math.max(1, window.devicePixelRatio || 1);
    const width = Math.max(300, canvas.parentElement?.clientWidth || canvas.clientWidth || 800);
    const height = cssHeight || Math.max(220, canvas.clientHeight || 280);
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
    canvas.style.width = `${width}px`;
    canvas.style.height = `${height}px`;
    const ctx = canvas.getContext('2d');
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    return { ctx, width, height };
  };

  const roundRect = (ctx, x, y, width, height, radius) => {
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

  const niceAxis = maxValue => {
    const max = Math.max(1, Number(maxValue || 0));
    const rough = max / 4;
    const magnitude = 10 ** Math.floor(Math.log10(rough));
    const normalized = rough / magnitude;
    const stepFactor = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 2.5 ? 2.5 : normalized <= 5 ? 5 : 10;
    const step = stepFactor * magnitude;
    return { step, max: Math.ceil(max / step) * step };
  };

  const drawEmpty = (ctx, width, height, message) => {
    ctx.fillStyle = '#7a8a82';
    ctx.font = `500 13px ${uiFont}`;
    ctx.textAlign = 'center';
    ctx.fillText(message, width / 2, height / 2);
    ctx.textAlign = 'start';
  };

  const setTooltipContent = (tooltip, title, rows) => {
    tooltip.replaceChildren();
    const heading = document.createElement('strong');
    heading.textContent = title;
    tooltip.appendChild(heading);
    rows.forEach(({ label, value, accent }) => {
      const row = document.createElement('div');
      const name = document.createElement('span');
      name.textContent = label;
      if (accent) {
        const dot = document.createElement('i');
        dot.style.background = accent;
        name.prepend(dot);
      }
      const amount = document.createElement('b');
      amount.textContent = value;
      row.append(name, amount);
      tooltip.appendChild(row);
    });
  };

  const positionTooltip = (canvas, tooltip, anchorX, anchorY) => {
    const stage = canvas.parentElement;
    if (!stage) return;
    tooltip.classList.add('is-visible');
    const pad = 10;
    const maxLeft = Math.max(pad, stage.clientWidth - tooltip.offsetWidth - pad);
    const left = Math.min(maxLeft, Math.max(pad, anchorX + 12));
    const top = Math.max(pad, Math.min(stage.clientHeight - tooltip.offsetHeight - pad, anchorY - tooltip.offsetHeight / 2));
    tooltip.style.left = `${left}px`;
    tooltip.style.top = `${top}px`;
  };

  const hideTooltip = tooltip => tooltip?.classList.remove('is-visible');

  const pointerPosition = (canvas, event) => {
    const rect = canvas.getBoundingClientRect();
    return {
      x: (event.clientX - rect.left) * (canvas.clientWidth / rect.width),
      y: (event.clientY - rect.top) * (canvas.clientHeight / rect.height)
    };
  };

  const bindRegions = (canvas, regionsRef, describeRegion) => {
    if (!canvas || canvas.dataset.chartInteractive === 'true') return;
    canvas.dataset.chartInteractive = 'true';
    const tooltip = canvas.parentElement?.querySelector(`[data-chart-tooltip="${canvas.id}"]`);
    let activeIndex = 0;

    const show = (region, x, y) => {
      if (!tooltip || !region) return;
      const content = describeRegion(region);
      setTooltipContent(tooltip, content.title, content.rows);
      positionTooltip(canvas, tooltip, x ?? region.anchorX, y ?? region.anchorY);
    };

    canvas.addEventListener('pointermove', event => {
      const point = pointerPosition(canvas, event);
      const region = regionsRef.current.find(item => item.hit(point.x, point.y));
      if (!region) return hideTooltip(tooltip);
      activeIndex = Math.max(0, regionsRef.current.indexOf(region));
      show(region, point.x, point.y);
    });
    canvas.addEventListener('pointerdown', event => {
      const point = pointerPosition(canvas, event);
      const region = regionsRef.current.find(item => item.hit(point.x, point.y));
      if (region) show(region, point.x, point.y);
    });
    canvas.addEventListener('pointerleave', () => hideTooltip(tooltip));
    canvas.addEventListener('blur', () => hideTooltip(tooltip));
    canvas.addEventListener('focus', () => {
      if (!regionsRef.current.length) return;
      activeIndex = Math.min(activeIndex, regionsRef.current.length - 1);
      show(regionsRef.current[activeIndex]);
    });
    canvas.addEventListener('keydown', event => {
      if (!regionsRef.current.length || !['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','Home','End'].includes(event.key)) return;
      event.preventDefault();
      if (event.key === 'Home') activeIndex = 0;
      else if (event.key === 'End') activeIndex = regionsRef.current.length - 1;
      else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') activeIndex = Math.max(0, activeIndex - 1);
      else activeIndex = Math.min(regionsRef.current.length - 1, activeIndex + 1);
      show(regionsRef.current[activeIndex]);
    });
  };

  const performanceSource = document.querySelector('[data-performance-source]');
  const performance = performanceSource ? [...performanceSource.children].map(node => ({
    date: node.dataset.date,
    income: Number(node.dataset.income || 0),
    expense: Number(node.dataset.expense || 0),
    profit: Number(node.dataset.profit || 0)
  })) : [];

  const salesExpenseRegions = { current: [] };
  const salesTrendRegions = { current: [] };
  const productRegions = { current: [] };
  const paymentRegions = { current: [] };

  const drawAxes = (ctx, width, height, left, right, top, bottom, maxValue) => {
    const plotW = width - left - right;
    const plotH = height - top - bottom;
    const axis = niceAxis(maxValue);
    ctx.lineWidth = 1;
    ctx.font = `500 10px ${uiFont}`;
    for (let index = 0; index <= 4; index += 1) {
      const value = axis.max - axis.step * index;
      const yy = top + plotH * index / 4;
      ctx.strokeStyle = '#e7eeea';
      ctx.beginPath();
      ctx.moveTo(left, yy);
      ctx.lineTo(width - right, yy);
      ctx.stroke();
      ctx.fillStyle = '#7a8a82';
      ctx.textAlign = 'right';
      ctx.fillText(money.format(value), left - 9, yy + 3);
    }
    ctx.textAlign = 'start';
    return { plotW, plotH, axisMax: axis.max };
  };

  const drawSalesExpense = () => {
    const canvas = document.getElementById('salesExpenseChart');
    const fitted = fitCanvas(canvas, 300);
    if (!fitted) return;
    const { ctx, width, height } = fitted;
    ctx.clearRect(0, 0, width, height);
    salesExpenseRegions.current = [];
    if (!performance.length) return drawEmpty(ctx, width, height, 'No sales or expense data for this month.');

    const left = 66, right = 18, top = 18, bottom = 42;
    const maxValue = Math.max(1, ...performance.flatMap(point => [point.income, point.expense]));
    const { plotW, plotH, axisMax } = drawAxes(ctx, width, height, left, right, top, bottom, maxValue);
    const groupW = plotW / performance.length;
    const barW = Math.max(3, Math.min(12, groupW * .28));
    const gap = Math.max(2, Math.min(5, groupW * .1));
    const y = value => top + plotH - (Math.max(0, value) / axisMax) * plotH;

    performance.forEach((point, index) => {
      const center = left + groupW * index + groupW / 2;
      const salesX = center - gap / 2 - barW;
      const expenseX = center + gap / 2;
      const salesY = y(point.income);
      const expenseY = y(point.expense);
      const salesH = point.income > 0 ? Math.max(2, top + plotH - salesY) : 0;
      const expenseH = point.expense > 0 ? Math.max(2, top + plotH - expenseY) : 0;

      if (salesH > 0) {
        roundRect(ctx, salesX, top + plotH - salesH, barW, salesH, Math.min(3, barW / 2));
        ctx.fillStyle = '#08785b';
        ctx.fill();
      }
      if (expenseH > 0) {
        roundRect(ctx, expenseX, top + plotH - expenseH, barW, expenseH, Math.min(3, barW / 2));
        ctx.fillStyle = '#c16b5f';
        ctx.fill();
      }

      salesExpenseRegions.current.push({
        data: point,
        anchorX: center,
        anchorY: Math.min(salesY, expenseY, top + plotH - 16),
        hit: (xPos, yPos) => xPos >= center - groupW / 2 && xPos <= center + groupW / 2 && yPos >= top && yPos <= top + plotH
      });
    });

    const labelStep = Math.max(1, Math.ceil(performance.length / 8));
    ctx.fillStyle = '#71817a';
    ctx.textAlign = 'center';
    ctx.font = `500 10px ${uiFont}`;
    performance.forEach((point, index) => {
      if (index % labelStep === 0 || index === performance.length - 1) {
        const center = left + groupW * index + groupW / 2;
        ctx.fillText(formatDate(point.date).split(' ')[0], center, height - 15);
      }
    });
    ctx.textAlign = 'start';
  };

  const drawSalesTrend = () => {
    const canvas = document.getElementById('salesTrendChart');
    const fitted = fitCanvas(canvas, 250);
    if (!fitted) return;
    const { ctx, width, height } = fitted;
    ctx.clearRect(0, 0, width, height);
    salesTrendRegions.current = [];
    if (!performance.length) return drawEmpty(ctx, width, height, 'No sales trend data for this month.');

    const left = 62, right = 18, top = 20, bottom = 38;
    const maxValue = Math.max(1, ...performance.map(point => point.income));
    const { plotW, plotH, axisMax } = drawAxes(ctx, width, height, left, right, top, bottom, maxValue);
    const x = index => left + (performance.length <= 1 ? plotW / 2 : index * plotW / (performance.length - 1));
    const y = value => top + plotH - (Math.max(0, value) / axisMax) * plotH;

    const gradient = ctx.createLinearGradient(0, top, 0, top + plotH);
    gradient.addColorStop(0, 'rgba(8,120,91,.16)');
    gradient.addColorStop(1, 'rgba(8,120,91,0)');
    ctx.beginPath();
    performance.forEach((point, index) => index ? ctx.lineTo(x(index), y(point.income)) : ctx.moveTo(x(index), y(point.income)));
    ctx.lineTo(x(performance.length - 1), top + plotH);
    ctx.lineTo(x(0), top + plotH);
    ctx.closePath();
    ctx.fillStyle = gradient;
    ctx.fill();

    ctx.beginPath();
    performance.forEach((point, index) => index ? ctx.lineTo(x(index), y(point.income)) : ctx.moveTo(x(index), y(point.income)));
    ctx.strokeStyle = '#08785b';
    ctx.lineWidth = 2.4;
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    ctx.stroke();

    const hitW = performance.length <= 1 ? plotW : plotW / Math.max(1, performance.length - 1);
    performance.forEach((point, index) => {
      const xx = x(index), yy = y(point.income);
      salesTrendRegions.current.push({
        data: point,
        anchorX: xx,
        anchorY: yy,
        hit: (xPos, yPos) => xPos >= xx - hitW / 2 && xPos <= xx + hitW / 2 && yPos >= top && yPos <= top + plotH
      });
    });

    const labelStep = Math.max(1, Math.ceil(performance.length / 6));
    ctx.fillStyle = '#71817a';
    ctx.textAlign = 'center';
    ctx.font = `500 10px ${uiFont}`;
    performance.forEach((point, index) => {
      if (index % labelStep === 0 || index === performance.length - 1) ctx.fillText(formatDate(point.date).split(' ')[0], x(index), height - 13);
    });
    ctx.textAlign = 'start';
  };

  const topProductsSource = document.querySelector('[data-top-products-source]');
  const topProducts = topProductsSource ? [...topProductsSource.children].map(node => ({
    label: node.dataset.label || 'Product',
    quantity: Number(node.dataset.quantity || 0)
  })).slice(0, 6) : [];

  const drawTopProducts = () => {
    const canvas = document.getElementById('topProductsChart');
    const chartHeight = Math.max(210, Math.min(300, topProducts.length * 43 + 48));
    const fitted = fitCanvas(canvas, chartHeight);
    if (!fitted) return;
    const { ctx, width, height } = fitted;
    ctx.clearRect(0, 0, width, height);
    productRegions.current = [];
    if (!topProducts.length) return drawEmpty(ctx, width, height, 'No product sales data for this period.');

    const left = Math.min(160, Math.max(112, width * .24));
    const right = 54, top = 26, bottom = 14;
    const plotW = Math.max(100, width - left - right);
    const plotH = height - top - bottom;
    const maxValue = Math.max(1, ...topProducts.map(item => item.quantity));
    const rowH = plotH / topProducts.length;

    ctx.strokeStyle = '#edf2ef';
    ctx.lineWidth = 1;
    for (let tick = 0; tick <= 4; tick += 1) {
      const xx = left + plotW * tick / 4;
      ctx.beginPath();
      ctx.moveTo(xx, top - 8);
      ctx.lineTo(xx, height - bottom);
      ctx.stroke();
    }

    topProducts.forEach((item, index) => {
      const rowTop = top + index * rowH;
      const centerY = rowTop + rowH / 2;
      const barH = Math.min(16, Math.max(11, rowH * .38));
      const proportional = plotW * (item.quantity / maxValue);
      const barW = item.quantity > 0 ? Math.max(4, proportional) : 0;

      if (barW > 0) {
        roundRect(ctx, left, centerY - barH / 2, barW, barH, 4);
        ctx.fillStyle = index === 0 ? '#08785b' : '#4d9a80';
        ctx.fill();
      }

      const label = item.label.length > 23 ? `${item.label.slice(0, 21)}…` : item.label;
      ctx.fillStyle = '#40594f';
      ctx.textAlign = 'right';
      ctx.font = `600 11px ${uiFont}`;
      ctx.fillText(label, left - 10, centerY + 4);

      ctx.fillStyle = '#29453a';
      ctx.textAlign = 'right';
      ctx.font = `700 11px ${uiFont}`;
      ctx.fillText(integer.format(item.quantity), width - 10, centerY + 4);

      productRegions.current.push({
        data: item,
        anchorX: Math.min(left + Math.max(barW, 12), width - right),
        anchorY: centerY,
        hit: (xPos, yPos) => xPos >= 0 && xPos <= width && yPos >= rowTop && yPos <= rowTop + rowH
      });
    });
    ctx.textAlign = 'start';
  };

  const paymentSource = document.querySelector('[data-payment-source]');
  const payment = paymentSource ? [...paymentSource.children].map(node => ({
    label: node.dataset.label || 'Payment',
    amount: Number(node.dataset.amount || 0),
    cash: node.dataset.cash === 'true'
  })).filter(item => item.amount > 0) : [];

  const drawPayment = () => {
    const canvas = document.getElementById('paymentMixChart');
    const fitted = fitCanvas(canvas, 220);
    if (!fitted) return;
    const { ctx, width, height } = fitted;
    const total = payment.reduce((sum, item) => sum + item.amount, 0);
    const cx = width / 2, cy = height / 2;
    const radius = Math.min(width, height) * .31;
    const inner = radius * .68;
    ctx.clearRect(0, 0, width, height);
    paymentRegions.current = [];

    const legend = document.querySelector('[data-payment-legend]');
    if (legend) legend.innerHTML = '';
    if (total <= 0) return drawEmpty(ctx, width, height, 'No collection data');

    let angle = -Math.PI / 2;
    payment.forEach((item, index) => {
      const slice = item.amount / total * Math.PI * 2;
      const start = angle;
      const end = angle + slice;
      ctx.beginPath();
      ctx.arc(cx, cy, radius, start, end);
      ctx.arc(cx, cy, inner, end, start, true);
      ctx.closePath();
      ctx.fillStyle = palette[index % palette.length];
      ctx.fill();

      const mid = start + slice / 2;
      paymentRegions.current.push({
        data: { ...item, percent: item.amount / total * 100 },
        anchorX: cx + Math.cos(mid) * radius * .78,
        anchorY: cy + Math.sin(mid) * radius * .78,
        hit: (xPos, yPos) => {
          const dx = xPos - cx, dy = yPos - cy;
          const distance = Math.hypot(dx, dy);
          if (distance < inner || distance > radius) return false;
          let pointAngle = Math.atan2(dy, dx);
          if (pointAngle < -Math.PI / 2) pointAngle += Math.PI * 2;
          let normalizedStart = start;
          let normalizedEnd = end;
          if (normalizedStart < -Math.PI / 2) normalizedStart += Math.PI * 2;
          if (normalizedEnd < normalizedStart) normalizedEnd += Math.PI * 2;
          if (pointAngle < normalizedStart) pointAngle += Math.PI * 2;
          return pointAngle >= normalizedStart && pointAngle <= normalizedEnd;
        }
      });
      angle = end;
    });

    ctx.fillStyle = '#203a31';
    ctx.font = `750 18px ${uiFont}`;
    ctx.textAlign = 'center';
    ctx.fillText(money.format(total), cx, cy - 2);
    ctx.fillStyle = '#7b8983';
    ctx.font = `500 10px ${uiFont}`;
    ctx.fillText('Total collected', cx, cy + 16);
    ctx.textAlign = 'start';

    if (legend) {
      payment.slice(0, 8).forEach((item, index) => {
        const share = item.amount / total * 100;
        const row = document.createElement('div');
        const left = document.createElement('span');
        const dot = document.createElement('i');
        dot.style.background = palette[index % palette.length];
        const label = document.createElement('span');
        label.textContent = item.label;
        left.append(dot, label);
        const amount = document.createElement('strong');
        amount.textContent = `${money.format(item.amount)} · ${percentFormat.format(share)}%`;
        row.append(left, amount);
        legend.appendChild(row);
      });
    }
  };

  const redraw = () => {
    drawSalesExpense();
    drawSalesTrend();
    drawTopProducts();
    drawPayment();
  };

  bindRegions(document.getElementById('salesExpenseChart'), salesExpenseRegions, region => ({
    title: formatDate(region.data.date),
    rows: [
      { label: 'Net sales', value: formatAmount(region.data.income), accent: '#08785b' },
      { label: 'Operating expense', value: formatAmount(region.data.expense), accent: '#c16b5f' }
    ]
  }));
  bindRegions(document.getElementById('salesTrendChart'), salesTrendRegions, region => ({
    title: formatDate(region.data.date),
    rows: [{ label: 'Net sales', value: formatAmount(region.data.income), accent: '#08785b' }]
  }));
  bindRegions(document.getElementById('topProductsChart'), productRegions, region => ({
    title: region.data.label,
    rows: [{ label: 'Sold quantity', value: integer.format(region.data.quantity), accent: '#08785b' }]
  }));
  bindRegions(document.getElementById('paymentMixChart'), paymentRegions, region => ({
    title: region.data.label,
    rows: [
      { label: 'Collected', value: formatAmount(region.data.amount), accent: '#08785b' },
      { label: 'Share', value: `${percentFormat.format(region.data.percent)}%` }
    ]
  }));

  let resizeTimer;
  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(redraw, 120);
  });
  redraw();

  document.querySelectorAll('[data-download-chart]').forEach(button => button.addEventListener('click', () => {
    const canvas = document.getElementById(button.dataset.downloadChart);
    if (!canvas) return;
    const link = document.createElement('a');
    link.href = canvas.toDataURL('image/png');
    link.download = `falcon-${button.dataset.downloadChart}.png`;
    link.click();
  }));

  const toggle = document.querySelector('[data-analytics-export-toggle]');
  const panel = document.querySelector('[data-analytics-export-panel]');
  if (toggle && panel) {
    toggle.addEventListener('click', event => {
      event.stopPropagation();
      panel.classList.toggle('is-open');
    });
    document.addEventListener('click', () => panel.classList.remove('is-open'));
    panel.addEventListener('click', event => event.stopPropagation());
  }
})();
