package com.spark.falcon.product.service;

import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.product.exception.ProductAccessDeniedException;
import com.spark.falcon.product.repository.ProductSkuSequenceRepository;
import com.spark.falcon.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProductSkuService {

    private static final String PREFIX = "SKU-";
    private static final int MINIMUM_DIGITS = 6;
    private static final int MAX_RESERVATION_ATTEMPTS = 10;

    private final ProductSkuSequenceRepository sequenceRepository;
    private final ProductVariantRepository variantRepository;
    private final BusinessAccessService businessAccessService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String reserveNext(Long ownerId) {
        BusinessAccessResponse business = businessAccessService.findByOwnerId(ownerId)
                .orElseThrow(ProductAccessDeniedException::new);

        for (int attempt = 0; attempt < MAX_RESERVATION_ATTEMPTS; attempt++) {
            String candidate = format(sequenceRepository.reserveNextNumber(business.businessId()));
            if (variantRepository.countForBusinessBySkuIgnoreCase(business.businessId(), candidate) == 0) {
                return candidate;
            }
        }

        throw new IllegalStateException("Unable to reserve a unique product SKU");
    }

    private String format(long number) {
        return String.format(Locale.ROOT, "%s%0" + MINIMUM_DIGITS + "d", PREFIX, number);
    }
}
