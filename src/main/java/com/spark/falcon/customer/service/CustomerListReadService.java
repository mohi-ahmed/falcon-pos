package com.spark.falcon.customer.service;

import com.spark.falcon.customer.dto.CustomerFinancialSummaryResponse;
import com.spark.falcon.customer.dto.CustomerListFilter;
import com.spark.falcon.customer.dto.CustomerListRowResponse;
import com.spark.falcon.customer.dto.CustomerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomerListReadService {

    private final CustomerService customerService;
    private final CustomerFinancialReadService financialReadService;

    public Page<CustomerListRowResponse> find(Long ownerId, Long branchId, CustomerListFilter filter,
                                               Pageable pageable, LocalDate asOfDate) {
        String keyword = filter.keyword() == null ? "" : filter.keyword().trim().toLowerCase(Locale.ROOT);
        if (!requiresFinancialScan(filter, keyword)) {
            Pageable profilePage = org.springframework.data.domain.PageRequest.of(
                    pageable.getPageNumber(), pageable.getPageSize(), profileSort(filter.sort()));
            return customerService.findAll(ownerId, keyword, filter.archived(), profilePage)
                    .map(customer -> new CustomerListRowResponse(customer,
                            financialReadService.summarize(ownerId, branchId, customer.id(), asOfDate)));
        }
        Set<Long> invoiceCustomerIds = invoiceCustomerIds(ownerId, branchId, keyword, asOfDate);
        List<CustomerListRowResponse> rows = new ArrayList<>();
        for (CustomerResponse customer : customerService.findAll(
                ownerId, "", filter.archived(), Pageable.unpaged()).getContent()) {
            if (!matchesProfile(customer, keyword, invoiceCustomerIds)) continue;
            if (!matchesCreated(customer, filter.createdFrom(), filter.createdTo())) continue;
            CustomerFinancialSummaryResponse financial = financialReadService.summarize(
                    ownerId, branchId, customer.id(), asOfDate);
            if (!matchesFinancial(financial, filter)) continue;
            rows.add(new CustomerListRowResponse(customer, financial));
        }
        rows.sort(comparator(filter.sort()));
        int from = Math.min((int) pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        return new PageImpl<>(List.copyOf(rows.subList(from, to)), pageable, rows.size());
    }

    private boolean requiresFinancialScan(CustomerListFilter filter, String keyword) {
        String sort = filter.sort() == null ? "created-desc" : filter.sort().toLowerCase(Locale.ROOT);
        return number(keyword) != null || filter.hasDue() != null || filter.overdue() != null
                || filter.createdFrom() != null || filter.createdTo() != null
                || filter.lastPurchaseFrom() != null || filter.lastPurchaseTo() != null
                || "due-desc".equals(sort) || "last-purchase-desc".equals(sort);
    }

    private Sort profileSort(String requested) {
        String sort = requested == null ? "created-desc" : requested.toLowerCase(Locale.ROOT);
        return switch (sort) {
            case "created-asc" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "name-asc" -> Sort.by(Sort.Direction.ASC, "name");
            case "name-desc" -> Sort.by(Sort.Direction.DESC, "name");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    private Set<Long> invoiceCustomerIds(Long ownerId, Long branchId, String keyword, LocalDate asOfDate) {
        Long invoiceId = number(keyword);
        if (invoiceId == null) return Set.of();
        Set<Long> result = new HashSet<>();
        financialReadService.invoices(ownerId, branchId, null, asOfDate, null).stream()
                .filter(invoice -> invoice.id().equals(invoiceId))
                .map(invoice -> invoice.customerId())
                .filter(value -> value != null)
                .forEach(result::add);
        return result;
    }

    private boolean matchesProfile(CustomerResponse customer, String keyword, Set<Long> invoiceCustomerIds) {
        if (keyword.isBlank()) return true;
        return String.valueOf(customer.id()).equals(keyword)
                || contains(customer.name(), keyword) || contains(customer.phone(), keyword)
                || contains(customer.email(), keyword) || invoiceCustomerIds.contains(customer.id());
    }

    private boolean matchesCreated(CustomerResponse customer, LocalDate from, LocalDate to) {
        LocalDate created = customer.createdAt().atZone(ZoneOffset.UTC).toLocalDate();
        return (from == null || !created.isBefore(from)) && (to == null || !created.isAfter(to));
    }

    private boolean matchesFinancial(CustomerFinancialSummaryResponse financial, CustomerListFilter filter) {
        if (filter.hasDue() != null && (financial.outstandingDue().signum() > 0) != filter.hasDue()) return false;
        if (filter.overdue() != null && (financial.overdueAmount().signum() > 0) != filter.overdue()) return false;
        if (filter.lastPurchaseFrom() == null && filter.lastPurchaseTo() == null) return true;
        if (financial.lastPurchaseAt() == null) return false;
        LocalDate lastPurchase = financial.lastPurchaseAt().atZone(ZoneOffset.UTC).toLocalDate();
        return (filter.lastPurchaseFrom() == null || !lastPurchase.isBefore(filter.lastPurchaseFrom()))
                && (filter.lastPurchaseTo() == null || !lastPurchase.isAfter(filter.lastPurchaseTo()));
    }

    private Comparator<CustomerListRowResponse> comparator(String requested) {
        String sort = requested == null ? "created-desc" : requested.toLowerCase(Locale.ROOT);
        Comparator<CustomerListRowResponse> byName = Comparator.comparing(
                row -> value(row.customer().name()), String.CASE_INSENSITIVE_ORDER);
        Comparator<CustomerListRowResponse> byCreated = Comparator.comparing(row -> row.customer().createdAt());
        Comparator<CustomerListRowResponse> byDue = Comparator.comparing(
                row -> amount(row.financial().outstandingDue()));
        Comparator<CustomerListRowResponse> byPurchaseDesc = Comparator.comparing(
                row -> row.financial().lastPurchaseAt(), Comparator.nullsLast(Comparator.reverseOrder()));
        return switch (sort) {
            case "created-asc" -> byCreated.thenComparing(row -> row.customer().id());
            case "name-asc" -> byName.thenComparing(row -> row.customer().id());
            case "name-desc" -> byName.reversed().thenComparing(row -> row.customer().id());
            case "due-desc" -> byDue.reversed().thenComparing(row -> row.customer().id());
            case "last-purchase-desc" -> byPurchaseDesc.thenComparing(row -> row.customer().id());
            default -> byCreated.reversed().thenComparing(row -> row.customer().id());
        };
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long number(String value) {
        try { return value.isBlank() ? null : Long.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }
}
