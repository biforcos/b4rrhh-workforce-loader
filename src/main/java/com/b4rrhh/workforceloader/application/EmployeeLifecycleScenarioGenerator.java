package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.domain.model.SyntheticEmployee;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@Component
public class EmployeeLifecycleScenarioGenerator {

    private final LoaderProperties properties;
    private final HireReferenceDataResolver hireReferenceDataResolver;
    private final WorkCenterMutationGenerator workCenterMutationGenerator;
    private final ContractMutationGenerator contractMutationGenerator;
    private final LaborClassificationMutationGenerator laborClassificationMutationGenerator;
    private final CostCenterMutationGenerator costCenterMutationGenerator;
    private final AbsenceScenarioGenerator absenceScenarioGenerator;
    private final PayrollInputScenarioGenerator payrollInputScenarioGenerator;

    public EmployeeLifecycleScenarioGenerator(
            LoaderProperties properties,
            HireReferenceDataResolver hireReferenceDataResolver,
            WorkCenterMutationGenerator workCenterMutationGenerator,
            ContractMutationGenerator contractMutationGenerator,
            LaborClassificationMutationGenerator laborClassificationMutationGenerator,
            CostCenterMutationGenerator costCenterMutationGenerator,
            AbsenceScenarioGenerator absenceScenarioGenerator,
            PayrollInputScenarioGenerator payrollInputScenarioGenerator
    ) {
        this.properties = properties;
        this.hireReferenceDataResolver = hireReferenceDataResolver;
        this.workCenterMutationGenerator = workCenterMutationGenerator;
        this.contractMutationGenerator = contractMutationGenerator;
        this.laborClassificationMutationGenerator = laborClassificationMutationGenerator;
        this.costCenterMutationGenerator = costCenterMutationGenerator;
        this.absenceScenarioGenerator = absenceScenarioGenerator;
        this.payrollInputScenarioGenerator = payrollInputScenarioGenerator;
    }

    public List<EmployeeLifecycleScenario> generate(List<SyntheticEmployee> employees) {
        LoaderProperties.Simulation simulation = properties.getSimulation();
        Random random = new Random(properties.getGeneration().getSeed() + 1);

        // El «hoy» de la simulacion, sin mirar el reloj para que la misma semilla de lo mismo
        // cualquier dia: hasta donde llega el ultimo cese posible.
        LocalDate simulationHorizon = properties.getGeneration().getHireDateTo()
                .plusDays(simulation.getTerminationMaxDaysAfterHire());

        String ruleSystemCode = normalizeCode(properties.getDefaults().getRuleSystemCode());
        ResolvedHireReferencePools referencePools = hireReferenceDataResolver.preloadPools(ruleSystemCode);

        LoaderProperties.WorkingTimeChange workingTimeChange = properties.getWorkingTimeChange();
        int workingTimeChangesPlanned = 0;

        // Azar propio para las horas extra: gastar del comun desplazaria toda la secuencia
        // posterior y la semilla entera cambiaria para anadir unas filas (workforce-loader#5).
        LoaderProperties.PayrollInput payrollInput = properties.getPayrollInput();
        Random payrollInputRandom = payrollInputScenarioGenerator.newRandom(
                properties.getGeneration().getSeed());

        List<EmployeeLifecycleScenario> scenarios = new ArrayList<>(employees.size());
        for (SyntheticEmployee employee : employees) {
            ResolvedHireData resolvedHireData = hireReferenceDataResolver
                .resolveFromPools(referencePools, ruleSystemCode, employee.hireDate(), random)
                    .withWorkingTimePercentage(employee.workingTimePercentage());
            String exitReasonCode = hireReferenceDataResolver.resolveExitReasonFromPools(referencePools, random);

            List<EmployeeLifecycleEvent> events = new ArrayList<>();
            events.add(new EmployeeLifecycleEvent(LifecycleEventType.HIRE, employee.hireDate()));

            LocalDate terminationDate = null;
            LocalDate rehireDate = null;
            if (random.nextDouble() < simulation.getTerminateRate()) {
                terminationDate = employee.hireDate().plusDays(randomDaysInRange(
                        simulation.getTerminationMinDaysAfterHire(),
                        simulation.getTerminationMaxDaysAfterHire(),
                        random
                ));
                events.add(new EmployeeLifecycleEvent(LifecycleEventType.TERMINATE, terminationDate));
            }

            if (terminationDate != null && random.nextDouble() < simulation.getRehireRateOfTerminated()) {
                rehireDate = terminationDate.plusDays(randomDaysInRange(
                        simulation.getRehireMinDaysAfterTermination(),
                        simulation.getRehireMaxDaysAfterTermination(),
                        random
                ));
                events.add(new EmployeeLifecycleEvent(LifecycleEventType.REHIRE, rehireDate));
            }

            ResolvedHireData rehireResolvedHireData = rehireDate == null
                    ? resolvedHireData
                    : planRehireResolvedHireData(resolvedHireData, ruleSystemCode, rehireDate, referencePools, random);

            List<ActiveWindow> activeWindows = buildActiveWindows(employee.hireDate(), terminationDate, rehireDate);
            addMutationEvents(events, activeWindows, simulation, random);
            events.addAll(absenceScenarioGenerator.generate(
                    activeWindows, simulationHorizon, referencePools.absenceTypes(), random));
            events.addAll(payrollInputScenarioGenerator.generate(
                    payrollInput, activeWindows, payrollInputRandom));

            if (workingTimeChangesPlanned < workingTimeChange.getEmployees()
                    && takesTheMidMonthWorkingTimeChange(workingTimeChange, activeWindows, resolvedHireData)) {
                events.add(new EmployeeLifecycleEvent(
                        LifecycleEventType.CHANGE_WORKING_TIME,
                        workingTimeChange.getDate()
                ));
                workingTimeChangesPlanned++;
            }

            events.sort(Comparator.comparing(EmployeeLifecycleEvent::effectiveDate));
                List<EmployeeLifecycleEvent> plannedEvents = addMutationPayloads(
                    events,
                    ruleSystemCode,
                    referencePools,
                    resolvedHireData,
                    rehireResolvedHireData,
                    random
                );

                scenarios.add(new EmployeeLifecycleScenario(
                    employee,
                    plannedEvents,
                    resolvedHireData,
                    rehireResolvedHireData,
                    exitReasonCode
                ));
        }

        return scenarios;
    }

