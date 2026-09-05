package com.spark.falcon.branch;

import com.spark.falcon.branch.dto.command.CreateBranchCommand;
import com.spark.falcon.branch.dto.response.BranchResponse;
import com.spark.falcon.branch.entity.Branch;
import com.spark.falcon.branch.mapper.BranchMapper;
import com.spark.falcon.branch.repository.BranchAuditEventRepository;
import com.spark.falcon.branch.repository.BranchRepository;
import com.spark.falcon.branch.service.BranchService;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.identity.security.CurrentActorService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BranchServiceTest {
    private final BranchRepository branchRepository = mock(BranchRepository.class);
    private final BranchAuditEventRepository auditRepository = mock(BranchAuditEventRepository.class);
    private final BusinessAccessService businessAccessService = mock(BusinessAccessService.class);
    private final BranchMapper branchMapper = mock(BranchMapper.class);
    private final CurrentActorService currentActorService = mock(CurrentActorService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC);
    private final BranchService service = new BranchService(
            branchRepository, auditRepository, businessAccessService, branchMapper, clock, currentActorService);

    @Test
    void returnsOriginalResultForRepeatedIdempotencyKey() {
        CreateBranchCommand command = command();
        Branch existingBranch = mock(Branch.class);
        BranchResponse expected = mock(BranchResponse.class);
        when(businessAccessService.findByOwnerId(7L))
                .thenReturn(Optional.of(new BusinessAccessResponse(11L, 7L, "Falcon")));
        when(branchRepository.findByBusinessIdAndCreateIdempotencyKey(11L, "key-1"))
                .thenReturn(Optional.of(existingBranch));
        when(branchMapper.toResponse(existingBranch)).thenReturn(expected);

        BranchResponse actual = service.create(command);

        assertThat(actual).isSameAs(expected);
        verify(branchRepository, never()).saveAndFlush(any(Branch.class));
        verifyNoInteractions(auditRepository);
    }

    private CreateBranchCommand command() {
        return new CreateBranchCommand(7L, "key-1", "Gulshan", "GUL-01", "BD", "branch@example.com",
                "+8801700000000", "Asia/Dhaka", "BDT", "Dhaka", "Dhaka", null, "1212", null,
                BigDecimal.ZERO, 10, 25, null);
    }
}
