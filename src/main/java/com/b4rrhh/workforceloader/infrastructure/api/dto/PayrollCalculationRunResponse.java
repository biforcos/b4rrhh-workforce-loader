package com.b4rrhh.workforceloader.infrastructure.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * La ejecucion de calculo, tal y como la sirve el backend ({@code workforce-loader#16}).
 *
 * <p>Solo los campos que el ciclo mira: el estado para saber cuando ha terminado, y los contadores
 * que van al informe. La anotacion de ignorar lo demas no es pereza — el contrato trae veinte campos
 * y copiarlos todos aqui seria una segunda lista que se desincroniza.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayrollCalculationRunResponse(
        Long runId,
        String status,
        Integer totalCandidates,
        Integer totalCalculated,
        Integer totalNotValid,
        Integer totalErrors,
        Integer totalSkippedNotEligible,
        Integer totalSkippedMissingInput,
        Integer totalRetroUnits,
        Integer totalRetroRecalculated,
        Integer totalRetroNotRecalculated
) {

    /** Si la ejecucion ha terminado, de una manera o de otra. */
    public boolean isFinished() {
        return "COMPLETED".equals(status)
                || "COMPLETED_WITH_ERRORS".equals(status)
                || "FAILED".equals(status);
    }

    public boolean hasFailed() {
        return "FAILED".equals(status);
    }
}
