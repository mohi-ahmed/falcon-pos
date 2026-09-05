package com.spark.falcon.settings.service;

import com.spark.falcon.settings.dto.response.PaymentMethodResponse;
import com.spark.falcon.settings.entity.PaymentMethod;
import com.spark.falcon.settings.entity.PaymentMethodBranch;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import com.spark.falcon.settings.mapper.SettingsMapper;
import com.spark.falcon.settings.repository.PaymentMethodBranchRepository;
import com.spark.falcon.settings.repository.PaymentMethodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentMethodAccessServiceImpl implements PaymentMethodAccessService {
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentMethodBranchRepository assignmentRepository;
    private final SettingsMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentMethodResponse> findActiveForBranch(Long businessId, Long branchId, Long paymentMethodId) {
        if (!assignmentRepository.existsByPaymentMethodIdAndBranchIdAndActiveTrue(paymentMethodId, branchId))
            return Optional.empty();
        return paymentMethodRepository.findByIdAndBusinessIdAndStatusAndArchivedAtIsNull(
                        paymentMethodId, businessId, ConfigurationStatus.ACTIVE)
                .map(method -> mapper.toResponse(method, activeBranchIds(method.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> findActiveForBranch(Long businessId, Long branchId) {
        List<Long> methodIds = assignmentRepository.findByBranchIdAndActiveTrue(branchId).stream()
                .map(PaymentMethodBranch::getPaymentMethodId).distinct().toList();
        if (methodIds.isEmpty()) return List.of();
        return paymentMethodRepository.findByBusinessIdAndIdInAndStatusAndArchivedAtIsNullOrderByDisplayOrderAscNameAsc(
                        businessId, methodIds, ConfigurationStatus.ACTIVE).stream()
                .map(method -> mapper.toResponse(method, activeBranchIds(method.getId())))
                .toList();
    }

    private Set<Long> activeBranchIds(Long paymentMethodId) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        assignmentRepository.findByPaymentMethodId(paymentMethodId).stream()
                .filter(PaymentMethodBranch::isActive)
                .map(PaymentMethodBranch::getBranchId)
                .forEach(ids::add);
        return ids;
    }
}
