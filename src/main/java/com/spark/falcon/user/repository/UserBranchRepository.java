package com.spark.falcon.user.repository;

import com.spark.falcon.user.entity.UserBranch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UserBranchRepository extends JpaRepository<UserBranch, Long> {
    List<UserBranch> findByUserId(Long userId);
    List<UserBranch> findByUserIdAndActiveTrue(Long userId);
    List<UserBranch> findByUserIdInAndActiveTrue(Collection<Long> userIds);
    boolean existsByUserIdAndBranchIdAndActiveTrue(Long userId, Long branchId);
}
