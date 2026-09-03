package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.domain.model.LoaderRunSummary;
import com.b4rrhh.workforceloader.domain.model.SyntheticEmployee;
import com.b4rrhh.workforceloader.domain.model.SyntheticPersonalData;
import com.b4rrhh.workforceloader.infrastructure.api.B4rrhhLifecycleClient;
import com.b4rrhh.workforceloader.infrastructure.api.CatalogApiClient;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CatalogOption;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CreateAddressRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CreateContactRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CreateIdentifierRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.HireEmployeeRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.HireEmployeeResponse;
import com.b4rrhh.workforceloader.infrastructure.api.dto.RehireEmployeeRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.RehireEmployeeResponse;
import com.b4rrhh.workforceloader.infrastructure.api.dto.TerminateEmployeeRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.TerminateEmployeeResponse;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import com.b4rrhh.workforceloader.infrastructure.generator.SyntheticEmployeeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunLifecycleSimulationServiceTest {

    @Test
    void shouldSendCanonicalWorkingTimeOnHireAndRehireOnly() {
        SyntheticEmployee employee = syntheticEmployee(new BigDecimal("75"));
        EmployeeLifecycleScenario scenario = new EmployeeLifecycleScenario(
                employee,
                List.of(
                        new EmployeeLifecycleEvent(LifecycleEventType.HIRE, LocalDate.of(2024, 1, 10)),
                        new EmployeeLifecycleEvent(LifecycleEventType.TERMINATE, LocalDate.of(2024, 3, 1)),
                        new EmployeeLifecycleEvent(LifecycleEventType.REHIRE, LocalDate.of(2024, 4, 1))
                ),
                resolvedHireData(new BigDecimal("75")),
                resolvedHireData(new BigDecimal("60")),
                "BAJA"
        );

        CapturingLifecycleClient client = new CapturingLifecycleClient(baseProperties());
        RunLifecycleSimulationService service = new RunLifecycleSimulationService(
                baseProperties(),
                new FixedSyntheticEmployeeGenerator(List.of(employee)),
                new FixedScenarioGenerator(baseProperties(), List.of(scenario)),
                client,
                new CostCenterMutationGenerator(null, baseProperties())
        );

        LoaderRunSummary summary = service.run();

        assertThat(summary.hiresSuccess()).isEqualTo(1);
        assertThat(summary.terminationsSuccess()).isEqualTo(1);
        assertThat(summary.rehiresSuccess()).isEqualTo(1);
        assertThat(client.hireRequests).hasSize(1);
        assertThat(client.rehireRequests).hasSize(1);
        assertThat(client.terminateRequests).hasSize(1);
        assertThat(client.hireRequests.getFirst().workingTime().workingTimePercentage()).isEqualByComparingTo("75");
        assertThat(client.rehireRequests.getFirst().workingTime().workingTimePercentage()).isEqualByComparingTo("60");
        assertThat(HireEmployeeRequest.WorkingTime.class.getRecordComponents()).hasSize(1);
        assertThat(HireEmployeeRequest.WorkingTime.class.getRecordComponents()[0].getName()).isEqualTo("workingTimePercentage");
        assertThat(RehireEmployeeRequest.WorkingTime.class.getRecordComponents()).hasSize(1);
        assertThat(RehireEmployeeRequest.WorkingTime.class.getRecordComponents()[0].getName()).isEqualTo("workingTimePercentage");
    }

    @Test
    void shouldFailHireBeforeBackendCallWhenWorkingTimeIsMissing() {
        SyntheticEmployee employee = syntheticEmployee(new BigDecimal("75"));
        EmployeeLifecycleScenario scenario = new EmployeeLifecycleScenario(
                employee,
                List.of(new EmployeeLifecycleEvent(LifecycleEventType.HIRE, LocalDate.of(2024, 1, 10))),
                resolvedHireData(null),
                null,
                "BAJA"
        );

        CapturingLifecycleClient client = new CapturingLifecycleClient(baseProperties());
        RunLifecycleSimulationService service = new RunLifecycleSimulationService(
                baseProperties(),
                new FixedSyntheticEmployeeGenerator(List.of(employee)),
                new FixedScenarioGenerator(baseProperties(), List.of(scenario)),
                client,
                new CostCenterMutationGenerator(null, baseProperties())
        );

        LoaderRunSummary summary = service.run();

        assertThat(summary.hiresFailed()).isEqualTo(1);
        assertThat(summary.hiresSuccess()).isZero();
        assertThat(client.hireRequests).isEmpty();
        assertThat(summary.results()).singleElement().extracting(result -> result.message())
                .isEqualTo("Missing workingTimePercentage for HIRE event of employee MAS000001");
    }

    @Test
    void shouldFailRehireBeforeBackendCallWhenWorkingTimeIsInvalid() {
        SyntheticEmployee employee = syntheticEmployee(new BigDecimal("75"));
        EmployeeLifecycleScenario scenario = new EmployeeLifecycleScenario(
                employee,
                List.of(
                        new EmployeeLifecycleEvent(LifecycleEventType.HIRE, LocalDate.of(2024, 1, 10)),
                        new EmployeeLifecycleEvent(LifecycleEventType.TERMINATE, LocalDate.of(2024, 3, 1)),
                        new EmployeeLifecycleEvent(LifecycleEventType.REHIRE, LocalDate.of(2024, 4, 1))
                ),
                resolvedHireData(new BigDecimal("75")),
                resolvedHireData(new BigDecimal("120")),
                "BAJA"
        );

        CapturingLifecycleClient client = new CapturingLifecycleClient(baseProperties());
        RunLifecycleSimulationService service = new RunLifecycleSimulationService(
                baseProperties(),
                new FixedSyntheticEmployeeGenerator(List.of(employee)),
                new FixedScenarioGenerator(baseProperties(), List.of(scenario)),
                client,
                new CostCenterMutationGenerator(null, baseProperties())
        );

        LoaderRunSummary summary = service.run();

        assertThat(summary.hiresSuccess()).isEqualTo(1);
        assertThat(summary.terminationsSuccess()).isEqualTo(1);
        assertThat(summary.rehiresFailed()).isEqualTo(1);
        assertThat(client.hireRequests).hasSize(1);
        assertThat(client.terminateRequests).hasSize(1);
        assertThat(client.rehireRequests).isEmpty();
        assertThat(summary.results().getLast().message())
                .isEqualTo("Invalid workingTimePercentage 120 for REHIRE event of employee MAS000001. Expected value greater than 0 and less than or equal to 100.");
    }

    // workforce-loader#3: la ficha «Persona» estaba vacía porque el alta no manda dirección, contacto ni identificador.
    @Test
    void shouldCreatePersonalDataRightAfterHireWithTheNumberTheBackendReturned() {
        SyntheticEmployee employee = syntheticEmployee(new BigDecimal("75")).withEmployeeNumber("EMP000001")
                .withPersonalData(personalData());
        EmployeeLifecycleScenario scenario = new EmployeeLifecycleScenario(
                employee,
                List.of(new EmployeeLifecycleEvent(LifecycleEventType.HIRE, LocalDate.of(2024, 1, 10))),
                resolvedHireData(new BigDecimal("75")),
                null,
                "BAJA"
        );

        CapturingLifecycleClient client = new CapturingLifecycleClient(baseProperties());
        RunLifecycleSimulationService service = new RunLifecycleSimulationService(
                baseProperties(),
                new FixedSyntheticEmployeeGenerator(List.of(employee)),
                new FixedScenarioGenerator(baseProperties(), List.of(scenario)),
                client,
                new CostCenterMutationGenerator(null, baseProperties())
        );

        LoaderRunSummary summary = service.run();

        assertThat(summary.hiresSuccess()).isEqualTo(1);
        assertThat(summary.personalDataRequested()).isEqualTo(3);
        assertThat(summary.personalDataSuccess()).isEqualTo(3);
        assertThat(client.personalDataEmployeeNumbers).containsOnly("MAS000001");
        assertThat(client.addressRequests).singleElement().extracting(CreateAddressRequest::addressTypeCode).isEqualTo("HOME");
        assertThat(client.contactRequests).singleElement().extracting(CreateContactRequest::contactValue).isEqualTo("ana.garcia@b4rrhh.example");
        assertThat(client.identifierRequests).singleElement().extracting(CreateIdentifierRequest::isPrimary).isEqualTo(true);
        assertThat(summary.results()).extracting(result -> result.eventType())
                .containsExactly("HIRE", "CREATE_ADDRESS", "CREATE_CONTACT", "CREATE_IDENTIFIER");
    }

    @Test
    void shouldRecordAFailedPersonalDataCallWithoutAbortingTheScenario() {
        SyntheticEmployee employee = syntheticEmployee(new BigDecimal("75")).withPersonalData(personalData());
        EmployeeLifecycleScenario scenario = new EmployeeLifecycleScenario(
                employee,
                List.of(
                        new EmployeeLifecycleEvent(LifecycleEventType.HIRE, LocalDate.of(2024, 1, 10)),
                        new EmployeeLifecycleEvent(LifecycleEventType.TERMINATE, LocalDate.of(2024, 3, 1))
                ),
                resolvedHireData(new BigDecimal("75")),
                null,
                "BAJA"
        );

        CapturingLifecycleClient client = new CapturingLifecycleClient(baseProperties());
        client.rejectContacts = true;
        RunLifecycleSimulationService service = new RunLifecycleSimulationService(
                baseProperties(),
                new FixedSyntheticEmployeeGenerator(List.of(employee)),
                new FixedScenarioGenerator(baseProperties(), List.of(scenario)),
                client,
                new CostCenterMutationGenerator(null, baseProperties())
        );

        LoaderRunSummary summary = service.run();

        assertThat(summary.personalDataRequested()).isEqualTo(3);
        assertThat(summary.personalDataSuccess()).isEqualTo(2);
        assertThat(summary.personalDataFailed()).isEqualTo(1);
        assertThat(summary.terminationsSuccess()).isEqualTo(1);
        assertThat(summary.results()).filteredOn(result -> !result.success()).singleElement()
                .satisfies(result -> {
                    assertThat(result.eventType()).isEqualTo("CREATE_CONTACT");
                    assertThat(result.message()).isEqualTo("contact rejected");
                });
    }

    private static SyntheticPersonalData personalData() {
        return new SyntheticPersonalData(
                List.of(new SyntheticPersonalData.Address(
                        "home", "Calle Mayor, 1", "Madrid", "esp", "28001", "ES-MD", LocalDate.of(2024, 1, 10), null
                )),
                List.of(new SyntheticPersonalData.Contact("email", "ana.garcia@b4rrhh.example")),
                List.of(new SyntheticPersonalData.Identifier("national_id", "00000001R", "esp", LocalDate.of(2030, 1, 1), true))
        );
    }

    private static SyntheticEmployee syntheticEmployee(BigDecimal workingTimePercentage) {
        return new SyntheticEmployee(
                "esp",
                "emp",
                "MAS000001",
                SyntheticEmployee.PersonName.of("Ana", "Garcia", null),
                LocalDate.of(2024, 1, 10),
                workingTimePercentage,
                SyntheticPersonalData.none()
        );
    }

    private static ResolvedHireData resolvedHireData(BigDecimal workingTimePercentage) {
        return new ResolvedHireData(
                "COMP01",
                "WC01",
                "ALTA",
                "AG01",
                "CAT01",
                "CT01",
                "CST01",
                workingTimePercentage
        );
    }

    private static LoaderProperties baseProperties() {
        LoaderProperties properties = new LoaderProperties();
        properties.getBackend().setBaseUrl("http://localhost:8080");
        properties.getDefaults().setRuleSystemCode("ESP");
        properties.getDefaults().setEmployeeTypeCode("INTERNAL");
        properties.getGeneration().setCount(1);
        properties.getGeneration().setHireDateFrom(LocalDate.of(2024, 1, 1));
        properties.getGeneration().setHireDateTo(LocalDate.of(2024, 12, 31));
        properties.getRun().setDryRun(false);
        properties.getSimulation().setTerminateRate(0);
        properties.getSimulation().setRehireRateOfTerminated(0);
        // Apagado aqui para que estos tests no pidan el catalogo; el que lo enciende trae su catalogo.
        properties.getCostCenter().setEnabled(false);
        return properties;
    }

    // workforce-loader#5: con el reparto apagado por configuracion, el alta no llevaba centros de coste
    // y employee.cost_center se quedaba vacia. Ahora los centros salen del catalogo.
    @Test
    void shouldSendACostCenterDistributionFromTheCatalogOnHireAndRehire() {
        SyntheticEmployee employee = syntheticEmployee(new BigDecimal("75"));
        EmployeeLifecycleScenario scenario = new EmployeeLifecycleScenario(
                employee,
                List.of(
                        new EmployeeLifecycleEvent(LifecycleEventType.HIRE, LocalDate.of(2024, 1, 10)),
                        new EmployeeLifecycleEvent(LifecycleEventType.TERMINATE, LocalDate.of(2024, 3, 1)),
                        new EmployeeLifecycleEvent(LifecycleEventType.REHIRE, LocalDate.of(2024, 4, 1))
                ),
                resolvedHireData(new BigDecimal("75")),
                resolvedHireData(new BigDecimal("60")),
                "BAJA"
        );

        LoaderProperties properties = baseProperties();
        properties.getCostCenter().setEnabled(true);
        CapturingLifecycleClient client = new CapturingLifecycleClient(properties);
        RunLifecycleSimulationService service = new RunLifecycleSimulationService(
                properties,
                new FixedSyntheticEmployeeGenerator(List.of(employee)),
                new FixedScenarioGenerator(properties, List.of(scenario)),
                client,
                new CostCenterMutationGenerator(new FixedCatalogApiClient(properties, "CC_ADMIN", "CC_HR"), properties)
        );

        LoaderRunSummary summary = service.run();

        assertThat(summary.hiresSuccess()).isEqualTo(1);
        assertThat(summary.rehiresSuccess()).isEqualTo(1);
        assertThat(client.hireRequests.getFirst().costCenterDistribution().items())
                .isNotEmpty()
                .allSatisfy(item -> assertThat(item.costCenterCode()).isIn("CC_ADMIN", "CC_HR"));
        assertThat(client.hireRequests.getFirst().costCenterDistribution().items().stream()
                .mapToInt(HireEmployeeRequest.CostCenterDistribution.Item::allocationPercentage).sum()).isEqualTo(100);
        assertThat(client.rehireRequests.getFirst().costCenterDistribution().items())
                .isNotEmpty()
                .allSatisfy(item -> assertThat(item.costCenterCode()).isIn("CC_ADMIN", "CC_HR"));
    }

    private static final class FixedCatalogApiClient extends CatalogApiClient {

        private final List<CatalogOption> options;

        private FixedCatalogApiClient(LoaderProperties properties, String... codes) {
            super(properties, WebClient.builder());
            this.options = java.util.Arrays.stream(codes).map(code -> new CatalogOption(code, code)).toList();
        }

        @Override
        public List<CatalogOption> getDirectOptionsForField(
                String ruleSystemCode, String resourceCode, String fieldCode, String... fallbackEntityTypeCodes
        ) {
            return options;
        }
    }

    private static final class FixedSyntheticEmployeeGenerator extends SyntheticEmployeeGenerator {

        private final List<SyntheticEmployee> employees;

        private FixedSyntheticEmployeeGenerator(List<SyntheticEmployee> employees) {
            super(baseProperties());
            this.employees = employees;
        }

        @Override
        public List<SyntheticEmployee> generateEmployees() {
            return employees;
        }
    }

    private static final class FixedScenarioGenerator extends EmployeeLifecycleScenarioGenerator {

        private final List<EmployeeLifecycleScenario> scenarios;

        private FixedScenarioGenerator(LoaderProperties properties, List<EmployeeLifecycleScenario> scenarios) {
            super(properties, null, null, null, null, new CostCenterMutationGenerator(null, properties));
            this.scenarios = scenarios;
        }

        @Override
        public List<EmployeeLifecycleScenario> generate(List<SyntheticEmployee> employees) {
            return scenarios;
        }
    }

    private static final class CapturingLifecycleClient extends B4rrhhLifecycleClient {

        private final List<HireEmployeeRequest> hireRequests = new java.util.ArrayList<>();
        private final List<TerminateEmployeeRequest> terminateRequests = new java.util.ArrayList<>();
        private final List<RehireEmployeeRequest> rehireRequests = new java.util.ArrayList<>();
        private final List<CreateAddressRequest> addressRequests = new java.util.ArrayList<>();
        private final List<CreateContactRequest> contactRequests = new java.util.ArrayList<>();
        private final List<CreateIdentifierRequest> identifierRequests = new java.util.ArrayList<>();
        private final List<String> personalDataEmployeeNumbers = new java.util.ArrayList<>();
        private boolean rejectContacts;

        private CapturingLifecycleClient(LoaderProperties properties) {
            super(properties, WebClient.builder());
        }

        @Override
        public void createAddress(String ruleSystemCode, String employeeTypeCode, String employeeNumber, CreateAddressRequest request) {
            personalDataEmployeeNumbers.add(employeeNumber);
            addressRequests.add(request);
        }

        @Override
        public void createContact(String ruleSystemCode, String employeeTypeCode, String employeeNumber, CreateContactRequest request) {
            personalDataEmployeeNumbers.add(employeeNumber);
            if (rejectContacts) {
                throw new RuntimeException("contact rejected");
            }
            contactRequests.add(request);
        }

        @Override
        public void createIdentifier(String ruleSystemCode, String employeeTypeCode, String employeeNumber, CreateIdentifierRequest request) {
            personalDataEmployeeNumbers.add(employeeNumber);
            identifierRequests.add(request);
        }

        @Override
        public HireEmployeeResponse hire(HireEmployeeRequest request) {
            hireRequests.add(request);
            return new HireEmployeeResponse("OK", "hire", "MAS000001");
        }

        @Override
        public TerminateEmployeeResponse terminate(
                String ruleSystemCode,
                String employeeTypeCode,
                String employeeNumber,
                TerminateEmployeeRequest request
        ) {
            terminateRequests.add(request);
            return new TerminateEmployeeResponse("OK", "terminate");
        }

        @Override
        public RehireEmployeeResponse rehire(
                String ruleSystemCode,
                String employeeTypeCode,
                String employeeNumber,
                RehireEmployeeRequest request
        ) {
            rehireRequests.add(request);
            return new RehireEmployeeResponse("OK", "rehire");
        }
    }
}