package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los dias del corte salen del ciclo, y no de una fecha escrita a mano (workforce-loader#12, con el
 * reparto del #16).
 *
 * <p>El test que importa sigue siendo {@link #movesWithThePeriodWithoutAnybodyTouchingAnything()}:
 * es el que se habria puesto rojo con la configuracion anterior, donde {@code date: 2026-09-16}
 * estaba escrito al lado de un comentario que decia «tiene que caer dentro del periodo». Y el que se
 * anade es {@link #spreadsOneCutPerMonthOfTheCycle()}: con un solo corte, los ocho meses cerrados
 * saldrian todos enteros y el mes partido pareceria una rareza de septiembre.
 */
class WorkingTimeChangeDateResolverTest {

    @Test
    void takesTheSixteenthOfThePeriod() {
        assertThat(resolverDe(202609, 202609, null, true).resolve())
                .containsExactly(LocalDate.of(2026, 9, 16));
    }

    /**
     * Un corte por mes del ciclo, el dia 16.
     *
     * <p>Con nueve meses calculados, un corte solo en el ultimo diria que el mes partido es una
     * rareza de septiembre: quien mirara la historia de un empleado no encontraria ni un tramo en
     * los ocho meses anteriores.
     */
    @Test
    void spreadsOneCutPerMonthOfTheCycle() {
        assertThat(resolverDe(202609, 202601, null, true).resolve())
                .hasSize(9)
                .startsWith(LocalDate.of(2026, 1, 16))
                .endsWith(LocalDate.of(2026, 9, 16));
    }

    /**
     * Mover el periodo mueve el corte, que es todo el issue.
     *
     * <p>Antes el corte era un numero en el {@code application.yml}: cambiar el periodo a octubre
     * dejaba el corte en septiembre, ningun recibo se partia, y la corrida terminaba en verde
     * anunciando sus cinco cambios de jornada escritos.
     */
    @Test
    void movesWithThePeriodWithoutAnybodyTouchingAnything() {
        assertThat(resolverDe(202610, 202610, null, true).resolve())
                .containsExactly(LocalDate.of(2026, 10, 16));
        assertThat(resolverDe(202701, 202701, null, true).resolve())
                .containsExactly(LocalDate.of(2027, 1, 16));
    }

    /** Apagado no hay cortes, y quien genera el escenario ve una lista vacia. */
    @Test
    void thereIsNoCutWhenTheSplitMonthIsOff() {
        assertThat(resolverDe(202609, 202609, null, false).resolve()).isEmpty();
    }

    /**
     * Encendido y sin periodo se para, en vez de tirar de la fecha de hoy.
     *
     * <p>Tirar del reloj daria una semilla distinta cada dia, y el diferencial contra la semilla
     * anterior dejaria de poder revisarse.
     */
    @Test
    void stopsWhenThereIsNowhereToTakeTheDayFrom() {
        assertThatThrownBy(() -> resolverDe(null, null, null, true).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declara loader.period");
    }

    /**
     * Puesta a mano dentro del periodo, se respeta y es <b>una sola</b>: manda lo escrito.
     *
     * <p>Es la via de escape de quien quiere exactamente un corte y sabe donde. El reparto es lo que
     * pasa cuando no se dice nada, que es el caso normal.
     */
    @Test
    void anExplicitDateInsideThePeriodWins() {
        assertThat(resolverDe(202609, 202601, LocalDate.of(2026, 9, 20), true).resolve())
                .containsExactly(LocalDate.of(2026, 9, 20));
    }

    /**
     * Y fuera del periodo se para.
     *
     * <p>Un corte en agosto con el periodo en septiembre escribe sus cambios de jornada, los
     * cuenta en el informe y no parte ni un recibo: el fallo no se ve hasta que alguien abre uno
     * esperando dos lineas de salario base.
     */
    @Test
    void anExplicitDateOutsideThePeriodStopsTheRun() {
        assertThatThrownBy(() -> resolverDe(202609, 202609, LocalDate.of(2026, 8, 16), true).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cae fuera del periodo 202609 (2026-09-01 a 2026-09-30)");
    }

    /** Sin periodo no hay contra que comprobarla, asi que se usa tal cual. */
    @Test
    void anExplicitDateWithoutAPeriodIsTakenAsItIs() {
        assertThat(resolverDe(null, null, LocalDate.of(2026, 9, 16), true).resolve())
                .containsExactly(LocalDate.of(2026, 9, 16));
    }

    private static WorkingTimeChangeDateResolver resolverDe(
            Integer periodo, Integer desde, LocalDate fecha, boolean encendido) {
        LoaderProperties properties = new LoaderProperties();
        properties.setPeriod(periodo);
        properties.getCycle().setFromPeriod(desde);
        properties.getWorkingTimeChange().setEnabled(encendido);
        properties.getWorkingTimeChange().setDate(fecha);
        return new WorkingTimeChangeDateResolver(properties);
    }
}
