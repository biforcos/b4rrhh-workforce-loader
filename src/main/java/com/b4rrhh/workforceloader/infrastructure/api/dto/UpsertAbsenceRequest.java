package com.b4rrhh.workforceloader.infrastructure.api.dto;

import java.time.LocalDate;

/** Cuerpo del PUT de ausencia en modo dia: el tipo y la fecha de inicio van en la ruta. */
public record UpsertAbsenceRequest(
        LocalDate endDate,
        String endTime
) {
}
