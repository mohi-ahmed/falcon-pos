package com.spark.falcon.cashmanagement.service;

import com.spark.falcon.cashmanagement.dto.CashMovementRequest;
import com.spark.falcon.cashmanagement.dto.CashMovementResponse;
import com.spark.falcon.cashmanagement.entity.CashSourceModule;

public interface CashManagementPostingService {

    CashMovementResponse post(Long businessId, Long branchId, CashMovementRequest request);

    CashMovementResponse reverse(
            Long businessId,
            Long branchId,
            Long originalCashMovementId,
            CashSourceModule sourceModule,
            String sourceTransactionId,
            String sourceReference,
            Long postedByUserId,
            String postingKey,
            String reason
    );
}
