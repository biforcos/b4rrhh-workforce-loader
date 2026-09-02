package com.b4rrhh.workforceloader.infrastructure.api.dto;

import java.time.LocalDate;

public record CreateAddressRequest(
        String addressTypeCode,
        String street,
        String city,
        String countryCode,
        String postalCode,
        String regionCode,
        LocalDate startDate,
        LocalDate endDate
) {
}
