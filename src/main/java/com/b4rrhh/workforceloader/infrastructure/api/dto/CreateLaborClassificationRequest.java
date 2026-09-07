package com.b4rrhh.workforceloader.infrastructure.api.dto;

import java.time.LocalDate;

public record CreateLaborClassificationRequest(
        String agreementCode,
        String agreementCategoryCode,
        LocalDate startDate,
        LocalDate endDate
) {
}
