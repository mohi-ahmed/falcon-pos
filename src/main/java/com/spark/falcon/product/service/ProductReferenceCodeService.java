package com.spark.falcon.product.service;

import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.product.exception.ProductAccessDeniedException;
import com.spark.falcon.product.repository.ProductReferenceSequenceRepository;
import com.spark.falcon.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProductReferenceCodeService {

    private static final String PREFIX = "PRD-";
    private static final int MINIMUM_DIGITS = 6;
    private static final int MAX_RESERVATION_ATTEMPTS = 10;

    private final ProductReferenceSequenceRepository sequenceRepository;
    private final ProductRepository productRepository;
    private final BusinessAccessService businessAccessService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String reserveNext(Long ownerId) {
        BusinessAccessResponse business = businessAccessService.findByOwnerId(ownerId)
                .orElseThrow(ProductAccessDeniedException::new);

        for (int attempt = 0; attempt < MAX_RESERVATION_ATTEMPTS; attempt++) {
            String candidate = format(sequenceRepository.reserveNextNumber(business.businessId()));
            if (!productRepository.existsByBusinessIdAndReferenceCodeIgnoreCase(business.businessId(), candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Unable to reserve a unique product reference code");
    }

    private String format(long number) {
        return String.format(Locale.ROOT, "%s%0" + MINIMUM_DIGITS + "d", PREFIX, number);
    }
}
