package com.spark.falcon.user.service;

import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.user.dto.command.ManagementActor;
import com.spark.falcon.user.entity.User;
import com.spark.falcon.user.entity.enumtype.ManagementActorType;
import com.spark.falcon.user.entity.enumtype.SystemPermissionCode;
import com.spark.falcon.user.exception.UserManagementAccessDeniedException;
import com.spark.falcon.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserManagementActorService {

    private final BusinessAccessService businessAccessService;
    private final UserRepository userRepository;
    private final UserAccessService userAccessService;

    @Transactional(readOnly = true)
    public ManagementActor forOwner(Long ownerId) {
        BusinessAccessResponse business = businessAccessService.findByOwnerId(ownerId)
                .orElseThrow(UserManagementAccessDeniedException::new);
        return new ManagementActor(business.businessId(), ManagementActorType.OWNER, ownerId);
    }

    @Transactional(readOnly = true)
    public ManagementActor forAuthorizedUser(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isOperationallyActive)
                .orElseThrow(UserManagementAccessDeniedException::new);
        if (!userAccessService.hasPermission(user.getBusinessId(), user.getId(),
                SystemPermissionCode.USER_MANAGEMENT.name())) {
            throw new UserManagementAccessDeniedException();
        }
        return new ManagementActor(user.getBusinessId(), ManagementActorType.USER, user.getId());
    }
}
