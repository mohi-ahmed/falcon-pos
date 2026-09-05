package com.spark.falcon.payment.service;

import com.spark.falcon.payment.dto.CustomerPaymentStatementEntryResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface PaymentCustomerFinancialReadService {
    BigDecimal availableCustomerCredit(Long businessId, Long branchId, Long customerId);

    List<CustomerPaymentStatementEntryResponse> findCustomerStatementEntries(
            Long businessId, Long branchId, Long customerId, Instant from, Instant to);

    Map<Long, BigDecimal> customerPaymentReversalsByInvoice(
            Long businessId, Long branchId, Long customerId);
}
