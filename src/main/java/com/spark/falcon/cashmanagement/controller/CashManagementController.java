package com.spark.falcon.cashmanagement.controller;

import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.cashmanagement.dto.*;
import com.spark.falcon.cashmanagement.entity.CashLocationStatus;
import com.spark.falcon.cashmanagement.entity.CashierShiftStatus;
import com.spark.falcon.cashmanagement.entity.RegisterStatus;
import com.spark.falcon.cashmanagement.entity.CashMovementType;
import com.spark.falcon.cashmanagement.entity.CashSourceModule;
import com.spark.falcon.cashmanagement.entity.CashMovementStatus;
import com.spark.falcon.cashmanagement.entity.CashVarianceResult;
import com.spark.falcon.cashmanagement.exception.CashManagementAccessDeniedException;
import com.spark.falcon.cashmanagement.exception.CashManagementValidationException;
import com.spark.falcon.cashmanagement.service.CashManagementService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.settings.service.BranchSettingsAccessService;
import com.spark.falcon.user.dto.command.ManagementActor;
import com.spark.falcon.user.service.UserManagementActorService;
import com.spark.falcon.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Controller
@RequestMapping("/owner/cash-management")
@RequiredArgsConstructor
public class CashManagementController {

    private final BranchContextService branchContextService;
    private final BranchAccessService branchAccessService;
    private final CashManagementService cashManagementService;
    private final UserManagementActorService userManagementActorService;
    private final UserService userService;
    private final BranchSettingsAccessService branchSettingsAccessService;

