package com.b4rrhh.workforceloader.application;

import java.math.BigDecimal;

/**
 * Cambio de jornada a mitad de mes. Solo lleva el porcentaje nuevo: la fecha va en el
 * evento y el cierre de la ventana anterior lo hace el backend (ADR-057).
 */
public record WorkingTimeChangeEventPayload(
        BigDecimal workingTimePercentage
) implements MutationEventPayload {
}
