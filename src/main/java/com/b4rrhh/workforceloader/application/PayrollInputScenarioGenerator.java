package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

/**
 * Horas extra declaradas por una parte de la plantilla (workforce-loader#5).
 *
 * <p>Es la tercera de las tres tablas vacias del issue, y la ultima que quedaba:
 * {@code employee.employee_payroll_input} llevaba meses a cero filas y no por descuido del loader.
 * Faltaba el otro extremo de la cadena — ningun concepto del motor declaraba {@code EMPLOYEE_INPUT},
 * asi que cualquier fila que se sembrara habria llevado un codigo que no existia en ningun sitio.
 * Lo declaro el {@code b4rrhh/backend#104}: {@code H01} son las horas y el {@code 102} lo que se
 * cobra por ellas.
 *
 * <h2>Solo a una parte, que es el punto</h2>
 *
 * <p>Un plus que cobran los mil no ensena nada que no ensene ya el salario base. Lo que hace que la
 * plantilla se lea como real es que <b>unos lo tengan y otros no</b>, y eso es justo lo que
 * {@code payroll_engine.concept_assignment} no sabe hacer: acota por sociedad, convenio y tipo de
 * empleado, y no por empleado. El concepto se le asigna al convenio entero y quien no declara horas
 * lo calcula a cero — y un cero no se imprime desde la regla del {@code backend#104}.
 *
 * <h2>Un periodo y no una fecha</h2>
 *
 * <p>Una entrada es del periodo. Se siembra en el <b>mes que la demo calcula</b>, porque una entrada
 * en cualquier otro mes no se ve en ninguna pantalla: no habria recibo que la consumiera. Va escrito
 * en la configuracion y no se saca del reloj, por lo mismo que la fecha del mes partido.
 *
 * <h2>Azar propio, a proposito</h2>
 *
 * <p>Este generador no toca el {@code Random} de la simulacion: lleva el suyo, derivado de la misma
 * semilla. Si gastara del comun desplazaria toda la secuencia posterior y la semilla entera
 * cambiaria —otras direcciones, otras ausencias, otros ceses— para anadir unas filas. Asi el
 * diferencial contra la semilla de hoy es exactamente lo que este cambio anade y nada mas, que es
 * lo que hace revisable una resiembra. Es la misma decision que tomo el mes partido.
 */
@Component
public class PayrollInputScenarioGenerator {

    /**
     * Desplazamiento de la semilla para el azar propio. El {@code +1} lo usa la simulacion; este es
     * el numero del issue del backend que declaro el concepto, para que se sepa de donde sale.
     */
    static final int SEED_OFFSET = 104;

    public Random newRandom(long seed) {
        return new Random(seed + SEED_OFFSET);
    }

    /**
     * Las horas extra de un empleado, si le tocan.
     *
     * @param activeWindows periodos de presencia; el ultimo puede estar abierto
     * @param random        el azar propio de este generador, uno por corrida
     * @return cero o un evento; nunca mas, porque la clave de una entrada es
     *         {@code (empleado, concepto, periodo)} y una segunda seria un 409
     */
    public List<EmployeeLifecycleEvent> generate(
            LoaderProperties.PayrollInput payrollInput,
            List<ActiveWindow> activeWindows,
            Random random
    ) {
        if (!payrollInput.isEnabled() || payrollInput.getPeriod() == null
                || payrollInput.getConceptCode() == null || payrollInput.getConceptCode().isBlank()) {
            return List.of();
        }

        LocalDate periodStart = firstDayOf(payrollInput.getPeriod());
        LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);

        // El azar se gasta SIEMPRE, tenga o no presencia en el mes: si solo se tirara para los
        // elegibles, anadir o quitar un cese en cualquier otro sitio correria la secuencia de
        // este generador y cambiaria quien tiene horas. Asi la tirada de cada empleado depende
        // solo de su posicion en la plantilla.
        boolean lotteryWon = random.nextDouble() < payrollInput.getRate();
        int hours = payrollInput.getMinHours()
                + random.nextInt(payrollInput.getMaxHours() - payrollInput.getMinHours() + 1);

        if (!lotteryWon || !coversTheWholePeriod(activeWindows, periodStart, periodEnd)) {
            return List.of();
        }

        return List.of(new EmployeeLifecycleEvent(
                LifecycleEventType.PAYROLL_INPUT,
                periodEnd,
                new PayrollInputEventPayload(
                        payrollInput.getConceptCode().trim().toUpperCase(),
                        payrollInput.getPeriod(),
                        BigDecimal.valueOf(hours)
                )
        ));
    }

    /**
     * Presente el mes entero, no un trozo.
     *
     * <p>Podrian declararse horas en un mes que se trabaja a medias, y seria legitimo. No se hace
     * porque enturbia lo que este escenario viene a ensenar: quien entra o sale a mitad de mes ya
     * tiene el recibo partido en tramos por otra razon, y mezclar las dos cosas en la misma persona
     * hace ilegible la unica pantalla donde esto se mira.
     */
    private static boolean coversTheWholePeriod(
            List<ActiveWindow> activeWindows, LocalDate periodStart, LocalDate periodEnd) {
        for (ActiveWindow window : activeWindows) {
            boolean startsBeforeOrWith = !window.startDate().isAfter(periodStart);
            boolean endsAfterOrWith = window.endDate() == null || !window.endDate().isBefore(periodEnd);
            if (startsBeforeOrWith && endsAfterOrWith) {
                return true;
            }
        }
        return false;
    }

    private static LocalDate firstDayOf(int period) {
        return LocalDate.of(period / 100, period % 100, 1);
    }
}
