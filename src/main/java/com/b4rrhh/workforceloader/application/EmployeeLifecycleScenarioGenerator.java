package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.domain.model.SyntheticEmployee;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.springframework.stereotype.Component;

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

    public EmployeeLifecycleScenarioGenerator(
            LoaderProperties properties,
            HireReferenceDataResolver hireReferenceDataResolver,
            WorkCenterMutationGenerator workCenterMutationGenerator,
            ContractMutationGenerator contractMutationGenerator,
            LaborClassificationMutationGenerator laborClassificationMutationGenerator,
            CostCenterMutationGenerator costCenterMutationGenerator
    ) {
        this.properties = properties;
        this.hireReferenceDataResolver = hireReferenceDataResolver;
        this.workCenterMutationGenerator = workCenterMutationGenerator;
        this.contractMutationGenerator = contractMutationGenerator;
        this.laborClassificationMutationGenerator = laborClassificationMutationGenerator;
        this.costCenterMutationGenerator = costCenterMutationGenerator;
    }

    public List<EmployeeLifecycleScenario> generate(List<SyntheticEmployee> employees) {
        LoaderProperties.Simulation simulation = properties.getSimulation();
        Random random = new Random(properties.getGeneration().getSeed() + 1);

        String ruleSystemCode = normalizeCode(properties.getDefaults().getRuleSystemCode());
        ResolvedHireReferencePools referencePools = hireReferenceDataResolver.preloadPools(ruleSystemCode);

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
                    CostCenterReplaceEventPayload payload = costCenterMutationGenerator.generate(state, random);
                    state.setCurrentCostCenterDistribution(payload.allocations());
                    state.setLastEffectiveDate(event.effectiveDate());
                    planned.add(new EmployeeLifecycleEvent(event.eventType(), event.effectiveDate(), payload));
                }
            }
        }

        return planned;
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