package com.spark.falcon.settings.repository;

import com.spark.falcon.settings.entity.PaymentMethodBranch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodBranchRepository extends JpaRepository<PaymentMethodBranch, Long> {
    List<PaymentMethodBranch> findByPaymentMethodId(Long paymentMethodId);
    List<PaymentMethodBranch> findByBranchIdAndActiveTrue(Long branchId);
    Optional<PaymentMethodBranch> findByPaymentMethodIdAndBranchId(Long paymentMethodId, Long branchId);
    boolean existsByPaymentMethodIdAndBranchIdAndActiveTrue(Long paymentMethodId, Long branchId);
}
