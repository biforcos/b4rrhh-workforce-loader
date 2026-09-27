package com.b4rrhh.workforceloader.infrastructure.report;

import com.b4rrhh.workforceloader.domain.model.LifecycleEventExecutionResult;
import com.b4rrhh.workforceloader.domain.model.LoaderRunSummary;
import com.b4rrhh.workforceloader.domain.model.PayrollCycleMonth;
import com.b4rrhh.workforceloader.domain.model.PayrollCycleSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RunReportWriter {

    private static final Logger log = LoggerFactory.getLogger(RunReportWriter.class);

    public void printSummary(LoaderRunSummary summary, boolean dryRun) {
        log.info("==========================================");
        log.info("B4RRHH Workforce Loader Summary");
        log.info("Mode: {}", dryRun ? "DRY-RUN" : "LIVE");
        log.info("Employees requested: {}", summary.totalEmployeesRequested());
        log.info("Hires: requested={} success={} failed={}", summary.hiresRequested(), summary.hiresSuccess(), summary.hiresFailed());
        log.info("Terminations: requested={} success={} failed={}", summary.terminationsRequested(), summary.terminationsSuccess(), summary.terminationsFailed());
        log.info("Rehires: requested={} success={} failed={}", summary.rehiresRequested(), summary.rehiresSuccess(), summary.rehiresFailed());
        log.info("Work center changes: requested={} success={} failed={}",
            summary.workCenterChangesRequested(),
            summary.workCenterChangesSuccess(),
            summary.workCenterChangesFailed());
        log.info("Working time changes: requested={} success={} failed={}",
            summary.workingTimeChangesRequested(),
            summary.workingTimeChangesSuccess(),
            summary.workingTimeChangesFailed());
        log.info("Extra payment regime changes (against the agreement default): requested={} success={} failed={}",
            summary.extraPaymentRegimeChangesRequested(),
            summary.extraPaymentRegimeChangesSuccess(),
            summary.extraPaymentRegimeChangesFailed());
        log.info("Contract replacements: requested={} success={} failed={}",
            summary.contractReplacementsRequested(),
            summary.contractReplacementsSuccess(),
            summary.contractReplacementsFailed());
        log.info("Labor classification replacements: requested={} success={} failed={}",
            summary.laborClassificationReplacementsRequested(),
            summary.laborClassificationReplacementsSuccess(),
            summary.laborClassificationReplacementsFailed());
        log.info("Cost center replacements: requested={} success={} failed={}",
            summary.costCenterReplacementsRequested(),
            summary.costCenterReplacementsSuccess(),
            summary.costCenterReplacementsFailed());
        log.info("Absences: requested={} success={} failed={}",
            summary.absencesRequested(),
            summary.absencesSuccess(),
            summary.absencesFailed());
        log.info("Payroll inputs (overtime hours): requested={} success={} failed={}",
            summary.payrollInputsRequested(),
            summary.payrollInputsSuccess(),
            summary.payrollInputsFailed());
        log.info("Personal data (addresses, contacts, identifiers): requested={} success={} failed={}",
            summary.personalDataRequested(),
            summary.personalDataSuccess(),
            summary.personalDataFailed());

        if (dryRun) {
            log.info("Dry-run payload preview:");
            summary.results().stream().limit(5).forEach(result ->
                    log.info("  - employeeNumber={} event={} date={} payload={}",
                            result.employeeNumber(),
                            result.eventType(),
                            result.effectiveDate(),
                            result.message())
            );
            if (summary.results().size() > 5) {
                log.info("  ... {} additional payloads", summary.results().size() - 5);
            }
        } else if (summary.hiresFailed()
            + summary.terminationsFailed()
            + summary.rehiresFailed()
            + summary.workCenterChangesFailed()
            + summary.workingTimeChangesFailed()
            + summary.extraPaymentRegimeChangesFailed()
            + summary.contractReplacementsFailed()
            + summary.laborClassificationReplacementsFailed()
            + summary.costCenterReplacementsFailed()
            + summary.absencesFailed()
            + summary.payrollInputsFailed()
            + summary.personalDataFailed() == 0) {
            log.info("All lifecycle events completed successfully");
        } else {
            log.info("Failed lifecycle events:");
            for (LifecycleEventExecutionResult result : summary.results()) {
                if (!result.success()) {
                    log.info("  - employeeNumber={} event={} date={} error={}",
                            result.employeeNumber(),
                            result.eventType(),
                            result.effectiveDate(),
                            result.message());
                }
            }
        }

        log.info("==========================================");
    }

    /**
     * El ciclo, mes a mes ({@code workforce-loader#16}).
     *
     * <p>Una tabla y no una frase: lo que hace comprobable una semilla de nueve meses es poder
     * cruzar fila a fila lo que el loader dice que dejo con lo que la base tiene. Un
     * «se calcularon nueve meses» no se puede cruzar con nada.
     */
    public void printCycle(PayrollCycleSummary cycle) {
        if (!cycle.ranAnything()) {
            return;
        }

        log.info("==========================================");
        log.info("Ciclo de nomina ejecutado: {} meses, limite de retro {} meses atras",
                cycle.months().size(), cycle.retroLimitMonthsBack());
        log.info("  mes     ejecucion  recibos  no-val  error   retro  vigentes  cerrados  "
                + "horas  ausenc  fuera-lim  quitadas  seg");
        for (PayrollCycleMonth mes : cycle.months()) {
            log.info(String.format(
                    "  %-6d  %-9s  %7d  %6d  %5d  %6d  %8d  %8d  %5d  %6d  %9d  %8d  %4d",
                    mes.period(),
                    mes.runId() == null ? "?" : String.valueOf(mes.runId()),
                    mes.calculated(),
                    mes.notValid(),
                    mes.errors(),
                    mes.retroUnits(),
                    mes.retroRecalculated(),
                    mes.finalized(),
                    mes.horasAlMesCerrado(),
                    mes.ausenciasAlAnterior(),
                    mes.fueraDelLimite(),
                    mes.horasQuitadas(),
                    mes.secondsElapsed()));
        }
        log.info("  El mes {} se queda ABIERTO, calculado y con sus atrasos dentro.",
                cycle.openPeriod());
        log.info("  Tiempo total del ciclo: {} s ({} min).",
                cycle.secondsElapsed(), cycle.secondsElapsed() / 60);
        log.info("  Esto NO prueba que la base tenga eso: lo prueba el recuento en la base.");
        log.info("==========================================");
    }
}
