package com.spark.falcon.customer.service;

import com.spark.falcon.customer.dto.CustomerAccessResponse;
import com.spark.falcon.customer.dto.CustomerDuplicateCandidateResponse;
import com.spark.falcon.customer.dto.CustomerRequest;
import com.spark.falcon.customer.dto.CustomerResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CustomerAccessService {

    Optional<CustomerAccessResponse> findActiveByBusinessIdAndCustomerId(Long businessId, Long customerId);

    Optional<CustomerAccessResponse> findByBusinessIdAndCustomerId(Long businessId, Long customerId);

    List<CustomerAccessResponse> searchActive(Long businessId, String keyword, int limit);

    Map<Long, CustomerAccessResponse> findByBusinessIdAndCustomerIds(Long businessId, Collection<Long> customerIds);

    CustomerResponse createForBusiness(Long businessId, CustomerRequest request);

    List<CustomerDuplicateCandidateResponse> findProbableDuplicatesByBusiness(
            Long businessId,
            String phone,
            String email,
            Long excludeCustomerId
    );

    CustomerAccessResponse getOrCreateWalkInCustomer(Long businessId);

    boolean isEligibleForDueSale(Long businessId, Long customerId);

    CustomerAccessResponse requireDueEligibleCustomer(Long businessId, Long customerId);
}
