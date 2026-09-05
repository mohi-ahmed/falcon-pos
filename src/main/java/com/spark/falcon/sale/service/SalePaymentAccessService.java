package com.spark.falcon.sale.service;

import com.spark.falcon.sale.dto.SalePaymentAllocationResult;
import com.spark.falcon.sale.dto.SalePaymentInvoiceResponse;

import java.math.BigDecimal;
import java.util.List;

public interface SalePaymentAccessService {

    SalePaymentInvoiceResponse lockEligibleCustomerInvoice(
            Long businessId,
            Long branchId,
            Long customerId,
            Long saleId
    );

    List<SalePaymentInvoiceResponse> findEligibleCustomerInvoicesOldestFirst(
            Long businessId,
            Long branchId,
            Long customerId
    );

    SalePaymentAllocationResult applyCustomerPayment(
            Long businessId,
            Long branchId,
            Long customerId,
            Long saleId,
            BigDecimal amount,
            Long paymentId,
            Long actorId
    );

    SalePaymentAllocationResult reverseCustomerPayment(
            Long businessId,
            Long branchId,
            Long customerId,
            Long saleId,
            BigDecimal amount,
            Long reversalPaymentId,
            Long actorId
    );
}
