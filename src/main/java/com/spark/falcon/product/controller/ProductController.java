//package com.spark.falcon.product.controller;
//
//import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
//import com.spark.falcon.businesssetup.service.BusinessSetupService;
//import com.spark.falcon.identity.security.OwnerPrincipal;
//import com.spark.falcon.product.dto.request.ProductBarcodeRequest;
//import com.spark.falcon.product.dto.request.ProductRequest;
//import com.spark.falcon.product.dto.request.ProductUnitConversionRequest;
//import com.spark.falcon.product.dto.request.ProductVariantRequest;
//import com.spark.falcon.product.entity.enumtype.ProductStatus;
//import com.spark.falcon.product.exception.ProductAccessDeniedException;
//import com.spark.falcon.product.mapper.ProductMapper;
//import com.spark.falcon.product.service.ProductBarcodeService;
//import com.spark.falcon.product.service.ProductService;
//import com.spark.falcon.product.service.ProductUnitConversionService;
//import com.spark.falcon.product.service.ProductVariantService;
//import com.spark.falcon.settings.dto.response.UnitResponse;
//import com.spark.falcon.settings.service.TaxRateService;
//import com.spark.falcon.settings.service.UnitService;
//import com.spark.falcon.supplier.service.SupplierService;
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import org.springframework.data.domain.Pageable;
//import org.springframework.format.annotation.DateTimeFormat;
//import org.springframework.security.core.annotation.AuthenticationPrincipal;
//import org.springframework.stereotype.Controller;
//import org.springframework.ui.Model;
//import org.springframework.validation.BindingResult;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.servlet.mvc.support.RedirectAttributes;
//
//import java.time.LocalDate;
//import java.util.List;
//
//@Controller
//@RequestMapping("/owner/products")
//@RequiredArgsConstructor
//public class ProductController {
//    private final ProductService productService;
//    private final ProductVariantService variantService;
//    private final ProductBarcodeService barcodeService;
//    private final ProductUnitConversionService conversionService;
//    private final ProductMapper mapper;
//    private final BusinessSetupService businessSetupService;
//    private final UnitService unitService;
//    private final TaxRateService taxRateService;
//    private final SupplierService supplierService;
//
//    @GetMapping("/new")
//    public String newProductPage(@RequestParam(required = false) Long supplierId,
//                                 @AuthenticationPrincipal OwnerPrincipal principal,
//                                 Model model) {
//        BusinessSetupResponse setup = businessSetupService.findByOwner(principal.ownerId())
//                .orElseThrow(ProductAccessDeniedException::new);
//
//        ProductRequest productRequest = new ProductRequest();
//        productRequest.getBranchIds().add(setup.branchId());
//
//        model.addAttribute("setup", setup);
//        model.addAttribute("productRequest", productRequest);
//        model.addAttribute("variantRequest", new ProductVariantRequest());
//        model.addAttribute("branches", List.of(setup));
//        model.addAttribute("units", unitService.findAll(principal.ownerId()));
//        model.addAttribute("taxRates", taxRateService.findAll(principal.ownerId()));
//        model.addAttribute("suppliers", supplierService.findAll(principal.ownerId(), "", Pageable.unpaged()).getContent());
//        model.addAttribute("selectedSupplierId", supplierId);
//        model.addAttribute("recentProductRows", List.of());
//        return "product/add-product";
//    }
//
//    @PostMapping
//    public String createProduct(@Valid @ModelAttribute ProductRequest request,
//                                BindingResult bindingResult,
//                                @AuthenticationPrincipal OwnerPrincipal principal,
//                                RedirectAttributes redirectAttributes) {
//        if (bindingResult.hasErrors()) return validationFailure(bindingResult, redirectAttributes);
//        productService.create(mapper.toCreateCommand(principal.ownerId(), request));
//        redirectAttributes.addFlashAttribute("successMessage", "Product created successfully.");
//        return dashboard();
//    }
//
//    @PostMapping("/{productId}")
//    public String updateProduct(@PathVariable Long productId,
//                                @Valid @ModelAttribute ProductRequest request,
//                                BindingResult bindingResult,
//                                @AuthenticationPrincipal OwnerPrincipal principal,
//                                RedirectAttributes redirectAttributes) {
//        if (bindingResult.hasErrors()) return validationFailure(bindingResult, redirectAttributes);
//        productService.update(mapper.toUpdateCommand(principal.ownerId(), productId, request));
//        redirectAttributes.addFlashAttribute("successMessage", "Product updated successfully.");
//        return dashboard();
//    }
//
//    @PostMapping("/{productId}/status")
//    public String productStatus(@PathVariable Long productId,
//                                @RequestParam ProductStatus status,
//                                @AuthenticationPrincipal OwnerPrincipal principal,
//                                RedirectAttributes redirectAttributes) {
//        productService.changeStatus(principal.ownerId(), productId, status);
//        redirectAttributes.addFlashAttribute("successMessage", "Product status updated.");
//        return dashboard();
//    }
//
//    @PostMapping("/{productId}/archive")
//    public String archiveProduct(@PathVariable Long productId,
//                                 @AuthenticationPrincipal OwnerPrincipal principal,
//                                 RedirectAttributes redirectAttributes) {
//        productService.archive(principal.ownerId(), productId);
//        redirectAttributes.addFlashAttribute("successMessage", "Product archived.");
//        return dashboard();
//    }
//
//    @PostMapping("/{productId}/restore")
//    public String restoreProduct(@PathVariable Long productId,
//                                 @AuthenticationPrincipal OwnerPrincipal principal,
//                                 RedirectAttributes redirectAttributes) {
//        productService.restore(principal.ownerId(), productId);
//        redirectAttributes.addFlashAttribute("successMessage", "Product restored.");
//        return dashboard();
//    }
//
//    @PostMapping("/{productId}/variants")
//    public String createVariant(@PathVariable Long productId,
//                                @Valid @ModelAttribute ProductVariantRequest request,
//                                BindingResult bindingResult,
//                                @AuthenticationPrincipal OwnerPrincipal principal,
//                                RedirectAttributes redirectAttributes) {
//        if (bindingResult.hasErrors()) return validationFailure(bindingResult, redirectAttributes);
//        variantService.create(mapper.toCreateVariantCommand(principal.ownerId(), productId, request));
//        redirectAttributes.addFlashAttribute("successMessage", "Product variant created successfully.");
//        return dashboard();
//    }
//
//    @PostMapping("/{productId}/variants/{variantId}")
//    public String updateVariant(@PathVariable Long productId,
//                                @PathVariable Long variantId,
//                                @Valid @ModelAttribute ProductVariantRequest request,
//                                BindingResult bindingResult,
//                                @AuthenticationPrincipal OwnerPrincipal principal,
//                                RedirectAttributes redirectAttributes) {
//        if (bindingResult.hasErrors()) return validationFailure(bindingResult, redirectAttributes);
//        variantService.update(mapper.toUpdateVariantCommand(principal.ownerId(), productId, variantId, request));
//        redirectAttributes.addFlashAttribute("successMessage", "Product variant updated successfully.");
//        return dashboard();
//    }
//
//    @PostMapping("/{productId}/variants/{variantId}/status")
//    public String variantStatus(@PathVariable Long productId,
//                                @PathVariable Long variantId,
//                                @RequestParam ProductStatus status,
//                                @AuthenticationPrincipal OwnerPrincipal principal,
//                                RedirectAttributes redirectAttributes) {
//        variantService.changeStatus(principal.ownerId(), productId, variantId, status);
//        redirectAttributes.addFlashAttribute("successMessage", "Product variant status updated.");
//        return dashboard();
//    }
//
//    @PostMapping("/{productId}/variants/{variantId}/barcodes")
//    public String createBarcode(@PathVariable Long productId,
//                                @PathVariable Long variantId,
//                                @Valid @ModelAttribute ProductBarcodeRequest request,
//                                BindingResult bindingResult,
//                                @AuthenticationPrincipal OwnerPrincipal principal,
//                                RedirectAttributes redirectAttributes) {
//        if (bindingResult.hasErrors()) return validationFailure(bindingResult, redirectAttributes);
//        barcodeService.create(mapper.toCreateBarcodeCommand(principal.ownerId(), productId, variantId, request));
//        redirectAttributes.addFlashAttribute("successMessage", "Product barcode created successfully.");
//        return dashboard();
//    }
//
//    @PostMapping("/{productId}/variants/{variantId}/barcodes/{barcodeId}/deactivate")
//    public String deactivateBarcode(@PathVariable Long productId,
//                                    @PathVariable Long variantId,
//                                    @PathVariable Long barcodeId,
//                                    @AuthenticationPrincipal OwnerPrincipal principal,
//                                    RedirectAttributes redirectAttributes) {
//        barcodeService.deactivate(principal.ownerId(), productId, variantId, barcodeId);
//        redirectAttributes.addFlashAttribute("successMessage", "Product barcode deactivated.");
//        return dashboard();
//    }
//
//    @PostMapping("/{productId}/variants/{variantId}/conversions")
//    public String createConversion(@PathVariable Long productId,
//                                   @PathVariable Long variantId,
//                                   @Valid @ModelAttribute ProductUnitConversionRequest request,
//                                   BindingResult bindingResult,
//                                   @AuthenticationPrincipal OwnerPrincipal principal,
//                                   RedirectAttributes redirectAttributes) {
//        if (bindingResult.hasErrors()) return validationFailure(bindingResult, redirectAttributes);
//        conversionService.create(mapper.toCreateConversionCommand(principal.ownerId(), productId, variantId, request));
//        redirectAttributes.addFlashAttribute("successMessage", "Product unit conversion created successfully.");
//        return dashboard();
//    }
//
//    @PostMapping("/{productId}/variants/{variantId}/conversions/{conversionId}/deactivate")
//    public String deactivateConversion(@PathVariable Long productId,
//                                       @PathVariable Long variantId,
//                                       @PathVariable Long conversionId,
//                                       @RequestParam(required = false)
//                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveTo,
//                                       @AuthenticationPrincipal OwnerPrincipal principal,
//                                       RedirectAttributes redirectAttributes) {
//        conversionService.deactivate(principal.ownerId(), productId, variantId, conversionId, effectiveTo);
//        redirectAttributes.addFlashAttribute("successMessage", "Product unit conversion deactivated.");
//        return dashboard();
//    }
//
//    private String validationFailure(BindingResult bindingResult, RedirectAttributes redirectAttributes) {
//        String message = bindingResult.getAllErrors().isEmpty()
//                ? "Please review the form and try again."
//                : bindingResult.getAllErrors().getFirst().getDefaultMessage();
//        redirectAttributes.addFlashAttribute("errorMessage", message);
//        return dashboard();
//    }
//
//    private String dashboard() {
//        return "redirect:/owner/dashboard";
//    }
//}



