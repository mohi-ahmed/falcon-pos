package com.spark.falcon.product.exception;

public class ProductAccessDeniedException extends RuntimeException {
    public ProductAccessDeniedException() {
        super("Product access is not permitted for this business or branch");
    }
}
