package com.spark.falcon.pos.controller;

import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.customer.dto.CustomerRequest;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.pos.dto.PosCartResponse;
import com.spark.falcon.pos.dto.PosCheckoutResponse;
import com.spark.falcon.pos.dto.PosRequest;
import com.spark.falcon.pos.exception.PosAccessDeniedException;
import com.spark.falcon.pos.exception.PosValidationException;
import com.spark.falcon.pos.service.PosService;
import com.spark.falcon.pos.service.PosReceiptService;
import com.spark.falcon.settings.dto.response.BranchSettingsResponse;
import com.spark.falcon.settings.service.BranchSettingsAccessService;
import com.spark.falcon.sale.dto.SaleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/owner/pos")
@RequiredArgsConstructor
public class PosController {

    private final PosService posService;
    private final PosReceiptService posReceiptService;
    private final BranchContextService branchContextService;
    private final BranchSettingsAccessService branchSettingsAccessService;

    @GetMapping
    public String page(@RequestParam(required = false) Long branchId,
                       @RequestParam(required = false) Long saleId,
                       @RequestParam(defaultValue = "") String customerSearch,
                       @AuthenticationPrincipal OwnerPrincipal principal,
                       Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = branchId == null ? setup.branchId() : branchId;
        PosRequest request = new PosRequest();
        request.setBranchId(activeBranchId);
        request.setIdempotencyKey(UUID.randomUUID().toString());
        model.addAttribute("posRequest", request);
        model.addAttribute("customerRequest", new CustomerRequest());
        if (saleId != null) {
            model.addAttribute("completedSale", posService.findSale(principal.ownerId(), activeBranchId, saleId));
        }
        populatePage(model, principal.ownerId(), activeBranchId, customerSearch, null, request);
        return "pos/pos";
    }

    @PostMapping("/cart")
    public String recalculate(@Valid @ModelAttribute("posRequest") PosRequest request,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal OwnerPrincipal principal,
                              Model model) {
        if (bindingResult.hasErrors()) {
            return renderWithError(model, principal.ownerId(), request, firstError(bindingResult), null);
        }
        try {
            PosCartResponse cart = posService.prepareCart(principal.ownerId(), request);
            return render(model, principal.ownerId(), request, cart, null);
        } catch (PosAccessDeniedException | PosValidationException exception) {
            return renderWithError(model, principal.ownerId(), request, exception.getMessage(), null);
        }
    }

    @PostMapping("/barcode")
    public String scanBarcode(@ModelAttribute("posRequest") PosRequest request,
                              @AuthenticationPrincipal OwnerPrincipal principal,
                              Model model) {
        try {
            PosCartResponse cart = posService.scanBarcode(principal.ownerId(), request);
            model.addAttribute("successMessage", "Product added to the cart.");
            return render(model, principal.ownerId(), request, cart, null);
        } catch (PosAccessDeniedException | PosValidationException exception) {
            return renderWithError(model, principal.ownerId(), request, exception.getMessage(), null);
        }
    }


