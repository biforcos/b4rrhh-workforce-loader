package com.b4rrhh.workforceloader.infrastructure.runner;

import com.b4rrhh.workforceloader.application.LoaderRunResult;
import com.b4rrhh.workforceloader.application.RunLifecycleSimulationUseCase;
import com.b4rrhh.workforceloader.application.RunPayrollCycleService;
import com.b4rrhh.workforceloader.domain.model.PayrollCycleSummary;
import com.b4rrhh.workforceloader.infrastructure.api.BackendTargetGuard;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import com.b4rrhh.workforceloader.infrastructure.config.RunMode;
import com.b4rrhh.workforceloader.infrastructure.report.RunReportWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class CliRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CliRunner.class);

    private final LoaderProperties properties;
    private final BackendTargetGuard backendTargetGuard;
    private final RunLifecycleSimulationUseCase runLifecycleSimulationUseCase;
    private final RunPayrollCycleService runPayrollCycleService;
    private final RunReportWriter runReportWriter;

    public CliRunner(
            LoaderProperties properties,
            BackendTargetGuard backendTargetGuard,
            RunLifecycleSimulationUseCase runLifecycleSimulationUseCase,
            RunPayrollCycleService runPayrollCycleService,
            RunReportWriter runReportWriter
    ) {
        this.properties = properties;
        this.backendTargetGuard = backendTargetGuard;
        this.runLifecycleSimulationUseCase = runLifecycleSimulationUseCase;
        this.runPayrollCycleService = runPayrollCycleService;
        this.runReportWriter = runReportWriter;
    }

    @Override
    public void run(String... args) {
        if (properties.getRun().getMode() != RunMode.LIFECYCLE) {
            log.info("Run mode '{}' is not supported in V1. Nothing to execute.", properties.getRun().getMode());
            return;
        }

        // Lo primero, antes de generar y antes de leer un solo catalogo: un
        // backend equivocado no solo se come las altas, tambien sirve los
        // codigos con los que se construyen (workforce-loader#8). Si esto
        // revienta, el proceso muere aqui y no ha escrito nada.
        backendTargetGuard.verifyBeforeTheRun();

        log.info(
            "Starting lifecycle simulation run: employees={}, dryRun={}",
            properties.getGeneration().getCount(),
            properties.getRun().isDryRun()
        );

        LoaderRunResult resultado = runLifecycleSimulationUseCase.run();
        runReportWriter.printSummary(resultado.summary(), properties.getRun().isDryRun());

        // El ciclo va DESPUES de la siembra y en el mismo proceso: calcular un mes sobre una
        // plantilla a medias daria recibos a medias (workforce-loader#16). Y va aqui y no dentro
        // del servicio de simulacion porque son dos cosas distintas: aquella siembra hechos del
        // empleado, esta ejecuta el ciclo del mes.
        PayrollCycleSummary ciclo = runPayrollCycleService.run(resultado.seeded());
        runReportWriter.printCycle(ciclo);
    }
}
