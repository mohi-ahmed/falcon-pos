package com.spark.falcon.settings.service;

import com.spark.falcon.settings.dto.response.PrinterResponse;
import java.util.List;
import java.util.Optional;

public interface PrinterAccessService {
    Optional<PrinterResponse> findActiveByBusinessAndBranch(Long businessId, Long branchId, Long printerId);
    List<PrinterResponse> findActiveByBusinessAndBranch(Long businessId, Long branchId);
    void print(Long businessId, Long branchId, Long printerId, byte[] content);
}
