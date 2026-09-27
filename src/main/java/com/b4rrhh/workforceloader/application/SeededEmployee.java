package com.b4rrhh.workforceloader.application;

import java.time.LocalDate;
import java.util.List;

/**
 * Un empleado sembrado, con los meses en que estuvo ({@code workforce-loader#16}).
 *
 * <p>Lo que el ciclo de nomina necesita de la siembra, y nada mas. No es el escenario entero: el
 * ciclo no tiene por que saber de contratos, categorias ni centros — solo a quien puede escribirle
 * una correccion de que mes.
 *
 * <p>Lleva las ventanas <b>planificadas</b> y no las escritas, y eso importa en seco: en
 * {@code dry-run} no se ha dado de alta a nadie, asi que el ciclo tiene que poder decir lo que
 * haria sin preguntarle a la base.
 */
public record SeededEmployee(
        String employeeTypeCode,
        String employeeNumber,
        List<ActiveWindow> activeWindows
) {

    /** Si esta persona estuvo en la empresa el mes entero. */
    public boolean coversWholeMonth(int period) {
        LocalDate inicio = LocalDate.of(period / 100, period % 100, 1);
        LocalDate fin = inicio.plusMonths(1).minusDays(1);
        for (ActiveWindow ventana : activeWindows) {
            boolean empiezaAntesOEse = !ventana.startDate().isAfter(inicio);
            boolean acabaDespuesOEse = ventana.endDate() == null || !ventana.endDate().isBefore(fin);
            if (empiezaAntesOEse && acabaDespuesOEse) {
                return true;
            }
        }
        return false;
    }
}
