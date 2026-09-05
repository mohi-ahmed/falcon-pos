package com.spark.falcon.settings.service;

import com.spark.falcon.settings.dto.response.TaxRateResponse;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import com.spark.falcon.settings.mapper.SettingsMapper;
import com.spark.falcon.settings.repository.TaxRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TaxRateAccessServiceImpl implements TaxRateAccessService {
    private final TaxRateRepository taxRateRepository;
    private final SettingsMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<TaxRateResponse> findActive(Long businessId, Long taxRateId) {
        return taxRateRepository.findByIdAndBusinessIdAndStatusAndArchivedAtIsNull(
                        taxRateId, businessId, ConfigurationStatus.ACTIVE)
                .map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxRateResponse> findActive(Long businessId) {
        return taxRateRepository.findByBusinessIdAndStatusAndArchivedAtIsNullOrderByDisplayOrderAscNameAsc(
                        businessId, ConfigurationStatus.ACTIVE).stream()
                .map(mapper::toResponse).toList();
    }
}
