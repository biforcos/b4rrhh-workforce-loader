package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.domain.model.LoaderRunSummary;

import java.util.List;

/**
 * Lo que deja la siembra: el informe y a quien se sembro ({@code workforce-loader#16}).
 *
 * <p>Los dos juntos y no el informe solo, porque el ciclo de nomina viene despues y necesita la
 * plantilla. Lo alternativo era que el ciclo volviera a generar los escenarios con la misma semilla
 * —da lo mismo, es determinista— y eso habria sido una segunda fuente de verdad que se desincroniza
 * el dia que uno de los dos cambie de parametros.
 */
public record LoaderRunResult(
        LoaderRunSummary summary,
        List<SeededEmployee> seeded
) {
}
