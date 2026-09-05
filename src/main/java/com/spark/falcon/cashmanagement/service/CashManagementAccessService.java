package com.spark.falcon.cashmanagement.service;

import com.spark.falcon.cashmanagement.dto.CashLocationResponse;
import com.spark.falcon.cashmanagement.dto.CashierShiftResponse;
import com.spark.falcon.cashmanagement.dto.RegisterResponse;

import java.util.List;

/**
 * Read-only application boundary exposed by Cash Management to other modules.
 * Repository access and cash-management persistence remain internal to the Cash Management module.
 */
public interface CashManagementAccessService {
    List<RegisterResponse> findRegisters(Long businessId, Long branchId);

    List<CashLocationResponse> findCashLocations(Long businessId, Long branchId);

    List<CashierShiftResponse> findShifts(Long businessId, Long branchId);

    CashierShiftResponse findShift(Long businessId, Long branchId, Long shiftId);
}
