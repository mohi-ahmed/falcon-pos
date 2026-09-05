package com.spark.falcon.web;

import com.spark.falcon.identity.security.StaffBranchAccessInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
public class FalconWebMvcConfiguration implements WebMvcConfigurer {

    private final StaffBranchAccessInterceptor staffBranchAccessInterceptor;

    @Value("${falcon.upload.user-profile-directory:uploads/users}")
    private String userProfileDirectory;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(staffBranchAccessInterceptor).addPathPatterns("/owner/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(userProfileDirectory).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/users/**").addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