package com.spark.falcon.product.controller;

import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.branch.service.BranchService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.product.dto.request.ProductBarcodeRequest;
import com.spark.falcon.product.dto.request.ProductRequest;
import com.spark.falcon.product.dto.request.ProductUnitConversionRequest;
import com.spark.falcon.product.dto.request.ProductVariantRequest;
import com.spark.falcon.product.dto.request.CategoryRequest;
import com.spark.falcon.product.dto.request.ProductImageRequest;
import com.spark.falcon.product.dto.response.ProductResponse;
import com.spark.falcon.product.dto.response.ProductVariantResponse;
import com.spark.falcon.product.entity.enumtype.ProductStatus;
import com.spark.falcon.product.exception.ProductAccessDeniedException;
import com.spark.falcon.product.exception.ProductConfigurationConflictException;
import com.spark.falcon.product.mapper.ProductMapper;
import com.spark.falcon.product.service.ProductBarcodeService;
import com.spark.falcon.product.service.ProductCreationService;
import com.spark.falcon.product.service.ProductReferenceCodeService;
import com.spark.falcon.product.service.ProductService;
import com.spark.falcon.product.service.ProductSkuService;
import com.spark.falcon.product.service.ProductUnitConversionService;
import com.spark.falcon.product.service.ProductVariantService;
import com.spark.falcon.product.web.session.ProductReferenceDraftSession;
import com.spark.falcon.product.web.session.ProductSkuDraftSession;
import com.spark.falcon.product.service.ProductImageStorageService;
import com.spark.falcon.product.service.CategoryService;
import com.spark.falcon.settings.dto.response.UnitResponse;
import com.spark.falcon.settings.dto.response.TaxRateResponse;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import com.spark.falcon.settings.service.TaxRateService;
import com.spark.falcon.settings.service.UnitService;
import com.spark.falcon.supplier.dto.SupplierResponse;
import com.spark.falcon.supplier.service.SupplierService;
import com.spark.falcon.inventory.service.InventoryQueryService;
import com.spark.falcon.inventory.dto.BranchProductStockResponse;
import com.spark.falcon.inventory.dto.ProductBatchResponse;
import com.spark.falcon.inventory.entity.ProductBatchStatus;
import com.spark.falcon.shared.export.*;
import com.spark.falcon.purchase.service.PurchaseProductReadService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.ZoneId;

