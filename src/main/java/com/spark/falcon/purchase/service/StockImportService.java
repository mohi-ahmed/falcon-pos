package com.spark.falcon.purchase.service;

import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.inventory.dto.BranchProductStockResponse;
import com.spark.falcon.inventory.dto.InventoryPostingResponse;
import com.spark.falcon.inventory.dto.ProductBatchResponse;
import com.spark.falcon.inventory.dto.StockImportPostingRequest;
import com.spark.falcon.inventory.dto.StockMovementReversalRequest;
import com.spark.falcon.inventory.entity.InventoryActorType;
import com.spark.falcon.inventory.exception.InventoryPostingException;
import com.spark.falcon.inventory.service.InventoryPostingService;
import com.spark.falcon.inventory.service.InventoryQueryService;
import com.spark.falcon.product.dto.response.ProductUnitConversionResponse;
import com.spark.falcon.product.dto.response.ProductVariantAccessResponse;
import com.spark.falcon.product.service.ProductAccessService;
import com.spark.falcon.purchase.dto.command.ConfirmStockImportCommand;
import com.spark.falcon.purchase.dto.command.ReverseStockImportCommand;
import com.spark.falcon.purchase.dto.command.StockImportUploadCommand;
import com.spark.falcon.purchase.dto.response.StockImportBatchResponse;
import com.spark.falcon.purchase.dto.response.StockImportTemplateResponse;
import com.spark.falcon.purchase.entity.PurchaseAuditEvent;
import com.spark.falcon.purchase.entity.StockImportBatch;
import com.spark.falcon.purchase.entity.StockImportRow;
import com.spark.falcon.purchase.entity.enumtype.PurchaseActorType;
import com.spark.falcon.purchase.entity.enumtype.PurchaseAuditAction;
import com.spark.falcon.purchase.entity.enumtype.StockImportPurpose;
import com.spark.falcon.purchase.entity.enumtype.StockImportStatus;
import com.spark.falcon.purchase.exception.PurchaseAccessDeniedException;
import com.spark.falcon.purchase.exception.StockImportNotFoundException;
import com.spark.falcon.purchase.exception.StockImportValidationException;
import com.spark.falcon.purchase.mapper.PurchaseMapper;
import com.spark.falcon.purchase.repository.PurchaseAuditEventRepository;
import com.spark.falcon.purchase.repository.StockImportBatchRepository;
import com.spark.falcon.purchase.repository.StockImportRowRepository;
import com.spark.falcon.purchase.validation.PurchaseValidator;
import com.spark.falcon.settings.dto.response.UnitResponse;
import com.spark.falcon.settings.service.UnitAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StockImportService {

    private static final int QUANTITY_SCALE = PurchaseValidator.QUANTITY_SCALE;
    private static final int MONEY_SCALE = PurchaseValidator.MONEY_SCALE;
    private static final Set<StockImportStatus> POSTED_FILE_STATUSES = Set.of(
            StockImportStatus.POSTED, StockImportStatus.REVERSED);

    private final StockImportBatchRepository batchRepository;
    private final StockImportRowRepository rowRepository;
    private final PurchaseAuditEventRepository auditRepository;
    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final ProductAccessService productAccessService;
    private final UnitAccessService unitAccessService;
    private final InventoryQueryService inventoryQueryService;
    private final InventoryPostingService inventoryPostingService;
    private final StockImportCsvParser csvParser;
    private final PurchaseMapper mapper;
    private final Clock clock;

    @Transactional
    public StockImportBatchResponse uploadAndValidate(StockImportUploadCommand command) {
        validateUploadCommand(command);
        BusinessAccessResponse business = requireBusiness(command.ownerId());
        requireBranch(business.businessId(), command.branchId());

        StockImportBatch repeated = batchRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                business.businessId(), command.branchId(), command.idempotencyKey().trim()).orElse(null);
        if (repeated != null) return response(repeated);

        String fingerprint = sha256(command.fileContent());
        if (batchRepository.existsByBusinessIdAndBranchIdAndImportPurposeAndFileFingerprintAndStatusIn(
                business.businessId(), command.branchId(), command.importPurpose(), fingerprint,
                POSTED_FILE_STATUSES)) {
            throw new StockImportValidationException(
                    "This Stock Import file has already been posted for the selected Branch and Import Purpose");
        }

        Instant now = Instant.now(clock);
        StockImportBatch batch = StockImportBatch.uploaded(
                business.businessId(), command.branchId(), command.importPurpose(), safeFileName(command.fileName()),
                fingerprint, command.idempotencyKey().trim(), PurchaseActorType.OWNER, command.ownerId(), now);
        batch = batchRepository.saveAndFlush(batch);

        auditRepository.save(PurchaseAuditEvent.record(
                business.businessId(), command.branchId(), null, PurchaseAuditAction.STOCK_IMPORT_UPLOADED,
                "STOCK_IMPORT", batch.getId(), PurchaseActorType.OWNER, command.ownerId(),
                "Stock Import file uploaded for validation", now));

        batch.startValidation();
        batchRepository.saveAndFlush(batch);
        rowRepository.deleteByStockImportBatchId(batch.getId());
        rowRepository.flush();

        List<StockImportCsvParser.ParsedCsvRow> parsedRows;
        try {
            parsedRows = csvParser.parse(command.fileContent());
        } catch (StockImportValidationException exception) {
            batch.completeValidation(0, 0, 0, exception.getMessage());
            batchRepository.saveAndFlush(batch);
            auditRepository.save(PurchaseAuditEvent.record(
                    business.businessId(), command.branchId(), null, PurchaseAuditAction.STOCK_IMPORT_VALIDATED,
                    "STOCK_IMPORT", batch.getId(), PurchaseActorType.OWNER, command.ownerId(),
                    exception.getMessage(), Instant.now(clock)));
            return response(batch);
        }

        int valid = 0;
        int failed = 0;
        Set<String> rowIdentities = new HashSet<>();
        List<String> errors = new ArrayList<>();

        for (StockImportCsvParser.ParsedCsvRow parsed : parsedRows) {
            StockImportRow row = null;
            try {
                row = parseRow(batch.getId(), parsed, now);
                validateRow(business.businessId(), batch, row, parsed, rowIdentities);
                valid++;
            } catch (RuntimeException exception) {
                if (row == null) {
                    row = StockImportRow.parsed(
                            batch.getId(), parsed.rowNumber(), null,
                            parsed.value("variant_sku", "sku", "variant_code"), null, null, null, null,
                            parsed.value("batch_number", "lot_number", "batch"), null, null, now);
                }
                row.markInvalid(cleanMessage(exception));
                failed++;
                if (errors.size() < 20) errors.add("Row " + parsed.rowNumber() + ": " + cleanMessage(exception));
            }
            rowRepository.save(row);
        }
        rowRepository.flush();

        String errorSummary = errors.isEmpty() ? null : String.join(" | ", errors);
        batch.completeValidation(parsedRows.size(), valid, failed, errorSummary);
        batch = batchRepository.saveAndFlush(batch);
        auditRepository.save(PurchaseAuditEvent.record(
                business.businessId(), command.branchId(), null, PurchaseAuditAction.STOCK_IMPORT_VALIDATED,
                "STOCK_IMPORT", batch.getId(), PurchaseActorType.OWNER, command.ownerId(),
                "Rows=" + parsedRows.size() + ", valid=" + valid + ", failed=" + failed,
                Instant.now(clock)));
        return response(batch);
    }

    @Transactional
    public StockImportBatchResponse confirm(ConfirmStockImportCommand command) {
        if (command == null || command.ownerId() == null || command.stockImportBatchId() == null) {
            throw new StockImportValidationException("Stock Import confirmation request is incomplete");
        }
        BusinessAccessResponse business = requireBusiness(command.ownerId());
        StockImportBatch existing = batchRepository.findById(command.stockImportBatchId())
                .filter(value -> value.getBusinessId().equals(business.businessId()))
                .orElseThrow(StockImportNotFoundException::new);
        requireBranch(business.businessId(), existing.getBranchId());

        StockImportBatch batch = batchRepository.findForUpdate(
                        business.businessId(), existing.getBranchId(), existing.getId())
                .orElseThrow(StockImportNotFoundException::new);
        if (batch.getStatus() == StockImportStatus.POSTED || batch.getStatus() == StockImportStatus.REVERSED) {
            return response(batch);
        }
        if (batch.getStatus() != StockImportStatus.READY_FOR_CONFIRMATION) {
            throw new StockImportValidationException("Only a validated Stock Import can be confirmed");
        }
        if (batchRepository.existsByBusinessIdAndBranchIdAndImportPurposeAndFileFingerprintAndIdNotAndStatusIn(
                batch.getBusinessId(), batch.getBranchId(), batch.getImportPurpose(), batch.getFileFingerprint(),
                batch.getId(), POSTED_FILE_STATUSES)) {
            throw new StockImportValidationException(
                    "This Stock Import file has already been posted for the selected Branch and Import Purpose");
        }

        List<StockImportRow> rows = rowRepository.findByStockImportBatchIdOrderByRowNumberAsc(batch.getId());
        if (rows.isEmpty() || rows.stream().anyMatch(row -> row.getStatus() != com.spark.falcon.purchase.entity.enumtype.StockImportRowStatus.VALID)) {
            throw new StockImportValidationException("Every Stock Import row must be valid before confirmation");
        }

        revalidateConfirmationSnapshots(batch, rows);

        int posted = 0;
        for (StockImportRow row : rows) {
            InventoryPostingResponse posting;
            try {
                posting = inventoryPostingService.postStockImport(
                        toPostingRequest(batch, row, command.ownerId()), row.getCurrentSystemQuantity());
            } catch (InventoryPostingException exception) {
                throw new StockImportValidationException(exception.getMessage());
            }
            BigDecimal resultingQuantity = posting.getBatch() != null
                    ? posting.getBatch().getAvailableBaseQuantity()
                    : posting.getStock().getBaseQuantity();
            row.markPosted(
                    posting.getMovement().getId(), resultingQuantity,
                    posting.getMovement().getInventoryValueChange());
            rowRepository.saveAndFlush(row);
            posted++;
        }

        Instant now = Instant.now(clock);
        batch.markPosted(posted, command.ownerId(), now);
        batch = batchRepository.saveAndFlush(batch);
        auditRepository.save(PurchaseAuditEvent.record(
                business.businessId(), batch.getBranchId(), null, PurchaseAuditAction.STOCK_IMPORT_POSTED,
                "STOCK_IMPORT", batch.getId(), PurchaseActorType.OWNER, command.ownerId(),
                "Stock Import posted atomically with " + posted + " Stock Movements", now));
        return response(batch);
    }

    @Transactional
    public StockImportBatchResponse reverse(ReverseStockImportCommand command) {
        if (command == null || command.ownerId() == null || command.stockImportBatchId() == null
                || command.reason() == null || command.reason().isBlank()) {
            throw new StockImportValidationException("Stock Import reversal reason is required");
        }
        BusinessAccessResponse business = requireBusiness(command.ownerId());
        StockImportBatch existing = batchRepository.findById(command.stockImportBatchId())
                .filter(value -> value.getBusinessId().equals(business.businessId()))
                .orElseThrow(StockImportNotFoundException::new);
        requireBranch(business.businessId(), existing.getBranchId());

        StockImportBatch batch = batchRepository.findForUpdate(
                        business.businessId(), existing.getBranchId(), existing.getId())
                .orElseThrow(StockImportNotFoundException::new);
        if (batch.getStatus() == StockImportStatus.REVERSED) return response(batch);
        if (batch.getStatus() != StockImportStatus.POSTED) {
            throw new StockImportValidationException("Only a Posted Stock Import can be reversed");
        }

        List<StockImportRow> rows = rowRepository.findByStockImportBatchIdOrderByRowNumberAsc(batch.getId());
        for (StockImportRow row : rows) {
            if (row.getPostedMovementId() == null) {
                throw new StockImportValidationException("Stock Import history is missing a posted Stock Movement reference");
            }
            StockMovementReversalRequest request = new StockMovementReversalRequest();
            request.setBusinessId(business.businessId());
            request.setBranchId(batch.getBranchId());
            request.setOriginalMovementId(row.getPostedMovementId());
            request.setSourceReferenceId(String.valueOf(batch.getId()));
            request.setSourceLineReference(String.valueOf(row.getRowNumber()));
            request.setPostingKey(limitedKey("SIR:" + batch.getId() + ":" + row.getRowNumber()));
            request.setActorType(InventoryActorType.OWNER);
            request.setActorId(command.ownerId());
            request.setReason(command.reason().trim());
            request.setNotes("Reverse Stock Import Batch " + batch.getId());
            inventoryPostingService.reverseStockMovement(request);
            row.markReversed();
            rowRepository.saveAndFlush(row);
        }

        Instant now = Instant.now(clock);
        batch.markReversed(now);
        batch = batchRepository.saveAndFlush(batch);
        auditRepository.save(PurchaseAuditEvent.record(
                business.businessId(), batch.getBranchId(), null, PurchaseAuditAction.STOCK_IMPORT_REVERSED,
                "STOCK_IMPORT", batch.getId(), PurchaseActorType.OWNER, command.ownerId(),
                command.reason().trim(), now));
        return response(batch);
    }

    @Transactional(readOnly = true)
    public StockImportBatchResponse findByOwnerAndId(Long ownerId, Long batchId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        StockImportBatch batch = batchRepository.findById(batchId)
                .filter(value -> value.getBusinessId().equals(business.businessId()))
                .orElseThrow(StockImportNotFoundException::new);
        requireBranch(business.businessId(), batch.getBranchId());
        return response(batch);
    }

    @Transactional(readOnly = true)
    public Page<StockImportBatchResponse> findHistory(Long ownerId, Long branchId, Pageable pageable) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        return batchRepository.findByBusinessIdAndBranchIdOrderByUploadedAtDesc(
                        business.businessId(), branchId, pageable)
                .map(this::response);
    }

    @Transactional(readOnly = true)
    public Page<StockImportBatchResponse> searchHistory(Long ownerId, Long branchId, StockImportPurpose purpose,
                                                         StockImportStatus status, Long uploadedBy, Long confirmedBy,
                                                         Instant uploadedFrom, Instant uploadedTo, Instant confirmedFrom,
                                                         Instant confirmedTo, Boolean reversed, Long batchId, String query,
                                                         Pageable pageable) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        return batchRepository.search(business.businessId(), branchId, purpose, status, uploadedBy, confirmedBy,
                uploadedFrom, uploadedTo, confirmedFrom, confirmedTo, reversed, batchId,
                query == null ? "" : query.trim(), pageable).map(this::response);
    }

    @Transactional(readOnly = true)
    public StockImportTemplateResponse generateTemplate(
            Long ownerId, Long branchId, StockImportPurpose purpose) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        if (purpose == null) throw new StockImportValidationException("Import Purpose is required");

        Map<Long, UnitResponse> units = new LinkedHashMap<>();
        for (UnitResponse unit : unitAccessService.findActiveForBranch(business.businessId(), branchId)) {
            units.put(unit.id(), unit);
        }

        StringBuilder csv = new StringBuilder();
        csv.append("variant_sku,product_name,variant_name,entered_unit_code,entered_quantity,conversion_factor,")
                .append("current_system_quantity,original_purchase_unit_cost,landed_base_unit_cost,")
                .append("batch_number,manufacturing_date,expiry_date\n");

        LocalDate today = branchDate(business.businessId(), branchId);
        for (ProductVariantAccessResponse variant : productAccessService.findActiveVariantsForBranch(
                business.businessId(), branchId)) {
            Long enteredUnitId = variant.purchaseUnitId() == null
                    ? variant.baseInventoryUnitId() : variant.purchaseUnitId();
            UnitResponse unit = units.get(enteredUnitId);
            String unitCode = unit == null ? String.valueOf(enteredUnitId) : unit.code();
            BigDecimal factor = resolveConversionFactor(
                    business.businessId(), branchId, variant, enteredUnitId, today).factor();
            BigDecimal current = inventoryQueryService.findStock(
                            business.businessId(), branchId, variant.variantId())
                    .map(BranchProductStockResponse::getBaseQuantity)
                    .orElse(quantityZero());

            csv.append(csvCell(variant.sku())).append(',')
                    .append(csvCell(variant.productName())).append(',')
                    .append(csvCell(variant.variantName())).append(',')
                    .append(csvCell(unitCode)).append(',')
                    .append(',')
                    .append(factor.toPlainString()).append(',')
                    .append(current.toPlainString()).append(',')
                    .append(',').append(',').append(',').append(',').append('\n');
        }

        String fileName = "stock-import-" + purpose.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".csv";
        return new StockImportTemplateResponse(fileName, csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private StockImportRow parseRow(Long batchId, StockImportCsvParser.ParsedCsvRow parsed, Instant now) {
        String sku = parsed.value("variant_sku", "sku", "variant_code");
        Long variantId = parseLongOptional(parsed.value("product_variant_id", "variant_id"), "Product Variant ID");
        Long enteredUnitId = parseLongOptional(parsed.value("entered_unit_id", "unit_id"), "Entered Unit ID");
        BigDecimal enteredQuantity = parseDecimalOptional(
                parsed.value("entered_quantity", "quantity", "counted_quantity"), "Entered Quantity", QUANTITY_SCALE);
        BigDecimal originalCost = parseDecimalOptional(
                parsed.value("original_purchase_unit_cost", "unit_cost", "purchase_unit_cost"),
                "Original Purchase Unit Cost", MONEY_SCALE);
        BigDecimal landedCost = parseDecimalOptional(
                parsed.value("landed_base_unit_cost", "allocated_landed_unit_cost", "landed_unit_cost"),
                "Landed Base Unit Cost", MONEY_SCALE);
        LocalDate manufacturingDate = parseDateOptional(parsed.value("manufacturing_date", "mfg_date"), "Manufacturing Date");
        LocalDate expiryDate = parseDateOptional(parsed.value("expiry_date", "best_before_date", "best_before"), "Expiry Date");
        return StockImportRow.parsed(
                batchId, parsed.rowNumber(), variantId, sku, enteredUnitId, enteredQuantity,
                originalCost, landedCost, parsed.value("batch_number", "lot_number", "batch"),
                manufacturingDate, expiryDate, now);
    }

    private void validateRow(Long businessId, StockImportBatch batch, StockImportRow row,
                             StockImportCsvParser.ParsedCsvRow parsed, Set<String> identities) {
        ProductVariantAccessResponse variant = resolveVariant(businessId, batch.getBranchId(), row);
        Long enteredUnitId = resolveUnitId(businessId, batch.getBranchId(), variant, row, parsed);
        BigDecimal enteredQuantity = row.getEnteredQuantity();
        if (enteredQuantity == null) throw new StockImportValidationException("Entered Quantity is required");
        if (batch.getImportPurpose() == StockImportPurpose.APPROVED_BULK_STOCK_ADJUSTMENT) {
            if (enteredQuantity.signum() < 0) throw new StockImportValidationException("Counted Quantity must not be negative");
        } else if (enteredQuantity.signum() <= 0) {
            throw new StockImportValidationException("Entered Quantity must be greater than zero");
        }

        LocalDate today = branchDate(businessId, batch.getBranchId());
        ResolvedConversion conversion = resolveConversionFactor(
                businessId, batch.getBranchId(), variant, enteredUnitId, today);
        validateQuantityPrecision(enteredQuantity, conversion.decimalPrecision());

        BigDecimal providedFactor = parseDecimalOptional(
                parsed.value("conversion_factor"), "Conversion Factor", QUANTITY_SCALE);
        if (providedFactor != null && providedFactor.compareTo(conversion.factor()) != 0) {
            throw new StockImportValidationException(
                    "CSV Conversion Factor does not match the active Product Unit Conversion");
        }

        BigDecimal baseQuantity = enteredQuantity.multiply(conversion.factor())
                .setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
        if (baseQuantity.signum() < 0) throw new StockImportValidationException("Base Quantity is invalid");

        ProductBatchResponse existingBatch = null;
        BigDecimal currentSystemQuantity;
        boolean batchControlled = variant.trackExpiry() || variant.batchTrackingRequired();
        if (batchControlled) {
            validateBatchFields(variant, row);
            existingBatch = inventoryQueryService.findBatchByNumber(
                    businessId, batch.getBranchId(), variant.variantId(), row.getBatchNumber()).orElse(null);
            currentSystemQuantity = existingBatch == null ? quantityZero() : existingBatch.getAvailableBaseQuantity();
            if (batch.getImportPurpose() == StockImportPurpose.APPROVED_BULK_STOCK_ADJUSTMENT
                    && existingBatch == null) {
                throw new StockImportValidationException(
                        "Approved Bulk Stock Adjustment requires an existing batch for batch-controlled Product Variant");
            }
        } else {
            currentSystemQuantity = inventoryQueryService.findStock(
                            businessId, batch.getBranchId(), variant.variantId())
                    .map(BranchProductStockResponse::getBaseQuantity)
                    .orElse(quantityZero());
        }

        if (batch.getImportPurpose() == StockImportPurpose.OPENING_STOCK
                && inventoryQueryService.hasPostedStockHistory(businessId, batch.getBranchId(), variant.variantId())) {
            throw new StockImportValidationException(
                    "Opening Stock is allowed only when the Product Variant has no previous posted stock history");
        }

        BigDecimal difference = batch.getImportPurpose() == StockImportPurpose.OPENING_STOCK
                ? baseQuantity
                : baseQuantity.subtract(currentSystemQuantity).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
        if (difference.signum() == 0) {
            throw new StockImportValidationException("Imported quantity produces no Stock Movement");
        }

        BigDecimal landedCost = row.getLandedBaseUnitCost();
        if (batch.getImportPurpose() == StockImportPurpose.OPENING_STOCK
                || batch.getImportPurpose() == StockImportPurpose.INVENTORY_MIGRATION) {
            if (landedCost == null || landedCost.signum() < 0) {
                throw new StockImportValidationException(
                        "Opening Stock and Inventory Migration require an approved Landed Base Unit Cost");
            }
        } else if (landedCost != null && landedCost.signum() < 0) {
            throw new StockImportValidationException("Landed Base Unit Cost must not be negative");
        }
        if (row.getOriginalPurchaseUnitCost() != null && row.getOriginalPurchaseUnitCost().signum() < 0) {
            throw new StockImportValidationException("Original Purchase Unit Cost must not be negative");
        }

        String identity = variant.variantId() + "|" + (batchControlled
                ? row.getBatchNumber().trim().toLowerCase(Locale.ROOT) : "NO_BATCH");
        if (!identities.add(identity)) {
            throw new StockImportValidationException("Duplicate Product Variant / Batch row in the same CSV file");
        }

        row.markValid(conversion.factor(), variant.baseInventoryUnitId(), baseQuantity,
                currentSystemQuantity, difference);
    }

    private void revalidateConfirmationSnapshots(StockImportBatch batch, List<StockImportRow> rows) {
        LocalDate postingDate = branchDate(batch.getBusinessId(), batch.getBranchId());
        for (StockImportRow row : rows) {
            ProductVariantAccessResponse variant = productAccessService.findActiveVariantForBranch(
                            batch.getBusinessId(), batch.getBranchId(), row.getProductVariantId())
                    .orElseThrow(() -> new StockImportValidationException(
                            "Product Variant became inactive before Stock Import confirmation"));

            if (!variant.baseInventoryUnitId().equals(row.getBaseInventoryUnitId())) {
                throw new StockImportValidationException(
                        "Product Variant Base Inventory Unit changed after Stock Import validation");
            }
            if (unitAccessService.findActiveForBranch(
                    batch.getBusinessId(), batch.getBranchId(), row.getEnteredUnitId()).isEmpty()) {
                throw new StockImportValidationException(
                        "Entered Unit became inactive or unavailable after Stock Import validation");
            }

            ResolvedConversion conversion = resolveConversionFactor(
                    batch.getBusinessId(), batch.getBranchId(), variant, row.getEnteredUnitId(), postingDate);
            if (row.getConversionFactor() == null
                    || conversion.factor().compareTo(row.getConversionFactor()) != 0) {
                throw new StockImportValidationException(
                        "Product Unit Conversion changed after Stock Import validation; validate the Import Batch again");
            }

            BigDecimal confirmedBaseQuantity = row.getEnteredQuantity().multiply(conversion.factor())
                    .setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
            if (row.getBaseQuantity() == null || confirmedBaseQuantity.compareTo(row.getBaseQuantity()) != 0) {
                throw new StockImportValidationException(
                        "Converted Base Quantity no longer matches the validated Stock Import snapshot");
            }

            boolean batchControlled = variant.trackExpiry() || variant.batchTrackingRequired();
            if (batchControlled) {
                validateBatchFields(variant, row);
                ProductBatchResponse currentBatch = inventoryQueryService.findBatchByNumber(
                        batch.getBusinessId(), batch.getBranchId(), variant.variantId(), row.getBatchNumber())
                        .orElse(null);
                if (batch.getImportPurpose() == StockImportPurpose.APPROVED_BULK_STOCK_ADJUSTMENT
                        && currentBatch == null) {
                    throw new StockImportValidationException(
                            "Approved Bulk Stock Adjustment requires the validated existing batch");
                }
            }

            if (batch.getImportPurpose() == StockImportPurpose.OPENING_STOCK
                    && inventoryQueryService.hasPostedStockHistory(
                    batch.getBusinessId(), batch.getBranchId(), variant.variantId())) {
                throw new StockImportValidationException(
                        "Opening Stock is no longer eligible because posted stock history now exists");
            }
        }
    }

    private StockImportPostingRequest toPostingRequest(StockImportBatch batch, StockImportRow row, Long ownerId) {
        ProductVariantAccessResponse variant = productAccessService.findActiveVariantForBranch(
                        batch.getBusinessId(), batch.getBranchId(), row.getProductVariantId())
                .orElseThrow(() -> new StockImportValidationException(
                        "Product Variant became inactive before Stock Import confirmation"));

        ProductBatchResponse existingBatch = null;
        if (variant.trackExpiry() || variant.batchTrackingRequired()) {
            existingBatch = inventoryQueryService.findBatchByNumber(
                    batch.getBusinessId(), batch.getBranchId(), row.getProductVariantId(), row.getBatchNumber())
                    .orElse(null);
        }

        StockImportPostingRequest request = new StockImportPostingRequest();
        request.setBusinessId(batch.getBusinessId());
        request.setBranchId(batch.getBranchId());
        request.setProductVariantId(row.getProductVariantId());
        request.setEnteredQuantity(row.getEnteredQuantity());
        request.setEnteredUnitId(row.getEnteredUnitId());
        request.setConversionFactorSnapshot(row.getConversionFactor());
        request.setBaseInventoryUnitIdSnapshot(row.getBaseInventoryUnitId());
        request.setBaseQuantityChange(row.getQuantityDifference());
        request.setLandedBaseUnitCost(resolvePostingCost(batch, row));
        request.setOriginalPurchaseUnitCost(row.getOriginalPurchaseUnitCost());
        request.setExistingProductBatchId(existingBatch == null ? null : existingBatch.getId());
        request.setBatchNumber(row.getBatchNumber());
        request.setManufacturingDate(row.getManufacturingDate());
        request.setExpiryDate(row.getExpiryDate());
        request.setNonSellableImport(variant.trackExpiry() && row.getExpiryDate() != null
                && !row.getExpiryDate().isAfter(branchDate(batch.getBusinessId(), batch.getBranchId())));
        request.setSourceReferenceId(String.valueOf(batch.getId()));
        request.setSourceLineReference(String.valueOf(row.getRowNumber()));
        request.setPostingKey(limitedKey("SI:" + batch.getId() + ":" + row.getRowNumber()));
        request.setActorType(InventoryActorType.OWNER);
        request.setActorId(ownerId);
        request.setReason(batch.getImportPurpose().name());
        request.setNotes("Stock Import Batch " + batch.getId());
        return request;
    }

    private BigDecimal resolvePostingCost(StockImportBatch batch, StockImportRow row) {
        if (row.getQuantityDifference().signum() <= 0) return null;
        if (batch.getImportPurpose() != StockImportPurpose.APPROVED_BULK_STOCK_ADJUSTMENT) {
            return row.getLandedBaseUnitCost();
        }
        return inventoryQueryService.findStock(batch.getBusinessId(), batch.getBranchId(), row.getProductVariantId())
                .map(BranchProductStockResponse::getWeightedAverageCost)
                .orElse(moneyZero());
    }

    private ProductVariantAccessResponse resolveVariant(Long businessId, Long branchId, StockImportRow row) {
        ProductVariantAccessResponse variant = null;
        if (row.getProductVariantId() != null) {
            variant = productAccessService.findActiveVariantForBranch(
                    businessId, branchId, row.getProductVariantId()).orElse(null);
        }
        if (variant == null && row.getVariantSku() != null) {
            variant = productAccessService.findActiveVariantBySkuForBranch(
                    businessId, branchId, row.getVariantSku()).orElse(null);
        }
        if (variant == null) {
            throw new StockImportValidationException("Unknown or inactive Product Variant / SKU for selected Branch");
        }
        if (row.getProductVariantId() != null && row.getVariantSku() != null
                && !variant.sku().equalsIgnoreCase(row.getVariantSku())) {
            throw new StockImportValidationException("Product Variant ID and SKU identify different Product Variants");
        }
        row.resolveVariant(variant.variantId(), variant.sku());
        return variant;
    }

    private Long resolveUnitId(Long businessId, Long branchId, ProductVariantAccessResponse variant,
                               StockImportRow row, StockImportCsvParser.ParsedCsvRow parsed) {
        Long unitId = row.getEnteredUnitId();
        String unitCode = parsed.value("entered_unit_code", "unit_code", "unit");
        if (unitId == null && unitCode != null) {
            unitId = unitAccessService.findActiveForBranch(businessId, branchId).stream()
                    .filter(unit -> unit.code().equalsIgnoreCase(unitCode) || unit.name().equalsIgnoreCase(unitCode))
                    .map(UnitResponse::id)
                    .findFirst()
                    .orElseGet(() -> parseLongOptional(unitCode, "Entered Unit"));
        }
        if (unitId == null) unitId = variant.baseInventoryUnitId();

        UnitResponse activeUnit = unitAccessService.findActiveForBranch(businessId, branchId, unitId)
                .orElseThrow(() -> new StockImportValidationException(
                        "Entered Unit is inactive or unavailable for selected Branch"));
        row.resolveEnteredUnit(activeUnit.id());
        return activeUnit.id();
    }

    private ResolvedConversion resolveConversionFactor(Long businessId, Long branchId,
                                                        ProductVariantAccessResponse variant,
                                                        Long enteredUnitId, LocalDate effectiveDate) {
        if (enteredUnitId.equals(variant.baseInventoryUnitId())) {
            return new ResolvedConversion(BigDecimal.ONE.setScale(QUANTITY_SCALE), QUANTITY_SCALE);
        }
        ProductUnitConversionResponse conversion = productAccessService.findEffectiveConversion(
                        businessId, branchId, variant.variantId(), enteredUnitId, effectiveDate)
                .orElseThrow(() -> new StockImportValidationException(
                        "Missing, inactive, conflicting or ineffective Product Unit Conversion"));
        if (!conversion.targetUnitId().equals(variant.baseInventoryUnitId())
                || conversion.conversionFactor() == null || conversion.conversionFactor().signum() <= 0) {
            throw new StockImportValidationException("Product Unit Conversion does not target the Base Inventory Unit");
        }
        return new ResolvedConversion(
                conversion.conversionFactor().setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY),
                conversion.decimalPrecision());
    }

    private void validateBatchFields(ProductVariantAccessResponse variant, StockImportRow row) {
        if (row.getBatchNumber() == null || row.getBatchNumber().isBlank()) {
            throw new StockImportValidationException("Batch Number is required for batch-controlled Product Variant");
        }
        if (!variant.trackExpiry()) return;
        if (row.getManufacturingDate() == null || row.getExpiryDate() == null) {
            throw new StockImportValidationException(
                    "Manufacturing Date and Expiry / Best-Before Date are required for expiry-controlled Product Variant");
        }
        if (!row.getExpiryDate().isAfter(row.getManufacturingDate())) {
            throw new StockImportValidationException("Expiry Date must be later than Manufacturing Date");
        }
    }

    private void validateQuantityPrecision(BigDecimal quantity, int allowedPrecision) {
        int scale = Math.max(0, quantity.stripTrailingZeros().scale());
        if (scale > Math.max(0, allowedPrecision)) {
            throw new StockImportValidationException("Entered Quantity exceeds the allowed unit decimal precision");
        }
    }

    private void validateUploadCommand(StockImportUploadCommand command) {
        if (command == null || command.ownerId() == null || command.branchId() == null
                || command.importPurpose() == null) {
            throw new StockImportValidationException("Stock Import request is incomplete");
        }
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().trim().length() > 100) {
            throw new StockImportValidationException("Stock Import idempotency key is required and must be at most 100 characters");
        }
        if (command.fileContent() == null || command.fileContent().length == 0) {
            throw new StockImportValidationException("Stock Import CSV file is required");
        }
        String fileName = safeFileName(command.fileName()).toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".csv")) {
            throw new StockImportValidationException("Stock Import file must be a CSV file");
        }
    }

    private StockImportBatchResponse response(StockImportBatch batch) {
        return mapper.toResponse(batch, rowRepository.findByStockImportBatchIdOrderByRowNumberAsc(batch.getId()));
    }

    private BusinessAccessResponse requireBusiness(Long ownerId) {
        return businessAccessService.findByOwnerId(ownerId)
                .orElseThrow(PurchaseAccessDeniedException::new);
    }

    private void requireBranch(Long businessId, Long branchId) {
        if (branchId == null || branchAccessService.findActiveByBusinessIdAndBranchId(businessId, branchId).isEmpty()) {
            throw new PurchaseAccessDeniedException();
        }
    }

    private LocalDate branchDate(Long businessId, Long branchId) {
        String timeZone = branchAccessService.findActiveByBusinessIdAndBranchId(businessId, branchId)
                .orElseThrow(PurchaseAccessDeniedException::new)
                .timeZone();
        return LocalDate.now(clock.withZone(ZoneId.of(timeZone)));
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private Long parseLongOptional(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new StockImportValidationException(field + " must be a valid whole number");
        }
    }

    private BigDecimal parseDecimalOptional(String value, String field, int scale) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim()).setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new StockImportValidationException(field + " has invalid number or precision");
        }
    }

    private LocalDate parseDateOptional(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new StockImportValidationException(field + " must use yyyy-MM-dd format");
        }
    }

    private String safeFileName(String fileName) {
        String value = fileName == null ? "stock-import.csv" : fileName.trim();
        if (value.isBlank()) value = "stock-import.csv";
        value = value.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        if (value.length() > 255) value = value.substring(value.length() - 255);
        return value;
    }

    private String cleanMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Stock Import row is invalid" : message;
    }

    private String csvCell(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String limitedKey(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= 100 ? trimmed : trimmed.substring(0, 100);
    }

    private BigDecimal quantityZero() {
        return BigDecimal.ZERO.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
    }

    private BigDecimal moneyZero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private record ResolvedConversion(BigDecimal factor, int decimalPrecision) {
    }
}
