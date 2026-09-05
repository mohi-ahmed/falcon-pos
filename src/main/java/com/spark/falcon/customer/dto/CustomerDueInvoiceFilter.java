package com.spark.falcon.customer.dto;

import java.time.LocalDate;

public record CustomerDueInvoiceFilter(String status, LocalDate invoiceFrom, LocalDate invoiceTo,
                                       LocalDate dueFrom, LocalDate dueTo, String agingBucket) {
}
