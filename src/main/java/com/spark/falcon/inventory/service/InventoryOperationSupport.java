package com.spark.falcon.inventory.service;

import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.inventory.exception.InventoryAccessDeniedException;
import com.spark.falcon.inventory.exception.InventoryPostingException;
import com.spark.falcon.product.dto.response.ProductUnitConversionResponse;
import com.spark.falcon.product.dto.response.ProductVariantAccessResponse;
import com.spark.falcon.product.service.ProductAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class InventoryOperationSupport {

    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final ProductAccessService productAccessService;

    public Long business(Long ownerId) {
        return businessAccessService.findByOwnerId(ownerId)
                .orElseThrow(InventoryAccessDeniedException::new)
                .businessId();
    }

    public void branch(Long businessId, Long branchId) {
        if (branchId == null || branchAccessService.findActiveByBusinessIdAndBranchId(businessId, branchId).isEmpty()) {
            throw new InventoryAccessDeniedException();
        }
    }

    public ProductVariantAccessResponse variant(Long businessId, Long branchId, Long variantId) {
        return productAccessService.findActiveVariantForBranch(businessId, branchId, variantId)
                .orElseThrow(InventoryAccessDeniedException::new);
    }

    public Resolved resolve(Long businessId, Long branchId, Long variantId,
                            Long enteredUnitId, BigDecimal enteredQuantity, LocalDate effectiveDate) {
        return resolveInternal(businessId, branchId, variantId, enteredUnitId, enteredQuantity, effectiveDate, false);
    }

    public Resolved resolveNonNegative(Long businessId, Long branchId, Long variantId,
                                       Long enteredUnitId, BigDecimal enteredQuantity, LocalDate effectiveDate) {
        return resolveInternal(businessId, branchId, variantId, enteredUnitId, enteredQuantity, effectiveDate, true);
    }

    private Resolved resolveInternal(Long businessId, Long branchId, Long variantId,
                                     Long enteredUnitId, BigDecimal enteredQuantity, LocalDate effectiveDate,
                                     boolean allowZero) {
        if (enteredQuantity == null || enteredUnitId == null
                || (allowZero ? enteredQuantity.signum() < 0 : enteredQuantity.signum() <= 0)) {
            throw new InventoryPostingException(allowZero
                    ? "Non-negative Entered Quantity and Unit are required"
                    : "Positive Entered Quantity and Unit are required");
        }
        if (effectiveDate == null) {
            throw new InventoryPostingException("Effective date is required for Unit Conversion resolution");
        }

        ProductVariantAccessResponse variant = variant(businessId, branchId, variantId);
        if (enteredUnitId.equals(variant.baseInventoryUnitId())) {
            return new Resolved(variant, BigDecimal.ONE,
                    enteredQuantity.setScale(8, RoundingMode.HALF_UP));
        }

        ProductUnitConversionResponse conversion = productAccessService
                .findEffectiveConversion(businessId, branchId, variantId, enteredUnitId, effectiveDate)
                .orElseThrow(() -> new InventoryPostingException("Active Product Unit Conversion is required"));

        if (!conversion.targetUnitId().equals(variant.baseInventoryUnitId())
                || conversion.conversionFactor().signum() <= 0
                || Math.max(enteredQuantity.stripTrailingZeros().scale(), 0) > conversion.decimalPrecision()) {
            throw new InventoryPostingException("Invalid Product Unit Conversion or quantity precision");
        }

        return new Resolved(
                variant,
                conversion.conversionFactor(),
                enteredQuantity.multiply(conversion.conversionFactor()).setScale(8, RoundingMode.HALF_UP));
    }

    public record Resolved(
            ProductVariantAccessResponse variant,
            BigDecimal factor,
            BigDecimal baseQuantity
    ) {
    }
}
