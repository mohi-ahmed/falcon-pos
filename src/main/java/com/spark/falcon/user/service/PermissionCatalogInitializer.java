package com.spark.falcon.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PermissionCatalogInitializer implements ApplicationRunner {

    private final PermissionService permissionService;

    @Override
    public void run(ApplicationArguments args) {
        permissionService.ensureDocumentedPermissions();
    }
}
