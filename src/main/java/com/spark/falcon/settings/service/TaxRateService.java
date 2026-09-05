package com.spark.falcon.settings.service;

import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.settings.dto.command.CreateTaxRateCommand;
import com.spark.falcon.settings.dto.command.UpdateTaxRateCommand;
import com.spark.falcon.settings.dto.response.TaxRateResponse;
import com.spark.falcon.settings.entity.SettingsAuditEvent;
import com.spark.falcon.settings.entity.TaxRate;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import com.spark.falcon.settings.entity.enumtype.SettingsAuditAction;
import com.spark.falcon.settings.exception.SettingsAccessDeniedException;
import com.spark.falcon.settings.exception.SettingsCodeAlreadyUsedException;
import com.spark.falcon.settings.exception.SettingsNotFoundException;
import com.spark.falcon.settings.mapper.SettingsMapper;
import com.spark.falcon.settings.repository.SettingsAuditEventRepository;
import com.spark.falcon.settings.repository.TaxRateRepository;
import com.spark.falcon.settings.validation.SettingsValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaxRateService {
    private final TaxRateRepository taxRateRepository;
    private final SettingsAuditEventRepository auditRepository;
    private final BusinessAccessService businessAccessService;
    private final SettingsMapper mapper;
    private final SettingsValidator validator;
    private final Clock clock;

    @Transactional
    public TaxRateResponse create(CreateTaxRateCommand command) {
        BusinessAccessResponse business = business(command.ownerId());
        String code = validator.normalizeCode(command.code());
        if (taxRateRepository.existsByBusinessIdAndCodeIgnoreCase(business.businessId(), code))
            throw new SettingsCodeAlreadyUsedException("Tax rate", code);
        Instant now = Instant.now(clock);
        TaxRate tax = taxRateRepository.saveAndFlush(TaxRate.create(business.businessId(), command.name(), code,
                command.rate(), command.displayOrder(), now));
        tax.changeStatus(command.status(), now);
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, command.ownerId(),
                SettingsAuditAction.TAX_RATE_CREATED, "TAX_RATE", tax.getId(), now));
        return mapper.toResponse(tax);
    }

    @Transactional
    public TaxRateResponse update(UpdateTaxRateCommand command) {
        BusinessAccessResponse business = business(command.ownerId());
        TaxRate tax = taxRateRepository.findByIdAndBusinessId(command.taxRateId(), business.businessId())
                .orElseThrow(() -> new SettingsNotFoundException("Tax rate"));
        String code = validator.normalizeCode(command.code());
        if (taxRateRepository.existsByBusinessIdAndCodeIgnoreCaseAndIdNot(business.businessId(), code, command.taxRateId()))
            throw new SettingsCodeAlreadyUsedException("Tax rate", code);
        Instant now = Instant.now(clock);
        tax.update(command.name(), code, command.rate(), command.displayOrder(), now);
        TaxRate saved = taxRateRepository.saveAndFlush(tax);
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, command.ownerId(),
                SettingsAuditAction.TAX_RATE_UPDATED, "TAX_RATE", saved.getId(), now));
        return mapper.toResponse(saved);
    }

    @Transactional
    public TaxRateResponse changeStatus(Long ownerId, Long taxRateId, ConfigurationStatus status) {
        BusinessAccessResponse business = business(ownerId);
        TaxRate tax = taxRateRepository.findByIdAndBusinessId(taxRateId, business.businessId())
                .orElseThrow(() -> new SettingsNotFoundException("Tax rate"));
        Instant now = Instant.now(clock);
        tax.changeStatus(status, now);
        TaxRate saved = taxRateRepository.saveAndFlush(tax);
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, ownerId,
                SettingsAuditAction.TAX_RATE_STATUS_CHANGED, "TAX_RATE", saved.getId(), now));
        return mapper.toResponse(saved);
    }

    @Transactional
    public void archive(Long ownerId, Long taxRateId) {
        BusinessAccessResponse business = business(ownerId);
        TaxRate tax = taxRateRepository.findByIdAndBusinessId(taxRateId, business.businessId())
                .orElseThrow(() -> new SettingsNotFoundException("Tax rate"));
        Instant now = Instant.now(clock);
        tax.archive(now);
        taxRateRepository.saveAndFlush(tax);
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, ownerId,
                SettingsAuditAction.TAX_RATE_ARCHIVED, "TAX_RATE", taxRateId, now));
    }

    @Transactional(readOnly = true)
    public List<TaxRateResponse> findAll(Long ownerId) {
        BusinessAccessResponse business = business(ownerId);
        return taxRateRepository.findByBusinessIdAndArchivedAtIsNullOrderByDisplayOrderAscNameAsc(business.businessId())
                .stream().map(mapper::toResponse).toList();
    }

    private BusinessAccessResponse business(Long ownerId) {
        return businessAccessService.findByOwnerId(ownerId).orElseThrow(SettingsAccessDeniedException::new);
    }
}
