package com.b4rrhh.workforceloader.infrastructure.api.dto;

import java.time.LocalDate;

public record CreateIdentifierRequest(
        String identifierTypeCode,
        String identifierValue,
        String issuingCountryCode,
        LocalDate expirationDate,
        Boolean isPrimary
) {
}
