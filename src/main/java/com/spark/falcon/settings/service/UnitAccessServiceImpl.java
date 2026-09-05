package com.spark.falcon.settings.service;

import com.spark.falcon.settings.dto.response.UnitResponse;
import com.spark.falcon.settings.entity.Unit;
import com.spark.falcon.settings.entity.UnitBranch;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import com.spark.falcon.settings.mapper.SettingsMapper;
import com.spark.falcon.settings.repository.UnitBranchRepository;
import com.spark.falcon.settings.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class UnitAccessServiceImpl implements UnitAccessService {
    private final UnitRepository unitRepository;
    private final UnitBranchRepository assignmentRepository;
    private final SettingsMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<UnitResponse> findActiveForBranch(Long businessId, Long branchId, Long unitId) {
        if (!assignmentRepository.existsByUnitIdAndBranchIdAndActiveTrue(unitId, branchId)) return Optional.empty();
        return unitRepository.findByIdAndBusinessIdAndStatusAndArchivedAtIsNull(unitId, businessId, ConfigurationStatus.ACTIVE)
                .map(unit -> mapper.toResponse(unit, activeBranchIds(unit.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitResponse> findActiveForBranch(Long businessId, Long branchId) {
        List<Long> unitIds = assignmentRepository.findByBranchIdAndActiveTrue(branchId).stream()
                .map(UnitBranch::getUnitId).distinct().toList();
        if (unitIds.isEmpty()) return List.of();
        return unitRepository.findByBusinessIdAndIdInAndStatusAndArchivedAtIsNullOrderByDisplayOrderAscNameAsc(
                        businessId, unitIds, ConfigurationStatus.ACTIVE).stream()
                .map(unit -> mapper.toResponse(unit, activeBranchIds(unit.getId())))
                .toList();
    }

    private Set<Long> activeBranchIds(Long unitId) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        assignmentRepository.findByUnitId(unitId).stream()
                .filter(UnitBranch::isActive)
                .map(UnitBranch::getBranchId)
                .forEach(ids::add);
        return ids;
    }
}
