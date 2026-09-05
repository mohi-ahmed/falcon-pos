package com.spark.falcon.branch.validation;

import com.spark.falcon.branch.dto.request.CreateBranchRequest;
import com.spark.falcon.branch.dto.request.UpdateBranchRequest;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

@Component
public class BranchValidator {
    private static final Set<String> COUNTRIES = Set.of("BD", "AE", "SA", "TR", "GB", "US");
    private static final Set<String> CURRENCIES = Set.of("BDT", "AED", "SAR", "TRY", "GBP", "USD");
    private static final Set<Integer> PAGE_SIZES = Set.of(10, 25, 50, 100);

    public void validate(CreateBranchRequest request, Errors errors) {
        validateCommon(request.getCountry(), request.getTimeZone(), request.getRecordsPerPage(), errors);
        if (request.getCurrency() != null && !CURRENCIES.contains(request.getCurrency().toUpperCase(Locale.ROOT)))
            errors.rejectValue("currency", "branch.currency.unsupported", "Select a supported currency");
    }

    public void validate(UpdateBranchRequest request, Errors errors) {
        validateCommon(request.getCountry(), request.getTimeZone(), request.getRecordsPerPage(), errors);
    }

    private void validateCommon(String country, String timeZone, Integer recordsPerPage, Errors errors) {
        if (country != null && !COUNTRIES.contains(country.toUpperCase(Locale.ROOT)))
            errors.rejectValue("country", "branch.country.unsupported", "Select a supported country");
        if (recordsPerPage != null && !PAGE_SIZES.contains(recordsPerPage))
            errors.rejectValue("recordsPerPage", "branch.recordsPerPage.invalid", "Select a supported page size");
        if (timeZone != null && !timeZone.isBlank()) {
            try { ZoneId.of(timeZone); }
            catch (Exception exception) {
                errors.rejectValue("timeZone", "branch.timeZone.invalid", "Select a valid time zone");
            }
        }
    }
}
