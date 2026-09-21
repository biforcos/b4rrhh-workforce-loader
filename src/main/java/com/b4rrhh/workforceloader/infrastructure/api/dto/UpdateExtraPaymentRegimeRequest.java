package com.b4rrhh.workforceloader.infrastructure.api.dto;

import java.time.LocalDate;

/**
 * Correccion de un tramo de regimen de pagas extras ({@code b4rrhh/backend#118}).
 *
 * <p>Las tres cosas van siempre: corregir es decir como queda la ocurrencia, no que cambia de
 * ella.
 */
public record UpdateExtraPaymentRegimeRequest(
        LocalDate startDate,
        LocalDate endDate,
        boolean prorated
) {
}
