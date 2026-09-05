package com.spark.falcon.product.service;

import com.spark.falcon.product.dto.request.ProductRequest;
import com.spark.falcon.product.dto.request.ProductVariantRequest;
import com.spark.falcon.product.dto.response.ProductResponse;
import com.spark.falcon.product.mapper.ProductMapper;
import com.spark.falcon.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductCreationService {

    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final SupplierService supplierService;
    private final ProductMapper productMapper;

    @Transactional
    public ProductResponse create(Long ownerId,
                                  ProductRequest productRequest,
                                  ProductVariantRequest variantRequest,
                                  Long supplierId) {
        ProductResponse product = productService.create(productMapper.toCreateCommand(ownerId, productRequest));
        productVariantService.create(productMapper.toCreateVariantCommand(ownerId, product.id(), variantRequest));
        if (supplierId != null) {
            supplierService.assignProduct(ownerId, supplierId, product.id());
        }
        return product;
    }
}
