package com.b4rrhh.workforceloader.infrastructure.api.dto;

import java.time.LocalDate;

public record CreateContractRequest(
        String contractCode,
        String contractSubtypeCode,
        LocalDate startDate,
        LocalDate endDate
) {
}
