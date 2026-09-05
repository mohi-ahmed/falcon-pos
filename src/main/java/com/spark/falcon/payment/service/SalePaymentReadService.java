package com.spark.falcon.payment.service;

import com.spark.falcon.payment.dto.SaleReceiptPaymentResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface SalePaymentReadService {
    String paymentMethodSummaryForSale(Long businessId, Long branchId, Long saleId);

    Map<Long, String> paymentMethodSummaryForSales(Long businessId, Long branchId, Collection<Long> saleIds);

    List<Long> findSaleIdsByPaymentMethodKeyword(Long businessId, Long branchId, String keyword);

    boolean hasConfirmedCustomerPayment(Long businessId, Long branchId, Long saleId);

    List<SaleReceiptPaymentResponse> findReceiptPaymentsForSale(Long businessId, Long branchId, Long saleId);
}
