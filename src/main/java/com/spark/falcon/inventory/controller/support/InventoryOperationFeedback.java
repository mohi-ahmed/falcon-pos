package com.spark.falcon.inventory.controller.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Component
@RequiredArgsConstructor
public class InventoryOperationFeedback {

    private final InventoryErrorMessageResolver messageResolver;

    public void addError(RedirectAttributes redirectAttributes,
                         InventoryErrorArea area,
                         RuntimeException exception) {
        redirectAttributes.addFlashAttribute("errorMessage", messageResolver.resolve(exception));
        redirectAttributes.addFlashAttribute("inventoryErrorArea", area.modelValue());
    }

    public void preserve(RedirectAttributes redirectAttributes, String attributeName, Object value) {
        if (value != null) {
            redirectAttributes.addFlashAttribute(attributeName, value);
        }
    }

    public void preserveAction(RedirectAttributes redirectAttributes, String action, String reason) {
        if (action != null && !action.isBlank()) {
            redirectAttributes.addFlashAttribute("inventoryAction", action);
        }
        if (reason != null) {
            redirectAttributes.addFlashAttribute("inventoryActionReason", reason);
        }
    }
}
