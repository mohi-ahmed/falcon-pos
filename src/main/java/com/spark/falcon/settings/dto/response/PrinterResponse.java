package com.spark.falcon.settings.dto.response;

import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import com.spark.falcon.settings.entity.enumtype.PrinterConnectionType;
import com.spark.falcon.settings.entity.enumtype.PrinterType;
import java.util.Set;

public record PrinterResponse(Long id, Long businessId, String title, PrinterType printerType,
                              PrinterConnectionType connectionType, int charactersPerLine,
                              String printerPath, String ipAddress, Integer port, int displayOrder,
                              ConfigurationStatus status, Set<Long> branchIds, boolean archived) {}
