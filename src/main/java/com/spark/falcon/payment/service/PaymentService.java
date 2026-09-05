package com.spark.falcon.payment.service;

import com.spark.falcon.branch.dto.response.BranchAccessResponse;
import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.cashmanagement.dto.CashMovementRequest;
import com.spark.falcon.cashmanagement.dto.CashMovementResponse;
import com.spark.falcon.cashmanagement.entity.CashMovementDirection;
import com.spark.falcon.cashmanagement.entity.CashMovementType;
import com.spark.falcon.cashmanagement.entity.CashSourceModule;
import com.spark.falcon.cashmanagement.service.CashManagementPostingService;
import com.spark.falcon.payment.dto.InitialSupplierPaymentCommand;
import com.spark.falcon.payment.dto.CustomerRefundCommand;
import com.spark.falcon.payment.dto.CustomerPaymentCommand;
import com.spark.falcon.payment.dto.CustomerPaymentAllocationCommand;
import com.spark.falcon.payment.dto.PaymentAllocationResponse;
import com.spark.falcon.payment.dto.PaymentAuditResponse;
import com.spark.falcon.payment.dto.PaymentResponse;
import com.spark.falcon.payment.dto.PaymentHistoryFilter;
import com.spark.falcon.payment.dto.PaymentOverviewResponse;
import com.spark.falcon.payment.dto.SupplierPaymentAllocationCommand;
import com.spark.falcon.payment.dto.SupplierPaymentCommand;
import com.spark.falcon.payment.dto.SalePaymentCommand;
import com.spark.falcon.payment.dto.SaleReceiptPaymentResponse;
import com.spark.falcon.payment.entity.Payment;
import com.spark.falcon.payment.entity.PaymentAllocation;
import com.spark.falcon.payment.entity.PaymentAllocationEffect;
import com.spark.falcon.payment.entity.PaymentAuditAction;
import com.spark.falcon.payment.entity.PaymentAuditEvent;
import com.spark.falcon.payment.entity.PaymentDirection;
import com.spark.falcon.payment.entity.PaymentFinancialPurpose;
import com.spark.falcon.payment.entity.PaymentInvoiceType;
import com.spark.falcon.payment.entity.PaymentSourceModule;
import com.spark.falcon.payment.entity.PaymentStatus;
import com.spark.falcon.payment.entity.CustomerPaymentSettlementType;
import com.spark.falcon.payment.exception.PaymentAccessDeniedException;
import com.spark.falcon.payment.exception.PaymentNotFoundException;
import com.spark.falcon.payment.exception.PaymentStateException;
import com.spark.falcon.payment.exception.PaymentValidationException;
import com.spark.falcon.payment.repository.PaymentAllocationRepository;
import com.spark.falcon.payment.repository.PaymentAuditEventRepository;
import com.spark.falcon.payment.repository.PaymentRepository;
import com.spark.falcon.purchase.dto.response.PurchasePaymentAllocationResult;
import com.spark.falcon.purchase.dto.response.PurchasePaymentInvoiceResponse;
import com.spark.falcon.purchase.service.PurchasePaymentAccessService;
import com.spark.falcon.sale.dto.SalePaymentAllocationResult;
import com.spark.falcon.sale.dto.SalePaymentInvoiceResponse;
import com.spark.falcon.sale.service.SalePaymentAccessService;
import com.spark.falcon.customer.service.CustomerAccessService;
import com.spark.falcon.settings.dto.response.PaymentMethodResponse;
import com.spark.falcon.settings.service.PaymentMethodAccessService;
import com.spark.falcon.supplier.service.SupplierService;
import com.spark.falcon.user.service.UserAccessService;
import com.spark.falcon.identity.security.CurrentActorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService implements PaymentPostingService, SalePaymentReadService,
        PaymentCustomerFinancialReadService, PaymentSupplierFinancialReadService {

    private static final int MONEY_SCALE = 4;
    private static final Instant EARLIEST_QUERY_TIME = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant LATEST_QUERY_TIME = Instant.parse("9999-12-31T23:59:59Z");

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final PaymentAuditEventRepository auditRepository;
    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final SupplierService supplierService;
    private final PaymentMethodAccessService paymentMethodAccessService;
    private final PurchasePaymentAccessService purchasePaymentAccessService;
    private final SalePaymentAccessService salePaymentAccessService;
    private final CustomerAccessService customerAccessService;
    private final CashManagementPostingService cashManagementPostingService;
    private final CurrentActorService currentActorService;
    private final UserAccessService userAccessService;
    private final Clock clock;

    public Page<PaymentResponse> findHistory(Long ownerId,
                                             PaymentHistoryFilter filter,
                                             Pageable pageable) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        PaymentHistoryFilter safe = filter == null
                ? new PaymentHistoryFilter(null, null, null, null, null, null, null, null, null, null, null)
                : filter;
        if (safe.branchId() != null) requireBranch(business.businessId(), safe.branchId());
        Instant fromTime = safe.from() == null ? EARLIEST_QUERY_TIME : safe.from();
        Instant toTime = safe.to() == null ? LATEST_QUERY_TIME : safe.to();
        return paymentRepository.searchHistory(
                        business.businessId(), safe.branchId(), safe.paymentId(), safe.invoiceId(),
                        safe.customerId(), safe.supplierId(), safe.paymentMethodId(), safe.direction(),
                        safe.status(), safe.actorId(), fromTime, toTime, pageable)
                .map(this::response);
    }

    public PaymentOverviewResponse overview(Long ownerId, Long branchId, Instant from, Instant to) {
        return overview(ownerId, branchId, null, null, null, null, from, to);
    }

    public PaymentOverviewResponse overview(Long ownerId, Long branchId, Long paymentMethodId,
                                            PaymentDirection direction, PaymentStatus status, Long actorId,
                                            Instant from, Instant to) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        if (branchId != null) requireBranch(business.businessId(), branchId);
        Instant fromTime = Objects.requireNonNull(from, "from is required");
        Instant toTime = Objects.requireNonNull(to, "to is required");
        if (!fromTime.isBefore(toTime)) throw new PaymentValidationException("Payment overview date range is invalid");

        if (paymentMethodId == null && direction == null && status == null && actorId == null) {
            BigDecimal customer = paymentRepository.sumCustomerDueCollected(
                    business.businessId(), branchId, fromTime, toTime);
            BigDecimal supplier = paymentRepository.sumByPurpose(
                    business.businessId(), branchId, PaymentStatus.CONFIRMED,
                    PaymentFinancialPurpose.SUPPLIER_DUE_SETTLEMENT, fromTime, toTime);
            BigDecimal cashIn = paymentRepository.sumByCashAndDirection(
                    business.businessId(), branchId, PaymentStatus.CONFIRMED, true,
                    PaymentDirection.INFLOW, fromTime, toTime);
            BigDecimal cashOut = paymentRepository.sumByCashAndDirection(
                    business.businessId(), branchId, PaymentStatus.CONFIRMED, true,
                    PaymentDirection.OUTFLOW, fromTime, toTime);
            BigDecimal cards = paymentRepository.sumCollectionByMethodPatterns(
                    business.businessId(), branchId, "%card%", "%visa%", fromTime, toTime);
            BigDecimal mobile = paymentRepository.sumCollectionByMethodPatterns(
                    business.businessId(), branchId, "%bkash%", "%nagad%", fromTime, toTime);
            BigDecimal bank = paymentRepository.sumCollectionByMethodPatterns(
                    business.businessId(), branchId, "%bank%", "%cheque%", fromTime, toTime);
            return new PaymentOverviewResponse(customer, supplier, cashIn, cashOut, cards, mobile, bank,
                    paymentRepository.countStatus(business.businessId(), branchId, PaymentStatus.FAILED, fromTime, toTime),
                    paymentRepository.countStatus(business.businessId(), branchId, PaymentStatus.REVERSED, fromTime, toTime),
                    paymentRepository.sumAvailableCustomerCredit(business.businessId(), branchId),
                    paymentRepository.countStatus(
                            business.businessId(), branchId, PaymentStatus.PENDING, fromTime, toTime));
        }

        List<Payment> rows = paymentRepository.findOverviewRows(
                business.businessId(), branchId, paymentMethodId, direction, status, actorId, fromTime, toTime);
        BigDecimal customer = rows.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.CONFIRMED)
                .filter(payment -> payment.getFinancialPurpose() == PaymentFinancialPurpose.CUSTOMER_DUE_COLLECTION)
                .map(Payment::effectiveAllocatedAmount).reduce(zero(), BigDecimal::add);
        BigDecimal supplier = rows.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.CONFIRMED)
                .filter(payment -> payment.getFinancialPurpose() == PaymentFinancialPurpose.SUPPLIER_DUE_SETTLEMENT)
                .map(Payment::getAmount).reduce(zero(), BigDecimal::add);
        BigDecimal cashIn = rows.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.CONFIRMED && payment.isCashPayment())
                .filter(payment -> payment.getDirection() == PaymentDirection.INFLOW)
                .map(payment -> payment.getAmount().subtract(payment.effectiveChangeAmount()))
                .reduce(zero(), BigDecimal::add);
        BigDecimal cashOut = rows.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.CONFIRMED && payment.isCashPayment())
                .filter(payment -> payment.getDirection() == PaymentDirection.OUTFLOW)
                .map(Payment::getAmount).reduce(zero(), BigDecimal::add);
        BigDecimal cards = collectionByMethod(rows, "card", "visa", "master");
        BigDecimal mobile = collectionByMethod(rows, "bkash", "nagad", "mobile");
        BigDecimal bank = collectionByMethod(rows, "bank", "cheque", "check");
        long failed = rows.stream().filter(payment -> payment.getStatus() == PaymentStatus.FAILED).count();
        long reversed = rows.stream().filter(payment -> payment.getStatus() == PaymentStatus.REVERSED).count();
        long pending = rows.stream().filter(payment -> payment.getStatus() == PaymentStatus.PENDING).count();
        return new PaymentOverviewResponse(customer, supplier, cashIn, cashOut, cards, mobile, bank, failed, reversed,
                paymentRepository.sumAvailableCustomerCredit(business.businessId(), branchId), pending);
    }

    private BigDecimal collectionByMethod(List<Payment> rows, String... patterns) {
        return rows.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.CONFIRMED)
                .filter(payment -> payment.getDirection() == PaymentDirection.INFLOW && !payment.isCashPayment())
                .filter(payment -> methodMatches(payment, patterns))
                .map(payment -> payment.getAmount().subtract(payment.effectiveChangeAmount()))
                .reduce(zero(), BigDecimal::add);
    }

    private boolean methodMatches(Payment payment, String... patterns) {
        String name = payment.getPaymentMethodNameSnapshot() == null ? ""
                : payment.getPaymentMethodNameSnapshot().toLowerCase(java.util.Locale.ROOT);
        String code = payment.getPaymentMethodCodeSnapshot() == null ? ""
                : payment.getPaymentMethodCodeSnapshot().toLowerCase(java.util.Locale.ROOT);
        for (String pattern : patterns) {
            if (name.contains(pattern) || code.contains(pattern)) return true;
        }
        return false;
    }

    @Override
    public BigDecimal availableCustomerCredit(Long businessId, Long branchId, Long customerId) {
        return paymentRepository.sumAvailableCustomerCreditForCustomer(businessId, branchId, customerId);
    }

    @Override
    public List<com.spark.falcon.payment.dto.CustomerPaymentStatementEntryResponse> findCustomerStatementEntries(
            Long businessId, Long branchId, Long customerId, Instant from, Instant to) {
        List<com.spark.falcon.payment.dto.CustomerPaymentStatementEntryResponse> entries = new ArrayList<>();
        Instant fromTime = from == null ? EARLIEST_QUERY_TIME : from;
        Instant toTime = to == null ? LATEST_QUERY_TIME : to;
        for (Payment payment : paymentRepository.findCustomerStatementPayments(
                businessId, branchId, customerId, fromTime, toTime)) {
            String sourcePath = "/owner/payments/" + payment.getId();
            for (PaymentAllocation allocation : allocationRepository.findByPaymentIdOrderByIdAsc(payment.getId())) {
                boolean reversal = allocation.getEffect() == PaymentAllocationEffect.REVERSE;
                entries.add(new com.spark.falcon.payment.dto.CustomerPaymentStatementEntryResponse(
                        payment.getConfirmedAt(), "PAY-" + payment.getId(), payment.getBranchId(),
                        reversal ? "PAYMENT_REVERSAL" : "PAYMENT_ALLOCATION",
                        reversal ? allocation.getAmount() : zero(),
                        reversal ? zero() : allocation.getAmount(), sourcePath));
            }
            if (payment.getFinancialPurpose() == PaymentFinancialPurpose.CUSTOMER_REFUND) {
                entries.add(new com.spark.falcon.payment.dto.CustomerPaymentStatementEntryResponse(
                        payment.getConfirmedAt(), "PAY-" + payment.getId(), payment.getBranchId(),
                        "CUSTOMER_REFUND", payment.getAmount(), zero(), sourcePath));
            } else if (payment.getFinancialPurpose() == PaymentFinancialPurpose.CUSTOMER_REFUND_REVERSAL) {
                entries.add(new com.spark.falcon.payment.dto.CustomerPaymentStatementEntryResponse(
                        payment.getConfirmedAt(), "PAY-" + payment.getId(), payment.getBranchId(),
                        "CUSTOMER_REFUND_REVERSAL", zero(), payment.getAmount(), sourcePath));
            }
            if (payment.getStatus() == PaymentStatus.CONFIRMED
                    && payment.effectiveCustomerCreditAmount().signum() > 0) {
                entries.add(new com.spark.falcon.payment.dto.CustomerPaymentStatementEntryResponse(
                        payment.getConfirmedAt(), "PAY-" + payment.getId(), payment.getBranchId(),
                        "CUSTOMER_CREDIT", zero(), payment.effectiveCustomerCreditAmount(), sourcePath));
            }
            if (payment.getReversalOfPaymentId() != null) {
                paymentRepository.findById(payment.getReversalOfPaymentId())
                        .map(Payment::effectiveCustomerCreditAmount)
                        .filter(value -> value.signum() > 0)
                        .ifPresent(value -> entries.add(
                                new com.spark.falcon.payment.dto.CustomerPaymentStatementEntryResponse(
                                        payment.getConfirmedAt(), "PAY-" + payment.getId(), payment.getBranchId(),
                                        "CUSTOMER_CREDIT_REVERSAL", value, zero(), sourcePath)));
            }
        }
        return entries.stream().sorted(Comparator.comparing(
                com.spark.falcon.payment.dto.CustomerPaymentStatementEntryResponse::transactionAt)).toList();
    }

    @Override
    public Map<Long, BigDecimal> customerPaymentReversalsByInvoice(
            Long businessId, Long branchId, Long customerId) {
        return allocationRepository.sumCustomerReversalsByInvoice(
                        businessId, branchId, customerId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        PaymentAllocationRepository.InvoiceAmountProjection::getInvoiceId,
                        PaymentAllocationRepository.InvoiceAmountProjection::getAmount));
    }

    @Override
    public BigDecimal confirmedSupplierAllocations(Long businessId, Long branchId, Long supplierId) {
        return allocationRepository.sumNetSupplierAllocations(businessId, branchId, supplierId);
    }

    @Transactional
    public PaymentResponse receiveCustomerDue(CustomerPaymentCommand command) {
        validateCustomerPayment(command);
        BusinessAccessResponse business = requireBusiness(command.ownerId());
        BranchAccessResponse branch = requireBranch(business.businessId(), command.branchId());
        requireCustomer(business.businessId(), command.customerId(), true);
        Payment repeated = paymentRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                business.businessId(), command.branchId(), command.idempotencyKey()).orElse(null);
        if (repeated != null) return response(repeated);
        PaymentMethodResponse method = requirePaymentMethod(
                business.businessId(), command.branchId(), command.paymentMethodId(), command.transactionReference());
        validatePaymentContext(method, command.cashLocationId(), command.registerId(), command.cashierShiftId());
        BigDecimal amount = money(command.amount());
        Instant paymentAt = Objects.requireNonNull(command.paymentDateTime(), "paymentDateTime is required");
        List<CustomerLockedAllocation> locked = command.automaticOldestDueFirst()
                ? allocateCustomerOldestFirst(business.businessId(), command.branchId(), command.customerId(), amount)
                : lockCustomerAllocations(business.businessId(), command.branchId(), command.customerId(),
                command.allocations(), amount);
        BigDecimal allocatedAmount = locked.stream().map(CustomerLockedAllocation::amount)
                .reduce(zero(), BigDecimal::add).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal excessAmount = amount.subtract(allocatedAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        CustomerPaymentSettlementType settlementType = resolveSettlementType(
                command.excessSettlementType(), excessAmount);
        BigDecimal creditAmount = settlementType == CustomerPaymentSettlementType.CUSTOMER_CREDIT
                ? excessAmount : zero();
        BigDecimal changeAmount = settlementType == CustomerPaymentSettlementType.CHANGE
                ? excessAmount : zero();
        Instant now = now();
        Payment payment = Payment.confirmedCustomerPayment(
                business.businessId(), command.branchId(), command.customerId(), PaymentDirection.INFLOW,
                amount, branch.currency(), method.id(), method.name(), method.code(), method.cash(),
                command.transactionReference(), command.accountReference(), command.cashLocationId(),
                command.registerId(), command.cashierShiftId(), PaymentSourceModule.PAYMENT,
                PaymentFinancialPurpose.CUSTOMER_DUE_COLLECTION, locked.getFirst().saleId(), actor(command.ownerId()),
                command.idempotencyKey(), command.attachmentReference(), command.notes(), null, null, paymentAt, now);
        payment.recordCustomerSettlement(allocatedAmount, settlementType, creditAmount, changeAmount);
        payment = paymentRepository.saveAndFlush(payment);
        List<PaymentAllocation> saved = new ArrayList<>();
        for (CustomerLockedAllocation value : locked) {
            SalePaymentAllocationResult result = salePaymentAccessService.applyCustomerPayment(
                    business.businessId(), command.branchId(), command.customerId(), value.saleId(),
                    value.amount(), payment.getId(), actor(command.ownerId()));
            saved.add(allocationRepository.save(PaymentAllocation.create(
                    payment.getId(), PaymentInvoiceType.SALE, result.saleId(), PaymentAllocationEffect.APPLY,
                    result.amount(), result.dueBefore(), result.dueAfter(), now)));
        }
        payment = postCustomerCashEffect(payment, method, CashMovementType.CUSTOMER_DUE_COLLECTION,
                CashMovementDirection.INFLOW, CashSourceModule.PAYMENT, actor(command.ownerId()), command.notes());
        auditRepository.save(PaymentAuditEvent.record(payment.getBusinessId(), payment.getBranchId(), payment.getId(),
                PaymentAuditAction.PAYMENT_CONFIRMED, actor(command.ownerId()),
                "Customer due Payment confirmed with " + saved.size() + " allocation(s)", now));
        auditRepository.flush();
        return response(payment, saved);
    }

    @Override
    @Transactional
    public PaymentResponse postSalePayment(SalePaymentCommand command) {
        validateSalePayment(command);
        BusinessAccessResponse business = requireBusiness(command.actorId());
        if (!business.businessId().equals(command.businessId())) throw new PaymentAccessDeniedException();
        BranchAccessResponse branch = requireBranch(command.businessId(), command.branchId());
        requireCustomer(command.businessId(), command.customerId(), true);

        Payment repeated = paymentRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                command.businessId(), command.branchId(), command.idempotencyKey()).orElse(null);
        if (repeated != null) return response(repeated);

        PaymentMethodResponse method = requirePaymentMethod(
                command.businessId(), command.branchId(), command.paymentMethodId(), command.transactionReference());
        validatePaymentContext(method, command.cashLocationId(), command.registerId(), command.cashierShiftId());
        SalePaymentInvoiceResponse invoice = salePaymentAccessService.lockEligibleCustomerInvoice(
                command.businessId(), command.branchId(), command.customerId(), command.saleId());
        BigDecimal amount = money(command.amount());
        if (amount.compareTo(invoice.outstandingDue()) > 0) {
            throw new PaymentValidationException("Sale Payment cannot exceed invoice outstanding due");
        }

        Instant now = now();
        Payment payment = Payment.confirmedCustomerPayment(
                command.businessId(), command.branchId(), command.customerId(), PaymentDirection.INFLOW,
                amount, branch.currency(), method.id(), method.name(), method.code(), method.cash(),
                command.transactionReference(), command.accountReference(), command.cashLocationId(),
                command.registerId(), command.cashierShiftId(), PaymentSourceModule.SELL,
                PaymentFinancialPurpose.CUSTOMER_DUE_COLLECTION, command.saleId(), actor(command.actorId()),
                command.idempotencyKey(), null, command.notes(), null, null, now);
        payment = paymentRepository.saveAndFlush(payment);
        SalePaymentAllocationResult result = salePaymentAccessService.applyCustomerPayment(
                command.businessId(), command.branchId(), command.customerId(), command.saleId(),
                amount, payment.getId(), actor(command.actorId()));
        PaymentAllocation allocation = allocationRepository.save(PaymentAllocation.create(
                payment.getId(), PaymentInvoiceType.SALE, result.saleId(), PaymentAllocationEffect.APPLY,
                result.amount(), result.dueBefore(), result.dueAfter(), now));
        payment = postCustomerCashEffect(payment, method, CashMovementType.CUSTOMER_DUE_COLLECTION,
                CashMovementDirection.INFLOW, CashSourceModule.SELL, actor(command.actorId()), command.notes());
        auditRepository.save(PaymentAuditEvent.record(payment.getBusinessId(), payment.getBranchId(), payment.getId(),
                PaymentAuditAction.PAYMENT_CONFIRMED, actor(command.actorId()), "Customer Sale Payment confirmed", now));
        auditRepository.flush();
        return response(payment, List.of(allocation));
    }

    @Override
    @Transactional
    public PaymentResponse postCustomerRefund(CustomerRefundCommand command) {
        if (command == null) throw new PaymentValidationException("Customer Refund is required");
        requirePositiveId(command.businessId(), "businessId");
        requirePositiveId(command.branchId(), "branchId");
        requirePositiveId(command.actorId(), "actorId");
        requirePositiveId(command.customerId(), "customerId");
        requirePositiveId(command.saleId(), "saleId");
        requirePositiveId(command.saleReturnId(), "saleReturnId");
        requireText(command.idempotencyKey(), 100, "Idempotency key");
        BusinessAccessResponse business = requireBusiness(command.actorId());
        if (!business.businessId().equals(command.businessId())) throw new PaymentAccessDeniedException();
        BranchAccessResponse branch = requireBranch(command.businessId(), command.branchId());
        requireCustomer(command.businessId(), command.customerId(), false);
        Payment repeated = paymentRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                command.businessId(), command.branchId(), command.idempotencyKey()).orElse(null);
        if (repeated != null) return response(repeated);
        PaymentMethodResponse method = requirePaymentMethod(
                command.businessId(), command.branchId(), command.paymentMethodId(), command.transactionReference());
        validatePaymentContext(method, command.cashLocationId(), command.registerId(), command.cashierShiftId());
        BigDecimal amount = money(command.amount());
        Instant now = now();
        Payment payment = Payment.confirmedCustomerPayment(
                command.businessId(), command.branchId(), command.customerId(), PaymentDirection.OUTFLOW,
                amount, branch.currency(), method.id(), method.name(), method.code(), method.cash(),
                command.transactionReference(), command.accountReference(), command.cashLocationId(),
                command.registerId(), command.cashierShiftId(), PaymentSourceModule.SELL,
                PaymentFinancialPurpose.CUSTOMER_REFUND, command.saleReturnId(), actor(command.actorId()),
                command.idempotencyKey(), null, command.notes(), null, null, now);
        payment = paymentRepository.saveAndFlush(payment);
        payment = postCustomerCashEffect(payment, method, CashMovementType.CUSTOMER_REFUND,
                CashMovementDirection.OUTFLOW, CashSourceModule.SELL, actor(command.actorId()), command.notes());
        auditRepository.save(PaymentAuditEvent.record(payment.getBusinessId(), payment.getBranchId(), payment.getId(),
                PaymentAuditAction.PAYMENT_CONFIRMED, actor(command.actorId()), "Customer Refund confirmed", now));
        auditRepository.flush();
        return response(payment, List.of());
    }

    @Transactional
    public PaymentResponse paySupplierDue(SupplierPaymentCommand command) {
        validateSupplierPaymentCommand(command);
        BusinessAccessResponse business = requireBusiness(command.ownerId());
        BranchAccessResponse branch = requireBranch(business.businessId(), command.branchId());
        requireSupplier(business.businessId(), command.supplierId());

        Payment repeated = paymentRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                business.businessId(), command.branchId(), command.idempotencyKey()).orElse(null);
        if (repeated != null) {
            return response(repeated);
        }

        PaymentMethodResponse method = requirePaymentMethod(
                business.businessId(), command.branchId(), command.paymentMethodId(),
                command.transactionReference());
        validatePaymentContext(method, command.cashLocationId(), command.registerId(),
                command.cashierShiftId());

        BigDecimal amount = money(command.amount());
        List<LockedAllocation> lockedAllocations = command.automaticOldestDueFirst()
                ? allocateOldestDueFirst(business.businessId(), command.branchId(), command.supplierId(), amount)
                : lockManualAllocations(business.businessId(), command.branchId(), command.supplierId(),
                command.allocations(), amount);

        return postSupplierPayment(
                business.businessId(), branch, command.supplierId(), actor(command.ownerId()), amount, method,
                command.transactionReference(), command.accountReference(), command.cashLocationId(),
                command.registerId(), command.cashierShiftId(), command.idempotencyKey(),
                command.attachmentReference(), command.notes(), PaymentSourceModule.PAYMENT,
                CashSourceModule.PAYMENT, lockedAllocations);
    }

    @Override
    @Transactional
    public PaymentResponse postInitialSupplierPayment(InitialSupplierPaymentCommand command) {
        if (command == null || command.amount() == null || command.amount().signum() <= 0) {
            return null;
        }
        requirePositiveId(command.businessId(), "businessId");
        requirePositiveId(command.branchId(), "branchId");
        requirePositiveId(command.ownerId(), "ownerId");
        requirePositiveId(command.supplierId(), "supplierId");
        requirePositiveId(command.purchaseId(), "purchaseId");

        BusinessAccessResponse business = requireBusiness(command.ownerId());
        if (!business.businessId().equals(command.businessId())) {
            throw new PaymentAccessDeniedException();
        }
        BranchAccessResponse branch = requireBranch(command.businessId(), command.branchId());
        requireSupplier(command.businessId(), command.supplierId());

        String idempotencyKey = limitedKey("PURCHASE:" + command.purchaseId() + ":INITIAL");
        Payment repeated = paymentRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                command.businessId(), command.branchId(), idempotencyKey).orElse(null);
        if (repeated != null) {
            return response(repeated);
        }

        PaymentMethodResponse method = requirePaymentMethod(
                command.businessId(), command.branchId(), command.paymentMethodId(),
                command.transactionReference());
        validatePaymentContext(method, command.cashLocationId(), command.registerId(), command.cashierShiftId());

        BigDecimal amount = money(command.amount());
        PurchasePaymentInvoiceResponse invoice = purchasePaymentAccessService.lockEligibleSupplierInvoice(
                command.businessId(), command.branchId(), command.supplierId(), command.purchaseId());
        if (amount.compareTo(invoice.outstandingDue()) > 0) {
            throw new PaymentValidationException("Initial supplier payment cannot exceed Purchase due");
        }

        return postSupplierPayment(
                command.businessId(), branch, command.supplierId(), actor(command.ownerId()), amount, method,
                command.transactionReference(), null, command.cashLocationId(), command.registerId(),
                command.cashierShiftId(), idempotencyKey, null, command.notes(), PaymentSourceModule.PURCHASE,
                CashSourceModule.PURCHASE, List.of(new LockedAllocation(invoice.purchaseId(), amount)));
    }

    @Transactional
    public PaymentResponse reversePayment(Long ownerId, Long branchId, Long paymentId,
                                          String reason, String idempotencyKey) {
        requirePositiveId(ownerId, "ownerId");
        requirePositiveId(branchId, "branchId");
        requirePositiveId(paymentId, "paymentId");
        requireText(reason, 500, "Reversal reason");
        requireText(idempotencyKey, 100, "Idempotency key");

        BusinessAccessResponse business = requireBusiness(ownerId);
        BranchAccessResponse branch = requireBranch(business.businessId(), branchId);
        Long actualActorId = actor(ownerId);

        Payment repeated = paymentRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                business.businessId(), branchId, idempotencyKey.trim()).orElse(null);
        if (repeated != null) {
            return response(repeated);
        }

        Payment original = paymentRepository.findForUpdate(business.businessId(), branchId, paymentId)
                .orElseThrow(PaymentNotFoundException::new);
        if (!original.isOriginalConfirmedPayment()) {
            throw new PaymentStateException("Only an original Confirmed Payment can be reversed once");
        }
        List<PaymentAllocation> originalAllocations = allocationRepository.findByPaymentIdOrderByIdAsc(original.getId());
        if (originalAllocations.isEmpty() && original.getFinancialPurpose() != PaymentFinancialPurpose.CUSTOMER_REFUND) {
            throw new PaymentStateException("Confirmed Payment has no invoice allocation to reverse");
        }

        Instant now = now();
        Payment reversal = original.getPartyType() == com.spark.falcon.payment.entity.PaymentPartyType.SUPPLIER
                ? Payment.confirmedSupplierPayment(
                original.getBusinessId(), original.getBranchId(), original.getSupplierId(),
                PaymentDirection.INFLOW, original.getAmount(), original.getCurrency(), original.getPaymentMethodId(),
                original.getPaymentMethodNameSnapshot(), original.getPaymentMethodCodeSnapshot(),
                original.isCashPayment(), original.getTransactionReference(), original.getAccountReference(),
                original.getCashLocationId(), original.getRegisterId(), original.getCashierShiftId(),
                PaymentSourceModule.PAYMENT, PaymentFinancialPurpose.SUPPLIER_DUE_SETTLEMENT_REVERSAL,
                actualActorId, idempotencyKey, original.getAttachmentReference(), original.getNotes(),
                original.getId(), reason, now)
                : Payment.confirmedCustomerPayment(
                original.getBusinessId(), original.getBranchId(), original.getCustomerId(),
                original.getDirection() == PaymentDirection.INFLOW ? PaymentDirection.OUTFLOW : PaymentDirection.INFLOW,
                original.getAmount(), original.getCurrency(), original.getPaymentMethodId(),
                original.getPaymentMethodNameSnapshot(), original.getPaymentMethodCodeSnapshot(),
                original.isCashPayment(), original.getTransactionReference(), original.getAccountReference(),
                original.getCashLocationId(), original.getRegisterId(), original.getCashierShiftId(),
                PaymentSourceModule.PAYMENT,
                original.getFinancialPurpose() == PaymentFinancialPurpose.CUSTOMER_REFUND
                        ? PaymentFinancialPurpose.CUSTOMER_REFUND_REVERSAL
                        : PaymentFinancialPurpose.CUSTOMER_DUE_COLLECTION_REVERSAL,
                original.getSourceTransactionId(), actualActorId, idempotencyKey, original.getAttachmentReference(),
                original.getNotes(), original.getId(), reason, now);
        reversal = paymentRepository.saveAndFlush(reversal);

        List<PaymentAllocation> reversalAllocations = new ArrayList<>();
        for (PaymentAllocation allocation : originalAllocations) {
            if (allocation.getEffect() != PaymentAllocationEffect.APPLY) {
                throw new PaymentStateException("Unsupported Payment allocation cannot be reversed");
            }
            if (allocation.getInvoiceType() == PaymentInvoiceType.PURCHASE) {
                PurchasePaymentAllocationResult result = purchasePaymentAccessService.reverseSupplierPayment(
                        original.getBusinessId(), original.getBranchId(), original.getSupplierId(),
                        allocation.getInvoiceId(), allocation.getAmount(), reversal.getId(), actualActorId);
                reversalAllocations.add(allocationRepository.save(PaymentAllocation.create(
                        reversal.getId(), PaymentInvoiceType.PURCHASE, result.purchaseId(),
                        PaymentAllocationEffect.REVERSE, result.amount(), result.dueBefore(), result.dueAfter(), now)));
            } else {
                SalePaymentAllocationResult result = salePaymentAccessService.reverseCustomerPayment(
                        original.getBusinessId(), original.getBranchId(), original.getCustomerId(),
                        allocation.getInvoiceId(), allocation.getAmount(), reversal.getId(), actualActorId);
                reversalAllocations.add(allocationRepository.save(PaymentAllocation.create(
                        reversal.getId(), PaymentInvoiceType.SALE, result.saleId(),
                        PaymentAllocationEffect.REVERSE, result.amount(), result.dueBefore(), result.dueAfter(), now)));
            }
        }

        if (original.isCashPayment()) {
            if (original.getCashMovementId() == null) {
                throw new PaymentStateException("Cash Payment is missing its Cash Movement reference");
            }
            CashMovementResponse cashReversal = cashManagementPostingService.reverse(
                    original.getBusinessId(), original.getBranchId(), original.getCashMovementId(),
                    CashSourceModule.PAYMENT, String.valueOf(reversal.getId()), original.getTransactionReference(),
                    actualActorId, cashReversalPostingKey(idempotencyKey), reason);
            reversal.attachCashMovement(cashReversal.getId());
            reversal = paymentRepository.saveAndFlush(reversal);
            auditRepository.save(PaymentAuditEvent.record(
                    reversal.getBusinessId(), reversal.getBranchId(), reversal.getId(),
                    PaymentAuditAction.PAYMENT_CASH_MOVEMENT_LINKED, actualActorId,
                    "Linked opposite Cash Movement " + cashReversal.getId(), now));
        }

        original.markReversed(reversal.getId(), now);
        paymentRepository.saveAndFlush(original);

        auditRepository.save(PaymentAuditEvent.record(
                original.getBusinessId(), original.getBranchId(), original.getId(),
                PaymentAuditAction.PAYMENT_REVERSED, actualActorId,
                "Payment reversed by Payment " + reversal.getId() + ". Reason: " + reason.trim(), now));
        auditRepository.save(PaymentAuditEvent.record(
                reversal.getBusinessId(), reversal.getBranchId(), reversal.getId(),
                PaymentAuditAction.PAYMENT_REVERSAL_CONFIRMED, actualActorId,
                "Reversal of Payment " + original.getId(), now));
        auditRepository.flush();

        return response(reversal, reversalAllocations);
    }

    public PaymentResponse findById(Long ownerId, Long branchId, Long paymentId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        Payment payment = paymentRepository.findByIdAndBusinessIdAndBranchId(paymentId, business.businessId(), branchId)
                .orElseThrow(PaymentNotFoundException::new);
        return response(payment);
    }

    @Transactional(readOnly = true)
    public List<SalePaymentInvoiceResponse> findEligibleCustomerInvoices(
            Long ownerId, Long branchId, Long customerId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        requireCustomer(business.businessId(), customerId, true);
        return salePaymentAccessService.findEligibleCustomerInvoicesOldestFirst(
                business.businessId(), branchId, customerId);
    }

    @Transactional(readOnly = true)
    public List<PurchasePaymentInvoiceResponse> findEligibleSupplierInvoices(
            Long ownerId, Long branchId, Long supplierId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        requireSupplier(business.businessId(), supplierId);
        return purchasePaymentAccessService.findEligibleSupplierInvoicesOldestFirst(
                business.businessId(), branchId, supplierId);
    }

    public List<PaymentResponse> findSupplierPayments(Long ownerId, Long branchId, Long supplierId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        requireSupplier(business.businessId(), supplierId);
        return paymentRepository.findByBusinessIdAndBranchIdAndSupplierIdOrderByCreatedAtDesc(
                        business.businessId(), branchId, supplierId)
                .stream()
                .map(this::response)
                .toList();
    }

    public List<PaymentResponse> findCustomerPayments(Long ownerId, Long branchId, Long customerId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        requireCustomer(business.businessId(), customerId, false);
        return paymentRepository.findByBusinessIdAndBranchIdAndCustomerIdOrderByCreatedAtDesc(
                        business.businessId(), branchId, customerId)
                .stream().map(this::response).toList();
    }

    @Override
    public String paymentMethodSummaryForSale(Long businessId, Long branchId, Long saleId) {
        return paymentRepository.findByBusinessIdAndBranchIdAndSourceTransactionIdAndStatusOrderByCreatedAtAsc(
                        businessId, branchId, saleId, PaymentStatus.CONFIRMED)
                .stream()
                .filter(value -> value.getFinancialPurpose() == PaymentFinancialPurpose.CUSTOMER_DUE_COLLECTION)
                .map(Payment::getPaymentMethodNameSnapshot)
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
    }

    @Override
    public Map<Long, String> paymentMethodSummaryForSales(
            Long businessId, Long branchId, Collection<Long> saleIds) {
        if (saleIds == null || saleIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = saleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, java.util.LinkedHashSet<String>> methods = new LinkedHashMap<>();
        paymentRepository.findByBusinessIdAndBranchIdAndSourceTransactionIdInAndStatusOrderBySourceTransactionIdAscCreatedAtAsc(
                        businessId, branchId, ids, PaymentStatus.CONFIRMED)
                .stream()
                .filter(value -> value.getFinancialPurpose() == PaymentFinancialPurpose.CUSTOMER_DUE_COLLECTION)
                .forEach(value -> methods.computeIfAbsent(
                                value.getSourceTransactionId(), ignored -> new java.util.LinkedHashSet<>())
                        .add(value.getPaymentMethodNameSnapshot()));
        return methods.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> String.join(", ", entry.getValue())));
    }

    @Override
    public List<Long> findSaleIdsByPaymentMethodKeyword(Long businessId, Long branchId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return paymentRepository.findSaleIdsByPaymentMethodKeyword(businessId, branchId, keyword.trim());
    }

    @Override
    public boolean hasConfirmedCustomerPayment(Long businessId, Long branchId, Long saleId) {
        return paymentRepository.findByBusinessIdAndBranchIdAndSourceTransactionIdAndStatusOrderByCreatedAtAsc(
                        businessId, branchId, saleId, PaymentStatus.CONFIRMED)
                .stream().anyMatch(value -> value.getFinancialPurpose() == PaymentFinancialPurpose.CUSTOMER_DUE_COLLECTION);
    }

    @Override
    public List<SaleReceiptPaymentResponse> findReceiptPaymentsForSale(Long businessId, Long branchId, Long saleId) {
        return paymentRepository.findByBusinessIdAndBranchIdAndSourceTransactionIdAndStatusOrderByCreatedAtAsc(
                        businessId, branchId, saleId, PaymentStatus.CONFIRMED)
                .stream()
                .filter(value -> value.getFinancialPurpose() == PaymentFinancialPurpose.CUSTOMER_DUE_COLLECTION)
                .map(value -> new SaleReceiptPaymentResponse(
                        value.getId(), value.getPaymentMethodNameSnapshot(), value.getAmount(),
                        value.getTransactionReference(), value.getConfirmedAt()))
                .toList();
    }

    private Payment postCustomerCashEffect(Payment payment,
                                           PaymentMethodResponse method,
                                           CashMovementType movementType,
                                           CashMovementDirection direction,
                                           CashSourceModule sourceModule,
                                           Long actorId,
                                           String notes) {
        if (!method.cash()) return payment;
        CashMovementRequest request = new CashMovementRequest();
        request.setCashLocationId(payment.getCashLocationId());
        request.setRegisterId(payment.getRegisterId());
        request.setCashierShiftId(payment.getCashierShiftId());
        request.setSourceModule(sourceModule);
        request.setSourceTransactionId(String.valueOf(payment.getId()));
        request.setSourceReference(payment.getTransactionReference());
        request.setMovementType(movementType);
        request.setDirection(direction);
        request.setAmount(payment.getAmount().subtract(payment.effectiveChangeAmount()));
        request.setPostedByUserId(actorId);
        request.setPostingKey(cashPostingKey(payment.getIdempotencyKey()));
        request.setNote(notes);
        CashMovementResponse movement = cashManagementPostingService.post(
                payment.getBusinessId(), payment.getBranchId(), request);
        payment.attachCashMovement(movement.getId());
        return paymentRepository.saveAndFlush(payment);
    }

    private void validateSalePayment(SalePaymentCommand command) {
        if (command == null) throw new PaymentValidationException("Sale Payment is required");
        requirePositiveId(command.businessId(), "businessId");
        requirePositiveId(command.branchId(), "branchId");
        requirePositiveId(command.actorId(), "actorId");
        requirePositiveId(command.customerId(), "customerId");
        requirePositiveId(command.saleId(), "saleId");
        requirePositiveId(command.paymentMethodId(), "paymentMethodId");
        money(command.amount());
        requireText(command.idempotencyKey(), 100, "Idempotency key");
    }

    private void validateCustomerPayment(CustomerPaymentCommand command) {
        if (command == null) throw new PaymentValidationException("Customer Payment is required");
        requirePositiveId(command.ownerId(), "ownerId");
        requirePositiveId(command.branchId(), "branchId");
        requirePositiveId(command.customerId(), "customerId");
        requirePositiveId(command.paymentMethodId(), "paymentMethodId");
        money(command.amount());
        requireText(command.idempotencyKey(), 100, "Idempotency key");
        optionalText(command.notes(), 1000, "notes");
        optionalText(command.attachmentReference(), 500, "attachmentReference");
        if (command.paymentDateTime() == null) {
            throw new PaymentValidationException("Payment Date & Time is required");
        }
        if (!command.automaticOldestDueFirst()
                && (command.allocations() == null || command.allocations().isEmpty())) {
            throw new PaymentValidationException("Manual Customer Payment allocation is required");
        }
    }

    private List<CustomerLockedAllocation> allocateCustomerOldestFirst(Long businessId,
                                                                        Long branchId,
                                                                        Long customerId,
                                                                        BigDecimal amount) {
        BigDecimal remaining = amount;
        List<CustomerLockedAllocation> result = new ArrayList<>();
        for (SalePaymentInvoiceResponse invoice : salePaymentAccessService.findEligibleCustomerInvoicesOldestFirst(
                businessId, branchId, customerId)) {
            if (remaining.signum() == 0) break;
            SalePaymentInvoiceResponse locked = salePaymentAccessService.lockEligibleCustomerInvoice(
                    businessId, branchId, customerId, invoice.saleId());
            BigDecimal allocated = remaining.min(locked.outstandingDue()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (allocated.signum() > 0) result.add(new CustomerLockedAllocation(locked.saleId(), allocated));
            remaining = remaining.subtract(allocated);
        }
        if (result.isEmpty()) {
            throw new PaymentValidationException("Customer Payment requires at least one eligible invoice allocation");
        }
        return List.copyOf(result);
    }

    private List<CustomerLockedAllocation> lockCustomerAllocations(Long businessId,
                                                                    Long branchId,
                                                                    Long customerId,
                                                                    List<CustomerPaymentAllocationCommand> commands,
                                                                    BigDecimal amount) {
        BigDecimal total = zero();
        Set<Long> saleIds = new HashSet<>();
        List<CustomerLockedAllocation> result = new ArrayList<>();
        for (CustomerPaymentAllocationCommand value : commands.stream()
                .sorted(Comparator.comparing(CustomerPaymentAllocationCommand::saleId)).toList()) {
            if (value == null) throw new PaymentValidationException("Customer Payment allocation is invalid");
            requirePositiveId(value.saleId(), "saleId");
            if (!saleIds.add(value.saleId())) {
                throw new PaymentValidationException("The same Sale invoice cannot be allocated twice");
            }
            BigDecimal allocated = money(value.amount());
            SalePaymentInvoiceResponse invoice = salePaymentAccessService.lockEligibleCustomerInvoice(
                    businessId, branchId, customerId, value.saleId());
            if (allocated.compareTo(invoice.outstandingDue()) > 0) {
                throw new PaymentValidationException("Customer Payment allocation exceeds invoice outstanding due");
            }
            result.add(new CustomerLockedAllocation(value.saleId(), allocated));
            total = total.add(allocated);
        }
        if (total.compareTo(amount) > 0) {
            throw new PaymentValidationException("Customer Payment allocations cannot exceed Amount Received");
        }
        return List.copyOf(result);
    }

    private CustomerPaymentSettlementType resolveSettlementType(
            CustomerPaymentSettlementType requested, BigDecimal excessAmount) {
        if (excessAmount.signum() == 0) {
            if (requested == null || requested == CustomerPaymentSettlementType.ALLOCATED_ONLY) {
                return CustomerPaymentSettlementType.ALLOCATED_ONLY;
            }
            throw new PaymentValidationException("Customer Credit or Change requires an unallocated excess amount");
        }
        if (requested != CustomerPaymentSettlementType.CUSTOMER_CREDIT
                && requested != CustomerPaymentSettlementType.CHANGE) {
            throw new PaymentValidationException(
                    "Choose Customer Credit or Change for the unallocated excess amount");
        }
        return requested;
    }

    private void requireCustomer(Long businessId, Long customerId, boolean requireActive) {
        var customer = requireActive
                ? customerAccessService.findActiveByBusinessIdAndCustomerId(businessId, customerId)
                : customerAccessService.findByBusinessIdAndCustomerId(businessId, customerId);
        if (customer.isEmpty()) {
            throw new PaymentValidationException(requireActive
                    ? "Customer is inactive or unavailable"
                    : "Customer is unavailable");
        }
    }

    private PaymentResponse postSupplierPayment(Long businessId,
                                                BranchAccessResponse branch,
                                                Long supplierId,
                                                Long actorId,
                                                BigDecimal amount,
                                                PaymentMethodResponse method,
                                                String transactionReference,
                                                String accountReference,
                                                Long cashLocationId,
                                                Long registerId,
                                                Long cashierShiftId,
                                                String idempotencyKey,
                                                String attachmentReference,
                                                String notes,
                                                PaymentSourceModule paymentSourceModule,
                                                CashSourceModule cashSourceModule,
                                                List<LockedAllocation> lockedAllocations) {
        Instant now = now();
        Payment payment = Payment.confirmedSupplierPayment(
                businessId, branch.branchId(), supplierId, PaymentDirection.OUTFLOW, amount, branch.currency(),
                method.id(), method.name(), method.code(), method.cash(), transactionReference, accountReference,
                cashLocationId, registerId, cashierShiftId, paymentSourceModule,
                PaymentFinancialPurpose.SUPPLIER_DUE_SETTLEMENT, actorId, idempotencyKey,
                attachmentReference, notes, null, null, now);
        payment = paymentRepository.saveAndFlush(payment);

        List<PaymentAllocation> allocations = new ArrayList<>();
        for (LockedAllocation locked : lockedAllocations) {
            PurchasePaymentAllocationResult result = purchasePaymentAccessService.applySupplierPayment(
                    businessId, branch.branchId(), supplierId, locked.purchaseId(), locked.amount(),
                    payment.getId(), actorId);
            allocations.add(allocationRepository.save(PaymentAllocation.create(
                    payment.getId(), PaymentInvoiceType.PURCHASE, result.purchaseId(),
                    PaymentAllocationEffect.APPLY, result.amount(), result.dueBefore(), result.dueAfter(), now)));
        }

        if (method.cash()) {
            CashMovementRequest cashRequest = new CashMovementRequest();
            cashRequest.setCashLocationId(cashLocationId);
            cashRequest.setRegisterId(registerId);
            cashRequest.setCashierShiftId(cashierShiftId);
            cashRequest.setSourceModule(cashSourceModule);
            cashRequest.setSourceTransactionId(String.valueOf(payment.getId()));
            cashRequest.setSourceReference(transactionReference);
            cashRequest.setMovementType(CashMovementType.SUPPLIER_DUE_PAYMENT);
            cashRequest.setDirection(CashMovementDirection.OUTFLOW);
            cashRequest.setAmount(amount);
            cashRequest.setPostedByUserId(actorId);
            cashRequest.setPostingKey(cashPostingKey(idempotencyKey));
            cashRequest.setNote(notes);

            CashMovementResponse cashMovement = cashManagementPostingService.post(
                    businessId, branch.branchId(), cashRequest);
            payment.attachCashMovement(cashMovement.getId());
            payment = paymentRepository.saveAndFlush(payment);
            auditRepository.save(PaymentAuditEvent.record(
                    businessId, branch.branchId(), payment.getId(),
                    PaymentAuditAction.PAYMENT_CASH_MOVEMENT_LINKED, actorId,
                    "Linked Cash Movement " + cashMovement.getId(), now));
        }

        auditRepository.save(PaymentAuditEvent.record(
                businessId, branch.branchId(), payment.getId(), PaymentAuditAction.PAYMENT_CONFIRMED,
                actorId, "Supplier due Payment confirmed with " + allocations.size() + " invoice allocation(s)", now));
        auditRepository.flush();
        return response(payment, allocations);
    }

    private List<LockedAllocation> lockManualAllocations(Long businessId,
                                                         Long branchId,
                                                         Long supplierId,
                                                         List<SupplierPaymentAllocationCommand> commands,
                                                         BigDecimal paymentAmount) {
        List<SupplierPaymentAllocationCommand> sorted = commands.stream()
                .sorted(Comparator.comparing(SupplierPaymentAllocationCommand::purchaseId))
                .toList();
        List<LockedAllocation> locked = new ArrayList<>();
        BigDecimal total = zero();
        for (SupplierPaymentAllocationCommand command : sorted) {
            PurchasePaymentInvoiceResponse invoice = purchasePaymentAccessService.lockEligibleSupplierInvoice(
                    businessId, branchId, supplierId, command.purchaseId());
            BigDecimal allocationAmount = money(command.amount());
            if (allocationAmount.compareTo(invoice.outstandingDue()) > 0) {
                throw new PaymentValidationException("Supplier Payment allocation exceeds Purchase outstanding due");
            }
            total = total.add(allocationAmount);
            locked.add(new LockedAllocation(invoice.purchaseId(), allocationAmount));
        }
        if (total.compareTo(paymentAmount) != 0) {
            throw new PaymentValidationException("Supplier Payment allocations must equal the Payment amount");
        }
        return locked;
    }

    private List<LockedAllocation> allocateOldestDueFirst(Long businessId,
                                                          Long branchId,
                                                          Long supplierId,
                                                          BigDecimal paymentAmount) {
        List<PurchasePaymentInvoiceResponse> candidates = purchasePaymentAccessService
                .findEligibleSupplierInvoicesOldestFirst(businessId, branchId, supplierId);
        BigDecimal remaining = paymentAmount;
        List<LockedAllocation> allocations = new ArrayList<>();

        for (PurchasePaymentInvoiceResponse candidate : candidates) {
            if (remaining.signum() == 0) {
                break;
            }
            PurchasePaymentInvoiceResponse locked = purchasePaymentAccessService.lockEligibleSupplierInvoice(
                    businessId, branchId, supplierId, candidate.purchaseId());
            BigDecimal allocationAmount = remaining.min(locked.outstandingDue());
            allocations.add(new LockedAllocation(locked.purchaseId(), allocationAmount));
            remaining = remaining.subtract(allocationAmount);
        }

        if (remaining.signum() > 0) {
            throw new PaymentValidationException("Supplier Payment amount exceeds eligible outstanding due");
        }
        return allocations;
    }

    private PaymentMethodResponse requirePaymentMethod(Long businessId,
                                                       Long branchId,
                                                       Long paymentMethodId,
                                                       String transactionReference) {
        requirePositiveId(paymentMethodId, "paymentMethodId");
        PaymentMethodResponse method = paymentMethodAccessService.findActiveForBranch(
                        businessId, branchId, paymentMethodId)
                .orElseThrow(() -> new PaymentValidationException(
                        "Payment Method is inactive or unavailable for this Branch"));
        if (method.transactionReferenceRequired()
                && (transactionReference == null || transactionReference.isBlank())) {
            throw new PaymentValidationException("Transaction reference is required for this Payment Method");
        }
        return method;
    }

    private void validatePaymentContext(PaymentMethodResponse method,
                                        Long cashLocationId,
                                        Long registerId,
                                        Long cashierShiftId) {
        if (!method.cash()) {
            return;
        }
        requirePositiveId(cashLocationId, "cashLocationId");
        requirePositiveId(registerId, "registerId");
        requirePositiveId(cashierShiftId, "cashierShiftId");
    }

    private void validateSupplierPaymentCommand(SupplierPaymentCommand command) {
        if (command == null) {
            throw new PaymentValidationException("Supplier Payment command is required");
        }
        requirePositiveId(command.ownerId(), "ownerId");
        requirePositiveId(command.branchId(), "branchId");
        requirePositiveId(command.supplierId(), "supplierId");
        requirePositiveId(command.paymentMethodId(), "paymentMethodId");
        money(command.amount());
        requireText(command.idempotencyKey(), 100, "Idempotency key");
        optionalText(command.transactionReference(), 160, "Transaction reference");
        optionalText(command.accountReference(), 160, "Account reference");
        optionalText(command.notes(), 1000, "Notes");
        optionalText(command.attachmentReference(), 500, "Attachment reference");

        List<SupplierPaymentAllocationCommand> allocations = command.allocations() == null
                ? List.of() : command.allocations();
        if (!command.automaticOldestDueFirst() && allocations.isEmpty()) {
            throw new PaymentValidationException("Manual supplier Payment requires invoice allocations");
        }
        if (command.automaticOldestDueFirst() && !allocations.isEmpty()) {
            throw new PaymentValidationException("Choose either automatic allocation or manual allocation, not both");
        }

        Set<Long> purchaseIds = new HashSet<>();
        for (SupplierPaymentAllocationCommand allocation : allocations) {
            if (allocation == null) {
                throw new PaymentValidationException("Supplier Payment allocation is required");
            }
            requirePositiveId(allocation.purchaseId(), "purchaseId");
            money(allocation.amount());
            if (!purchaseIds.add(allocation.purchaseId())) {
                throw new PaymentValidationException("The same Purchase invoice cannot be allocated twice");
            }
        }
    }

    private BusinessAccessResponse requireBusiness(Long ownerId) {
        return businessAccessService.findByOwnerId(ownerId)
                .orElseThrow(PaymentAccessDeniedException::new);
    }

    private BranchAccessResponse requireBranch(Long businessId, Long branchId) {
        BranchAccessResponse branch = branchAccessService.findActiveByBusinessIdAndBranchId(businessId, branchId)
                .orElseThrow(PaymentAccessDeniedException::new);
        if (currentActorService.isStaff()) {
            Long staffUserId = currentActorService.actorId(null);
            if (staffUserId == null || !userAccessService.hasActiveBranchAccess(businessId, staffUserId, branchId)) {
                throw new PaymentAccessDeniedException();
            }
        }
        return branch;
    }

    private void requireSupplier(Long businessId, Long supplierId) {
        if (supplierService.findActiveByBusinessIdAndId(businessId, supplierId).isEmpty()) {
            throw new PaymentValidationException("Supplier is inactive or unavailable");
        }
    }

    private PaymentResponse response(Payment payment) {
        return response(payment, allocationRepository.findByPaymentIdOrderByIdAsc(payment.getId()));
    }

    private PaymentResponse response(Payment payment, List<PaymentAllocation> allocations) {
        List<PaymentAuditResponse> auditTimeline = auditRepository
                .findByPaymentIdOrderByCreatedAtAscIdAsc(payment.getId())
                .stream()
                .map(event -> new PaymentAuditResponse(
                        event.getId(), event.getAction(), event.getActorId(), event.getDetails(), event.getCreatedAt()))
                .toList();
        return new PaymentResponse(
                payment.getId(), payment.getBusinessId(), payment.getBranchId(), payment.getPartyType(),
                payment.getCustomerId(), payment.getSupplierId(), payment.getDirection(), payment.getAmount(),
                payment.effectiveAllocatedAmount(), payment.getCustomerSettlementType(),
                payment.effectiveCustomerCreditAmount(), payment.effectiveChangeAmount(),
                payment.getCurrency(), payment.getPaymentMethodId(), payment.getPaymentMethodNameSnapshot(),
                payment.getPaymentMethodCodeSnapshot(), payment.isCashPayment(), payment.getTransactionReference(),
                payment.getAccountReference(), payment.getCashLocationId(), payment.getRegisterId(),
                payment.getCashierShiftId(), payment.getCashMovementId(), payment.getStatus(),
                payment.getSourceModule(), payment.getFinancialPurpose(), payment.getSourceTransactionId(),
                payment.getCreatedByActorId(),
                payment.getConfirmedByActorId(), payment.getCreatedAt(), payment.getConfirmedAt(),
                payment.getReversalOfPaymentId(), payment.getReversedByPaymentId(), payment.getReversedAt(),
                payment.getReversalReason(), payment.getAttachmentReference(), payment.getNotes(),
                allocations.stream().map(this::allocationResponse).toList(), auditTimeline);
    }

    private PaymentAllocationResponse allocationResponse(PaymentAllocation allocation) {
        return new PaymentAllocationResponse(
                allocation.getId(), allocation.getInvoiceType(), allocation.getInvoiceId(), allocation.getEffect(),
                allocation.getAmount(), allocation.getDueBefore(), allocation.getDueAfter());
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new PaymentValidationException("Payment amount must be greater than zero");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void requirePositiveId(Long value, String field) {
        if (value == null || value <= 0) {
            throw new PaymentValidationException(field + " must be a positive ID");
        }
    }

    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            throw new PaymentValidationException(field + " is required");
        }
        if (value.trim().length() > maxLength) {
            throw new PaymentValidationException(field + " is too long");
        }
    }

    private void optionalText(String value, int maxLength, String field) {
        if (value != null && value.trim().length() > maxLength) {
            throw new PaymentValidationException(field + " is too long");
        }
    }

    private String cashPostingKey(String idempotencyKey) {
        return limitedKey("PAY:" + idempotencyKey);
    }

    private String cashReversalPostingKey(String idempotencyKey) {
        return limitedKey("PAY-REV:" + idempotencyKey);
    }

    private String limitedKey(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private Long actor(Long ownerId) {
        return currentActorService.actorId(ownerId);
    }

    private Instant now() {
        return Instant.now(clock);
    }

    @Transactional(readOnly = true)
    public Optional<String> findLatestPurchasePaymentMethod(Long purchaseId) {
        return allocationRepository.findByInvoiceTypeAndInvoiceIdOrderByCreatedAtDesc(
                        PaymentInvoiceType.PURCHASE, purchaseId).stream()
                .map(allocation -> paymentRepository.findById(allocation.getPaymentId()).orElse(null))
                .filter(Objects::nonNull)
                .filter(payment -> payment.getStatus() == PaymentStatus.CONFIRMED)
                .map(Payment::getPaymentMethodNameSnapshot)
                .findFirst();
    }

    private record LockedAllocation(Long purchaseId, BigDecimal amount) {
    }

    private record CustomerLockedAllocation(Long saleId, BigDecimal amount) {
    }
}
