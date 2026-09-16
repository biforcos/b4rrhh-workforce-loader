package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las horas extra las declara una parte de la plantilla, no toda (workforce-loader#5).
 *
 * <p>Lo que hace real a una plantilla no es que todo el mundo cobre lo mismo: es que unos tengan
 * una linea que otros no tienen. Y como {@code concept_assignment} no acota por empleado, la unica
 * cosa que distingue a unos de otros es tener fila en {@code employee_payroll_input} o no tenerla.
 *
 * <p>Los tests miden el <b>reparto</b> sobre una plantilla entera y no un caso, que es lo mismo que
 * hace el generador de ausencias: un solo empleado no dice si el reparto es un reparto.
 */
class PayrollInputScenarioGeneratorTest {

    private static final int PERIODO = 202609;
    private static final LocalDate MES_ENTERO_DESDE = LocalDate.of(2024, 1, 1);

    private final PayrollInputScenarioGenerator generator = new PayrollInputScenarioGenerator();

    @Test
    void aroundAQuarterOfThePayrollDeclaresHours_andTheRestNone() {
        LoaderProperties.PayrollInput config = config();
        Random random = generator.newRandom(12345);

        int conHoras = 0;
        for (int i = 0; i < 1000; i++) {
            conHoras += generator.generate(config, siempreDeAlta(), random).size();
        }

        // Ni nadie ni todos: eso es lo unico que hace visible el concepto. El margen es ancho a
        // proposito —lo que se afirma es que hay reparto, no que el generador acierte un decimal—.
        assertThat(conHoras).isBetween(200, 300);
    }

    @Test
    void whatEachOneDeclaresIsTheConceptThePeriodAndAPlausibleNumberOfHours() {
        LoaderProperties.PayrollInput config = config();
        Random random = generator.newRandom(12345);

        List<PayrollInputEventPayload> cargas = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            generator.generate(config, siempreDeAlta(), random).stream()
                    .map(event -> (PayrollInputEventPayload) event.payload())
                    .forEach(cargas::add);
        }

        assertThat(cargas).isNotEmpty();
        assertThat(cargas).allSatisfy(carga -> {
            assertThat(carga.conceptCode()).isEqualTo("H01");
            assertThat(carga.period()).isEqualTo(PERIODO);
            assertThat(carga.quantity()).isBetween(BigDecimal.valueOf(4), BigDecimal.valueOf(20));
        });
        // Y no son todas iguales: un numero fijo seria un plus con otro nombre.
        assertThat(cargas.stream().map(PayrollInputEventPayload::quantity).distinct().count())
                .isGreaterThan(5);
    }

    /**
     * Solo quien esta el mes entero.
     *
     * <p>Quien entra o sale a mitad de mes ya tiene el recibo partido en tramos por otra razon, y
     * mezclar las dos cosas en la misma persona hace ilegible la unica pantalla donde esto se mira.
     */
    @Test
    void nobodyWhoIsMissingPartOfTheMonthDeclaresAnything() {
        LoaderProperties.PayrollInput config = config();
        config.setRate(1.0); // que la loteria no sea la que decide: lo que se mide es la presencia

        List<List<ActiveWindow>> fuera = List.of(
                // Cesado antes de que empiece el mes.
                List.of(new ActiveWindow(MES_ENTERO_DESDE, LocalDate.of(2026, 8, 31))),
                // Cesado a mitad de mes.
                List.of(new ActiveWindow(MES_ENTERO_DESDE, LocalDate.of(2026, 9, 20))),
                // De alta a mitad de mes.
                List.of(new ActiveWindow(LocalDate.of(2026, 9, 10), null)),
                // Y el que entra el dia despues de que acabe.
                List.of(new ActiveWindow(LocalDate.of(2026, 10, 1), null))
        );

        Random random = generator.newRandom(12345);
        for (List<ActiveWindow> ventanas : fuera) {
            assertThat(generator.generate(config, ventanas, random))
                    .as("ventanas %s", ventanas)
                    .isEmpty();
        }

        // Y el que empieza justo el dia 1 y sigue abierto si entra: el mes esta cubierto entero.
        assertThat(generator.generate(config, List.of(new ActiveWindow(LocalDate.of(2026, 9, 1), null)), random))
                .hasSize(1);
    }

    /**
     * La tirada se gasta aunque la persona no sea elegible.
     *
     * <p>Si solo se tirara para los elegibles, anadir o quitar un cese en cualquier otro sitio
     * correria la secuencia y cambiaria <b>quien</b> tiene horas. Asi la tirada de cada empleado
     * depende solo de su posicion en la plantilla.
     *
     * <p>Se mide <b>por posicion</b> y no comparando bolsas de cantidades. La primera version de
     * este test juntaba las cantidades de todos y comprobaba que las de despues estuvieran entre
     * las de antes: con un rango de diecisiete valores eso se cumple siempre, y el sabotaje de
     * tirar solo para los elegibles <b>paso en verde</b>. Lo que hay que afirmar es que a cada
     * empleado le sale exactamente lo mismo que antes, y eso solo se ve con la lista alineada.
     */
    @Test
    void theDrawIsSpentEvenForWhoeverCannotTakeIt() {
        LoaderProperties.PayrollInput config = config();

        // La misma plantilla de treinta, dos veces, y en la segunda el tercero esta cesado.
        List<String> antes = sorteo(config, 30, -1);
        List<String> despues = sorteo(config, 30, 2);

        // Al tercero deja de tocarle, claro.
        assertThat(despues.get(2)).isEqualTo("-");
        // Y a nadie mas le cambia nada: ni quien la tiene ni cuanta.
        for (int i = 0; i < 30; i++) {
            if (i == 2) {
                continue;
            }
            assertThat(despues.get(i))
                    .as("al empleado %d le tiene que salir lo mismo con y sin el cese del tercero", i)
                    .isEqualTo(antes.get(i));
        }
        // Y el escenario tiene que tener a quien comparar, o esto no prueba nada.
        assertThat(antes).filteredOn(horas -> !"-".equals(horas)).isNotEmpty();
    }

    /**
     * Lo que le sale a cada empleado de una plantilla, en orden. {@code "-"} es no tener horas.
     *
     * @param indiceCesado la posicion del que esta cesado antes del mes, o -1 si no hay ninguno
     */
    private List<String> sorteo(LoaderProperties.PayrollInput config, int tamano, int indiceCesado) {
        Random random = generator.newRandom(12345);
        List<String> resultado = new ArrayList<>();
        for (int i = 0; i < tamano; i++) {
            List<ActiveWindow> ventanas = i == indiceCesado
                    ? List.of(new ActiveWindow(MES_ENTERO_DESDE, LocalDate.of(2026, 1, 31)))
                    : siempreDeAlta();
            resultado.add(generator.generate(config, ventanas, random).stream()
                    .map(event -> ((PayrollInputEventPayload) event.payload()).quantity().toPlainString())
                    .findFirst()
                    .orElse("-"));
        }
        return resultado;
    }

    @Test
    void withoutPeriodOrDisabledItSeedsNothing() {
        LoaderProperties.PayrollInput apagado = config();
        apagado.setEnabled(false);

        LoaderProperties.PayrollInput sinPeriodo = config();
        sinPeriodo.setPeriod(null);

        LoaderProperties.PayrollInput sinConcepto = config();
        sinConcepto.setConceptCode("  ");

        for (LoaderProperties.PayrollInput config : List.of(apagado, sinPeriodo, sinConcepto)) {
            assertThat(generator.generate(config, siempreDeAlta(), generator.newRandom(12345))).isEmpty();
        }
    }

    private static LoaderProperties.PayrollInput config() {
        LoaderProperties.PayrollInput config = new LoaderProperties.PayrollInput();
        config.setPeriod(PERIODO);
        return config;
    }

    private static List<ActiveWindow> siempreDeAlta() {
        return List.of(new ActiveWindow(MES_ENTERO_DESDE, null));
    }
}
