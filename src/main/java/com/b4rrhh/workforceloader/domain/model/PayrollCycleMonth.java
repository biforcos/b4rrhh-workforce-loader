package com.b4rrhh.workforceloader.domain.model;

/**
 * Lo que dejo un mes del ciclo ({@code workforce-loader#16}).
 *
 * <p>Una fila por mes, y el informe es la tabla. Los contadores del calculo salen de la ejecucion y
 * los del cierre del cierre: no se suman ni se derivan, porque lo que hace util esta tabla es poder
 * cruzarla con la base despues.
 *
 * @param period            el mes, en {@code yyyyMM}
 * @param runId             la ejecucion que lo calculo, para poder abrirla en la pantalla
 * @param status            en que acabo la ejecucion
 * @param calculated        recibos escritos
 * @param notValid          unidades que acabaron NO validas
 * @param errors            unidades con error
 * @param retroUnits        el universo de la retro: unidades empleado x mes que habia que recalcular
 * @param retroRecalculated vigentes escritos
 * @param finalized         recibos cerrados; cero en el mes que se queda abierto
 * @param horasAlMesCerrado correcciones de horas escritas a ESTE mes despues de cerrarlo
 * @param ausenciasAlAnterior ausencias olvidadas escritas al mes anterior
 * @param fueraDelLimite    correcciones escritas mas atras de lo que el limite alcanza
 * @param secondsElapsed    lo que tardo el mes entero, que es lo que se acumula al final
 */
public record PayrollCycleMonth(
        int period,
        Long runId,
        String status,
        int calculated,
        int notValid,
        int errors,
        int retroUnits,
        int retroRecalculated,
        int finalized,
        int horasAlMesCerrado,
        int ausenciasAlAnterior,
        int fueraDelLimite,
        long secondsElapsed
) {
}
