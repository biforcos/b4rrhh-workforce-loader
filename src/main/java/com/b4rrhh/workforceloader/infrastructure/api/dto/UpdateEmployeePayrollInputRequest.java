package com.b4rrhh.workforceloader.infrastructure.api.dto;

import java.math.BigDecimal;

/**
 * Corregir la cantidad de una entrada que ya existe. El concepto y el periodo van en la ruta y en
 * la query, porque son su clave: lo unico que se puede cambiar es cuanto.
 */
public record UpdateEmployeePayrollInputRequest(BigDecimal quantity) {
}
