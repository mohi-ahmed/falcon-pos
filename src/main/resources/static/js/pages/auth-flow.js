(() => {
    'use strict';

    const statusBox = document.querySelector('[data-auth-status]');
    const showStatus = (message, tone = 'error') => {
        if (!statusBox) return;
        statusBox.hidden = false;
        statusBox.dataset.tone = tone;
        const copy = statusBox.querySelector('[data-status-copy]');
        if (copy) copy.textContent = message;
        statusBox.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    };
    const clearStatus = () => { if (statusBox) statusBox.hidden = true; };
    const setError = (input, message = '') => {
        const field = input?.closest('.form-field');
        if (field) field.classList.toggle('has-error', Boolean(message));
        const error = document.querySelector(`[data-error-for="${input?.id}"]`);
        if (error) error.textContent = message;
        input?.setAttribute('aria-invalid', message ? 'true' : 'false');
    };
    const validateEmail = input => {
        const valid = input.value.trim() && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.value.trim());
        setError(input, valid ? '' : 'Enter a valid email address.');
        return valid;
    };
    const setLoading = (form, loading) => {
        const button = form.querySelector('[type="submit"]');
        if (!button) return;
        button.disabled = loading;
        button.setAttribute('aria-busy', String(loading));
        const label = button.dataset.submitLabel || 'Continue';
        button.innerHTML = loading ? `<span class="button-spinner" aria-hidden="true"></span> Please wait…` : `${label} <span aria-hidden="true">→</span>`;
    };
    const afterDelay = callback => window.setTimeout(callback, 650);

    document.querySelectorAll('[data-password-toggle]').forEach(button => button.addEventListener('click', () => {
        const input = document.getElementById(button.dataset.passwordToggle);
        if (!input) return;
        const reveal = input.type === 'password';
        input.type = reveal ? 'text' : 'password';
        button.textContent = reveal ? 'Hide' : 'Show';
        button.setAttribute('aria-label', `${reveal ? 'Hide' : 'Show'} password`);
    }));

    const otpInputs = [...document.querySelectorAll('[data-otp] input')];

    otpInputs.forEach((input, index) => {
        input.addEventListener('input', () => {
            input.value = input.value.replace(/\D/g, '').slice(0, 1);
            clearStatus();
            if (input.value && otpInputs[index + 1]) otpInputs[index + 1].focus();
        });
        input.addEventListener('keydown', event => {
            if (event.key === 'Backspace' && !input.value && otpInputs[index - 1]) otpInputs[index - 1].focus();
            if (event.key === 'ArrowLeft' && otpInputs[index - 1]) otpInputs[index - 1].focus();
            if (event.key === 'ArrowRight' && otpInputs[index + 1]) otpInputs[index + 1].focus();
        });
        input.addEventListener('paste', event => {
            const digits = event.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
            if (!digits) return;
            event.preventDefault();
            digits.split('').forEach((digit, digitIndex) => { if (otpInputs[digitIndex]) otpInputs[digitIndex].value = digit; });
            otpInputs[Math.min(digits.length, 6) - 1].focus();
        });
    });
    const resendButton = document.querySelector('[data-resend-code]');
    const resendSeconds = document.querySelector('[data-resend-seconds]');

    if (resendButton && resendSeconds) {

        let secondsRemaining = Number(
            resendSeconds.dataset.remainingSeconds || 0
        );

        const formatTime = totalSeconds => {
            const minutes = Math.floor(totalSeconds / 60);
            const seconds = totalSeconds % 60;

            return `${minutes}:${String(seconds).padStart(2, '0')}`;
        };

        const updateResendButton = () => {

            if (secondsRemaining <= 0) {
                resendButton.disabled = false;
                resendButton.textContent = 'Resend code';
                return;
            }

            resendButton.disabled = true;

            resendSeconds.textContent =
                formatTime(secondsRemaining);
        };

        updateResendButton();

        if (secondsRemaining > 0) {

            const timer = window.setInterval(() => {

                secondsRemaining -= 1;

                updateResendButton();

                if (secondsRemaining <= 0) {
                    window.clearInterval(timer);
                }

            }, 1000);
        }
    }

    const password = document.querySelector('#newPassword');
    const meter = document.querySelector('[data-password-meter]');
    const passwordRules = {
        length: value => value.length >= 8,
        upper: value => /[A-Z]/.test(value),
        lower: value => /[a-z]/.test(value),
        number: value => /\d/.test(value)
    };
    const passwordScore = value => Object.values(passwordRules).filter(rule => rule(value)).length;
    password?.addEventListener('input', () => {
        const value = password.value;
        if (meter) meter.dataset.score = String(passwordScore(value));
        Object.entries(passwordRules).forEach(([name, rule]) => document.querySelector(`[data-rule="${name}"]`)?.classList.toggle('is-met', rule(value)));
        setError(password, '');
    });

    document.querySelectorAll('[data-auth-form]').forEach(form => form.addEventListener('submit', event => {
        event.preventDefault();
        clearStatus();
        const type = form.dataset.authForm;

        if (type === 'signin') {
            const email = form.querySelector('#email');
            const passwordInput = form.querySelector('#loginPassword');
            const validEmail = validateEmail(email);
            setError(passwordInput, passwordInput.value ? '' : 'Enter your password.');
            if (!validEmail || !passwordInput.value) return;
            if (form.dataset.serverForm === 'true' && window.location.protocol !== 'file:') {
                setLoading(form, true);
                form.submit();
                return;
            }
            setLoading(form, true);
            afterDelay(() => {
                setLoading(form, false);
                const value = passwordInput.value.toLowerCase();
                if (value === 'wrongpass') { setError(passwordInput, 'The email or password is incorrect.'); passwordInput.focus(); return; }
                if (value === 'lockedpass') { showStatus('This account is temporarily locked after multiple attempts. Try again in 15 minutes or reset your password.', 'warning'); return; }
                if (value === 'serverfail') { showStatus('We could not reach Falcon POS. Check your connection and try again.'); return; }
                showStatus('Sign-in details validated. Role-based redirection will be connected to Spring Security.', 'success');
            });
        }

        if (type === 'forgot') {
            const email = form.querySelector('#recoveryEmail');
            if (!validateEmail(email)) return;

            setLoading(form, true);
            afterDelay(() => {
                setLoading(form, false);
                showStatus('If an account matches that email, a verification code has been sent.', 'success');
                window.setTimeout(() => { window.location.href = 'password-reset-verification.html'; }, 850);
            });
        }

        if (type === 'reset-otp') {

            const code = otpInputs
                .map(input => input.value)
                .join('');

            if (code.length !== 6) {
                showStatus('Enter all six digits from your email.');

                otpInputs
                    .find(input => !input.value)
                    ?.focus();

                return;
            }

            const codeField =
                form.querySelector('[data-otp-code]');

            if (codeField) {
                codeField.value = code;
            }

            if (
                form.dataset.serverForm === 'true' &&
                window.location.protocol !== 'file:'
            ) {
                setLoading(form, true);
                form.submit();
                return;
            }


        }

        if (type === 'reset-password') {
            const confirm = form.querySelector('#confirmPassword');
            const score = passwordScore(password.value);

            setError(
                password,
                score === 4
                    ? ''
                    : 'Meet all four password requirements.'
            );

            setError(
                confirm,
                confirm.value === password.value && confirm.value
                    ? ''
                    : 'Passwords do not match.'
            );

            if (score !== 4 || confirm.value !== password.value) {
                return;
            }

            if (
                form.dataset.serverForm === 'true' &&
                window.location.protocol !== 'file:'
            ) {
                setLoading(form, true);
                form.submit();
                return;
            }

            setLoading(form, true);

            afterDelay(() => {
                window.location.href = 'password-reset-complete.html';
            });
        }
    }));

    document.querySelectorAll('[data-demo-form]:not([data-auth-form])').forEach(form => form.addEventListener('submit', event => {
        event.preventDefault();
        if (!form.checkValidity()) { form.reportValidity(); return; }
        const button = form.querySelector('[type="submit"]');
        if (button) { button.disabled = true; button.innerHTML = '<span class="button-spinner" aria-hidden="true"></span> Creating workspace…'; }
        window.setTimeout(() => { if (form.dataset.next) window.location.href = form.dataset.next; }, 650);
    }));

    const country = document.querySelector('#businessCountry');
    const currency = document.querySelector('#currency');
    const timezone = document.querySelector('#timezone');
    const regionalDefaults = { BD: { currency: 'BDT', timezone: 'Asia/Dhaka' }, AE: { currency: 'AED', timezone: 'Asia/Dubai' }, SA: { currency: 'SAR', timezone: 'Asia/Riyadh' }, TR: { currency: 'TRY', timezone: 'Europe/Istanbul' }, GB: { currency: 'GBP', timezone: 'Europe/London' }, US: { currency: 'USD', timezone: 'America/New_York' } };
    country?.addEventListener('change', () => { const defaults = regionalDefaults[country.value]; if (defaults) { currency.value = defaults.currency; timezone.value = defaults.timezone; } });
})();
