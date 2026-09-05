package com.spark.falcon.settings.service;

import com.spark.falcon.settings.dto.response.PrinterResponse;
import com.spark.falcon.settings.entity.Printer;
import com.spark.falcon.settings.entity.PrinterBranch;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import com.spark.falcon.settings.repository.PrinterBranchRepository;
import com.spark.falcon.settings.repository.PrinterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PrinterAccessServiceImpl implements PrinterAccessService {
    private final PrinterRepository printerRepository;
    private final PrinterBranchRepository assignmentRepository;
    private final PrinterTestService printerTestService;

    @Override
    @Transactional(readOnly = true)
    public Optional<PrinterResponse> findActiveByBusinessAndBranch(Long businessId, Long branchId, Long printerId) {
        if (!assignmentRepository.existsByPrinterIdAndBranchIdAndActiveTrue(printerId, branchId)) return Optional.empty();
        return printerRepository.findByIdAndBusinessId(printerId, businessId)
                .filter(value -> value.getStatus() == ConfigurationStatus.ACTIVE && !value.isArchived())
                .map(value -> response(value, Set.of(branchId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrinterResponse> findActiveByBusinessAndBranch(Long businessId, Long branchId) {
        List<Long> ids = assignmentRepository.findByBranchIdAndActiveTrue(branchId).stream()
                .map(PrinterBranch::getPrinterId).toList();
        return printerRepository.findByBusinessIdAndArchivedAtIsNullOrderByDisplayOrderAscTitleAsc(businessId).stream()
                .filter(value -> ids.contains(value.getId()) && value.getStatus() == ConfigurationStatus.ACTIVE)
                .map(value -> response(value, Set.of(branchId))).toList();
    }

    @Override
    public void print(Long businessId, Long branchId, Long printerId, byte[] content) {
        if (content == null || content.length == 0) throw new IllegalArgumentException("Receipt content is required");
        if (!assignmentRepository.existsByPrinterIdAndBranchIdAndActiveTrue(printerId, branchId))
            throw new IllegalStateException("Receipt printer is not assigned to the active branch");
        Printer printer = printerRepository.findByIdAndBusinessId(printerId, businessId)
                .filter(value -> value.getStatus() == ConfigurationStatus.ACTIVE && !value.isArchived())
                .orElseThrow(() -> new IllegalStateException("Receipt printer is inactive or unavailable"));
        printerTestService.print(printer, content);
    }

    private PrinterResponse response(Printer value, Set<Long> branchIds) {
        return new PrinterResponse(value.getId(), value.getBusinessId(), value.getTitle(), value.getPrinterType(),
                value.getConnectionType(), value.getCharactersPerLine(), value.getPrinterPath(), value.getIpAddress(),
                value.getPort(), value.getDisplayOrder(), value.getStatus(), branchIds, value.isArchived());
    }
}
