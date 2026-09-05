(function () {
    'use strict';

    function lockForm(form) {
        if (!form || form.dataset.submitting === 'true') return false;
        form.dataset.submitting = 'true';
        form.setAttribute('aria-busy', 'true');

        form.querySelectorAll('button[type="submit"]').forEach(function (button) {
            if (!button.dataset.originalText) button.dataset.originalText = button.textContent;
            button.disabled = true;
            var label = button.dataset.submitLabel;
            if (label) button.textContent = label + '…';
        });
        return true;
    }

    function unlockForm(form) {
        if (!form) return;
        delete form.dataset.submitting;
        form.removeAttribute('aria-busy');
        form.querySelectorAll('button[type="submit"]').forEach(function (button) {
            button.disabled = false;
            if (button.dataset.originalText) {
                button.textContent = button.dataset.originalText;
                delete button.dataset.originalText;
            }
        });
    }

    document.querySelectorAll('form[data-supplier-submit-lock]').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            if (!lockForm(form)) event.preventDefault();
        });
    });

    window.addEventListener('pageshow', function () {
        document.querySelectorAll('form[data-supplier-submit-lock]').forEach(unlockForm);
    });
}());
