package com.b4rrhh.workforceloader.domain.model;

import java.util.List;

/**
 * El ciclo entero, mes a mes ({@code workforce-loader#16}).
 *
 * @param months     una fila por mes, de mas viejo a mas nuevo
 * @param openPeriod el mes que se queda abierto, con sus atrasos dentro
 * @param retroLimitMonthsBack con que limite de retroactividad se lanzo cada mes
 * @param secondsElapsed lo que tardo el ciclo entero. Se dice porque es un dato de la receta y no
 *        una queja: quien vaya a resembrar tiene que saber si se sienta a esperar o se va a comer
 */
public record PayrollCycleSummary(
        List<PayrollCycleMonth> months,
        int openPeriod,
        int retroLimitMonthsBack,
        long secondsElapsed
) {

    public static PayrollCycleSummary empty() {
        return new PayrollCycleSummary(List.of(), 0, 0, 0);
    }

    public boolean ranAnything() {
        return !months.isEmpty();
    }
}
