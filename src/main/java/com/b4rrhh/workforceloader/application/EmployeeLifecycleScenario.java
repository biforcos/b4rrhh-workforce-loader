package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.domain.model.SyntheticEmployee;

import java.util.List;

/**
 * @param activeWindows los periodos en que el empleado esta en la empresa, el ultimo abierto si
 *        sigue. Se exponen desde el {@code workforce-loader#16}: el ciclo de nomina escribe
 *        correcciones a meses cerrados y tiene que saber si esa persona estaba ahi ese mes. Se
 *        calculan una vez al planificar y volver a deducirlas del listado de eventos seria
 *        reconstruir aqui algo que ya esta hecho —y mal, porque el orden de los eventos se altera
 *        despues—.
 */
public record EmployeeLifecycleScenario(
        SyntheticEmployee syntheticEmployee,
        List<EmployeeLifecycleEvent> events,
        ResolvedHireData resolvedHireData,
        ResolvedHireData rehireResolvedHireData,
        String exitReasonCode,
        List<ActiveWindow> activeWindows
) {
}