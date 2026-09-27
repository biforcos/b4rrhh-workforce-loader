package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.api.dto.CatalogOption;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// workforce-loader#5: mil empleados y ni una ausencia. Una plantilla donde nadie se ha puesto malo
// ni ha tenido vacaciones no se lee como datos de verdad.
class AbsenceScenarioGeneratorTest {

    /** Cualquier empleado: entra en la identidad del testigo de derecho y en nada mas. */
    private static final String EMP = "EMP000001";

    /** La tasa de la demo: uno de cada veinte (workforce-loader#14). */
    private static final double SIN_DERECHO = 0.05;

    private static final LocalDate HORIZON = LocalDate.of(2026, 10, 28);
    private static final List<CatalogOption> ESP_TYPES = catalog(
            "UNPAID_LEAVE", "IT_WORK_ACCIDENT", "IT_COMMON", "PARENTAL_LEAVE",
            "FORCE_MAJEURE", "PAID_PERSONAL_LEAVE", "VACATION"
    );

    private final AbsenceScenarioGenerator generator = new AbsenceScenarioGenerator();

    @Test
    void everyAbsenceFallsStrictlyInsideAPresenceWindowAndNoneTouchEachOther() {
        ActiveWindow closed = new ActiveWindow(LocalDate.of(2023, 3, 10), LocalDate.of(2023, 11, 20));
        ActiveWindow open = new ActiveWindow(LocalDate.of(2024, 1, 15), null);

        for (int seed = 0; seed < 300; seed++) {
            List<EmployeeLifecycleEvent> events =
                    generator.generate(EMP, List.of(closed, open), HORIZON, ESP_TYPES, SIN_DERECHO, new Random(seed));

            assertThat(events).allSatisfy(event -> assertThat(event.eventType()).isEqualTo(LifecycleEventType.ABSENCE));
            for (ActiveWindow window : List.of(closed, open)) {
                List<EmployeeLifecycleEvent> inside = events.stream()
                        .filter(event -> !event.effectiveDate().isBefore(window.startDate())
                                && (window.endDate() == null || !event.effectiveDate().isAfter(window.endDate())))
                        .sorted(java.util.Comparator.comparing(EmployeeLifecycleEvent::effectiveDate))
                        .toList();

                LocalDate previousEnd = null;
                for (int i = 0; i < inside.size(); i++) {
                    EmployeeLifecycleEvent event = inside.get(i);
                    AbsenceEventPayload payload = (AbsenceEventPayload) event.payload();

                    assertThat(event.effectiveDate()).isAfter(window.startDate());
                    if (payload.endDate() == null) {
                        // Abierta: solo la ultima, y solo en el periodo que sigue abierto.
                        assertThat(window.endDate()).isNull();
                        assertThat(i).isEqualTo(inside.size() - 1);
                        assertThat(event.effectiveDate()).isAfter(HORIZON.minusDays(31)).isBeforeOrEqualTo(HORIZON);
                    } else {
                        assertThat(payload.endDate()).isAfterOrEqualTo(event.effectiveDate());
                        assertThat(payload.endDate()).isBefore(window.endDate() == null ? HORIZON.plusDays(1) : window.endDate());
                    }
                    if (previousEnd != null) {
                        assertThat(event.effectiveDate()).isAfter(previousEnd.plusDays(1));
                    }
                    previousEnd = payload.endDate();
                }
            }
            // Y ninguna fuera de los dos periodos.
            assertThat(events).allSatisfy(event -> assertThat(
                    (event.effectiveDate().isAfter(closed.startDate()) && event.effectiveDate().isBefore(closed.endDate()))
                            || event.effectiveDate().isAfter(open.startDate())).isTrue());
        }
    }

