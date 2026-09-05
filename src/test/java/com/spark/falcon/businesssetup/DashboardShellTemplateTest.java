package com.spark.falcon.businesssetup;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardShellTemplateTest {

    private static final Path DASHBOARD_TEMPLATE = Path.of(
            "src", "main", "resources", "templates", "onboarding", "dashboard-entry.html"
    );

    @Test
    void rendersBusinessOwnerAndBranchFromServerContext() throws IOException {
        String template = Files.readString(DASHBOARD_TEMPLATE);

        assertThat(template)
                .contains("th:text=\"${setup.businessName}\"")
                .contains("th:text=\"${setup.ownerName}\"")
                .contains("th:text=\"${setup.branchName}\"")
                .contains("th:text=\"${setup.branchCode}\"");
    }

    @Test
    void exposesDashboardAndBranchNavigationWithoutFakeMetrics() throws IOException {
        String template = Files.readString(DASHBOARD_TEMPLATE);

        assertThat(template)
                .contains("th:href=\"@{/owner/dashboard}\"")
                .contains("th:href=\"@{/owner/branches}\"")
                .contains("No estimated or hardcoded business figures are shown.")
                .doesNotContain("48,620", "16,840", "6,780");
    }
}
