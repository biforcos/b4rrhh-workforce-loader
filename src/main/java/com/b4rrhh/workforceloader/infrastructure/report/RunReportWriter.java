package com.b4rrhh.workforceloader.infrastructure.report;

import com.b4rrhh.workforceloader.domain.model.LifecycleEventExecutionResult;
import com.b4rrhh.workforceloader.domain.model.LoaderRunSummary;
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
            + summary.contractReplacementsFailed()
            + summary.laborClassificationReplacementsFailed()
            + summary.costCenterReplacementsFailed()
            + summary.absencesFailed()
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
}
