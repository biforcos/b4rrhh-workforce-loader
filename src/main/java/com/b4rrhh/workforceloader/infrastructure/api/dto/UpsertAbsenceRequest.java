package com.b4rrhh.workforceloader.infrastructure.api.dto;

import java.time.LocalDate;

/**
 * Cuerpo del PUT de ausencia en modo dia: el tipo y la fecha de inicio van en la ruta.
 *
 * @param benefitEntitled si la baja lleva derecho a prestacion ({@code workforce-loader#14}). Nulo
 *        se omite del JSON y el backend deja el que corresponde -con derecho-, que es el caso normal
 */
public record UpsertAbsenceRequest(
        LocalDate endDate,
        String endTime,
        Boolean benefitEntitled
) {
}
