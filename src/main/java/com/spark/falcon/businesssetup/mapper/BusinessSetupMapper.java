package com.spark.falcon.businesssetup.mapper;
import com.spark.falcon.businesssetup.dto.command.CreateBusinessSetupCommand;
import com.spark.falcon.businesssetup.dto.request.BusinessSetupRequest;
import org.springframework.stereotype.Component;
@Component
public class BusinessSetupMapper {
    public CreateBusinessSetupCommand toCommand(Long ownerId, BusinessSetupRequest r) {
        return new CreateBusinessSetupCommand(ownerId, r.getIdempotencyKey(), r.getBusinessName(), r.getBusinessCode(),
                r.getBusinessType(), r.getBusinessEmail(), r.getBusinessPhone(), r.getBranchName(), r.getBranchCode(),
                r.getBranchEmail(), r.getBranchPhone(), r.getCountry(), r.getTimezone(), r.getCurrency(), r.getAddress(),
                r.getCity(), r.getStateDivision(), r.getPostalCode(), r.getVatBinNumber(), r.getDefaultTaxRate(),
                r.getLowStockAlertQuantity(), r.getRecordsPerPage(), r.getReceiptFooter());
    }
}