@Controller
@RequestMapping("/owner/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;
    private final ProductCreationService productCreationService;
    private final ProductReferenceCodeService productReferenceCodeService;
    private final ProductSkuService productSkuService;
    private final ProductVariantService variantService;
    private final ProductBarcodeService barcodeService;
    private final ProductUnitConversionService conversionService;
    private final ProductMapper mapper;
    private final BranchContextService branchContextService;
    private final UnitService unitService;
    private final TaxRateService taxRateService;
    private final SupplierService supplierService;
    private final CategoryService categoryService;
    private final BranchService branchService;
    private final InventoryQueryService inventoryQueryService;
    private final ExportResponse exportResponse;
    private final ProductImageStorageService imageStorageService;
    private final PurchaseProductReadService purchaseProductReadService;

    @GetMapping("/export")
    public void exportProducts(@RequestParam String format,
                               @RequestParam(required = false) Long branchId,
                               @RequestParam(required = false) Long categoryId,
                               @RequestParam(required = false) ProductStatus status,
                               @RequestParam(defaultValue = "false") Boolean archived,
                               @RequestParam(required = false) String q,
                               @RequestParam(defaultValue = "name") String sortBy,
                               @RequestParam(defaultValue = "asc") String sortDir,
                               @AuthenticationPrincipal OwnerPrincipal principal,
                               HttpServletResponse response) throws java.io.IOException {
        BusinessSetupResponse setup = setup(principal);
        Long activeBranchId = branchId == null ? setup.branchId() : branchId;
        branchService.findOwnedById(principal.ownerId(), activeBranchId);
        String safeSort = Set.of("name", "referenceCode", "displayOrder", "createdAt", "status").contains(sortBy) ? sortBy : "name";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String query = q == null ? "" : q.trim();
        Map<Long, String> categoryNames = categoryService.findAll(principal.ownerId()).stream()
                .collect(java.util.stream.Collectors.toMap(com.spark.falcon.product.dto.response.CategoryResponse::id,
                        com.spark.falcon.product.dto.response.CategoryResponse::name));
        Map<Long, BranchProductStockResponse> stocks = inventoryQueryService
                .findBranchStocks(setup.businessId(), activeBranchId).stream()
                .collect(java.util.stream.Collectors.toMap(BranchProductStockResponse::getProductVariantId,
                        java.util.function.Function.identity(), (left, right) -> left));
        Map<String, String> filters = new java.util.LinkedHashMap<>();
        if (!query.isBlank()) filters.put("Search", query);
        if (categoryId != null) filters.put("Category", categoryNames.getOrDefault(categoryId, String.valueOf(categoryId)));
        if (status != null) filters.put("Status", status.name());
        ExportDocument document = new ExportDocument("Product List", setup.businessName(), setup.branchName(),
                ZoneId.systemDefault(), filters,
                List.of(ExportColumn.text("Product Code"), ExportColumn.text("Product Name"), ExportColumn.text("Variant"),
                        ExportColumn.text("SKU"), ExportColumn.text("Category"), ExportColumn.money("Selling Price"),
                        ExportColumn.number("Active-Branch Stock"), ExportColumn.text("Image Reference"), ExportColumn.text("Status")),
                consumer -> {
                    int pageNumber = 0;
                    Page<ProductResponse> page;
                    do {
                        page = productService.search(principal.ownerId(), activeBranchId, categoryId, status, archived, query,
                                PageRequest.of(pageNumber++, 250, Sort.by(direction, safeSort).and(Sort.by("id"))));
                        List<Long> productIds = page.getContent().stream().map(ProductResponse::id).toList();
                        Map<Long, List<ProductVariantResponse>> variants = variantService.findAllForProducts(principal.ownerId(), productIds);
                        for (ProductResponse product : page.getContent()) {
                            String imageReference = product.thumbnailReference() == null ? "" : product.thumbnailReference();
                            for (ProductVariantResponse variant : variants.getOrDefault(product.id(), List.of())) {
                                BranchProductStockResponse stock = stocks.get(variant.id());
                                consumer.accept(product.referenceCode(), product.name(), variant.variantName(), variant.sku(),
                                        categoryNames.getOrDefault(product.categoryId(), ""), variant.sellingPrice(),
                                        stock == null ? java.math.BigDecimal.ZERO : stock.getAvailableBaseQuantity(),
                                        imageReference, variant.status());
                            }
                        }
                    } while (page.hasNext());
                });
        exportResponse.write(format, document, response);
    }

    @GetMapping
    public String productListPage(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) Long categoryId,
                                  @RequestParam(required = false) ProductStatus status,
                                  @RequestParam(defaultValue = "false") Boolean archived,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(defaultValue = "name") String sortBy,
                                  @RequestParam(defaultValue = "asc") String sortDir,
                                  @AuthenticationPrincipal OwnerPrincipal principal,
                                  Model model) {
        BusinessSetupResponse setup = setup(principal);
        Long ownerId = principal.ownerId();
        int safeSize = Set.of(10, 25, 50, 100).contains(size) ? size : 10;
        String safeSort = Set.of("name", "referenceCode", "createdAt", "status").contains(sortBy) ? sortBy : "name";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String query = q == null ? "" : q.trim();
        Page<ProductResponse> productPage = productService.search(ownerId, setup.branchId(), categoryId, status, archived, query,
                PageRequest.of(Math.max(0, page), safeSize, Sort.by(direction, safeSort).and(Sort.by("id"))));
        List<Long> productIds = productPage.getContent().stream().map(ProductResponse::id).toList();
        Map<Long, List<ProductVariantResponse>> variantsByProduct = variantService.findAllForProducts(ownerId, productIds);
        Map<Long, List<SupplierResponse>> suppliersByProduct = supplierService.findLinkedSuppliersByProductIds(ownerId, productIds);
        Map<Long, BranchProductStockResponse> stocks = inventoryQueryService.findBranchStocks(setup.businessId(), setup.branchId()).stream()
                .collect(java.util.stream.Collectors.toMap(BranchProductStockResponse::getProductVariantId,
                        java.util.function.Function.identity(), (left, right) -> left));
        Map<Long, List<ProductBatchResponse>> batchesByVariant = inventoryQueryService
                .findBranchBatches(setup.businessId(), setup.branchId()).stream()
                .collect(java.util.stream.Collectors.groupingBy(ProductBatchResponse::getProductVariantId));
        LocalDate today = LocalDate.now(ZoneId.of(branchService.findOwnedById(ownerId, setup.branchId()).timeZone()));
        List<ProductListRow> productRows = productPage.getContent().stream()
                .map(product -> {
                    List<ProductVariantResponse> variants = variantsByProduct.getOrDefault(product.id(), List.of());
                    ProductVariantResponse primaryVariant = variants.stream().findFirst().orElse(null);
                    List<SupplierResponse> linkedSuppliers = suppliersByProduct.getOrDefault(product.id(), List.of());
                    List<VariantListValue> variantValues = variants.stream().map(variant -> {
                        BranchProductStockResponse stock = stocks.get(variant.id());
                        return new VariantListValue(variant.variantName(), variant.sku(), variant.sellingPrice(),
                                stock == null ? java.math.BigDecimal.ZERO : stock.getAvailableBaseQuantity());
                    }).toList();
                    List<ProductBatchResponse> productBatches = variants.stream()
                            .flatMap(variant -> batchesByVariant.getOrDefault(variant.id(), List.of()).stream())
                            .filter(batch -> batch.getAvailableBaseQuantity().signum() > 0).toList();
                    java.math.BigDecimal purchasePrice = productBatches.stream()
                            .max(java.util.Comparator.comparing(ProductBatchResponse::getCreatedAt))
                            .map(ProductBatchResponse::getOriginalPurchaseUnitCost).orElse(null);
                    LocalDate nearestExpiry = productBatches.stream().map(ProductBatchResponse::getExpiryDate)
                            .filter(java.util.Objects::nonNull).min(LocalDate::compareTo).orElse(null);
                    LocalDate alertThrough = today.plusDays(product.expiryAlertBeforeDays() == null ? 0 : product.expiryAlertBeforeDays());
                    java.math.BigDecimal expiringQuantity = productBatches.stream()
                            .filter(batch -> batch.getExpiryDate() != null && !batch.getExpiryDate().isBefore(today)
                                    && !batch.getExpiryDate().isAfter(alertThrough))
                            .map(ProductBatchResponse::getAvailableBaseQuantity).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    java.math.BigDecimal expiredQuantity = productBatches.stream()
                            .filter(batch -> batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today))
                            .map(ProductBatchResponse::getAvailableBaseQuantity).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    long activeBatchCount = productBatches.stream().filter(batch -> batch.getStatus() == ProductBatchStatus.ACTIVE).count();
                    return new ProductListRow(
                            product,
                            primaryVariant,
                            variantValues,
                            linkedSuppliers,
                            purchasePrice, nearestExpiry, expiringQuantity, expiredQuantity, activeBatchCount,
                            productSearchText(product, variants, linkedSuppliers)
                    );
                })
                .toList();

        model.addAttribute("setup", setup);
        model.addAttribute("activePage", "product-list");
        model.addAttribute("productRows", productRows);
        model.addAttribute("productPage", productPage);
        model.addAttribute("query", query);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedArchived", archived);
        model.addAttribute("selectedSize", safeSize);
        model.addAttribute("selectedSort", safeSort);
        model.addAttribute("selectedDirection", direction.name().toLowerCase());
        model.addAttribute("pageSizes", List.of(10, 25, 50, 100));
        model.addAttribute("categories", categoryService.findAll(ownerId));
        return "product/product-list";
    }

    @GetMapping("/new")
    public String newProductPage(@RequestParam(required = false) Long supplierId,
                                 @RequestParam(required = false) String returnTo,
                                 @RequestParam(required = false) Long returnBranchId,
                                 @RequestParam(required = false) String draftId,
                                 @AuthenticationPrincipal OwnerPrincipal principal,
                                 HttpSession session,
                                 Model model) {
        if ((draftId == null || draftId.isBlank()) && !model.containsAttribute("productRequest")) {
            return redirectToNewProductDraft(supplierId, returnTo, returnBranchId);
        }
        if (draftId == null || draftId.isBlank()) {
            draftId = UUID.randomUUID().toString();
        }

        BusinessSetupResponse setup = setup(principal);
        productListPage(null, null, null, false, 0, 10, "createdAt", "desc", principal, model);

        ProductRequest productRequest;
        if (model.containsAttribute("productRequest")) {
            productRequest = (ProductRequest) model.getAttribute("productRequest");
        } else {
            productRequest = new ProductRequest();
            String stableDraftId = draftId;
            productRequest.setReferenceCode(ProductReferenceDraftSession.getOrCreate(
                    session,
                    stableDraftId,
                    () -> productReferenceCodeService.reserveNext(principal.ownerId())));
            productRequest.getBranchIds().add(setup.branchId());
            model.addAttribute("productRequest", productRequest);
        }

        model.addAttribute("setup", setup);
        model.addAttribute("activePage", "add-product");
        if (!model.containsAttribute("variantRequest")) {
            ProductVariantRequest variantRequest = new ProductVariantRequest();
            variantRequest.setSku(ProductSkuDraftSession.getOrCreateForNewProduct(
                    session,
                    draftId,
                    () -> productSkuService.reserveNext(principal.ownerId())));
            model.addAttribute("variantRequest", variantRequest);
        }
        model.addAttribute("branches", branchService.findAll(principal.ownerId(), "", Pageable.unpaged()).getContent());
        model.addAttribute("units", unitService.findAll(principal.ownerId()));
        model.addAttribute("taxRates", taxRateService.findAll(principal.ownerId()));
        model.addAttribute("suppliers", supplierService.findAll(principal.ownerId(), "", Pageable.unpaged()).getContent());
        model.addAttribute("categories", categoryService.findAll(principal.ownerId()).stream().filter(c -> !c.archived()).toList());
        model.addAttribute("selectedSupplierId", supplierId);
        model.addAttribute("draftId", draftId);
        model.addAttribute("returnTo", "purchase".equalsIgnoreCase(returnTo) ? "purchase" : null);
        model.addAttribute("returnBranchId", "purchase".equalsIgnoreCase(returnTo) ? returnBranchId : null);
        model.addAttribute("recentProductRows", model.getAttribute("productRows"));
        return "product/add-product";
    }

    @GetMapping("/categories/new")
    public String addCategoryPage(@AuthenticationPrincipal OwnerPrincipal principal,
                                  Model model) {
        BusinessSetupResponse setup = setup(principal);
        CategoryRequest request;
        if (model.containsAttribute("categoryRequest")) {
            request = (CategoryRequest) model.getAttribute("categoryRequest");
        } else {
            request = new CategoryRequest();
            request.getBranchIds().add(setup.branchId());
        }
        model.addAttribute("setup", setup);
        model.addAttribute("activePage", "add-category");
        model.addAttribute("categoryRequest", request);
        model.addAttribute("categories", categoryService.findAll(principal.ownerId()).stream().filter(c -> !c.archived()).toList());
        model.addAttribute("categoryRows", categoryService.findAll(principal.ownerId()));
        model.addAttribute("branches", branchService.findAll(principal.ownerId(), "", Pageable.unpaged()).getContent());
        return "product/add-category";
    }

    @GetMapping("/categories")
    public String categoryListPage(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) ProductStatus status,
                                   @RequestParam(defaultValue = "false") Boolean archived,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "10") int size,
                                   @RequestParam(defaultValue = "displayOrder") String sortBy,
                                   @RequestParam(defaultValue = "asc") String sortDir,
                                   @AuthenticationPrincipal OwnerPrincipal principal,
                                   Model model) {
        BusinessSetupResponse setup = setup(principal);
        int safeSize = Set.of(10, 25, 50, 100).contains(size) ? size : 10;
        String safeSort = Set.of("name", "displayOrder", "createdAt", "status").contains(sortBy) ? sortBy : "displayOrder";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String query = q == null ? "" : q.trim();
        Page<com.spark.falcon.product.dto.response.CategoryResponse> categories = categoryService.search(
                principal.ownerId(), setup.branchId(), status, archived, query,
                PageRequest.of(Math.max(0, page), safeSize, Sort.by(direction, safeSort).and(Sort.by("name"))));
        model.addAttribute("setup", setup);
        model.addAttribute("activePage", "category-list");
        model.addAttribute("categoryRows", categories.getContent());
        model.addAttribute("categoryPage", categories);
        model.addAttribute("query", query); model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedArchived", archived); model.addAttribute("selectedSize", safeSize);
        model.addAttribute("selectedSort", safeSort); model.addAttribute("selectedDirection", direction.name().toLowerCase());
        model.addAttribute("pageSizes", List.of(10, 25, 50, 100));
        return "product/category-list";
    }

    @GetMapping("/categories/export")
    public void exportCategories(@RequestParam String format, @RequestParam(required = false) String q,
                                 @RequestParam(required = false) ProductStatus status,
                                 @RequestParam(defaultValue = "false") Boolean archived,
                                 @AuthenticationPrincipal OwnerPrincipal principal,
                                 HttpServletResponse response) throws java.io.IOException {
        BusinessSetupResponse setup = setup(principal);
        String query = q == null ? "" : q.trim();
        ExportDocument document = new ExportDocument("Category List", setup.businessName(), setup.branchName(),
                ZoneId.systemDefault(), query.isBlank() ? Map.of() : Map.of("Search", query),
                List.of(ExportColumn.number("ID"), ExportColumn.text("Name"), ExportColumn.number("Total Products"),
                        ExportColumn.number("Display Order"), ExportColumn.text("Status"), ExportColumn.dateTime("Creation Date")),
                consumer -> {
                    int number = 0; Page<com.spark.falcon.product.dto.response.CategoryResponse> values;
                    do {
                        values = categoryService.search(principal.ownerId(), setup.branchId(), status, archived, query,
                                PageRequest.of(number++, 500, Sort.by("displayOrder").and(Sort.by("name"))));
                        for (var category : values) consumer.accept(category.id(), category.name(), category.totalProducts(),
                                category.displayOrder(), category.archived() ? "ARCHIVED" : category.status(), category.createdAt());
                    } while (values.hasNext());
                });
        exportResponse.write(format, document, response);
    }

    @GetMapping("/categories/{categoryId}")
    public String categoryDetails(@PathVariable Long categoryId, @AuthenticationPrincipal OwnerPrincipal principal,
                                  Model model) {
        model.addAttribute("setup", setup(principal));
        model.addAttribute("activePage", "category-list");
        model.addAttribute("category", categoryService.findOne(principal.ownerId(), categoryId));
        return "product/category-details";
    }

    @GetMapping("/categories/{categoryId}/edit")
    public String editCategory(@PathVariable Long categoryId, @AuthenticationPrincipal OwnerPrincipal principal,
                               Model model) {
        var category = categoryService.findOne(principal.ownerId(), categoryId);
        CategoryRequest request;
        if (model.containsAttribute("categoryRequest")) {
            request = (CategoryRequest) model.getAttribute("categoryRequest");
        } else {
            request = new CategoryRequest();
            request.setName(category.name()); request.setSlug(category.slug());
            request.setParentCategoryId(category.parentCategoryId()); request.setDescription(category.description());
            request.setThumbnailReference(category.thumbnailReference()); request.setDisplayOrder(category.displayOrder());
            request.setStatus(category.status()); request.setBranchIds(new java.util.LinkedHashSet<>(category.branchIds()));
        }
        model.addAttribute("setup", setup(principal)); model.addAttribute("activePage", "category-list");
        model.addAttribute("category", category); model.addAttribute("categoryRequest", request);
        model.addAttribute("categories", categoryService.findAll(principal.ownerId()).stream()
                .filter(c -> !c.archived() && !c.id().equals(categoryId)).toList());
        model.addAttribute("branches", branchService.findAll(principal.ownerId(), "", Pageable.unpaged()).getContent());
        return "product/edit-category";
    }

    @PostMapping("/categories")
    public String createCategory(@Valid @ModelAttribute CategoryRequest request, BindingResult bindingResult,
                                 @RequestParam(required = false) MultipartFile categoryImageFile,
                                 @AuthenticationPrincipal OwnerPrincipal principal, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) return validationFailure(bindingResult, redirectAttributes, "/owner/products/categories/new");
        String stored = imageStorageService.storeCategory(categoryImageFile);
        if (stored != null) request.setThumbnailReference(stored);
        try { categoryService.create(principal.ownerId(), request); }
        catch (RuntimeException exception) { imageStorageService.deleteNewReference(stored); throw exception; }
        redirectAttributes.addFlashAttribute("successMessage", "Category created successfully.");
        return "redirect:/owner/products/categories";
    }

    @PostMapping("/categories/{categoryId}")
    public String updateCategory(@PathVariable Long categoryId, @Valid @ModelAttribute CategoryRequest request,
                                 BindingResult bindingResult, @AuthenticationPrincipal OwnerPrincipal principal,
                                 @RequestParam(required = false) MultipartFile categoryImageFile,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) return validationFailure(bindingResult, redirectAttributes,
                "/owner/products/categories/" + categoryId + "/edit");
        String previous = categoryService.findOne(principal.ownerId(), categoryId).thumbnailReference();
        String stored = imageStorageService.storeCategory(categoryImageFile);
        if (stored != null) request.setThumbnailReference(stored);
        try { categoryService.update(principal.ownerId(), categoryId, request); }
        catch (RuntimeException exception) { imageStorageService.deleteNewReference(stored); throw exception; }
        if (previous != null && !previous.equals(request.getThumbnailReference())) imageStorageService.deleteNewReference(previous);
        redirectAttributes.addFlashAttribute("successMessage", "Category updated successfully.");
        return "redirect:/owner/products/categories/" + categoryId;
    }

    @PostMapping("/categories/{categoryId}/archive")
    public String archiveCategory(@PathVariable Long categoryId, @AuthenticationPrincipal OwnerPrincipal principal,
                                  RedirectAttributes redirectAttributes) {
        categoryService.archive(principal.ownerId(), categoryId);
        redirectAttributes.addFlashAttribute("successMessage", "Category archived.");
        return "redirect:/owner/products/categories";
    }

    @PostMapping("/categories/{categoryId}/restore")
    public String restoreCategory(@PathVariable Long categoryId, @AuthenticationPrincipal OwnerPrincipal principal,
                                  RedirectAttributes redirectAttributes) {
        categoryService.restore(principal.ownerId(), categoryId);
        redirectAttributes.addFlashAttribute("successMessage", "Category restored.");
        return "redirect:/owner/products/categories?archived=true";
    }

    @GetMapping("/{productId}")
    public String productDetails(@PathVariable Long productId,
                                 @RequestParam(defaultValue = "false") boolean addVariant,
                                 @RequestParam(required = false) String variantDraftId,
                                 @AuthenticationPrincipal OwnerPrincipal principal,
                                 HttpSession session,
                                 Model model) {
        if (addVariant && (variantDraftId == null || variantDraftId.isBlank())) {
            return redirectToVariantDraft(productId);
        }
        var product = productService.findByOwnerAndId(principal.ownerId(), productId)
                .orElseThrow(com.spark.falcon.product.exception.ProductNotFoundException::new);
        List<ProductVariantResponse> variants = variantService.findAll(principal.ownerId(), productId);
        List<SupplierResponse> supplierOptions = supplierService
                .findAll(principal.ownerId(), "", Pageable.unpaged()).getContent();
        List<SupplierResponse> linkedSuppliers = supplierOptions.stream()
                .filter(supplier -> supplierService.findLinkedProductIds(principal.ownerId(), supplier.getId())
                        .contains(productId))
                .toList();
        Map<Long, String> branchNames = new HashMap<>();
        branchService.findAll(principal.ownerId(), "", Pageable.unpaged()).getContent()
                .forEach(branch -> branchNames.put(branch.id(), branch.name()));
        Map<Long, String> taxNames = new HashMap<>();
        taxRateService.findAll(principal.ownerId())
                .forEach(tax -> taxNames.put(tax.id(), tax.name() + " — " + tax.rate() + "%"));
        Map<Long, BranchProductStockResponse> stockByVariant = inventoryQueryService
                .findBranchStocks(product.businessId(), setup(principal).branchId()).stream()
                .collect(java.util.stream.Collectors.toMap(BranchProductStockResponse::getProductVariantId,
                        java.util.function.Function.identity(), (left, right) -> left));
        List<ProductBatchResponse> productBatches = variants.stream()
                .flatMap(variant -> inventoryQueryService.findBatches(product.businessId(), setup(principal).branchId(), variant.id()).stream())
                .toList();
        Map<Long, com.spark.falcon.purchase.dto.ProductPurchaseTraceResponse> purchaseTraces = purchaseProductReadService
                .findTraces(product.businessId(), productBatches.stream().map(ProductBatchResponse::getSourcePurchaseItemId)
                        .filter(java.util.Objects::nonNull).toList());
        Map<Long, String> supplierNames = supplierOptions.stream()
                .collect(java.util.stream.Collectors.toMap(SupplierResponse::getId, SupplierResponse::getName, (left, right) -> left));
        List<ProductBatchRow> batchRows = productBatches.stream().map(batch -> {
            var trace = purchaseTraces.get(batch.getSourcePurchaseItemId());
            return new ProductBatchRow(batch, supplierNames.getOrDefault(batch.getSupplierId(), "—"),
                    trace == null ? null : trace.purchaseDate(), branchNames.getOrDefault(batch.getBranchId(), String.valueOf(batch.getBranchId())));
        }).toList();
        java.math.BigDecimal totalStock = stockByVariant.values().stream().map(BranchProductStockResponse::getAvailableBaseQuantity)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal inventoryValue = stockByVariant.values().stream().map(BranchProductStockResponse::getInventoryValue)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal weightedAverageCost = totalStock.signum() == 0 ? java.math.BigDecimal.ZERO
                : inventoryValue.divide(totalStock, 4, java.math.RoundingMode.HALF_UP);

        model.addAttribute("setup", setup(principal)); model.addAttribute("activePage", "product-list");
        model.addAttribute("product", product); model.addAttribute("variants", variants);
        if (!model.containsAttribute("variantRequest")) {
            ProductVariantRequest variantRequest = new ProductVariantRequest();
            if (addVariant) {
                variantRequest.setSku(ProductSkuDraftSession.getOrCreateForProductVariant(
                        session,
                        productId,
                        variantDraftId,
                        () -> productSkuService.reserveNext(principal.ownerId())));
            }
            model.addAttribute("variantRequest", variantRequest);
        }
        model.addAttribute("openAddVariantModal", addVariant);
        model.addAttribute("variantDraftId", variantDraftId);
        model.addAttribute("newVariantDraftId", UUID.randomUUID().toString());
        if (!model.containsAttribute("barcodeRequest")) model.addAttribute("barcodeRequest", new ProductBarcodeRequest());
        if (!model.containsAttribute("conversionRequest")) model.addAttribute("conversionRequest", new ProductUnitConversionRequest());
        var units = unitService.findAll(principal.ownerId());
        List<UnitResponse> variantUnits = compatibleVariantUnits(units, product.branchIds());
        Map<Long, String> unitNames = new HashMap<>();
        units.forEach(unit -> unitNames.put(unit.id(), unit.name() + " (" + unit.code() + ")"));
        Map<Long, List<com.spark.falcon.product.dto.response.ProductBarcodeResponse>> barcodeMap = new HashMap<>();
        Map<Long, List<com.spark.falcon.product.dto.response.ProductUnitConversionResponse>> conversionMap = new HashMap<>();
        for (ProductVariantResponse variant : variants) {
            barcodeMap.put(variant.id(), barcodeService.findAll(principal.ownerId(), productId, variant.id()));
            conversionMap.put(variant.id(), conversionService.findAll(principal.ownerId(), productId, variant.id()));
        }
        model.addAttribute("units", units);
        model.addAttribute("variantUnits", variantUnits);
        model.addAttribute("unitNames", unitNames);
        model.addAttribute("barcodeStocks", inventoryQueryService.findBranchStocks(product.businessId(), setup(principal).branchId()).stream()
                .collect(java.util.stream.Collectors.toMap(BranchProductStockResponse::getProductVariantId,
                        BranchProductStockResponse::getAvailableBaseQuantity, (left, right) -> left)));
        model.addAttribute("barcodeMap", barcodeMap);
        model.addAttribute("conversionMap", conversionMap);
        model.addAttribute("linkedSuppliers", linkedSuppliers);
        model.addAttribute("supplierOptions", supplierOptions.stream()
                .filter(supplier -> linkedSuppliers.stream().noneMatch(linked -> linked.getId().equals(supplier.getId())))
                .toList());
        model.addAttribute("branchNames", branchNames);
        model.addAttribute("taxNames", taxNames);
        model.addAttribute("stockByVariant", stockByVariant);
        model.addAttribute("totalStock", totalStock);
        model.addAttribute("weightedAverageCost", weightedAverageCost);
        model.addAttribute("batchRows", batchRows);
        return "product/product-details";
    }

    @GetMapping("/{productId}/barcode-generator")
    public String barcodeGenerator(@PathVariable Long productId,
                                   @AuthenticationPrincipal OwnerPrincipal principal,
                                   Model model) {
        ProductResponse product = productService.findByOwnerAndId(principal.ownerId(), productId)
                .orElseThrow(com.spark.falcon.product.exception.ProductNotFoundException::new);
        List<ProductVariantResponse> variants = variantService.findAll(principal.ownerId(), productId);
        Map<Long, List<com.spark.falcon.product.dto.response.ProductBarcodeResponse>> barcodeMap = new HashMap<>();
        variants.forEach(variant -> barcodeMap.put(variant.id(),
                barcodeService.findAll(principal.ownerId(), productId, variant.id())));
        Map<Long, String> unitNames = new HashMap<>();
        unitService.findAll(principal.ownerId()).forEach(unit ->
                unitNames.put(unit.id(), unit.name() + " (" + unit.code() + ")"));

        BusinessSetupResponse setup = setup(principal);
        model.addAttribute("setup", setup);
        model.addAttribute("activePage", "product-list");
        model.addAttribute("product", product);
        model.addAttribute("variants", variants);
        model.addAttribute("barcodeMap", barcodeMap);
        model.addAttribute("unitNames", unitNames);
        model.addAttribute("barcodeStocks", inventoryQueryService.findBranchStocks(product.businessId(), setup(principal).branchId()).stream()
                .collect(java.util.stream.Collectors.toMap(BranchProductStockResponse::getProductVariantId,
                        BranchProductStockResponse::getAvailableBaseQuantity, (left, right) -> left)));
        List<ProductResponse> productOptions = productService.findAll(principal.ownerId()).stream()
                .filter(option -> !option.archived() && option.status() == ProductStatus.ACTIVE).toList();
        Map<Long, List<ProductVariantResponse>> allVariants = variantService.findAllForProducts(principal.ownerId(),
                productOptions.stream().map(ProductResponse::id).toList());
        Map<Long, java.math.BigDecimal> allStocks = inventoryQueryService.findBranchStocks(product.businessId(), setup.branchId()).stream()
                .collect(java.util.stream.Collectors.toMap(BranchProductStockResponse::getProductVariantId,
                        BranchProductStockResponse::getAvailableBaseQuantity, (left, right) -> left));
        Map<Long, String> categoryNames = categoryService.findAll(principal.ownerId()).stream()
                .collect(java.util.stream.Collectors.toMap(com.spark.falcon.product.dto.response.CategoryResponse::id,
                        com.spark.falcon.product.dto.response.CategoryResponse::name));
        String currency = branchService.findOwnedById(principal.ownerId(), setup.branchId()).currency();
        List<BarcodeLabelOption> labelOptions = new ArrayList<>();
        for (ProductResponse optionProduct : productOptions) {
            for (ProductVariantResponse optionVariant : allVariants.getOrDefault(optionProduct.id(), List.of())) {
                for (var barcode : barcodeService.findAll(principal.ownerId(), optionProduct.id(), optionVariant.id())) {
                    if (!barcode.active()) continue;
                    labelOptions.add(new BarcodeLabelOption(optionProduct.id(), optionProduct.name(), optionProduct.referenceCode(),
                            optionProduct.thumbnailReference(), optionVariant.id(), optionVariant.variantName(), optionVariant.sku(),
                            barcode.barcode(), optionVariant.sellingPrice(), currency,
                            categoryNames.getOrDefault(optionProduct.categoryId(), ""),
                            unitNames.getOrDefault(barcode.unitId(), String.valueOf(barcode.unitId())),
                            allStocks.getOrDefault(optionVariant.id(), java.math.BigDecimal.ZERO)));
                }
            }
        }
        model.addAttribute("productOptions", productOptions);
        model.addAttribute("labelOptions", labelOptions);
        return "product/barcode-generator";
    }

    @GetMapping("/{productId}/edit")
    public String editProduct(@PathVariable Long productId, @AuthenticationPrincipal OwnerPrincipal principal,
                              Model model) {
        var product = productService.findByOwnerAndId(principal.ownerId(), productId)
                .orElseThrow(com.spark.falcon.product.exception.ProductNotFoundException::new);
        ProductRequest request = model.containsAttribute("productRequest")
                ? (ProductRequest) model.getAttribute("productRequest") : request(product);
        model.addAttribute("setup", setup(principal)); model.addAttribute("activePage", "product-list");
        model.addAttribute("product", product); model.addAttribute("productRequest", request);
        model.addAttribute("branches", branchService.findAll(principal.ownerId(), "", Pageable.unpaged()).getContent());
        model.addAttribute("categories", categoryService.findAll(principal.ownerId()).stream().filter(c -> !c.archived()).toList());
        model.addAttribute("units", unitService.findAll(principal.ownerId()));
        model.addAttribute("taxRates", taxRateService.findAll(principal.ownerId()));
        model.addAttribute("suppliers", supplierService.findAll(principal.ownerId(), "", Pageable.unpaged()).getContent());
        List<ProductVariantResponse> editVariants = variantService.findAll(principal.ownerId(), productId);
        List<SupplierResponse> linkedSuppliers = supplierService.findLinkedSuppliersByProductIds(principal.ownerId(), List.of(productId))
                .getOrDefault(productId, List.of());
        model.addAttribute("editVariants", editVariants);
        model.addAttribute("linkedSuppliers", linkedSuppliers);
        model.addAttribute("selectedSupplierIds", linkedSuppliers.stream().map(SupplierResponse::getId).toList());
        return "product/edit-product";
    }

    @GetMapping("/stock-alert")
    public String stockAlertPage(@AuthenticationPrincipal OwnerPrincipal principal,
                                 @RequestParam(required = false) String q,
                                 @RequestParam(defaultValue = "stock") String sort,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size,
                                 Model model) {
        BusinessSetupResponse setup = setup(principal);
        List<StockAlertRow> allRows = stockAlertRows(principal.ownerId(), setup);
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<StockAlertRow> filtered = allRows.stream().filter(row -> query.isEmpty()
                || row.productName().toLowerCase(Locale.ROOT).contains(query)
                || row.supplierName().toLowerCase(Locale.ROOT).contains(query))
                .sorted(stockAlertComparator(sort)).toList();
        int safeSize = Set.of(10, 25, 50, 100).contains(size) ? size : 10;
        int from = Math.min(Math.max(0, page) * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        model.addAttribute("setup", setup);
        model.addAttribute("activePage", "stock-alert");
        model.addAttribute("rows", filtered.subList(from, to));
        model.addAttribute("query", q == null ? "" : q.trim());
        model.addAttribute("sort", sort);
        model.addAttribute("page", Math.max(0, page)); model.addAttribute("size", safeSize);
        model.addAttribute("totalRows", filtered.size());
        model.addAttribute("totalPages", Math.max(1, (filtered.size() + safeSize - 1) / safeSize));
        return "product/stock-alert";
    }

    @GetMapping("/stock-alert/export")
    public void exportStockAlert(@RequestParam String format, @RequestParam(required = false) String q,
                                 @RequestParam(defaultValue = "stock") String sort,
                                 @AuthenticationPrincipal OwnerPrincipal principal,
                                 HttpServletResponse response) throws java.io.IOException {
        BusinessSetupResponse setup = setup(principal);
        ExportDocument document = new ExportDocument("Stock Alert", setup.businessName(), setup.branchName(),
                ZoneId.of(branchService.findOwnedById(principal.ownerId(), setup.branchId()).timeZone()), Map.of(),
                List.of(ExportColumn.number("Product ID"), ExportColumn.text("Product / Variant"),
                        ExportColumn.text("Supplier"), ExportColumn.text("Supplier Mobile"),
                        ExportColumn.money("Purchase Price"), ExportColumn.number("Current Stock"),
                        ExportColumn.number("Reorder Level")),
                consumer -> {
                    List<StockAlertRow> rows = stockAlertRows(principal.ownerId(), setup).stream()
                            .filter(row -> q == null || q.isBlank()
                                    || row.productName().toLowerCase(Locale.ROOT).contains(q.trim().toLowerCase(Locale.ROOT))
                                    || row.supplierName().toLowerCase(Locale.ROOT).contains(q.trim().toLowerCase(Locale.ROOT)))
                            .sorted(stockAlertComparator(sort))
                            .toList();
                    for (StockAlertRow row : rows) {
                        consumer.accept(row.productId(), row.productName(), row.supplierName(), row.supplierMobile(),
                                row.purchasePrice(), row.currentStock(), row.reorderLevel());
                    }
                });
        exportResponse.write(format, document, response);
    }

    @GetMapping("/expiry-alert")
    public String expiryAlertPage(@AuthenticationPrincipal OwnerPrincipal principal,
                                  @RequestParam(defaultValue = "30") String window,
                                  @RequestParam(required = false) String q,
                                  Model model) {
        BusinessSetupResponse setup = setup(principal);
        List<ExpiryAlertRow> rows = expiryAlertRows(principal.ownerId(), setup, window, q);
        model.addAttribute("setup", setup);
        model.addAttribute("activePage", "expiry-alert");
        model.addAttribute("rows", rows); model.addAttribute("window", window);
        model.addAttribute("query", q == null ? "" : q.trim());
        return "product/expiry-alert";
    }

    @GetMapping("/expiry-alert/export")
    public void exportExpiryAlert(@RequestParam String format, @RequestParam(defaultValue = "30") String window,
                                  @RequestParam(required = false) String q,
                                  @AuthenticationPrincipal OwnerPrincipal principal,
                                  HttpServletResponse response) throws java.io.IOException {
        BusinessSetupResponse setup = setup(principal);
        ExportDocument document = new ExportDocument("Expiry Alert", setup.businessName(), setup.branchName(),
                ZoneId.of(branchService.findOwnedById(principal.ownerId(), setup.branchId()).timeZone()), Map.of("Window", window),
                List.of(ExportColumn.text("Product"), ExportColumn.text("Code / SKU"), ExportColumn.text("Batch"),
                        ExportColumn.text("Supplier"), ExportColumn.text("Branch"), ExportColumn.date("Purchase Date"),
                        ExportColumn.date("Expiry Date"), ExportColumn.number("Days Remaining"),
                        ExportColumn.number("Available Qty"), ExportColumn.money("Unit Cost"),
                        ExportColumn.money("Stock Value"), ExportColumn.text("Status")),
                consumer -> {
                    for (ExpiryAlertRow row : expiryAlertRows(principal.ownerId(), setup, window, q)) {
                        consumer.accept(row.productName(), row.code(), row.batchNumber(), row.supplierName(), row.branchName(),
                                row.purchaseDate(), row.expiryDate(), row.daysRemaining(), row.availableQuantity(), row.unitCost(),
                                row.totalValue(), row.status());
                    }
                });
        exportResponse.write(format, document, response);
    }

    @PostMapping
    public String createProduct(@Valid @ModelAttribute("productRequest") ProductRequest request,
                                BindingResult productBindingResult,
                                @Valid @ModelAttribute("variantRequest") ProductVariantRequest variantRequest,
                                BindingResult variantBindingResult,
                                @RequestParam(required = false) Long supplierId,
                                @RequestParam(required = false) String returnTo,
                                @RequestParam(required = false) Long returnBranchId,
                                @RequestParam(required = false) String draftId,
                                @RequestParam(defaultValue = "ACTIVE") ProductStatus variantStatus,
                                @RequestParam(required = false) MultipartFile primaryImageFile,
                                @RequestParam(required = false) List<MultipartFile> additionalImageFiles,
                                @AuthenticationPrincipal OwnerPrincipal principal,
                                HttpSession session,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        variantRequest.setStatus(variantStatus);
        if (productBindingResult.hasErrors() || variantBindingResult.hasErrors()) {
            model.addAttribute("errorMessage", "Please review the highlighted fields and try again.");
            return newProductPage(supplierId, returnTo, returnBranchId, draftId, principal, session, model);
        }
        List<String> storedReferences = new ArrayList<>();
        ProductResponse product;
        try {
            applyImageUploads(request, primaryImageFile, additionalImageFiles, storedReferences);
            product = productCreationService.create(principal.ownerId(), request, variantRequest, supplierId);
        } catch (ProductConfigurationConflictException | com.spark.falcon.product.exception.ProductImageStorageException exception) {
            storedReferences.forEach(imageStorageService::deleteNewReference);
            model.addAttribute("errorMessage", exception.getMessage());
            return newProductPage(supplierId, returnTo, returnBranchId, draftId, principal, session, model);
        } catch (IllegalArgumentException exception) {
            storedReferences.forEach(imageStorageService::deleteNewReference);
            model.addAttribute("errorMessage", exception.getMessage());
            return newProductPage(supplierId, returnTo, returnBranchId, draftId, principal, session, model);
        } catch (RuntimeException exception) {
            storedReferences.forEach(imageStorageService::deleteNewReference);
            throw exception;
        }
        ProductReferenceDraftSession.remove(session, draftId);
        ProductSkuDraftSession.removeForNewProduct(session, draftId);
        redirectAttributes.addFlashAttribute("successMessage", "Product created successfully.");
        if ("purchase".equalsIgnoreCase(returnTo)) {
            String branchQuery = returnBranchId == null ? "" : "?returnBranchId=" + returnBranchId;
            return "redirect:/owner/products/" + product.id() + "/purchase-created" + branchQuery;
        }
        return "redirect:/owner/products/" + product.id();
    }

    @GetMapping("/{productId}/purchase-created")
    public String purchaseCreatedCallback(@PathVariable Long productId,
                                          @RequestParam(required = false) Long returnBranchId,
                                          @AuthenticationPrincipal OwnerPrincipal principal,
                                          Model model) {
        ProductResponse product = productService.findByOwnerAndId(principal.ownerId(), productId)
                .orElseThrow(ProductAccessDeniedException::new);
        ProductVariantResponse variant = variantService.findAll(principal.ownerId(), productId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Created product does not have an initial Product Variant"));
        Map<Long, UnitResponse> units = unitService.findAll(principal.ownerId()).stream()
                .collect(java.util.stream.Collectors.toMap(UnitResponse::id, java.util.function.Function.identity()));
        Long purchaseUnitId = variant.purchaseUnitId() != null ? variant.purchaseUnitId() : variant.baseInventoryUnitId();
        TaxRateResponse taxRate = product.taxRateId() == null ? null : taxRateService.findAll(principal.ownerId()).stream()
                .filter(value -> value.id().equals(product.taxRateId()))
                .findFirst().orElse(null);
        model.addAttribute("product", product);
        model.addAttribute("variant", variant);
        model.addAttribute("taxRate", taxRate);
        model.addAttribute("purchaseUnitId", purchaseUnitId);
        model.addAttribute("purchaseUnitName", units.containsKey(purchaseUnitId) ? units.get(purchaseUnitId).name() : "Unit #" + purchaseUnitId);
        model.addAttribute("baseUnitName", units.containsKey(variant.baseInventoryUnitId()) ? units.get(variant.baseInventoryUnitId()).name() : "Unit #" + variant.baseInventoryUnitId());
        model.addAttribute("returnBranchAssigned", returnBranchId == null || product.branchIds().contains(returnBranchId));
        return "product/purchase-product-created";
    }

    @PostMapping("/{productId}")
    public String updateProduct(@PathVariable Long productId,
                                @Valid @ModelAttribute ProductRequest request,
                                BindingResult bindingResult,
                                @RequestParam(required = false) MultipartFile primaryImageFile,
                                @RequestParam(required = false) List<MultipartFile> additionalImageFiles,
                                @AuthenticationPrincipal OwnerPrincipal principal,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) return validationFailure(bindingResult, redirectAttributes,
                "/owner/products/" + productId + "/edit");
        List<String> storedReferences = new ArrayList<>();
        ProductResponse existingProduct = productService.findByOwnerAndId(principal.ownerId(), productId)
                .orElseThrow(com.spark.falcon.product.exception.ProductNotFoundException::new);
        try {
            applyImageUploads(request, primaryImageFile, additionalImageFiles, storedReferences);
            productService.update(mapper.toUpdateCommand(principal.ownerId(), productId, request));
            Set<String> retainedReferences = new java.util.HashSet<>();
            if (request.getThumbnailReference() != null) retainedReferences.add(request.getThumbnailReference());
            request.getImages().stream().map(ProductImageRequest::getImageReference).filter(java.util.Objects::nonNull)
                    .forEach(retainedReferences::add);
            List<String> previousReferences = new ArrayList<>();
            if (existingProduct.thumbnailReference() != null) previousReferences.add(existingProduct.thumbnailReference());
            existingProduct.images().stream().map(com.spark.falcon.product.dto.response.ProductImageResponse::imageReference)
                    .forEach(previousReferences::add);
            previousReferences.stream().filter(reference -> !retainedReferences.contains(reference))
                    .forEach(imageStorageService::deleteNewReference);
        } catch (com.spark.falcon.product.exception.ProductImageStorageException exception) {
            storedReferences.forEach(imageStorageService::deleteNewReference);
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/owner/products/" + productId + "/edit";
        } catch (IllegalArgumentException exception) {
            storedReferences.forEach(imageStorageService::deleteNewReference);
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/owner/products/" + productId + "/edit";
        } catch (RuntimeException exception) {
            storedReferences.forEach(imageStorageService::deleteNewReference);
            throw exception;
        }
        redirectAttributes.addFlashAttribute("successMessage", "Product updated successfully.");
        return "redirect:/owner/products/" + productId;
    }

    @PostMapping("/{productId}/status")
    public String productStatus(@PathVariable Long productId,
                                @RequestParam ProductStatus status,
                                @AuthenticationPrincipal OwnerPrincipal principal,
                                RedirectAttributes redirectAttributes) {
        productService.changeStatus(principal.ownerId(), productId, status);
        redirectAttributes.addFlashAttribute("successMessage", "Product status updated.");
        return "redirect:/owner/products/" + productId;
    }

    @PostMapping("/{productId}/archive")
    public String archiveProduct(@PathVariable Long productId,
                                 @AuthenticationPrincipal OwnerPrincipal principal,
                                 RedirectAttributes redirectAttributes) {
        productService.archive(principal.ownerId(), productId);
        redirectAttributes.addFlashAttribute("successMessage", "Product archived.");
        return "redirect:/owner/products";
    }

    @PostMapping("/{productId}/restore")
    public String restoreProduct(@PathVariable Long productId,
                                 @AuthenticationPrincipal OwnerPrincipal principal,
                                 RedirectAttributes redirectAttributes) {
        productService.restore(principal.ownerId(), productId);
        redirectAttributes.addFlashAttribute("successMessage", "Product restored.");
        return "redirect:/owner/products";
    }

    @PostMapping("/{productId}/suppliers")
    public String linkSupplier(@PathVariable Long productId,
                               @RequestParam Long supplierId,
                               @AuthenticationPrincipal OwnerPrincipal principal,
                               RedirectAttributes redirectAttributes) {
        supplierService.assignProduct(principal.ownerId(), supplierId, productId);
        redirectAttributes.addFlashAttribute("successMessage", "Supplier linked to product.");
        return "redirect:/owner/products/" + productId;
    }

    @PostMapping("/bulk")
    public String bulkProducts(@RequestParam String action, @RequestParam List<Long> productIds,
                               @AuthenticationPrincipal OwnerPrincipal principal,
                               RedirectAttributes redirectAttributes) {
        if ("ARCHIVE".equalsIgnoreCase(action)) productService.bulkArchive(principal.ownerId(), productIds);
        else if ("RESTORE".equalsIgnoreCase(action)) productService.bulkRestore(principal.ownerId(), productIds);
        else throw new IllegalArgumentException("Unsupported product bulk action");
        redirectAttributes.addFlashAttribute("successMessage", productIds.size() + " product record(s) updated.");
        return "redirect:/owner/products";
    }

    @PostMapping("/{productId}/suppliers/{supplierId}/remove")
    public String unlinkSupplier(@PathVariable Long productId,
                                 @PathVariable Long supplierId,
                                 @AuthenticationPrincipal OwnerPrincipal principal,
                                 RedirectAttributes redirectAttributes) {
        supplierService.removeProduct(principal.ownerId(), supplierId, productId);
        redirectAttributes.addFlashAttribute("successMessage", "Supplier link removed.");
        return "redirect:/owner/products/" + productId;
    }

    @PostMapping("/{productId}/variants")
    public String createVariant(@PathVariable Long productId,
                                @Valid @ModelAttribute("variantRequest") ProductVariantRequest request,
                                BindingResult bindingResult,
                                @RequestParam(required = false) String variantDraftId,
                                @AuthenticationPrincipal OwnerPrincipal principal,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors())
            return validationFailure(bindingResult, redirectAttributes,
                    variantDraftPath(productId, variantDraftId));
        try {
            variantService.create(mapper.toCreateVariantCommand(principal.ownerId(), productId, request));
        } catch (ProductConfigurationConflictException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("variantRequest", request);
            return "redirect:" + variantDraftPath(productId, variantDraftId);
        }
        ProductSkuDraftSession.removeForProductVariant(session, productId, variantDraftId);
        redirectAttributes.addFlashAttribute("successMessage", "Product variant created successfully.");
        return "redirect:/owner/products/" + productId;
    }

    @PostMapping("/{productId}/variants/{variantId}")
    public String updateVariant(@PathVariable Long productId,
                                @PathVariable Long variantId,
                                @Valid @ModelAttribute("variantRequest") ProductVariantRequest request,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal OwnerPrincipal principal,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors())
            return validationFailure(bindingResult, redirectAttributes, "/owner/products/" + productId);
        try {
            variantService.update(mapper.toUpdateVariantCommand(principal.ownerId(), productId, variantId, request));
        } catch (ProductConfigurationConflictException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/owner/products/" + productId;
        }
        redirectAttributes.addFlashAttribute("successMessage", "Product variant updated successfully.");
        return "redirect:/owner/products/" + productId;
    }

    @PostMapping("/{productId}/variants/{variantId}/status")
    public String variantStatus(@PathVariable Long productId,
                                @PathVariable Long variantId,
                                @RequestParam ProductStatus status,
                                @AuthenticationPrincipal OwnerPrincipal principal,
                                RedirectAttributes redirectAttributes) {
        variantService.changeStatus(principal.ownerId(), productId, variantId, status);
        redirectAttributes.addFlashAttribute("successMessage", "Product variant status updated.");
        return "redirect:/owner/products/" + productId;
    }

    @PostMapping("/{productId}/variants/{variantId}/barcodes")
    public String createBarcode(@PathVariable Long productId,
                                @PathVariable Long variantId,
                                @Valid @ModelAttribute("barcodeRequest") ProductBarcodeRequest request,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal OwnerPrincipal principal,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) return validationFailure(bindingResult, redirectAttributes,
                "/owner/products/" + productId);
        barcodeService.create(mapper.toCreateBarcodeCommand(principal.ownerId(), productId, variantId, request));
        redirectAttributes.addFlashAttribute("successMessage", "Product barcode created successfully.");
        return "redirect:/owner/products/" + productId;
    }

    @PostMapping("/{productId}/variants/{variantId}/barcodes/{barcodeId}/deactivate")
    public String deactivateBarcode(@PathVariable Long productId,
                                    @PathVariable Long variantId,
                                    @PathVariable Long barcodeId,
                                    @AuthenticationPrincipal OwnerPrincipal principal,
                                    RedirectAttributes redirectAttributes) {
        barcodeService.deactivate(principal.ownerId(), productId, variantId, barcodeId);
        redirectAttributes.addFlashAttribute("successMessage", "Product barcode deactivated.");
        return "redirect:/owner/products/" + productId;
    }

    @PostMapping("/{productId}/variants/{variantId}/conversions")
    public String createConversion(@PathVariable Long productId,
                                   @PathVariable Long variantId,
                                   @Valid @ModelAttribute("conversionRequest") ProductUnitConversionRequest request,
                                   BindingResult bindingResult,
                                   @AuthenticationPrincipal OwnerPrincipal principal,
                                   RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) return validationFailure(bindingResult, redirectAttributes,
                "/owner/products/" + productId);
        conversionService.create(mapper.toCreateConversionCommand(principal.ownerId(), productId, variantId, request));
        redirectAttributes.addFlashAttribute("successMessage", "Product unit conversion created successfully.");
        return "redirect:/owner/products/" + productId;
    }

    @PostMapping("/{productId}/variants/{variantId}/conversions/{conversionId}/deactivate")
    public String deactivateConversion(@PathVariable Long productId,
                                       @PathVariable Long variantId,
                                       @PathVariable Long conversionId,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveTo,
                                       @AuthenticationPrincipal OwnerPrincipal principal,
                                       RedirectAttributes redirectAttributes) {
        conversionService.deactivate(principal.ownerId(), productId, variantId, conversionId, effectiveTo);
        redirectAttributes.addFlashAttribute("successMessage", "Product unit conversion deactivated.");
        return dashboard();
    }

    private String redirectToVariantDraft(Long productId) {
        return "redirect:" + variantDraftPath(productId, UUID.randomUUID().toString());
    }

    private String variantDraftPath(Long productId, String variantDraftId) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath("/owner/products/{productId}")
                .queryParam("addVariant", true)
                .queryParam("variantDraftId", variantDraftId == null || variantDraftId.isBlank()
                        ? UUID.randomUUID().toString()
                        : variantDraftId);
        return builder.buildAndExpand(productId).encode().toUriString();
    }

    private String redirectToNewProductDraft(Long supplierId, String returnTo, Long returnBranchId) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath("/owner/products/new")
                .queryParam("draftId", UUID.randomUUID().toString());
        if (supplierId != null) {
            builder.queryParam("supplierId", supplierId);
        }
        if ("purchase".equalsIgnoreCase(returnTo)) {
            builder.queryParam("returnTo", "purchase");
            if (returnBranchId != null) {
                builder.queryParam("returnBranchId", returnBranchId);
            }
        }
        return "redirect:" + builder.build().encode().toUriString();
    }

    private BusinessSetupResponse setup(OwnerPrincipal principal) {
        return branchContextService.resolveOwnerSetup(principal.ownerId());
    }

    private String productSearchText(ProductResponse product,
                                     List<ProductVariantResponse> variants,
                                     List<SupplierResponse> suppliers) {
        StringBuilder text = new StringBuilder()
                .append(product.name()).append(' ')
                .append(product.referenceCode()).append(' ');

        for (ProductVariantResponse variant : variants) {
            text.append(variant.variantName()).append(' ').append(variant.sku()).append(' ');
        }
        for (SupplierResponse supplier : suppliers) {
            text.append(supplier.getName()).append(' ');
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private List<StockAlertRow> stockAlertRows(Long ownerId, BusinessSetupResponse setup) {
        List<ProductResponse> products = productService.findAll(ownerId).stream()
                .filter(product -> !product.archived() && product.status() == ProductStatus.ACTIVE).toList();
        Map<Long, ProductResponse> productsById = products.stream()
                .collect(java.util.stream.Collectors.toMap(ProductResponse::id, java.util.function.Function.identity()));
        Map<Long, List<ProductVariantResponse>> variants = variantService.findAllForProducts(ownerId, productsById.keySet().stream().toList());
        Map<Long, List<SupplierResponse>> suppliers = supplierService.findLinkedSuppliersByProductIds(ownerId, productsById.keySet().stream().toList());
        Map<Long, BranchProductStockResponse> stocks = inventoryQueryService.findBranchStocks(setup.businessId(), setup.branchId()).stream()
                .collect(java.util.stream.Collectors.toMap(BranchProductStockResponse::getProductVariantId,
                        java.util.function.Function.identity(), (left, right) -> left));
        Map<Long, List<ProductBatchResponse>> batches = inventoryQueryService.findBranchBatches(setup.businessId(), setup.branchId()).stream()
                .collect(java.util.stream.Collectors.groupingBy(ProductBatchResponse::getProductVariantId));
        List<StockAlertRow> rows = new ArrayList<>();
        for (ProductResponse product : products) {
            SupplierResponse supplier = suppliers.getOrDefault(product.id(), List.of()).stream().findFirst().orElse(null);
            for (ProductVariantResponse variant : variants.getOrDefault(product.id(), List.of())) {
                BranchProductStockResponse stock = stocks.get(variant.id());
                java.math.BigDecimal available = stock == null ? java.math.BigDecimal.ZERO : stock.getAvailableBaseQuantity();
                if (available.compareTo(variant.reorderLevel()) > 0) continue;
                java.math.BigDecimal purchasePrice = batches.getOrDefault(variant.id(), List.of()).stream()
                        .max(java.util.Comparator.comparing(ProductBatchResponse::getCreatedAt))
                        .map(ProductBatchResponse::getOriginalPurchaseUnitCost).orElse(null);
                rows.add(new StockAlertRow(product.id(), variant.id(), product.name() + " — " + variant.variantName(),
                        supplier == null ? "—" : supplier.getName(), supplier == null ? "—" : supplier.getMobileNumber(),
                        supplier == null ? null : supplier.getId(), purchasePrice, available, variant.reorderLevel()));
            }
        }
        return rows.stream().sorted(java.util.Comparator.comparing(StockAlertRow::currentStock)
                .thenComparing(StockAlertRow::productName)).toList();
    }

    private java.util.Comparator<StockAlertRow> stockAlertComparator(String sort) {
        return switch (sort == null ? "stock" : sort.toLowerCase(Locale.ROOT)) {
            case "name" -> java.util.Comparator.comparing(StockAlertRow::productName, String.CASE_INSENSITIVE_ORDER);
            case "supplier" -> java.util.Comparator.comparing(StockAlertRow::supplierName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(StockAlertRow::productName);
            default -> java.util.Comparator.comparing(StockAlertRow::currentStock).thenComparing(StockAlertRow::productName);
        };
    }

    private List<ExpiryAlertRow> expiryAlertRows(Long ownerId, BusinessSetupResponse setup, String window, String q) {
        List<ProductResponse> products = productService.findAll(ownerId).stream().filter(ProductResponse::trackExpiry).toList();
        Map<Long, ProductResponse> productById = products.stream()
                .collect(java.util.stream.Collectors.toMap(ProductResponse::id, java.util.function.Function.identity()));
        Map<Long, ProductVariantResponse> variantById = variantService.findAllForProducts(ownerId, productById.keySet().stream().toList())
                .values().stream().flatMap(List::stream)
                .collect(java.util.stream.Collectors.toMap(ProductVariantResponse::id, java.util.function.Function.identity()));
        Map<Long, Long> productIdByVariant = variantById.values().stream()
                .collect(java.util.stream.Collectors.toMap(ProductVariantResponse::id, ProductVariantResponse::productId));
        Map<Long, List<SupplierResponse>> suppliers = supplierService.findLinkedSuppliersByProductIds(ownerId, productById.keySet().stream().toList());
        LocalDate today = LocalDate.now(ZoneId.of(branchService.findOwnedById(ownerId, setup.branchId()).timeZone()));
        int days = switch (window.toUpperCase(Locale.ROOT)) {
            case "TODAY" -> 0; case "7" -> 7; case "15" -> 15; case "30" -> 30; case "60" -> 60; case "90" -> 90;
            default -> 30;
        };
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<ExpiryAlertRow> rows = new ArrayList<>();
        for (ProductBatchResponse batch : inventoryQueryService.findBranchBatches(setup.businessId(), setup.branchId())) {
            ProductVariantResponse variant = variantById.get(batch.getProductVariantId());
            ProductResponse product = productById.get(productIdByVariant.get(batch.getProductVariantId()));
            if (variant == null || product == null || batch.getAvailableBaseQuantity().signum() <= 0) continue;
            LocalDate expiry = batch.getExpiryDate();
            boolean matches = switch (window.toUpperCase(Locale.ROOT)) {
                case "EXPIRED" -> expiry != null && expiry.isBefore(today);
                case "MISSING" -> expiry == null;
                default -> expiry != null && !expiry.isBefore(today) && !expiry.isAfter(today.plusDays(days));
            };
            if (!matches) continue;
            SupplierResponse supplier = suppliers.getOrDefault(product.id(), List.of()).stream()
                    .filter(value -> value.getId().equals(batch.getSupplierId())).findFirst().orElse(null);
            String text = (product.name() + " " + product.referenceCode() + " " + variant.sku() + " " + batch.getBatchNumber()).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !text.contains(query)) continue;
            long daysRemaining = expiry == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(today, expiry);
            java.math.BigDecimal value = batch.getAvailableBaseQuantity().multiply(batch.getAllocatedLandedUnitCost());
            rows.add(new ExpiryAlertRow(product.id(), batch.getId(), product.name() + " — " + variant.variantName(),
                    product.referenceCode() + " / " + variant.sku(), batch.getBatchNumber(), supplier == null ? "—" : supplier.getName(),
                    setup.branchName(), batch.getCreatedAt().atZone(ZoneId.of(branchService.findOwnedById(ownerId, setup.branchId()).timeZone())).toLocalDate(),
                    expiry, daysRemaining, batch.getAvailableBaseQuantity(), batch.getAllocatedLandedUnitCost(), value,
                    expiry == null ? "MISSING" : (expiry.isBefore(today) ? "EXPIRED" : "EXPIRING")));
        }
        return rows.stream().sorted(java.util.Comparator.comparing(ExpiryAlertRow::expiryDate,
                java.util.Comparator.nullsLast(LocalDate::compareTo))).toList();
    }

    private List<UnitResponse> compatibleVariantUnits(List<UnitResponse> units, Set<Long> productBranchIds) {
        if (units == null || productBranchIds == null || productBranchIds.isEmpty()) return List.of();
        return units.stream()
                .filter(unit -> !unit.archived())
                .filter(unit -> unit.status() == ConfigurationStatus.ACTIVE)
                .filter(unit -> unit.branchIds() != null && unit.branchIds().containsAll(productBranchIds))
                .toList();
    }

    private void applyImageUploads(ProductRequest request, MultipartFile primaryImageFile,
                                   List<MultipartFile> additionalImageFiles, List<String> storedReferences) {
        String primaryReference = imageStorageService.store(primaryImageFile);
        if (primaryReference != null) {
            storedReferences.add(primaryReference);
            request.setThumbnailReference(primaryReference);
            request.getImages().forEach(image -> image.setPrimaryImage(false));
        }
        if (additionalImageFiles == null) return;
        for (int index = 0; index < additionalImageFiles.size(); index++) {
            String reference = imageStorageService.store(additionalImageFiles.get(index));
            if (reference == null) continue;
            storedReferences.add(reference);
            while (request.getImages().size() <= index) request.getImages().add(new ProductImageRequest());
            request.getImages().get(index).setImageReference(reference);
        }
    }

    private String validationFailure(BindingResult result, RedirectAttributes attributes, String path) {
        String message = result.getAllErrors().isEmpty() ? "Please review the form and try again."
                : result.getAllErrors().getFirst().getDefaultMessage();
        attributes.addFlashAttribute("org.springframework.validation.BindingResult." + result.getObjectName(), result);
        attributes.addFlashAttribute(result.getObjectName(), result.getTarget());
        attributes.addFlashAttribute("errorMessage", message);
        return "redirect:" + path;
    }

    private ProductRequest request(ProductResponse value) {
        ProductRequest request = new ProductRequest();
        request.setName(value.name()); request.setReferenceCode(value.referenceCode()); request.setProductType(value.productType());
        request.setCategoryId(value.categoryId()); request.setBrand(value.brand()); request.setBarcodeFormat(value.barcodeFormat());
        request.setPackagingType(value.packagingType()); request.setTaxRateId(value.taxRateId());
        request.setTaxCalculationMethod(value.taxCalculationMethod()); request.setDescription(value.description());
        request.setThumbnailReference(value.thumbnailReference()); request.setTrackExpiry(value.trackExpiry());
        request.setExpiryType(value.expiryType()); request.setBatchTrackingRequired(value.batchTrackingRequired());
        request.setDefaultShelfLifeDays(value.defaultShelfLifeDays()); request.setExpiryAlertBeforeDays(value.expiryAlertBeforeDays());
        request.setBlockSaleAfterExpiry(value.blockSaleAfterExpiry()); request.setDisplayOrder(value.displayOrder());
        request.setStatus(value.status()); request.setBranchIds(new java.util.LinkedHashSet<>(value.branchIds()));
        request.setImages(value.images().stream().map(image -> { ProductImageRequest item = new ProductImageRequest();
            item.setImageReference(image.imageReference()); item.setDisplayOrder(image.displayOrder());
            item.setPrimaryImage(image.primaryImage()); return item; }).toList());
        return request;
    }

    private String dashboard() {
        return "redirect:/owner/dashboard";
    }

    public record ProductListRow(
            ProductResponse product,
            ProductVariantResponse primaryVariant,
            List<VariantListValue> variants,
            List<SupplierResponse> suppliers,
            java.math.BigDecimal purchasePrice,
            LocalDate nearestExpiry,
            java.math.BigDecimal expiringQuantity,
            java.math.BigDecimal expiredQuantity,
            long activeBatchCount,
            String searchText) {
    }

    public record VariantListValue(String name, String sku, java.math.BigDecimal sellingPrice,
                                   java.math.BigDecimal availableStock) {}

    public record ProductBatchRow(ProductBatchResponse batch, String supplierName, LocalDate purchaseDate,
                                  String branchName) { }
    public record StockAlertRow(Long productId, Long variantId, String productName, String supplierName,
                                String supplierMobile, Long supplierId, java.math.BigDecimal purchasePrice,
                                java.math.BigDecimal currentStock, java.math.BigDecimal reorderLevel) { }
    public record ExpiryAlertRow(Long productId, Long batchId, String productName, String code,
                                 String batchNumber, String supplierName, String branchName, LocalDate purchaseDate,
                                 LocalDate expiryDate, long daysRemaining, java.math.BigDecimal availableQuantity,
                                 java.math.BigDecimal unitCost, java.math.BigDecimal totalValue, String status) { }
    public record BarcodeLabelOption(Long productId, String productName, String referenceCode, String imageReference,
                                     Long variantId, String variantName, String sku, String barcode,
                                     java.math.BigDecimal sellingPrice, String currency, String categoryName, String unitName,
                                     java.math.BigDecimal availableStock) { }
}
