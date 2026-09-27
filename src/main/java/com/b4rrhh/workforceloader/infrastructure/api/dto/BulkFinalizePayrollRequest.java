package com.b4rrhh.workforceloader.infrastructure.api.dto;

/**
 * Cerrar en masa los recibos de un mes ({@code b4rrhh/backend#102}).
 *
 * <p>No lleva motivo, y la ausencia es del contrato: cerrar no da un motivo, conserva el que el
 * recibo tuviera. Invalidar si lo pide, porque invalidar es una decision sobre algo que estaba bien.
 */
public record BulkFinalizePayrollRequest(
        String ruleSystemCode,
        String payrollPeriodCode,
        String payrollTypeCode,
        LaunchPayrollCalculationRequest.TargetSelectionRequest targetSelection
) {
}
