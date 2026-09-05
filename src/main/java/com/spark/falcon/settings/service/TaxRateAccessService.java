package com.spark.falcon.settings.service;

import com.spark.falcon.settings.dto.response.TaxRateResponse;

import java.util.List;
import java.util.Optional;

public interface TaxRateAccessService {
    Optional<TaxRateResponse> findActive(Long businessId, Long taxRateId);
    List<TaxRateResponse> findActive(Long businessId);
}
