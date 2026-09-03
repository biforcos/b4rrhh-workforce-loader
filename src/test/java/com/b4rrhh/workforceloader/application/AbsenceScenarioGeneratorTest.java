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
                    generator.generate(List.of(closed, open), HORIZON, ESP_TYPES, new Random(seed));

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
            events.addAll(generator.generate(List.of(new ActiveWindow(hire, null)), HORIZON, ESP_TYPES, random));
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
            events.addAll(generator.generate(List.of(new ActiveWindow(LocalDate.of(2023, 1, 1), null)), HORIZON, ESP_TYPES, random));
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
            events.addAll(generator.generate(List.of(new ActiveWindow(LocalDate.of(2024, 1, 1), null)), HORIZON, catalog, random));
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
        assertThat(generator.generate(List.of(new ActiveWindow(LocalDate.of(2024, 1, 1), null)), HORIZON, List.of(), new Random(1)))
                .isEmpty();
        assertThat(generator.generate(
                List.of(new ActiveWindow(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2))), HORIZON, ESP_TYPES, new Random(1)))
                .isEmpty();
    }

    @Test
    void sameSeedSameAbsencesWhateverTheCatalogOrder() {
        List<ActiveWindow> windows = List.of(new ActiveWindow(LocalDate.of(2023, 5, 1), null));
        List<CatalogOption> reversed = ESP_TYPES.reversed();

        List<EmployeeLifecycleEvent> first = generator.generate(windows, HORIZON, ESP_TYPES, new Random(42));
        List<EmployeeLifecycleEvent> second = generator.generate(windows, HORIZON, reversed, new Random(42));

        assertThat(first).isNotEmpty().isEqualTo(second);
    }

    private static List<CatalogOption> catalog(String... codes) {
        return Stream.of(codes).map(code -> new CatalogOption(code, code)).toList();
    }
}
