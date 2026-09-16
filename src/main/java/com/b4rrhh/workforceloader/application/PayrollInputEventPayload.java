package com.b4rrhh.workforceloader.application;

import java.math.BigDecimal;

/**
 * Una entrada de nomina del empleado: la cantidad que declara una persona para un concepto y un
 * periodo (workforce-loader#5).
 *
 * <p>No lleva fecha porque no la tiene: una entrada es del <b>periodo</b>, no del dia. La fecha del
 * evento que la transporta solo sirve para colocarla en la secuencia, detras del alta.
 */
public record PayrollInputEventPayload(
        String conceptCode,
        int period,
        BigDecimal quantity
) implements MutationEventPayload {
}
