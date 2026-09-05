package com.spark.falcon.businesssetup.service;

import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BusinessAccessServiceImpl implements BusinessAccessService {
    private final BusinessRepository businessRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<BusinessAccessResponse> findByOwnerId(Long ownerId) {
        return businessRepository.findByOwnerId(ownerId)
                .map(business -> new BusinessAccessResponse(
                        business.getId(), business.getOwner().getId(), business.getName()));
    }
}
