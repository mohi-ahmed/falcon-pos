package com.spark.falcon.sale.service;

import com.spark.falcon.sale.dto.SaleInvoiceResponse;

public interface SaleInvoiceReadService {
    SaleInvoiceResponse findInvoice(Long ownerId, Long branchId, Long saleId);
}
