package com.b4rrhh.workforceloader.infrastructure.api.dto;

import java.time.LocalDate;

/**
 * El alta de un domicilio: el tipo, las fechas y la dirección anidada, que es la misma estructura que
 * la de la empresa y el centro (backend#155).
 */
public record CreateAddressRequest(
        String addressTypeCode,
        PostalAddress address,
        LocalDate startDate,
        LocalDate endDate
) {

    /** La dirección, con los campos de {@code PostalAddressRequest} que el loader rellena. */
    public record PostalAddress(
            String streetTypeCode,
            String streetName,
            String streetNumber,
            String floor,
            String door,
            String locality,
            String municipalityCode,
            String subdivisionCode,
            String postalCode,
            String countryCode
    ) {
    }
}
