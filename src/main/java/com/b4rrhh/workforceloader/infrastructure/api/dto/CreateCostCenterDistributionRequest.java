package com.b4rrhh.workforceloader.infrastructure.api.dto;

import java.time.LocalDate;
import java.util.List;

public record CreateCostCenterDistributionRequest(
        LocalDate startDate,
        LocalDate endDate,
        List<Item> items
) {
    public record Item(
            String costCenterCode,
            Integer allocationPercentage
    ) {
    }
}
