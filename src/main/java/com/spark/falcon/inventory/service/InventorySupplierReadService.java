package com.spark.falcon.inventory.service;

import com.spark.falcon.inventory.dto.SupplierInventoryExpiryProjectionResponse;
import com.spark.falcon.inventory.entity.ProductBatch;
import com.spark.falcon.inventory.entity.ProductBatchStatus;
import com.spark.falcon.inventory.repository.InventoryLossRepository;
import com.spark.falcon.inventory.repository.ProductBatchRepository;
import com.spark.falcon.product.dto.response.ProductExpiryAccessResponse;
import com.spark.falcon.product.service.ProductAccessService;
import com.spark.falcon.settings.dto.response.BranchSettingsResponse;
import com.spark.falcon.settings.service.BranchSettingsAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InventorySupplierReadService {

    private final ProductBatchRepository productBatchRepository;
    private final InventoryLossRepository inventoryLossRepository;
    private final ProductAccessService productAccessService;
    private final BranchSettingsAccessService branchSettingsAccessService;
    private final Clock clock;

    public SupplierInventoryExpiryProjectionResponse projectSupplierExpiry(
            Long businessId, Long branchId, Long supplierId) {
        List<ProductBatch> batches = productBatchRepository
                .findByBusinessIdAndBranchIdAndSupplierIdOrderByExpiryDateAscIdAsc(
                        businessId, branchId, supplierId);

        BranchSettingsResponse branchSettings = branchSettingsAccessService
                .findByBusinessIdAndBranchId(businessId, branchId)
                .orElse(null);
        ZoneId branchZone = branchSettings == null || branchSettings.timeZone() == null
                ? clock.getZone()
                : ZoneId.of(branchSettings.timeZone());
        LocalDate today = LocalDate.now(clock.withZone(branchZone));
        Integer branchDefaultAlertDays = branchSettings == null
                ? null
                : branchSettings.defaultExpiryAlertDays();
        boolean nearExpiryWarningsEnabled = branchSettings == null
                || !Boolean.FALSE.equals(branchSettings.nearExpiryWarningsEnabled());

        Set<Long> expiryBatchIds = new LinkedHashSet<>();
        long activeCount = 0;
        long nearExpiryCount = 0;
        long expiredCount = 0;

        for (ProductBatch batch : batches) {
            if (batch.getExpiryDate() == null) continue;
            expiryBatchIds.add(batch.getId());

            if (batch.getAvailableBaseQuantity() == null || batch.getAvailableBaseQuantity().signum() <= 0) {
                continue;
            }

            boolean expired = batch.getExpiryDate().isBefore(today)
                    || batch.getStatus() == ProductBatchStatus.EXPIRED;
            if (expired) {
                expiredCount++;
                continue;
            }

            if (batch.getStatus() == ProductBatchStatus.ACTIVE) {
                activeCount++;
                Integer alertDays = productAccessService
                        .findExpiryConfiguration(businessId, batch.getProductVariantId())
                        .map(ProductExpiryAccessResponse::expiryAlertBeforeDays)
                        .orElse(branchDefaultAlertDays);
                if (nearExpiryWarningsEnabled && alertDays != null && alertDays > 0
                        && !batch.getExpiryDate().isAfter(today.plusDays(alertDays))) {
                    nearExpiryCount++;
                }
            }
        }

        BigDecimal expiryLossAmount = inventoryLossRepository
                .sumPostedExpiryLossForSupplier(businessId, branchId, supplierId);
        if (expiryLossAmount == null) expiryLossAmount = BigDecimal.ZERO;

        return new SupplierInventoryExpiryProjectionResponse(
                Set.copyOf(expiryBatchIds),
                activeCount,
                nearExpiryCount,
                expiredCount,
                expiryLossAmount
        );
    }
}
