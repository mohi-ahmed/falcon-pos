(() => {
    const normalize = (value) => (value ?? "").toString().trim();

    document.querySelectorAll("form[data-confirm]").forEach((form) => {
        form.addEventListener("submit", (event) => {
            if (!window.confirm(form.dataset.confirm || "Continue with this action?")) event.preventDefault();
        });
    });

    document.querySelectorAll("[data-format-instant]").forEach((element) => {
        const raw = element.getAttribute("datetime") || element.textContent;
        const date = new Date(raw);
        if (Number.isNaN(date.getTime())) return;
        element.textContent = new Intl.DateTimeFormat(undefined, {day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit"}).format(date);
    });

    document.querySelectorAll("[data-password-toggle]").forEach((button) => {
        button.addEventListener("click", () => {
            const input = button.closest(".password-input-shell")?.querySelector("input");
            if (!input) return;
            const reveal = input.type === "password";
            input.type = reveal ? "text" : "password";
            button.textContent = reveal ? "Hide" : "Show";
            button.setAttribute("aria-pressed", String(reveal));
        });
    });

    document.querySelectorAll("[data-password-meter-input]").forEach((input) => {
        const meter = input.closest(".form-field")?.querySelector("[data-password-meter]");
        const update = () => {
            if (!meter) return;
            const value = input.value;
            let score = 0;
            if (value.length >= 8) score++;
            if (/[A-Z]/.test(value) && /[a-z]/.test(value)) score++;
            if (/\d/.test(value)) score++;
            if (value.length >= 12) score++;
            meter.dataset.strength = String(score);
        };
        input.addEventListener("input", update);
        update();
    });

    document.querySelectorAll("[data-photo-input]").forEach((input) => {
        input.addEventListener("change", () => {
            const file = input.files?.[0];
            const preview = document.querySelector(input.dataset.photoTarget || "");
            if (!file || !preview || !file.type.startsWith("image/")) return;
            const reader = new FileReader();
            reader.addEventListener("load", () => {
                preview.innerHTML = "";
                const image = document.createElement("img");
                image.src = reader.result;
                image.alt = "Selected profile photo preview";
                preview.appendChild(image);
            });
            reader.readAsDataURL(file);
        });
    });

    const slugify = (value) => normalize(value)
        .normalize("NFKD")
        .replace(/[\u0300-\u036f]/g, "")
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/^-+|-+$/g, "")
        .slice(0, 140);

    document.querySelectorAll("[data-slug-source]").forEach((nameInput) => {
        const form = nameInput.closest("form");
        const slugInput = form?.querySelector("[data-slug-input]");
        const preview = form?.querySelector("[data-slug-preview]");
        if (!slugInput) return;
        let manuallyEdited = normalize(slugInput.value) !== "";
        slugInput.addEventListener("input", () => {
            manuallyEdited = normalize(slugInput.value) !== "";
            if (preview) preview.textContent = normalize(slugInput.value) || slugify(nameInput.value) || "group-slug";
        });
        nameInput.addEventListener("input", () => {
            if (!manuallyEdited) slugInput.value = slugify(nameInput.value);
            if (preview) preview.textContent = normalize(slugInput.value) || slugify(nameInput.value) || "group-slug";
        });
        if (preview) preview.textContent = normalize(slugInput.value) || slugify(nameInput.value) || "group-slug";
    });

    document.querySelectorAll("[data-create-panel]").forEach((panel) => {
        const toggle = panel.querySelector("[data-create-toggle]");
        const body = panel.querySelector("[data-create-body]");
        if (!toggle || !body) return;
        const setOpen = (open) => {
            panel.classList.toggle("is-open", open);
            body.hidden = !open;
            toggle.setAttribute("aria-expanded", String(open));
        };
        toggle.addEventListener("click", () => setOpen(body.hidden));
        setOpen(panel.dataset.openInitially === "true" || panel.querySelector(".has-error, .field-error:not(:empty)") !== null);
    });

    const parsePermissionIds = (value) => new Set(normalize(value).split(",").map((item) => item.trim()).filter(Boolean));
    document.querySelectorAll("[data-role-select]").forEach((select) => {
        const scope = select.closest("form") || document;
        const items = [...scope.querySelectorAll("[data-permission-preview-item]")];
        const summary = scope.querySelector("[data-role-permission-summary]");
        const update = () => {
            const option = select.selectedOptions?.[0];
            const ids = parsePermissionIds(option?.dataset.permissionIds);
            items.forEach((item) => {
                const granted = ids.has(normalize(item.dataset.permissionId));
                item.classList.toggle("is-granted", granted);
                const mark = item.querySelector("i");
                if (mark) mark.textContent = granted ? "✓" : "–";
            });
            if (summary) summary.textContent = option?.value ? `${ids.size} permissions from ${option.textContent.trim()}` : "Select a group to preview access";
        };
        select.addEventListener("change", update);
        update();
    });

    document.querySelectorAll("[data-permission-checkbox]").forEach((checkbox) => {
        const form = checkbox.closest("form");
        const count = form?.querySelector("[data-selected-permission-count]");
        const updateCount = () => {
            if (!count || !form) return;
            count.textContent = String(form.querySelectorAll("[data-permission-checkbox]:checked").length);
        };
        checkbox.addEventListener("change", updateCount);
        updateCount();
    });

    const escapeCsv = (value) => `"${normalize(value).replaceAll('"', '""')}"`;
    const downloadBlob = (blob, filename) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = filename;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        setTimeout(() => URL.revokeObjectURL(url), 500);
    };

    const exportTable = (table, type) => {
        if (!table) return;
        const actionIndex = [...table.tHead.rows[0].cells].findIndex((cell) => cell.classList.contains("action-column"));
        const cleanCells = (row) => [...row.cells]
            .filter((_, index) => index !== actionIndex)
            .map((cell) => normalize(cell.innerText).replace(/\s+/g, " "));
        const headers = cleanCells(table.tHead.rows[0]);
        const rows = [...table.tBodies[0].rows]
            .filter((row) => !row.hidden && !row.classList.contains("empty-table-row"))
            .map(cleanCells);
        const name = table.dataset.exportName || "falcon-users";
        if (type === "print" || type === "pdf") {
            window.print();
            return;
        }
        if (type === "csv") {
            const csv = [headers, ...rows].map((row) => row.map(escapeCsv).join(",")).join("\r\n");
            downloadBlob(new Blob(["\uFEFF", csv], {type: "text/csv;charset=utf-8"}), `${name}.csv`);
            return;
        }
        if (type === "excel") {
            const encode = (value) => normalize(value).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
            const html = `<html><head><meta charset="UTF-8"></head><body><table><thead><tr>${headers.map((h) => `<th>${encode(h)}</th>`).join("")}</tr></thead><tbody>${rows.map((row) => `<tr>${row.map((cell) => `<td>${encode(cell)}</td>`).join("")}</tr>`).join("")}</tbody></table></body></html>`;
            downloadBlob(new Blob([html], {type: "application/vnd.ms-excel;charset=utf-8"}), `${name}.xls`);
        }
    };

    document.querySelectorAll("[data-export-target]").forEach((button) => {
        button.addEventListener("click", () => {
            const table = document.getElementById(button.dataset.exportTarget);
            exportTable(table, button.dataset.exportType || "print");
        });
    });

    document.querySelectorAll("[data-client-table]").forEach((table) => {
        if (!table.tBodies.length) return;
        const card = table.closest(".settings-table-card");
        const search = card?.querySelector("[data-client-search]");
        const sizeSelect = card?.querySelector("[data-client-size]");
        const pagination = card?.querySelector("[data-client-pagination]");
        const summary = card?.querySelector("[data-client-summary]");
        const allRows = [...table.tBodies[0].rows].filter((row) => !row.classList.contains("empty-table-row"));
        let page = 1;
        let sortIndex = null;
        let asc = true;

        const getRows = () => {
            const q = normalize(search?.value).toLowerCase();
            let rows = allRows.filter((row) => !q || normalize(row.dataset.searchText || row.innerText).toLowerCase().includes(q));
            if (sortIndex !== null) {
                rows = [...rows].sort((a, b) => {
                    const left = normalize(a.cells[sortIndex]?.innerText).toLowerCase();
                    const right = normalize(b.cells[sortIndex]?.innerText).toLowerCase();
                    return left.localeCompare(right, undefined, {numeric: true}) * (asc ? 1 : -1);
                });
            }
            return rows;
        };

        const render = () => {
            const rows = getRows();
            const pageSize = Number(sizeSelect?.value || 10);
            const pages = Math.max(1, Math.ceil(rows.length / pageSize));
            page = Math.min(page, pages);
            const start = (page - 1) * pageSize;
            const visible = new Set(rows.slice(start, start + pageSize));
            allRows.forEach((row) => row.hidden = !visible.has(row));
            const empty = table.querySelector(".empty-table-row");
            if (empty) empty.hidden = rows.length !== 0;
            if (summary) summary.textContent = rows.length ? `Showing ${start + 1}–${Math.min(start + pageSize, rows.length)} of ${rows.length}` : "No matching records";
            if (pagination) {
                pagination.innerHTML = "";
                const add = (label, target, active = false, disabled = false) => {
                    const button = document.createElement("button");
                    button.type = "button";
                    button.textContent = label;
                    button.disabled = disabled;
                    button.classList.toggle("is-active", active);
                    button.addEventListener("click", () => { page = target; render(); });
                    pagination.appendChild(button);
                };
                add("‹", Math.max(1, page - 1), false, page === 1);
                const first = Math.max(1, Math.min(page - 2, pages - 4));
                for (let i = first; i <= Math.min(pages, first + 4); i++) add(String(i), i, i === page);
                add("›", Math.min(pages, page + 1), false, page === pages);
            }
        };

        search?.addEventListener("input", () => { page = 1; render(); });
        sizeSelect?.addEventListener("change", () => { page = 1; render(); });
        table.querySelectorAll("th[data-client-sort]").forEach((header) => {
            header.addEventListener("click", () => {
                const next = Number(header.dataset.clientSort);
                if (sortIndex === next) asc = !asc; else { sortIndex = next; asc = true; }
                page = 1;
                render();
            });
        });
        render();
    });
})();
