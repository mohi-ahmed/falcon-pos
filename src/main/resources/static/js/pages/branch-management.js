(() => {
    document.querySelector("[data-print-branches]")?.addEventListener("click", () => window.print());

    document.querySelectorAll("form").forEach((form) => {
        const submitButton = form.querySelector("[data-submit-button]");
        if (!submitButton) return;
        form.addEventListener("submit", () => {
            if (!form.checkValidity()) return;
            submitButton.disabled = true;
            submitButton.setAttribute("aria-busy", "true");
            submitButton.textContent = "Saving…";
        });
    });

    const switchForms = [...document.querySelectorAll("[data-branch-switch-form]")];
    if (!switchForms.length) return;

    const modal = document.createElement("div");
    modal.className = "branch-confirm-modal";
    modal.hidden = true;
    modal.innerHTML = `
        <div class="branch-confirm-backdrop" data-branch-confirm-cancel></div>
        <section class="branch-confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="branch-switch-title" aria-describedby="branch-switch-description">
            <div class="branch-confirm-icon" aria-hidden="true">↔</div>
            <div class="branch-confirm-copy">
                <span class="branch-confirm-eyebrow">Branch workspace</span>
                <h2 id="branch-switch-title">Switch active branch?</h2>
                <p id="branch-switch-description">You are changing the active working branch.</p>
            </div>
            <div class="branch-confirm-route" aria-label="Branch change">
                <div><small>Current</small><strong data-current-branch>Current branch</strong></div>
                <span aria-hidden="true">→</span>
                <div><small>Switch to</small><strong data-target-branch>Selected branch</strong></div>
            </div>
            <div class="branch-confirm-notice">
                <span aria-hidden="true">!</span>
                <p>Complete or discard any unsaved order or incomplete transaction before switching branches.</p>
            </div>
            <div class="branch-confirm-actions">
                <button class="branch-button" type="button" data-branch-confirm-cancel>Cancel</button>
                <button class="branch-button primary" type="button" data-branch-confirm-submit>Use selected branch</button>
            </div>
        </section>`;
    document.body.appendChild(modal);

    const dialog = modal.querySelector(".branch-confirm-dialog");
    const currentName = modal.querySelector("[data-current-branch]");
    const targetName = modal.querySelector("[data-target-branch]");
    const submit = modal.querySelector("[data-branch-confirm-submit]");
    const cancelButtons = [...modal.querySelectorAll("[data-branch-confirm-cancel]")];
    let pendingForm = null;
    let trigger = null;

    const focusable = () => [...dialog.querySelectorAll("button:not([disabled])")];

    const closeModal = () => {
        modal.hidden = true;
        document.body.classList.remove("branch-modal-open");
        pendingForm = null;
        trigger?.focus();
        trigger = null;
    };

    const openModal = (form) => {
        pendingForm = form;
        trigger = document.activeElement;
        const current = form.dataset.currentBranch || "Current branch";
        const target = form.dataset.targetBranch || "Selected branch";
        currentName.textContent = current;
        targetName.textContent = target;
        submit.textContent = `Use ${target}`;
        modal.hidden = false;
        document.body.classList.add("branch-modal-open");
        requestAnimationFrame(() => submit.focus());
    };

    switchForms.forEach((form) => {
        form.addEventListener("submit", (event) => {
            if (form.dataset.branchSwitchConfirmed === "true") return;
            event.preventDefault();
            openModal(form);
        });
    });

    cancelButtons.forEach((button) => button.addEventListener("click", closeModal));

    submit.addEventListener("click", () => {
        if (!pendingForm) return;
        const form = pendingForm;
        form.dataset.branchSwitchConfirmed = "true";
        submit.disabled = true;
        submit.setAttribute("aria-busy", "true");
        submit.textContent = "Switching…";
        form.submit();
    });

    document.addEventListener("keydown", (event) => {
        if (modal.hidden) return;
        if (event.key === "Escape") {
            event.preventDefault();
            closeModal();
            return;
        }
        if (event.key !== "Tab") return;
        const items = focusable();
        if (!items.length) return;
        const first = items[0];
        const last = items[items.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    });
})();