    @PostMapping("/customer-search")
    public String searchCustomers(@ModelAttribute("posRequest") PosRequest request,
                                  @RequestParam(defaultValue = "") String customerSearch,
                                  @AuthenticationPrincipal OwnerPrincipal principal,
                                  Model model) {
        PosCartResponse cart = null;
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            try {
                cart = posService.prepareCart(principal.ownerId(), request);
            } catch (RuntimeException exception) {
                model.addAttribute("errorMessage", exception.getMessage());
            }
        }
        model.addAttribute("posRequest", request);
        model.addAttribute("customerRequest", new CustomerRequest());
        if (cart != null) model.addAttribute("cart", cart);
        populatePage(model, principal.ownerId(), request.getBranchId(), customerSearch, cart, request);
        return "pos/pos";
    }

    @PostMapping("/customer")
    public String createCustomer(@ModelAttribute("posRequest") PosRequest request,
                                 @Valid @ModelAttribute("customerRequest") CustomerRequest customerRequest,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal OwnerPrincipal principal,
                                 Model model) {
        if (bindingResult.hasErrors()) {
            return renderWithError(model, principal.ownerId(), request, firstError(bindingResult), null);
        }
        try {
            var customer = posService.createCustomer(principal.ownerId(), customerRequest);
            request.setCustomerId(customer.id());
            PosCartResponse cart = request.getItems() == null || request.getItems().isEmpty()
                    ? null : posService.prepareCart(principal.ownerId(), request);
            model.addAttribute("successMessage", "Customer created and selected without clearing the cart.");
            return render(model, principal.ownerId(), request, cart, customerRequest);
        } catch (RuntimeException exception) {
            return renderWithError(model, principal.ownerId(), request, exception.getMessage(), customerRequest);
        }
    }

    @PostMapping("/hold")
    public String hold(@Valid @ModelAttribute("posRequest") PosRequest request,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal OwnerPrincipal principal,
                       RedirectAttributes redirectAttributes,
                       Model model) {
        if (bindingResult.hasErrors()) {
            return renderWithError(model, principal.ownerId(), request, firstError(bindingResult), null);
        }
        try {
            SaleResponse sale = posService.hold(principal.ownerId(), request);
            redirectAttributes.addFlashAttribute("successMessage", "Sale held successfully.");
            return "redirect:/owner/pos?branchId=" + request.getBranchId() + "&saleId=" + sale.id();
        } catch (PosAccessDeniedException | PosValidationException exception) {
            return renderWithError(model, principal.ownerId(), request, exception.getMessage(), null);
        } catch (RuntimeException exception) {
            return renderWithError(model, principal.ownerId(), request,
                    exception.getMessage() == null ? "Sale could not be held." : exception.getMessage(), null);
        }
    }

    @PostMapping("/checkout")
    public String checkout(@Valid @ModelAttribute("posRequest") PosRequest request,
                           BindingResult bindingResult,
                           @AuthenticationPrincipal OwnerPrincipal principal,
                           RedirectAttributes redirectAttributes,
                           Model model) {
        if (bindingResult.hasErrors()) {
            return renderWithError(model, principal.ownerId(), request, firstError(bindingResult), null);
        }
        try {
            BusinessSetupResponse setup = setup(principal.ownerId());
            BranchSettingsResponse settings = branchSettings(setup.businessId(), request.getBranchId());
            PosCheckoutResponse checkout = posService.checkout(principal.ownerId(), request);
            String successMessage = "Sale confirmed successfully.";
            if (Boolean.TRUE.equals(settings.autoPrintReceipt()) && settings.receiptPrinterId() != null) {
                try {
                    String printerTitle = posReceiptService.print(principal.ownerId(), setup.businessId(),
                            request.getBranchId(), settings.receiptPrinterId(), checkout.sale().id());
                    successMessage += " Receipt sent to " + printerTitle + ".";
                } catch (RuntimeException exception) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Sale was confirmed, but the receipt could not be printed: " + exception.getMessage());
                }
            }
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
            return afterSaleRedirect(settings.afterSalePage(), request.getBranchId(), checkout.sale().id());
        } catch (PosAccessDeniedException | PosValidationException exception) {
            return renderWithError(model, principal.ownerId(), request, exception.getMessage(), null);
        } catch (RuntimeException exception) {
            return renderWithError(model, principal.ownerId(), request,
                    exception.getMessage() == null ? "POS checkout could not be completed." : exception.getMessage(), null);
        }
    }

    private String render(Model model,
                          Long ownerId,
                          PosRequest request,
                          PosCartResponse cart,
                          CustomerRequest customerRequest) {
        model.addAttribute("posRequest", request);
        model.addAttribute("customerRequest", customerRequest == null ? new CustomerRequest() : customerRequest);
        if (cart != null) model.addAttribute("cart", cart);
        populatePage(model, ownerId, request.getBranchId(), "", cart, request);
        return "pos/pos";
    }

    private String renderWithError(Model model,
                                   Long ownerId,
                                   PosRequest request,
                                   String message,
                                   CustomerRequest customerRequest) {
        model.addAttribute("errorMessage", message == null ? "Please review the POS form and try again." : message);
        PosCartResponse cart = null;
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            try {
                cart = posService.prepareCart(ownerId, request);
            } catch (RuntimeException ignored) {
                // Preserve the submitted form; the original validation message remains the visible error.
            }
        }
        return render(model, ownerId, request, cart, customerRequest);
    }

    private void populatePage(Model model,
                              Long ownerId,
                              Long branchId,
                              String customerSearch,
                              PosCartResponse cart,
                              PosRequest request) {
        BusinessSetupResponse setup = setup(ownerId);
        model.addAttribute("setup", setup);
        model.addAttribute("products", posService.findProducts(ownerId, branchId));
        model.addAttribute("paymentMethods", posService.findPaymentMethods(ownerId, branchId));
        model.addAttribute("customerMatches", posService.searchCustomers(ownerId, customerSearch, 15));
        BranchSettingsResponse settings = branchSettings(setup.businessId(), branchId);
        model.addAttribute("branchSettings", settings);
        model.addAttribute("posSoundEffectsEnabled", Boolean.TRUE.equals(settings.posSoundEffectsEnabled()));
        var cashContext = posService.prepareCashContext(ownerId, request);
        model.addAttribute("openCashierShifts", cashContext.openShifts());
        model.addAttribute("activeCashLocations", cashContext.activeCashLocations());
        model.addAttribute("activePage", "pos");
        if (cart != null) model.addAttribute("cart", cart);
    }

    private BranchSettingsResponse branchSettings(Long businessId, Long branchId) {
        return branchSettingsAccessService.findByBusinessIdAndBranchId(businessId, branchId)
                .orElseThrow(PosAccessDeniedException::new);
    }

    private String afterSaleRedirect(String afterSalePage, Long branchId, Long saleId) {
        if ("INVOICE".equals(afterSalePage)) {
            return "redirect:/owner/sales/" + saleId + "/invoice?branchId=" + branchId;
        }
        if ("SELL_LIST".equals(afterSalePage)) {
            return "redirect:/owner/sales?branchId=" + branchId;
        }
        return "redirect:/owner/pos?branchId=" + branchId + "&saleId=" + saleId;
    }

    private BusinessSetupResponse setup(Long ownerId) {
        return branchContextService.resolveOwnerSetup(ownerId);
    }

    private String firstError(BindingResult result) {
        return result.getAllErrors().isEmpty()
                ? "Please review the POS form and try again."
                : result.getAllErrors().getFirst().getDefaultMessage();
    }
}
