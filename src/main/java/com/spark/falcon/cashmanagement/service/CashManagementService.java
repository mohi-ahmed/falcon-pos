package com.spark.falcon.cashmanagement.service;

import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.cashmanagement.dto.*;
import com.spark.falcon.cashmanagement.entity.*;
import com.spark.falcon.cashmanagement.exception.CashLocationNotFoundException;
import com.spark.falcon.cashmanagement.exception.CashManagementAccessDeniedException;
import com.spark.falcon.cashmanagement.exception.CashManagementValidationException;
import com.spark.falcon.cashmanagement.exception.CashRegisterNotFoundException;
import com.spark.falcon.cashmanagement.repository.*;
import com.spark.falcon.identity.security.CurrentActorService;
import com.spark.falcon.settings.dto.response.BranchSettingsResponse;
import com.spark.falcon.settings.service.BranchSettingsAccessService;
import com.spark.falcon.user.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CashManagementService implements CashManagementPostingService, CashManagementAccessService {

    private static final Instant FILTER_START = Instant.parse("1900-01-01T00:00:00Z");
    private static final Instant FILTER_END = Instant.parse("9999-12-31T23:59:59Z");

    private final RegisterRepository registerRepository;
    private final CashLocationRepository cashLocationRepository;
    private final CashierShiftRepository cashierShiftRepository;
    private final CashbookRepository cashbookRepository;
    private final CashMovementRepository cashMovementRepository;
    private final BranchAccessService branchAccessService;
    private final UserAccessService userAccessService;
    private final BranchSettingsAccessService branchSettingsAccessService;
    private final CurrentActorService currentActorService;
    private final Clock clock;

    @Transactional
    public RegisterResponse createRegister(Long businessId, Long branchId, RegisterRequest request) {
        requireBranch(businessId, branchId);
        if (registerRepository.existsByBranchIdAndCodeIgnoreCase(branchId, request.getCode().trim())) {
            throw new CashManagementValidationException("Register code is already used in this branch");
        }
        Register register = Register.create(businessId, branchId, request.getName(), request.getCode(), now());
        try {
            return toResponse(registerRepository.saveAndFlush(register));
        } catch (DataIntegrityViolationException ex) {
            throw new CashManagementValidationException("Register code is already used in this branch");
        }
    }

    @Transactional
    public CashLocationResponse createCashLocation(Long businessId, Long branchId, CashLocationRequest request) {
        requireBranch(businessId, branchId);
        if (cashLocationRepository.existsByBranchIdAndNameIgnoreCase(branchId, request.getName().trim())) {
            throw new CashManagementValidationException("Cash location name is already used in this branch");
        }
        CashLocation location = CashLocation.create(businessId, branchId, request.getName(), request.getType(), now());
        try {
            return toResponse(cashLocationRepository.saveAndFlush(location));
        } catch (DataIntegrityViolationException ex) {
            throw new CashManagementValidationException("Cash location name is already used in this branch");
        }
    }

    @Transactional
    public RegisterResponse changeRegisterStatus(Long businessId, Long branchId, Long registerId, RegisterStatus status) {
        Register register = registerRepository.findByIdAndBusinessIdAndBranchId(registerId, businessId, branchId)
                .orElseThrow(CashManagementAccessDeniedException::new);
        register.changeStatus(status, Instant.now(clock));
        return toResponse(registerRepository.saveAndFlush(register));
    }

    @Transactional
    public CashLocationResponse changeCashLocationStatus(Long businessId, Long branchId, Long locationId, CashLocationStatus status) {
        CashLocation location = cashLocationRepository.findByIdAndBusinessIdAndBranchId(locationId, businessId, branchId)
                .orElseThrow(CashManagementAccessDeniedException::new);
        location.changeStatus(status, Instant.now(clock));
        return toResponse(cashLocationRepository.saveAndFlush(location));
    }

    @Transactional
    public CashbookResponse initializeCashbook(Long businessId, Long branchId, BigDecimal openingBalance) {
        requireBranch(businessId, branchId);
        Cashbook existing = cashbookRepository.findByBusinessIdAndBranchId(businessId, branchId).orElse(null);
        if (existing != null) return toResponse(existing);

        Cashbook cashbook = Cashbook.create(businessId, branchId, openingBalance, now());
        try {
            return toResponse(cashbookRepository.saveAndFlush(cashbook));
        } catch (DataIntegrityViolationException ex) {
            return cashbookRepository.findByBusinessIdAndBranchId(businessId, branchId)
                    .map(this::toResponse)
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional
    public CashierShiftResponse openShift(Long businessId, Long branchId, OpenCashierShiftRequest request) {
        requireBranch(businessId, branchId);
        if (!userAccessService.hasActiveBranchAccess(businessId, request.getCashierUserId(), branchId)) {
            throw new CashManagementValidationException("Cashier does not have active access to this branch");
        }

        CashierShift repeated = cashierShiftRepository.findByBusinessIdAndBranchIdAndOpenKey(
                businessId, branchId, request.getOpenKey().trim()).orElse(null);
        if (repeated != null) return toResponse(repeated);

        Register register = requireActiveRegister(businessId, branchId, request.getRegisterId());
        CashLocation source = requireActiveLocation(businessId, branchId, request.getSourceCashLocationId());
        BranchSettingsResponse settings = branchSettingsAccessService
                .findByBusinessIdAndBranchId(businessId, branchId).orElse(null);
        validateAllowedCashLocation(source, settings == null ? null : settings.allowedOpeningSources(), "opening source");
        validateOpeningFloatPolicy(settings, request.getOpeningFloat());

        // Part K keeps one Open shift per Register. The Part N cashier policy controls only whether the
        // same cashier may concurrently operate another eligible Register in the same branch.
        if (cashierShiftRepository.existsByRegisterIdAndStatus(register.getId(), CashierShiftStatus.OPEN)) {
            throw new CashManagementValidationException("This register already has an open cashier shift");
        }
        boolean cashierMultipleShiftsAllowed = settings != null
                && Boolean.TRUE.equals(settings.cashierMultipleShiftsAllowed());
        if (!cashierMultipleShiftsAllowed
                && cashierShiftRepository.existsByBusinessIdAndBranchIdAndCashierUserIdAndStatus(
                businessId, branchId, request.getCashierUserId(), CashierShiftStatus.OPEN)) {
            throw new CashManagementValidationException("This cashier already has an open shift in the branch");
        }

        CashierShift shift = CashierShift.open(
                businessId, branchId, register.getId(), request.getCashierUserId(), source.getId(),
                request.getShiftCode(), request.getOpenKey(), request.getOpeningFloat(), request.getNote(), now());
        try {
            shift = cashierShiftRepository.saveAndFlush(shift);

            if (request.getOpeningFloat().signum() > 0) {
                CashMovementRequest openingFloat = new CashMovementRequest();
                openingFloat.setCashLocationId(source.getId());
                openingFloat.setRegisterId(register.getId());
                openingFloat.setCashierShiftId(shift.getId());
                openingFloat.setSourceModule(CashSourceModule.CASH_MANAGEMENT);
                openingFloat.setSourceTransactionId(String.valueOf(shift.getId()));
                openingFloat.setSourceReference(shift.getShiftCode());
                openingFloat.setMovementType(CashMovementType.OPENING_FLOAT);
                openingFloat.setDirection(CashMovementDirection.TRANSFER);
                openingFloat.setAmount(request.getOpeningFloat());
                openingFloat.setPostedByUserId(request.getCashierUserId());
                openingFloat.setPostingKey(request.getOpenKey());
                openingFloat.setNote(request.getNote());
                post(businessId, branchId, openingFloat);
            }

            return toResponse(shift);
        } catch (DataIntegrityViolationException ex) {
            CashierShift original = cashierShiftRepository.findByBusinessIdAndBranchIdAndOpenKey(
                    businessId, branchId, request.getOpenKey().trim()).orElse(null);
            if (original != null) return toResponse(original);
            throw new CashManagementValidationException("Cashier shift could not be opened because of a duplicate request");
        }
    }

    public BigDecimal expectedCash(Long businessId, Long branchId, Long shiftId) {
        requireBranch(businessId, branchId);
        CashierShift shift = cashierShiftRepository.findByIdAndBusinessIdAndBranchId(shiftId, businessId, branchId)
                .orElseThrow(() -> new CashManagementValidationException("Cashier shift does not belong to this branch"));
        if (!shift.isOpen() && shift.getExpectedCash() != null) {
            return shift.getExpectedCash();
        }
        return calculateExpectedCash(shift, cashMovementRepository.findAllForShiftOrderByPostedAtAsc(shiftId));
    }

    @Transactional
    public CashierShiftResponse closeShift(Long businessId, Long branchId, Long ownerId, Long shiftId,
                                           CloseCashierShiftRequest request) {
        requireBranch(businessId, branchId);
        String closeKey = requiredText(request.getCloseKey(), "Close request key");
        CashierShift shift = cashierShiftRepository.findForUpdate(businessId, branchId, shiftId)
                .orElseThrow(() -> new CashManagementValidationException("Cashier shift does not belong to this branch"));

        if (!shift.isOpen()) {
            if (shift.wasClosedWithKey(closeKey)) return toResponse(shift);
            throw new CashManagementValidationException("Cashier shift is already closed");
        }

        CashLocation destination = requireActiveLocation(businessId, branchId, request.getClosingCashLocationId());
        BigDecimal expected = calculateExpectedCash(
                shift, cashMovementRepository.findAllForShiftOrderByPostedAtAsc(shift.getId()));
        BigDecimal counted = nonNegativeMoney(request.getPhysicalCountedCash(), "Physical counted cash");
        BigDecimal variance = counted.subtract(expected).setScale(4, java.math.RoundingMode.HALF_UP);

        BranchSettingsResponse settings = branchSettingsAccessService
                .findByBusinessIdAndBranchId(businessId, branchId).orElse(null);
        validateAllowedCashLocation(destination, settings == null ? null : settings.allowedClosingDestinations(), "closing destination");
        boolean denominationRequired = settings != null && Boolean.TRUE.equals(settings.denominationCountRequired());
        if (denominationRequired && (request.getDenominationCount() == null || request.getDenominationCount().isBlank())) {
            throw new CashManagementValidationException("Denomination count is required for shift closing in this branch");
        }
        if (variance.signum() != 0 && (request.getNote() == null || request.getNote().isBlank())) {
            throw new CashManagementValidationException("A closing note or variance reason is required for shortage or excess");
        }

        BigDecimal varianceThreshold = settings == null ? null : settings.varianceApprovalThreshold();
        boolean varianceApprovalRequired = thresholdExceeded(variance.abs(), varianceThreshold);
        if (varianceApprovalRequired && !request.isVarianceApproved()) {
            throw new CashManagementValidationException("Owner approval is required because the cash variance exceeds the configured threshold");
        }
        if (varianceApprovalRequired && currentActorService.isStaff()) {
            throw new CashManagementValidationException(
                    "The primary owner must submit this shift close because threshold approval is required");
        }
        if (settings != null && Boolean.TRUE.equals(settings.cashDropRequired()) && hasPostedCashInflow(shift.getId())
                && !hasPostedCashDrop(shift.getId())) {
            throw new CashManagementValidationException("A cash drop is required before closing this shift by the current branch policy");
        }

        CashMovementResponse closingTransfer = null;
        if (counted.signum() > 0) {
            CashMovementRequest transfer = new CashMovementRequest();
            transfer.setDestinationCashLocationId(destination.getId());
            transfer.setRegisterId(shift.getRegisterId());
            transfer.setCashierShiftId(shift.getId());
            transfer.setSourceModule(CashSourceModule.CASH_MANAGEMENT);
            transfer.setSourceTransactionId(String.valueOf(shift.getId()));
            transfer.setSourceReference(shift.getShiftCode());
            transfer.setMovementType(CashMovementType.HANDOVER);
            transfer.setDirection(CashMovementDirection.TRANSFER);
            transfer.setAmount(counted);
            transfer.setPostedByUserId(shift.getCashierUserId());
            transfer.setPostingKey("shift-close-transfer:" + closeKey);
            transfer.setApprovalRequired(varianceApprovalRequired);
            transfer.setApprovedByOwnerId(varianceApprovalRequired ? ownerId : null);
            transfer.setNote("Shift closing transfer to " + destination.getName()
                    + (request.getNote() == null || request.getNote().isBlank() ? "" : ": " + request.getNote().trim()));
            closingTransfer = post(businessId, branchId, transfer);
        }

        try {
            shift.close(expected, counted, destination.getId(), request.getDenominationCount(), request.getNote(),
                    varianceApprovalRequired, varianceApprovalRequired ? ownerId : null, ownerId,
                    closingTransfer == null ? null : closingTransfer.getId(), closeKey, now());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new CashManagementValidationException(ex.getMessage());
        }
        return toResponse(cashierShiftRepository.saveAndFlush(shift));
    }

    @Transactional
    public CashMovementResponse postTransfer(Long businessId, Long branchId, Long ownerId,
                                             CashTransferRequest request) {
        requireBranch(businessId, branchId);
        String transferKey = requiredText(request.getTransferKey(), "Transfer request key");
        CashMovement repeated = cashMovementRepository.findByBusinessIdAndBranchIdAndPostingKey(
                businessId, branchId, transferKey).orElse(null);
        if (repeated != null) return toResponse(repeated);

        if (!userAccessService.hasActiveBranchAccess(businessId, request.getResponsibleUserId(), branchId)) {
            throw new CashManagementValidationException("Responsible user does not have active access to this branch");
        }
        BigDecimal amount = positiveMoney(request.getAmount(), "Transfer amount");
        String transferId = requiredText(request.getTransferId(), "Transfer ID");
        CashTransferType type = Objects.requireNonNull(request.getTransferType(), "transferType is required");
        BranchSettingsResponse settings = branchSettingsAccessService
                .findByBusinessIdAndBranchId(businessId, branchId).orElse(null);
        String bankAccountReference = firstNonBlank(request.getExternalAccountReference(),
                settings == null ? null : settings.defaultDepositAccountReference());

        CashMovementRequest movement = new CashMovementRequest();
        movement.setSourceModule(CashSourceModule.CASH_MANAGEMENT);
        movement.setSourceTransactionId(transferId);
        movement.setSourceReference(transferId + " · " + type.name());
        movement.setAmount(amount);
        movement.setPostedByUserId(request.getResponsibleUserId());
        movement.setPostingKey(transferKey);
        movement.setAttachmentReference(request.getAttachmentReference());
        movement.setNote(request.getReason());

        switch (type) {
            case SAFE_TO_REGISTER -> {
                requireId(request.getSourceCashLocationId(), "Source cash location");
                requireId(request.getDestinationRegisterId(), "Destination register");
                requireId(request.getDestinationCashierShiftId(), "Destination cashier shift");
                movement.setCashLocationId(request.getSourceCashLocationId());
                movement.setDestinationRegisterId(request.getDestinationRegisterId());
                movement.setDestinationCashierShiftId(request.getDestinationCashierShiftId());
                movement.setMovementType(CashMovementType.HANDOVER);
                movement.setDirection(CashMovementDirection.TRANSFER);
            }
            case REGISTER_TO_SAFE -> {
                requireId(request.getSourceRegisterId(), "Source register");
                requireId(request.getSourceCashierShiftId(), "Source cashier shift");
                requireId(request.getDestinationCashLocationId(), "Destination cash location");
                movement.setRegisterId(request.getSourceRegisterId());
                movement.setCashierShiftId(request.getSourceCashierShiftId());
                movement.setDestinationCashLocationId(request.getDestinationCashLocationId());
                movement.setMovementType(CashMovementType.HANDOVER);
                movement.setDirection(CashMovementDirection.TRANSFER);
            }
            case REGISTER_TO_REGISTER_HANDOVER -> {
                requireId(request.getSourceRegisterId(), "Source register");
                requireId(request.getSourceCashierShiftId(), "Source cashier shift");
                requireId(request.getDestinationRegisterId(), "Destination register");
                requireId(request.getDestinationCashierShiftId(), "Destination cashier shift");
                if (Objects.equals(request.getSourceRegisterId(), request.getDestinationRegisterId())
                        || Objects.equals(request.getSourceCashierShiftId(), request.getDestinationCashierShiftId())) {
                    throw new CashManagementValidationException("Source and destination register/shift must be different");
                }
                movement.setRegisterId(request.getSourceRegisterId());
                movement.setCashierShiftId(request.getSourceCashierShiftId());
                movement.setDestinationRegisterId(request.getDestinationRegisterId());
                movement.setDestinationCashierShiftId(request.getDestinationCashierShiftId());
                movement.setMovementType(CashMovementType.HANDOVER);
                movement.setDirection(CashMovementDirection.TRANSFER);
            }
            case CASH_DROP -> {
                requireId(request.getSourceRegisterId(), "Source register");
                requireId(request.getSourceCashierShiftId(), "Source cashier shift");
                requireId(request.getDestinationCashLocationId(), "Destination cash location");
                movement.setRegisterId(request.getSourceRegisterId());
                movement.setCashierShiftId(request.getSourceCashierShiftId());
                movement.setDestinationCashLocationId(request.getDestinationCashLocationId());
                movement.setMovementType(CashMovementType.CASH_DROP);
                movement.setDirection(CashMovementDirection.TRANSFER);
            }
            case CASH_DEPOSIT_TO_BANK -> {
                requireId(request.getSourceCashLocationId(), "Source cash location");
                movement.setCashLocationId(request.getSourceCashLocationId());
                movement.setExternalAccountReference(requiredText(
                        bankAccountReference, "Bank account reference"));
                movement.setMovementType(CashMovementType.BANK_DEPOSIT);
                movement.setDirection(CashMovementDirection.OUTFLOW);
            }
            case CASH_WITHDRAWAL_FROM_BANK -> {
                requireId(request.getDestinationCashLocationId(), "Destination cash location");
                movement.setCashLocationId(request.getDestinationCashLocationId());
                movement.setExternalAccountReference(requiredText(
                        bankAccountReference, "Bank account reference"));
                movement.setMovementType(CashMovementType.BANK_WITHDRAWAL);
                movement.setDirection(CashMovementDirection.INFLOW);
            }
        }

        BigDecimal approvalThreshold = settings == null ? null : settings.highValueTransferApprovalThreshold();
        boolean approvalRequired = thresholdExceeded(amount, approvalThreshold);
        if (approvalRequired && !request.isApprovalConfirmed()) {
            throw new CashManagementValidationException("Owner approval is required because the transfer exceeds the configured high-value threshold");
        }
        if (approvalRequired && currentActorService.isStaff()) {
            throw new CashManagementValidationException(
                    "The primary owner must submit this high-value cash transfer because owner approval is required");
        }
        movement.setApprovalRequired(approvalRequired);
        movement.setApprovedByOwnerId(approvalRequired ? ownerId : null);
        return post(businessId, branchId, movement);
    }

    @Transactional(readOnly = true)
    public boolean canReverseCashTransfer(Long businessId, Long branchId, Long originalCashMovementId) {
        requireBranch(businessId, branchId);
        CashMovement original = cashMovementRepository.findByIdAndBusinessIdAndBranchId(
                originalCashMovementId, businessId, branchId).orElse(null);
        if (original == null || !isDocumentedCashTransfer(original)
                || original.getStatus() != CashMovementStatus.POSTED || original.getReversalReferenceId() != null) {
            return false;
        }
        return isOpenShiftForTransferReversal(businessId, branchId, original.getCashierShiftId())
                && isOpenShiftForTransferReversal(businessId, branchId, original.getDestinationCashierShiftId());
    }

    @Transactional
    public CashMovementResponse reverseCashTransfer(Long businessId, Long branchId, Long ownerId,
                                                    Long originalCashMovementId, CashTransferReversalRequest request) {
        requireBranch(businessId, branchId);
        CashMovement original = cashMovementRepository.findByIdAndBusinessIdAndBranchId(
                        originalCashMovementId, businessId, branchId)
                .orElseThrow(() -> new CashManagementValidationException(
                        "Cash transfer movement was not found in this branch"));

        if (!isDocumentedCashTransfer(original)) {
            throw new CashManagementValidationException(
                    "Only a posted Cash Transfer/Handover created from the Cash Transfer page can be reversed here");
        }
        if (original.getStatus() != CashMovementStatus.POSTED || original.getReversalReferenceId() != null) {
            throw new CashManagementValidationException("Cash transfer has already been reversed");
        }
        requireOpenShiftForTransferReversal(businessId, branchId, original.getCashierShiftId(), "source");
        requireOpenShiftForTransferReversal(businessId, branchId, original.getDestinationCashierShiftId(), "destination");

        String reversalKey = requiredText(request.getReversalKey(), "Reversal request key");
        String reason = requiredText(request.getReason(), "Reversal reason");
        String sourceReference = original.getSourceReference() == null
                ? "Cash transfer reversal" : original.getSourceReference() + " · Reversal";
        return reverse(businessId, branchId, original.getId(), CashSourceModule.CASH_MANAGEMENT,
                original.getSourceTransactionId(), sourceReference, currentActorService.actorId(ownerId),
                "cash-transfer-reversal:" + original.getId() + ":" + reversalKey, reason);
    }

    public List<CashierShiftResponse> findUnresolvedVarianceShifts(Long businessId, Long branchId) {
        requireBranch(businessId, branchId);
        return cashierShiftRepository.findByBusinessIdAndBranchIdOrderByOpeningTimeDesc(businessId, branchId).stream()
                .filter(CashierShift::hasUnresolvedVariance)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CashMovementResponse resolveVariance(Long businessId, Long branchId, Long ownerId, Long shiftId,
                                                CashVarianceAdjustmentRequest request) {
        requireBranch(businessId, branchId);
        CashierShift shift = cashierShiftRepository.findForUpdate(businessId, branchId, shiftId)
                .orElseThrow(() -> new CashManagementValidationException("Cashier shift does not belong to this branch"));

        if (shift.getVarianceAdjustmentCashMovementId() != null) {
            return cashMovementRepository.findByIdAndBusinessIdAndBranchId(
                            shift.getVarianceAdjustmentCashMovementId(), businessId, branchId)
                    .map(this::toResponse)
                    .orElseThrow(() -> new CashManagementValidationException(
                            "Cash variance is already resolved but its linked adjustment movement was not found"));
        }
        if (!shift.hasUnresolvedVariance() || shift.getCashVariance() == null || shift.getCashVariance().signum() == 0) {
            throw new CashManagementValidationException("This cashier shift has no unresolved cash variance");
        }
        if (shift.getClosingCashLocationId() == null) {
            throw new CashManagementValidationException("The closed shift has no closing cash location for variance reconciliation");
        }
        if (shift.isVarianceApprovalRequired() && shift.getVarianceReviewedByOwnerId() == null) {
            throw new CashManagementValidationException("Required owner approval is missing for this cash variance");
        }

        CashLocation location = requireActiveLocation(businessId, branchId, shift.getClosingCashLocationId());
        BigDecimal amount = shift.getCashVariance().abs().setScale(4, java.math.RoundingMode.HALF_UP);
        CashMovementDirection direction = shift.getVarianceResult() == CashVarianceResult.SHORTAGE
                ? CashMovementDirection.OUTFLOW : CashMovementDirection.INFLOW;

        CashMovementRequest movement = new CashMovementRequest();
        movement.setCashLocationId(location.getId());
        movement.setSourceModule(CashSourceModule.CASH_MANAGEMENT);
        movement.setSourceTransactionId(String.valueOf(shift.getId()));
        movement.setSourceReference(shift.getShiftCode());
        movement.setMovementType(CashMovementType.VARIANCE_ADJUSTMENT);
        movement.setDirection(direction);
        movement.setAmount(amount);
        movement.setPostedByUserId(currentActorService.actorId(ownerId));
        movement.setPostingKey("variance-adjustment:shift:" + shift.getId());
        movement.setApprovalRequired(shift.isVarianceApprovalRequired());
        movement.setApprovedByOwnerId(shift.isVarianceApprovalRequired() ? shift.getVarianceReviewedByOwnerId() : null);
        movement.setNote(requiredText(request.getNote(), "Resolution note"));

        CashMovementResponse adjustment = post(businessId, branchId, movement);
        try {
            shift.attachVarianceAdjustment(adjustment.getId(), now());
        } catch (IllegalStateException ex) {
            throw new CashManagementValidationException(ex.getMessage());
        }
        cashierShiftRepository.saveAndFlush(shift);
        return adjustment;
    }

    @Override
    @Transactional
    public CashMovementResponse post(Long businessId, Long branchId, CashMovementRequest request) {
        requireBranch(businessId, branchId);
        validateMovementContext(businessId, branchId, request);

        CashMovement repeated = cashMovementRepository.findByBusinessIdAndBranchIdAndPostingKey(
                businessId, branchId, request.getPostingKey().trim()).orElse(null);
        if (repeated != null) return toResponse(repeated);

        Cashbook cashbook = cashbookRepository.findForUpdate(businessId, branchId)
                .orElseThrow(() -> new CashManagementValidationException(
                        "Cashbook must be initialized before posting physical cash movements"));

        CashMovement committedWhileWaiting = cashMovementRepository.findPostingForUpdate(
                businessId, branchId, request.getPostingKey().trim()).orElse(null);
        if (committedWhileWaiting != null) return toResponse(committedWhileWaiting);

        Instant now = now();
        BigDecimal balanceBefore = cashbook.post(request.getDirection(), request.getAmount(), now);
        cashbook = cashbookRepository.saveAndFlush(cashbook);

        CashMovement movement = CashMovement.posted(
                businessId, branchId, cashbook.getId(), request.getCashLocationId(),
                request.getDestinationCashLocationId(), request.getRegisterId(), request.getDestinationRegisterId(),
                request.getCashierShiftId(), request.getDestinationCashierShiftId(),
                request.getSourceModule(), request.getSourceTransactionId(), request.getSourceReference(),
                request.getMovementType(), request.getDirection(), request.getAmount(), balanceBefore,
                cashbook.getCurrentBalance(), request.getPostedByUserId(), request.getPostingKey(),
                request.getExternalAccountReference(), request.getAttachmentReference(),
                request.isApprovalRequired(), request.getApprovedByOwnerId(), request.getNote(), now);
        return toResponse(cashMovementRepository.saveAndFlush(movement));
    }

    @Override
    @Transactional
    public CashMovementResponse reverse(Long businessId,
                                        Long branchId,
                                        Long originalCashMovementId,
                                        CashSourceModule sourceModule,
                                        String sourceTransactionId,
                                        String sourceReference,
                                        Long postedByUserId,
                                        String postingKey,
                                        String reason) {
        requireBranch(businessId, branchId);
        if (originalCashMovementId == null || originalCashMovementId <= 0) {
            throw new CashManagementValidationException("Original Cash Movement ID is required");
        }
        if (sourceModule == null) {
            throw new CashManagementValidationException("Cash Movement source module is required");
        }
        if (postedByUserId == null || postedByUserId <= 0) {
            throw new CashManagementValidationException("Posted by user ID is required");
        }
        if (postingKey == null || postingKey.isBlank()) {
            throw new CashManagementValidationException("Cash Movement reversal posting key is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new CashManagementValidationException("Cash Movement reversal reason is required");
        }

        String safePostingKey = postingKey.trim();
        CashMovement repeated = cashMovementRepository.findByBusinessIdAndBranchIdAndPostingKey(
                businessId, branchId, safePostingKey).orElse(null);
        if (repeated != null) {
            return toResponse(repeated);
        }

        CashMovement original = cashMovementRepository.findForUpdate(
                        businessId, branchId, originalCashMovementId)
                .orElseThrow(() -> new CashManagementValidationException(
                        "Original Cash Movement was not found in this branch"));
        if (original.getStatus() != CashMovementStatus.POSTED || original.getReversalReferenceId() != null) {
            throw new CashManagementValidationException("Cash Movement has already been reversed");
        }

        Cashbook cashbook = cashbookRepository.findForUpdate(businessId, branchId)
                .orElseThrow(() -> new CashManagementValidationException(
                        "Cashbook must exist before reversing a physical cash movement"));

        CashMovement committedWhileWaiting = cashMovementRepository.findPostingForUpdate(
                businessId, branchId, safePostingKey).orElse(null);
        if (committedWhileWaiting != null) {
            return toResponse(committedWhileWaiting);
        }

        CashMovementDirection oppositeDirection = switch (original.getDirection()) {
            case INFLOW -> CashMovementDirection.OUTFLOW;
            case OUTFLOW -> CashMovementDirection.INFLOW;
            case TRANSFER -> CashMovementDirection.TRANSFER;
        };

        Instant now = now();
        BigDecimal balanceBefore = cashbook.post(oppositeDirection, original.getAmount(), now);
        cashbook = cashbookRepository.saveAndFlush(cashbook);

        boolean transfer = original.getDirection() == CashMovementDirection.TRANSFER;
        CashMovement reversal = CashMovement.posted(
                businessId,
                branchId,
                cashbook.getId(),
                transfer ? original.getDestinationCashLocationId() : original.getCashLocationId(),
                transfer ? original.getCashLocationId() : original.getDestinationCashLocationId(),
                transfer ? original.getDestinationRegisterId() : original.getRegisterId(),
                transfer ? original.getRegisterId() : original.getDestinationRegisterId(),
                transfer ? original.getDestinationCashierShiftId() : original.getCashierShiftId(),
                transfer ? original.getCashierShiftId() : original.getDestinationCashierShiftId(),
                sourceModule,
                sourceTransactionId,
                sourceReference,
                CashMovementType.REVERSAL,
                oppositeDirection,
                original.getAmount(),
                balanceBefore,
                cashbook.getCurrentBalance(),
                postedByUserId,
                safePostingKey,
                original.getExternalAccountReference(),
                original.getAttachmentReference(),
                false,
                null,
                reason,
                now
        );
        reversal.referenceOriginalMovement(original.getId());
        reversal = cashMovementRepository.saveAndFlush(reversal);

        original.linkReversal(reversal.getId());
        cashMovementRepository.saveAndFlush(original);
        return toResponse(reversal);
    }

    public CashbookResponse findCashbook(Long businessId, Long branchId) {
        requireBranch(businessId, branchId);
        return cashbookRepository.findByBusinessIdAndBranchId(businessId, branchId)
                .map(this::toResponse)
                .orElse(null);
    }

    public List<CashMovementResponse> findMovements(Long businessId, Long branchId) {
        requireBranch(businessId, branchId);
        return cashMovementRepository.findAllByBusinessIdAndBranchIdOrderByPostedAtDesc(businessId, branchId)
                .stream().map(this::toResponse).toList();
    }

    public Page<CashMovementResponse> searchMovements(Long businessId, Long branchId, Long cashLocationId,
                                                       Long registerId, Long shiftId, CashMovementType movementType,
                                                       CashSourceModule sourceModule, Long userId, CashMovementStatus status,
                                                       Instant fromTime, Instant toTime, String query, Pageable pageable) {
        requireBranch(businessId, branchId);
        return cashMovementRepository.search(businessId, branchId, cashLocationId, registerId, shiftId, movementType,
                sourceModule, userId, status, fromTime == null ? FILTER_START : fromTime,
                toTime == null ? FILTER_END : toTime,
                query == null ? "" : query.trim(), pageable).map(this::toResponse);
    }

    public List<RegisterResponse> findRegisters(Long businessId, Long branchId) {
        requireBranch(businessId, branchId);
        return registerRepository.findByBusinessIdAndBranchIdOrderByNameAsc(businessId, branchId).stream().map(this::toResponse).toList();
    }

    public List<CashLocationResponse> findCashLocations(Long businessId, Long branchId) {
        requireBranch(businessId, branchId);
        return cashLocationRepository.findByBusinessIdAndBranchIdOrderByNameAsc(businessId, branchId).stream().map(this::toResponse).toList();
    }

    public List<CashLocationResponse> findAllowedOpeningCashLocations(Long businessId, Long branchId) {
        BranchSettingsResponse settings = branchSettingsAccessService
                .findByBusinessIdAndBranchId(businessId, branchId).orElse(null);
        return filterAllowedCashLocations(findCashLocations(businessId, branchId),
                settings == null ? null : settings.allowedOpeningSources());
    }

    public List<CashLocationResponse> findAllowedClosingCashLocations(Long businessId, Long branchId) {
        BranchSettingsResponse settings = branchSettingsAccessService
                .findByBusinessIdAndBranchId(businessId, branchId).orElse(null);
        return filterAllowedCashLocations(findCashLocations(businessId, branchId),
                settings == null ? null : settings.allowedClosingDestinations());
    }

    public List<CashierShiftResponse> findShifts(Long businessId, Long branchId) {
        requireBranch(businessId, branchId);
        return cashierShiftRepository.findByBusinessIdAndBranchIdOrderByOpeningTimeDesc(businessId, branchId).stream().map(this::toResponse).toList();
    }

    public long countOpenShifts(Long businessId, Long branchId) {
        requireBranch(businessId, branchId);
        return cashierShiftRepository.countByBusinessIdAndBranchIdAndStatus(businessId, branchId, CashierShiftStatus.OPEN);
    }

    public Map<Long, CashierShiftMovementSummaryResponse> summarizeShifts(Long businessId, Long branchId,
                                                                          List<Long> shiftIds) {
        requireBranch(businessId, branchId);
        if (shiftIds == null || shiftIds.isEmpty()) return Map.of();

        Map<Long, ShiftSummaryAccumulator> totals = new HashMap<>();
        shiftIds.forEach(id -> totals.put(id, new ShiftSummaryAccumulator()));
        for (CashMovement movement : cashMovementRepository.findAllForShifts(businessId, branchId, shiftIds)) {
            if (movement.getStatus() != CashMovementStatus.POSTED) continue;
            Long sourceShiftId = movement.getCashierShiftId();
            Long destinationShiftId = movement.getDestinationCashierShiftId();

            if (sourceShiftId != null && totals.containsKey(sourceShiftId)) {
                ShiftSummaryAccumulator acc = totals.get(sourceShiftId);
                acc.recordActivity(movement.getPostedAt());
                if (movement.getMovementType() != CashMovementType.OPENING_FLOAT) acc.transactionCount++;
                if (movement.getDirection() == CashMovementDirection.INFLOW) {
                    acc.cashInflows = acc.cashInflows.add(movement.getAmount());
                } else if (movement.getDirection() == CashMovementDirection.OUTFLOW) {
                    acc.cashOutflows = acc.cashOutflows.add(movement.getAmount());
                } else if (movement.getDirection() == CashMovementDirection.TRANSFER
                        && movement.getMovementType() != CashMovementType.OPENING_FLOAT
                        && !Objects.equals(sourceShiftId, destinationShiftId)) {
                    acc.transfersOut = acc.transfersOut.add(movement.getAmount());
                }
            }
            if (destinationShiftId != null && totals.containsKey(destinationShiftId)
                    && !Objects.equals(sourceShiftId, destinationShiftId)) {
                ShiftSummaryAccumulator acc = totals.get(destinationShiftId);
                acc.recordActivity(movement.getPostedAt());
                if (movement.getMovementType() != CashMovementType.OPENING_FLOAT) acc.transactionCount++;
                if (movement.getDirection() == CashMovementDirection.TRANSFER) {
                    acc.transfersIn = acc.transfersIn.add(movement.getAmount());
                }
            }
        }

        Map<Long, CashierShiftMovementSummaryResponse> result = new HashMap<>();
        totals.forEach((id, acc) -> result.put(id, acc.toResponse()));
        return result;
    }

    public Page<CashierShiftResponse> searchShifts(Long businessId, Long branchId, Long registerId, Long cashierId,
                                                    CashierShiftStatus status, CashVarianceResult varianceResult,
                                                    Boolean approvalRequired, Instant fromTime, Instant toTime,
                                                    String query, Pageable pageable) {
        requireBranch(businessId, branchId);
        return cashierShiftRepository.search(businessId, branchId, registerId, cashierId, status, varianceResult,
                approvalRequired, fromTime == null ? FILTER_START : fromTime,
                toTime == null ? FILTER_END : toTime,
                query == null ? "" : query.trim(), pageable).map(this::toResponse);
    }

    public CashierShiftResponse findShift(Long businessId, Long branchId, Long shiftId) {
        requireBranch(businessId, branchId);
        return cashierShiftRepository.findByIdAndBusinessIdAndBranchId(shiftId, businessId, branchId).map(this::toResponse).orElse(null);
    }

    public List<CashMovementResponse> findShiftMovements(Long businessId, Long branchId, Long shiftId) {
        requireBranch(businessId, branchId);
        CashierShift shift = cashierShiftRepository.findByIdAndBusinessIdAndBranchId(shiftId, businessId, branchId)
                .orElseThrow(() -> new CashManagementValidationException("Cashier shift does not belong to this branch"));
        return cashMovementRepository.findAllForShiftOrderByPostedAtAsc(shift.getId())
                .stream().map(this::toResponse).toList();
    }

    public CashMovementResponse findMovement(Long businessId, Long branchId, Long movementId) {
        requireBranch(businessId, branchId);
        return cashMovementRepository.findByIdAndBusinessIdAndBranchId(movementId, businessId, branchId)
                .map(this::toResponse)
                .orElseThrow(CashManagementAccessDeniedException::new);
    }

    private boolean isDocumentedCashTransfer(CashMovement movement) {
        return movement.getSourceModule() == CashSourceModule.CASH_MANAGEMENT
                && movement.getSourceTransactionId() != null
                && movement.getSourceTransactionId().startsWith("CT-")
                && Set.of(CashMovementType.HANDOVER, CashMovementType.CASH_DROP,
                        CashMovementType.BANK_DEPOSIT, CashMovementType.BANK_WITHDRAWAL)
                .contains(movement.getMovementType());
    }

    private boolean isOpenShiftForTransferReversal(Long businessId, Long branchId, Long shiftId) {
        if (shiftId == null) return true;
        return cashierShiftRepository.findByIdAndBusinessIdAndBranchId(shiftId, businessId, branchId)
                .map(CashierShift::isOpen)
                .orElse(false);
    }

    private void requireOpenShiftForTransferReversal(Long businessId, Long branchId, Long shiftId, String side) {
        if (shiftId == null) return;
        CashierShift shift = cashierShiftRepository.findByIdAndBusinessIdAndBranchId(shiftId, businessId, branchId)
                .orElseThrow(() -> new CashManagementValidationException(
                        "The " + side + " cashier shift linked to this transfer was not found in the active branch"));
        if (!shift.isOpen()) {
            throw new CashManagementValidationException(
                    "This transfer cannot be reversed directly because its " + side
                            + " cashier shift is already closed. Closed shift reconciliation remains immutable");
        }
    }

    private void validateMovementContext(Long businessId, Long branchId, CashMovementRequest request) {
        if (request.getCashLocationId() != null) {
            requireActiveLocation(businessId, branchId, request.getCashLocationId());
        }
        if (request.getDestinationCashLocationId() != null) {
            requireActiveLocation(businessId, branchId, request.getDestinationCashLocationId());
        }
        if (request.getRegisterId() != null) {
            requireActiveRegister(businessId, branchId, request.getRegisterId());
        }
        if (request.getDestinationRegisterId() != null) {
            requireActiveRegister(businessId, branchId, request.getDestinationRegisterId());
        }
        if (request.getCashierShiftId() != null) {
            requireOpenShift(businessId, branchId, request.getCashierShiftId(), request.getRegisterId(), "Source");
        }
        if (request.getDestinationCashierShiftId() != null) {
            requireOpenShift(businessId, branchId, request.getDestinationCashierShiftId(), request.getDestinationRegisterId(), "Destination");
        }
        if (request.getDirection() == CashMovementDirection.TRANSFER
                && request.getCashLocationId() == null
                && request.getRegisterId() == null) {
            throw new CashManagementValidationException("A cash transfer requires a source cash location or register");
        }
    }

    private BigDecimal calculateExpectedCash(CashierShift shift, List<CashMovement> movements) {
        BigDecimal expected = shift.getOpeningFloat().setScale(4, java.math.RoundingMode.HALF_UP);
        for (CashMovement movement : movements) {
            if (movement.getStatus() != CashMovementStatus.POSTED) continue;
            boolean sourceShift = Objects.equals(movement.getCashierShiftId(), shift.getId());
            boolean destinationShift = Objects.equals(movement.getDestinationCashierShiftId(), shift.getId());

            if (movement.getDirection() == CashMovementDirection.TRANSFER) {
                if (movement.getMovementType() == CashMovementType.OPENING_FLOAT && sourceShift) continue;
                if (sourceShift && !destinationShift) expected = expected.subtract(movement.getAmount());
                if (destinationShift && !sourceShift) expected = expected.add(movement.getAmount());
                continue;
            }
            if (!sourceShift && !destinationShift) continue;
            if (movement.getDirection() == CashMovementDirection.INFLOW) expected = expected.add(movement.getAmount());
            if (movement.getDirection() == CashMovementDirection.OUTFLOW) expected = expected.subtract(movement.getAmount());
        }
        if (expected.signum() < 0) {
            throw new CashManagementValidationException("Expected shift cash cannot be negative; review linked Cash Movements before closing");
        }
        return expected.setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private boolean hasPostedCashInflow(Long shiftId) {
        return cashMovementRepository.findAllForShiftOrderByPostedAtAsc(shiftId).stream()
                .anyMatch(movement -> movement.getStatus() == CashMovementStatus.POSTED
                        && movement.getDirection() == CashMovementDirection.INFLOW
                        && Objects.equals(movement.getCashierShiftId(), shiftId));
    }

    private boolean hasPostedCashDrop(Long shiftId) {
        return cashMovementRepository.findAllForShiftOrderByPostedAtAsc(shiftId).stream()
                .anyMatch(movement -> movement.getStatus() == CashMovementStatus.POSTED
                        && movement.getMovementType() == CashMovementType.CASH_DROP
                        && Objects.equals(movement.getCashierShiftId(), shiftId));
    }

    private List<CashLocationResponse> filterAllowedCashLocations(List<CashLocationResponse> locations, String configuredReferences) {
        Set<String> allowed = configuredReferences(configuredReferences);
        if (allowed.isEmpty()) return locations;
        return locations.stream().filter(location -> matchesConfiguredReference(
                location.getId(), location.getName(), location.getType().name(), allowed)).toList();
    }

    private void validateAllowedCashLocation(CashLocation location, String configuredReferences, String label) {
        Set<String> allowed = configuredReferences(configuredReferences);
        if (allowed.isEmpty()) return;
        if (!matchesConfiguredReference(location.getId(), location.getName(), location.getType().name(), allowed)) {
            throw new CashManagementValidationException("Selected " + label + " is not allowed by the current branch settings");
        }
    }

    private Set<String> configuredReferences(String configuredReferences) {
        if (configuredReferences == null || configuredReferences.isBlank()) return Set.of();
        return Arrays.stream(configuredReferences.split(","))
                .map(String::trim).filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean matchesConfiguredReference(Long id, String name, String type, Set<String> allowed) {
        return allowed.contains(String.valueOf(id).toLowerCase(Locale.ROOT))
                || (name != null && allowed.contains(name.trim().toLowerCase(Locale.ROOT)))
                || (type != null && allowed.contains(type.trim().toLowerCase(Locale.ROOT)));
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) return primary.trim();
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }

    private void validateOpeningFloatPolicy(BranchSettingsResponse settings, BigDecimal openingFloat) {
        BigDecimal amount = nonNegativeMoney(openingFloat, "Opening float");
        if (settings == null || settings.openingFloatPolicy() == null) return;

        switch (settings.openingFloatPolicy()) {
            case OPTIONAL -> {
                // Zero or a positive opening float is allowed.
            }
            case REQUIRED -> {
                if (amount.signum() <= 0) {
                    throw new CashManagementValidationException(
                            "Opening float is required for cashier shifts in this branch");
                }
            }
            case FIXED_AMOUNT -> {
                BigDecimal fixed = settings.fixedOpeningFloatAmount();
                if (fixed == null || fixed.signum() < 0) {
                    throw new CashManagementValidationException(
                            "A valid fixed opening float amount must be configured before opening a shift");
                }
                if (amount.compareTo(fixed) != 0) {
                    throw new CashManagementValidationException(
                            "Opening float must equal the configured fixed amount of "
                                    + fixed.stripTrailingZeros().toPlainString());
                }
            }
        }
    }

    private boolean thresholdExceeded(BigDecimal value, BigDecimal threshold) {
        return threshold != null && threshold.signum() >= 0 && value.compareTo(threshold) > 0;
    }

    private BigDecimal nonNegativeMoney(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new CashManagementValidationException(field + " must be zero or greater");
        }
        return value.setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal positiveMoney(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new CashManagementValidationException(field + " must be greater than zero");
        }
        return value.setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank()) throw new CashManagementValidationException(field + " is required");
        return value.trim();
    }

    private void requireId(Long value, String field) {
        if (value == null || value <= 0) throw new CashManagementValidationException(field + " is required");
    }

    private CashierShift requireOpenShift(Long businessId, Long branchId, Long shiftId, Long registerId, String label) {
        CashierShift shift = cashierShiftRepository.findByIdAndBusinessIdAndBranchId(shiftId, businessId, branchId)
                .orElseThrow(() -> new CashManagementValidationException(label + " cashier shift does not belong to this branch"));
        if (!shift.isOpen()) {
            throw new CashManagementValidationException(label + " cashier shift is closed");
        }
        if (registerId != null && !shift.getRegisterId().equals(registerId)) {
            throw new CashManagementValidationException(label + " cashier shift and register do not match");
        }
        return shift;
    }

    private Register requireActiveRegister(Long businessId, Long branchId, Long registerId) {
        Register register = registerRepository.findByIdAndBusinessIdAndBranchId(registerId, businessId, branchId)
                .orElseThrow(CashRegisterNotFoundException::new);
        if (!register.isActive()) throw new CashManagementValidationException("Inactive register cannot accept new postings");
        return register;
    }

    private CashLocation requireActiveLocation(Long businessId, Long branchId, Long locationId) {
        CashLocation location = cashLocationRepository.findByIdAndBusinessIdAndBranchId(locationId, businessId, branchId)
                .orElseThrow(CashLocationNotFoundException::new);
        if (!location.isActive()) throw new CashManagementValidationException("Inactive cash location cannot accept new postings");
        return location;
    }

    private void requireBranch(Long businessId, Long branchId) {
        branchAccessService.findActiveByBusinessIdAndBranchId(businessId, branchId)
                .orElseThrow(CashManagementAccessDeniedException::new);
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private RegisterResponse toResponse(Register register) {
        return new RegisterResponse(register.getId(), register.getBusinessId(), register.getBranchId(),
                register.getName(), register.getCode(), register.getStatus(), register.getCreatedAt());
    }

    private CashLocationResponse toResponse(CashLocation location) {
        return new CashLocationResponse(location.getId(), location.getBusinessId(), location.getBranchId(),
                location.getName(), location.getType(), location.getStatus(), location.getCreatedAt());
    }

    private CashierShiftResponse toResponse(CashierShift shift) {
        return new CashierShiftResponse(shift.getId(), shift.getBusinessId(), shift.getBranchId(), shift.getRegisterId(),
                shift.getCashierUserId(), shift.getSourceCashLocationId(), shift.getShiftCode(), shift.getOpeningTime(),
                shift.getOpeningFloat(), shift.getNote(), shift.getStatus(), shift.getClosingTime(),
                shift.getExpectedCash(), shift.getPhysicalCountedCash(), shift.getCashVariance(), shift.getVarianceResult(),
                shift.getClosingCashLocationId(), shift.getDenominationCount(), shift.getClosingNote(),
                shift.isVarianceApprovalRequired(), shift.getVarianceReviewedByOwnerId(), shift.getVarianceReviewedAt(),
                shift.getClosedByOwnerId(), shift.getClosingTransferCashMovementId(),
                shift.getVarianceAdjustmentCashMovementId(), shift.getVarianceResolvedAt(), shift.hasUnresolvedVariance());
    }

    private CashbookResponse toResponse(Cashbook cashbook) {
        return new CashbookResponse(cashbook.getId(), cashbook.getBusinessId(), cashbook.getBranchId(),
                cashbook.getOpeningBalance(), cashbook.getCurrentBalance(), cashbook.getCreatedAt(), cashbook.getUpdatedAt());
    }

    private CashMovementResponse toResponse(CashMovement movement) {
        return new CashMovementResponse(movement.getId(), movement.getBusinessId(), movement.getBranchId(),
                movement.getCashbookId(), movement.getCashLocationId(), movement.getDestinationCashLocationId(),
                movement.getRegisterId(), movement.getDestinationRegisterId(), movement.getCashierShiftId(),
                movement.getDestinationCashierShiftId(), movement.getSourceModule(), movement.getSourceTransactionId(),
                movement.getSourceReference(), movement.getMovementType(), movement.getDirection(), movement.getAmount(),
                movement.getBalanceBefore(), movement.getBalanceAfter(), movement.getPostedByUserId(), movement.getPostedAt(),
                movement.getStatus(), movement.getReversalReferenceId(), movement.getExternalAccountReference(),
                movement.getAttachmentReference(), movement.isApprovalRequired(), movement.getApprovedByOwnerId(),
                movement.getApprovedAt(), movement.getNote());
    }

    private static final class ShiftSummaryAccumulator {
        private BigDecimal cashInflows = BigDecimal.ZERO.setScale(4);
        private BigDecimal cashOutflows = BigDecimal.ZERO.setScale(4);
        private BigDecimal transfersIn = BigDecimal.ZERO.setScale(4);
        private BigDecimal transfersOut = BigDecimal.ZERO.setScale(4);
        private long transactionCount;
        private Instant lastActivity;

        private void recordActivity(Instant postedAt) {
            if (postedAt != null && (lastActivity == null || postedAt.isAfter(lastActivity))) lastActivity = postedAt;
        }

        private CashierShiftMovementSummaryResponse toResponse() {
            return new CashierShiftMovementSummaryResponse(cashInflows, cashOutflows, transfersIn, transfersOut,
                    transactionCount, lastActivity);
        }
    }
}
