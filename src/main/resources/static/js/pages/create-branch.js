(() => {
    const form = document.querySelector("[data-branch-form]");
    if (!form) return;
    const submitButton = form.querySelector("[data-submit-button]");
    const submitLabel = form.querySelector("[data-submit-label]");
    const countrySelect = form.querySelector("[data-country]");
    const timeZoneSelect = form.querySelector("[data-timezone]");
    const currencySelect = form.querySelector("[data-currency]");
    const characterSource = form.querySelector("[data-character-source]");
    const characterCount = form.querySelector("[data-character-count]");
    const countryDefaults = {BD:{timeZone:"Asia/Dhaka",currency:"BDT"},AE:{timeZone:"Asia/Dubai",currency:"AED"},SA:{timeZone:"Asia/Riyadh",currency:"SAR"},TR:{timeZone:"Europe/Istanbul",currency:"TRY"},GB:{timeZone:"Europe/London",currency:"GBP"},US:{timeZone:"America/New_York",currency:"USD"}};

    form.querySelectorAll("[data-uppercase]").forEach((input) => input.addEventListener("input", () => {
        input.value = input.value.toUpperCase().replace(/[^A-Z0-9-]/g, "");
    }));
    countrySelect?.addEventListener("change", () => {
        const defaults = countryDefaults[countrySelect.value];
        if (!defaults) return;
        if (timeZoneSelect && !timeZoneSelect.value) timeZoneSelect.value = defaults.timeZone;
        if (currencySelect && !currencySelect.value) currencySelect.value = defaults.currency;
    });
    const updateCharacterCount = () => {
        if (characterSource && characterCount) characterCount.textContent = String(characterSource.value.length);
    };
    characterSource?.addEventListener("input", updateCharacterCount);
    updateCharacterCount();
    form.addEventListener("submit", (event) => {
        if (!form.checkValidity()) { event.preventDefault(); form.reportValidity(); return; }
        if (submitButton) submitButton.disabled = true;
        if (submitLabel) submitLabel.textContent = "Creating branch...";
    });
})();
