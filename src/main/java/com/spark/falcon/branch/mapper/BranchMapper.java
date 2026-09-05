package com.spark.falcon.branch.mapper;

import com.spark.falcon.branch.dto.command.CreateBranchCommand;
import com.spark.falcon.branch.dto.command.UpdateBranchProfileCommand;
import com.spark.falcon.branch.dto.request.CreateBranchRequest;
import com.spark.falcon.branch.dto.request.UpdateBranchRequest;
import com.spark.falcon.branch.dto.response.BranchAccessResponse;
import com.spark.falcon.branch.dto.response.BranchAuditResponse;
import com.spark.falcon.branch.dto.response.BranchResponse;
import com.spark.falcon.branch.entity.Branch;
import com.spark.falcon.branch.entity.BranchAuditEvent;
import org.springframework.stereotype.Component;

@Component
public class BranchMapper {
    public CreateBranchCommand toCommand(Long ownerId, CreateBranchRequest request) {
        return new CreateBranchCommand(ownerId, request.getIdempotencyKey(), request.getName(), request.getCode(),
                request.getCountry(), request.getEmail(), request.getPhone(), request.getTimeZone(), request.getCurrency(),
                request.getAddress(), request.getCity(), request.getStateDivision(), request.getPostalCode(),
                request.getVatBinNumber(), request.getDefaultTaxRate(), request.getLowStockAlertQuantity(),
                request.getRecordsPerPage(), request.getReceiptFooter());
    }

    public UpdateBranchProfileCommand toUpdateCommand(Long ownerId, Long branchId, UpdateBranchRequest request) {
        return new UpdateBranchProfileCommand(ownerId, branchId, request.getName(), request.getCode(),
                request.getCountry(), request.getEmail(), request.getPhone(), request.getTimeZone(),
                request.getAddress(), request.getCity(), request.getStateDivision(), request.getPostalCode(),
                request.getVatBinNumber(), request.getDefaultTaxRate(), request.getLowStockAlertQuantity(),
                request.getRecordsPerPage(), request.getReceiptFooter());
    }

    public UpdateBranchRequest toUpdateRequest(BranchResponse branch) {
        UpdateBranchRequest request = new UpdateBranchRequest();
        request.setName(branch.name());
        request.setCode(branch.code());
        request.setCountry(branch.country());
        request.setEmail(branch.email());
        request.setPhone(branch.phone());
        request.setTimeZone(branch.timeZone());
        request.setAddress(branch.address());
        request.setCity(branch.city());
        request.setStateDivision(branch.stateDivision());
        request.setPostalCode(branch.postalCode());
        request.setVatBinNumber(branch.vatBinNumber());
        request.setDefaultTaxRate(branch.defaultTaxRate());
        request.setLowStockAlertQuantity(branch.lowStockAlertQuantity());
        request.setRecordsPerPage(branch.recordsPerPage());
        request.setReceiptFooter(branch.receiptFooter());
        return request;
    }

    public BranchAccessResponse toAccessResponse(Branch branch) {
        return new BranchAccessResponse(branch.getId(), branch.getBusinessId(), branch.getName(), branch.getCode(),
                branch.getTimeZone(), branch.getCurrency(), branch.getStatus());
    }

    public BranchResponse toResponse(Branch branch) {
        return new BranchResponse(branch.getId(), branch.getBusinessId(), branch.getName(), branch.getCode(),
                branch.getCountry(), branch.getEmail(), branch.getPhone(), branch.getTimeZone(), branch.getCurrency(),
                branch.getAddress(), branch.getCity(), branch.getStateDivision(), branch.getPostalCode(),
                branch.getVatBinNumber(), branch.getDefaultTaxRate(), branch.getLowStockAlertQuantity(),
                branch.getRecordsPerPage(), branch.getReceiptFooter(), branch.getStatus(), branch.getCreatedAt());
    }

    public BranchAuditResponse toAuditResponse(BranchAuditEvent event) {
        return new BranchAuditResponse(event.getId(), event.getActorOwnerId(), event.getAction(),
                event.getBeforeSnapshot(), event.getAfterSnapshot(), event.getOccurredAt());
    }
}