    private List<EmployeeLifecycleEvent> addMutationPayloads(
            List<EmployeeLifecycleEvent> sortedEvents,
            String ruleSystemCode,
            ResolvedHireReferencePools pools,
            ResolvedHireData resolvedHireData,
            ResolvedHireData rehireResolvedHireData,
            Random random
    ) {
        EmployeeExecutionState state = EmployeeExecutionState.inactive();
        List<EmployeeLifecycleEvent> planned = new ArrayList<>(sortedEvents.size());

        for (EmployeeLifecycleEvent event : sortedEvents) {
            switch (event.eventType()) {
                case HIRE -> {
                    applyInitialHireState(state, resolvedHireData, event.effectiveDate());
                    planned.add(event);
                }
                case REHIRE -> {
                    applyRehireState(state, rehireResolvedHireData, event.effectiveDate());
                    planned.add(event);
                }
                case TERMINATE -> {
                    state.setActive(false);
                    state.setLastEffectiveDate(event.effectiveDate());
                    planned.add(event);
                }
                case CHANGE_WORK_CENTER -> {
                    if (!state.isActive()) {
                        continue;
                    }
                    var payload = workCenterMutationGenerator.generate(
                            ruleSystemCode,
                            state,
                            event.effectiveDate(),
                            random
                    );
                    if (payload.isEmpty()) {
                        continue;
                    }
                    state.setCurrentWorkCenterCode(payload.get().workCenterCode());
                    state.setLastEffectiveDate(event.effectiveDate());
                    planned.add(new EmployeeLifecycleEvent(event.eventType(), event.effectiveDate(), payload.get()));
                }
                case REPLACE_CONTRACT -> {
                    if (!state.isActive()) {
                        continue;
                    }
                    ContractReplaceEventPayload payload =
                            contractMutationGenerator.generate(pools, state, event.effectiveDate(), random);
                    state.setCurrentContractData(new ResolvedContractData(payload.contractCode(), payload.contractSubtypeCode()));
                    state.setLastEffectiveDate(event.effectiveDate());
                    planned.add(new EmployeeLifecycleEvent(event.eventType(), event.effectiveDate(), payload));
                }
                case REPLACE_LABOR_CLASSIFICATION -> {
                    if (!state.isActive()) {
                        continue;
                    }
                    LaborClassificationReplaceEventPayload payload = laborClassificationMutationGenerator.generate(pools, state, random);
                    state.setCurrentLaborClassificationData(
                            new ResolvedLaborClassificationData(payload.agreementCode(), payload.agreementCategoryCode())
                    );
                    state.setLastEffectiveDate(event.effectiveDate());
                    planned.add(new EmployeeLifecycleEvent(event.eventType(), event.effectiveDate(), payload));
                }
                case REPLACE_COST_CENTER -> {
                    if (!state.isActive() || !properties.getCostCenter().isEnabled()) {
                        continue;
                    }
                    CostCenterReplaceEventPayload payload =
                            costCenterMutationGenerator.generate(state, event.effectiveDate(), random);
                    state.setCurrentCostCenterDistribution(payload.allocations());
                    state.setLastEffectiveDate(event.effectiveDate());
                    planned.add(new EmployeeLifecycleEvent(event.eventType(), event.effectiveDate(), payload));
                }
                case CHANGE_WORKING_TIME -> {
                    if (!state.isActive()) {
                        continue;
                    }
                    state.setLastEffectiveDate(event.effectiveDate());
                    planned.add(new EmployeeLifecycleEvent(
                            event.eventType(),
                            event.effectiveDate(),
                            new WorkingTimeChangeEventPayload(properties.getWorkingTimeChange().getPercentage())
                    ));
                }
                case ABSENCE -> {
                    // Ya viene con su carga: la planifico el generador de ausencias, dentro de un
                    // periodo de presencia. No toca el estado: nada posterior depende de ella.
                    if (!state.isActive()) {
                        continue;
                    }
                    planned.add(event);
                }
                case PAYROLL_INPUT -> {
                    // Tambien viene con su carga. No toca el estado ni lo mira mas alla de estar
                    // de alta: una entrada es del periodo y no cambia nada de la relacion.
                    if (!state.isActive()) {
                        continue;
                    }
                    planned.add(event);
                }
            }
        }

        return planned;
    }

