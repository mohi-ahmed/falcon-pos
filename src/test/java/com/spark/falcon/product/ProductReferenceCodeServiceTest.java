package com.spark.falcon.product;

import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.product.repository.ProductReferenceSequenceRepository;
import com.spark.falcon.product.repository.ProductRepository;
import com.spark.falcon.product.service.ProductReferenceCodeService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductReferenceCodeServiceTest {

    private final ProductReferenceSequenceRepository sequenceRepository = mock(ProductReferenceSequenceRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final BusinessAccessService businessAccessService = mock(BusinessAccessService.class);
    private final ProductReferenceCodeService service = new ProductReferenceCodeService(
            sequenceRepository, productRepository, businessAccessService);

    @Test
    void reservesSixDigitBusinessReferenceCode() {
        when(businessAccessService.findByOwnerId(7L))
                .thenReturn(Optional.of(new BusinessAccessResponse(11L, 7L, "Falcon")));
        when(sequenceRepository.reserveNextNumber(11L)).thenReturn(125L);
        when(productRepository.existsByBusinessIdAndReferenceCodeIgnoreCase(11L, "PRD-000125"))
                .thenReturn(false);

        String code = service.reserveNext(7L);

        assertThat(code).isEqualTo("PRD-000125");
        verify(sequenceRepository).reserveNextNumber(11L);
        verify(productRepository).existsByBusinessIdAndReferenceCodeIgnoreCase(11L, "PRD-000125");
    }

    @Test
    void skipsUnexpectedCollisionAndReservesAnotherCode() {
        when(businessAccessService.findByOwnerId(7L))
                .thenReturn(Optional.of(new BusinessAccessResponse(11L, 7L, "Falcon")));
        when(sequenceRepository.reserveNextNumber(11L)).thenReturn(1L, 2L);
        when(productRepository.existsByBusinessIdAndReferenceCodeIgnoreCase(11L, "PRD-000001"))
                .thenReturn(true);
        when(productRepository.existsByBusinessIdAndReferenceCodeIgnoreCase(11L, "PRD-000002"))
                .thenReturn(false);

        String code = service.reserveNext(7L);

        assertThat(code).isEqualTo("PRD-000002");
    }
}
