package com.spark.falcon.product;

import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.product.repository.ProductSkuSequenceRepository;
import com.spark.falcon.product.repository.ProductVariantRepository;
import com.spark.falcon.product.service.ProductSkuService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSkuServiceTest {

    private final ProductSkuSequenceRepository sequenceRepository = mock(ProductSkuSequenceRepository.class);
    private final ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
    private final BusinessAccessService businessAccessService = mock(BusinessAccessService.class);
    private final ProductSkuService service = new ProductSkuService(
            sequenceRepository, variantRepository, businessAccessService);

    @Test
    void reservesSixDigitBusinessSku() {
        when(businessAccessService.findByOwnerId(7L))
                .thenReturn(Optional.of(new BusinessAccessResponse(11L, 7L, "Falcon")));
        when(sequenceRepository.reserveNextNumber(11L)).thenReturn(125L);
        when(variantRepository.countForBusinessBySkuIgnoreCase(11L, "SKU-000125")).thenReturn(0L);

        String sku = service.reserveNext(7L);

        assertThat(sku).isEqualTo("SKU-000125");
        verify(sequenceRepository).reserveNextNumber(11L);
        verify(variantRepository).countForBusinessBySkuIgnoreCase(11L, "SKU-000125");
    }

    @Test
    void skipsUnexpectedCollisionAndReservesAnotherSku() {
        when(businessAccessService.findByOwnerId(7L))
                .thenReturn(Optional.of(new BusinessAccessResponse(11L, 7L, "Falcon")));
        when(sequenceRepository.reserveNextNumber(11L)).thenReturn(1L, 2L);
        when(variantRepository.countForBusinessBySkuIgnoreCase(11L, "SKU-000001")).thenReturn(1L);
        when(variantRepository.countForBusinessBySkuIgnoreCase(11L, "SKU-000002")).thenReturn(0L);

        String sku = service.reserveNext(7L);

        assertThat(sku).isEqualTo("SKU-000002");
    }
}
