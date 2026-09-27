package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.domain.model.SyntheticEmployee;
import com.b4rrhh.workforceloader.domain.model.SyntheticPersonalData;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CatalogOption;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El mes partido de la demo lo planifica el generador, no una migracion (backend#74).
 *
 * <p>La V118 intentaba partir la jornada de EMP001000 con un UPDATE. No podia funcionar: Flyway
 * corre al arrancar el backend y los empleados los crea el loader despues, asi que el UPDATE
 * tocaba cero filas y la migracion terminaba en verde igual. Estos tests comprueban que ahora
 * el corte sale de la generacion —sin que nadie lo pida a mano— y solo para quien lo ejercita.
 */
class EmployeeLifecycleScenarioGeneratorTest {

    private static final LocalDate CORTE = LocalDate.of(2026, 9, 16);
    /**
     * Los cortes que el generador recibe. Desde el {@code workforce-loader#16} son una lista —uno
     * por mes del ciclo— y estos tests siguen pasando uno solo: lo que miden es a quien le toca y
     * que se le escribe, no el reparto, que tiene su propio test en el resolutor.
     */
    private static final List<LocalDate> CORTES = List.of(CORTE);

    /**
     * La ventana que estos tests usaban, ahora escrita.
     *
     * <p>Sus dos extremos estaban en la configuracion y desde el {@code workforce-loader#1} y el
     * {@code #11} entran por parametro: el principio lo pone el catalogo y el final, el periodo.
     * Se deja la misma para que lo que estos tests miden no se mueva.
     */
    private static final HireWindow VENTANA =
            new HireWindow(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 5, 1));

    @Test
    void partsTheMonthForAsManyEmployeesAsAreAskedFor() {
        LoaderProperties properties = baseProperties();
        properties.getWorkingTimeChange().setEmployees(2);

        List<EmployeeLifecycleScenario> scenarios = generatorFor(properties).generate(List.of(
                employee("EMP000001", LocalDate.of(2024, 1, 10)),
                employee("EMP000002", LocalDate.of(2024, 2, 10)),
                employee("EMP000003", LocalDate.of(2024, 3, 10))
        ), VENTANA, CORTES);

        assertThat(scenarios).extracting(EmployeeLifecycleScenarioGeneratorTest::workingTimeChangeOf)
                .containsExactly(CORTE, CORTE, null);
        assertThat(scenarios.getFirst().events())
                .filteredOn(event -> event.eventType() == LifecycleEventType.CHANGE_WORKING_TIME)
                .singleElement()
                .satisfies(event -> assertThat(event.payload())
                        .isEqualTo(new WorkingTimeChangeEventPayload(new BigDecimal("50"))));
    }

    /**
     * La correccion de regimen de pagas extras llega al escenario, y no se queda por el camino.
     *
     * <p>Es el defecto que este test existe para impedir y que costo una corrida entera: el
     * generador planificaba 247 correcciones, {@code addMutationPayloads} no reconocia el tipo de
     * evento nuevo, y su {@code switch} de sentencia —que sobre un enum no obliga a cubrirlos
     * todos— <b>lo tiraba en silencio</b>. La corrida terminaba en verde y el informe decia
     * {@code requested=0} de algo que se habia pedido ({@code workforce-loader#13}).
     *
     * <p>Se mira sobre el escenario y no sobre la lista de eventos planificados a proposito: el
     * escenario es lo que el ejecutor recibe, y era justo ahi donde se perdia.
     */
    @Test
    void theExtraPaymentRegimeCorrectionSurvivesIntoTheScenario() {
        LoaderProperties todos = baseProperties();
        todos.getSimulation().setExtrasProrrateadasRate(1.0);

        List<EmployeeLifecycleScenario> scenarios = generatorFor(todos).generate(List.of(
                employee("EMP000001", LocalDate.of(2024, 1, 10))
        ), VENTANA, CORTES);

        assertThat(scenarios.getFirst().events())
                .filteredOn(event -> event.eventType() == LifecycleEventType.CHANGE_EXTRA_PAYMENT_REGIME)
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.payload())
                        .isInstanceOf(ExtraPaymentRegimeChangeEventPayload.class)
                        .extracting(payload -> ((ExtraPaymentRegimeChangeEventPayload) payload).prorated())
                        .as("el convenio de este test no prorratea, asi que la excepcion es prorratear")
                        .isEqualTo(true));
    }

    /** Y a quien no le toca, no le llega ninguna. */
    @Test
    void whoeverIsNotPickedKeepsTheAgreementRegimeAndGetsNoEvent() {
        LoaderProperties ninguno = baseProperties();
        ninguno.getSimulation().setExtrasProrrateadasRate(0.0);

        List<EmployeeLifecycleScenario> scenarios = generatorFor(ninguno).generate(List.of(
                employee("EMP000001", LocalDate.of(2024, 1, 10))
        ), VENTANA, CORTES);

        assertThat(scenarios.getFirst().events())
                .filteredOn(event -> event.eventType() == LifecycleEventType.CHANGE_EXTRA_PAYMENT_REGIME)
                .isEmpty();
    }

    // Quien entra a mitad de mes no parte nada: su primer tramo empezaria el dia del alta y el
    // mes no quedaria dividido en dos, que es justo lo que hay que ensenar (ADR-058).
    @Test
    void leavesOutWhoeverWasNotAlreadyThereWhenTheMonthStarted() {
        List<EmployeeLifecycleScenario> scenarios = generatorFor(baseProperties()).generate(List.of(
                employee("EMP000001", LocalDate.of(2026, 9, 20))
        ), VENTANA, CORTES);

        assertThat(workingTimeChangeOf(scenarios.getFirst())).isNull();
    }

    // Cambiar al mismo porcentaje daria dos tramos con el mismo precio: dos lineas iguales que no
    // ensenan nada. Y apagado, no se planifica.
    //
    // El apagado entra ahora como un corte nulo y no como una propiedad, porque desde el
    // workforce-loader#12 quien decide si hay corte -- y que dia -- es el
    // WorkingTimeChangeDateResolver, en un solo sitio. Se le pregunta a el en vez de escribir
    // null a mano, que es lo que hace el servicio de verdad.
    @Test
    void leavesOutTheChangeThatWouldNotChangeAnything() {
        LoaderProperties samePercentage = baseProperties();
        samePercentage.getWorkingTimeChange().setPercentage(new BigDecimal("100"));

        LoaderProperties disabled = baseProperties();
        disabled.getWorkingTimeChange().setEnabled(false);
        List<LocalDate> sinCorte = new WorkingTimeChangeDateResolver(disabled).resolve();

        List<SyntheticEmployee> employees = List.of(employee("EMP000001", LocalDate.of(2024, 1, 10)));

        assertThat(workingTimeChangeOf(generatorFor(samePercentage).generate(employees, VENTANA, CORTES).getFirst())).isNull();
        assertThat(workingTimeChangeOf(generatorFor(disabled).generate(employees, VENTANA, sinCorte).getFirst())).isNull();
    }

    /**
     * Sembrar horas extra no mueve ni una coma del resto de la semilla (workforce-loader#5).
     *
     * <p>Es la razon de que el generador de horas extra lleve su propio {@code Random}. Si gastara
     * del comun, cada tirada suya desplazaria la secuencia posterior y la semilla entera cambiaria
     * —otras fechas de cese, otras ausencias, otras direcciones— para anadir unas filas. El
     * diferencial contra la semilla de hoy tiene que ser exactamente lo que este cambio anade, o la
     * resiembra no hay quien la revise.
     *
     * <p>Este test es el que se pone rojo si alguien «simplifica» pasandole el {@code random} de la
     * simulacion.
     */
    @Test
    void seedingOvertimeDoesNotMoveTheRestOfTheSeed() {
        List<SyntheticEmployee> plantilla = new java.util.ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            plantilla.add(employee(String.format("EMP%06d", i), LocalDate.of(2024, 1, 1).plusDays(i * 7L)));
        }

        LoaderProperties sinHoras = mutableProperties();
        LoaderProperties conHoras = mutableProperties();
        conHoras.setPeriod(202609);

        List<EmployeeLifecycleScenario> antes = generatorFor(sinHoras).generate(plantilla, VENTANA, CORTES);
        List<EmployeeLifecycleScenario> despues = generatorFor(conHoras).generate(plantilla, VENTANA, CORTES);

        assertThat(sinEventosDeNomina(despues)).isEqualTo(sinEventosDeNomina(antes));
        assertThat(despues.stream()
                .flatMap(scenario -> scenario.events().stream())
                .filter(event -> event.eventType() == LifecycleEventType.PAYROLL_INPUT)
                .count())
                .as("y el escenario nuevo si aparece, o este test no probaria nada")
                .isPositive();
    }

    private static List<List<EmployeeLifecycleEvent>> sinEventosDeNomina(
            List<EmployeeLifecycleScenario> scenarios) {
        return scenarios.stream()
                .map(scenario -> scenario.events().stream()
                        .filter(event -> event.eventType() != LifecycleEventType.PAYROLL_INPUT)
                        .toList())
                .toList();
    }

    /** Como {@link #baseProperties()} pero con cese, readmision y mutaciones: una secuencia rica. */
    private static LoaderProperties mutableProperties() {
        LoaderProperties properties = baseProperties();
        properties.getSimulation().setTerminateRate(0.6);
        properties.getSimulation().setRehireRateOfTerminated(0.8);
        properties.getSimulation().setWorkCenterChangeRate(0.35);
        properties.getSimulation().setContractReplaceRate(0.25);
        properties.getSimulation().setLaborClassificationReplaceRate(0.25);
        return properties;
    }

    private static LocalDate workingTimeChangeOf(EmployeeLifecycleScenario scenario) {
        return scenario.events().stream()
                .filter(event -> event.eventType() == LifecycleEventType.CHANGE_WORKING_TIME)
                .map(EmployeeLifecycleEvent::effectiveDate)
                .findFirst()
                .orElse(null);
    }

    private static EmployeeLifecycleScenarioGenerator generatorFor(LoaderProperties properties) {
        HireReferenceDataResolver resolver = new FixedHireReferenceDataResolver(properties);
        return new EmployeeLifecycleScenarioGenerator(
                properties,
                resolver,
                new WorkCenterMutationGenerator(resolver),
                new ContractMutationGenerator(),
                new LaborClassificationMutationGenerator(),
                new CostCenterMutationGenerator(null, properties),
                new AbsenceScenarioGenerator(),
                new PayrollInputScenarioGenerator(),
                new ExtraPaymentRegimeScenarioGenerator(),
                // El convenio de la demo no prorratea; lo que el loader siembra es la excepcion.
                (ruleSystemCode, agreementCode) -> false
        );
    }

    private static SyntheticEmployee employee(String number, LocalDate hireDate) {
        return new SyntheticEmployee(
                "ESP",
                "INTERNAL",
                number,
                SyntheticEmployee.PersonName.of("Lucia", "Suarez", "Diaz"),
                hireDate,
                new BigDecimal("100"),
                SyntheticPersonalData.none()
        );
    }

    private static LoaderProperties baseProperties() {
        LoaderProperties properties = new LoaderProperties();
        properties.getDefaults().setRuleSystemCode("ESP");
        properties.getDefaults().setEmployeeTypeCode("INTERNAL");
        properties.getGeneration().setSeed(12345);
        properties.getGeneration().setHireDateFrom(LocalDate.of(2024, 1, 1));
        properties.getGeneration().setHireDateTo(LocalDate.of(2026, 5, 1));
        // Sin cese, sin readmision y sin mutaciones: lo que se mide aqui es el corte de jornada,
        // y el resto solo anadiria eventos que no se miran.
        properties.getSimulation().setTerminateRate(0);
        properties.getSimulation().setRehireRateOfTerminated(0);
        properties.getSimulation().setWorkCenterChangeRate(0);
        properties.getSimulation().setContractReplaceRate(0);
        properties.getSimulation().setLaborClassificationReplaceRate(0);
        properties.getSimulation().setCostCenterReplaceRate(0);
        properties.getCostCenter().setEnabled(false);
        properties.getWorkingTimeChange().setDate(CORTE);
        properties.getWorkingTimeChange().setPercentage(new BigDecimal("50"));
        return properties;
    }

    /** Sustituye el catalogo entero: aqui no se prueba de donde salen los codigos. */
    private static final class FixedHireReferenceDataResolver extends HireReferenceDataResolver {

        private FixedHireReferenceDataResolver(LoaderProperties properties) {
            super(null, properties);
        }

        @Override
        public ResolvedHireReferencePools preloadPools(String ruleSystemCode) {
            return new ResolvedHireReferencePools(
                    List.of(new CatalogOption("ES02", "Spain Company 02")),
                    List.of(new CatalogOption("MAD", "Madrid HQ")),
                    List.of(new CatalogOption("HIRING", "Hiring")),
                    List.of(new CatalogOption("BAJA", "Baja voluntaria")),
                    // Con convenios y tipos de contrato, para que las sustituciones y la variacion
                    // de la readmision tengan de donde elegir y gasten azar comun de verdad.
                    List.of(new AgreementWithCategories(
                            new CatalogOption("99002405011982", "Grandes almacenes"),
                            List.of(new CatalogOption("99002405-G1", "Grupo I"),
                                    new CatalogOption("99002405-G2", "Grupo II")))),
                    List.of(new ContractTypeWithSubtypes(
                            new CatalogOption("100", "Indefinido"),
                            List.of(new CatalogOption("01", "Subtipo 01")))),
                    List.of()
            );
        }

        @Override
        public ResolvedHireData resolveFromPools(
                ResolvedHireReferencePools pools,
                String ruleSystemCode,
                LocalDate referenceDate,
                Random random
        ) {
            return new ResolvedHireData(
                    "ES02", "MAD", "HIRING", "99002405011982", "99002405-G1", "100", "01", null);
        }

        @Override
        public String resolveExitReasonFromPools(ResolvedHireReferencePools pools, Random random) {
            return "BAJA";
        }

        // La readmision pregunta por los centros de la sociedad, y eso va a la API. Aqui no hay
        // API: se contesta lo mismo siempre, que para lo que se mide da igual cual sea.
        @Override
        public String resolveWorkCenterCodeForCompany(
                String ruleSystemCode,
                String companyCode,
                LocalDate referenceDate,
                String currentWorkCenterCode,
                Random random
        ) {
            return "MAD";
        }
    }
}
