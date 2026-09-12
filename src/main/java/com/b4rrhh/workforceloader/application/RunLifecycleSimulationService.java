package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.domain.model.LifecycleEventExecutionResult;
import com.b4rrhh.workforceloader.domain.model.LoaderRunSummary;
import com.b4rrhh.workforceloader.domain.model.SyntheticEmployee;
import com.b4rrhh.workforceloader.domain.model.SyntheticPersonalData;
import com.b4rrhh.workforceloader.infrastructure.api.B4rrhhLifecycleClient;
import com.b4rrhh.workforceloader.infrastructure.api.BackendTargetMismatchException;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CreateAddressRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CreateContactRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CreateContractRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CreateCostCenterDistributionRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CreateIdentifierRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CreateLaborClassificationRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CreateWorkCenterRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CreateWorkingTimeRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.HireEmployeeRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.HireEmployeeResponse;
import com.b4rrhh.workforceloader.infrastructure.api.dto.RehireEmployeeRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.RehireEmployeeResponse;
import com.b4rrhh.workforceloader.infrastructure.api.dto.TerminateEmployeeRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.TerminateEmployeeResponse;
import com.b4rrhh.workforceloader.infrastructure.api.dto.UpsertAbsenceRequest;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import com.b4rrhh.workforceloader.infrastructure.generator.SyntheticEmployeeGenerator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class RunLifecycleSimulationService implements RunLifecycleSimulationUseCase {

    private final LoaderProperties properties;
    private final SyntheticEmployeeGenerator syntheticEmployeeGenerator;
    private final EmployeeLifecycleScenarioGenerator scenarioGenerator;
    private final B4rrhhLifecycleClient b4rrhhLifecycleClient;
    private final CostCenterMutationGenerator costCenterMutationGenerator;

    public RunLifecycleSimulationService(
            LoaderProperties properties,
            SyntheticEmployeeGenerator syntheticEmployeeGenerator,
            EmployeeLifecycleScenarioGenerator scenarioGenerator,
            B4rrhhLifecycleClient b4rrhhLifecycleClient,
            CostCenterMutationGenerator costCenterMutationGenerator
    ) {
        this.properties = properties;
        this.syntheticEmployeeGenerator = syntheticEmployeeGenerator;
        this.scenarioGenerator = scenarioGenerator;
        this.b4rrhhLifecycleClient = b4rrhhLifecycleClient;
        this.costCenterMutationGenerator = costCenterMutationGenerator;
    }

    @Override
    public LoaderRunSummary run() {
        validateConfiguration();

        List<SyntheticEmployee> employees = syntheticEmployeeGenerator.generateEmployees();
        List<EmployeeLifecycleScenario> scenarios = scenarioGenerator.generate(employees);

        List<LifecycleEventExecutionResult> results = new ArrayList<>();

        int hiresRequested = 0;
        int hiresSuccess = 0;
        int hiresFailed = 0;

        int terminationsRequested = 0;
        int terminationsSuccess = 0;
        int terminationsFailed = 0;

        int rehiresRequested = 0;
        int rehiresSuccess = 0;
        int rehiresFailed = 0;

        int workCenterChangesRequested = 0;
        int workCenterChangesSuccess = 0;
        int workCenterChangesFailed = 0;

        int workingTimeChangesRequested = 0;
        int workingTimeChangesSuccess = 0;
        int workingTimeChangesFailed = 0;

        int contractReplacementsRequested = 0;
        int contractReplacementsSuccess = 0;
        int contractReplacementsFailed = 0;

        int laborClassificationReplacementsRequested = 0;
        int laborClassificationReplacementsSuccess = 0;
        int laborClassificationReplacementsFailed = 0;

        int costCenterReplacementsRequested = 0;
        int costCenterReplacementsSuccess = 0;
        int costCenterReplacementsFailed = 0;

        int absencesRequested = 0;
        int absencesSuccess = 0;
        int absencesFailed = 0;

        PersonalDataTally personalData = new PersonalDataTally();

        for (EmployeeLifecycleScenario scenario : scenarios) {
            SyntheticEmployee employee = scenario.syntheticEmployee();
            ResolvedHireData employeeResolvedHireData = scenario.resolvedHireData();
            ResolvedHireData employeeRehireResolvedHireData = scenario.rehireResolvedHireData();
            String employeeExitReasonCode = scenario.exitReasonCode();
            EmployeeExecutionState executionState = EmployeeExecutionState.inactive();

            boolean scenarioCanContinue = true;

            for (EmployeeLifecycleEvent event : scenario.events()) {
                if (!scenarioCanContinue) {
                    break;
                }

                EventOutcome outcome = EventOutcome.failure("Unsupported lifecycle event: " + event.eventType());
                boolean hireSucceeded = false;
                switch (event.eventType()) {
                    case HIRE -> {
                        hiresRequested++;
                        try {
                            HireEmployeeRequest request = toHireRequest(employee, event, employeeResolvedHireData);
                            HireResult hireResult = executeHire(request);
                            outcome = hireResult.outcome();
                            if (outcome.success()) {
                                hiresSuccess++;
                                hireSucceeded = true;
                                if (hireResult.employeeNumber() != null) {
                                    employee = employee.withEmployeeNumber(hireResult.employeeNumber());
                                }
                                applyInitialHireState(executionState, employeeResolvedHireData, event.effectiveDate(), request.costCenterDistribution());
                            } else {
                                hiresFailed++;
                                scenarioCanContinue = false;
                            }
                        } catch (IllegalArgumentException ex) {
                            hiresFailed++;
                            outcome = EventOutcome.failure(ex.getMessage());
                            scenarioCanContinue = false;
                        }
                    }
                    case TERMINATE -> {
                        terminationsRequested++;
                        TerminateEmployeeRequest request = toTerminateRequest(event, employeeExitReasonCode);
                        outcome = executeTerminate(employee, request);
                        if (outcome.success()) {
                            terminationsSuccess++;
                            applyTerminationState(executionState, event.effectiveDate());
                        } else {
                            terminationsFailed++;
                            scenarioCanContinue = false;
                        }
                    }
                    case REHIRE -> {
                        rehiresRequested++;
                        try {
                            RehireEmployeeRequest request = toRehireRequest(employee, event, employeeRehireResolvedHireData);
                            outcome = executeRehire(employee, request);
                            if (outcome.success()) {
                                rehiresSuccess++;
                                applyRehireState(executionState, employeeRehireResolvedHireData, event.effectiveDate(), request.costCenterDistribution());
                            } else {
                                rehiresFailed++;
                                scenarioCanContinue = false;
                            }
                        } catch (IllegalArgumentException ex) {
                            rehiresFailed++;
                            outcome = EventOutcome.failure(ex.getMessage());
                            scenarioCanContinue = false;
                        }
                    }
                    case CHANGE_WORK_CENTER -> {
                        workCenterChangesRequested++;
                        outcome = executePlannedWorkCenterChange(employee, event, executionState);
                        if (outcome.success()) {
                            workCenterChangesSuccess++;
                        } else {
                            workCenterChangesFailed++;
                            scenarioCanContinue = false;
                        }
                    }
                    case REPLACE_CONTRACT -> {
                        contractReplacementsRequested++;
                        outcome = executePlannedContractReplace(employee, event, executionState);
                        if (outcome.success()) {
                            contractReplacementsSuccess++;
                        } else {
                            contractReplacementsFailed++;
                            scenarioCanContinue = false;
                        }
                    }
                    case REPLACE_LABOR_CLASSIFICATION -> {
                        laborClassificationReplacementsRequested++;
                        outcome = executePlannedLaborClassificationReplace(employee, event, executionState);
                        if (outcome.success()) {
                            laborClassificationReplacementsSuccess++;
                        } else {
                            laborClassificationReplacementsFailed++;
                            scenarioCanContinue = false;
                        }
                    }
                    case REPLACE_COST_CENTER -> {
                        costCenterReplacementsRequested++;
                        outcome = executePlannedCostCenterReplace(employee, event, executionState);
                        if (outcome.success()) {
                            costCenterReplacementsSuccess++;
                        } else {
                            costCenterReplacementsFailed++;
                            scenarioCanContinue = false;
                        }
                    }
                    case CHANGE_WORKING_TIME -> {
                        workingTimeChangesRequested++;
                        outcome = executePlannedWorkingTimeChange(employee, event, executionState);
                        if (outcome.success()) {
                            workingTimeChangesSuccess++;
                        } else {
                            workingTimeChangesFailed++;
                            scenarioCanContinue = false;
                        }
                    }
                    case ABSENCE -> {
                        // Una ausencia rechazada se anota y el escenario sigue: ningun evento
                        // posterior depende de ella (workforce-loader#5).
                        absencesRequested++;
                        outcome = executePlannedAbsence(employee, event, executionState);
                        if (outcome.success()) {
                            absencesSuccess++;
                        } else {
                            absencesFailed++;
                        }
                    }
                }

                results.add(new LifecycleEventExecutionResult(
                        employee.employeeNumber(),
                        event.eventType().name(),
                        event.effectiveDate(),
                        outcome.success(),
                        outcome.message()
                ));

                if (hireSucceeded) {
                    // Dirección, contacto e identificador no caben en el payload del alta: van justo
                    // detrás, ya con el número que devolvió el backend. Si una falla, se anota y el
                    // escenario sigue —ningún evento posterior depende de ellos— (workforce-loader#3).
                    executePersonalData(employee, event.effectiveDate(), results, personalData);
                }
            }
        }

        return new LoaderRunSummary(
                employees.size(),
                hiresRequested,
                hiresSuccess,
                hiresFailed,
                terminationsRequested,
                terminationsSuccess,
                terminationsFailed,
                rehiresRequested,
                rehiresSuccess,
                rehiresFailed,
                workCenterChangesRequested,
                workCenterChangesSuccess,
                workCenterChangesFailed,
                workingTimeChangesRequested,
                workingTimeChangesSuccess,
                workingTimeChangesFailed,
                contractReplacementsRequested,
                contractReplacementsSuccess,
                contractReplacementsFailed,
                laborClassificationReplacementsRequested,
                laborClassificationReplacementsSuccess,
                laborClassificationReplacementsFailed,
                costCenterReplacementsRequested,
                costCenterReplacementsSuccess,
                costCenterReplacementsFailed,
                absencesRequested,
                absencesSuccess,
                absencesFailed,
                personalData.requested,
                personalData.success,
                personalData.failed,
                results
        );
    }

    private void executePersonalData(
            SyntheticEmployee employee,
            LocalDate hireDate,
            List<LifecycleEventExecutionResult> results,
            PersonalDataTally tally
    ) {
        for (SyntheticPersonalData.Address address : employee.personalData().addresses()) {
            EventOutcome outcome = executeCreateAddress(employee, toCreateAddressRequest(address));
            tally.record(outcome);
            results.add(new LifecycleEventExecutionResult(
                    employee.employeeNumber(), "CREATE_ADDRESS", address.startDate(), outcome.success(), outcome.message()
            ));
        }
        for (SyntheticPersonalData.Contact contact : employee.personalData().contacts()) {
            EventOutcome outcome = executeCreateContact(employee, new CreateContactRequest(
                    normalizeCode(contact.contactTypeCode()), contact.contactValue()
            ));
            tally.record(outcome);
            results.add(new LifecycleEventExecutionResult(
                    employee.employeeNumber(), "CREATE_CONTACT", hireDate, outcome.success(), outcome.message()
            ));
        }
        for (SyntheticPersonalData.Identifier identifier : employee.personalData().identifiers()) {
            EventOutcome outcome = executeCreateIdentifier(employee, new CreateIdentifierRequest(
                    normalizeCode(identifier.identifierTypeCode()),
                    identifier.identifierValue(),
                    normalizeCode(identifier.issuingCountryCode()),
                    identifier.expirationDate(),
                    identifier.primary()
            ));
            tally.record(outcome);
            results.add(new LifecycleEventExecutionResult(
                    employee.employeeNumber(), "CREATE_IDENTIFIER", hireDate, outcome.success(), outcome.message()
            ));
        }
    }

    private static CreateAddressRequest toCreateAddressRequest(SyntheticPersonalData.Address address) {
        return new CreateAddressRequest(
                normalizeCode(address.addressTypeCode()),
                address.street(),
                address.city(),
                normalizeCode(address.countryCode()),
                address.postalCode(),
                address.regionCode(),
                address.startDate(),
                address.endDate()
        );
    }

    private EventOutcome executeCreateAddress(SyntheticEmployee employee, CreateAddressRequest request) {
        if (properties.getRun().isDryRun()) {
            return EventOutcome.success("DRY-RUN payload -> " + summarizeEmployee(employee)
                    + ", addressTypeCode=" + request.addressTypeCode()
                    + ", city=" + request.city()
                    + ", countryCode=" + request.countryCode()
                    + ", startDate=" + request.startDate()
                    + ", endDate=" + request.endDate());
        }

        try {
            b4rrhhLifecycleClient.createAddress(
                    normalizeCode(employee.ruleSystemCode()),
                    normalizeCode(employee.employeeTypeCode()),
                    employee.employeeNumber(),
                    request
            );
            return EventOutcome.success("Address create call completed: " + request.addressTypeCode());
        } catch (Exception ex) {
            return failureUnlessTheBackendIsWrong(ex);
        }
    }

    private EventOutcome executeCreateContact(SyntheticEmployee employee, CreateContactRequest request) {
        if (properties.getRun().isDryRun()) {
            return EventOutcome.success("DRY-RUN payload -> " + summarizeEmployee(employee)
                    + ", contactTypeCode=" + request.contactTypeCode()
                    + ", contactValue=" + request.contactValue());
        }

        try {
            b4rrhhLifecycleClient.createContact(
                    normalizeCode(employee.ruleSystemCode()),
                    normalizeCode(employee.employeeTypeCode()),
                    employee.employeeNumber(),
                    request
            );
            return EventOutcome.success("Contact create call completed: " + request.contactTypeCode());
        } catch (Exception ex) {
            return failureUnlessTheBackendIsWrong(ex);
        }
    }

    private EventOutcome executeCreateIdentifier(SyntheticEmployee employee, CreateIdentifierRequest request) {
        if (properties.getRun().isDryRun()) {
            return EventOutcome.success("DRY-RUN payload -> " + summarizeEmployee(employee)
                    + ", identifierTypeCode=" + request.identifierTypeCode()
                    + ", identifierValue=" + request.identifierValue()
                    + ", issuingCountryCode=" + request.issuingCountryCode()
                    + ", isPrimary=" + request.isPrimary());
        }

        try {
            b4rrhhLifecycleClient.createIdentifier(
                    normalizeCode(employee.ruleSystemCode()),
                    normalizeCode(employee.employeeTypeCode()),
                    employee.employeeNumber(),
                    request
            );
            return EventOutcome.success("Identifier create call completed: " + request.identifierTypeCode());
        } catch (Exception ex) {
            return failureUnlessTheBackendIsWrong(ex);
        }
    }

    private static String summarizeEmployee(SyntheticEmployee employee) {
        return "employeeNumber=" + employee.employeeNumber()
                + ", ruleSystemCode=" + normalizeCode(employee.ruleSystemCode())
                + ", employeeTypeCode=" + normalizeCode(employee.employeeTypeCode());
    }

    /** Recuento de las llamadas de datos personales; van juntas en el resumen porque se piden juntas, tras el alta. */
    private static final class PersonalDataTally {
        private int requested;
        private int success;
        private int failed;

        void record(EventOutcome outcome) {
            requested++;
            if (outcome.success()) {
                success++;
            } else {
                failed++;
            }
        }
    }

    private HireEmployeeRequest toHireRequest(
            SyntheticEmployee employee,
            EmployeeLifecycleEvent event,
            ResolvedHireData resolvedHireData
    ) {
        return new HireEmployeeRequest(
                normalizeCode(employee.ruleSystemCode()),
                normalizeCode(employee.employeeTypeCode()),
                employee.firstName(),
                employee.lastName1(),
                employee.lastName2(),
                employee.preferredName(),
                event.effectiveDate(),
                normalizeCode(resolvedHireData.entryReasonCode()),
                normalizeCode(resolvedHireData.companyCode()),
                normalizeCode(resolvedHireData.workCenterCode()),
                new HireEmployeeRequest.Contract(
                        normalizeCode(resolvedHireData.contractTypeCode()),
                        normalizeCode(resolvedHireData.contractSubtypeCode())
                ),
                new HireEmployeeRequest.LaborClassification(
                        normalizeCode(resolvedHireData.agreementCode()),
                        normalizeCode(resolvedHireData.agreementCategoryCode())
                ),
                buildHireWorkingTime(employee, resolvedHireData),
                buildHireCostCenterDistribution(employee, event.effectiveDate())
        );
    }

    private TerminateEmployeeRequest toTerminateRequest(EmployeeLifecycleEvent event, String exitReasonCode) {
        return new TerminateEmployeeRequest(
                event.effectiveDate(),
                normalizeCode(exitReasonCode)
        );
    }

    private RehireEmployeeRequest toRehireRequest(
            SyntheticEmployee employee,
            EmployeeLifecycleEvent event,
            ResolvedHireData resolvedHireData
    ) {
        return new RehireEmployeeRequest(
                event.effectiveDate(),
                normalizeCode(resolvedHireData.entryReasonCode()),
                normalizeCode(resolvedHireData.companyCode()),
                new RehireEmployeeRequest.WorkCenter(normalizeCode(resolvedHireData.workCenterCode())),
                new RehireEmployeeRequest.Contract(
                        normalizeCode(resolvedHireData.contractTypeCode()),
                        normalizeCode(resolvedHireData.contractSubtypeCode())
                ),
                new RehireEmployeeRequest.LaborClassification(
                        normalizeCode(resolvedHireData.agreementCode()),
                        normalizeCode(resolvedHireData.agreementCategoryCode())
                ),
                buildRehireWorkingTime(employee, resolvedHireData),
                buildRehireCostCenterDistribution(employee, event.effectiveDate())
        );
    }

            private HireEmployeeRequest.WorkingTime buildHireWorkingTime(
                SyntheticEmployee employee,
                ResolvedHireData resolvedHireData
            ) {
            return new HireEmployeeRequest.WorkingTime(
                requireWorkingTimePercentage(resolvedHireData, employee, LifecycleEventType.HIRE)
            );
            }

            private RehireEmployeeRequest.WorkingTime buildRehireWorkingTime(
                SyntheticEmployee employee,
                ResolvedHireData resolvedHireData
            ) {
            return new RehireEmployeeRequest.WorkingTime(
                requireWorkingTimePercentage(resolvedHireData, employee, LifecycleEventType.REHIRE)
            );
            }

            private BigDecimal requireWorkingTimePercentage(
                ResolvedHireData resolvedHireData,
                SyntheticEmployee employee,
                LifecycleEventType eventType
            ) {
            BigDecimal workingTimePercentage = resolvedHireData.workingTimePercentage();
            if (workingTimePercentage == null) {
                throw new IllegalArgumentException(
                    "Missing workingTimePercentage for " + eventType + " event of employee " + employee.employeeNumber()
                );
            }
            if (workingTimePercentage.compareTo(BigDecimal.ZERO) <= 0 || workingTimePercentage.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException(
                    "Invalid workingTimePercentage " + workingTimePercentage
                        + " for " + eventType
                        + " event of employee " + employee.employeeNumber()
                        + ". Expected value greater than 0 and less than or equal to 100."
                );
            }
            return workingTimePercentage.stripTrailingZeros();
            }

    // ADR-057: el cambio de centro es un alta con su fecha de inicio, y cerrar la
    // asignacion anterior es consecuencia del backend, no una orden del loader
    // (workforce-loader#7). Va sin fin porque la asignacion que desplaza tambien
    // esta abierta: el loader solo muta dentro de una presencia viva, y quien
    // cierra la ventana es el cese.
    private CreateWorkCenterRequest toCreateWorkCenterRequest(
            EmployeeLifecycleEvent event,
            WorkCenterChangeEventPayload payload
    ) {
        return new CreateWorkCenterRequest(
                normalizeCode(payload.workCenterCode()),
                event.effectiveDate(),
                null
        );
    }

    // Sin fecha de fin, igual que el centro de trabajo: la ventana nueva queda abierta y es
    // el backend quien cierra la anterior el dia de antes (ADR-057).
    private CreateWorkingTimeRequest toCreateWorkingTimeRequest(
            EmployeeLifecycleEvent event,
            WorkingTimeChangeEventPayload payload
    ) {
        return new CreateWorkingTimeRequest(
                event.effectiveDate(),
                null,
                payload.workingTimePercentage()
        );
    }

    private CreateContractRequest toCreateContractRequest(
            EmployeeLifecycleEvent event,
            ContractReplaceEventPayload payload
    ) {
        return new CreateContractRequest(
                normalizeCode(payload.contractCode()),
                normalizeCode(payload.contractSubtypeCode()),
                event.effectiveDate(),
                null
        );
    }

    private CreateLaborClassificationRequest toCreateLaborClassificationRequest(
            EmployeeLifecycleEvent event,
            LaborClassificationReplaceEventPayload payload
    ) {
        return new CreateLaborClassificationRequest(
                normalizeCode(payload.agreementCode()),
                normalizeCode(payload.agreementCategoryCode()),
                event.effectiveDate(),
                null
        );
    }

    private CreateCostCenterDistributionRequest toCreateCostCenterDistributionRequest(
            EmployeeLifecycleEvent event,
            CostCenterReplaceEventPayload payload
    ) {
        return new CreateCostCenterDistributionRequest(
                event.effectiveDate(),
                null,
                toApiDistributionItems(payload.allocations())
        );
    }

    private EventOutcome executePlannedWorkCenterChange(
            SyntheticEmployee employee,
            EmployeeLifecycleEvent event,
            EmployeeExecutionState state
    ) {
        if (!state.isActive()) {
            return EventOutcome.failure("Cannot change work center on inactive employee state");
        }
        if (!(event.payload() instanceof WorkCenterChangeEventPayload payload)) {
            return EventOutcome.failure("Missing WorkCenterChangeEventPayload for CHANGE_WORK_CENTER");
        }

        CreateWorkCenterRequest request = toCreateWorkCenterRequest(event, payload);
        EventOutcome outcome = executeWorkCenterChange(employee, request);
        if (outcome.success()) {
            state.setCurrentWorkCenterCode(payload.workCenterCode());
            state.setLastEffectiveDate(event.effectiveDate());
        }
        return outcome;
    }

    private EventOutcome executePlannedWorkingTimeChange(
            SyntheticEmployee employee,
            EmployeeLifecycleEvent event,
            EmployeeExecutionState state
    ) {
        if (!state.isActive()) {
            return EventOutcome.failure("Cannot change working time on inactive employee state");
        }
        if (!(event.payload() instanceof WorkingTimeChangeEventPayload payload)) {
            return EventOutcome.failure("Missing WorkingTimeChangeEventPayload for CHANGE_WORKING_TIME");
        }

        CreateWorkingTimeRequest request = toCreateWorkingTimeRequest(event, payload);
        EventOutcome outcome = executeWorkingTimeChange(employee, request);
        if (outcome.success()) {
            state.setLastEffectiveDate(event.effectiveDate());
        }
        return outcome;
    }

    private EventOutcome executePlannedContractReplace(
            SyntheticEmployee employee,
            EmployeeLifecycleEvent event,
            EmployeeExecutionState state
    ) {
        if (!state.isActive()) {
            return EventOutcome.failure("Cannot replace contract on inactive employee state");
        }
        if (!(event.payload() instanceof ContractReplaceEventPayload payload)) {
            return EventOutcome.failure("Missing ContractReplaceEventPayload for REPLACE_CONTRACT");
        }

        CreateContractRequest request = toCreateContractRequest(event, payload);
        EventOutcome outcome = executeContractReplace(employee, request);
        if (outcome.success()) {
            state.setCurrentContractData(new ResolvedContractData(payload.contractCode(), payload.contractSubtypeCode()));
            state.setLastEffectiveDate(event.effectiveDate());
        }
        return outcome;
    }

    private EventOutcome executePlannedLaborClassificationReplace(
            SyntheticEmployee employee,
            EmployeeLifecycleEvent event,
            EmployeeExecutionState state
    ) {
        if (!state.isActive()) {
            return EventOutcome.failure("Cannot replace labor classification on inactive employee state");
        }
        if (!(event.payload() instanceof LaborClassificationReplaceEventPayload payload)) {
            return EventOutcome.failure("Missing LaborClassificationReplaceEventPayload for REPLACE_LABOR_CLASSIFICATION");
        }

        CreateLaborClassificationRequest request = toCreateLaborClassificationRequest(event, payload);
        EventOutcome outcome = executeLaborClassificationReplace(employee, request);
        if (outcome.success()) {
            state.setCurrentLaborClassificationData(
                    new ResolvedLaborClassificationData(payload.agreementCode(), payload.agreementCategoryCode())
            );
            state.setLastEffectiveDate(event.effectiveDate());
        }
        return outcome;
    }

    private EventOutcome executePlannedCostCenterReplace(
            SyntheticEmployee employee,
            EmployeeLifecycleEvent event,
            EmployeeExecutionState state
    ) {
        if (!state.isActive()) {
            return EventOutcome.failure("Cannot replace cost center on inactive employee state");
        }
        if (!(event.payload() instanceof CostCenterReplaceEventPayload payload)) {
            return EventOutcome.failure("Missing CostCenterReplaceEventPayload for REPLACE_COST_CENTER");
        }

        CreateCostCenterDistributionRequest request = toCreateCostCenterDistributionRequest(event, payload);
        EventOutcome outcome = executeCostCenterReplace(employee, request);
        if (outcome.success()) {
            state.setCurrentCostCenterDistribution(payload.allocations());
            state.setLastEffectiveDate(event.effectiveDate());
        }
        return outcome;
    }

    private EventOutcome executePlannedAbsence(
            SyntheticEmployee employee,
            EmployeeLifecycleEvent event,
            EmployeeExecutionState state
    ) {
        if (!state.isActive()) {
            return EventOutcome.failure("Cannot register absence on inactive employee state");
        }
        if (!(event.payload() instanceof AbsenceEventPayload payload)) {
            return EventOutcome.failure("Missing AbsenceEventPayload for ABSENCE");
        }

        String absenceTypeCode = normalizeCode(payload.absenceTypeCode());
        UpsertAbsenceRequest request = new UpsertAbsenceRequest(payload.endDate(), null);

        if (properties.getRun().isDryRun()) {
            return EventOutcome.success("DRY-RUN payload -> " + summarizeEmployee(employee)
                    + ", absenceTypeCode=" + absenceTypeCode
                    + ", startDate=" + event.effectiveDate()
                    + ", endDate=" + request.endDate());
        }

        try {
            b4rrhhLifecycleClient.upsertAbsence(
                    normalizeCode(employee.ruleSystemCode()),
                    normalizeCode(employee.employeeTypeCode()),
                    employee.employeeNumber(),
                    absenceTypeCode,
                    event.effectiveDate(),
                    request
            );
            return EventOutcome.success("Absence upsert call completed: " + absenceTypeCode);
        } catch (Exception ex) {
            return failureUnlessTheBackendIsWrong(ex);
        }
    }

    private void applyInitialHireState(
            EmployeeExecutionState state,
            ResolvedHireData resolvedHireData,
            LocalDate effectiveDate,
            HireEmployeeRequest.CostCenterDistribution costCenterDistribution
    ) {
        state.setActive(true);
        state.setCurrentWorkCenterCode(normalizeCode(resolvedHireData.workCenterCode()));
        state.setCurrentContractData(new ResolvedContractData(
                normalizeCode(resolvedHireData.contractTypeCode()),
                normalizeCode(resolvedHireData.contractSubtypeCode())
        ));
        state.setCurrentLaborClassificationData(new ResolvedLaborClassificationData(
                normalizeCode(resolvedHireData.agreementCode()),
                normalizeCode(resolvedHireData.agreementCategoryCode())
        ));
        state.setCurrentCostCenterDistribution(toSimulationAllocations(costCenterDistribution));
        state.setLastEffectiveDate(effectiveDate);
    }

    private void applyRehireState(
            EmployeeExecutionState state,
            ResolvedHireData resolvedHireData,
            LocalDate effectiveDate,
            RehireEmployeeRequest.CostCenterDistribution costCenterDistribution
    ) {
        state.setActive(true);
        state.setCurrentWorkCenterCode(normalizeCode(resolvedHireData.workCenterCode()));
        state.setCurrentContractData(new ResolvedContractData(
                normalizeCode(resolvedHireData.contractTypeCode()),
                normalizeCode(resolvedHireData.contractSubtypeCode())
        ));
        state.setCurrentLaborClassificationData(new ResolvedLaborClassificationData(
                normalizeCode(resolvedHireData.agreementCode()),
                normalizeCode(resolvedHireData.agreementCategoryCode())
        ));
        state.setCurrentCostCenterDistribution(toSimulationAllocations(costCenterDistribution));
        state.setLastEffectiveDate(effectiveDate);
    }

    private void applyTerminationState(EmployeeExecutionState state, LocalDate effectiveDate) {
        state.setActive(false);
        state.setLastEffectiveDate(effectiveDate);
    }

    private static List<SimulationCostCenterAllocation> toSimulationAllocations(
            HireEmployeeRequest.CostCenterDistribution distribution
    ) {
        if (distribution == null || distribution.items() == null) {
            return null;
        }
        return distribution.items().stream()
                .map(item -> new SimulationCostCenterAllocation(
                        normalizeCode(item.costCenterCode()),
                        item.allocationPercentage()
                ))
                .toList();
    }

    private static List<SimulationCostCenterAllocation> toSimulationAllocations(
            RehireEmployeeRequest.CostCenterDistribution distribution
    ) {
        if (distribution == null || distribution.items() == null) {
            return null;
        }
        return distribution.items().stream()
                .map(item -> new SimulationCostCenterAllocation(
                        normalizeCode(item.costCenterCode()),
                        item.allocationPercentage()
                ))
                .toList();
    }

    private static List<CreateCostCenterDistributionRequest.Item> toApiDistributionItems(
            List<SimulationCostCenterAllocation> allocations
    ) {
        if (allocations == null) {
            return List.of();
        }
        return allocations.stream()
                .map(allocation -> new CreateCostCenterDistributionRequest.Item(
                        normalizeCode(allocation.costCenterCode()),
                        allocation.allocationPercentage()
                ))
                .toList();
    }

    private HireResult executeHire(HireEmployeeRequest request) {
        if (properties.getRun().isDryRun()) {
            return new HireResult(EventOutcome.success("DRY-RUN payload -> " + summarizeHirePayload(request)), null);
        }

        try {
            HireEmployeeResponse response = b4rrhhLifecycleClient.hire(request);
            return new HireResult(
                    EventOutcome.success(summarizeApiResponse("Hire", response.status(), response.message())),
                    response.employeeNumber()
            );
        } catch (Exception ex) {
            return new HireResult(failureUnlessTheBackendIsWrong(ex), null);
        }
    }

    private record HireResult(EventOutcome outcome, String employeeNumber) {}

    private EventOutcome executeTerminate(SyntheticEmployee employee, TerminateEmployeeRequest request) {
        if (properties.getRun().isDryRun()) {
            return EventOutcome.success("DRY-RUN payload -> " + summarizeTerminatePayload(employee, request));
        }

        try {
            TerminateEmployeeResponse response = b4rrhhLifecycleClient.terminate(
                    normalizeCode(employee.ruleSystemCode()),
                    normalizeCode(employee.employeeTypeCode()),
                    employee.employeeNumber(),
                    request
            );
            return EventOutcome.success(summarizeApiResponse("Terminate", response.status(), response.message()));
        } catch (Exception ex) {
            return failureUnlessTheBackendIsWrong(ex);
        }
    }

    private EventOutcome executeRehire(SyntheticEmployee employee, RehireEmployeeRequest request) {
        if (properties.getRun().isDryRun()) {
            return EventOutcome.success("DRY-RUN payload -> " + summarizeRehirePayload(employee, request));
        }

        try {
            RehireEmployeeResponse response = b4rrhhLifecycleClient.rehire(
                    normalizeCode(employee.ruleSystemCode()),
                    normalizeCode(employee.employeeTypeCode()),
                    employee.employeeNumber(),
                    request
            );
            return EventOutcome.success(summarizeApiResponse("Rehire", response.status(), response.message()));
        } catch (Exception ex) {
            return failureUnlessTheBackendIsWrong(ex);
        }
    }

    private EventOutcome executeWorkCenterChange(SyntheticEmployee employee, CreateWorkCenterRequest request) {
        if (properties.getRun().isDryRun()) {
            return EventOutcome.success("DRY-RUN payload -> " + summarizeWorkCenterChangePayload(employee, request));
        }

        try {
            b4rrhhLifecycleClient.createWorkCenter(
                    normalizeCode(employee.ruleSystemCode()),
                    normalizeCode(employee.employeeTypeCode()),
                    employee.employeeNumber(),
                    request
            );
            return EventOutcome.success("Work center create call completed");
        } catch (Exception ex) {
            return failureUnlessTheBackendIsWrong(ex);
        }
    }

    private EventOutcome executeWorkingTimeChange(SyntheticEmployee employee, CreateWorkingTimeRequest request) {
        if (properties.getRun().isDryRun()) {
            return EventOutcome.success("DRY-RUN payload -> " + summarizeWorkingTimeChangePayload(employee, request));
        }

        try {
            b4rrhhLifecycleClient.createWorkingTime(
                    normalizeCode(employee.ruleSystemCode()),
                    normalizeCode(employee.employeeTypeCode()),
                    employee.employeeNumber(),
                    request
            );
            return EventOutcome.success("Working time create call completed");
        } catch (Exception ex) {
            return failureUnlessTheBackendIsWrong(ex);
        }
    }

    private EventOutcome executeContractReplace(SyntheticEmployee employee, CreateContractRequest request) {
        if (properties.getRun().isDryRun()) {
            return EventOutcome.success("DRY-RUN payload -> " + summarizeContractReplacePayload(employee, request));
        }

        try {
            b4rrhhLifecycleClient.createContract(
                    normalizeCode(employee.ruleSystemCode()),
                    normalizeCode(employee.employeeTypeCode()),
                    employee.employeeNumber(),
                    request
            );
            return EventOutcome.success("Contract create call completed");
        } catch (Exception ex) {
            return failureUnlessTheBackendIsWrong(ex);
        }
    }

    private EventOutcome executeLaborClassificationReplace(
            SyntheticEmployee employee,
            CreateLaborClassificationRequest request
    ) {
        if (properties.getRun().isDryRun()) {
            return EventOutcome.success("DRY-RUN payload -> " + summarizeLaborClassificationReplacePayload(employee, request));
        }

        try {
            b4rrhhLifecycleClient.createLaborClassification(
                    normalizeCode(employee.ruleSystemCode()),
                    normalizeCode(employee.employeeTypeCode()),
                    employee.employeeNumber(),
                    request
            );
            return EventOutcome.success("Labor classification create call completed");
        } catch (Exception ex) {
            return failureUnlessTheBackendIsWrong(ex);
        }
    }

    private EventOutcome executeCostCenterReplace(
            SyntheticEmployee employee,
            CreateCostCenterDistributionRequest request
    ) {
        if (properties.getRun().isDryRun()) {
            return EventOutcome.success("DRY-RUN payload -> " + summarizeCostCenterReplacePayload(employee, request));
        }

        try {
            b4rrhhLifecycleClient.createCostCenterDistribution(
                    normalizeCode(employee.ruleSystemCode()),
                    normalizeCode(employee.employeeTypeCode()),
                    employee.employeeNumber(),
                    request
            );
            return EventOutcome.success("Cost center distribution create call completed");
        } catch (Exception ex) {
            return failureUnlessTheBackendIsWrong(ex);
        }
    }

    private HireEmployeeRequest.CostCenterDistribution buildHireCostCenterDistribution(
            SyntheticEmployee employee,
            LocalDate hireDate
    ) {
        if (!properties.getCostCenter().isEnabled()) {
            return null;
        }

        Random random = deterministicEmployeeRandom(employee, 11);
        List<HireEmployeeRequest.CostCenterDistribution.Item> items = costCenterMutationGenerator.generateDistribution(hireDate, random).stream()
                .map(item -> new HireEmployeeRequest.CostCenterDistribution.Item(
                normalizeCode(item.costCenterCode()),
                item.allocationPercentage()
                ))
                .toList();

        return new HireEmployeeRequest.CostCenterDistribution(items);
    }

    private RehireEmployeeRequest.CostCenterDistribution buildRehireCostCenterDistribution(
            SyntheticEmployee employee,
            LocalDate rehireDate
    ) {
        if (!properties.getCostCenter().isEnabled()) {
            return null;
        }

        Random random = deterministicEmployeeRandom(employee, 23);
        List<RehireEmployeeRequest.CostCenterDistribution.Item> items = costCenterMutationGenerator.generateDistribution(rehireDate, random).stream()
                .map(item -> new RehireEmployeeRequest.CostCenterDistribution.Item(
                normalizeCode(item.costCenterCode()),
                item.allocationPercentage()
                ))
                .toList();

        return new RehireEmployeeRequest.CostCenterDistribution(items);
    }

    private Random deterministicEmployeeRandom(SyntheticEmployee employee, int salt) {
        long seed = properties.getGeneration().getSeed();
        long employeeHash = employee.employeeNumber() == null ? 0L : employee.employeeNumber().hashCode();
        return new Random(seed + employeeHash + salt);
    }

    private void validateConfiguration() {
        LoaderProperties.Generation generation = properties.getGeneration();
        if (generation.getHireDateFrom().isAfter(generation.getHireDateTo())) {
            throw new IllegalArgumentException("Invalid date range: loader.generation.hire-date-from must be <= hire-date-to");
        }

        LoaderProperties.Simulation simulation = properties.getSimulation();
        if (simulation.getTerminationMinDaysAfterHire() > simulation.getTerminationMaxDaysAfterHire()) {
            throw new IllegalArgumentException(
                    "Invalid simulation configuration: termination min days must be <= max days"
            );
        }
        if (simulation.getRehireMinDaysAfterTermination() > simulation.getRehireMaxDaysAfterTermination()) {
            throw new IllegalArgumentException(
                    "Invalid simulation configuration: rehire min days must be <= max days"
            );
        }
    }

    /**
     * Un evento que falla se anota y la corrida sigue: es lo que hace util el
     * informe final. Un backend equivocado no es un evento que falla, es la
     * corrida entera, y tiene que subir hasta arriba sin que nadie lo convierta
     * en una linea roja mas (workforce-loader#8).
     */
    private static EventOutcome failureUnlessTheBackendIsWrong(Exception ex) {
        if (ex instanceof BackendTargetMismatchException mismatch) {
            throw mismatch;
        }
        return EventOutcome.failure(ex.getMessage());
    }

    private static String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private static String summarizeHirePayload(HireEmployeeRequest request) {
        return "ruleSystemCode=" + request.ruleSystemCode()
                + ", employeeTypeCode=" + request.employeeTypeCode()
                + ", hireDate=" + request.hireDate();
    }

    private static String summarizeTerminatePayload(SyntheticEmployee employee, TerminateEmployeeRequest request) {
        return "employeeNumber=" + employee.employeeNumber()
                + ", ruleSystemCode=" + normalizeCode(employee.ruleSystemCode())
                + ", employeeTypeCode=" + normalizeCode(employee.employeeTypeCode())
                + ", terminationDate=" + request.terminationDate()
                + ", exitReasonCode=" + request.exitReasonCode();
    }

    private static String summarizeRehirePayload(SyntheticEmployee employee, RehireEmployeeRequest request) {
        return "employeeNumber=" + employee.employeeNumber()
                + ", ruleSystemCode=" + normalizeCode(employee.ruleSystemCode())
                + ", employeeTypeCode=" + normalizeCode(employee.employeeTypeCode())
                + ", rehireDate=" + request.rehireDate();
    }

    private static String summarizeWorkCenterChangePayload(SyntheticEmployee employee, CreateWorkCenterRequest request) {
        return "operation=create"
                + ", employeeNumber=" + employee.employeeNumber()
                + ", ruleSystemCode=" + normalizeCode(employee.ruleSystemCode())
                + ", employeeTypeCode=" + normalizeCode(employee.employeeTypeCode())
                + ", startDate=" + request.startDate()
                + ", workCenterCode=" + request.workCenterCode()
                ;
    }

    private static String summarizeWorkingTimeChangePayload(SyntheticEmployee employee, CreateWorkingTimeRequest request) {
        return "operation=create"
                + ", employeeNumber=" + employee.employeeNumber()
                + ", ruleSystemCode=" + normalizeCode(employee.ruleSystemCode())
                + ", employeeTypeCode=" + normalizeCode(employee.employeeTypeCode())
                + ", startDate=" + request.startDate()
                + ", workingTimePercentage=" + request.workingTimePercentage()
                ;
    }

    private static String summarizeContractReplacePayload(SyntheticEmployee employee, CreateContractRequest request) {
        return "employeeNumber=" + employee.employeeNumber()
                + ", ruleSystemCode=" + normalizeCode(employee.ruleSystemCode())
                + ", employeeTypeCode=" + normalizeCode(employee.employeeTypeCode())
                + ", startDate=" + request.startDate()
                + ", contractCode=" + request.contractCode()
                + ", contractSubtypeCode=" + request.contractSubtypeCode();
    }

    private static String summarizeLaborClassificationReplacePayload(
            SyntheticEmployee employee,
            CreateLaborClassificationRequest request
    ) {
        return "employeeNumber=" + employee.employeeNumber()
                + ", ruleSystemCode=" + normalizeCode(employee.ruleSystemCode())
                + ", employeeTypeCode=" + normalizeCode(employee.employeeTypeCode())
                + ", startDate=" + request.startDate()
                + ", agreementCode=" + request.agreementCode()
                + ", agreementCategoryCode=" + request.agreementCategoryCode();
    }

    private static String summarizeCostCenterReplacePayload(
            SyntheticEmployee employee,
            CreateCostCenterDistributionRequest request
    ) {
        return "employeeNumber=" + employee.employeeNumber()
                + ", ruleSystemCode=" + normalizeCode(employee.ruleSystemCode())
                + ", employeeTypeCode=" + normalizeCode(employee.employeeTypeCode())
                + ", startDate=" + request.startDate()
                + ", items=" + request.items().size();
    }

    private static String summarizeApiResponse(String operation, String status, String message) {
        String responseMessage = operation + " call completed";
        if (status != null && !status.isBlank()) {
            responseMessage = responseMessage + ": " + status;
        }
        if (message != null && !message.isBlank()) {
            responseMessage = responseMessage + " - " + message;
        }
        return responseMessage;
    }

    private record EventOutcome(boolean success, String message) {
        static EventOutcome success(String message) {
            return new EventOutcome(true, message);
        }

        static EventOutcome failure(String message) {
            return new EventOutcome(false, message);
        }
    }
}