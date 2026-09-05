package com.spark.falcon.sale.service;

import com.spark.falcon.sale.dto.CustomerDueInvoiceResponse;
import com.spark.falcon.sale.dto.CustomerSaleFinancialSummaryResponse;
import com.spark.falcon.sale.dto.CustomerSaleStatementEntryResponse;
import com.spark.falcon.sale.entity.Sale;
import com.spark.falcon.sale.entity.SaleStatus;
import com.spark.falcon.sale.repository.SaleRepository;
import com.spark.falcon.sale.repository.SaleReturnRepository;
import com.spark.falcon.sale.entity.SaleReturn;
import com.spark.falcon.sale.entity.SaleReturnStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SaleCustomerFinancialReadServiceImpl implements SaleCustomerFinancialReadService {

    private static final int MONEY_SCALE = 4;
    private final SaleRepository saleRepository;
    private final SaleReturnRepository saleReturnRepository;

    @Override
    public CustomerSaleFinancialSummaryResponse summarizeCustomer(
            Long businessId, Long branchId, Long customerId, LocalDate asOfDate) {
        List<Sale> sales = postedSales(businessId, branchId, customerId);
        BigDecimal purchases = sum(sales, Sale::getTotalPayable);
        BigDecimal returns = sum(sales, Sale::getReturnedAmount);
        BigDecimal paid = sum(sales, Sale::getPaidAmount);
        BigDecimal due = sum(sales, Sale::getDueAmount);
        BigDecimal overdue = sales.stream().filter(sale -> isOverdue(sale, asOfDate))
                .map(Sale::getDueAmount).reduce(zero(), BigDecimal::add);
        BigDecimal customerCredit = saleReturnRepository.findCustomerFinancialReturns(
                        businessId, branchId, customerId).stream()
                .filter(value -> value.getStatus() == SaleReturnStatus.CONFIRMED)
                .map(SaleReturn::getCustomerCreditAmount).reduce(zero(), BigDecimal::add);
        Instant lastPurchaseAt = sales.stream().map(Sale::getConfirmedAt)
                .filter(value -> value != null).max(Comparator.naturalOrder()).orElse(null);
        return new CustomerSaleFinancialSummaryResponse(
                money(purchases), money(returns), money(paid), money(due), money(overdue),
                money(customerCredit), lastPurchaseAt);
    }

    @Override
    public List<CustomerDueInvoiceResponse> findCustomerInvoices(
            Long businessId, Long branchId, Long customerId, LocalDate asOfDate) {
        return postedSales(businessId, branchId, customerId).stream()
                .map(sale -> new CustomerDueInvoiceResponse(
                        sale.getId(), sale.getCustomerId(), invoiceInstant(sale), sale.getDueDate(),
                        sale.getTotalPayable(), sale.getPaidAmount(), sale.getReturnedAmount(), zero(), sale.getDueAmount(),
                        daysOverdue(sale, asOfDate), sale.getPaymentStatus()))
                .sorted(Comparator.comparing(CustomerDueInvoiceResponse::createdAt).reversed())
                .toList();
    }

    @Override
    public List<CustomerSaleStatementEntryResponse> findCustomerStatementEntries(
            Long businessId, Long branchId, Long customerId, Instant from, Instant to) {
        List<CustomerSaleStatementEntryResponse> entries = new ArrayList<>();
        for (Sale sale : postedSales(businessId, branchId, customerId)) {
            Instant transactionAt = invoiceInstant(sale);
            if (!within(transactionAt, from, to)) continue;
            entries.add(new CustomerSaleStatementEntryResponse(
                    transactionAt, "SALE-" + sale.getId(), sale.getBranchId(), "CONFIRMED_INVOICE",
                    sale.getTotalPayable(), zero(), "/owner/sales/" + sale.getId()));
        }
        for (SaleReturn saleReturn : saleReturnRepository.findCustomerFinancialReturns(
                businessId, branchId, customerId)) {
            if (saleReturn.getConfirmedAt() != null && within(saleReturn.getConfirmedAt(), from, to)) {
                entries.add(new CustomerSaleStatementEntryResponse(
                        saleReturn.getConfirmedAt(), saleReturn.getReferenceNumber(), saleReturn.getBranchId(),
                        "SALES_RETURN_OR_CREDIT", zero(), saleReturn.getTotalReturnAmount(),
                        "/owner/sales/returns/" + saleReturn.getId()));
            }
            if (saleReturn.getStatus() == SaleReturnStatus.REVERSED && saleReturn.getReversedAt() != null
                    && within(saleReturn.getReversedAt(), from, to)) {
                entries.add(new CustomerSaleStatementEntryResponse(
                        saleReturn.getReversedAt(), saleReturn.getReferenceNumber(), saleReturn.getBranchId(),
                        "SALES_RETURN_REVERSAL", saleReturn.getTotalReturnAmount(), zero(),
                        "/owner/sales/returns/" + saleReturn.getId()));
            }
        }
        return entries.stream().sorted(Comparator.comparing(CustomerSaleStatementEntryResponse::transactionAt))
                .toList();
    }

    private List<Sale> postedSales(Long businessId, Long branchId, Long customerId) {
        return saleRepository.findCustomerFinancialSales(businessId, branchId, customerId,
                List.of(SaleStatus.CONFIRMED, SaleStatus.PARTIALLY_RETURNED, SaleStatus.RETURNED));
    }

    private boolean isOverdue(Sale sale, LocalDate asOfDate) {
        return sale.getDueAmount().signum() > 0 && agingDate(sale).isBefore(asOfDate);
    }

    private long daysOverdue(Sale sale, LocalDate asOfDate) {
        return isOverdue(sale, asOfDate) ? ChronoUnit.DAYS.between(agingDate(sale), asOfDate) : 0L;
    }

    private LocalDate agingDate(Sale sale) {
        return sale.getDueDate() != null ? sale.getDueDate() : invoiceInstant(sale).atZone(ZoneOffset.UTC).toLocalDate();
    }

    private Instant invoiceInstant(Sale sale) {
        return sale.getConfirmedAt() == null ? sale.getCreatedAt() : sale.getConfirmedAt();
    }

    private boolean within(Instant value, Instant from, Instant to) {
        return (from == null || !value.isBefore(from)) && (to == null || value.isBefore(to));
    }

    private BigDecimal sum(List<Sale> sales, java.util.function.Function<Sale, BigDecimal> getter) {
        return sales.stream().map(getter).reduce(zero(), BigDecimal::add);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }
}
