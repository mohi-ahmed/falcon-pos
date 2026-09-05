package com.spark.falcon.inventory.controller;

import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.inventory.dto.*;
import com.spark.falcon.inventory.entity.TransferDiscrepancyResolutionType;
import com.spark.falcon.inventory.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/owner/inventory")
@RequiredArgsConstructor
public class InventoryOperationsController {
    private final PhysicalStockCountService countService;
    private final StockAdjustmentService adjustmentService;
    private final BranchTransferService transferService;
    private final InventoryLossService lossService;

    @PostMapping("/counts")
    public String createCount(@ModelAttribute CreateStockCountCommand r, @AuthenticationPrincipal OwnerPrincipal p, RedirectAttributes flash) {
        var count = countService.create(new CreateStockCountCommand(p.ownerId(), r.branchId(), r.countDate(), r.scope(),
                r.assignedCounterId(), r.notes(), r.attachmentReference(), r.idempotencyKey(), r.selections()));
        success(flash, "Physical Stock Count created.");
        return back("counts", count.getId());
    }

    @PostMapping("/counts/{id}/draft/update")
    public String updateCount(@PathVariable Long id, @ModelAttribute UpdateStockCountCommand r, @AuthenticationPrincipal OwnerPrincipal p, RedirectAttributes flash) {
        countService.updateDraft(new UpdateStockCountCommand(p.ownerId(), id, r.countDate(), r.scope(), r.assignedCounterId(),
                r.notes(), r.attachmentReference(), r.selections()));
        success(flash, "Physical Stock Count draft updated.");
        return back("counts", id);
    }

    @PostMapping("/counts/{id}/rows")
    public String countRow(@PathVariable Long id, @ModelAttribute RecordStockCountRowCommand r, @AuthenticationPrincipal OwnerPrincipal p, RedirectAttributes flash) {
        countService.record(new RecordStockCountRowCommand(p.ownerId(), id, r.rowId(), r.countedQuantity(), r.countedUnitId(),
                r.conversionFactor(), r.varianceReason()));
        success(flash, "Counted quantity saved.");
        return back("counts", id);
    }

    @PostMapping("/counts/{id}/{action}")
    public String countAction(@PathVariable Long id, @PathVariable String action, @RequestParam(required = false) String reason,
                              @AuthenticationPrincipal OwnerPrincipal p, RedirectAttributes flash) {
        switch (action) {
            case "start" -> countService.start(p.ownerId(), id);
            case "submit" -> countService.submit(p.ownerId(), id);
            case "review" -> countService.review(p.ownerId(), id);
            case "recount" -> countService.requestRecount(p.ownerId(), id);
            case "approve" -> countService.approve(p.ownerId(), id);
            case "reject" -> countService.reject(p.ownerId(), id, reason);
            case "cancel" -> countService.cancel(p.ownerId(), id, reason);
            case "post" -> countService.post(p.ownerId(), id);
            case "delete" -> { countService.deleteDraft(p.ownerId(), id); success(flash, "Draft Physical Stock Count deleted."); return "redirect:/owner/inventory/counts"; }
            default -> throw new IllegalArgumentException("Unsupported Count action");
        }
        success(flash, "Physical Stock Count action completed.");
        return back("counts", id);
    }

    @PostMapping("/adjustments")
    public String createAdjustment(@ModelAttribute CreateStockAdjustmentCommand r, @AuthenticationPrincipal OwnerPrincipal p, RedirectAttributes flash) {
        var adjustment = adjustmentService.create(new CreateStockAdjustmentCommand(p.ownerId(), r.branchId(), r.productVariantId(),
                r.productBatchId(), r.type(), r.enteredQuantity(), r.enteredUnitId(), r.conversionFactor(), r.reason(), r.notes(),
                r.attachmentReference(), r.sourceType(), r.sourceCountId(), r.idempotencyKey()));
        success(flash, "Stock Adjustment created as Draft.");
        return back("adjustments", adjustment.getId());
    }

    @PostMapping("/adjustments/{id}/draft/update")
    public String updateAdjustment(@PathVariable Long id, @ModelAttribute UpdateStockAdjustmentCommand r, @AuthenticationPrincipal OwnerPrincipal p, RedirectAttributes flash) {
        adjustmentService.updateDraft(new UpdateStockAdjustmentCommand(p.ownerId(), id, r.productVariantId(), r.productBatchId(), r.type(),
                r.enteredQuantity(), r.enteredUnitId(), r.reason(), r.notes(), r.attachmentReference(), r.sourceType(), r.sourceCountId()));
        success(flash, "Stock Adjustment draft updated.");
        return back("adjustments", id);
    }

    @PostMapping("/adjustments/{id}/{action}")
    public String adjustmentAction(@PathVariable Long id, @PathVariable String action, @RequestParam(required = false) String reason,
                                   @RequestParam(required = false) String idempotencyKey, @AuthenticationPrincipal OwnerPrincipal p, RedirectAttributes flash) {
        switch (action) {
            case "submit" -> adjustmentService.submit(p.ownerId(), id);
            case "approve" -> adjustmentService.approve(p.ownerId(), id);
            case "reject" -> adjustmentService.reject(p.ownerId(), id, reason);
            case "cancel" -> adjustmentService.cancel(p.ownerId(), id, reason);
            case "post" -> adjustmentService.post(p.ownerId(), id);
            case "reverse" -> adjustmentService.reverse(p.ownerId(), id, reason, idempotencyKey);
            case "delete" -> { adjustmentService.deleteDraft(p.ownerId(), id); success(flash, "Draft Stock Adjustment deleted."); return "redirect:/owner/inventory/adjustments"; }
            default -> throw new IllegalArgumentException("Unsupported Adjustment action");
        }
        success(flash, "Stock Adjustment action completed.");
        return back("adjustments", id);
    }

