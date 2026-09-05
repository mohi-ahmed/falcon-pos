package com.spark.falcon.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class UploadResourceConfiguration implements WebMvcConfigurer {
    private final String uploadRoot;

    public UploadResourceConfiguration(@Value("${falcon.upload.root-directory:${user.dir}/uploads}") String uploadRoot) {
        this.uploadRoot = Path.of(uploadRoot).toAbsolutePath().normalize().toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**").addResourceLocations(uploadRoot.endsWith("/") ? uploadRoot : uploadRoot + "/");
    }
}
