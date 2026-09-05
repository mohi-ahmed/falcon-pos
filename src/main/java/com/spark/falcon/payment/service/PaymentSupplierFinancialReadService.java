package com.spark.falcon.payment.service;

import java.math.BigDecimal;

public interface PaymentSupplierFinancialReadService {
    BigDecimal confirmedSupplierAllocations(Long businessId, Long branchId, Long supplierId);
}
