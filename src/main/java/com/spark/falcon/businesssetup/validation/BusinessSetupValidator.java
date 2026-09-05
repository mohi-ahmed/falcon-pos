package com.spark.falcon.businesssetup.validation;
import com.spark.falcon.businesssetup.dto.request.BusinessSetupRequest;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import java.time.ZoneId;
import java.util.Set;
@Component
public class BusinessSetupValidator {
    private static final Set<String> COUNTRIES = Set.of("BD", "AE", "SA", "TR", "GB", "US");
    private static final Set<String> CURRENCIES = Set.of("BDT", "AED", "SAR", "TRY", "GBP", "USD");
    private static final Set<Integer> PAGE_SIZES = Set.of(10, 25, 50, 100);
    public void validate(BusinessSetupRequest request, Errors errors) {
        if (request.getCountry() != null && !COUNTRIES.contains(request.getCountry()))
            errors.rejectValue("country", "country.unsupported", "Select a supported country");
        if (request.getCurrency() != null && !CURRENCIES.contains(request.getCurrency()))
            errors.rejectValue("currency", "currency.unsupported", "Select a supported currency");
        if (request.getRecordsPerPage() != null && !PAGE_SIZES.contains(request.getRecordsPerPage()))
            errors.rejectValue("recordsPerPage", "records.invalid", "Select a supported page size");
        if (request.getTimezone() != null) try { ZoneId.of(request.getTimezone()); }
        catch (Exception ignored) { errors.rejectValue("timezone", "timezone.invalid", "Select a valid time zone"); }
    }
}
