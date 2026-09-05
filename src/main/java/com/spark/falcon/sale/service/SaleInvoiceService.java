package com.spark.falcon.sale.service;

import com.spark.falcon.branch.dto.response.BranchAccessResponse;
import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.customer.dto.CustomerAccessResponse;
import com.spark.falcon.customer.service.CustomerAccessService;
import com.spark.falcon.payment.dto.SaleReceiptPaymentResponse;
import com.spark.falcon.payment.service.SalePaymentReadService;
import com.spark.falcon.sale.dto.SaleInvoiceItemResponse;
import com.spark.falcon.sale.dto.SaleInvoicePaymentLine;
import com.spark.falcon.sale.dto.SaleInvoiceResponse;
import com.spark.falcon.sale.dto.SaleResponse;
import com.spark.falcon.sale.exception.SaleAccessDeniedException;
import com.spark.falcon.settings.dto.response.BranchSettingsResponse;
import com.spark.falcon.settings.service.BranchSettingsAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class SaleInvoiceService implements SaleInvoiceReadService {

    private final SaleService saleService;
    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final CustomerAccessService customerAccessService;
    private final BranchSettingsAccessService branchSettingsAccessService;
    private final SalePaymentReadService salePaymentReadService;

    @Override
    @Transactional(readOnly = true)
    public SaleInvoiceResponse findInvoice(Long ownerId, Long branchId, Long saleId) {
        SaleResponse sale = saleService.findDetails(ownerId, branchId, saleId);
        BusinessAccessResponse business = businessAccessService.findByOwnerId(ownerId)
                .filter(value -> value.businessId().equals(sale.businessId()))
                .orElseThrow(SaleAccessDeniedException::new);
        BranchAccessResponse branch = branchAccessService
                .findByBusinessIdAndBranchId(sale.businessId(), branchId)
                .orElseThrow(SaleAccessDeniedException::new);
        BranchSettingsResponse settings = branchSettingsAccessService
                .findByBusinessIdAndBranchId(sale.businessId(), branchId)
                .orElse(null);
        CustomerAccessResponse customer = sale.customerId() == null ? null
                : customerAccessService.findByBusinessIdAndCustomerId(sale.businessId(), sale.customerId()).orElse(null);
        ZoneId zone = safeZone(branch.timeZone());

        List<SaleInvoiceItemResponse> items = sale.items().stream()
                .map(item -> new SaleInvoiceItemResponse(
                        item.productName(), item.variantName(), item.productCode(), item.enteredQuantity(),
                        item.unitPrice(), item.discountAmount(), item.taxAmount(), item.linePayable()))
                .toList();
        List<SaleInvoicePaymentLine> payments = salePaymentReadService
                .findReceiptPaymentsForSale(sale.businessId(), branchId, saleId).stream()
                .map(payment -> paymentLine(payment, zone))
                .toList();

        return new SaleInvoiceResponse(
                sale.id(), branchId, business.businessName(), branch.branchName(), branch.branchCode(), branch.currency(),
                settings == null ? null : settings.businessLogoReference(),
                settings == null ? null : settings.phone(),
                settings == null ? null : settings.email(),
                address(settings), settings == null ? null : settings.vatBinNumber(),
                customerName(sale, customer),
                customer == null ? null : customer.phone(),
                customer == null ? null : customer.email(),
                sale.status(), sale.paymentStatus(), issuedAt(sale, zone), sale.dueDate(), items,
                sale.grossItemTotal(), sale.itemDiscountTotal(), sale.itemTaxTotal(), sale.orderDiscount(),
                sale.shippingCharge(), sale.otherCharge(), sale.totalPayable(), sale.paidAmount(), sale.dueAmount(),
                sale.changeAmount(), sale.returnedAmount(), payments, settings == null ? null : settings.receiptFooter());
    }


    private String customerName(SaleResponse sale, CustomerAccessResponse customer) {
        if (customer != null && customer.name() != null && !customer.name().isBlank()) return customer.name();
        return sale.customerId() == null ? "Walk-in Customer" : "Customer";
    }

    private SaleInvoicePaymentLine paymentLine(SaleReceiptPaymentResponse payment, ZoneId zone) {
        return new SaleInvoicePaymentLine(payment.paymentId(), payment.paymentMethodName(), payment.amount(),
                payment.transactionReference(), payment.confirmedAt() == null ? null : payment.confirmedAt().atZone(zone));
    }

    private ZonedDateTime issuedAt(SaleResponse sale, ZoneId zone) {
        Instant value = sale.confirmedAt() != null ? sale.confirmedAt() : sale.createdAt();
        return value == null ? null : value.atZone(zone);
    }

    private String address(BranchSettingsResponse settings) {
        if (settings == null) return null;
        String joined = String.join(", ", Stream.of(
                        settings.address(), settings.city(), settings.stateDivision(), settings.postalCode(), settings.country())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList());
        return joined.isBlank() ? null : joined;
    }

    private ZoneId safeZone(String value) {
        try {
            return value == null || value.isBlank() ? ZoneId.systemDefault() : ZoneId.of(value);
        } catch (RuntimeException exception) {
            return ZoneId.systemDefault();
        }
    }
}