    @Test
    void aWholeWorkforceGetsEveryKindOfAbsenceWithVacationOnTopAndAFewStillOpen() {
        List<EmployeeLifecycleEvent> events = new ArrayList<>();
        Random random = new Random(12345);
        int people = 1000;
        for (int i = 0; i < people; i++) {
            LocalDate hire = LocalDate.of(2023, 1, 1).plusDays(random.nextInt(1200));
            events.addAll(generator.generate(EMP, List.of(new ActiveWindow(hire, null)), HORIZON, ESP_TYPES, SIN_DERECHO, random));
        }

        Map<String, Long> byType = events.stream()
                .collect(Collectors.groupingBy(event -> ((AbsenceEventPayload) event.payload()).absenceTypeCode(), Collectors.counting()));
        long open = events.stream().filter(event -> ((AbsenceEventPayload) event.payload()).endDate() == null).count();

        // Varias por persona, de los siete tipos, con las vacaciones a la cabeza y las bajas comunes detras.
        assertThat(events).hasSizeGreaterThan(people * 3);
        assertThat(byType.keySet()).containsExactlyInAnyOrderElementsOf(ESP_TYPES.stream().map(CatalogOption::code).toList());
        assertThat(byType.get("VACATION")).isGreaterThan(byType.get("IT_COMMON"));
        assertThat(byType.get("IT_COMMON")).isGreaterThan(byType.get("PARENTAL_LEAVE"));
        // Unas pocas personas de baja ahora mismo: entre el 1 % y el 6 % de la plantilla.
        assertThat(open).isBetween(people / 100L, people * 6 / 100L);
        // Y las vacaciones caen sobre todo en verano.
        long summerVacations = events.stream()
                .filter(event -> "VACATION".equals(((AbsenceEventPayload) event.payload()).absenceTypeCode()))
                .filter(event -> event.effectiveDate().getMonthValue() == 7 || event.effectiveDate().getMonthValue() == 8)
                .count();
        assertThat(summerVacations).isGreaterThan(byType.get("VACATION") / 2);
    }

    @Test
    void durationsMatchWhatEachKindOfAbsenceIs() {
        List<EmployeeLifecycleEvent> events = new ArrayList<>();
        Random random = new Random(99);
        for (int i = 0; i < 400; i++) {
            events.addAll(generator.generate(EMP, List.of(new ActiveWindow(LocalDate.of(2023, 1, 1), null)), HORIZON, ESP_TYPES, SIN_DERECHO, random));
        }

        Map<String, List<Long>> daysByType = events.stream()
                .filter(event -> ((AbsenceEventPayload) event.payload()).endDate() != null)
                .collect(Collectors.groupingBy(
                        event -> ((AbsenceEventPayload) event.payload()).absenceTypeCode(),
                        Collectors.mapping(event -> java.time.temporal.ChronoUnit.DAYS.between(
                                event.effectiveDate(), ((AbsenceEventPayload) event.payload()).endDate()) + 1,
                                Collectors.toList())));

        assertThat(daysByType.get("VACATION")).allSatisfy(days -> assertThat(days).isBetween(5L, 15L));
        assertThat(daysByType.get("PAID_PERSONAL_LEAVE")).allSatisfy(days -> assertThat(days).isBetween(1L, 3L));
        assertThat(daysByType.get("PARENTAL_LEAVE")).allSatisfy(days -> assertThat(days).isEqualTo(112L));
        assertThat(daysByType.get("IT_WORK_ACCIDENT")).allSatisfy(days -> assertThat(days).isBetween(7L, 45L));
    }

    @Test
    void onlyCodesFromTheCatalogAreUsedAndUnknownOnesGetAGenericProfile() {
        List<CatalogOption> catalog = catalog("VACATION", "ZZ_CUSTOM");
        List<EmployeeLifecycleEvent> events = new ArrayList<>();
        Random random = new Random(3);
        for (int i = 0; i < 200; i++) {
            events.addAll(generator.generate(EMP, List.of(new ActiveWindow(LocalDate.of(2024, 1, 1), null)), HORIZON, catalog, SIN_DERECHO, random));
        }

        Map<String, Long> byType = events.stream()
                .collect(Collectors.groupingBy(event -> ((AbsenceEventPayload) event.payload()).absenceTypeCode(), Collectors.counting()));

        assertThat(byType.keySet()).containsExactlyInAnyOrder("VACATION", "ZZ_CUSTOM");
        assertThat(events).filteredOn(event -> "ZZ_CUSTOM".equals(((AbsenceEventPayload) event.payload()).absenceTypeCode()))
                .allSatisfy(event -> assertThat(java.time.temporal.ChronoUnit.DAYS.between(
                        event.effectiveDate(), ((AbsenceEventPayload) event.payload()).endDate()) + 1).isBetween(1L, 5L));
        // Sin bajas medicas en el catalogo, nada se queda abierto.
        assertThat(events).allSatisfy(event -> assertThat(((AbsenceEventPayload) event.payload()).endDate()).isNotNull());
    }