    @PostMapping("/transfers")
    public String createTransfer(@ModelAttribute CreateBranchTransferCommand r, @AuthenticationPrincipal OwnerPrincipal p, RedirectAttributes flash) {
        var transfer = transferService.create(new CreateBranchTransferCommand(p.ownerId(), r.sourceBranchId(), r.destinationBranchId(),
                r.requestDate(), r.expectedDispatchDate(), r.notes(), r.attachmentReference(), r.idempotencyKey(), r.items()));
        success(flash, "Branch Transfer created as Draft.");
        return back("transfers", transfer.getId());
    }

    @PostMapping("/transfers/{id}/draft/update")
    public String updateTransfer(@PathVariable Long id, @ModelAttribute UpdateBranchTransferCommand r, @AuthenticationPrincipal OwnerPrincipal p, RedirectAttributes flash) {
        transferService.updateDraft(new UpdateBranchTransferCommand(p.ownerId(), id, r.destinationBranchId(), r.requestDate(),
                r.expectedDispatchDate(), r.notes(), r.attachmentReference(), r.items()));
        success(flash, "Branch Transfer draft updated.");
        return back("transfers", id);
    }

    @PostMapping("/transfers/{id}/{action}")
    public String transferAction(@PathVariable Long id, @PathVariable String action, @RequestParam(required = false) Long itemId,
                                 @RequestParam(required = false) String reason,
                                 @RequestParam(required = false) TransferDiscrepancyResolutionType resolutionType,
                                 @RequestParam(required = false) String resolutionReference,
                                 @AuthenticationPrincipal OwnerPrincipal p, RedirectAttributes flash) {
        switch (action) {
            case "submit" -> transferService.submit(p.ownerId(), id);
            case "approve" -> transferService.approve(p.ownerId(), id);
            case "reject" -> transferService.reject(p.ownerId(), id, reason);
            case "cancel" -> transferService.cancelWithRelease(p.ownerId(), id, reason);
            case "dispatch" -> transferService.dispatch(p.ownerId(), id);
            case "resolve" -> transferService.resolveDiscrepancy(p.ownerId(), id, itemId, resolutionType, resolutionReference, reason);
            case "delete" -> { transferService.deleteDraft(p.ownerId(), id); success(flash, "Draft Branch Transfer deleted."); return "redirect:/owner/inventory/transfers"; }
            default -> throw new IllegalArgumentException("Unsupported Transfer action");
        }
        success(flash, "Branch Transfer action completed.");
        return back("transfers", id);
    }

    @PostMapping("/transfers/{id}/receive")
    public String receive(@PathVariable Long id, @ModelAttribute ReceiveBranchTransferCommand r, @AuthenticationPrincipal OwnerPrincipal p, RedirectAttributes flash) {
        transferService.receive(new ReceiveBranchTransferCommand(p.ownerId(), id, r.idempotencyKey(), r.items()));
        success(flash, "Transfer receipt saved.");
        return back("transfers", id);
    }

    @PostMapping("/wastage")
    public String createLoss(@ModelAttribute CreateInventoryLossCommand r, @AuthenticationPrincipal OwnerPrincipal p, RedirectAttributes flash) {
        var loss = lossService.create(new CreateInventoryLossCommand(p.ownerId(), r.branchId(), r.productVariantId(), r.productBatchId(),
                r.disposalDate(), r.enteredQuantity(), r.enteredUnitId(), r.conversionFactor(), r.reason(), r.notes(),
                r.attachmentReference(), r.idempotencyKey()));
        success(flash, "Inventory Loss / Wastage record created as Draft.");
        return back("wastage", loss.getId());
    }

    @PostMapping("/wastage/{id}/draft/update")
    public String updateLoss(@PathVariable Long id, @ModelAttribute UpdateInventoryLossCommand r, @AuthenticationPrincipal OwnerPrincipal p, RedirectAttributes flash) {
        lossService.updateDraft(new UpdateInventoryLossCommand(p.ownerId(), id, r.productVariantId(), r.productBatchId(), r.disposalDate(),
                r.enteredQuantity(), r.enteredUnitId(), r.reason(), r.notes(), r.attachmentReference()));
        success(flash, "Inventory Loss / Wastage draft updated.");
        return back("wastage", id);
    }

    @PostMapping("/wastage/{id}/{action}")
    public String lossAction(@PathVariable Long id, @PathVariable String action, @RequestParam(required = false) String reason,
                             @RequestParam(required = false) String idempotencyKey, @AuthenticationPrincipal OwnerPrincipal p, RedirectAttributes flash) {
        switch (action) {
            case "submit" -> lossService.submit(p.ownerId(), id);
            case "approve" -> lossService.approve(p.ownerId(), id);
            case "reject" -> lossService.reject(p.ownerId(), id, reason);
            case "cancel" -> lossService.cancel(p.ownerId(), id, reason);
            case "post" -> lossService.post(p.ownerId(), id);
            case "reverse" -> lossService.reverse(p.ownerId(), id, reason, idempotencyKey);
            case "delete" -> { lossService.deleteDraft(p.ownerId(), id); success(flash, "Draft Inventory Loss / Wastage record deleted."); return "redirect:/owner/inventory/wastage"; }
            default -> throw new IllegalArgumentException("Unsupported Loss action");
        }
        success(flash, "Inventory Loss / Wastage action completed.");
        return back("wastage", id);
    }

    private void success(RedirectAttributes flash, String message) {
        flash.addFlashAttribute("successMessage", message);
    }

    private String back(String type, Long id) { return "redirect:/owner/inventory/" + type + "/" + id; }
}
