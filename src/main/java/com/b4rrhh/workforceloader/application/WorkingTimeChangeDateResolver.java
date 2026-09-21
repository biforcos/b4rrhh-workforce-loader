package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Que dia se parte el mes ({@code workforce-loader#12}).
 *
 * <p>Era la tercera fecha escrita a mano que dependia del periodo, y la ultima. El comentario de
 * su lado decia que <b>tiene que caer dentro del periodo que la demo calcula</b>, o sea: una
 * dependencia de {@code period} escrita como numero. El dia que alguien moviera el periodo a
 * {@code 202610}, esta fecha se habria quedado en septiembre, ningun recibo se habria partido, y
 * la demo del ambito {@code SEGMENT} habria dejado de ensenar nada <b>sin que nadie se enterara</b>
 * — el loader no avisaba.
 *
 * <p>Ahora sale del periodo, y si se pone a mano se comprueba que cae dentro. Si no cae, se para:
 * sembrar un mes sin tramos es peor que no sembrar, porque el fallo no se ve hasta que alguien
 * abre un recibo esperando dos lineas de salario base.
 */
@Component
public class WorkingTimeChangeDateResolver {

    private static final Logger log = LoggerFactory.getLogger(WorkingTimeChangeDateResolver.class);

    private final LoaderProperties properties;

    public WorkingTimeChangeDateResolver(LoaderProperties properties) {
        this.properties = properties;
    }

    /** El dia del corte, o {@code null} si el mes partido esta apagado. */
    public LocalDate resolve() {
        LoaderProperties.WorkingTimeChange corte = properties.getWorkingTimeChange();
        if (!corte.isEnabled()) {
            log.info("Mes partido: apagado.");
            return null;
        }

        LocalDate configurada = corte.getDate();
        LocalDate delPeriodo = properties.workingTimeChangeDateFromPeriod();

        if (configurada == null) {
            if (delPeriodo == null) {
                throw new IllegalStateException(
                        "El mes partido esta encendido y no hay de donde sacar el dia del corte:"
                                + " declara loader.period (el mes que se calcula, en yyyyMM) o"
                                + " loader.working-time-change.date. No se usa la fecha de hoy a"
                                + " proposito: la misma semilla tiene que dar lo mismo cualquier dia.");
            }
            log.info("Mes partido: corte el {}, el dia 16 del periodo {}.",
                    delPeriodo, properties.getPeriod());
            return delPeriodo;
        }

        exigirQueCaigaDentroDelPeriodo(configurada);
        if (delPeriodo != null && !configurada.equals(delPeriodo)) {
            log.warn("Mes partido: corte el {}, configurado a mano. Del periodo {} saldria el {};"
                    + " quitalo del application.yml para que lo ponga el periodo.",
                    configurada, properties.getPeriod(), delPeriodo);
        } else {
            log.info("Mes partido: corte el {}, configurado a mano.", configurada);
        }
        return configurada;
    }

    /**
     * Un corte fuera del periodo no parte nada, y el recibo sale entero sin una palabra.
     *
     * <p>Por eso esto lanza en vez de avisar: el informe de la corrida diria «Working time
     * changes: 5 success» igual, porque los cinco cambios de jornada se escriben bien — lo que
     * no pasa es que partan un recibo. Un fallo que sale en verde es el pecado del
     * {@code workspace#3}.
     */
    private void exigirQueCaigaDentroDelPeriodo(LocalDate configurada) {
        LocalDate desde = properties.periodStart();
        LocalDate hasta = properties.periodEnd();
        if (desde == null) {
            return;
        }
        if (configurada.isBefore(desde) || configurada.isAfter(hasta)) {
            throw new IllegalStateException(
                    "loader.working-time-change.date=" + configurada + " cae fuera del periodo "
                            + properties.getPeriod() + " (" + desde + " a " + hasta + ")."
                            + " Un corte fuera del mes que se calcula no parte ningun recibo, y la"
                            + " corrida terminaria en verde sin mes partido. Quitalo para que salga"
                            + " del periodo, o ponlo dentro.");
        }
    }
}
