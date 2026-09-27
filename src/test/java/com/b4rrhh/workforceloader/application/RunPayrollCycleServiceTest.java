package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quien recibe cada correccion, y desde cuando (workforce-loader#16).
 *
 * <p>Lo que estos tests defienden es lo que el issue llama <b>una necesidad y no una
 * comprobacion</b>: con nueve meses encadenados, una eleccion que dependa de una secuencia de azar
 * se mueve entera en cuanto cambia cualquier otra cosa, y entonces dos corridas no se pueden
 * comparar con nada.
 */
class RunPayrollCycleServiceTest {

    /**
     * Dos corridas eligen a los mismos, y no porque se guarde nada: la eleccion sale del numero de
     * empleado, del mes y del nombre de la correccion.
     */
    @Test
    void theSameEmployeesAreChosenEveryRun() {
        List<SeededEmployee> plantilla = plantillaDe(500);

        List<String> primera = elegidos(plantilla, 202603, "horas", 0.05);
        List<String> segunda = elegidos(plantilla, 202603, "horas", 0.05);

        assertThat(primera).isEqualTo(segunda);
        assertThat(primera).as("y alguien sale, o este test no prueba nada").isNotEmpty();
    }

    /**
     * Anadir gente detras no cambia a quien le tocaba.
     *
     * <p>Es lo que un {@code Random} compartido no puede dar: alli, un empleado mas al principio
     * corre la secuencia y cambia la eleccion de todos los que vienen despues.
     */
    @Test
    void addingMoreEmployeesDoesNotMoveTheOnesAlreadyChosen() {
        List<String> conQuinientos = elegidos(plantillaDe(500), 202603, "horas", 0.05);
        List<String> conMil = elegidos(plantillaDe(1000), 202603, "horas", 0.05);

        assertThat(conMil).startsWith(conQuinientos.toArray(new String[0]));
    }

    /**
     * Las tres correcciones no caen en la misma gente, porque el nombre entra en el sorteo.
     *
     * <p>Si cayeran juntas, el recibo del mes abierto tendria a unos pocos con todo y a la mayoria
     * con nada, que no es la operativa que se quiere ensenar.
     */
    @Test
    void eachCorrectionPicksItsOwnPeople() {
        List<SeededEmployee> plantilla = plantillaDe(1000);

        List<String> horas = elegidos(plantilla, 202603, "horas", 0.05);
        List<String> ausencias = elegidos(plantilla, 202603, "ausencia", 0.05);

        assertThat(horas).isNotEqualTo(ausencias);
        // Con la misma fraccion, el solape esperado entre dos sorteos independientes es pequeno.
        assertThat(horas).doesNotContainAnyElementsOf(
                ausencias.stream().filter(n -> !horas.contains(n)).toList());
        assertThat(horas.stream().filter(ausencias::contains).count())
                .as("algun solape es normal; que sean los mismos no")
                .isLessThan(horas.size());
    }

    /** Y cada mes elige a otros: una correccion no es del mismo de siempre. */
    @Test
    void eachMonthPicksItsOwnPeople() {
        List<SeededEmployee> plantilla = plantillaDe(1000);

        assertThat(elegidos(plantilla, 202603, "horas", 0.05))
                .isNotEqualTo(elegidos(plantilla, 202604, "horas", 0.05));
    }

    /** La fraccion se respeta de largo, que es lo unico que se le pide a un sorteo asi. */
    @Test
    void theFractionIsRoughlyWhatWasAsked() {
        List<SeededEmployee> plantilla = plantillaDe(2000);

        assertThat(elegidos(plantilla, 202603, "horas", 0.05)).hasSizeBetween(70, 130);
        assertThat(elegidos(plantilla, 202603, "horas", 0.02)).hasSizeBetween(20, 60);
    }

    /** Fraccion cero no elige a nadie: apagar una correccion la apaga de verdad. */
    @Test
    void aZeroFractionChoosesNobody() {
        assertThat(elegidos(plantillaDe(1000), 202603, "horas", 0.0)).isEmpty();
    }

    @Test
    void goesBackAcrossTheNewYear() {
        assertThat(RunPayrollCycleService.restarMeses(202603, 5)).isEqualTo(202510);
        assertThat(RunPayrollCycleService.restarMeses(202601, 1)).isEqualTo(202512);
        assertThat(RunPayrollCycleService.restarMeses(202609, 3)).isEqualTo(202606);
    }

    /**
     * Una correccion solo se escribe a quien estuvo el mes ENTERO.
     *
     * <p>A quien entro a mitad de mes se le puede corregir igual y seria legitimo, pero su recibo ya
     * esta partido en tramos por otra razon: mezclar las dos cosas en la misma persona hace
     * ilegible el atraso, que es lo unico que ese recibo viene a ensenar.
     */
    @Test
    void onlyWhoeverWasThereTheWholeMonth() {
        SeededEmployee entero = new SeededEmployee("INTERNAL", "EMP000001",
                List.of(new ActiveWindow(LocalDate.of(2026, 1, 1), null)));
        SeededEmployee aMedias = new SeededEmployee("INTERNAL", "EMP000002",
                List.of(new ActiveWindow(LocalDate.of(2026, 3, 10), null)));
        SeededEmployee queSeFue = new SeededEmployee("INTERNAL", "EMP000003",
                List.of(new ActiveWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 20))));

        assertThat(entero.coversWholeMonth(202603)).isTrue();
        assertThat(aMedias.coversWholeMonth(202603)).isFalse();
        assertThat(queSeFue.coversWholeMonth(202603)).isFalse();
        // Y el mes anterior, que si lo cubrio entero, si.
        assertThat(queSeFue.coversWholeMonth(202602)).isTrue();
    }

    /** Los meses del ciclo, de mas viejo a mas nuevo y sin saltos. */
    @Test
    void theCycleRunsForwardsFromTheFirstMonthToTheOpenOne() {
        LoaderProperties properties = new LoaderProperties();
        properties.setPeriod(202609);
        properties.getCycle().setFromPeriod(202601);

        assertThat(properties.cycleMonths())
                .containsExactly(202601, 202602, 202603, 202604, 202605,
                        202606, 202607, 202608, 202609);
    }

    /**
     * Y la ventana de contratacion termina el dia antes del PRIMER mes del ciclo.
     *
     * <p>Es lo que el issue pide con esas palabras, y no es cosmetico: con la ventana terminando el
     * dia antes del mes abierto, las altas caerian dentro de los ocho meses que se calculan y esos
     * recibos saldrian partidos por el alta, que es una causa de tramo distinta de la que la demo
     * ensena.
     */
    @Test
    void theHiringWindowEndsBeforeTheFirstCalculatedMonth() {
        LoaderProperties properties = new LoaderProperties();
        properties.setPeriod(202609);
        properties.getCycle().setFromPeriod(202601);

        assertThat(properties.hireDateToFromPeriod()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    /** Sin ciclo declarado, el unico mes es el abierto: el loader de antes del #16. */
    @Test
    void withoutACycleThereIsOnlyTheOpenMonth() {
        LoaderProperties properties = new LoaderProperties();
        properties.setPeriod(202609);

        assertThat(properties.cycleMonths()).containsExactly(202609);
        assertThat(properties.hireDateToFromPeriod()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    private static List<String> elegidos(
            List<SeededEmployee> plantilla, int mes, String correccion, double rate) {
        List<String> elegidos = new ArrayList<>();
        for (SeededEmployee empleado : plantilla) {
            if (RunPayrollCycleService.tocaA(empleado, mes, correccion, rate)) {
                elegidos.add(empleado.employeeNumber());
            }
        }
        return elegidos;
    }

    private static List<SeededEmployee> plantillaDe(int cuantos) {
        List<SeededEmployee> plantilla = new ArrayList<>(cuantos);
        for (int i = 1; i <= cuantos; i++) {
            plantilla.add(new SeededEmployee(
                    "INTERNAL",
                    String.format("EMP%06d", i),
                    List.of(new ActiveWindow(LocalDate.of(2024, 1, 1), null))));
        }
        return plantilla;
    }
}
