package com.spark.falcon.identity.usecase;

import com.spark.falcon.identity.dto.OwnerRegistrationResult;
import com.spark.falcon.identity.dto.RegisterOwnerCommand;

public interface RegisterOwnerUseCase {
    OwnerRegistrationResult register(RegisterOwnerCommand command);
}

