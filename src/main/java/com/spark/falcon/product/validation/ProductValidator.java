package com.spark.falcon.product.validation;

import com.spark.falcon.product.dto.command.CreateProductCommand;
import com.spark.falcon.product.dto.command.CreateProductUnitConversionCommand;
import com.spark.falcon.product.dto.command.CreateProductVariantCommand;
import com.spark.falcon.product.dto.command.UpdateProductCommand;
import com.spark.falcon.product.dto.command.UpdateProductVariantCommand;
import com.spark.falcon.product.entity.enumtype.ExpiryType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class ProductValidator {

    public void validate(CreateProductCommand command) {
        validateProduct(command.trackExpiry(), command.expiryType(), command.defaultShelfLifeDays(),
                command.expiryAlertBeforeDays(), command.blockSaleAfterExpiry(), command.displayOrder(), command.branchIds());
    }

    public void validate(UpdateProductCommand command) {
        validateProduct(command.trackExpiry(), command.expiryType(), command.defaultShelfLifeDays(),
                command.expiryAlertBeforeDays(), command.blockSaleAfterExpiry(), command.displayOrder(), command.branchIds());
    }

    public void validate(CreateProductVariantCommand command) {
        validateVariant(command.sellingPrice(), command.reorderLevel());
    }

    public void validate(UpdateProductVariantCommand command) {
        validateVariant(command.sellingPrice(), command.reorderLevel());
    }

    public void validate(CreateProductUnitConversionCommand command) {
        if (command.conversionFactor() == null || command.conversionFactor().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("conversionFactor must be positive");
        if (command.decimalPrecision() < 0)
            throw new IllegalArgumentException("decimalPrecision must not be negative");
        if (command.effectiveFrom() == null)
            throw new IllegalArgumentException("effectiveFrom is required");
        if (command.effectiveTo() != null && command.effectiveTo().isBefore(command.effectiveFrom()))
            throw new IllegalArgumentException("effectiveTo must not be before effectiveFrom");
    }

    public String normalizeReferenceCode(String value) {
        return required(value, "referenceCode").toUpperCase(Locale.ROOT);
    }

    public String normalizeSku(String value) {
        return required(value, "sku").toUpperCase(Locale.ROOT);
    }

    public String normalizeBarcode(String value) {
        return required(value, "barcode");
    }

    public Set<Long> copyBranchIds(Set<Long> branchIds) {
        if (branchIds == null || branchIds.isEmpty())
            throw new IllegalArgumentException("At least one branch assignment is required");
        LinkedHashSet<Long> copy = new LinkedHashSet<>(branchIds);
        if (copy.stream().anyMatch(id -> id == null || id <= 0))
            throw new IllegalArgumentException("branchIds contains an invalid branch id");
        return copy;
    }

    private void validateProduct(boolean trackExpiry, ExpiryType expiryType, Integer defaultShelfLifeDays,
                                 Integer expiryAlertBeforeDays, boolean blockSaleAfterExpiry,
                                 int displayOrder, Set<Long> branchIds) {
        copyBranchIds(branchIds);
        if (displayOrder < 0) throw new IllegalArgumentException("displayOrder must not be negative");
        if (!trackExpiry) return;
        if (expiryType == null) throw new IllegalArgumentException("expiryType is required when expiry tracking is enabled");
        if (defaultShelfLifeDays != null && defaultShelfLifeDays <= 0)
            throw new IllegalArgumentException("defaultShelfLifeDays must be positive");
        if (expiryAlertBeforeDays == null || expiryAlertBeforeDays <= 0)
            throw new IllegalArgumentException("expiryAlertBeforeDays must be positive when expiry tracking is enabled");
        if (!blockSaleAfterExpiry)
            throw new IllegalArgumentException("Expired products must remain blocked from sale");
    }

    private void validateVariant(BigDecimal sellingPrice, BigDecimal reorderLevel) {
        if (sellingPrice == null || sellingPrice.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("sellingPrice must not be negative");
        if (reorderLevel == null || reorderLevel.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("reorderLevel must not be negative");
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
