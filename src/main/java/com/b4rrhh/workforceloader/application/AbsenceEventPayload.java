package com.b4rrhh.workforceloader.application;

import java.time.LocalDate;

/** Una ausencia planificada. {@code endDate} a null es una ausencia abierta: la persona sigue de baja. */
public record AbsenceEventPayload(
        String absenceTypeCode,
        LocalDate endDate
) implements MutationEventPayload {
}
