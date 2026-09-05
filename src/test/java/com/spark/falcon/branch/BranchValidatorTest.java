package com.spark.falcon.branch;

import com.spark.falcon.branch.dto.request.CreateBranchRequest;
import com.spark.falcon.branch.validation.BranchValidator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import static org.assertj.core.api.Assertions.assertThat;

class BranchValidatorTest {
    private final BranchValidator validator = new BranchValidator();

    @Test
    void rejectsUnsupportedOperationalContext() {
        CreateBranchRequest request = new CreateBranchRequest();
        request.setCountry("XX");
        request.setCurrency("XYZ");
        request.setTimeZone("Invalid/Zone");
        request.setRecordsPerPage(13);
        Errors errors = new BeanPropertyBindingResult(request, "branchRequest");

        validator.validate(request, errors);

        assertThat(errors.getFieldErrorCount()).isEqualTo(4);
    }
}
