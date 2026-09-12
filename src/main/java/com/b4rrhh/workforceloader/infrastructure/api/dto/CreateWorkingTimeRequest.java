package com.b4rrhh.workforceloader.infrastructure.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateWorkingTimeRequest(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal workingTimePercentage
) {
}
