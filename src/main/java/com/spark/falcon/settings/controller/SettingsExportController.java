package com.spark.falcon.settings.controller;

import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.settings.service.*;
import com.spark.falcon.shared.export.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class SettingsExportController {
    private final BranchContextService branchContextService;
    private final PaymentMethodService paymentMethodService;
    private final UnitService unitService;
    private final TaxRateService taxRateService;
    private final PrinterService printerService;
    private final ExportResponse exportResponse;

    @GetMapping("/owner/settings/payment-methods/export")
    public void paymentMethods(@RequestParam String format, @AuthenticationPrincipal OwnerPrincipal principal,
                               HttpServletResponse response) throws IOException {
        BusinessSetupResponse setup = setup(principal);
        var rows = paymentMethodService.findAll(principal.ownerId());
        write(format, setup, "Payment Method List",
                List.of(ExportColumn.number("ID"), ExportColumn.text("Name"), ExportColumn.text("Code"),
                        ExportColumn.number("Display Order"), ExportColumn.text("Description"), ExportColumn.text("Status")),
                consumer -> { for (var row : rows) consumer.accept(row.id(), row.name(), row.code(), row.displayOrder(), row.description(), row.status()); }, response);
    }

    @GetMapping("/owner/settings/units/export")
    public void units(@RequestParam String format, @AuthenticationPrincipal OwnerPrincipal principal,
                      HttpServletResponse response) throws IOException {
        BusinessSetupResponse setup = setup(principal);
        var rows = unitService.findAll(principal.ownerId());
        write(format, setup, "Unit List",
                List.of(ExportColumn.number("ID"), ExportColumn.text("Name"), ExportColumn.text("Code"),
                        ExportColumn.text("Description"), ExportColumn.number("Display Order"), ExportColumn.text("Status")),
                consumer -> { for (var row : rows) consumer.accept(row.id(), row.name(), row.code(), row.description(), row.displayOrder(), row.status()); }, response);
    }

    @GetMapping("/owner/settings/taxes/export")
    public void taxes(@RequestParam String format, @AuthenticationPrincipal OwnerPrincipal principal,
                      HttpServletResponse response) throws IOException {
        BusinessSetupResponse setup = setup(principal);
        var rows = taxRateService.findAll(principal.ownerId());
        write(format, setup, "Tax Rate List",
                List.of(ExportColumn.number("ID"), ExportColumn.text("Name"), ExportColumn.text("Code"),
                        ExportColumn.number("Percentage"), ExportColumn.number("Display Order"), ExportColumn.text("Status")),
                consumer -> { for (var row : rows) consumer.accept(row.id(), row.name(), row.code(), row.rate(), row.displayOrder(), row.status()); }, response);
    }

    @GetMapping("/owner/settings/printers/export")
    public void printers(@RequestParam String format, @AuthenticationPrincipal OwnerPrincipal principal,
                         HttpServletResponse response) throws IOException {
        BusinessSetupResponse setup = setup(principal);
        var rows = printerService.findAll(principal.ownerId());
        write(format, setup, "Printer List",
                List.of(ExportColumn.number("Serial"), ExportColumn.text("Title"), ExportColumn.text("Connection Type"),
                        ExportColumn.text("Path"), ExportColumn.text("IP Address"), ExportColumn.number("Port"), ExportColumn.text("Status")),
                consumer -> { long serial = 1; for (var row : rows) consumer.accept(serial++, row.title(), row.connectionType(), row.printerPath(), row.ipAddress(), row.port(), row.status()); }, response);
    }

    private BusinessSetupResponse setup(OwnerPrincipal principal) {
        return branchContextService.resolveOwnerSetup(principal.ownerId());
    }

    private void write(String format, BusinessSetupResponse setup, String title, List<ExportColumn> columns,
                       ExportRowSource rows, HttpServletResponse response) throws IOException {
        exportResponse.write(format, new ExportDocument(title, setup.businessName(), setup.branchName(),
                ZoneId.systemDefault(), Map.of(), columns, rows), response);
    }
}
