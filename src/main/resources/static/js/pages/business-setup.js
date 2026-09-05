(function () {
    "use strict";

    var form = document.querySelector("[data-preview] #setupForm") || document.getElementById("setupForm");
    if (!form) return;

    var businessName = document.getElementById("businessName");
    var businessCode = document.getElementById("businessCode");
    var branchName = document.getElementById("branchName");
    var branchCode = document.getElementById("branchCode");
    var country = document.getElementById("country");
    var timezone = document.getElementById("timezone");
    var currency = document.getElementById("currency");
    var receiptFooter = document.getElementById("receiptFooter");
    var characterCount = document.querySelector("[data-character-count]");

    function codeFrom(value) {
        return value.trim().toUpperCase().replace(/[^A-Z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 20);
    }

    function fillCode(source, target) {
        if (!source || !target || target.dataset.edited === "true") return;
        target.value = codeFrom(source.value);
    }

    [businessCode, branchCode].forEach(function (input) {
        if (!input) return;
        input.addEventListener("input", function () {
            input.dataset.edited = input.value ? "true" : "false";
            input.value = codeFrom(input.value);
        });
    });

    if (businessName) businessName.addEventListener("input", function () {
        fillCode(businessName, businessCode);
        if (branchName && !branchName.value.trim()) branchName.placeholder = businessName.value ? businessName.value + " — Main Branch" : "e.g. Gulshan Branch";
    });
    if (branchName) branchName.addEventListener("input", function () { fillCode(branchName, branchCode); });

    var regionalDefaults = {
        BD: ["Asia/Dhaka", "BDT", "+880"], AE: ["Asia/Dubai", "AED", "+971"], SA: ["Asia/Riyadh", "SAR", "+966"],
        TR: ["Europe/Istanbul", "TRY", "+90"], GB: ["Europe/London", "GBP", "+44"], US: ["America/New_York", "USD", "+1"]
    };
    if (country) country.addEventListener("change", function () {
        var defaults = regionalDefaults[country.value];
        if (!defaults) return;
        timezone.value = defaults[0]; currency.value = defaults[1];
    });

    function updateCharacterCount() {
        if (characterCount && receiptFooter) characterCount.textContent = receiptFooter.value.length;
    }
    if (receiptFooter) { receiptFooter.addEventListener("input", updateCharacterCount); updateCharacterCount(); }

    function showError(field, message) {
        var wrapper = field.closest(".form-field");
        if (!wrapper) return;
        wrapper.classList.add("has-error");
        var error = wrapper.querySelector(".field-error");
        if (error) error.textContent = message;
    }

    function clearError(field) {
        var wrapper = field.closest(".form-field");
        if (!wrapper) return;
        wrapper.classList.remove("has-error");
        var error = wrapper.querySelector(".field-error");
        if (error) error.textContent = "";
    }

    form.querySelectorAll("input, select, textarea").forEach(function (field) {
        field.addEventListener("input", function () { clearError(field); });
        field.addEventListener("change", function () { clearError(field); });
    });

    form.addEventListener("submit", function (event) {
        var invalid = form.querySelector(":invalid");
        var confirmation = document.getElementById("confirmation");
        var confirmationError = document.querySelector(".confirmation-error");
        form.querySelectorAll(".has-error").forEach(function (node) { node.classList.remove("has-error"); });
        form.querySelectorAll(".form-field .field-error").forEach(function (node) { node.textContent = ""; });
        if (confirmationError) confirmationError.textContent = "";

        if (invalid) {
            event.preventDefault();
            if (invalid === confirmation) {
                if (confirmationError) confirmationError.textContent = "Please confirm the business details before continuing.";
            } else {
                var label = form.querySelector("label[for='" + invalid.id + "']");
                showError(invalid, (label ? label.textContent.replace("*", "").trim() : "This field") + " is required or invalid.");
            }
            invalid.focus();
        }
    });
})();