    @GetMapping({"", "/entry"})
    public String cashbook(@RequestParam(required = false) Long cashLocationId,
                           @RequestParam(required = false) Long registerId,
                           @RequestParam(required = false) Long shiftId,
                           @RequestParam(required = false) CashMovementType movementType,
                           @RequestParam(required = false) CashSourceModule sourceModule,
                           @RequestParam(required = false) Long userId,
                           @RequestParam(required = false) CashMovementStatus status,
                           @RequestParam(required = false) LocalDate from,
                           @RequestParam(required = false) LocalDate to,
                           @RequestParam(required = false) String q,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "25") int size,
                           @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal);
        addCashContext(model, setup, principal);
        model.addAttribute("cashbook", cashManagementService.findCashbook(setup.businessId(), setup.branchId()));
        var activeShift = cashManagementService.findShifts(setup.businessId(), setup.branchId()).stream()
                .filter(shift -> shift.getStatus() == CashierShiftStatus.OPEN)
                .findFirst().orElse(null);
        model.addAttribute("activeShift", activeShift);
        model.addAttribute("activeShiftExpectedCash", activeShift == null ? null
                : cashManagementService.expectedCash(setup.businessId(), setup.branchId(), activeShift.getId()));
        int safeSize = java.util.Set.of(10, 25, 50, 100).contains(size) ? size : 25;
        ZoneId branchZone = (ZoneId) model.asMap().get("branchZone");
        var pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "postedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<CashMovementResponse> movementPage;
        if (from != null && to != null && from.isAfter(to)) {
            model.addAttribute("errorMessage", "From date cannot be later than To date.");
            movementPage = Page.empty(pageable);
        } else {
            movementPage = cashManagementService.searchMovements(setup.businessId(), setup.branchId(), cashLocationId,
                    registerId, shiftId, movementType, sourceModule, userId, status,
                    from == null ? null : from.atStartOfDay(branchZone).toInstant(),
                    to == null ? null : to.plusDays(1).atStartOfDay(branchZone).toInstant(), q, pageable);
        }
        model.addAttribute("movements", movementPage.getContent()); model.addAttribute("movementPage", movementPage);
        model.addAttribute("cashLocationId", cashLocationId); model.addAttribute("registerId", registerId); model.addAttribute("shiftId", shiftId);
        model.addAttribute("movementType", movementType); model.addAttribute("sourceModule", sourceModule); model.addAttribute("userId", userId);
        model.addAttribute("movementStatus", status); model.addAttribute("from", from); model.addAttribute("to", to);
        model.addAttribute("query", q == null ? "" : q.trim()); model.addAttribute("selectedSize", safeSize);
        model.addAttribute("activePage", "cashbook");
        return "cash-management/cashbook";
    }

    @PostMapping("/initialize")
    public String initializeCashbook(@RequestParam(required = false) String openingBalance,
                                     @AuthenticationPrincipal OwnerPrincipal principal,
                                     RedirectAttributes redirectAttributes) {
        BigDecimal parsedOpeningBalance;
        try {
            parsedOpeningBalance = openingBalance == null || openingBalance.isBlank()
                    ? null : new BigDecimal(openingBalance.trim());
        } catch (NumberFormatException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Opening balance must be a valid number.");
            return "redirect:/owner/cash-management";
        }
        if (parsedOpeningBalance == null || parsedOpeningBalance.signum() < 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "Opening balance must be zero or greater.");
            return "redirect:/owner/cash-management";
        }
        BusinessSetupResponse setup = setup(principal);
        try {
            cashManagementService.initializeCashbook(setup.businessId(), setup.branchId(), parsedOpeningBalance);
            redirectAttributes.addFlashAttribute("successMessage", "Branch Cashbook initialized successfully.");
        } catch (CashManagementValidationException | CashManagementAccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/cash-management";
    }

    @GetMapping("/shifts/open")
    public String openShiftPage(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal);
        addCashContext(model, setup, principal);
        OpenCashierShiftRequest request;
        if (model.containsAttribute("openShiftRequest")) {
            request = (OpenCashierShiftRequest) model.getAttribute("openShiftRequest");
        } else {
            request = new OpenCashierShiftRequest();
            request.setOpeningFloat(BigDecimal.ZERO);
            request.setShiftCode(referenceCode("SHIFT", (ZoneId) model.asMap().get("branchZone")));
            request.setOpenKey(UUID.randomUUID().toString());
            model.addAttribute("openShiftRequest", request);
        }
        ManagementActor actor = userManagementActorService.forOwner(principal.ownerId());
        model.addAttribute("cashiers", userService.findByBranch(actor, setup.branchId(), "", Pageable.unpaged()).getContent());
        model.addAttribute("activePage", "open-shift");
        return "cash-management/open-cashier-shift";
    }

    @PostMapping("/shifts/open")
    public String openShift(@Valid @ModelAttribute("openShiftRequest") OpenCashierShiftRequest request,
                            BindingResult bindingResult,
                            @AuthenticationPrincipal OwnerPrincipal principal,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("openShiftRequest", request);
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "openShiftRequest", bindingResult);
            redirectAttributes.addFlashAttribute("errorMessage", firstValidationMessage(bindingResult));
            return "redirect:/owner/cash-management/shifts/open";
        }
        BusinessSetupResponse setup = setup(principal);
        try {
            CashierShiftResponse shift = cashManagementService.openShift(setup.businessId(), setup.branchId(), request);
            redirectAttributes.addFlashAttribute("successMessage", "Cashier shift " + shift.getShiftCode() + " opened.");
            return "redirect:/owner/cash-management/shifts/active?shiftId=" + shift.getId();
        } catch (CashManagementValidationException | CashManagementAccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("openShiftRequest", request);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/owner/cash-management/shifts/open";
        }
    }

    @GetMapping("/shifts/active")
    public String activeShift(@RequestParam(required = false) Long shiftId,
                              @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal); addCashContext(model, setup, principal);
        List<CashierShiftResponse> shifts = cashManagementService.findShifts(setup.businessId(), setup.branchId());
        CashierShiftResponse activeShift;
        if (shiftId == null) {
            activeShift = shifts.stream().filter(s -> s.getStatus() == CashierShiftStatus.OPEN).findFirst().orElse(null);
        } else {
            CashierShiftResponse selected = cashManagementService.findShift(setup.businessId(), setup.branchId(), shiftId);
            activeShift = selected != null && selected.getStatus() == CashierShiftStatus.OPEN ? selected : null;
        }
        model.addAttribute("activeShift", activeShift);
        model.addAttribute("openShiftCount", cashManagementService.countOpenShifts(setup.businessId(), setup.branchId()));
        model.addAttribute("shiftMovements", activeShift == null ? List.of()
                : cashManagementService.findShiftMovements(setup.businessId(), setup.branchId(), activeShift.getId()));
        model.addAttribute("shiftSummary", activeShift == null ? CashierShiftMovementSummaryResponse.empty()
                : cashManagementService.summarizeShifts(setup.businessId(), setup.branchId(), List.of(activeShift.getId()))
                .getOrDefault(activeShift.getId(), CashierShiftMovementSummaryResponse.empty()));
        model.addAttribute("expectedCash", activeShift == null ? null
                : cashManagementService.expectedCash(setup.businessId(), setup.branchId(), activeShift.getId()));
        model.addAttribute("activePage", "active-shift");
        return "cash-management/active-cashier-shift";
    }

    @GetMapping("/shifts/{shiftId}/close")
    public String closeShift(@PathVariable Long shiftId, @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal); addCashContext(model, setup, principal);
        CashierShiftResponse shift = cashManagementService.findShift(setup.businessId(), setup.branchId(), shiftId);
        if (shift == null) throw new CashManagementAccessDeniedException();
        CloseCashierShiftRequest request;
        if (model.containsAttribute("closeShiftRequest")) {
            request = (CloseCashierShiftRequest) model.getAttribute("closeShiftRequest");
        } else {
            request = new CloseCashierShiftRequest();
            request.setCloseKey(UUID.randomUUID().toString());
            model.addAttribute("closeShiftRequest", request);
        }
        model.addAttribute("shift", shift);
        model.addAttribute("expectedCash", cashManagementService.expectedCash(setup.businessId(), setup.branchId(), shiftId));
        model.addAttribute("branchSettings", branchSettingsAccessService.findByBusinessIdAndBranchId(
                setup.businessId(), setup.branchId()).orElse(null));
        model.addAttribute("activePage", "active-shift");
        return "cash-management/close-cashier-shift";
    }

    @PostMapping("/shifts/{shiftId}/close")
    public String closeShiftPost(@PathVariable Long shiftId,
                                 @Valid @ModelAttribute("closeShiftRequest") CloseCashierShiftRequest request,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal OwnerPrincipal principal,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("closeShiftRequest", request);
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "closeShiftRequest", bindingResult);
            redirectAttributes.addFlashAttribute("errorMessage", firstValidationMessage(bindingResult));
            return "redirect:/owner/cash-management/shifts/" + shiftId + "/close";
        }
        BusinessSetupResponse setup = setup(principal);
        try {
            CashierShiftResponse shift = cashManagementService.closeShift(
                    setup.businessId(), setup.branchId(), principal.ownerId(), shiftId, request);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Cashier shift " + shift.getShiftCode() + " closed and reconciled.");
            return "redirect:/owner/cash-management/shifts/" + shiftId;
        } catch (CashManagementValidationException | CashManagementAccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("closeShiftRequest", request);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/owner/cash-management/shifts/" + shiftId + "/close";
        }
    }

    @GetMapping("/shifts")
    public String shiftHistory(@RequestParam(required = false) Long registerId,
                               @RequestParam(required = false) Long cashierId,
                               @RequestParam(required = false) CashierShiftStatus status,
                               @RequestParam(required = false) CashVarianceResult varianceResult,
                               @RequestParam(required = false) Boolean approvalRequired,
                               @RequestParam(required = false) LocalDate from,
                               @RequestParam(required = false) LocalDate to,
                               @RequestParam(required = false) String q,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "25") int size,
                               @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal); addCashContext(model, setup, principal);
        int safeSize = java.util.Set.of(10, 25, 50, 100).contains(size) ? size : 25;
        ZoneId branchZone = (ZoneId) model.asMap().get("branchZone");
        var pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "openingTime").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<CashierShiftResponse> shiftPage;
        if (from != null && to != null && from.isAfter(to)) {
            model.addAttribute("errorMessage", "From date cannot be later than To date.");
            shiftPage = Page.empty(pageable);
        } else {
            shiftPage = cashManagementService.searchShifts(setup.businessId(), setup.branchId(), registerId, cashierId,
                    status, varianceResult, approvalRequired, from == null ? null : from.atStartOfDay(branchZone).toInstant(),
                    to == null ? null : to.plusDays(1).atStartOfDay(branchZone).toInstant(), q, pageable);
        }
        model.addAttribute("shifts", shiftPage.getContent()); model.addAttribute("shiftPage", shiftPage);
        model.addAttribute("shiftSummaryById", cashManagementService.summarizeShifts(setup.businessId(), setup.branchId(),
                shiftPage.getContent().stream().map(CashierShiftResponse::getId).toList()));
        model.addAttribute("registerId", registerId); model.addAttribute("cashierId", cashierId); model.addAttribute("shiftStatus", status);
        model.addAttribute("varianceResult", varianceResult); model.addAttribute("approvalRequired", approvalRequired);
        model.addAttribute("from", from); model.addAttribute("to", to); model.addAttribute("query", q == null ? "" : q.trim()); model.addAttribute("selectedSize", safeSize);
        model.addAttribute("activePage", "shift-history");
        return "cash-management/cashier-shift-history";
    }

    @GetMapping("/shifts/{shiftId}")
    public String shiftDetails(@PathVariable Long shiftId, @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal); addCashContext(model, setup, principal);
        CashierShiftResponse shift = cashManagementService.findShift(setup.businessId(), setup.branchId(), shiftId);
        if (shift == null) throw new CashManagementAccessDeniedException();
        model.addAttribute("shift", shift);
        model.addAttribute("shiftMovements", cashManagementService.findShiftMovements(setup.businessId(), setup.branchId(), shiftId));
        model.addAttribute("shiftSummary", cashManagementService.summarizeShifts(setup.businessId(), setup.branchId(), List.of(shiftId))
                .getOrDefault(shiftId, CashierShiftMovementSummaryResponse.empty()));
        model.addAttribute("varianceAdjustmentMovement", shift.getVarianceAdjustmentCashMovementId() == null ? null
                : cashManagementService.findMovement(setup.businessId(), setup.branchId(), shift.getVarianceAdjustmentCashMovementId()));
        model.addAttribute("activePage", "shift-history");
        return "cash-management/cashier-shift-details";
    }

    @GetMapping("/transfers/new")
    public String cashTransfer(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal); addCashContext(model, setup, principal);
        CashTransferRequest request;
        if (model.containsAttribute("cashTransferRequest")) {
            request = (CashTransferRequest) model.getAttribute("cashTransferRequest");
        } else {
            request = new CashTransferRequest();
            request.setTransferId(referenceCode("CT", (ZoneId) model.asMap().get("branchZone")));
            request.setTransferKey(UUID.randomUUID().toString());
            model.addAttribute("cashTransferRequest", request);
        }
        model.addAttribute("transferTypes", CashTransferType.values());
        ManagementActor actor = userManagementActorService.forOwner(principal.ownerId());
        model.addAttribute("cashiers", userService.findByBranch(actor, setup.branchId(), "", Pageable.unpaged()).getContent());
        model.addAttribute("openShifts", cashManagementService.findShifts(setup.businessId(), setup.branchId()).stream()
                .filter(value -> value.getStatus() == CashierShiftStatus.OPEN).toList());
        var branchSettings = branchSettingsAccessService.findByBusinessIdAndBranchId(
                setup.businessId(), setup.branchId()).orElse(null);
        if (request.getExternalAccountReference() == null && branchSettings != null
                && branchSettings.defaultDepositAccountReference() != null) {
            request.setExternalAccountReference(branchSettings.defaultDepositAccountReference());
        }
        model.addAttribute("branchSettings", branchSettings);
        model.addAttribute("activePage", "cash-transfer");
        return "cash-management/cash-transfer";
    }

    @PostMapping("/transfers")
    public String postCashTransfer(@Valid @ModelAttribute("cashTransferRequest") CashTransferRequest request,
                                   BindingResult bindingResult,
                                   @AuthenticationPrincipal OwnerPrincipal principal,
                                   RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("cashTransferRequest", request);
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "cashTransferRequest", bindingResult);
            redirectAttributes.addFlashAttribute("errorMessage", firstValidationMessage(bindingResult));
            return "redirect:/owner/cash-management/transfers/new";
        }
        BusinessSetupResponse setup = setup(principal);
        try {
            var movement = cashManagementService.postTransfer(
                    setup.businessId(), setup.branchId(), principal.ownerId(), request);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Cash transfer " + request.getTransferId() + " posted as Cash Movement " + movement.getId() + ".");
            return "redirect:/owner/cash-management";
        } catch (CashManagementValidationException | CashManagementAccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("cashTransferRequest", request);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/owner/cash-management/transfers/new";
        }
    }

    @GetMapping("/variance")
    public String varianceReview(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal); addCashContext(model, setup, principal);
        model.addAttribute("shifts", cashManagementService.findUnresolvedVarianceShifts(setup.businessId(), setup.branchId()));
        model.addAttribute("branchSettings", branchSettingsAccessService.findByBusinessIdAndBranchId(
                setup.businessId(), setup.branchId()).orElse(null));
        model.addAttribute("activePage", "cash-variance");
        return "cash-management/cash-variance-review";
    }

    @PostMapping("/variance/{shiftId}/adjust")
    public String resolveVariance(@PathVariable Long shiftId,
                                  @Valid @ModelAttribute CashVarianceAdjustmentRequest request,
                                  BindingResult bindingResult,
                                  @AuthenticationPrincipal OwnerPrincipal principal,
                                  RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", firstValidationMessage(bindingResult));
            return "redirect:/owner/cash-management/variance";
        }
        BusinessSetupResponse setup = setup(principal);
        try {
            var movement = cashManagementService.resolveVariance(
                    setup.businessId(), setup.branchId(), principal.ownerId(), shiftId, request);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Cash variance resolved with linked Cash Movement " + movement.getId() + ".");
            return "redirect:/owner/cash-management/shifts/" + shiftId;
        } catch (CashManagementValidationException | CashManagementAccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/owner/cash-management/variance";
        }
    }

    @GetMapping("/movements/{movementId}")
    public String movementDetails(@PathVariable Long movementId,
                                  @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal); addCashContext(model, setup, principal);
        CashMovementResponse movement = cashManagementService.findMovement(setup.businessId(), setup.branchId(), movementId);
        model.addAttribute("movement", movement);
        boolean reversibleCashTransfer = cashManagementService.canReverseCashTransfer(
                setup.businessId(), setup.branchId(), movementId);
        model.addAttribute("reversibleCashTransfer", reversibleCashTransfer);
        if (reversibleCashTransfer && !model.containsAttribute("cashTransferReversalRequest")) {
            CashTransferReversalRequest reversalRequest = new CashTransferReversalRequest();
            reversalRequest.setReversalKey(UUID.randomUUID().toString());
            model.addAttribute("cashTransferReversalRequest", reversalRequest);
        }
        model.addAttribute("activePage", "cashbook");
        return "cash-management/cash-movement-details";
    }

    @PostMapping("/movements/{movementId}/reverse-transfer")
    public String reverseCashTransfer(@PathVariable Long movementId,
                                      @Valid @ModelAttribute("cashTransferReversalRequest") CashTransferReversalRequest request,
                                      BindingResult bindingResult,
                                      @AuthenticationPrincipal OwnerPrincipal principal,
                                      RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("cashTransferReversalRequest", request);
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "cashTransferReversalRequest", bindingResult);
            redirectAttributes.addFlashAttribute("errorMessage", firstValidationMessage(bindingResult));
            return "redirect:/owner/cash-management/movements/" + movementId;
        }
        BusinessSetupResponse setup = setup(principal);
        try {
            CashMovementResponse reversal = cashManagementService.reverseCashTransfer(
                    setup.businessId(), setup.branchId(), principal.ownerId(), movementId, request);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Cash transfer reversed with linked Cash Movement " + reversal.getId() + ".");
        } catch (CashManagementValidationException | CashManagementAccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("cashTransferReversalRequest", request);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/cash-management/movements/" + movementId;
    }

    private void addCashContext(Model model, BusinessSetupResponse setup, OwnerPrincipal principal) {
        var registers = cashManagementService.findRegisters(setup.businessId(), setup.branchId());
        var locations = cashManagementService.findCashLocations(setup.businessId(), setup.branchId());
        model.addAttribute("setup", setup);
        model.addAttribute("registers", registers); model.addAttribute("cashLocations", locations);
        model.addAttribute("activeRegisters", registers.stream().filter(r -> r.getStatus() == RegisterStatus.ACTIVE).toList());
        model.addAttribute("activeCashLocations", locations.stream().filter(l -> l.getStatus() == CashLocationStatus.ACTIVE).toList());
        model.addAttribute("allowedOpeningCashLocations", cashManagementService.findAllowedOpeningCashLocations(setup.businessId(), setup.branchId())
                .stream().filter(l -> l.getStatus() == CashLocationStatus.ACTIVE).toList());
        model.addAttribute("allowedClosingCashLocations", cashManagementService.findAllowedClosingCashLocations(setup.businessId(), setup.branchId())
                .stream().filter(l -> l.getStatus() == CashLocationStatus.ACTIVE).toList());
        model.addAttribute("registerNameById", registers.stream().collect(java.util.stream.Collectors.toMap(
                value -> value.getId(), value -> value.getName() + " (" + value.getCode() + ")")));
        model.addAttribute("cashLocationNameById", locations.stream().collect(java.util.stream.Collectors.toMap(
                value -> value.getId(), value -> value.getName())));
        ManagementActor actor = userManagementActorService.forOwner(principal.ownerId());
        var branchUsers = userService.findByBranch(actor, setup.branchId(), "", Pageable.unpaged()).getContent();
        model.addAttribute("branchUsers", branchUsers);
        model.addAttribute("userNameById", branchUsers.stream().collect(java.util.stream.Collectors.toMap(
                value -> value.id(), value -> value.fullName())));
        var activeBranch = branchAccessService.findByBusinessIdAndBranchId(
                setup.businessId(), setup.branchId()).orElseThrow(CashManagementAccessDeniedException::new);
        model.addAttribute("branchZone", ZoneId.of(activeBranch.timeZone()));
        model.addAttribute("currency", activeBranch.currency());
        model.addAttribute("ownerActor", principal.isOwner());
    }

    private String referenceCode(String prefix, ZoneId branchZone) {
        String timestamp = Instant.now().atZone(branchZone).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        return prefix + "-" + timestamp + "-" + suffix;
    }

    private BusinessSetupResponse setup(OwnerPrincipal principal) {
        BusinessSetupResponse setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        if (setup.businessId() == null || setup.branchId() == null) throw new CashManagementAccessDeniedException();
        return setup;
    }

    private String firstValidationMessage(BindingResult bindingResult) {
        return bindingResult.getAllErrors().isEmpty() ? "Please review the form and try again."
                : bindingResult.getAllErrors().getFirst().getDefaultMessage();
    }
}
