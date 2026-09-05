(() => {
    const normalize = (value) => (value ?? "").toString().trim();
    const parseIdSet = (value) => new Set((normalize(value).match(/\d+/g) || []).map(String));
    const applyBranchAssignments = (form, value) => {
        if (!form) return;
        const selected = parseIdSet(value);
        form.querySelectorAll("[data-edit-branch-field]").forEach((input) => {
            input.checked = selected.has(normalize(input.value));
        });
    };

    document.querySelectorAll("[data-uppercase]").forEach((input) => {
        input.addEventListener("input", () => {
            const start = input.selectionStart;
            const end = input.selectionEnd;
            input.value = input.value.toUpperCase();
            if (start !== null && end !== null) input.setSelectionRange(start, end);
        });
    });

    // Branch Settings: one focused section at a time.
    const sectionButtons = [...document.querySelectorAll("[data-settings-section-target]")];
    const sections = [...document.querySelectorAll("[data-settings-section]")];
    const connectedSections = new Set(["general", "pos", "expiry", "cash", "communication", "branding", "jobs"]);
    const saveBar = document.querySelector("[data-live-settings-actions]");

    const setSection = (name, updateHash = true) => {
        if (!sections.length) return;
        const valid = sections.some((section) => section.dataset.settingsSection === name);
        const selected = valid ? name : "general";
        sections.forEach((section) => {
            const active = section.dataset.settingsSection === selected;
            section.hidden = !active;
            section.classList.toggle("is-active", active);
        });
        sectionButtons.forEach((button) => {
            const active = button.dataset.settingsSectionTarget === selected;
            button.classList.toggle("is-active", active);
            button.setAttribute("aria-selected", active ? "true" : "false");
        });
        if (saveBar) saveBar.hidden = !connectedSections.has(selected);
        if (updateHash && history.replaceState) history.replaceState(null, "", `#${selected}`);
    };

    sectionButtons.forEach((button) => button.addEventListener("click", () => setSection(button.dataset.settingsSectionTarget)));
    if (sections.length) {
        const errorPanel = sections.find((section) => section.querySelector(".has-error, .field-error:not(:empty)"));
        setSection(errorPanel?.dataset.settingsSection || location.hash.replace("#", "") || "general", false);
    }

    // Destructive confirmation.
    document.querySelectorAll("form[data-confirm]").forEach((form) => {
        form.addEventListener("submit", (event) => {
            if (!window.confirm(form.dataset.confirm || "Continue with this action?")) event.preventDefault();
        });
    });

    // Professional right-side drawers for create/edit forms.
    let previouslyFocused = null;
    const openDrawer = (drawer) => {
        if (!drawer) return;
        previouslyFocused = document.activeElement;
        drawer.hidden = false;
        document.body.classList.add("is-drawer-open");
        requestAnimationFrame(() => drawer.querySelector("input:not([type='hidden']):not(:disabled), select:not(:disabled), textarea:not(:disabled), button:not([data-drawer-close]):not(:disabled)")?.focus());
    };
    const closeDrawer = (drawer) => {
        if (!drawer) return;
        drawer.hidden = true;
        document.body.classList.remove("is-drawer-open");
        if (previouslyFocused instanceof HTMLElement) previouslyFocused.focus();
        previouslyFocused = null;
    };

    document.querySelectorAll("[data-drawer-open]").forEach((button) => {
        button.addEventListener("click", () => openDrawer(document.querySelector(`[data-drawer="${button.dataset.drawerOpen}"]`)));
    });
    document.querySelectorAll("[data-drawer-close]").forEach((button) => {
        button.addEventListener("click", () => closeDrawer(button.closest("[data-drawer]")));
    });
    document.addEventListener("keydown", (event) => {
        if (event.key !== "Escape") return;
        const open = document.querySelector("[data-drawer]:not([hidden])");
        if (open) closeDrawer(open);
    });
    document.querySelectorAll("[data-drawer-auto-open]").forEach((marker) => openDrawer(marker.closest("[data-drawer]")));

    const paymentDrawer = document.querySelector('[data-drawer="payment-edit"]');
    const paymentForm = paymentDrawer?.querySelector("[data-payment-edit-form]");
    document.querySelectorAll("[data-edit-payment]").forEach((button) => {
        button.addEventListener("click", () => {
            if (!paymentForm) return;
            const data = button.dataset;
            paymentForm.action = `/owner/settings/payment-methods/${data.id}`;
            paymentForm.querySelector('[data-edit-field="name"]').value = normalize(data.name);
            paymentForm.querySelector('[data-edit-field="code"]').value = normalize(data.code);
            paymentForm.querySelector('[data-edit-field="description"]').value = normalize(data.description);
            paymentForm.querySelector('[data-edit-field="displayOrder"]').value = normalize(data.displayOrder) || "0";
            paymentForm.querySelector('[data-edit-field="cash"]').checked = data.cash === "true";
            paymentForm.querySelector('[data-edit-field="referenceRequired"]').checked = data.referenceRequired === "true";
            paymentForm.querySelector('[data-edit-field="reconciliationChannel"]').value = normalize(data.reconciliationChannel);
            paymentForm.querySelector('[data-edit-field="reconciliationAccount"]').value = normalize(data.reconciliationAccount);
            paymentForm.querySelector('[data-edit-field="status"]').value = normalize(data.status) || "ACTIVE";
            applyBranchAssignments(paymentForm, data.branchIds);
            openDrawer(paymentDrawer);
        });
    });

    const unitDrawer = document.querySelector('[data-drawer="unit-edit"]');
    const unitForm = unitDrawer?.querySelector("[data-unit-edit-form]");
    document.querySelectorAll("[data-edit-unit]").forEach((button) => {
        button.addEventListener("click", () => {
            if (!unitForm) return;
            const data = button.dataset;
            unitForm.action = `/owner/settings/units/${data.id}`;
            unitForm.querySelector('[data-edit-field="name"]').value = normalize(data.name);
            unitForm.querySelector('[data-edit-field="code"]').value = normalize(data.code);
            unitForm.querySelector('[data-edit-field="description"]').value = normalize(data.description);
            unitForm.querySelector('[data-edit-field="displayOrder"]').value = normalize(data.displayOrder) || "0";
            unitForm.querySelector('[data-edit-field="status"]').value = normalize(data.status) || "ACTIVE";
            applyBranchAssignments(unitForm, data.branchIds);
            openDrawer(unitDrawer);
        });
    });

    const taxDrawer = document.querySelector('[data-drawer="tax-edit"]');
    const taxForm = taxDrawer?.querySelector("[data-tax-edit-form]");
    document.querySelectorAll("[data-edit-tax]").forEach((button) => {
        button.addEventListener("click", () => {
            if (!taxForm) return;
            const data = button.dataset;
            taxForm.action = `/owner/settings/taxes/${data.id}`;
            taxForm.querySelector('[data-edit-field="name"]').value = normalize(data.name);
            taxForm.querySelector('[data-edit-field="code"]').value = normalize(data.code);
            taxForm.querySelector('[data-edit-field="rate"]').value = normalize(data.rate);
            taxForm.querySelector('[data-edit-field="displayOrder"]').value = normalize(data.displayOrder) || "0";
            taxForm.querySelector('[data-edit-field="status"]').value = normalize(data.status) || "ACTIVE";
            openDrawer(taxDrawer);
        });
    });

    const printerDrawer = document.querySelector('[data-drawer="printer-edit"]');
    const printerForm = printerDrawer?.querySelector("[data-printer-edit-form]");
    document.querySelectorAll("[data-edit-printer]").forEach((button) => {
        button.addEventListener("click", () => {
            if (!printerForm) return;
            const data = button.dataset;
            printerForm.action = `/owner/settings/printers/${data.id}`;
            printerForm.querySelector('[data-edit-field="title"]').value = normalize(data.title);
            printerForm.querySelector('[data-edit-field="printerType"]').value = normalize(data.printerType);
            printerForm.querySelector('[data-edit-field="connectionType"]').value = normalize(data.connectionType);
            printerForm.querySelector('[data-edit-field="charactersPerLine"]').value = normalize(data.charactersPerLine);
            printerForm.querySelector('[data-edit-field="printerPath"]').value = normalize(data.printerPath);
            printerForm.querySelector('[data-edit-field="ipAddress"]').value = normalize(data.ipAddress);
            printerForm.querySelector('[data-edit-field="port"]').value = normalize(data.port);
            printerForm.querySelector('[data-edit-field="displayOrder"]').value = normalize(data.displayOrder) || "0";
            printerForm.querySelector('[data-edit-field="status"]').value = normalize(data.status) || "ACTIVE";
            applyBranchAssignments(printerForm, data.branchIds);
            openDrawer(printerDrawer);
        });
    });

    document.querySelectorAll("form").forEach((form) => {
        form.addEventListener("submit", () => {
            const submitter = form.querySelector('button[type="submit"]');
            if (!submitter || submitter.disabled) return;
            submitter.disabled = true;
            submitter.setAttribute("aria-busy", "true");
        });
    });

    // Search, sort, page and browser print. Download buttons stay disabled until real backend routes exist.

    document.querySelectorAll("[data-managed-table]").forEach((table) => {
        const toolbar = document.querySelector(`[data-table-toolbar="${table.id}"]`);
        if (!toolbar || !table.tBodies.length) return;
        const tableCard = table.closest(".settings-table-card");
        const allRows = [...table.tBodies[0].rows].filter((row) => !row.classList.contains("empty-table-row"));
        const searchInput = toolbar.querySelector("[data-table-search]");
        const pageSizeSelect = toolbar.querySelector("[data-page-size]");
        const pagination = tableCard?.querySelector("[data-pagination]");
        const summary = tableCard?.querySelector("[data-result-summary]");
        let currentPage = 1;
        let sortIndex = null;
        let sortAscending = true;

        const filteredRows = () => {
            const query = normalize(searchInput?.value).toLowerCase();
            let rows = allRows.filter((row) => !query || normalize(row.dataset.searchText || row.textContent).toLowerCase().includes(query));
            if (sortIndex !== null) {
                rows = [...rows].sort((a, b) => {
                    const left = normalize(a.cells[sortIndex]?.innerText).toLowerCase();
                    const right = normalize(b.cells[sortIndex]?.innerText).toLowerCase();
                    const leftNumber = Number(left.replace(/[^0-9.-]/g, ""));
                    const rightNumber = Number(right.replace(/[^0-9.-]/g, ""));
                    const numeric = left !== "" && right !== "" && Number.isFinite(leftNumber) && Number.isFinite(rightNumber);
                    const comparison = numeric ? leftNumber - rightNumber : left.localeCompare(right, undefined, {numeric: true});
                    return sortAscending ? comparison : -comparison;
                });
            }
            return rows;
        };

        const render = () => {
            const rows = filteredRows();
            const body = table.tBodies[0];
            rows.forEach((row) => body.appendChild(row));
            const emptyRow = table.querySelector(".empty-table-row");
            if (emptyRow) body.appendChild(emptyRow);
            const pageSize = Number(pageSizeSelect?.value || 10);
            const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
            currentPage = Math.min(currentPage, totalPages);
            const start = (currentPage - 1) * pageSize;
            const visible = new Set(rows.slice(start, start + pageSize));
            allRows.forEach((row) => { row.hidden = !visible.has(row); });
            if (emptyRow) emptyRow.hidden = rows.length !== 0;

            if (summary) summary.textContent = rows.length ? `Showing ${start + 1}–${Math.min(start + pageSize, rows.length)} of ${rows.length}` : "No matching records";
            if (pagination) {
                pagination.replaceChildren();
                const makeButton = (label, page, active = false, disabled = false) => {
                    const button = document.createElement("button");
                    button.type = "button";
                    button.textContent = label;
                    button.disabled = disabled;
                    button.classList.toggle("is-active", active);
                    button.addEventListener("click", () => { currentPage = page; render(); });
                    return button;
                };
                pagination.append(makeButton("‹", Math.max(1, currentPage - 1), false, currentPage === 1));
                const first = Math.max(1, Math.min(currentPage - 2, totalPages - 4));
                const last = Math.min(totalPages, first + 4);
                for (let page = first; page <= last; page++) pagination.append(makeButton(String(page), page, page === currentPage));
                pagination.append(makeButton("›", Math.min(totalPages, currentPage + 1), false, currentPage === totalPages));
            }
        };

        searchInput?.addEventListener("input", () => { currentPage = 1; render(); });
        pageSizeSelect?.addEventListener("change", () => { currentPage = 1; render(); });
        table.querySelectorAll("th[data-sort-index]").forEach((header) => {
            header.addEventListener("click", () => {
                const nextIndex = Number(header.dataset.sortIndex);
                if (sortIndex === nextIndex) sortAscending = !sortAscending;
                else { sortIndex = nextIndex; sortAscending = true; }
                currentPage = 1;
                render();
            });
        });

        const printFiltered = () => {
            const visible = new Set(filteredRows());
            allRows.forEach((row) => { row.hidden = !visible.has(row); });
            const restore = () => { window.removeEventListener("afterprint", restore); render(); };
            window.addEventListener("afterprint", restore);
            window.print();
            setTimeout(restore, 1000);
        };

        toolbar.querySelector("[data-table-print]")?.addEventListener("click", printFiltered);
        render();
    });
})();
