package com.b4rrhh.workforceloader.infrastructure.api.dto;

public record CreateContactRequest(
        String contactTypeCode,
        String contactValue
) {
}
