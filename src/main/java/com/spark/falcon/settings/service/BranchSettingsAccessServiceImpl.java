package com.spark.falcon.settings.service;

import com.spark.falcon.branch.dto.response.BranchResponse;
import com.spark.falcon.branch.service.BranchService;
import com.spark.falcon.settings.dto.response.BranchSettingsResponse;
import com.spark.falcon.settings.mapper.SettingsMapper;
import com.spark.falcon.settings.repository.BranchSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BranchSettingsAccessServiceImpl implements BranchSettingsAccessService {
    private final BranchService branchService;
    private final BranchSettingsRepository branchSettingsRepository;
    private final SettingsMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<BranchSettingsResponse> findByBusinessIdAndBranchId(Long businessId, Long branchId) {
        Optional<BranchResponse> branch = branchService.findByBusinessIdAndBranchId(businessId, branchId);
        if (branch.isEmpty()) return Optional.empty();
        return Optional.of(mapper.toResponse(branch.get(),
                branchSettingsRepository.findByBusinessIdAndBranchId(businessId, branchId).orElse(null)));
    }
}
