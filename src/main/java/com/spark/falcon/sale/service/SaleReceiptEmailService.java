package com.spark.falcon.sale.service;

import com.spark.falcon.sale.dto.SaleInvoiceResponse;
import com.spark.falcon.sale.entity.SaleStatus;
import com.spark.falcon.sale.exception.SaleValidationException;
import com.spark.falcon.sale.notification.SaleReceiptEmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SaleReceiptEmailService {

    private static final int EMAIL_RECEIPT_WIDTH = 48;

    private final SaleInvoiceReadService saleInvoiceReadService;
    private final SaleReceiptTextFormatter receiptTextFormatter;
    private final SaleReceiptEmailSender emailSender;

    public String send(Long ownerId, Long branchId, Long saleId) {
        SaleInvoiceResponse invoice = saleInvoiceReadService.findInvoice(ownerId, branchId, saleId);
        if (invoice.saleStatus() == SaleStatus.DRAFT || invoice.saleStatus() == SaleStatus.HELD) {
            throw new SaleValidationException("Only a posted Sale can be emailed as a receipt.");
        }
        String recipient = clean(invoice.customerEmail());
        if (recipient == null) {
            throw new SaleValidationException("This sale does not have a customer email address. Add or update the customer email before sending a receipt.");
        }
        String businessName = clean(invoice.businessName());
        String subject = (businessName == null ? "Falcon POS" : businessName) + " receipt · Sale #" + invoice.saleId();
        emailSender.send(recipient, subject, receiptTextFormatter.format(invoice, EMAIL_RECEIPT_WIDTH));
        return recipient;
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
