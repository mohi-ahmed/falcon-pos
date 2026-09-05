package com.spark.falcon.inventory.controller;

import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.inventory.service.*;
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
public class InventoryExportController {
    private final BranchContextService branchContextService;
    private final InventoryQueryService inventoryQueryService;
    private final PhysicalStockCountService physicalStockCountService;
    private final StockAdjustmentService stockAdjustmentService;
    private final BranchTransferService branchTransferService;
    private final InventoryLossService inventoryLossService;
    private final ExportResponse exportResponse;

    @GetMapping("/owner/inventory/movements/export")
    public void movements(@RequestParam(required=false) Long branchId, @RequestParam String format,
                          @AuthenticationPrincipal OwnerPrincipal principal, HttpServletResponse response) throws IOException {
        var setup=branchContextService.resolveOwnerSetup(principal.ownerId()); Long branch=branchId==null?setup.branchId():branchId;
        var rows=inventoryQueryService.findBranchMovements(setup.businessId(),branch);
        write(format,"Stock Movement Ledger",setup.businessName(),setup.branchName(),List.of(
                ExportColumn.number("ID"),ExportColumn.dateTime("Posted At"),ExportColumn.number("Variant ID"),
                ExportColumn.number("Batch ID"),ExportColumn.text("Movement Type"),ExportColumn.number("Quantity Change"),
                ExportColumn.number("Quantity Before"),ExportColumn.number("Quantity After"),ExportColumn.money("Value Change"),
                ExportColumn.text("Source"),ExportColumn.text("Reference")),c->{for(var v:rows)c.accept(
                v.getId(),v.getPostedAt(),v.getProductVariantId(),v.getProductBatchId(),v.getMovementType(),v.getBaseQuantityChange(),
                v.getQuantityBefore(),v.getQuantityAfter(),v.getInventoryValueChange(),v.getSourceType(),v.getSourceReferenceId());},response);
    }

    @GetMapping("/owner/inventory/counts/export")
    public void counts(@RequestParam(required=false) Long branchId,@RequestParam String format,@AuthenticationPrincipal OwnerPrincipal p,HttpServletResponse r)throws IOException{
        var s=branchContextService.resolveOwnerSetup(p.ownerId());Long b=branchId==null?s.branchId():branchId;var rows=physicalStockCountService.list(p.ownerId(),b);
        write(format,"Physical Stock Count List",s.businessName(),s.branchName(),List.of(ExportColumn.number("ID"),ExportColumn.date("Count Date"),ExportColumn.text("Scope"),ExportColumn.number("Assigned Counter"),ExportColumn.text("Status"),ExportColumn.dateTime("Created At")),c->{for(var v:rows)c.accept(v.getId(),v.getCountDate(),v.getScope(),v.getAssignedCounterId(),v.getStatus(),v.getCreatedAt());},r);
    }

    @GetMapping("/owner/inventory/adjustments/export")
    public void adjustments(@RequestParam(required=false) Long branchId,@RequestParam String format,@AuthenticationPrincipal OwnerPrincipal p,HttpServletResponse r)throws IOException{
        var s=branchContextService.resolveOwnerSetup(p.ownerId());Long b=branchId==null?s.branchId():branchId;var rows=stockAdjustmentService.list(p.ownerId(),b);
        write(format,"Stock Adjustment List",s.businessName(),s.branchName(),List.of(ExportColumn.number("ID"),ExportColumn.number("Variant ID"),ExportColumn.number("Batch ID"),ExportColumn.number("Quantity"),ExportColumn.money("Value Change"),ExportColumn.text("Reason"),ExportColumn.text("Status"),ExportColumn.dateTime("Created At")),c->{for(var v:rows)c.accept(v.getId(),v.getProductVariantId(),v.getProductBatchId(),v.getBaseQuantity(),v.getInventoryValueChange(),v.getReason(),v.getStatus(),v.getCreatedAt());},r);
    }

    @GetMapping("/owner/inventory/transfers/export")
    public void transfers(@RequestParam(required=false) Long branchId,@RequestParam String format,@AuthenticationPrincipal OwnerPrincipal p,HttpServletResponse r)throws IOException{
        var s=branchContextService.resolveOwnerSetup(p.ownerId());Long b=branchId==null?s.branchId():branchId;var rows=branchTransferService.list(p.ownerId(),b);
        write(format,"Branch Transfer List",s.businessName(),s.branchName(),List.of(ExportColumn.number("ID"),ExportColumn.date("Request Date"),ExportColumn.number("Source Branch"),ExportColumn.number("Destination Branch"),ExportColumn.date("Expected Dispatch"),ExportColumn.text("Status"),ExportColumn.dateTime("Created At")),c->{for(var v:rows)c.accept(v.getId(),v.getRequestDate(),v.getSourceBranchId(),v.getDestinationBranchId(),v.getExpectedDispatchDate(),v.getStatus(),v.getCreatedAt());},r);
    }

    @GetMapping("/owner/inventory/wastage/export")
    public void wastage(@RequestParam(required=false) Long branchId,@RequestParam String format,@AuthenticationPrincipal OwnerPrincipal p,HttpServletResponse r)throws IOException{
        var s=branchContextService.resolveOwnerSetup(p.ownerId());Long b=branchId==null?s.branchId():branchId;var rows=inventoryLossService.list(p.ownerId(),b);
        write(format,"Inventory Loss and Wastage List",s.businessName(),s.branchName(),List.of(ExportColumn.number("ID"),ExportColumn.date("Disposal Date"),ExportColumn.number("Variant ID"),ExportColumn.number("Batch ID"),ExportColumn.number("Quantity"),ExportColumn.money("Financial Loss"),ExportColumn.text("Reason"),ExportColumn.text("Status")),c->{for(var v:rows)c.accept(v.getId(),v.getDisposalDate(),v.getProductVariantId(),v.getProductBatchId(),v.getBaseQuantity(),v.getFinancialLoss(),v.getReason(),v.getStatus());},r);
    }

    private void write(String f,String t,String b,String branch,List<ExportColumn> cols,ExportRowSource rows,HttpServletResponse response)throws IOException{
        exportResponse.write(f,new ExportDocument(t,b,branch,ZoneId.of("UTC"),Map.of(),cols,rows),response);
    }
}
