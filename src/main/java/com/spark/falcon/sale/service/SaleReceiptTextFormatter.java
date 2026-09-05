package com.spark.falcon.sale.service;

import com.spark.falcon.sale.dto.SaleInvoiceItemResponse;
import com.spark.falcon.sale.dto.SaleInvoicePaymentLine;
import com.spark.falcon.sale.dto.SaleInvoiceResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

@Component
public class SaleReceiptTextFormatter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    public String format(SaleInvoiceResponse invoice, int configuredWidth) {
        int width = Math.max(24, Math.min(configuredWidth, 64));
        String line = "-".repeat(width);
        StringBuilder out = new StringBuilder();

        append(out, center(invoice.businessName(), width));
        append(out, center(invoice.branchName(), width));
        if (notBlank(invoice.branchPhone())) append(out, center(invoice.branchPhone(), width));
        if (notBlank(invoice.vatBinNumber())) append(out, center("VAT/BIN: " + invoice.vatBinNumber(), width));
        append(out, line);
        append(out, pair("Invoice", "#" + invoice.saleId(), width));
        append(out, pair("Currency", invoice.currency(), width));
        if (invoice.issuedAt() != null) append(out, pair("Date", DATE_TIME.format(invoice.issuedAt()), width));
        append(out, pair("Customer", invoice.customerName(), width));
        if (notBlank(invoice.customerPhone())) append(out, pair("Phone", invoice.customerPhone(), width));
        append(out, line);

        for (SaleInvoiceItemResponse item : invoice.items()) {
            String name = item.productName();
            if (notBlank(item.variantName()) && !item.variantName().equalsIgnoreCase(item.productName())) {
                name += " - " + item.variantName();
            }
            appendWrapped(out, name, width);
            String quantityPrice = quantity(item.quantity()) + " x " + money(item.unitPrice());
            append(out, pair(quantityPrice, money(item.linePayable()), width));
            if (positive(item.discountAmount())) append(out, pair("  Discount", "-" + money(item.discountAmount()), width));
            if (positive(item.taxAmount())) append(out, pair("  Tax", money(item.taxAmount()), width));
        }

        append(out, line);
        append(out, pair("Subtotal", money(invoice.grossItemTotal()), width));
        if (positive(invoice.itemDiscountTotal())) append(out, pair("Item discount", "-" + money(invoice.itemDiscountTotal()), width));
        if (positive(invoice.itemTaxTotal())) append(out, pair("Tax", money(invoice.itemTaxTotal()), width));
        if (positive(invoice.orderDiscount())) append(out, pair("Order discount", "-" + money(invoice.orderDiscount()), width));
        if (positive(invoice.shippingCharge())) append(out, pair("Shipping", money(invoice.shippingCharge()), width));
        if (positive(invoice.otherCharge())) append(out, pair("Other charge", money(invoice.otherCharge()), width));
        append(out, "=".repeat(width));
        append(out, pair("TOTAL", money(invoice.totalPayable()), width));
        append(out, pair("Paid", money(invoice.paidAmount()), width));
        if (positive(invoice.returnedAmount())) append(out, pair("Returned", money(invoice.returnedAmount()), width));
        append(out, pair("Due", money(invoice.dueAmount()), width));
        append(out, pair("Change", money(invoice.changeAmount()), width));

        if (!invoice.payments().isEmpty()) {
            append(out, line);
            append(out, "PAYMENTS");
            for (SaleInvoicePaymentLine payment : invoice.payments()) {
                append(out, pair(payment.paymentMethodName(), money(payment.amount()), width));
                if (notBlank(payment.transactionReference())) {
                    appendWrapped(out, "Ref: " + payment.transactionReference(), width);
                }
            }
        }

        append(out, line);
        append(out, center(invoice.paymentStatus().name().replace('_', ' '), width));
        if (notBlank(invoice.receiptFooter())) {
            append(out, line);
            appendWrapped(out, invoice.receiptFooter(), width);
        }
        append(out, "");
        append(out, "");
        return out.toString();
    }

    private String pair(String leftValue, String rightValue, int width) {
        String left = leftValue == null ? "" : leftValue;
        String right = rightValue == null ? "" : rightValue;
        if (left.length() + right.length() + 1 > width) {
            int maxLeft = Math.max(1, width - right.length() - 1);
            left = left.substring(0, Math.min(left.length(), maxLeft));
        }
        int spaces = Math.max(1, width - left.length() - right.length());
        return left + " ".repeat(spaces) + right;
    }

    private void appendWrapped(StringBuilder out, String value, int width) {
        if (!notBlank(value)) return;
        String remaining = value.trim();
        while (remaining.length() > width) {
            int breakAt = remaining.lastIndexOf(' ', width);
            if (breakAt <= 0) breakAt = width;
            append(out, remaining.substring(0, breakAt).trim());
            remaining = remaining.substring(breakAt).trim();
        }
        if (!remaining.isEmpty()) append(out, remaining);
    }

    private String center(String value, int width) {
        if (!notBlank(value)) return "";
        String trimmed = value.trim();
        if (trimmed.length() >= width) return trimmed.substring(0, width);
        int left = Math.max(0, (width - trimmed.length()) / 2);
        return " ".repeat(left) + trimmed;
    }

    private String money(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return safe.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String quantity(BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private void append(StringBuilder out, String value) {
        out.append(value == null ? "" : value).append('\n');
    }
}
