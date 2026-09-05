package com.spark.falcon.branch.service;

import com.spark.falcon.branch.dto.response.BranchAccessResponse;
import com.spark.falcon.branch.entity.enumtype.BranchStatus;
import com.spark.falcon.branch.mapper.BranchMapper;
import com.spark.falcon.branch.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BranchAccessServiceImpl implements BranchAccessService {

    private final BranchRepository branchRepository;
    private final BranchMapper branchMapper;

    @Override
    public Optional<BranchAccessResponse> findByBusinessIdAndBranchId(Long businessId, Long branchId) {
        return branchRepository.findByIdAndBusinessId(branchId, businessId)
                .map(branchMapper::toAccessResponse);
    }

    @Override
    public Optional<BranchAccessResponse> findActiveByBusinessIdAndBranchId(Long businessId, Long branchId) {
        return branchRepository.findByIdAndBusinessIdAndStatus(branchId, businessId, BranchStatus.ACTIVE)
                .map(branchMapper::toAccessResponse);
    }

    @Override
    public List<BranchAccessResponse> findActiveByBusinessId(Long businessId) {
        return branchRepository.findByBusinessIdAndStatusOrderByNameAsc(businessId, BranchStatus.ACTIVE).stream()
                .map(branchMapper::toAccessResponse)
                .toList();
    }
}
