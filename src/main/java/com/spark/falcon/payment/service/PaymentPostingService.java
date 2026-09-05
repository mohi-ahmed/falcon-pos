package com.spark.falcon.payment.service;

import com.spark.falcon.payment.dto.InitialSupplierPaymentCommand;
import com.spark.falcon.payment.dto.CustomerRefundCommand;
import com.spark.falcon.payment.dto.PaymentResponse;
import com.spark.falcon.payment.dto.SalePaymentCommand;

public interface PaymentPostingService {
    PaymentResponse postInitialSupplierPayment(InitialSupplierPaymentCommand command);

    PaymentResponse postSalePayment(SalePaymentCommand command);

    PaymentResponse postCustomerRefund(CustomerRefundCommand command);

    PaymentResponse reversePayment(Long ownerId, Long branchId, Long paymentId,
                                   String reason, String idempotencyKey);
}
