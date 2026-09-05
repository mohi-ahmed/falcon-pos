package com.spark.falcon.settings.service;

import com.spark.falcon.settings.dto.response.PaymentMethodResponse;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodAccessService {
    Optional<PaymentMethodResponse> findActiveForBranch(Long businessId, Long branchId, Long paymentMethodId);
    List<PaymentMethodResponse> findActiveForBranch(Long businessId, Long branchId);
}
