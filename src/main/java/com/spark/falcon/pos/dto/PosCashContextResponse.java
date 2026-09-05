package com.spark.falcon.pos.dto;

import java.util.List;

/**
 * POS-facing read model for selecting a valid cash checkout context without exposing raw
 * Cash Management entities or repository concerns to the web controller/template.
 */
public record PosCashContextResponse(
        List<ShiftOption> openShifts,
        List<CashLocationOption> activeCashLocations
) {
    public record ShiftOption(Long id, Long registerId, String shiftCode, String registerName) {}

    public record CashLocationOption(Long id, String name, String type) {}
}
