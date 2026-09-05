package com.spark.falcon.purchase.dto.request;

import com.spark.falcon.purchase.entity.enumtype.StockImportPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
public class StockImportUploadRequest {
    @NotNull
    private Long branchId;

    @NotNull
    private StockImportPurpose importPurpose;

    @NotBlank
    @Size(max = 100)
    private String idempotencyKey;

    @NotNull
    private MultipartFile file;
}
