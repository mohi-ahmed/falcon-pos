package com.spark.falcon.branch;

import com.spark.falcon.branch.dto.response.BranchAccessResponse;
import com.spark.falcon.branch.entity.Branch;
import com.spark.falcon.branch.entity.enumtype.BranchStatus;
import com.spark.falcon.branch.mapper.BranchMapper;
import com.spark.falcon.branch.repository.BranchRepository;
import com.spark.falcon.branch.service.BranchAccessServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BranchAccessServiceImplTest {

    private final BranchRepository branchRepository = mock(BranchRepository.class);
    private final BranchMapper branchMapper = mock(BranchMapper.class);
    private final BranchAccessServiceImpl service = new BranchAccessServiceImpl(branchRepository, branchMapper);

    @Test
    void returnsOnlyActiveBranchForOperationalAccess() {
        Branch branch = mock(Branch.class);
        BranchAccessResponse expected = new BranchAccessResponse(
                21L, 11L, "Gulshan", "GUL-01", "Asia/Dhaka", "BDT", BranchStatus.ACTIVE);

        when(branchRepository.findByIdAndBusinessIdAndStatus(21L, 11L, BranchStatus.ACTIVE))
                .thenReturn(Optional.of(branch));
        when(branchMapper.toAccessResponse(branch)).thenReturn(expected);

        Optional<BranchAccessResponse> actual = service.findActiveByBusinessIdAndBranchId(11L, 21L);

        assertThat(actual).containsSame(expected);
        verify(branchRepository).findByIdAndBusinessIdAndStatus(21L, 11L, BranchStatus.ACTIVE);
        verify(branchMapper).toAccessResponse(branch);
    }

    @Test
    void returnsEmptyWhenBranchDoesNotBelongToBusiness() {
        when(branchRepository.findByIdAndBusinessId(21L, 99L)).thenReturn(Optional.empty());

        Optional<BranchAccessResponse> actual = service.findByBusinessIdAndBranchId(99L, 21L);

        assertThat(actual).isEmpty();
        verifyNoInteractions(branchMapper);
    }
}
