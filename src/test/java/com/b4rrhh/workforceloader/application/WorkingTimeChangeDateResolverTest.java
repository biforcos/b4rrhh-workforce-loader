package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El dia del corte sale del periodo, y no de una fecha escrita a mano (workforce-loader#12).
 *
 * <p>El test que importa es {@link #movesWithThePeriodWithoutAnybodyTouchingAnything()}: es el
 * que se habria puesto rojo con la configuracion anterior, donde {@code date: 2026-09-16} estaba
 * escrito al lado de un comentario que decia «tiene que caer dentro del periodo». Los demas
 * cubren lo que pasa cuando falta el dato o cuando alguien lo pone a mano.
 */
class WorkingTimeChangeDateResolverTest {

    @Test
    void takesTheSixteenthOfThePeriod() {
        assertThat(resolverDe(202609, null, true).resolve()).isEqualTo(LocalDate.of(2026, 9, 16));
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
        assertThat(resolverDe(202610, null, true).resolve()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(resolverDe(202701, null, true).resolve()).isEqualTo(LocalDate.of(2027, 1, 16));
    }

    /** Apagado no hay corte, y quien genera el escenario lo ve como un {@code null}. */
    @Test
    void thereIsNoCutWhenTheSplitMonthIsOff() {
        assertThat(resolverDe(202609, null, false).resolve()).isNull();
    }

    /**
     * Encendido y sin periodo se para, en vez de tirar de la fecha de hoy.
     *
     * <p>Tirar del reloj daria una semilla distinta cada dia, y el diferencial contra la semilla
     * anterior dejaria de poder revisarse.
     */
    @Test
    void stopsWhenThereIsNowhereToTakeTheDayFrom() {
        assertThatThrownBy(() -> resolverDe(null, null, true).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declara loader.period");
    }

    /** Puesta a mano dentro del periodo, se respeta: manda lo escrito. */
    @Test
    void anExplicitDateInsideThePeriodWins() {
        assertThat(resolverDe(202609, LocalDate.of(2026, 9, 20), true).resolve())
                .isEqualTo(LocalDate.of(2026, 9, 20));
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
        assertThatThrownBy(() -> resolverDe(202609, LocalDate.of(2026, 8, 16), true).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cae fuera del periodo 202609 (2026-09-01 a 2026-09-30)");
    }

    /** Sin periodo no hay contra que comprobarla, asi que se usa tal cual. */
    @Test
    void anExplicitDateWithoutAPeriodIsTakenAsItIs() {
        assertThat(resolverDe(null, LocalDate.of(2026, 9, 16), true).resolve())
                .isEqualTo(LocalDate.of(2026, 9, 16));
    }

    private static WorkingTimeChangeDateResolver resolverDe(
            Integer periodo, LocalDate fecha, boolean encendido) {
        LoaderProperties properties = new LoaderProperties();
        properties.setPeriod(periodo);
        properties.getWorkingTimeChange().setEnabled(encendido);
        properties.getWorkingTimeChange().setDate(fecha);
        return new WorkingTimeChangeDateResolver(properties);
    }
}
