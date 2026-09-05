package com.spark.falcon.expense.controller;

import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.expense.dto.ExpenseCategoryFilter;
import com.spark.falcon.expense.dto.ExpenseCategoryRequest;
import com.spark.falcon.expense.dto.ExpenseCategoryResponse;
import com.spark.falcon.expense.service.ExpenseCategoryService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/owner/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseCategoryService service;
    private final BranchContextService branchContextService;

    @GetMapping
    public String list(@RequestParam(required = false) Long parentCategoryId,
                       @RequestParam(required = false) Boolean topLevelOnly,
                       @RequestParam(required = false) Boolean active,
                       @RequestParam(required = false) Boolean used,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDate,
                       @RequestParam(required = false) String query,
                       @PageableDefault(size = 25, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable,
                       @AuthenticationPrincipal OwnerPrincipal principal,
                       Model model) {
        common(model, principal.ownerId());
        ExpenseCategoryFilter filter = new ExpenseCategoryFilter(
                parentCategoryId, topLevelOnly, active, createdDate, used, query);
        var page = service.search(principal.ownerId(), filter, pageable);
        model.addAttribute("activePage", "expense-category-list");
        model.addAttribute("categoryPage", page);
        model.addAttribute("categories", page.getContent());
        model.addAttribute("allCategories", service.list(principal.ownerId()));
        model.addAttribute("categoryFilter", filter);
        model.addAttribute("sortParam", pageable.getSort().stream().findFirst()
                .map(order -> order.getProperty() + "," + order.getDirection().name().toLowerCase())
                .orElse("displayOrder,asc"));
        return "expense/category-list";
    }

    @GetMapping("/add")
    public String add(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        common(model, principal.ownerId());
        model.addAttribute("activePage", "add-expense-category");
        model.addAttribute("categories", service.list(principal.ownerId()));
        if (!model.containsAttribute("categoryRequest")) {
            model.addAttribute("categoryRequest", new ExpenseCategoryRequest());
        }
        model.addAttribute("editMode", false);
        return "expense/add-expense-category";
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          Model model) {
        common(model, principal.ownerId());
        model.addAttribute("activePage", "expense-category-list");
        model.addAttribute("category", service.find(principal.ownerId(), id));
        return "expense/expense-category-details";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @AuthenticationPrincipal OwnerPrincipal principal,
                       Model model) {
        common(model, principal.ownerId());
        model.addAttribute("activePage", "expense-category-list");
        model.addAttribute("categories", service.list(principal.ownerId()));
        ExpenseCategoryResponse category = service.find(principal.ownerId(), id);
        model.addAttribute("category", category);
        if (!model.containsAttribute("categoryRequest")) {
            model.addAttribute("categoryRequest", requestFrom(category));
        }
        model.addAttribute("editMode", true);
        return "expense/add-expense-category";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute ExpenseCategoryRequest request,
                         BindingResult result,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", first(result));
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "categoryRequest", result);
            redirectAttributes.addFlashAttribute("categoryRequest", request);
            return "redirect:/owner/expense-categories/add";
        }
        service.create(principal.ownerId(), request);
        redirectAttributes.addFlashAttribute("successMessage", "Expense category created.");
        return "redirect:/owner/expense-categories";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute ExpenseCategoryRequest request,
                         BindingResult result,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", first(result));
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "categoryRequest", result);
            redirectAttributes.addFlashAttribute("categoryRequest", request);
            return "redirect:/owner/expense-categories/" + id + "/edit";
        }
        service.update(principal.ownerId(), id, request);
        redirectAttributes.addFlashAttribute("successMessage", "Expense category updated.");
        return "redirect:/owner/expense-categories/" + id;
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id,
                           @AuthenticationPrincipal OwnerPrincipal principal,
                           RedirectAttributes redirectAttributes) {
        service.activate(principal.ownerId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Expense category activated.");
        return "redirect:/owner/expense-categories/" + id;
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id,
                             @AuthenticationPrincipal OwnerPrincipal principal,
                             RedirectAttributes redirectAttributes) {
        service.deactivate(principal.ownerId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Expense category deactivated.");
        return "redirect:/owner/expense-categories/" + id;
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        service.archive(principal.ownerId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Unused category archived.");
        return "redirect:/owner/expense-categories";
    }

    private void common(Model model, Long ownerId) {
        BusinessSetupResponse setup = branchContextService.resolveOwnerSetup(ownerId);
        model.addAttribute("setup", setup);
    }

    private ExpenseCategoryRequest requestFrom(ExpenseCategoryResponse category) {
        ExpenseCategoryRequest request = new ExpenseCategoryRequest();
        request.setName(category.name());
        request.setSlug(category.slug());
        request.setParentCategoryId(category.parentCategoryId());
        request.setDescription(category.description());
        request.setActive(category.active());
        request.setDisplayOrder(category.displayOrder());
        return request;
    }

    private String first(BindingResult result) {
        return result.getFieldErrors().isEmpty()
                ? "Invalid Expense Category"
                : result.getFieldErrors().getFirst().getDefaultMessage();
    }
}
