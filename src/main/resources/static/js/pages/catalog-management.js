(() => {
    document.querySelectorAll("[data-auto-dismiss-toast]").forEach((toast) => {
        const closeButton = toast.querySelector("[data-toast-close]");
        const reducedMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches;
        let dismissTimer;

        const dismiss = () => {
            window.clearTimeout(dismissTimer);
            if (toast.classList.contains("is-leaving")) return;
            toast.classList.add("is-leaving");
            window.setTimeout(() => toast.remove(), reducedMotion ? 0 : 180);
        };
        const startTimer = () => {
            window.clearTimeout(dismissTimer);
            toast.classList.remove("is-paused");
            dismissTimer = window.setTimeout(dismiss, 4500);
        };
        const pauseTimer = () => {
            window.clearTimeout(dismissTimer);
            toast.classList.add("is-paused");
        };

        closeButton?.addEventListener("click", dismiss);
        toast.addEventListener("mouseenter", pauseTimer);
        toast.addEventListener("mouseleave", startTimer);
        toast.addEventListener("focusin", pauseTimer);
        toast.addEventListener("focusout", startTimer);
        startTimer();
    });
    const tabRoot = document.querySelector("[data-form-tabs]");
    if (tabRoot) {
        const tabs = [...tabRoot.querySelectorAll("[data-form-tab]")];
        const panels = [...document.querySelectorAll("[data-form-panel]")];
        const activate = (name) => {
            tabs.forEach((tab) => {
                const active = tab.dataset.formTab === name;
                tab.classList.toggle("is-active", active);
                tab.setAttribute("aria-selected", String(active));
            });
            panels.forEach((panel) => panel.classList.toggle("is-active", panel.dataset.formPanel === name));
        };
        tabs.forEach((tab) => tab.addEventListener("click", () => activate(tab.dataset.formTab)));
    }

    const expiryToggle = document.querySelector("[data-expiry-toggle]");
    const expiryFields = [...document.querySelectorAll("[data-expiry-field]")];
    const expiryTypeField = document.querySelector('[name="expiryType"]');
    const expiryAlertField = document.querySelector('[name="expiryAlertBeforeDays"]');
    const blockSaleField = document.querySelector('[name="blockSaleAfterExpiry"]');
    const syncExpiry = () => {
        if (!expiryToggle) return;
        const enabled = expiryToggle.checked;
        expiryFields.forEach((field) => field.disabled = !enabled);
        if (expiryTypeField) {
            expiryTypeField.required = enabled;
            expiryTypeField.setCustomValidity("");
        }
        if (expiryAlertField) {
            expiryAlertField.required = enabled;
            expiryAlertField.setCustomValidity("");
        }
        if (blockSaleField) blockSaleField.setCustomValidity("");
    };
    expiryToggle?.addEventListener("change", syncExpiry);
    expiryTypeField?.addEventListener("change", () => expiryTypeField.setCustomValidity(""));
    expiryAlertField?.addEventListener("input", () => expiryAlertField.setCustomValidity(""));
    blockSaleField?.addEventListener("change", () => blockSaleField.setCustomValidity(""));
    expiryToggle?.closest("form")?.addEventListener("submit", (event) => {
        if (!expiryToggle.checked) return;
        let invalidField = null;
        if (expiryTypeField && !expiryTypeField.value) {
            expiryTypeField.setCustomValidity("Select Expiry Type when Track Expiry is enabled.");
            invalidField = expiryTypeField;
        } else if (expiryAlertField && (!expiryAlertField.value || Number(expiryAlertField.value) <= 0)) {
            expiryAlertField.setCustomValidity("Enter a positive Expiry Alert Before Days value when Track Expiry is enabled.");
            invalidField = expiryAlertField;
        } else if (blockSaleField && !blockSaleField.checked) {
            blockSaleField.setCustomValidity("Block Sale After Expiry must remain enabled for expiry-tracked products.");
            invalidField = blockSaleField;
        }
        if (invalidField) {
            event.preventDefault();
            invalidField.reportValidity();
            invalidField.focus();
        }
    });
    syncExpiry();

    const branchUnitSelects = [...document.querySelectorAll("[data-branch-unit-select]")];
    const assignedBranchInputs = [...document.querySelectorAll('input[name="branchIds"]')];
    const syncBranchCompatibleUnits = () => {
        if (!branchUnitSelects.length || !assignedBranchInputs.length) return;
        const selectedBranchIds = assignedBranchInputs.filter((input) => input.checked).map((input) => input.value);
        branchUnitSelects.forEach((select) => {
            [...select.options].forEach((option) => {
                if (!option.value) return;
                const activeBranchIds = new Set((option.dataset.branchIds || "").split(",").filter(Boolean));
                const compatible = selectedBranchIds.length > 0
                    && selectedBranchIds.every((branchId) => activeBranchIds.has(branchId));
                option.disabled = !compatible;
                option.hidden = !compatible;
            });
            if (select.selectedOptions[0]?.disabled) select.value = "";
        });
    };
    assignedBranchInputs.forEach((input) => input.addEventListener("change", syncBranchCompatibleUnits));
    syncBranchCompatibleUnits();

    const imageInput = document.querySelector("[data-image-reference]");
    const imageTarget = document.querySelector("[data-image-preview]");
    const syncImage = () => {
        if (!imageInput || !imageTarget) return;
        const url = imageInput.value.trim();
        if (!url) {
            imageTarget.textContent = "P";
            return;
        }
        const image = document.createElement("img");
        image.src = url;
        image.alt = "Product preview";
        imageTarget.replaceChildren(image);
    };
    imageInput?.addEventListener("input", syncImage);
    syncImage();

    document.querySelectorAll("[data-client-table]").forEach((root) => {
        const table = root.querySelector("[data-catalog-table]");
        if (!table) return;
        const tbody = table.tBodies[0];
        const rows = [...tbody.querySelectorAll("tr[data-search-row]")];
        const emptyRow = tbody.querySelector("[data-empty-row]");
        const noMatchRow = tbody.querySelector("[data-no-match-row]");
        const search = root.querySelector("[data-table-search]");
        const status = root.querySelector("[data-table-status]");
        const sort = root.querySelector("[data-table-sort]");
        const size = root.querySelector("[data-table-size]");
        const summary = root.querySelector("[data-table-summary]");
        const pagination = root.querySelector("[data-table-pagination]");
        const selectAll = root.querySelector("[data-select-all]");
        let currentPage = 1;

        const compareRows = (a, b) => {
            const mode = sort?.value || "name-asc";
            if (mode.startsWith("price-")) {
                const av = Number(a.dataset.sortPrice || 0);
                const bv = Number(b.dataset.sortPrice || 0);
                return mode.endsWith("desc") ? bv - av : av - bv;
            }
            const key = mode.startsWith("reference-") ? "sortReference" : "sortName";
            const av = a.dataset[key] || "";
            const bv = b.dataset[key] || "";
            const result = av.localeCompare(bv, undefined, { numeric: true, sensitivity: "base" });
            return mode.endsWith("desc") ? -result : result;
        };

        const renderPagination = (pageCount) => {
            if (!pagination) return;
            pagination.replaceChildren();
            if (pageCount <= 1) return;
            const makeButton = (label, page, active = false, disabled = false) => {
                const button = document.createElement("button");
                button.type = "button";
                button.className = `page-link${active ? " is-active" : ""}`;
                button.textContent = label;
                button.disabled = disabled;
                if (!disabled) button.addEventListener("click", () => {
                    currentPage = page;
                    refresh();
                });
                return button;
            };
            pagination.append(makeButton("‹", Math.max(1, currentPage - 1), false, currentPage === 1));
            const start = Math.max(1, currentPage - 2);
            const end = Math.min(pageCount, start + 4);
            for (let page = Math.max(1, end - 4); page <= end; page += 1) {
                pagination.append(makeButton(String(page), page, page === currentPage));
            }
            pagination.append(makeButton("›", Math.min(pageCount, currentPage + 1), false, currentPage === pageCount));
        };

        const refresh = () => {
            const q = (search?.value || "").trim().toLowerCase();
            const state = status?.value || "all";
            const pageSize = Math.max(1, Number(size?.value || 10));
            const matched = rows.filter((row) => {
                const matchesQ = !q || (row.dataset.searchText || "").includes(q);
                const matchesState = state === "all" || row.dataset.status === state;
                row.dataset.filteredOut = String(!(matchesQ && matchesState));
                return matchesQ && matchesState;
            }).sort(compareRows);

            rows.forEach((row) => tbody.append(row));
            matched.forEach((row) => tbody.append(row));

            const pageCount = Math.max(1, Math.ceil(matched.length / pageSize));
            currentPage = Math.min(currentPage, pageCount);
            const firstIndex = (currentPage - 1) * pageSize;
            const lastIndex = firstIndex + pageSize;
            const matchedSet = new Set(matched);
            rows.forEach((row) => {
                const matchIndex = matched.indexOf(row);
                row.hidden = !matchedSet.has(row) || matchIndex < firstIndex || matchIndex >= lastIndex;
            });
            if (emptyRow) emptyRow.hidden = rows.length > 0;
            if (noMatchRow) noMatchRow.hidden = rows.length === 0 || matched.length > 0;
            if (summary) {
                if (!matched.length) summary.textContent = "0 product records";
                else summary.textContent = `Showing ${firstIndex + 1}–${Math.min(lastIndex, matched.length)} of ${matched.length} product record(s)`;
            }
            if (selectAll) selectAll.checked = false;
            renderPagination(matched.length ? pageCount : 0);
        };

        search?.addEventListener("input", () => { currentPage = 1; refresh(); });
        status?.addEventListener("change", () => { currentPage = 1; refresh(); });
        sort?.addEventListener("change", () => { currentPage = 1; refresh(); });
        size?.addEventListener("change", () => { currentPage = 1; refresh(); });
        selectAll?.addEventListener("change", () => rows.filter((row) => !row.hidden).forEach((row) => {
            const checkbox = row.querySelector("[data-row-select]");
            if (checkbox) checkbox.checked = selectAll.checked;
        }));
        refresh();
    });

    const extractTable = (button) => {
        const pageRoot = button.closest(".catalog-card") || document;
        const table = pageRoot.querySelector("[data-catalog-table]") || document.querySelector("[data-catalog-table]");
        if (!table) return [];
        const header = [...table.querySelectorAll("thead tr")].map((row) => [...row.querySelectorAll("th")]
            .filter((cell) => !cell.classList.contains("no-export"))
            .map((cell) => cell.innerText.replace(/\s+/g, " ").trim()));
        const body = [...table.querySelectorAll("tbody tr")]
            .filter((row) => row.hasAttribute("data-search-row") ? row.dataset.filteredOut !== "true" : !row.hidden)
            .filter((row) => !row.hasAttribute("data-empty-row"))
            .map((row) => [...row.querySelectorAll("td")]
                .filter((cell) => !cell.classList.contains("no-export"))
                .map((cell) => cell.innerText.replace(/\s+/g, " ").trim()));
        return [...header, ...body];
    };

    const download = (content, type, filename) => {
        const blob = new Blob([content], { type });
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = filename;
        document.body.append(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(url);
    };

    document.querySelectorAll("[data-export]").forEach((button) => button.addEventListener("click", async () => {
        const kind = button.dataset.export;
        if (kind === "print") return window.print();
        const data = extractTable(button);
        if (!data.length) return;
        if (kind === "copy") {
            await navigator.clipboard?.writeText(data.map((row) => row.join("\t")).join("\n"));
            return;
        }
        if (kind === "csv") {
            const csv = data.map((row) => row.map((cell) => `"${cell.replace(/"/g, '""')}"`).join(",")).join("\n");
            return download(csv, "text/csv;charset=utf-8", "falcon-export.csv");
        }
        if (kind === "excel") {
            const tsv = data.map((row) => row.join("\t")).join("\n");
            return download(tsv, "application/vnd.ms-excel", "falcon-export.xls");
        }
    }));

    document.querySelectorAll("[data-modal-open]").forEach((button) => {
        button.addEventListener("click", () => document.getElementById(button.dataset.modalOpen)?.classList.add("is-open"));
    });
    document.querySelectorAll("[data-modal-close]").forEach((button) => {
        button.addEventListener("click", () => button.closest(".modal-backdrop")?.classList.remove("is-open"));
    });
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") document.querySelectorAll(".modal-backdrop.is-open").forEach((modal) => modal.classList.remove("is-open"));
    });

    const categoryName = document.querySelector("[data-category-name]");
    const categorySlug = document.querySelector("[data-category-slug]");
    let slugEdited = false;
    categorySlug?.addEventListener("input", () => { slugEdited = categorySlug.value.trim().length > 0; });
    categoryName?.addEventListener("input", () => {
        if (!categorySlug || slugEdited) return;
        categorySlug.value = categoryName.value.trim().toLowerCase()
            .replace(/[^a-z0-9]+/g, "-")
            .replace(/^-+|-+$/g, "");
    });

    document.querySelectorAll("[data-reset-form]").forEach((button) => button.addEventListener("click", () => {
        const form = button.closest("form");
        if (!form) return;
        form.reset();
        form.querySelectorAll("[data-gallery-row]").forEach((row) => row.remove());
        form.querySelectorAll("[data-gallery-empty]").forEach((empty) => empty.hidden = false);
        syncExpiry();
        syncImage();
    }));

    document.querySelectorAll("[data-barcode-label-count]").forEach((input) => input.addEventListener("input", () => {
        const target = document.querySelector("[data-barcode-sheet]");
        if (!target) return;
        const count = Math.max(1, Math.min(200, Number(input.value) || 1));
        target.dataset.count = String(count);
    }));


    const primaryImageFile = document.querySelector("[data-primary-image-file]");
    primaryImageFile?.addEventListener("change", () => {
        const selected = primaryImageFile.files?.[0];
        if (!selected || !imageTarget) return;
        const objectUrl = URL.createObjectURL(selected);
        const image = document.createElement("img");
        image.src = objectUrl;
        image.alt = "Product preview";
        imageTarget.replaceChildren(image);
    });

    const additionalBarcodeProduct = document.querySelector("[data-additional-barcode-product]");
    const additionalBarcodeAdd = document.querySelector("[data-additional-barcode-add]");
    const barcodeProductSet = document.querySelector("[data-barcode-product-set]");
    additionalBarcodeAdd?.addEventListener("click", () => {
        const option = additionalBarcodeProduct?.selectedOptions?.[0];
        if (!option || !option.value || !barcodeProductSet) return;
        if (barcodeProductSet.querySelector(`[data-product-id="${option.value}"]`)) return;
        const chip = document.createElement("span");
        chip.className = "branch-chip";
        chip.dataset.productId = option.value;
        chip.textContent = option.textContent.trim();
        barcodeProductSet.append(chip);
    });

    document.querySelectorAll("[data-gallery-editor]").forEach((editor) => {
        const body = editor.querySelector("[data-gallery-body]");
        const empty = editor.querySelector("[data-gallery-empty]");
        const template = editor.querySelector("[data-gallery-template]");
        const addButton = editor.querySelector("[data-gallery-add]");
        if (!body || !template || !addButton) return;

        const refreshRows = () => {
            const rows = [...body.querySelectorAll("[data-gallery-row]")];
            if (empty) empty.hidden = rows.length > 0;
            rows.forEach((row, index) => {
                const serial = row.querySelector("[data-gallery-serial]");
                if (serial) serial.textContent = String(index + 1);
                const url = row.querySelector("[data-gallery-url]");
                const order = row.querySelector("[data-gallery-order]");
                const primary = row.querySelector("[data-gallery-primary]");
                if (url) url.name = `images[${index}].imageReference`;
                if (order) order.name = `images[${index}].displayOrder`;
                if (primary) { primary.name = `images[${index}].primaryImage`; primary.value = "true"; }
            });
        };

        const previewFromUrl = (row, url) => {
            const target = row.querySelector("[data-gallery-preview]");
            if (!target) return;
            if (!url) { target.textContent = "Image"; return; }
            const image = document.createElement("img");
            image.src = url;
            image.alt = "Additional product preview";
            target.replaceChildren(image);
        };

        const wireRow = (row) => {
            const file = row.querySelector("[data-gallery-file]");
            const url = row.querySelector("[data-gallery-url]");
            const primary = row.querySelector("[data-gallery-primary]");
            row.querySelector("[data-gallery-remove]")?.addEventListener("click", () => { row.remove(); refreshRows(); });
            url?.addEventListener("input", () => previewFromUrl(row, url.value.trim()));
            file?.addEventListener("change", () => {
                const selected = file.files?.[0];
                if (!selected) return previewFromUrl(row, url?.value.trim() || "");
                const objectUrl = URL.createObjectURL(selected);
                previewFromUrl(row, objectUrl);
            });
            primary?.addEventListener("change", () => {
                if (!primary.checked) return;
                editor.querySelectorAll("[data-gallery-primary]").forEach((other) => { if (other !== primary) other.checked = false; });
            });
        };

        addButton.addEventListener("click", () => {
            const fragment = template.content.cloneNode(true);
            const row = fragment.querySelector("[data-gallery-row]");
            wireRow(row);
            body.append(fragment);
            refreshRows();
        });
        body.querySelectorAll("[data-gallery-row]").forEach(wireRow);
        refreshRows();
    });

    document.querySelectorAll("form").forEach((form) => form.addEventListener("submit", (event) => {
        if (event.defaultPrevented) return;
        form.querySelectorAll('button[type="submit"]').forEach((button) => {
            button.disabled = true;
            button.setAttribute("aria-busy", "true");
        });
    }));

    document.querySelectorAll("[data-open-product-window]").forEach((button) => {
        button.addEventListener("click", () => {
            const url = button.dataset.productUrl;
            if (!url) return;
            window.open(url, "falconCreateProduct", "width=1240,height=860,resizable=yes,scrollbars=yes");
        });
    });

    const alertExportBase = location.pathname.endsWith('/stock-alert')
        ? '/owner/products/stock-alert/export'
        : (location.pathname.endsWith('/expiry-alert') ? '/owner/products/expiry-alert/export' : null);
    if (alertExportBase) {
        const toolbar = document.querySelector('.toolbar');
        const params = new URLSearchParams(location.search);
        if (location.pathname.endsWith('/stock-alert') && toolbar && !toolbar.querySelector('[name="sort"]')) {
            const sort = document.createElement('select'); sort.name = 'sort'; sort.setAttribute('aria-label', 'Sort stock alerts');
            [['stock', 'Lowest stock'], ['name', 'Product name'], ['supplier', 'Supplier']].forEach(([value, label]) => {
                const option = document.createElement('option'); option.value = value; option.textContent = label;
                option.selected = (params.get('sort') || 'stock') === value; sort.append(option);
            });
            toolbar.insertBefore(sort, toolbar.querySelector('button'));
            document.querySelectorAll('.pagination a').forEach((anchor) => {
                const url = new URL(anchor.href, location.origin); url.searchParams.set('sort', sort.value); anchor.href = url;
            });
        }
        ['xlsx', 'csv', 'pdf'].forEach((format) => {
            const link = document.createElement('a');
            const exportParams = new URLSearchParams(params);
            exportParams.set('format', format);
            link.className = 'export-btn';
            link.href = `${alertExportBase}?${exportParams}`;
            link.textContent = format === 'xlsx' ? 'Excel' : format.toUpperCase();
            toolbar?.append(link);
        });
    }


})();
