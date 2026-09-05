package com.spark.falcon.businesssetup.service;

import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;

import java.util.Optional;

public interface BusinessAccessService {
    Optional<BusinessAccessResponse> findByOwnerId(Long ownerId);
}
