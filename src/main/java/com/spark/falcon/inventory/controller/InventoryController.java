package com.spark.falcon.inventory.controller;

import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.branch.dto.response.BranchAccessResponse;
import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.inventory.dto.*;
import com.spark.falcon.inventory.entity.*;
import com.spark.falcon.inventory.exception.InventoryAccessDeniedException;
import com.spark.falcon.inventory.service.*;
import com.spark.falcon.product.dto.response.ProductVariantAccessResponse;
import com.spark.falcon.product.service.ProductAccessService;
import com.spark.falcon.settings.dto.response.UnitResponse;
import com.spark.falcon.settings.service.BranchSettingsAccessService;
import com.spark.falcon.settings.service.UnitAccessService;
import com.spark.falcon.user.dto.command.ManagementActor;
import com.spark.falcon.user.dto.response.UserResponse;
import com.spark.falcon.user.entity.enumtype.ManagementActorType;
import com.spark.falcon.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/owner/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final BranchContextService branchContextService;
    private final BranchAccessService branchAccessService;
    private final InventoryQueryService inventoryQueryService;
    private final ProductAccessService productAccessService;
    private final UnitAccessService unitAccessService;
    private final PhysicalStockCountService countService;
    private final StockAdjustmentService adjustmentService;
    private final BranchTransferService transferService;
    private final InventoryLossService lossService;
    private final BranchSettingsAccessService branchSettingsAccessService;
    private final InventoryAuditQueryService auditQueryService;
    private final UserService userService;

    @GetMapping({"", "/entry"})
    public String inventoryOverview(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal);
        List<BranchProductStockResponse> stocks = inventoryQueryService.findBranchStocks(setup.businessId(), setup.branchId());
        List<ProductBatchResponse> batches = inventoryQueryService.findBranchBatches(setup.businessId(), setup.branchId());
        addInventoryContext(model, setup, principal);
        model.addAttribute("stocks", stocks); model.addAttribute("batches", batches);
        model.addAttribute("metrics", metrics(principal.ownerId(), setup, stocks, batches));
        model.addAttribute("activePage", "inventory-overview");
        return "inventory/inventory-overview";
    }

    @GetMapping("/movements")
    public String stockMovements(
                                 @RequestParam(required = false) Long movementId,
                                 @RequestParam(required = false) Long productId,
                                 @RequestParam(required = false) Long productVariantId,
                                 @RequestParam(required = false) Long batchId,
                                 @RequestParam(required = false) StockMovementType movementType,
                                 @RequestParam(required = false) StockSourceType sourceType,
                                 @RequestParam(required = false) Long userId,
                                 @RequestParam(required = false) String from,
                                 @RequestParam(required = false) String to,
                                 @RequestParam(required = false) Boolean reversal,
                                 @RequestParam(required = false) Long reversalReferenceId,
                                 @RequestParam(required = false) String query,
                                 @PageableDefault(size = 25, sort = "postedAt", direction = Sort.Direction.DESC) Pageable pageable,
                                 @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        StockMovementFilter filter = new StockMovementFilter(movementId, productId, productVariantId, batchId, movementType, sourceType, userId,
                parseInstant(from), parseInstant(to), reversal, reversalReferenceId, query);
        BusinessSetupResponse setup = setup(principal); addInventoryContext(model, setup, principal);
        var page = inventoryQueryService.searchMovements(setup.businessId(), setup.branchId(), filter, pageable);
        model.addAttribute("movementPage", page); model.addAttribute("movements", page.getContent());
        model.addAttribute("movementFilter", filter); model.addAttribute("sortParam", sortParam(pageable, "postedAt,desc"));
        model.addAttribute("activePage", "stock-movements");
        return "inventory/stock-movement-ledger";
    }

    @GetMapping("/movements/{movementId}")
    public String stockMovementDetails(@PathVariable Long movementId, @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal); addInventoryContext(model, setup, principal);
        StockMovementResponse movement = inventoryQueryService.findMovement(setup.businessId(), setup.branchId(), movementId)
                .orElseThrow(InventoryAccessDeniedException::new);
        model.addAttribute("movement", movement);
        model.addAttribute("batch", movement.getProductBatchId() == null ? null : inventoryQueryService.findBatch(setup.businessId(), setup.branchId(), movement.getProductBatchId()).orElse(null));
        model.addAttribute("linkedReversal", inventoryQueryService.findLinkedReversal(movementId).orElse(null));
        model.addAttribute("originalMovement", movement.getReversalOfMovementId() == null ? null : inventoryQueryService.findMovement(setup.businessId(), setup.branchId(), movement.getReversalOfMovementId()).orElse(null));
        model.addAttribute("activePage", "stock-movements");
        return "inventory/stock-movement-details";
    }

    @GetMapping("/counts/new")
    public String createPhysicalCount(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal); addInventoryContext(model, setup, principal);
        model.addAttribute("today", LocalDate.now()); model.addAttribute("activePage", "create-stock-count");
        return "inventory/create-physical-stock-count";
    }

    @GetMapping("/counts")
    public String physicalCountList(
                                    @RequestParam(required = false) Long countId,
                                    @RequestParam(required = false) StockCountScope scope,
                                    @RequestParam(required = false) InventoryOperationStatus status,
                                    @RequestParam(required = false) Long createdBy,
                                    @RequestParam(required = false) Long assignedCounterId,
                                    @RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to,
                                    @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
                                    @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        PhysicalStockCountFilter filter = new PhysicalStockCountFilter(countId, scope, status, createdBy, assignedCounterId, parseInstant(from), parseInstant(to));
        BusinessSetupResponse setup = setup(principal); addInventoryContext(model, setup, principal);
        var page = countService.search(principal.ownerId(), setup.branchId(), filter, pageable);
        Map<Long, Long> countedRowCounts = page.getContent().stream().collect(Collectors.toMap(
                PhysicalStockCount::getId, count -> count.getRows().stream().filter(row -> row.getCountedQuantity() != null).count()));
        Map<Long, Long> varianceRowCounts = page.getContent().stream().collect(Collectors.toMap(
                PhysicalStockCount::getId, count -> count.getRows().stream()
                        .filter(row -> row.getVariance() != null && row.getVariance().signum() != 0).count()));
        Map<Long, BigDecimal> totalAbsoluteVarianceValues = new HashMap<>(adjustmentService.totalAbsoluteVarianceValues(
                principal.ownerId(), setup.branchId(), page.getContent().stream().map(PhysicalStockCount::getId).toList()));
        page.getContent().stream()
                .filter(count -> count.getStatus() == InventoryOperationStatus.POSTED)
                .forEach(count -> totalAbsoluteVarianceValues.putIfAbsent(count.getId(), BigDecimal.ZERO));
        model.addAttribute("countPage", page); model.addAttribute("counts", page.getContent()); model.addAttribute("countFilter", filter);
        model.addAttribute("countedRowCounts", countedRowCounts); model.addAttribute("varianceRowCounts", varianceRowCounts);
        model.addAttribute("totalAbsoluteVarianceValues", totalAbsoluteVarianceValues);
        model.addAttribute("sortParam", sortParam(pageable, "createdAt,desc"));
        model.addAttribute("activePage", "stock-count-list"); return "inventory/physical-stock-count-list";
    }

    @GetMapping("/counts/{countId}")
    public String physicalCountDetails(@PathVariable Long countId, @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal); addInventoryContext(model, setup, principal);
        model.addAttribute("count", countService.find(principal.ownerId(), countId));
        model.addAttribute("auditTimeline", auditQueryService.timeline("COUNT", countId));
        model.addAttribute("activePage", "stock-count-list"); return "inventory/physical-stock-count-details";
    }

    @GetMapping("/adjustments/new")
    public String createStockAdjustment(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal); addInventoryContext(model, setup, principal);
        model.addAttribute("activePage", "create-adjustment"); return "inventory/create-stock-adjustment";
    }

    @GetMapping("/adjustments")
    public String stockAdjustmentList(
                                      @RequestParam(required = false) Long adjustmentId,
                                      @RequestParam(required = false) Long productVariantId,
                                      @RequestParam(required = false) Long batchId,
                                      @RequestParam(required = false) AdjustmentType type,
                                      @RequestParam(required = false) String reason,
                                      @RequestParam(required = false) InventoryOperationStatus status,
                                      @RequestParam(required = false) AdjustmentSourceType sourceType,
                                      @RequestParam(required = false) Long createdBy,
                                      @RequestParam(required = false) Long approvedBy,
                                      @RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to,
                                      @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
                                      @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        StockAdjustmentFilter filter = new StockAdjustmentFilter(adjustmentId, productVariantId, batchId, type, reason, status, sourceType, createdBy, approvedBy, parseInstant(from), parseInstant(to));
        BusinessSetupResponse setup = setup(principal); addInventoryContext(model, setup, principal);
        var page = adjustmentService.search(principal.ownerId(), setup.branchId(), filter, pageable);
        model.addAttribute("adjustmentPage", page); model.addAttribute("adjustments", page.getContent()); model.addAttribute("adjustmentFilter", filter);
        model.addAttribute("sortParam", sortParam(pageable, "createdAt,desc"));
        model.addAttribute("activePage", "adjustment-list"); return "inventory/stock-adjustment-list";
    }

    @GetMapping("/adjustments/{adjustmentId}")
    public String stockAdjustmentDetails(@PathVariable Long adjustmentId, @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal); addInventoryContext(model, setup, principal);
        model.addAttribute("adjustment", adjustmentService.find(principal.ownerId(), adjustmentId));
        model.addAttribute("auditTimeline", auditQueryService.timeline("ADJUSTMENT", adjustmentId));
        model.addAttribute("activePage", "adjustment-list"); return "inventory/stock-adjustment-details";
    }

    @GetMapping("/transfers/new")
    public String createBranchTransfer(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal); addInventoryContext(model, setup, principal);
        model.addAttribute("today", LocalDate.now()); model.addAttribute("activePage", "create-transfer");
        return "inventory/create-branch-transfer";
    }

    @GetMapping("/transfers")
    public String branchTransferList(
                                     @RequestParam(required = false) Long transferId,
                                     @RequestParam(required = false) Long sourceBranchId,
                                     @RequestParam(required = false) Long destinationBranchId,
                                     @RequestParam(required = false) BranchTransferStatus status,
                                     @RequestParam(required = false) Long createdBy,
                                     @RequestParam(required = false) Long approvedBy,
                                     @RequestParam(required = false) Long dispatchedBy,
                                     @RequestParam(required = false) Long receivedBy,
                                     @RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to,
                                     @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
                                     @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BranchTransferFilter filter = new BranchTransferFilter(transferId, sourceBranchId, destinationBranchId, status, createdBy, approvedBy, dispatchedBy, receivedBy, parseInstant(from), parseInstant(to));
        BusinessSetupResponse setup = setup(principal); addInventoryContext(model, setup, principal);
        var page = transferService.search(principal.ownerId(), setup.branchId(), filter, pageable);
        Map<Long, BigDecimal> transferBaseQuantityMap = page.getContent().stream().collect(Collectors.toMap(
                BranchTransfer::getId, transfer -> transfer.getItems().stream().map(BranchTransferItem::getBaseQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
        Map<Long, BigDecimal> transferValueMap = page.getContent().stream().collect(Collectors.toMap(
                BranchTransfer::getId, transfer -> transfer.getItems().stream()
                        .map(item -> safe(item.getBaseQuantity()).multiply(safe(item.getTransferUnitCostSnapshot())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
        model.addAttribute("transferPage", page); model.addAttribute("transfers", page.getContent()); model.addAttribute("transferFilter", filter);
        model.addAttribute("transferBaseQuantityMap", transferBaseQuantityMap); model.addAttribute("transferValueMap", transferValueMap);
        model.addAttribute("sortParam", sortParam(pageable, "createdAt,desc"));
        model.addAttribute("activePage", "transfer-list"); return "inventory/branch-transfer-list";
    }

    @GetMapping("/transfers/{transferId}")
    public String branchTransferDetails(@PathVariable Long transferId, @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal); addInventoryContext(model, setup, principal);
        BranchTransfer transfer = transferService.find(principal.ownerId(), transferId);
        Map<Long, ProductBatchResponse> transferBatchMap = inventoryQueryService
                .findBranchBatches(setup.businessId(), transfer.getSourceBranchId()).stream()
                .collect(Collectors.toMap(ProductBatchResponse::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        model.addAttribute("transfer", transfer);
        model.addAttribute("transferBatchMap", transferBatchMap);
        model.addAttribute("transferReceipts", transferService.receipts(principal.ownerId(), transferId));
        model.addAttribute("auditTimeline", auditQueryService.timeline("TRANSFER", transferId));
        model.addAttribute("activePage", "transfer-list"); return "inventory/branch-transfer-details";
    }

    @GetMapping("/wastage/new")
    public String createWastage(@RequestParam(required = false) Long productVariantId,
                                @RequestParam(required = false) Long batchId,
                                @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal); addInventoryContext(model, setup, principal);
        model.addAttribute("selectedProductVariantId", productVariantId);
        model.addAttribute("selectedBatchId", batchId);
        model.addAttribute("today", LocalDate.now()); model.addAttribute("activePage", "create-wastage");
        return "inventory/create-inventory-wastage";
    }

    @GetMapping("/wastage")
    public String wastageHistory(
                                 @RequestParam(required = false) Long lossId,
                                 @RequestParam(required = false) Long productVariantId,
                                 @RequestParam(required = false) Long batchId,
                                 @RequestParam(required = false) String reason,
                                 @RequestParam(required = false) InventoryOperationStatus status,
                                 @RequestParam(required = false) Long createdBy,
                                 @RequestParam(required = false) Long approvedBy,
                                 @RequestParam(required = false) String from,
                                 @RequestParam(required = false) String to,
                                 @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
                                 @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        InventoryLossFilter filter = new InventoryLossFilter(lossId, productVariantId, batchId, reason, status, createdBy, approvedBy, parseInstant(from), parseInstant(to));
        BusinessSetupResponse setup = setup(principal); addInventoryContext(model, setup, principal);
        var page = lossService.search(principal.ownerId(), setup.branchId(), filter, pageable);
        model.addAttribute("lossPage", page); model.addAttribute("losses", page.getContent()); model.addAttribute("lossFilter", filter);
        model.addAttribute("sortParam", sortParam(pageable, "createdAt,desc"));
        model.addAttribute("activePage", "wastage-history"); return "inventory/inventory-wastage-history";
    }

    @GetMapping("/wastage/{lossId}")
    public String wastageDetails(@PathVariable Long lossId, @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal); addInventoryContext(model, setup, principal);
        model.addAttribute("loss", lossService.find(principal.ownerId(), lossId));
        model.addAttribute("auditTimeline", auditQueryService.timeline("LOSS", lossId));
        model.addAttribute("activePage", "wastage-history"); return "inventory/inventory-wastage-details";
    }

    private void addInventoryContext(Model model, BusinessSetupResponse setup, OwnerPrincipal principal) {
        List<ProductVariantAccessResponse> variants = productAccessService.findActiveVariantsForBranch(setup.businessId(), setup.branchId());
        Map<Long, ProductVariantAccessResponse> variantMap = variants.stream()
                .collect(Collectors.toMap(ProductVariantAccessResponse::variantId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Long, String> productOptions = variants.stream()
                .collect(Collectors.toMap(ProductVariantAccessResponse::productId, ProductVariantAccessResponse::productName,
                        (a, b) -> a, LinkedHashMap::new));
        List<UnitResponse> units = unitAccessService.findActiveForBranch(setup.businessId(), setup.branchId());
        Map<Long, UnitResponse> unitMap = units.stream()
                .collect(Collectors.toMap(UnitResponse::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<BranchAccessResponse> branches = branchAccessService.findActiveByBusinessId(setup.businessId());
        Map<Long, BranchAccessResponse> branchMap = branches.stream()
                .collect(Collectors.toMap(BranchAccessResponse::branchId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<BranchProductStockResponse> stocks = inventoryQueryService.findBranchStocks(setup.businessId(), setup.branchId());
        Map<Long, BranchProductStockResponse> stockMap = stocks.stream()
                .collect(Collectors.toMap(BranchProductStockResponse::getProductVariantId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<ProductBatchResponse> batches = inventoryQueryService.findBranchBatches(setup.businessId(), setup.branchId());
        Map<Long, ProductBatchResponse> batchMap = batches.stream()
                .collect(Collectors.toMap(ProductBatchResponse::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<UserResponse> counters = userService.findByBranch(
                        new ManagementActor(setup.businessId(), ManagementActorType.OWNER, principal.ownerId()),
                        setup.branchId(), null, PageRequest.of(0, 250, Sort.by("fullName").ascending()))
                .getContent();

        model.addAttribute("setup", setup);
        model.addAttribute("variantOptions", variants);
        model.addAttribute("variantMap", variantMap);
        model.addAttribute("productOptions", productOptions);
        model.addAttribute("units", units);
        model.addAttribute("unitMap", unitMap);
        model.addAttribute("branchOptions", branches);
        model.addAttribute("branchMap", branchMap);
        model.addAttribute("stockOptions", stocks);
        model.addAttribute("stockMap", stockMap);
        model.addAttribute("batchOptions", batches);
        model.addAttribute("batchMap", batchMap);
        int lowStockThreshold = branchSettingsAccessService.findByBusinessIdAndBranchId(setup.businessId(), setup.branchId())
                .map(value -> value.lowStockAlertQuantity() == null ? 0 : value.lowStockAlertQuantity())
                .orElse(0);
        model.addAttribute("counterOptions", counters);
        model.addAttribute("currentActorId", principal.ownerId());
        model.addAttribute("lowStockThreshold", lowStockThreshold);
        model.addAttribute("draftKey", UUID.randomUUID().toString());
    }

    private String sortParam(Pageable pageable, String fallback) {
        return pageable.getSort().stream().findFirst()
                .map(order -> order.getProperty() + "," + order.getDirection().name().toLowerCase())
                .orElse(fallback);
    }

    private BigDecimal safe(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value.trim());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Inventory date filters must use ISO-8601 UTC format, for example 2026-08-30T00:00:00Z", ex);
        }
    }

    private BusinessSetupResponse setup(OwnerPrincipal principal) {
        BusinessSetupResponse setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        if (setup.businessId() == null || setup.branchId() == null) throw new InventoryAccessDeniedException();
        return setup;
    }

    private InventoryMetrics metrics(Long ownerId, BusinessSetupResponse setup, List<BranchProductStockResponse> stocks, List<ProductBatchResponse> batches) {
        List<ProductVariantAccessResponse> variants = productAccessService.findActiveVariantsForBranch(setup.businessId(), setup.branchId());
        Map<Long, ProductVariantAccessResponse> byId = variants.stream().collect(Collectors.toMap(ProductVariantAccessResponse::variantId, Function.identity()));
        Map<Long, BranchProductStockResponse> stockByVariant = stocks.stream().collect(Collectors.toMap(BranchProductStockResponse::getProductVariantId, Function.identity(), (a,b) -> a));
        var branchSettings = branchSettingsAccessService
                .findByBusinessIdAndBranchId(setup.businessId(), setup.branchId()).orElse(null);
        java.time.ZoneId branchZone = branchSettings == null || branchSettings.timeZone() == null
                ? java.time.ZoneId.systemDefault() : java.time.ZoneId.of(branchSettings.timeZone());
        LocalDate today = LocalDate.now(branchZone);
        boolean nearExpiryWarningsEnabled = branchSettings == null
                || !Boolean.FALSE.equals(branchSettings.nearExpiryWarningsEnabled());
        int expiryWarningDays = branchSettings == null || branchSettings.defaultExpiryAlertDays() == null
                ? 30 : branchSettings.defaultExpiryAlertDays();
        BigDecimal qty = stocks.stream().map(stock -> {
            ProductVariantAccessResponse variant = byId.get(stock.getProductVariantId());
            if (variant == null || (!variant.trackExpiry() && !variant.batchTrackingRequired())) return stock.getAvailableBaseQuantity();
            return batches.stream().filter(b -> b.getProductVariantId().equals(stock.getProductVariantId()))
                    .filter(b -> b.getStatus() == ProductBatchStatus.ACTIVE)
                    .filter(b -> b.getExpiryDate() == null || !b.getExpiryDate().isBefore(today))
                    .map(ProductBatchResponse::getSellableBaseQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        }).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal value = stocks.stream().map(BranchProductStockResponse::getInventoryValue).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        int threshold = branchSettings == null || branchSettings.lowStockAlertQuantity() == null
                ? 0 : branchSettings.lowStockAlertQuantity();
        long outOfStock = variants.stream().filter(v -> {
            BranchProductStockResponse s = stockByVariant.get(v.variantId());
            return s == null || s.getAvailableBaseQuantity() == null || s.getAvailableBaseQuantity().signum() == 0;
        }).count();
        long lowStock = variants.stream().filter(v -> {
            BranchProductStockResponse s = stockByVariant.get(v.variantId());
            return s != null && s.getAvailableBaseQuantity() != null && s.getAvailableBaseQuantity().signum() > 0
                    && s.getAvailableBaseQuantity().compareTo(BigDecimal.valueOf(threshold)) <= 0;
        }).count();
        long expiring = nearExpiryWarningsEnabled ? batches.stream()
                .filter(b -> b.getExpiryDate() != null && !b.getExpiryDate().isBefore(today))
                .filter(b -> ChronoUnit.DAYS.between(today, b.getExpiryDate()) <= expiryWarningDays)
                .filter(b -> b.getSellableBaseQuantity() != null && b.getSellableBaseQuantity().signum() > 0)
                .count() : 0;
        long expired = batches.stream().filter(b -> b.getExpiryDate() != null && b.getExpiryDate().isBefore(today)
                && b.getAvailableBaseQuantity() != null && b.getAvailableBaseQuantity().signum() > 0).count();
        long pendingCounts = countService.list(ownerId, setup.branchId()).stream().filter(c -> c.getStatus() != InventoryOperationStatus.POSTED && c.getStatus() != InventoryOperationStatus.REJECTED && c.getStatus() != InventoryOperationStatus.CANCELLED).count();
        long pendingAdjustments = adjustmentService.list(ownerId, setup.branchId()).stream().filter(a -> a.getStatus() == InventoryOperationStatus.SUBMITTED || a.getStatus() == InventoryOperationStatus.APPROVED).count();
        long pendingTransfers = transferService.list(ownerId, setup.branchId()).stream().filter(t -> t.getStatus() != BranchTransferStatus.RECEIVED && t.getStatus() != BranchTransferStatus.REJECTED && t.getStatus() != BranchTransferStatus.CANCELLED).count();
        long pendingWastage = lossService.list(ownerId, setup.branchId()).stream().filter(l -> l.getStatus() == InventoryOperationStatus.SUBMITTED || l.getStatus() == InventoryOperationStatus.APPROVED).count();
        return new InventoryMetrics(variants.size(), qty, value, lowStock, outOfStock, expiring, expired, pendingCounts, pendingAdjustments, pendingTransfers, pendingWastage);
    }

    public record InventoryMetrics(long activeVariants, BigDecimal sellableBaseQuantity, BigDecimal inventoryValue,
                                   long lowStockItems, long outOfStockItems, long expiringBatches, long expiredBatches,
                                   long pendingCounts, long pendingAdjustments, long pendingTransfers, long pendingWastageApprovals) {}
}
