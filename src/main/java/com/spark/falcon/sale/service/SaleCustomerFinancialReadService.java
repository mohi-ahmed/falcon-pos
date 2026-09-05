package com.spark.falcon.sale.service;

import com.spark.falcon.sale.dto.CustomerDueInvoiceResponse;
import com.spark.falcon.sale.dto.CustomerSaleFinancialSummaryResponse;
import com.spark.falcon.sale.dto.CustomerSaleStatementEntryResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface SaleCustomerFinancialReadService {
    CustomerSaleFinancialSummaryResponse summarizeCustomer(
            Long businessId, Long branchId, Long customerId, LocalDate asOfDate);

    List<CustomerDueInvoiceResponse> findCustomerInvoices(
            Long businessId, Long branchId, Long customerId, LocalDate asOfDate);

    List<CustomerSaleStatementEntryResponse> findCustomerStatementEntries(
            Long businessId, Long branchId, Long customerId, Instant from, Instant to);
}
