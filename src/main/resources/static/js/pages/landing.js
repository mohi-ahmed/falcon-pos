(() => {
    const body = document.body;
    const header = document.querySelector('[data-header]');
    const menu = document.querySelector('[data-menu]');
    const menuButton = document.querySelector('[data-menu-button]');
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    const closeMenu = (restoreFocus = false) => {
        if (!menu?.classList.contains('open')) return;
        menu.classList.remove('open');
        body.classList.remove('menu-open');
        menuButton.setAttribute('aria-expanded', 'false');
        menuButton.setAttribute('aria-label', 'Open navigation');
        if (restoreFocus) menuButton.focus();
    };

    const updateHeader = () => header?.classList.toggle('scrolled', window.scrollY > 8);
    updateHeader();
    window.addEventListener('scroll', updateHeader, { passive: true });

    menuButton?.addEventListener('click', () => {
        const open = !menu.classList.contains('open');
        if (!open) { closeMenu(); return; }
        menu.classList.add('open');
        body.classList.add('menu-open');
        menuButton.setAttribute('aria-expanded', 'true');
        menuButton.setAttribute('aria-label', 'Close navigation');
        menu.querySelector('a')?.focus();
    });
    menu?.querySelectorAll('a').forEach(link => link.addEventListener('click', () => closeMenu()));
    document.addEventListener('keydown', event => { if (event.key === 'Escape') closeMenu(true); });
    document.addEventListener('click', event => {
        if (!menu?.classList.contains('open') || header.contains(event.target)) return;
        closeMenu();
    });

    const revealTargets = document.querySelectorAll('[data-reveal], main > section:not(.hero)');
    if (!reducedMotion && 'IntersectionObserver' in window) {
        body.classList.add('reveal-ready');
        const revealObserver = new IntersectionObserver(entries => entries.forEach(entry => {
            if (!entry.isIntersecting) return;
            entry.target.classList.add('is-visible');
            revealObserver.unobserve(entry.target);
        }), { threshold: .08 });
        revealTargets.forEach(target => revealObserver.observe(target));
    } else {
        revealTargets.forEach(target => target.classList.add('is-visible'));
    }

    const navLinks = [...document.querySelectorAll('.primary-nav > a[href^="#"]')];
    if ('IntersectionObserver' in window) {
        const activeObserver = new IntersectionObserver(entries => entries.forEach(entry => {
            if (!entry.isIntersecting) return;
            navLinks.forEach(link => link.classList.toggle('active', link.hash === `#${entry.target.id}`));
        }), { rootMargin: '-35% 0px -55%', threshold: 0 });
        navLinks.map(link => document.querySelector(link.hash)).filter(Boolean).forEach(section => activeObserver.observe(section));
    }

    document.querySelectorAll('[data-count]').forEach(counter => {
        const target = Number(counter.dataset.count);
        if (reducedMotion) { counter.textContent = target.toLocaleString('en-US'); return; }
        const start = performance.now();
        const animate = time => {
            const progress = Math.min((time - start) / 1100, 1);
            counter.textContent = Math.round(target * (1 - Math.pow(1 - progress, 3))).toLocaleString('en-US');
            if (progress < 1) requestAnimationFrame(animate);
        };
        requestAnimationFrame(animate);
    });

    const orderLines = document.querySelector('[data-order-lines]');
    const totalOutput = document.querySelector('[data-order-total]');
    const feedback = document.querySelector('[data-demo-feedback]');
    const initialOrder = [{ name: 'Shampoo 400 ML', price: 380, quantity: 1 }, { name: 'Premium Rice 5 KG', price: 690, quantity: 1 }];
    let order = initialOrder.map(item => ({ ...item }));

    const calculateTotal = () => order.reduce((total, item) => total + item.price * item.quantity, 0);
    const renderOrder = () => {
        orderLines.replaceChildren(...order.map(item => {
            const line = document.createElement('div');
            line.className = 'order-line';
            line.dataset.orderProduct = item.name;
            line.innerHTML = `<strong></strong><b></b><small><span></span><button type="button">Remove</button></small>`;
            line.querySelector('strong').textContent = item.name;
            line.querySelector('b').textContent = `BDT ${(item.price * item.quantity).toLocaleString('en-US')}`;
            line.querySelector('small span').textContent = `${item.quantity} ${item.quantity === 1 ? 'item' : 'items'}`;
            const removeButton = line.querySelector('button');
            removeButton.dataset.removeItem = '';
            removeButton.setAttribute('aria-label', `Remove ${item.name}`);
            return line;
        }));
        totalOutput.textContent = `BDT ${calculateTotal().toLocaleString('en-US')}`;
    };

    document.querySelectorAll('[data-product]').forEach(product => product.addEventListener('click', () => {
        const name = product.dataset.product;
        const price = Number(product.dataset.price);
        const existing = order.find(item => item.name === name);
        if (existing) existing.quantity += 1;
        else order.push({ name, price, quantity: 1 });
        renderOrder();
        feedback.textContent = `${name} added to the sale`;
        orderLines.scrollTo({ top: orderLines.scrollHeight, behavior: reducedMotion ? 'auto' : 'smooth' });
    }));
    orderLines?.addEventListener('click', event => {
        const button = event.target.closest('[data-remove-item]');
        if (!button) return;
        const name = button.closest('[data-order-product]').dataset.orderProduct;
        order = order.filter(item => item.name !== name);
        renderOrder();
        feedback.textContent = `${name} removed`;
    });
    document.querySelector('[data-reset-demo]')?.addEventListener('click', () => {
        order = initialOrder.map(item => ({ ...item }));
        renderOrder();
        feedback.textContent = 'Demo sale reset';
    });
    document.querySelector('[data-payment-demo]')?.addEventListener('click', () => {
        feedback.textContent = order.length ? 'Payment flow opens from the live POS workspace.' : 'Add an item before payment.';
    });

    const featureContent = {
        pos: { label: 'Point of sale', title: 'Fast, focused counter sales', copy: 'Keep barcode and counter sales moving with a cashier workspace built for speed and accuracy.', points: ['Purpose-built cashier workspace','Clear product, quantity and totals','Keyboard and touch friendly'], metricLabel: 'Current sale', metric: 'BDT 1,290', rows: [['Premium Rice 5 KG','BDT 690'],['Shampoo 400 ML','BDT 380'],['LED Bulb 12W','BDT 220']], action: 'Proceed to payment' },
        purchasing: { label: 'Purchasing', title: 'Supplier purchases under control', copy: 'Record purchases, supplier invoices, due payments and returns while stock updates remain traceable.', points: ['Supplier-linked purchases','Purchase returns and dues','Automatic stock receiving'], metricLabel: 'Outstanding supplier due', metric: 'BDT 12,400', rows: [['Rahman Traders','Paid'],['City Wholesale','Due'],['Metro Supply','Partial']], action: 'View purchases' },
        inventory: { label: 'Inventory control', title: 'Turn every sale into stock clarity', copy: 'Connect products, purchases and stock movements so managers can understand availability and act before shelves run empty.', points: ['Sale-linked stock deduction','Low-stock visibility','Supplier and receiving foundation'], metricLabel: 'Products to review', metric: '06', rows: [['Premium Rice 5 KG','Low'],['Shampoo 400 ML','Reorder'],['LED Bulb 12W','Healthy']], action: 'Review inventory' },
        management: { label: 'Business management', title: 'One view across every branch', copy: 'Give owners and managers dependable performance visibility while each store team stays focused on sales.', points: ['Role-based access control','Branch-level comparison','Auditable operational activity'], metricLabel: 'Today’s sales', metric: 'BDT 48,620', rows: [['Gulshan Branch','+12.4%'],['Dhanmondi Branch','+8.1%'],['Uttara Branch','+5.7%']], action: 'View performance' }
    };
    const tabs = [...document.querySelectorAll('[data-feature-tab]')];
    const panel = document.querySelector('#feature-panel');
    const visual = panel?.querySelector('.showcase-visual');
    const activateTab = tab => {
        const content = featureContent[tab.dataset.featureTab];
        tabs.forEach(item => { const selected = item === tab; item.classList.toggle('active', selected); item.setAttribute('aria-selected', String(selected)); item.tabIndex = selected ? 0 : -1; });
        panel.setAttribute('aria-labelledby', tab.id);
        visual.classList.add('switching');
        window.setTimeout(() => {
            panel.querySelector('[data-feature-label]').textContent = content.label;
            panel.querySelector('[data-feature-title]').textContent = content.title;
            panel.querySelector('[data-feature-copy]').textContent = content.copy;
            panel.querySelector('[data-feature-points]').replaceChildren(...content.points.map(point => { const item = document.createElement('li'); item.textContent = point; return item; }));
            panel.querySelector('.visual-metric small').textContent = content.metricLabel;
            panel.querySelector('.visual-metric strong').textContent = content.metric;
            panel.querySelector('.visual-rows').replaceChildren(...content.rows.map(row => { const item = document.createElement('span'); item.innerHTML = '<i></i><span></span><b></b>'; item.querySelector('span').textContent = row[0]; item.querySelector('b').textContent = row[1]; return item; }));
            visual.querySelector('button').textContent = content.action;
            visual.classList.remove('switching');
        }, reducedMotion ? 0 : 160);
    };
    tabs.forEach((tab, index) => {
        tab.addEventListener('click', () => activateTab(tab));
        tab.addEventListener('keydown', event => {
            let targetIndex;
            if (event.key === 'ArrowRight') targetIndex = (index + 1) % tabs.length;
            else if (event.key === 'ArrowLeft') targetIndex = (index - 1 + tabs.length) % tabs.length;
            else if (event.key === 'Home') targetIndex = 0;
            else if (event.key === 'End') targetIndex = tabs.length - 1;
            else return;
            event.preventDefault();
            tabs[targetIndex].focus();
            activateTab(tabs[targetIndex]);
        });
    });
})();
