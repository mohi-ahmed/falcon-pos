package com.spark.falcon.settings.service;

import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.settings.dto.request.PrinterRequest;
import com.spark.falcon.settings.dto.response.PrinterResponse;
import com.spark.falcon.settings.entity.Printer;
import com.spark.falcon.settings.entity.PrinterBranch;
import com.spark.falcon.settings.entity.SettingsAuditEvent;
import com.spark.falcon.settings.entity.enumtype.*;
import com.spark.falcon.settings.exception.SettingsAccessDeniedException;
import com.spark.falcon.settings.exception.SettingsNotFoundException;
import com.spark.falcon.settings.repository.*;
import com.spark.falcon.settings.validation.SettingsValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PrinterService {
    private final PrinterRepository printerRepository;
    private final PrinterBranchRepository assignmentRepository;
    private final SettingsAuditEventRepository auditRepository;
    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final SettingsValidator validator;
    private final PrinterTestService printerTestService;
    private final Clock clock;

    @Transactional
    public PrinterResponse create(Long ownerId, PrinterRequest request) {
        BusinessAccessResponse business = business(ownerId);
        validate(business.businessId(), request);
        Instant now = Instant.now(clock);
        Printer printer = printerRepository.saveAndFlush(Printer.create(business.businessId(), request.getTitle(),
                request.getPrinterType(), request.getConnectionType(), request.getCharactersPerLine(),
                request.getPrinterPath(), request.getIpAddress(), request.getPort(), request.getDisplayOrder(), now));
        if (request.getStatus() == ConfigurationStatus.INACTIVE) printer.changeStatus(ConfigurationStatus.INACTIVE, now);
        Set<Long> branches = syncAssignments(printer.getId(), request.getBranchIds(), now);
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, ownerId,
                SettingsAuditAction.PRINTER_CREATED, "PRINTER", printer.getId(), now));
        return response(printer, branches);
    }

    @Transactional
    public PrinterResponse update(Long ownerId, Long printerId, PrinterRequest request) {
        BusinessAccessResponse business = business(ownerId);
        validate(business.businessId(), request);
        Printer printer = find(printerId, business.businessId());
        Instant now = Instant.now(clock);
        printer.update(request.getTitle(), request.getPrinterType(), request.getConnectionType(),
                request.getCharactersPerLine(), request.getPrinterPath(), request.getIpAddress(), request.getPort(),
                request.getDisplayOrder(), now);
        printerRepository.saveAndFlush(printer);
        Set<Long> branches = syncAssignments(printerId, request.getBranchIds(), now);
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, ownerId,
                SettingsAuditAction.PRINTER_UPDATED, "PRINTER", printerId, now));
        return response(printer, branches);
    }

    @Transactional
    public void changeStatus(Long ownerId, Long printerId, ConfigurationStatus status) {
        BusinessAccessResponse business = business(ownerId);
        Printer printer = find(printerId, business.businessId());
        Instant now = Instant.now(clock);
        printer.changeStatus(status, now);
        printerRepository.saveAndFlush(printer);
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, ownerId,
                SettingsAuditAction.PRINTER_STATUS_CHANGED, "PRINTER", printerId, now));
    }

    @Transactional
    public void archive(Long ownerId, Long printerId) {
        BusinessAccessResponse business = business(ownerId);
        Printer printer = find(printerId, business.businessId());
        Instant now = Instant.now(clock);
        printer.archive(now);
        printerRepository.saveAndFlush(printer);
        assignmentRepository.findByPrinterId(printerId).forEach(value -> value.deactivate(now));
        assignmentRepository.flush();
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, ownerId,
                SettingsAuditAction.PRINTER_ARCHIVED, "PRINTER", printerId, now));
    }

    @Transactional(readOnly = true)
    public List<PrinterResponse> findAll(Long ownerId) {
        BusinessAccessResponse business = business(ownerId);
        return printerRepository.findByBusinessIdAndArchivedAtIsNullOrderByDisplayOrderAscTitleAsc(business.businessId())
                .stream().map(value -> response(value, activeBranchIds(value.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<PrinterResponse> findActiveForBranch(Long ownerId, Long branchId) {
        return findAll(ownerId).stream().filter(value -> value.status() == ConfigurationStatus.ACTIVE
                && value.branchIds().contains(branchId)).toList();
    }

    @Transactional
    public void recordTest(Long ownerId, Long printerId) {
        BusinessAccessResponse business = business(ownerId);
        Printer printer = find(printerId, business.businessId());
        if (printer.getStatus() != ConfigurationStatus.ACTIVE)
            throw new IllegalStateException("Inactive printer cannot accept a test print");
        printerTestService.test(printer);
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, ownerId,
                SettingsAuditAction.PRINTER_TESTED, "PRINTER", printerId, Instant.now(clock)));
    }

    private void validate(Long businessId, PrinterRequest request) {
        validator.validateAssignments(request.getBranchIds());
        if (request.getCharactersPerLine() <= 0) throw new IllegalArgumentException("charactersPerLine must be positive");
        if (request.getDisplayOrder() < 0) throw new IllegalArgumentException("displayOrder must not be negative");
        if (request.getConnectionType() == PrinterConnectionType.NETWORK) {
            if (request.getIpAddress() == null || request.getIpAddress().isBlank())
                throw new IllegalArgumentException("IP address is required for a network printer");
            if (request.getPort() == null || request.getPort() < 1 || request.getPort() > 65535)
                throw new IllegalArgumentException("A valid port is required for a network printer");
        } else if (request.getPrinterPath() == null || request.getPrinterPath().isBlank()) {
            throw new IllegalArgumentException("Printer path is required for a system/local printer");
        }
        for (Long branchId : request.getBranchIds())
            if (branchAccessService.findActiveByBusinessIdAndBranchId(businessId, branchId).isEmpty())
                throw new SettingsAccessDeniedException();
    }

    private BusinessAccessResponse business(Long ownerId) {
        return businessAccessService.findByOwnerId(ownerId).orElseThrow(SettingsAccessDeniedException::new);
    }
    private Printer find(Long id, Long businessId) {
        return printerRepository.findByIdAndBusinessId(id, businessId).orElseThrow(() -> new SettingsNotFoundException("Printer"));
    }
    private Set<Long> syncAssignments(Long printerId, Set<Long> requested, Instant now) {
        Map<Long, PrinterBranch> existing = new LinkedHashMap<>();
        assignmentRepository.findByPrinterId(printerId).forEach(value -> existing.put(value.getBranchId(), value));
        existing.values().forEach(value -> { if (requested.contains(value.getBranchId())) value.activate(now); else value.deactivate(now); });
        requested.stream().filter(id -> !existing.containsKey(id))
                .forEach(id -> assignmentRepository.save(PrinterBranch.assign(printerId, id, now)));
        assignmentRepository.flush();
        return new LinkedHashSet<>(requested);
    }
    private Set<Long> activeBranchIds(Long printerId) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        assignmentRepository.findByPrinterId(printerId).stream().filter(PrinterBranch::isActive)
                .map(PrinterBranch::getBranchId).forEach(ids::add);
        return ids;
    }
    private PrinterResponse response(Printer value, Set<Long> branchIds) {
        return new PrinterResponse(value.getId(), value.getBusinessId(), value.getTitle(), value.getPrinterType(),
                value.getConnectionType(), value.getCharactersPerLine(), value.getPrinterPath(), value.getIpAddress(),
                value.getPort(), value.getDisplayOrder(), value.getStatus(), Set.copyOf(branchIds), value.isArchived());
    }
}
