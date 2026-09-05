package com.spark.falcon.pos.dto;

import com.spark.falcon.payment.dto.PaymentResponse;
import com.spark.falcon.sale.dto.SaleResponse;

public record PosCheckoutResponse(
        SaleResponse sale,
        PaymentResponse payment
) {
}
