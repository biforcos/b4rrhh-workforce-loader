package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.api.dto.CatalogOption;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Ausencias de un empleado a lo largo de sus periodos de presencia (workforce-loader#5).
 *
 * <p>Una plantilla de mil personas donde nadie se ha puesto malo ni ha tenido vacaciones no se
 * lee como datos de verdad. Aqui se reparten unas pocas ausencias por persona y año, de tipos
 * distintos y con duraciones que se corresponden con lo que son: las vacaciones duran una o dos
 * semanas y caen sobre todo en verano; una baja comun, unos dias; un permiso, uno o dos.
 *
 * <p>Los tipos salen del catalogo EMPLOYEE_ABSENCE_TYPE del sistema de reglas, no de aqui: si el
 * catalogo trae un codigo que este generador no conoce, se usa igual, con un perfil generico. Y si
 * no trae ninguno, no hay ausencias. Lo que si es de aqui es el perfil de cada codigo conocido —peso,
 * duracion y estacionalidad—, que es conocimiento del dominio y no del catalogo.
 *
 * <p>Reglas duras, que son las que el backend comprueba: toda ausencia cae estrictamente dentro
 * de un periodo de presencia; dos ausencias de la misma persona nunca se tocan (queda al menos un
 * dia entre ellas, porque el backend considera solapadas las que comparten un dia); y una
 * ausencia sin fecha de fin solo puede ser la ultima, y solo si la persona sigue en activo.
 */
@Component
public class AbsenceScenarioGenerator {

    /**
     * Ausencias por persona y año, de media. Entre vacaciones, alguna baja y algun permiso, tres o
     * cuatro al año es una plantilla normal; con mil personas y dos o tres años de historia salen
     * varios miles de filas, bastantes para que las pantallas de ausencias tengan algo que enseñar.
     */
    static final double ABSENCES_PER_YEAR = 3.5;
    /** De cada cien personas en activo, las que estan ahora mismo de baja, sin fecha de vuelta. */
    static final int OPEN_ABSENCE_PERCENT = 3;
    /** Una baja abierta empezo hace poco: como mucho estos dias antes del horizonte de la simulacion. */
    static final int OPEN_ABSENCE_MAX_DAYS_AGO = 30;
    /** Vacaciones que caen en julio o agosto, si el periodo los incluye. */
    static final int SUMMER_VACATION_PERCENT = 60;

    /** Peso relativo entre tipos, duracion en dias naturales y si prefiere el verano. */
    record Profile(int weight, int minDays, int maxDays, boolean preferSummer) {
    }

    /**
     * Perfil de los codigos que se conocen del catalogo ESP. Un codigo que no este aqui recibe
     * {@link #UNKNOWN_TYPE}; un codigo de aqui que no este en el catalogo no se usa.
     */
    static final Map<String, Profile> PROFILES = Map.of(
            "VACATION", new Profile(40, 5, 15, true),
            "IT_COMMON", new Profile(30, 1, 12, false),
            "PAID_PERSONAL_LEAVE", new Profile(18, 1, 3, false),
            "UNPAID_LEAVE", new Profile(4, 5, 30, false),
            "IT_WORK_ACCIDENT", new Profile(4, 7, 45, false),
            "PARENTAL_LEAVE", new Profile(2, 112, 112, false),
            "FORCE_MAJEURE", new Profile(2, 1, 2, false)
    );
    static final Profile UNKNOWN_TYPE = new Profile(3, 1, 5, false);
    /** Lo unico que se deja abierto es una baja medica: unas vacaciones sin fecha de vuelta no existen. */
    static final List<String> OPEN_ABSENCE_TYPES = List.of("IT_COMMON", "IT_WORK_ACCIDENT");

    private record Candidate(String code, Profile profile) {
    }

    private record Planned(String code, LocalDate start, LocalDate end) {
    }

    /**
     * @param windows           periodos de presencia; el ultimo puede estar abierto (sin fin)
     * @param openWindowHorizon hasta donde se planifica en un periodo abierto: el «hoy» de la simulacion
     * @param absenceTypes      tipos del catalogo; sin ninguno, no hay ausencias
     */
    public List<EmployeeLifecycleEvent> generate(
            List<ActiveWindow> windows,
            LocalDate openWindowHorizon,
            List<CatalogOption> absenceTypes,
            Random random
    ) {
        if (absenceTypes == null || absenceTypes.isEmpty()) {
            return List.of();
        }

        // Ordenados por codigo para que la misma semilla de la misma plantilla, venga el catalogo
        // en el orden que venga.
        List<Candidate> candidates = absenceTypes.stream()
                .map(option -> normalizeCode(option.code()))
                .distinct()
                .sorted()
                .map(code -> new Candidate(code, PROFILES.getOrDefault(code, UNKNOWN_TYPE)))
                .toList();

        List<EmployeeLifecycleEvent> events = new ArrayList<>();
        for (ActiveWindow window : windows) {
            for (Planned planned : planWindow(window, openWindowHorizon, candidates, random)) {
                events.add(new EmployeeLifecycleEvent(
                        LifecycleEventType.ABSENCE,
                        planned.start(),
                        new AbsenceEventPayload(planned.code(), planned.end())
                ));
            }
        }
        return events;
    }

    private List<Planned> planWindow(
            ActiveWindow window,
            LocalDate openWindowHorizon,
            List<Candidate> candidates,
            Random random
    ) {
        // Estrictamente dentro: ni el dia del alta ni el del cese.
        LocalDate first = window.startDate().plusDays(1);
        LocalDate last = window.endDate() != null ? window.endDate().minusDays(1) : openWindowHorizon;
        if (last.isBefore(first)) {
            return List.of();
        }

        long days = ChronoUnit.DAYS.between(first, last) + 1;
        List<Planned> planned = new ArrayList<>();

        int count = expectedCount(days, random);
        for (int i = 0; i < count; i++) {
            Candidate candidate = pickWeighted(candidates, random);
            Profile profile = candidate.profile();
            int duration = profile.minDays() + random.nextInt(profile.maxDays() - profile.minDays() + 1);

            for (int attempt = 0; attempt < 6; attempt++) {
                LocalDate start = pickStart(first, last, duration, profile.preferSummer(), random);
                if (start == null) {
                    break;
                }
                LocalDate end = start.plusDays(duration - 1L);
                if (fits(planned, start, end)) {
                    planned.add(new Planned(candidate.code(), start, end));
                    break;
                }
            }
        }

        if (window.endDate() == null && random.nextInt(100) < OPEN_ABSENCE_PERCENT) {
            planOpenAbsence(first, last, candidates, planned, random);
        }

        planned.sort(Comparator.comparing(Planned::start));
        return planned;
    }

    /** Una baja que empezo hace poco y sigue: solo si queda despues de todo lo demas. */
    private static void planOpenAbsence(
            LocalDate first,
            LocalDate last,
            List<Candidate> candidates,
            List<Planned> planned,
            Random random
    ) {
        List<Candidate> openTypes = candidates.stream()
                .filter(candidate -> OPEN_ABSENCE_TYPES.contains(candidate.code()))
                .toList();
        if (openTypes.isEmpty()) {
            return;
        }

        LocalDate earliest = max(first, last.minusDays(OPEN_ABSENCE_MAX_DAYS_AGO));
        LocalDate start = between(earliest, last, random);
        boolean afterEverything = planned.stream()
                .allMatch(other -> other.end().plusDays(1).isBefore(start));
        if (afterEverything) {
            planned.add(new Planned(RandomSelector.pickRandom(openTypes, random).code(), start, null));
        }
    }

    /** Cuantas ausencias caben en tantos dias, con la parte fraccionaria echada a suertes. */
    private static int expectedCount(long days, Random random) {
        double expected = days * ABSENCES_PER_YEAR / 365.0;
        int count = (int) Math.floor(expected);
        if (random.nextDouble() < expected - count) {
            count++;
        }
        return count;
    }

    private static Candidate pickWeighted(List<Candidate> candidates, Random random) {
        int total = candidates.stream().mapToInt(candidate -> candidate.profile().weight()).sum();
        int roll = random.nextInt(total);
        for (Candidate candidate : candidates) {
            roll -= candidate.profile().weight();
            if (roll < 0) {
                return candidate;
            }
        }
        return candidates.getLast();
    }

    /** Un inicio tal que la ausencia entera quepa en [first, last]; null si no cabe. */
    private static LocalDate pickStart(
            LocalDate first,
            LocalDate last,
            int duration,
            boolean preferSummer,
            Random random
    ) {
        LocalDate latestStart = last.minusDays(duration - 1L);
        if (latestStart.isBefore(first)) {
            return null;
        }

        if (preferSummer && random.nextInt(100) < SUMMER_VACATION_PERCENT) {
            List<LocalDate[]> summers = new ArrayList<>();
            for (int year = first.getYear(); year <= latestStart.getYear(); year++) {
                LocalDate from = max(LocalDate.of(year, 7, 1), first);
                LocalDate to = min(LocalDate.of(year, 8, 31), latestStart);
                if (!to.isBefore(from)) {
                    summers.add(new LocalDate[]{from, to});
                }
            }
            if (!summers.isEmpty()) {
                LocalDate[] summer = RandomSelector.pickRandom(summers, random);
                return between(summer[0], summer[1], random);
            }
        }

        return between(first, latestStart, random);
    }

    /** Sin tocarse: el backend da por solapadas dos ausencias que comparten un dia. */
    private static boolean fits(List<Planned> planned, LocalDate start, LocalDate end) {
        return planned.stream().allMatch(other ->
                end.plusDays(1).isBefore(other.start()) || start.isAfter(other.end().plusDays(1)));
    }

    private static LocalDate between(LocalDate from, LocalDate to, Random random) {
        return from.plusDays(random.nextInt((int) ChronoUnit.DAYS.between(from, to) + 1));
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
