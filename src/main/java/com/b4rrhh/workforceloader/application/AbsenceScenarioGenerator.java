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
 *
 * <h2>El testigo de derecho a prestacion ({@code workforce-loader#14})</h2>
 *
 * <p>Las bajas por enfermedad comun llevan el testigo, y una fraccion pequena va <b>sin derecho</b>
 * para que la demo tenga los dos casos: sin derecho la baja quita dias, no paga prestacion y no
 * cotiza, y ese recibo se lee distinto ({@code b4rrhh/backend#129}).
 *
 * <p><b>El testigo no sale del {@code Random} de la simulacion.</b> Sale de la <b>identidad</b> de la
 * ausencia: el empleado, el tipo y el dia en que empieza. Es la decision de este issue y no un
 * detalle, por dos razones:
 *
 * <ul>
 *   <li><b>La plantilla no se mueve.</b> Sacar un numero mas del {@code Random} compartido corre el
 *       resto de la corrida y cambia <i>todas</i> las ausencias, todos los ceses y todas las
 *       readmisiones. El diferencial del {@code deploy#21} dice «se mueven exactamente los recibos con
 *       baja»; con el testigo sacado del stream comun, se moverian los 863.</li>
 *   <li><b>Es estable aunque la plantilla cambie.</b> La misma baja del mismo empleado tiene el mismo
 *       testigo aunque manana se siembre otra cosa antes. Eso convierte «dos corridas, mismas bajas
 *       sin derecho» en algo cierto por construccion y no por suerte.</li>
 * </ul>
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

    /** El unico tipo del que cuelga una prestacion, y por tanto el unico con testigo. */
    static final String IT_COMMON = "IT_COMMON";

    /**
     * De cada cien bajas por enfermedad comun, las que son <b>largas</b> ({@code workforce-loader#15}).
     *
     * <p>Doce de cada cien. El numero no sale de una estadistica: sale de lo que la demo tiene que
     * poder ensenar. Los tramos del pago delegado son 1-15 (nada), 16-20 (60 %) y 21+ (75 %)
     * ({@code b4rrhh/backend#129}), y con el perfil de 1 a 12 dias que habia <b>ninguna baja llegaba
     * al dia 16</b>: los tres tramos estaban calculados, citados y con tests, y el {@code 111} valia
     * cero en los 863 recibos de la semilla.
     *
     * <p>Doce por ciento sobre las bajas comunes de mil personas y nueve meses deja unas decenas en
     * cada tramo alto y alguna que cruza de un mes al siguiente, que es el recibo mas distinto que
     * tiene la demo. Mas seria mentir sobre cual es el caso normal: la baja corta lo es.
     */
    static final int LONG_SICK_LEAVE_PERCENT = 12;
    /** Lo que dura una baja larga: de aqui hasta {@link #LONG_SICK_LEAVE_MAX_DAYS}. */
    static final int LONG_SICK_LEAVE_MIN_DAYS = 20;
    /**
     * Y hasta aqui.
     *
     * <p>Sesenta y no mas: una baja de meses existe y no es lo que falta ensenar — el
     * {@code PARENTAL_LEAVE} ya tiene 112 dias y ensena el caso del mes entero sin salir de la
     * plantilla. Lo que faltaba era el tramo 16-20 y el 21+, y sesenta los cubre con holgura.
     */
    static final int LONG_SICK_LEAVE_MAX_DAYS = 60;

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
     * @param employeeNumber    de quien son las ausencias; entra en la identidad del testigo de
     *                          derecho y en nada mas ({@code workforce-loader#14})
     * @param windows           periodos de presencia; el ultimo puede estar abierto (sin fin)
     * @param openWindowHorizon hasta donde se planifica en un periodo abierto: el «hoy» de la simulacion
     * @param absenceTypes      tipos del catalogo; sin ninguno, no hay ausencias
     * @param sinDerechoRate    que parte de las bajas por enfermedad comun va sin derecho a prestacion
     */
    public List<EmployeeLifecycleEvent> generate(
            String employeeNumber,
            List<ActiveWindow> windows,
            LocalDate openWindowHorizon,
            List<CatalogOption> absenceTypes,
            double sinDerechoRate,
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
                        new AbsenceEventPayload(
                                planned.code(),
                                planned.end(),
                                benefitEntitled(employeeNumber, planned, sinDerechoRate))
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
            int duration = drawDuration(candidate.code(), profile, random);

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

    /**
     * Cuanto dura esta ausencia, con la cola de las bajas largas ({@code workforce-loader#15}).
     *
     * <p>El sorteo de la cola se hace <b>siempre</b> para las bajas comunes, gane o pierda, y no solo
     * cuando la baja va a ser larga. Es la misma regla que ya gobierna el sorteo de las horas extra:
     * tirar el dado solo en unos casos hace que la secuencia del {@code Random} dependa de cuantos
     * casos hubo antes, y entonces cualquier cambio en cualquier otro sitio mueve la plantilla
     * entera.
     *
     * <p><b>Esta funcion si mueve la plantilla</b>, y se dice: el perfil de duraciones cambia, asi
     * que todas las bajas comunes de la semilla cambian de largo y con ellas los recuentos. Es lo que
     * el {@code workforce-loader#15} decidio de frente — no se toco entonces para no estropear el
     * diferencial del paso 5, y ahora ya se puede.
     */
    private static int drawDuration(String code, Profile profile, Random random) {
        int corta = profile.minDays() + random.nextInt(profile.maxDays() - profile.minDays() + 1);
        boolean tocaLarga = random.nextInt(100) < LONG_SICK_LEAVE_PERCENT;
        if (!IT_COMMON.equals(code) || !tocaLarga) {
            return corta;
        }
        return LONG_SICK_LEAVE_MIN_DAYS
                + random.nextInt(LONG_SICK_LEAVE_MAX_DAYS - LONG_SICK_LEAVE_MIN_DAYS + 1);
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

    /**
     * Si esta baja lleva derecho a prestacion ({@code workforce-loader#14}).
     *
     * <p>Nulo para los tipos que no son baja por enfermedad comun: en unas vacaciones el derecho a
     * prestacion no es un dato que falte, es una pregunta que no significa nada, y por eso no se
     * manda. El backend deja entonces el valor que corresponde.
     *
     * <p>Y para las bajas, un numero sacado de la <b>identidad</b> de la ausencia y no del
     * {@code Random} de la corrida. El {@code hashCode} de la cadena es determinista por contrato de
     * Java —lo esta desde la 1.2 y esta escrito en el Javadoc de {@code String}—, asi que sirve: la
     * misma baja del mismo empleado da el mismo testigo en todas las corridas y en todas las maquinas.
     * Se pasa por {@code Math.abs} sobre un {@code long} porque {@code Math.abs(Integer.MIN_VALUE)} es
     * negativo, y un modulo de un numero negativo tambien.
     */
    static Boolean benefitEntitled(String employeeNumber, Planned planned, double sinDerechoRate) {
        if (!IT_COMMON.equals(planned.code())) {
            return null;
        }
        String identidad = employeeNumber + "|" + planned.code() + "|" + planned.start();
        long sorteo = Math.abs((long) identidad.hashCode()) % 10_000L;
        return sorteo >= Math.round(sinDerechoRate * 10_000);
    }

    private static String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
