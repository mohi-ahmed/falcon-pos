(() => {
    const form = document.querySelector('#ownerSignupForm');
    if (!form) return;

    const password = form.querySelector('#password');
    const phoneCountry = form.querySelector('#phoneCountry');
    const mobile = form.querySelector('#mobile');
    const mobileInternational = form.querySelector('#mobileInternational');
    const strength = form.querySelector('.password-strength span');
    const alert = document.querySelector('#prototypeAlert');

    const messages = {
        fullName: 'Enter your full name.',
        mobile: 'Enter a valid phone number using 6 to 14 digits, or leave it blank.',
        email: 'Enter a valid email address.',
        password: 'Use at least 8 characters with upper, lower and a number.'
    };

    const passwordIsStrong = value => value.length >= 8 && /[A-Z]/.test(value) && /[a-z]/.test(value) && /\d/.test(value);

    const validateField = input => {
        const field = input.closest('.form-field');
        if (!field) return true;
        const mobileDigits = input.value.replace(/\D/g, '');
        const mobileValid = input.id !== 'mobile' || !input.value.trim() || /^\d{6,14}$/.test(mobileDigits);
        const valid = input.id === 'password' ? passwordIsStrong(input.value) : input.checkValidity() && mobileValid;
        field.classList.toggle('has-error', !valid);
        const error = field.querySelector('.field-error');
        if (error) error.textContent = valid ? '' : (messages[input.id] || 'Check this field and try again.');
        return valid;
    };

    form.querySelectorAll('input:not([type="checkbox"])').forEach(input => {
        input.addEventListener('blur', () => validateField(input));
        input.addEventListener('input', () => {
            const field = input.closest('.form-field');
            if (field?.classList.contains('has-error')) validateField(input);
        });
    });

    form.querySelector('[data-password-toggle]').addEventListener('click', event => {
        const reveal = password.type === 'password';
        password.type = reveal ? 'text' : 'password';
        event.currentTarget.textContent = reveal ? 'Hide' : 'Show';
        event.currentTarget.setAttribute('aria-label', reveal ? 'Hide password' : 'Show password');
    });

    password.addEventListener('input', () => {
        let score = 0;
        if (password.value.length >= 8) score++;
        if (/[A-Z]/.test(password.value) && /[a-z]/.test(password.value)) score++;
        if (/\d/.test(password.value)) score++;
        if (/[^A-Za-z0-9]/.test(password.value)) score++;
        strength.style.width = `${score * 25}%`;
        strength.style.background = score >= 4 ? '#08785b' : score >= 2 ? '#d97706' : '#b42318';
    });

    const syncPhoneNumber = () => {
        const selected = phoneCountry.options[phoneCountry.selectedIndex];
        mobile.placeholder = selected.dataset.example || 'Phone number';
        const nationalNumber = mobile.value.replace(/\D/g, '');
        mobileInternational.value = nationalNumber ? `${phoneCountry.value}${nationalNumber}` : '';
    };
    phoneCountry.addEventListener('change', syncPhoneNumber);
    mobile.addEventListener('input', syncPhoneNumber);
    syncPhoneNumber();

    form.addEventListener('submit', event => {
        event.preventDefault();
        const fieldsValid = [...form.querySelectorAll('input:not([type="checkbox"])')].every(validateField);
        const terms = form.querySelector('#terms');
        const consentError = form.querySelector('.consent-error');
        if (consentError) consentError.textContent = terms.checked ? '' : 'Accept the terms to continue.';
        if (!fieldsValid || !terms.checked) {
            form.querySelector('.has-error input, input:invalid')?.focus();
            return;
        }
        const hasResolvedServerAction = form.action
            && form.getAttribute('action') !== '#'
            && !form.action.endsWith('#');
        if (form.dataset.serverForm === 'true' && hasResolvedServerAction) {
            const submit = form.querySelector('[type="submit"]');
            submit.disabled = true;
            submit.setAttribute('aria-busy', 'true');
            submit.innerHTML = '<span class="button-spinner" aria-hidden="true"></span> Creating account&hellip;';
            form.submit();
            return;
        }
        if (form.querySelector('#email').value.trim().toLowerCase() === 'owner@falcon.test') {
            const emailField = form.querySelector('#email').closest('.form-field');
            emailField.classList.add('has-error');
            const emailError = emailField.querySelector('.field-error');
            if (emailError) emailError.textContent = 'An account already exists for this email. Sign in or reset the password.';
            form.querySelector('#email').focus();
            return;
        }
        const submit = form.querySelector('[type="submit"]');
        submit.disabled = true;
        submit.setAttribute('aria-busy', 'true');
        submit.innerHTML = '<span class="button-spinner" aria-hidden="true"></span> Creating account…';
        alert.hidden = false;
        alert.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        window.setTimeout(() => {
            window.location.href = form.querySelector('[data-next-page]').dataset.nextPage;
        }, 650);
    });
})();
