package com.b4rrhh.workforceloader.application;

import java.time.LocalDate;

/**
 * Una ausencia planificada. {@code endDate} a null es una ausencia abierta: la persona sigue de baja.
 *
 * @param benefitEntitled si la baja lleva derecho a prestacion, o {@code null} si el tipo no admite
 *        el testigo ({@code workforce-loader#14}). Nulo no es «no lo se»: es «en este tipo de
 *        ausencia esa pregunta no significa nada», y por eso no se manda. Solo la baja por
 *        enfermedad comun paga prestacion ({@code b4rrhh/backend#129})
 */
public record AbsenceEventPayload(
        String absenceTypeCode,
        LocalDate endDate,
        Boolean benefitEntitled
) implements MutationEventPayload {
}