    /**
     * Se lo lleva quien ya estaba dado de alta antes de que empezara el mes del corte y sigue
     * sin cese: solo asi el cambio parte el mes entero en dos tramos y no un trozo suelto.
     *
     * La eleccion recorre los empleados en el orden en que vienen y se queda con los primeros
     * que valen, sin gastar azar: anadir este escenario no desplaza la secuencia del Random y
     * por tanto no cambia el resto de la siembra.
     */
    private static boolean takesTheMidMonthWorkingTimeChange(
            LoaderProperties.WorkingTimeChange workingTimeChange,
            List<ActiveWindow> activeWindows,
            ResolvedHireData resolvedHireData
    ) {
        if (!workingTimeChange.isEnabled() || workingTimeChange.getDate() == null) {
            return false;
        }
        // Cambiar la jornada al mismo porcentaje daria dos tramos con el mismo precio: dos
        // lineas iguales que no ensenan nada de lo que ADR-058 decidio.
        BigDecimal current = resolvedHireData.workingTimePercentage();
        if (current == null || current.compareTo(workingTimeChange.getPercentage()) == 0) {
            return false;
        }

        LocalDate monthStart = workingTimeChange.getDate().withDayOfMonth(1);
        for (ActiveWindow window : activeWindows) {
            if (window.endDate() == null && window.startDate().isBefore(monthStart)) {
                return true;
            }
        }
        return false;
    }

    private static void applyInitialHireState(
            EmployeeExecutionState state,
            ResolvedHireData resolvedHireData,
            LocalDate effectiveDate
    ) {
        state.setActive(true);
        state.setCurrentCompanyCode(resolvedHireData.companyCode());
        state.setCurrentWorkCenterCode(resolvedHireData.workCenterCode());
        state.setCurrentContractData(new ResolvedContractData(
                resolvedHireData.contractTypeCode(),
                resolvedHireData.contractSubtypeCode()
        ));
        state.setCurrentLaborClassificationData(new ResolvedLaborClassificationData(
                resolvedHireData.agreementCode(),
                resolvedHireData.agreementCategoryCode()
        ));
        state.setLastEffectiveDate(effectiveDate);
    }

        private static void applyRehireState(
            EmployeeExecutionState state,
            ResolvedHireData resolvedHireData,
            LocalDate effectiveDate
        ) {
        state.setActive(true);
        state.setCurrentCompanyCode(resolvedHireData.companyCode());
        state.setCurrentWorkCenterCode(resolvedHireData.workCenterCode());
        state.setCurrentContractData(new ResolvedContractData(
            resolvedHireData.contractTypeCode(),
            resolvedHireData.contractSubtypeCode()
        ));
        state.setCurrentLaborClassificationData(new ResolvedLaborClassificationData(
            resolvedHireData.agreementCode(),
            resolvedHireData.agreementCategoryCode()
        ));
        state.setLastEffectiveDate(effectiveDate);
    }

    private static int randomDaysInRange(int min, int max, Random random) {
        return min + random.nextInt(max - min + 1);
    }

    private List<ActiveWindow> buildActiveWindows(LocalDate hireDate, LocalDate terminationDate, LocalDate rehireDate) {
        List<ActiveWindow> windows = new ArrayList<>();

        if (terminationDate == null) {
            windows.add(new ActiveWindow(hireDate, null));
            return windows;
        }

        windows.add(new ActiveWindow(hireDate, terminationDate));
        if (rehireDate != null) {
            windows.add(new ActiveWindow(rehireDate, null));
        }

        return windows;
    }

