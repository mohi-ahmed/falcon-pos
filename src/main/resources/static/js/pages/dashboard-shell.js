(() => {
    'use strict';

    const shell = document.querySelector('[data-app-shell]');
    const sidebarToggle = document.querySelector('[data-sidebar-toggle]');
    const legacyOpenButtons = document.querySelectorAll('[data-sidebar-open]');
    const collapseButtons = document.querySelectorAll('[data-sidebar-collapse]');
    const closeButtons = document.querySelectorAll('[data-sidebar-close]');
    const profileTrigger = document.querySelector('[data-profile-trigger]');
    const profileDropdown = document.querySelector('[data-profile-dropdown]');
    const navGroups = [...document.querySelectorAll('[data-nav-group]')];
    const brandingHost = document.querySelector('[data-ui-favicon]');
    const faviconReference = brandingHost?.dataset.uiFavicon?.trim();
    const liveClock = document.querySelector('[data-live-clock]');
    const liveDate = liveClock?.querySelector('[data-live-date]');
    const liveTime = liveClock?.querySelector('[data-live-time]');

    if (liveClock && liveDate && liveTime) {
        const locale = document.documentElement.lang || navigator.language || 'en';
        const dateFormatter = new Intl.DateTimeFormat(locale, { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric' });
        const timeFormatter = new Intl.DateTimeFormat(locale, { hour: 'numeric', minute: '2-digit', second: '2-digit' });

        const renderClock = () => {
            const now = new Date();
            liveDate.textContent = dateFormatter.format(now);
            liveTime.textContent = timeFormatter.format(now);
            liveClock.dataset.timestamp = now.toISOString();
        };

        renderClock();
        window.setInterval(renderClock, 1000);
    }

    if (faviconReference) {
        let favicon = document.querySelector('link[rel="icon"]');
        if (!favicon) {
            favicon = document.createElement('link');
            favicon.rel = 'icon';
            document.head.appendChild(favicon);
        }
        favicon.href = faviconReference;
    }

    const MOBILE_MAX = 820;
    const SIDEBAR_STORAGE_KEY = 'falcon.sidebar.collapsed.v2';
    const isMobileViewport = () => window.innerWidth <= MOBILE_MAX;

    const updateToggleState = () => {
        if (!shell) return;
        if (isMobileViewport()) {
            const open = shell.classList.contains('is-sidebar-open');
            if (sidebarToggle) {
                sidebarToggle.setAttribute('aria-expanded', String(open));
                sidebarToggle.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
                sidebarToggle.title = open ? 'Close navigation' : 'Open navigation';
            }
            legacyOpenButtons.forEach((button) => button.setAttribute('aria-expanded', String(open)));
            return;
        }

        const collapsed = shell.classList.contains('is-sidebar-collapsed');
        if (sidebarToggle) {
            sidebarToggle.setAttribute('aria-expanded', String(!collapsed));
            sidebarToggle.setAttribute('aria-label', collapsed ? 'Expand navigation' : 'Collapse navigation');
            sidebarToggle.title = collapsed ? 'Expand navigation' : 'Collapse navigation';
            sidebarToggle.classList.toggle('is-collapsed', collapsed);
        }
        collapseButtons.forEach((button) => {
            button.setAttribute('aria-expanded', String(!collapsed));
            button.setAttribute('aria-label', collapsed ? 'Expand navigation' : 'Collapse navigation');
            button.title = collapsed ? 'Expand navigation' : 'Collapse navigation';
        });
    };

    const setMobileSidebar = (open) => {
        if (!shell) return;
        const shouldOpen = Boolean(open) && isMobileViewport();
        shell.classList.toggle('is-sidebar-open', shouldOpen);
        document.body.style.overflow = shouldOpen ? 'hidden' : '';
        updateToggleState();
    };

    const readStoredCollapsePreference = () => {
        try {
            const value = window.localStorage.getItem(SIDEBAR_STORAGE_KEY);
            return value === null ? null : value === 'true';
        } catch (_error) {
            return null;
        }
    };

    const storeCollapsePreference = (collapsed) => {
        try {
            window.localStorage.setItem(SIDEBAR_STORAGE_KEY, String(collapsed));
        } catch (_error) {
            // Layout remains functional when browser storage is unavailable.
        }
    };

    const syncSidebarTooltips = (collapsed) => {
        document.querySelectorAll('.navigation-link, .active-branch-card').forEach((item) => {
            if (!item.dataset.sidebarTooltip) {
                const label = item.textContent.replace(/\s+/g, ' ').trim();
                if (label) item.dataset.sidebarTooltip = label;
            }
            if (collapsed && item.dataset.sidebarTooltip) item.title = item.dataset.sidebarTooltip;
            else if (item.title === item.dataset.sidebarTooltip) item.removeAttribute('title');
        });
    };

    const setSidebarCollapsed = (collapsed, persist = false) => {
        if (!shell) return;
        const shouldCollapse = !isMobileViewport() && Boolean(collapsed);
        shell.classList.toggle('is-sidebar-collapsed', shouldCollapse);
        syncSidebarTooltips(shouldCollapse);
        if (persist) storeCollapsePreference(shouldCollapse);
        updateToggleState();
    };

    const initializeSidebarLayout = () => {
        if (!shell) return;
        if (isMobileViewport()) {
            shell.classList.remove('is-sidebar-collapsed');
            setMobileSidebar(false);
            return;
        }

        shell.classList.remove('is-sidebar-open');
        document.body.style.overflow = '';
        const stored = readStoredCollapsePreference();
        // Desktop opens fully by default. The user's explicit choice is then remembered.
        setSidebarCollapsed(stored === true, false);
    };

    sidebarToggle?.addEventListener('click', () => {
        if (!shell) return;
        if (isMobileViewport()) {
            setMobileSidebar(!shell.classList.contains('is-sidebar-open'));
            return;
        }
        setSidebarCollapsed(!shell.classList.contains('is-sidebar-collapsed'), true);
    });

    legacyOpenButtons.forEach((button) => button.addEventListener('click', () => {
        if (isMobileViewport()) setMobileSidebar(true);
        else setSidebarCollapsed(!shell?.classList.contains('is-sidebar-collapsed'), true);
    }));

    collapseButtons.forEach((button) => button.addEventListener('click', () => {
        if (!isMobileViewport()) setSidebarCollapsed(!shell?.classList.contains('is-sidebar-collapsed'), true);
    }));

    closeButtons.forEach((button) => button.addEventListener('click', () => setMobileSidebar(false)));
    initializeSidebarLayout();

    const setGroup = (group, open) => {
        const toggle = group.querySelector('[data-nav-toggle]');
        group.classList.toggle('is-open', open);
        toggle?.setAttribute('aria-expanded', String(open));
    };

    navGroups.forEach((group) => {
        const toggle = group.querySelector('[data-nav-toggle]');
        if (!toggle) return;
        setGroup(group, group.classList.contains('is-open'));
        toggle.addEventListener('click', () => {
            if (shell?.classList.contains('is-sidebar-collapsed')) {
                setSidebarCollapsed(false, true);
            }
            const willOpen = !group.classList.contains('is-open');
            navGroups.forEach((other) => {
                if (other !== group) setGroup(other, false);
            });
            setGroup(group, willOpen);
        });
    });

    const setProfileMenu = (open) => {
        if (!profileTrigger || !profileDropdown) return;
        profileTrigger.setAttribute('aria-expanded', String(open));
        profileDropdown.hidden = !open;
    };

    profileTrigger?.addEventListener('click', (event) => {
        event.stopPropagation();
        setProfileMenu(profileTrigger.getAttribute('aria-expanded') !== 'true');
    });

    document.addEventListener('click', (event) => {
        if (profileDropdown && !profileDropdown.hidden && !event.target.closest('.profile-menu')) setProfileMenu(false);
    });

    document.addEventListener('keydown', (event) => {
        if (event.key !== 'Escape') return;
        setMobileSidebar(false);
        setProfileMenu(false);
    });

    window.addEventListener('resize', initializeSidebarLayout, { passive: true });

    // Step 9: keep every documented list export on the same filter query as the visible page.
    const exportPaths = new Set([
        '/owner/branches', '/owner/products', '/owner/products/categories', '/owner/suppliers',
        '/owner/customers', '/owner/purchases', '/owner/purchases/returns', '/owner/sales',
        '/owner/sales/returns/list', '/owner/payments/history', '/owner/expenses',
        '/owner/settings/payment-methods', '/owner/settings/units', '/owner/settings/taxes',
        '/owner/settings/printers', '/owner/inventory/movements', '/owner/inventory/counts',
        '/owner/inventory/adjustments', '/owner/inventory/transfers', '/owner/inventory/wastage',
        '/owner/cash-management', '/owner/cash-management/shifts', '/owner/purchases/logs',
        '/owner/purchases/stock-import/history', '/owner/sales/log'
    ]);
    const currentPath = window.location.pathname.replace(/\/$/, '');
    if (exportPaths.has(currentPath)) {
        document.querySelectorAll('.is-disabled, [disabled]').forEach((control) => {
            const format = control.textContent.trim().toLowerCase();
            if (!['excel', 'xlsx', 'csv', 'pdf'].includes(format)) return;
            const link = document.createElement('a');
            link.className = control.className.replace(/\bis-disabled\b/g, '').trim();
            const params = new URLSearchParams(window.location.search);
            params.delete('page');
            params.delete('size');
            params.set('format', format === 'excel' ? 'xlsx' : format);
            link.href = `${currentPath}/export?${params}`;
            link.textContent = control.textContent.trim();
            link.setAttribute('aria-label', `${link.textContent} export`);
            control.replaceWith(link);
        });
    }

    if (!document.querySelector('link[href$="/css/print.css"]')) {
        const printStyles = document.createElement('link');
        printStyles.rel = 'stylesheet';
        printStyles.href = '/css/print.css';
        document.head.appendChild(printStyles);
    }
})();