    @Test
    void noCatalogMeansNoAbsencesAndAWindowTooShortGetsNone() {
        assertThat(generator.generate(EMP, List.of(new ActiveWindow(LocalDate.of(2024, 1, 1), null)), HORIZON, List.of(), SIN_DERECHO, new Random(1)))
                .isEmpty();
        assertThat(generator.generate(EMP, List.of(new ActiveWindow(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2))), HORIZON, ESP_TYPES, SIN_DERECHO, new Random(1)))
                .isEmpty();
    }

    @Test
    void sameSeedSameAbsencesWhateverTheCatalogOrder() {
        List<ActiveWindow> windows = List.of(new ActiveWindow(LocalDate.of(2023, 5, 1), null));
        List<CatalogOption> reversed = ESP_TYPES.reversed();

        List<EmployeeLifecycleEvent> first = generator.generate(EMP, windows, HORIZON, ESP_TYPES, SIN_DERECHO, new Random(42));
        List<EmployeeLifecycleEvent> second = generator.generate(EMP, windows, HORIZON, reversed, SIN_DERECHO, new Random(42));

        assertThat(first).isNotEmpty().isEqualTo(second);
    }

    // ── El testigo de derecho a prestacion (workforce-loader#14) ─────────────

    /**
     * El testigo solo lo llevan las bajas por enfermedad comun.
     *
     * <p>En una vacacion el derecho a prestacion no es un dato que falte: es una pregunta que no
     * significa nada. Por eso viaja nulo y el loader no lo manda.
     */
    @Test
    void onlyCommonSickLeaveCarriesTheBenefitWitness() {
        List<ActiveWindow> windows = List.of(new ActiveWindow(LocalDate.of(2022, 1, 1), null));

        for (int seed = 0; seed < 200; seed++) {
            for (EmployeeLifecycleEvent event :
                    generator.generate(EMP, windows, HORIZON, ESP_TYPES, SIN_DERECHO, new Random(seed))) {
                AbsenceEventPayload payload = (AbsenceEventPayload) event.payload();
                if ("IT_COMMON".equals(payload.absenceTypeCode())) {
                    assertThat(payload.benefitEntitled())
                            .as("la baja por enfermedad comun siempre lleva testigo")
                            .isNotNull();
                } else {
                    assertThat(payload.benefitEntitled())
                            .as("%s no paga prestacion: el testigo no significa nada",
                                    payload.absenceTypeCode())
                            .isNull();
                }
            }
        }
    }

    /** Dos corridas, las mismas bajas sin derecho: es lo que el issue pide comprobar. */
    @Test
    void twoRunsGiveTheSameLeavesWithoutEntitlement() {
        List<ActiveWindow> windows = List.of(new ActiveWindow(LocalDate.of(2022, 3, 1), null));

        assertThat(generator.generate(EMP, windows, HORIZON, ESP_TYPES, SIN_DERECHO, new Random(7)))
                .isEqualTo(generator.generate(EMP, windows, HORIZON, ESP_TYPES, SIN_DERECHO, new Random(7)));
    }

    /**
     * <b>El testigo no mueve la plantilla.</b>
     *
     * <p>Es el test que de verdad importa de este issue, y el hermano del
     * {@code seedingOvertimeDoesNotMoveTheRestOfTheSeed}. Si el testigo saliera del {@code Random}
     * compartido, sacar un numero mas correria el resto de la corrida: cambiarian las fechas, los
     * tipos y las duraciones de <b>todas</b> las ausencias, y con ellas los 863 recibos de la demo. El
     * diferencial del {@code deploy#21} dice «se mueven exactamente los recibos con baja», y eso solo
     * es cierto si el testigo sale de la identidad de la ausencia y no del stream.
     *
     * <p>Se comprueba con las dos tasas extremas: con cero nadie se queda sin derecho y con uno nadie
     * lo tiene, y en los dos casos <b>las ausencias son las mismas</b>: mismo tipo, mismo inicio,
     * mismo fin.
     */
    @Test
    void theWitnessDoesNotMoveTheRestOfTheSeed() {
        List<ActiveWindow> windows = List.of(new ActiveWindow(LocalDate.of(2022, 1, 1), null));

        for (int seed = 0; seed < 100; seed++) {
            List<EmployeeLifecycleEvent> todas =
                    generator.generate(EMP, windows, HORIZON, ESP_TYPES, 0.0, new Random(seed));
            List<EmployeeLifecycleEvent> ninguna =
                    generator.generate(EMP, windows, HORIZON, ESP_TYPES, 1.0, new Random(seed));

            assertThat(sinTestigo(todas))
                    .as("la tasa del testigo no puede cambiar ni una fecha ni un tipo (semilla %d)", seed)
                    .isEqualTo(sinTestigo(ninguna));

            assertThat(todas).filteredOn(AbsenceScenarioGeneratorTest::esBaja)
                    .allSatisfy(event -> assertThat(testigo(event)).isTrue());
            assertThat(ninguna).filteredOn(AbsenceScenarioGeneratorTest::esBaja)
                    .allSatisfy(event -> assertThat(testigo(event)).isFalse());
        }
    }

    /**
     * Las bajas llegan al dia 16 y al 21 (workforce-loader#15).
     *
     * <p>Este es el test del issue, y lo que mide es un agujero que estuvo abierto meses: los tramos
     * del pago delegado -1-15 nada, 16-20 el 60 %, 21+ el 75 %- estaban calculados, citados y con
     * tests en el {@code b4rrhh/backend#129}, y el perfil de duraciones de aqui iba de 1 a 12 dias.
     * O sea que el {@code 111} valia CERO en los 863 recibos de la semilla y no habia forma de
     * ensenar la mitad de lo que el motor sabia hacer.
     *
     * <p>Se miran los tres tramos y se exige que los tres tengan gente. Y se exige tambien que la
     * baja corta siga siendo la mayoria: una demo donde la mitad de las bajas duran mes y medio
     * mentiria sobre cual es el caso normal, que es justo el error contrario.
     */
    @Test
    void sickLeavesReachTheDelegatedPaymentTranches() {
        List<ActiveWindow> windows = List.of(new ActiveWindow(LocalDate.of(2022, 1, 1), null));
        long hasta15 = 0;
        long de16a20 = 0;
        long de21enAdelante = 0;

        for (int i = 1; i <= 1_000; i++) {
            String empleado = String.format("EMP%06d", i);
            for (EmployeeLifecycleEvent event :
                    generator.generate(empleado, windows, HORIZON, ESP_TYPES, SIN_DERECHO, new Random(i))) {
                if (!esBaja(event)) continue;
                AbsenceEventPayload baja = (AbsenceEventPayload) event.payload();
                if (baja.endDate() == null) continue;
                long dias = java.time.temporal.ChronoUnit.DAYS.between(
                        event.effectiveDate(), baja.endDate()) + 1;
                if (dias <= 15) hasta15++;
                else if (dias <= 20) de16a20++;
                else de21enAdelante++;
            }
        }

        assertThat(de16a20).as("el tramo del 60 %% delegado tiene que existir").isPositive();
        assertThat(de21enAdelante).as("y el del 75 %% tambien").isPositive();
        assertThat(hasta15)
                .as("y la baja corta sigue siendo la mayoria, o la demo mentiria sobre el caso normal")
                .isGreaterThan(de16a20 + de21enAdelante);
    }

    /**
     * Sin derecho es la excepcion, y el numero es lo que se quiere ensenar.
     *
     * <p>Se mira sobre mil empleados porque el testigo sale de la identidad de la ausencia: para un
     * solo empleado la proporcion no significa nada, y para mil tiene que rondar la tasa. El margen es
     * ancho a proposito —entre el 2 y el 9 % para una tasa del 5 %—: lo que este test defiende es que
     * hay de los dos y que sin derecho es la minoria, no que la moneda este perfectamente equilibrada.
     */
    @Test
    void withoutEntitlementIsTheExceptionAndBothCasesExist() {
        List<ActiveWindow> windows = List.of(new ActiveWindow(LocalDate.of(2022, 1, 1), null));
        long conDerecho = 0;
        long sinDerecho = 0;

        for (int i = 1; i <= 1_000; i++) {
            String empleado = String.format("EMP%06d", i);
            for (EmployeeLifecycleEvent event :
                    generator.generate(empleado, windows, HORIZON, ESP_TYPES, SIN_DERECHO, new Random(i))) {
                if (!esBaja(event)) continue;
                if (testigo(event)) conDerecho++;
                else sinDerecho++;
            }
        }

        assertThat(sinDerecho).as("tiene que haber alguna sin derecho, o no hay nada que ensenar").isPositive();
        assertThat(conDerecho).as("y la mayoria con derecho").isGreaterThan(sinDerecho * 5);
        double proporcion = (double) sinDerecho / (conDerecho + sinDerecho);
        assertThat(proporcion).isBetween(0.02, 0.09);
    }

    private static boolean esBaja(EmployeeLifecycleEvent event) {
        return "IT_COMMON".equals(((AbsenceEventPayload) event.payload()).absenceTypeCode());
    }

    private static boolean testigo(EmployeeLifecycleEvent event) {
        return Boolean.TRUE.equals(((AbsenceEventPayload) event.payload()).benefitEntitled());
    }

    /** Las ausencias sin su testigo: tipo, inicio y fin, que es lo que no puede moverse. */
    private static List<String> sinTestigo(List<EmployeeLifecycleEvent> events) {
        return events.stream()
                .map(event -> {
                    AbsenceEventPayload payload = (AbsenceEventPayload) event.payload();
                    return payload.absenceTypeCode() + "|" + event.effectiveDate() + "|" + payload.endDate();
                })
                .toList();
    }

    private static List<CatalogOption> catalog(String... codes) {
        return Stream.of(codes).map(code -> new CatalogOption(code, code)).toList();
    }
}