    private void addMutationEvents(
            List<EmployeeLifecycleEvent> events,
            List<ActiveWindow> activeWindows,
            LoaderProperties.Simulation simulation,
            Random random
    ) {
        for (ActiveWindow window : activeWindows) {
            maybeAddMutationEvent(events, window, simulation.getWorkCenterChangeRate(), LifecycleEventType.CHANGE_WORK_CENTER, random);

            LocalDate contractReplaceDate = maybeAddMutationEvent(
                    events,
                    window,
                    simulation.getContractReplaceRate(),
                    LifecycleEventType.REPLACE_CONTRACT,
                    random
            );

            LocalDate laborReplaceDate = maybeAddMutationEvent(
                    events,
                    window,
                    simulation.getLaborClassificationReplaceRate(),
                    LifecycleEventType.REPLACE_LABOR_CLASSIFICATION,
                    random
            );

            if (contractReplaceDate != null
                    && laborReplaceDate == null
                    && simulation.getLaborClassificationReplaceRate() > 0
                    && random.nextDouble() < 0.20) {
                events.add(new EmployeeLifecycleEvent(
                        LifecycleEventType.REPLACE_LABOR_CLASSIFICATION,
                        contractReplaceDate
                ));
            }

            if (properties.getCostCenter().isEnabled()) {
                maybeAddMutationEvent(events, window, simulation.getCostCenterReplaceRate(), LifecycleEventType.REPLACE_COST_CENTER, random);
            }
        }
    }

    private LocalDate maybeAddMutationEvent(
            List<EmployeeLifecycleEvent> events,
            ActiveWindow window,
            double rate,
            LifecycleEventType eventType,
            Random random
    ) {
        if (rate <= 0 || random.nextDouble() >= rate) {
            return null;
        }

        LocalDate eventDate = pickDateInsideWindow(window, random);
        if (eventDate == null) {
            return null;
        }

        events.add(new EmployeeLifecycleEvent(eventType, eventDate));
        return eventDate;
    }

    private ResolvedHireData planRehireResolvedHireData(
            ResolvedHireData baseResolvedHireData,
            String ruleSystemCode,
            LocalDate rehireDate,
            ResolvedHireReferencePools pools,
            Random random
    ) {
        if (random.nextDouble() < 0.75) {
            return baseResolvedHireData;
        }

        EmployeeExecutionState seedState = EmployeeExecutionState.inactive();
        applyInitialHireState(seedState, baseResolvedHireData, LocalDate.MIN);

        double variation = random.nextDouble();
        if (variation < 0.60) {
            var payload = workCenterMutationGenerator.generate(
                    ruleSystemCode,
                    seedState,
                    rehireDate,
                    random
            );
            if (payload.isEmpty()) {
                return baseResolvedHireData;
            }
            return new ResolvedHireData(
                    baseResolvedHireData.companyCode(),
                    payload.get().workCenterCode(),
                    baseResolvedHireData.entryReasonCode(),
                    baseResolvedHireData.agreementCode(),
                    baseResolvedHireData.agreementCategoryCode(),
                    baseResolvedHireData.contractTypeCode(),
                    baseResolvedHireData.contractSubtypeCode(),
                    baseResolvedHireData.workingTimePercentage()
            );
        }

        if (variation < 0.80) {
            ContractReplaceEventPayload payload =
                    contractMutationGenerator.generate(pools, seedState, rehireDate, random);
            return new ResolvedHireData(
                    baseResolvedHireData.companyCode(),
                    baseResolvedHireData.workCenterCode(),
                    baseResolvedHireData.entryReasonCode(),
                    baseResolvedHireData.agreementCode(),
                    baseResolvedHireData.agreementCategoryCode(),
                    payload.contractCode(),
                    payload.contractSubtypeCode(),
                    baseResolvedHireData.workingTimePercentage()
            );
        }

        LaborClassificationReplaceEventPayload payload = laborClassificationMutationGenerator.generate(pools, seedState, random);
        return new ResolvedHireData(
                baseResolvedHireData.companyCode(),
                baseResolvedHireData.workCenterCode(),
                baseResolvedHireData.entryReasonCode(),
                payload.agreementCode(),
                payload.agreementCategoryCode(),
                baseResolvedHireData.contractTypeCode(),
                baseResolvedHireData.contractSubtypeCode(),
                baseResolvedHireData.workingTimePercentage()
        );
    }

    private LocalDate pickDateInsideWindow(ActiveWindow window, Random random) {
        if (window.endDate() != null) {
            long daysBetween = window.endDate().toEpochDay() - window.startDate().toEpochDay();
            if (daysBetween <= 1) {
                return null;
            }

            int offset = randomDaysInRange(1, (int) daysBetween - 1, random);
            return window.startDate().plusDays(offset);
        }

        int horizon = Math.max(2, properties.getSimulation().getTerminationMaxDaysAfterHire());
        int offset = randomDaysInRange(1, horizon, random);
        return window.startDate().plusDays(offset);
    }

    private static String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}