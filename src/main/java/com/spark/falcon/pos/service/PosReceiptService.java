package com.spark.falcon.pos.service;

import com.spark.falcon.sale.dto.SaleInvoiceResponse;
import com.spark.falcon.sale.service.SaleInvoiceReadService;
import com.spark.falcon.sale.service.SaleReceiptTextFormatter;
import com.spark.falcon.settings.dto.response.PrinterResponse;
import com.spark.falcon.settings.service.PrinterAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class PosReceiptService {

    private final PrinterAccessService printerAccessService;
    private final SaleInvoiceReadService saleInvoiceReadService;
    private final SaleReceiptTextFormatter receiptTextFormatter;

    public String print(Long ownerId, Long businessId, Long branchId, Long printerId, Long saleId) {
        PrinterResponse printer = printerAccessService.findActiveByBusinessAndBranch(businessId, branchId, printerId)
                .orElseThrow(() -> new IllegalStateException("Configured receipt printer is unavailable for this branch"));
        SaleInvoiceResponse invoice = saleInvoiceReadService.findInvoice(ownerId, branchId, saleId);
        String receipt = receiptTextFormatter.format(invoice, printer.charactersPerLine());
        printerAccessService.print(businessId, branchId, printerId, receipt.getBytes(StandardCharsets.UTF_8));
        return printer.title();
    }
}
