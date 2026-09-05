package com.spark.falcon.settings.service;

import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.settings.dto.command.CreateUnitCommand;
import com.spark.falcon.settings.dto.command.UpdateUnitCommand;
import com.spark.falcon.settings.dto.response.UnitResponse;
import com.spark.falcon.settings.entity.SettingsAuditEvent;
import com.spark.falcon.settings.entity.Unit;
import com.spark.falcon.settings.entity.UnitBranch;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import com.spark.falcon.settings.entity.enumtype.SettingsAuditAction;
import com.spark.falcon.settings.exception.SettingsAccessDeniedException;
import com.spark.falcon.settings.exception.SettingsCodeAlreadyUsedException;
import com.spark.falcon.settings.exception.SettingsNotFoundException;
import com.spark.falcon.settings.mapper.SettingsMapper;
import com.spark.falcon.settings.repository.SettingsAuditEventRepository;
import com.spark.falcon.settings.repository.UnitBranchRepository;
import com.spark.falcon.settings.repository.UnitRepository;
import com.spark.falcon.settings.validation.SettingsValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UnitService {
    private final UnitRepository unitRepository;
    private final UnitBranchRepository assignmentRepository;
    private final SettingsAuditEventRepository auditRepository;
    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final SettingsMapper mapper;
    private final SettingsValidator validator;
    private final Clock clock;

    @Transactional
    public UnitResponse create(CreateUnitCommand command) {
        BusinessAccessResponse business = business(command.ownerId());
        validator.validateAssignments(command.branchIds());
        validateBranches(business.businessId(), command.branchIds());
        String code = validator.normalizeCode(command.code());
        if (unitRepository.existsByBusinessIdAndCodeIgnoreCase(business.businessId(), code))
            throw new SettingsCodeAlreadyUsedException("Unit", code);
        Instant now = Instant.now(clock);
        Unit unit = unitRepository.saveAndFlush(Unit.create(business.businessId(), command.name(), code,
                command.description(), command.displayOrder(), now));
        unit.changeStatus(command.status(), now);
        Set<Long> branchIds = syncAssignments(unit.getId(), command.branchIds(), now);
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, command.ownerId(),
                SettingsAuditAction.UNIT_CREATED, "UNIT", unit.getId(), now));
        return mapper.toResponse(unit, branchIds);
    }

    @Transactional
    public UnitResponse update(UpdateUnitCommand command) {
        BusinessAccessResponse business = business(command.ownerId());
        validator.validateAssignments(command.branchIds());
        validateBranches(business.businessId(), command.branchIds());
        Unit unit = unitRepository.findByIdAndBusinessId(command.unitId(), business.businessId())
                .orElseThrow(() -> new SettingsNotFoundException("Unit"));
        String code = validator.normalizeCode(command.code());
        if (unitRepository.existsByBusinessIdAndCodeIgnoreCaseAndIdNot(business.businessId(), code, command.unitId()))
            throw new SettingsCodeAlreadyUsedException("Unit", code);
        Instant now = Instant.now(clock);
        unit.update(command.name(), code, command.description(), command.displayOrder(), now);
        Unit saved = unitRepository.saveAndFlush(unit);
        Set<Long> branchIds = syncAssignments(saved.getId(), command.branchIds(), now);
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, command.ownerId(),
                SettingsAuditAction.UNIT_UPDATED, "UNIT", saved.getId(), now));
        return mapper.toResponse(saved, branchIds);
    }

    @Transactional
    public UnitResponse changeStatus(Long ownerId, Long unitId, ConfigurationStatus status) {
        BusinessAccessResponse business = business(ownerId);
        Unit unit = unitRepository.findByIdAndBusinessId(unitId, business.businessId())
                .orElseThrow(() -> new SettingsNotFoundException("Unit"));
        Instant now = Instant.now(clock);
        unit.changeStatus(status, now);
        Unit saved = unitRepository.saveAndFlush(unit);
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, ownerId,
                SettingsAuditAction.UNIT_STATUS_CHANGED, "UNIT", saved.getId(), now));
        return mapper.toResponse(saved, activeBranchIds(saved.getId()));
    }

    @Transactional
    public void archive(Long ownerId, Long unitId) {
        BusinessAccessResponse business = business(ownerId);
        Unit unit = unitRepository.findByIdAndBusinessId(unitId, business.businessId())
                .orElseThrow(() -> new SettingsNotFoundException("Unit"));
        Instant now = Instant.now(clock);
        unit.archive(now);
        unitRepository.saveAndFlush(unit);
        for (UnitBranch assignment : assignmentRepository.findByUnitId(unitId)) {
            if (assignment.isActive()) assignment.deactivate(now);
        }
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, ownerId,
                SettingsAuditAction.UNIT_ARCHIVED, "UNIT", unitId, now));
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> findAll(Long ownerId) {
        BusinessAccessResponse business = business(ownerId);
        return unitRepository.findByBusinessIdAndArchivedAtIsNullOrderByDisplayOrderAscNameAsc(business.businessId())
                .stream().map(unit -> mapper.toResponse(unit, activeBranchIds(unit.getId()))).toList();
    }

    private BusinessAccessResponse business(Long ownerId) {
        return businessAccessService.findByOwnerId(ownerId).orElseThrow(SettingsAccessDeniedException::new);
    }

    private void validateBranches(Long businessId, Set<Long> branchIds) {
        for (Long branchId : branchIds) {
            if (branchAccessService.findActiveByBusinessIdAndBranchId(businessId, branchId).isEmpty())
                throw new SettingsAccessDeniedException();
        }
    }

    private Set<Long> syncAssignments(Long unitId, Set<Long> requested, Instant now) {
        Map<Long, UnitBranch> existing = new LinkedHashMap<>();
        for (UnitBranch assignment : assignmentRepository.findByUnitId(unitId)) existing.put(assignment.getBranchId(), assignment);
        for (UnitBranch assignment : existing.values()) {
            if (requested.contains(assignment.getBranchId())) assignment.activate(now);
            else assignment.deactivate(now);
        }
        for (Long branchId : requested) {
            if (!existing.containsKey(branchId)) assignmentRepository.save(UnitBranch.assign(unitId, branchId, now));
        }
        assignmentRepository.flush();
        return new LinkedHashSet<>(requested);
    }

    private Set<Long> activeBranchIds(Long unitId) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        assignmentRepository.findByUnitId(unitId).stream().filter(UnitBranch::isActive)
                .map(UnitBranch::getBranchId).forEach(ids::add);
        return ids;
    }
}
