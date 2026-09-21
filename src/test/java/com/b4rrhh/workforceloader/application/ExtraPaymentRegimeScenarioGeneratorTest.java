package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Una parte de la plantilla va en el regimen de pagas extras contrario al de su convenio
 * ({@code workforce-loader#13}).
 *
 * <p>Y hace falta que sea una parte y no todos: la invariante del paso 4 —<b>la base no sabe si se
 * pago</b>— se ensena comparando dos empleados con el mismo salario y distinto regimen. Con la
 * plantilla entera de un lado no hay con que comparar, y la consulta daria cero filas por no tener
 * caso, que es la peor forma de que una invariante parezca cierta.
 *
 * <p>Se mide el <b>reparto</b> sobre una plantilla entera y no un caso suelto, como el generador de
 * horas extra: un solo empleado no dice si el reparto es un reparto.
 */
class ExtraPaymentRegimeScenarioGeneratorTest {

    private static final LocalDate ALTA = LocalDate.of(2024, 3, 1);
    private static final LocalDate CESE = LocalDate.of(2025, 6, 30);
    private static final LocalDate READMISION = LocalDate.of(2025, 9, 15);

    private final ExtraPaymentRegimeScenarioGenerator generator = new ExtraPaymentRegimeScenarioGenerator();

    @Test
    void aroundAQuarterOfThePayrollGoesAgainstTheAgreement_andTheRestWithIt() {
        LoaderProperties.Simulation config = config(0.25);
        Random random = generator.newRandom(12345);

        int alReves = 0;
        for (int i = 0; i < 1000; i++) {
            if (!generator.generate(config, false, unaPresencia(), random).isEmpty()) {
                alReves++;
            }
        }

        // Ni nadie ni todos. El margen es ancho a proposito: lo que se afirma es que hay reparto,
        // no que el generador acierte un decimal.
        assertThat(alReves).isBetween(200, 300);
    }

    /**
     * Al reves DEL CONVENIO, no «prorrateado» a secas. Es la diferencia que hace que la semilla
     * siga teniendo los dos regimenes el dia que un convenio cambie de opinion.
     */
    @Test
    void whatIsSeededIsTheOppositeOfWhatTheAgreementSays() {
        LoaderProperties.Simulation siempre = config(1.0);

        assertThat(regimenSembrado(siempre, false))
                .as("convenio que no prorratea: la excepcion es prorratear")
                .isTrue();
        assertThat(regimenSembrado(siempre, true))
                .as("convenio que prorratea: la excepcion es no prorratear")
                .isFalse();
    }

    /**
     * Quien se readmite tiene dos presencias y dos filas, y se corrigen las dos.
     *
     * <p>Media vuelta dejaria al readmitido en el regimen del convenio justo en el mes que la demo
     * mira, que es el que decide la ultima fila.
     */
    @Test
    void aRehiredEmployeeGetsOneCorrectionPerPresence_numberedInOrder() {
        List<EmployeeLifecycleEvent> eventos =
                generator.generate(config(1.0), false, dosPresencias(), generator.newRandom(1));

        assertThat(eventos).hasSize(2);
        assertThat(eventos.get(0).effectiveDate()).isEqualTo(ALTA);
        assertThat(numeroDe(eventos.get(0))).isEqualTo(1);
        assertThat(eventos.get(1).effectiveDate()).isEqualTo(READMISION);
        assertThat(numeroDe(eventos.get(1))).isEqualTo(2);
    }

    /**
     * La propiedad que el issue pide y que no se ve en el resultado sino en las fechas: ninguna
     * correccion estrena una fecha.
     *
     * <p>Cada una cae exactamente en el arranque de una presencia, que ya existia. Por eso esto no
     * anade ni un corte al periodo que se calcula: lo que parte un recibo en esta demo es el mes
     * partido de la jornada, y dos causas de tramos a la vez harian ilegible lo que el paso 4
     * quiere ensenar.
     */
    @Test
    void everyCorrectionLandsOnTheStartOfAPresence_soItAddsNoCutToAnyPeriod() {
        List<ActiveWindow> presencias = dosPresencias();

        List<LocalDate> fechas = generator.generate(config(1.0), false, presencias, generator.newRandom(7))
                .stream().map(EmployeeLifecycleEvent::effectiveDate).toList();

        assertThat(fechas).containsExactlyElementsOf(
                presencias.stream().map(ActiveWindow::startDate).toList());
    }

    /**
     * El azar se gasta SIEMPRE, le toque o no y tenga presencias o no.
     *
     * <p>Si solo se tirara para los elegibles, anadir o quitar un cese en cualquier otro sitio
     * correria la secuencia de este generador y cambiaria quien va al reves. La tirada de cada
     * empleado tiene que depender solo de su posicion en la plantilla.
     */
    @Test
    void theDrawIsSpentEvenForAnEmployeeWithNoPresence() {
        LoaderProperties.Simulation config = config(0.25);

        Random conHuecos = generator.newRandom(12345);
        Random sinHuecos = generator.newRandom(12345);

        // Uno de cada tres empleados no tiene presencia ninguna; los otros dos, una.
        int conHuecosAlReves = 0;
        int sinHuecosAlReves = 0;
        for (int i = 0; i < 300; i++) {
            List<ActiveWindow> presencias = i % 3 == 0 ? List.of() : unaPresencia();
            if (!generator.generate(config, false, presencias, conHuecos).isEmpty()) {
                conHuecosAlReves++;
            }
            if (!generator.generate(config, false, unaPresencia(), sinHuecos).isEmpty()) {
                sinHuecosAlReves++;
            }
        }

        // Los que SI tienen presencia coinciden en los dos recorridos: la ausencia de presencia
        // quita eventos pero no desplaza la secuencia.
        assertThat(conHuecosAlReves).isLessThan(sinHuecosAlReves);
        for (int i = 0; i < 10; i++) {
            Random a = generator.newRandom(99);
            Random b = generator.newRandom(99);
            generator.generate(config, false, List.of(), a);
            generator.generate(config, false, unaPresencia(), b);
            assertThat(a.nextLong()).isEqualTo(b.nextLong());
        }
    }

    /** La misma semilla, los mismos empleados. */
    @Test
    void theSameSeedPicksTheSameEmployees() {
        LoaderProperties.Simulation config = config(0.25);

        assertThat(quienesVanAlReves(config, 12345))
                .isEqualTo(quienesVanAlReves(config, 12345))
                .isNotEqualTo(quienesVanAlReves(config, 12346));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<Integer> quienesVanAlReves(LoaderProperties.Simulation config, long seed) {
        Random random = generator.newRandom(seed);
        List<Integer> elegidos = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) {
            if (!generator.generate(config, false, unaPresencia(), random).isEmpty()) {
                elegidos.add(i);
            }
        }
        return elegidos;
    }

    private boolean regimenSembrado(LoaderProperties.Simulation config, boolean loQueDiceElConvenio) {
        List<EmployeeLifecycleEvent> eventos =
                generator.generate(config, loQueDiceElConvenio, unaPresencia(), generator.newRandom(3));
        assertThat(eventos).hasSize(1);
        return ((ExtraPaymentRegimeChangeEventPayload) eventos.get(0).payload()).prorated();
    }

    private static int numeroDe(EmployeeLifecycleEvent evento) {
        return ((ExtraPaymentRegimeChangeEventPayload) evento.payload()).extraPaymentRegimeNumber();
    }

    private static List<ActiveWindow> unaPresencia() {
        return List.of(new ActiveWindow(ALTA, null));
    }

    private static List<ActiveWindow> dosPresencias() {
        return List.of(new ActiveWindow(ALTA, CESE), new ActiveWindow(READMISION, null));
    }

    private static LoaderProperties.Simulation config(double rate) {
        LoaderProperties.Simulation simulation = new LoaderProperties.Simulation();
        simulation.setExtrasProrrateadasRate(rate);
        return simulation;
    }
}
