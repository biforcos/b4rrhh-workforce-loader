package com.b4rrhh.workforceloader.infrastructure.api.dto;

import java.math.BigDecimal;

/** Cuerpo del alta de una entrada de nomina: el empleado va en la ruta, el resto aqui. */
public record CreateEmployeePayrollInputRequest(
        String conceptCode,
        int period,
        BigDecimal quantity
) {
}
