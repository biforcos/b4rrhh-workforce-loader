package com.b4rrhh.workforceloader.infrastructure.api.dto;

/**
 * Lo que se pide al lanzar el calculo de un mes ({@code workforce-loader#16}).
 *
 * <p>Los dos parametros de la retro van en la <b>peticion</b> y no en la empresa
 * ({@code b4rrhh/backend#132}): una revision de convenio es una cosa que pasa una vez.
 *
 * @param retroLimitPeriodCode hasta que mes atras puede recalcular esta corrida. Sin el, la corrida
 *        no recalcula ningun mes cerrado y lo dice en sus mensajes, o sea que los atrasos no se
 *        pagarian y la semilla no tendria ninguno
 * @param retroFloorPeriodCode el suelo para todos. El loader no lo usa: la semilla no simula una
 *        revision de convenio, simula la operativa normal, donde recalcula solo quien tiene marca.
 *        Viaja como {@code null} y no omitido, que es lo que el contrato declara
 */
public record LaunchPayrollCalculationRequest(
        String ruleSystemCode,
        String payrollPeriodCode,
        String payrollTypeCode,
        String calculationEngineCode,
        String calculationEngineVersion,
        TargetSelectionRequest targetSelection,
        String retroLimitPeriodCode,
        String retroFloorPeriodCode
) {

    /** Todos los empleados con presencia en el periodo, que es lo que una nomina de mes hace. */
    public record TargetSelectionRequest(String selectionType) {

        public static TargetSelectionRequest allWithPresence() {
            return new TargetSelectionRequest("ALL_EMPLOYEES_WITH_PRESENCE_IN_PERIOD");
        }
    }
}
