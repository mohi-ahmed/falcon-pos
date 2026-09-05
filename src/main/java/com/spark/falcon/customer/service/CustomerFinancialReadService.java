package com.spark.falcon.customer.service;

import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.customer.dto.CustomerFinancialSummaryResponse;
import com.spark.falcon.customer.dto.CustomerStatementEntryResponse;
import com.spark.falcon.customer.dto.CustomerDueInvoiceFilter;
import com.spark.falcon.customer.exception.CustomerAccessDeniedException;
import com.spark.falcon.customer.exception.CustomerNotFoundException;
import com.spark.falcon.payment.service.PaymentCustomerFinancialReadService;
import com.spark.falcon.sale.dto.CustomerDueInvoiceResponse;
import com.spark.falcon.sale.service.SaleCustomerFinancialReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerFinancialReadService {

    private static final int MONEY_SCALE = 4;
    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final CustomerAccessService customerAccessService;
    private final SaleCustomerFinancialReadService saleReadService;
    private final PaymentCustomerFinancialReadService paymentReadService;

    public CustomerFinancialSummaryResponse summarize(
            Long ownerId, Long branchId, Long customerId, LocalDate asOfDate) {
        Long businessId = authorize(ownerId, branchId, customerId);
        var sales = saleReadService.summarizeCustomer(businessId, branchId, customerId, asOfDate);
        return new CustomerFinancialSummaryResponse(
                customerId, sales.totalConfirmedPurchases(), sales.totalSalesReturns(), sales.totalPaid(),
                sales.outstandingDue(), sales.overdueAmount(),
                sales.customerCredit().add(
                        paymentReadService.availableCustomerCredit(businessId, branchId, customerId)),
                sales.lastPurchaseAt());
    }

    public List<CustomerDueInvoiceResponse> invoices(
            Long ownerId, Long branchId, Long customerId, LocalDate asOfDate,
            CustomerDueInvoiceFilter filter) {
        Long businessId = authorize(ownerId, branchId, customerId);
        var reversals = paymentReadService.customerPaymentReversalsByInvoice(businessId, branchId, customerId);
        CustomerDueInvoiceFilter safe = filter == null
                ? new CustomerDueInvoiceFilter(null, null, null, null, null, null) : filter;
        return saleReadService.findCustomerInvoices(businessId, branchId, customerId, asOfDate).stream()
                .filter(value -> matchesInvoiceStatus(value, safe.status()))
                .filter(value -> safe.invoiceFrom() == null || !invoiceDate(value).isBefore(safe.invoiceFrom()))
                .filter(value -> safe.invoiceTo() == null || !invoiceDate(value).isAfter(safe.invoiceTo()))
                .filter(value -> safe.dueFrom() == null
                        || (value.dueDate() != null && !value.dueDate().isBefore(safe.dueFrom())))
                .filter(value -> safe.dueTo() == null
                        || (value.dueDate() != null && !value.dueDate().isAfter(safe.dueTo())))
                .filter(value -> matchesAgingBucket(value.daysOverdue(), safe.agingBucket()))
                .map(value -> new CustomerDueInvoiceResponse(
                        value.id(), value.customerId(), value.createdAt(), value.dueDate(), value.totalPayable(),
                        value.paidAmount(), value.returnedAmount(), reversals.getOrDefault(value.id(), zero()),
                        value.dueAmount(), value.daysOverdue(), value.paymentStatus()))
                .toList();
    }

    private LocalDate invoiceDate(CustomerDueInvoiceResponse value) {
        return value.createdAt().atZone(java.time.ZoneOffset.UTC).toLocalDate();
    }

    private boolean matchesInvoiceStatus(CustomerDueInvoiceResponse value, String requested) {
        if (requested == null || requested.isBlank() || "ALL".equalsIgnoreCase(requested)) return true;
        return switch (requested.trim().toUpperCase()) {
            case "UNPAID" -> value.paymentStatus() == com.spark.falcon.sale.entity.SalePaymentStatus.UNPAID;
            case "PARTIALLY_PAID" -> value.paymentStatus() == com.spark.falcon.sale.entity.SalePaymentStatus.PARTIALLY_PAID;
            case "PAID" -> value.paymentStatus() == com.spark.falcon.sale.entity.SalePaymentStatus.PAID;
            case "OVERDUE" -> value.daysOverdue() > 0 && value.dueAmount().signum() > 0;
            default -> true;
        };
    }

    private boolean matchesAgingBucket(long daysOverdue, String requested) {
        if (requested == null || requested.isBlank() || "ALL".equalsIgnoreCase(requested)) return true;
        return switch (requested.trim().toUpperCase()) {
            case "CURRENT" -> daysOverdue == 0;
            case "1_30" -> daysOverdue >= 1 && daysOverdue <= 30;
            case "31_60" -> daysOverdue >= 31 && daysOverdue <= 60;
            case "61_90" -> daysOverdue >= 61 && daysOverdue <= 90;
            case "OVER_90" -> daysOverdue > 90;
            default -> true;
        };
    }

    public List<CustomerStatementEntryResponse> statement(
            Long ownerId, Long branchId, Long customerId, Instant from, Instant to) {
        Long businessId = authorize(ownerId, branchId, customerId);
        List<RawEntry> entries = new ArrayList<>();
        saleReadService.findCustomerStatementEntries(businessId, branchId, customerId, from, to)
                .forEach(value -> entries.add(new RawEntry(value.transactionAt(), value.reference(), value.branchId(),
                        value.type(), value.debit(), value.credit(), value.sourcePath())));
        paymentReadService.findCustomerStatementEntries(businessId, branchId, customerId, from, to)
                .forEach(value -> entries.add(new RawEntry(value.transactionAt(), value.reference(), value.branchId(),
                        value.type(), value.debit(), value.credit(), value.sourcePath())));
        entries.sort(Comparator.comparing(RawEntry::transactionAt).thenComparing(RawEntry::reference));
        BigDecimal running = zero();
        List<CustomerStatementEntryResponse> result = new ArrayList<>();
        for (RawEntry entry : entries) {
            running = running.add(entry.debit()).subtract(entry.credit()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            result.add(new CustomerStatementEntryResponse(
                    entry.transactionAt(), entry.reference(), entry.branchId(), entry.type(), entry.debit(),
                    entry.credit(), running, entry.sourcePath()));
        }
        return List.copyOf(result);
    }

    private Long authorize(Long ownerId, Long branchId, Long customerId) {
        BusinessAccessResponse business = businessAccessService.findByOwnerId(ownerId)
                .orElseThrow(CustomerAccessDeniedException::new);
        branchAccessService.findActiveByBusinessIdAndBranchId(business.businessId(), branchId)
                .orElseThrow(CustomerAccessDeniedException::new);
        if (customerId != null) {
            customerAccessService.findByBusinessIdAndCustomerId(business.businessId(), customerId)
                    .orElseThrow(CustomerNotFoundException::new);
        }
        return business.businessId();
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private record RawEntry(Instant transactionAt, String reference, Long branchId, String type,
                            BigDecimal debit, BigDecimal credit, String sourcePath) {
    }
}
