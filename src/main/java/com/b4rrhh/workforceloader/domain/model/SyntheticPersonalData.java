package com.b4rrhh.workforceloader.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * La mitad «Persona» de la ficha: direcciones, contactos e identificadores que el alta no
 * acepta en su payload y hay que dar de alta con sus propias llamadas justo después
 * (workforce-loader#3). No todos los empleados llevan de todo: las listas pueden estar vacías.
 */
public record SyntheticPersonalData(
        List<Address> addresses,
        List<Contact> contacts,
        List<Identifier> identifiers
) {

    public SyntheticPersonalData {
        addresses = List.copyOf(Objects.requireNonNull(addresses, "addresses must not be null"));
        contacts = List.copyOf(Objects.requireNonNull(contacts, "contacts must not be null"));
        identifiers = List.copyOf(Objects.requireNonNull(identifiers, "identifiers must not be null"));
    }

    public static SyntheticPersonalData none() {
        return new SyntheticPersonalData(List.of(), List.of(), List.of());
    }

    public record Address(
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

    public record Contact(
            String contactTypeCode,
            String contactValue
    ) {
    }

    public record Identifier(
            String identifierTypeCode,
            String identifierValue,
            String issuingCountryCode,
            LocalDate expirationDate,
            boolean primary
    ) {
    }
}
