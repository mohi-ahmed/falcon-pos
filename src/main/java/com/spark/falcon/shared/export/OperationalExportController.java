package com.spark.falcon.shared.export;

import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.customer.dto.CustomerResponse;
import com.spark.falcon.customer.service.CustomerFinancialReadService;
import com.spark.falcon.expense.entity.ExpenseClassification;
import com.spark.falcon.expense.entity.ExpenseStatus;
import com.spark.falcon.expense.service.ExpenseService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.payment.dto.PaymentHistoryFilter;
import com.spark.falcon.payment.entity.PaymentDirection;
import com.spark.falcon.payment.entity.PaymentStatus;
import com.spark.falcon.payment.service.PaymentService;
import com.spark.falcon.purchase.entity.enumtype.PurchasePaymentStatus;
import com.spark.falcon.purchase.entity.enumtype.PurchaseReturnStatus;
import com.spark.falcon.purchase.entity.enumtype.PurchaseStatus;
import com.spark.falcon.purchase.entity.enumtype.StockImportPurpose;
import com.spark.falcon.purchase.entity.enumtype.StockImportStatus;
import com.spark.falcon.purchase.exception.PurchaseAccessDeniedException;
import com.spark.falcon.purchase.service.PurchaseReturnService;
import com.spark.falcon.purchase.service.PurchaseService;
import com.spark.falcon.purchase.service.StockImportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class OperationalExportController {
    private static final int EXPORT_LIMIT = 10_000;
    private final BranchContextService branchContextService;
    private final BranchAccessService branchAccessService;
    private final ExportResponse exportResponse;
    private final CustomerFinancialReadService customerFinancialReadService;
    private final com.spark.falcon.customer.service.CustomerListReadService customerListReadService;
    private final ExpenseService expenseService;
    private final PaymentService paymentService;
    private final PurchaseService purchaseService;
    private final PurchaseReturnService purchaseReturnService;
    private final StockImportService stockImportService;

    @GetMapping("/owner/purchases/logs/export")
    public void purchaseLogs(@RequestParam(required=false) Long branchId,@RequestParam(required=false) Long supplierId,
                             @RequestParam(required=false) String query,@RequestParam String format,
                             @AuthenticationPrincipal OwnerPrincipal principal,HttpServletResponse response)throws IOException{
        var setup=branchContextService.resolveOwnerSetup(principal.ownerId());Long branch=branchId==null?setup.branchId():branchId;
        var rows=purchaseService.findLogs(principal.ownerId(),branch,supplierId,query,
                PageRequest.of(0,EXPORT_LIMIT,Sort.by(Sort.Direction.DESC,"createdAt"))).getContent();
        write(format,"Purchase Log",setup.businessName(),setup.branchName(),List.of(ExportColumn.number("Audit ID"),
                ExportColumn.dateTime("Date"),ExportColumn.text("Transaction Type"),ExportColumn.number("Purchase ID"),
                ExportColumn.number("Supplier ID"),ExportColumn.number("Actor ID"),ExportColumn.money("Amount"),
                ExportColumn.text("Payment Method"),ExportColumn.text("Details")),c->{for(var v:rows)c.accept(
                v.auditId(),v.createdAt(),v.transactionType(),v.purchaseId(),v.supplierId(),v.actorId(),v.totalAmount(),
                v.paymentMethod(),v.details());},response);
    }

    @GetMapping("/owner/purchases/stock-import/history/export")
    public void stockImports(@RequestParam(required = false) Long branchId,
                             @RequestParam(required = false) StockImportPurpose purpose,
                             @RequestParam(required = false) StockImportStatus status,
                             @RequestParam(required = false) Long uploadedBy,
                             @RequestParam(required = false) Long confirmedBy,
                             @RequestParam(required = false) LocalDate uploadedFrom,
                             @RequestParam(required = false) LocalDate uploadedTo,
                             @RequestParam(required = false) LocalDate confirmedFrom,
                             @RequestParam(required = false) LocalDate confirmedTo,
                             @RequestParam(required = false) Boolean reversed,
                             @RequestParam(required = false) String q,
                             @RequestParam(defaultValue = "uploadedAt") String sortBy,
                             @RequestParam(defaultValue = "desc") String sortDir,
                             @RequestParam String format,
                             @AuthenticationPrincipal OwnerPrincipal principal,
                             HttpServletResponse response) throws IOException {
        var setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        Long branch = branchId == null ? setup.branchId() : branchId;
        var branchInfo = branchAccessService.findByBusinessIdAndBranchId(setup.businessId(), branch)
                .orElseThrow(PurchaseAccessDeniedException::new);
        ZoneId branchZone = ZoneId.of(branchInfo.timeZone());

        String query = q == null ? "" : q.trim();
        Long batchId = query.matches("\\d+") ? Long.valueOf(query) : null;
        String safeSortBy = Set.of("uploadedAt", "confirmedAt", "status", "importPurpose", "fileName", "id").contains(sortBy)
                ? sortBy : "uploadedAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        var rows = stockImportService.searchHistory(principal.ownerId(), branch, purpose, status, uploadedBy, confirmedBy,
                uploadedFrom == null ? null : uploadedFrom.atStartOfDay(branchZone).toInstant(),
                uploadedTo == null ? null : uploadedTo.plusDays(1).atStartOfDay(branchZone).toInstant(),
                confirmedFrom == null ? null : confirmedFrom.atStartOfDay(branchZone).toInstant(),
                confirmedTo == null ? null : confirmedTo.plusDays(1).atStartOfDay(branchZone).toInstant(),
                reversed, batchId, query,
                PageRequest.of(0, EXPORT_LIMIT, Sort.by(direction, safeSortBy).and(Sort.by(direction, "id")))).getContent();

        write(format,"Stock Import History",setup.businessName(),setup.branchName(),List.of(ExportColumn.number("Batch ID"),
                ExportColumn.dateTime("Uploaded At"),ExportColumn.text("Purpose"),ExportColumn.text("File Name"),
                ExportColumn.number("Total Rows"),ExportColumn.number("Valid Rows"),ExportColumn.number("Failed Rows"),
                ExportColumn.number("Posted Movements"),ExportColumn.text("Status")),c->{for(var v:rows)c.accept(
                v.id(),v.uploadedAt(),v.importPurpose(),v.fileName(),v.totalRows(),v.validRows(),v.failedRows(),
                v.postedMovementCount(),v.status());},response);
    }

    @GetMapping("/owner/customers/export")
    public void customers(@RequestParam(defaultValue = "") String q,
                          @RequestParam(required = false) Boolean archived,
                          @RequestParam(required = false) Boolean hasDue,
                          @RequestParam(required = false) Boolean overdue,
                          @RequestParam(required = false) LocalDate createdFrom,
                          @RequestParam(required = false) LocalDate createdTo,
                          @RequestParam(required = false) LocalDate lastPurchaseFrom,
                          @RequestParam(required = false) LocalDate lastPurchaseTo,
                          @RequestParam(defaultValue = "created-desc") String sort,
                          @RequestParam String format, @AuthenticationPrincipal OwnerPrincipal principal,
                          HttpServletResponse response) throws IOException {
        var setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        var rows = customerListReadService.find(principal.ownerId(), setup.branchId(),
                new com.spark.falcon.customer.dto.CustomerListFilter(q, archived, hasDue, overdue,
                        createdFrom, createdTo, lastPurchaseFrom, lastPurchaseTo, sort),
                PageRequest.of(0, EXPORT_LIMIT), LocalDate.now()).getContent();
        write(format, "Customer List", setup.businessName(), setup.branchName(),
                List.of(ExportColumn.number("ID"), ExportColumn.text("Name"), ExportColumn.text("Phone"),
                        ExportColumn.text("Email"), ExportColumn.money("Total Sales"), ExportColumn.money("Paid"),
                        ExportColumn.money("Outstanding Due"), ExportColumn.money("Credit"), ExportColumn.text("Status")),
                consumer -> { for (var row : rows) {
                    var customer = row.customer();
                    var finance = row.financial();
                    consumer.accept(customer.id(), customer.name(), customer.phone(), customer.email(),
                            finance.totalConfirmedPurchases(), finance.totalPaid(), finance.outstandingDue(),
                            finance.customerCredit(), customer.archived() ? "ARCHIVED" : customer.active() ? "ACTIVE" : "INACTIVE");
                } }, response);
    }

    @GetMapping("/owner/customers/due-invoices/export")
    public void customerDueInvoices(@RequestParam(required = false) Long branchId,
                                    @RequestParam(required = false) Long customerId,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) LocalDate invoiceFrom,
                                    @RequestParam(required = false) LocalDate invoiceTo,
                                    @RequestParam(required = false) LocalDate dueFrom,
                                    @RequestParam(required = false) LocalDate dueTo,
                                    @RequestParam(required = false) String agingBucket,
                                    @RequestParam(defaultValue = "newest") String sort,
                                    @RequestParam String format,
                                    @AuthenticationPrincipal OwnerPrincipal principal,
                                    HttpServletResponse response) throws IOException {
        var setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        Long branch = branchId == null ? setup.branchId() : branchId;
        var filter = new com.spark.falcon.customer.dto.CustomerDueInvoiceFilter(
                status, invoiceFrom, invoiceTo, dueFrom, dueTo, agingBucket);
        var rows = new java.util.ArrayList<>(customerFinancialReadService.invoices(
                principal.ownerId(), branch, customerId, LocalDate.now(), filter));
        java.util.Comparator<com.spark.falcon.sale.dto.CustomerDueInvoiceResponse> rowSort = switch (sort.toLowerCase()) {
            case "oldest" -> java.util.Comparator.comparing(com.spark.falcon.sale.dto.CustomerDueInvoiceResponse::createdAt);
            case "highest-due" -> java.util.Comparator.comparing(
                    com.spark.falcon.sale.dto.CustomerDueInvoiceResponse::dueAmount).reversed();
            default -> java.util.Comparator.comparing(
                    com.spark.falcon.sale.dto.CustomerDueInvoiceResponse::createdAt).reversed();
        };
        rows.sort(rowSort.thenComparing(com.spark.falcon.sale.dto.CustomerDueInvoiceResponse::id));
        write(format, "Customer Due Invoices", setup.businessName(), setup.branchName(),
                List.of(ExportColumn.number("Invoice ID"), ExportColumn.dateTime("Invoice Date"),
                        ExportColumn.date("Due Date"), ExportColumn.money("Original Payable"),
                        ExportColumn.money("Confirmed Paid"), ExportColumn.money("Return / Credit"),
                        ExportColumn.money("Payment Reversal"), ExportColumn.money("Outstanding Due"),
                        ExportColumn.number("Days Overdue"), ExportColumn.text("Status")),
                consumer -> { for (var row : rows) consumer.accept(
                        row.id(), row.createdAt(), row.dueDate(), row.totalPayable(), row.paidAmount(),
                        row.returnedAmount(), row.paymentReversalAmount(), row.dueAmount(),
                        row.daysOverdue(), row.paymentStatus()); }, response);
    }

    @GetMapping("/owner/customers/{customerId}/statement/export")
    public void customerStatement(@org.springframework.web.bind.annotation.PathVariable Long customerId,
                                  @RequestParam(required = false) Long branchId,
                                  @RequestParam(required = false) LocalDate from,
                                  @RequestParam(required = false) LocalDate to,
                                  @RequestParam String format,
                                  @AuthenticationPrincipal OwnerPrincipal principal,
                                  HttpServletResponse response) throws IOException {
        var setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        Long branch = branchId == null ? setup.branchId() : branchId;
        var rows = customerFinancialReadService.statement(
                principal.ownerId(), branch, customerId,
                from == null ? null : from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        write(format, "Customer Statement", setup.businessName(), setup.branchName(),
                List.of(ExportColumn.dateTime("Transaction Date"), ExportColumn.text("Reference"),
                        ExportColumn.number("Branch ID"), ExportColumn.text("Type"),
                        ExportColumn.money("Debit"), ExportColumn.money("Credit"),
                        ExportColumn.money("Running Due Balance"), ExportColumn.text("Source")),
                consumer -> { for (var row : rows) consumer.accept(
                        row.transactionAt(), row.reference(), row.branchId(), row.type(), row.debit(),
                        row.credit(), row.runningDueBalance(), row.sourcePath()); }, response);
    }

    @GetMapping("/owner/expenses/export")
    public void expenses(@RequestParam(required = false) Long branchId,
                         @RequestParam(required = false) Long expenseId,
                         @RequestParam(required = false) ExpenseClassification classification,
                         @RequestParam(required = false) Long categoryId,
                         @RequestParam(required = false) Long paymentMethodId,
                         @RequestParam(required = false) ExpenseStatus status,
                         @RequestParam(required = false) Long createdBy,
                         @RequestParam(required = false) LocalDate from,
                         @RequestParam(required = false) LocalDate to,
                         @RequestParam String format, @AuthenticationPrincipal OwnerPrincipal principal,
                         HttpServletResponse response) throws IOException {
        var setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        Long activeBranch = branchId == null ? setup.branchId() : branchId;
        var rows = expenseService.filter(principal.ownerId(), activeBranch, expenseId, classification, categoryId,
                paymentMethodId, status, createdBy, from, to);
        write(format, "Expense List", setup.businessName(), setup.branchName(),
                List.of(ExportColumn.number("ID"), ExportColumn.date("Date"), ExportColumn.text("Classification"),
                        ExportColumn.text("Category"), ExportColumn.text("Title"), ExportColumn.money("Amount"),
                        ExportColumn.text("Payment Method"), ExportColumn.text("Payee"), ExportColumn.text("Status")),
                consumer -> { for (var item : rows) consumer.accept(item.id(), item.expenseDate(), item.classification(),
                        item.category(), item.title(), item.amount(), item.paymentMethod(), item.payee(), item.status()); }, response);
    }

    @GetMapping("/owner/payments/history/export")
    public void payments(@RequestParam(required = false) Long branchId,
                         @RequestParam(required = false) Long paymentId,
                         @RequestParam(required = false) Long invoiceId,
                         @RequestParam(required = false) Long customerId,
                         @RequestParam(required = false) Long supplierId,
                         @RequestParam(required = false) Long paymentMethodId,
                         @RequestParam(required = false) PaymentDirection direction,
                         @RequestParam(required = false) PaymentStatus status,
                         @RequestParam(required = false) Long actorId,
                         @RequestParam(required = false) LocalDate from,
                         @RequestParam(required = false) LocalDate to,
                         @RequestParam String format, @AuthenticationPrincipal OwnerPrincipal principal,
                         HttpServletResponse response) throws IOException {
        var setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        Long filterBranchId = branchId == null && principal.isStaff()
                ? setup.branchId() : branchId;
        Long zoneBranchId = filterBranchId == null ? setup.branchId() : filterBranchId;
        ZoneId zone = ZoneId.of(branchAccessService.findByBusinessIdAndBranchId(setup.businessId(), zoneBranchId)
                .orElseThrow().timeZone());
        var filter = new PaymentHistoryFilter(filterBranchId, paymentId, invoiceId, customerId, supplierId, paymentMethodId,
                direction, status, actorId, from == null ? null : from.atStartOfDay(zone).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant());
        var rows = paymentService.findHistory(principal.ownerId(), filter,
                PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        boolean canViewReference = principal.hasPermission("PAYMENT_VIEW_REFERENCE");
        write(format, "Payment History", setup.businessName(), setup.branchName(),
                List.of(ExportColumn.number("Payment ID"), ExportColumn.dateTime("Date"), ExportColumn.number("Branch ID"),
                        ExportColumn.text("Party"), ExportColumn.number("Party ID"), ExportColumn.text("Allocated Invoices"),
                        ExportColumn.money("Amount"), ExportColumn.text("Method"), ExportColumn.text("Reference"),
                        ExportColumn.number("Received / Posted By"), ExportColumn.text("Direction"),
                        ExportColumn.text("Status"), ExportColumn.text("Reversal")), consumer -> { for (var item : rows) {
                    String invoices = item.allocations().stream()
                            .map(a -> a.invoiceType() + " #" + a.invoiceId())
                            .collect(java.util.stream.Collectors.joining(", "));
                    String reversal = item.reversalOfPaymentId() != null ? "Reversal of #" + item.reversalOfPaymentId()
                            : item.reversedByPaymentId() != null ? "Reversed by #" + item.reversedByPaymentId() : null;
                    consumer.accept(item.id(), item.createdAt(), item.branchId(), item.partyType(),
                            item.customerId() == null ? item.supplierId() : item.customerId(), invoices, item.amount(),
                            item.paymentMethodName(), canViewReference ? item.transactionReference() : "Restricted",
                            item.confirmedByActorId() == null ? item.createdByActorId() : item.confirmedByActorId(),
                            item.direction(), item.status(), reversal);
                } }, response);
    }

    @GetMapping("/owner/purchases/export")
    public void purchases(@RequestParam(required = false) Long branchId, @RequestParam(required = false) Long supplierId,
                          @RequestParam(required = false) PurchaseStatus status,
                          @RequestParam(required = false) PurchasePaymentStatus paymentStatus,
                          @RequestParam(required = false) LocalDate fromDate, @RequestParam(required = false) LocalDate toDate,
                          @RequestParam(required = false) String keyword, @RequestParam String format,
                          @AuthenticationPrincipal OwnerPrincipal principal, HttpServletResponse response) throws IOException {
        var setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        Long activeBranch = branchId == null ? setup.branchId() : branchId;
        var rows = purchaseService.search(principal.ownerId(), activeBranch, supplierId, status, paymentStatus, fromDate,
                toDate, keyword, PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        write(format, "Purchase List", setup.businessName(), setup.branchName(),
                List.of(ExportColumn.number("Purchase ID"), ExportColumn.date("Date"), ExportColumn.number("Supplier ID"),
                        ExportColumn.text("Supplier Invoice"), ExportColumn.money("Total"), ExportColumn.money("Paid"),
                        ExportColumn.money("Due"), ExportColumn.text("Payment Status"), ExportColumn.text("Status")),
                consumer -> { for (var item : rows) consumer.accept(item.id(), item.purchaseDate(), item.supplierId(),
                        item.supplierInvoiceReference(), item.totalPayable(), item.paidAmount(), item.dueAmount(),
                        item.paymentStatus(), item.status()); }, response);
    }

    @GetMapping("/owner/purchases/returns/export")
    public void purchaseReturns(@RequestParam(required = false) Long branchId,
                                @RequestParam(required = false) Long supplierId,
                                @RequestParam(required = false) PurchaseReturnStatus status,
                                @RequestParam(required = false) LocalDate fromDate,
                                @RequestParam(required = false) LocalDate toDate,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(defaultValue = "returnDate") String sortBy,
                                @RequestParam(defaultValue = "desc") String sortDir,
                                @RequestParam String format, @AuthenticationPrincipal OwnerPrincipal principal,
                                HttpServletResponse response) throws IOException {
        var setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        Long activeBranch = branchId == null ? setup.branchId() : branchId;
        String safeSortBy = Set.of("returnDate", "createdAt", "referenceNumber", "totalReturnAmount", "status").contains(sortBy)
                ? sortBy : "returnDate";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        var rows = purchaseReturnService.search(principal.ownerId(), activeBranch, supplierId, status, fromDate, toDate,
                keyword, PageRequest.of(0, EXPORT_LIMIT, Sort.by(direction, safeSortBy).and(Sort.by(direction, "id")))).getContent();
        write(format, "Purchase Return List", setup.businessName(), setup.branchName(),
                List.of(ExportColumn.text("Reference"), ExportColumn.number("Purchase ID"), ExportColumn.date("Date"),
                        ExportColumn.number("Supplier ID"), ExportColumn.money("Return Amount"),
                        ExportColumn.money("Due Reduction"), ExportColumn.money("Refund"), ExportColumn.text("Status")),
                consumer -> { for (var item : rows) consumer.accept(item.referenceNumber(), item.purchaseId(),
                        item.returnDate(), item.supplierId(), item.totalReturnAmount(), item.supplierDueReduction(),
                        item.refundAmount(), item.status()); }, response);
    }

    private void write(String format, String title, String business, String branch, List<ExportColumn> columns,
                       ExportRowSource rows, HttpServletResponse response) throws IOException {
        exportResponse.write(format, new ExportDocument(title, business, branch, ZoneId.of("UTC"), Map.of(), columns, rows), response);
    }
}
