package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Que dias se parte el mes ({@code workforce-loader#12}, repartido en el {@code #16}).
 *
 * <p>Era la tercera fecha escrita a mano que dependia del periodo, y la ultima. El comentario de
 * su lado decia que <b>tiene que caer dentro del periodo que la demo calcula</b>, o sea: una
 * dependencia de {@code period} escrita como numero. El dia que alguien moviera el periodo a
 * {@code 202610}, esa fecha se habria quedado en septiembre, ningun recibo se habria partido, y
 * la demo del ambito {@code SEGMENT} habria dejado de ensenar nada <b>sin que nadie se enterara</b>
 * — el loader no avisaba.
 *
 * <h2>Y por que ahora son varias</h2>
 *
 * <p>Con un solo mes calculado, un corte bastaba. Con nueve, un corte solo en septiembre diria que
 * el mes partido es una rareza de septiembre: los ocho meses cerrados saldrian todos enteros, y
 * quien mirara la historia de un empleado no encontraria ni un tramo. El mes partido <b>se reparte
 * por el ano</b>: el dia 16 de cada mes del ciclo, con unos cuantos empleados en cada uno.
 *
 * <p>El 16 y no otro dia por lo de siempre: lo que se ensena son dos tramos comparables, y partir
 * por la mitad los deja con el mismo peso.
 *
 * <p>Puesta a mano, la fecha sigue siendo <b>una</b> y tiene que caer dentro del mes abierto. Es la
 * via de escape de quien quiere exactamente un corte y sabe donde; el reparto es lo que pasa cuando
 * no se dice nada, que es el caso normal.
 */
@Component
public class WorkingTimeChangeDateResolver {

    private static final Logger log = LoggerFactory.getLogger(WorkingTimeChangeDateResolver.class);

    private static final int DIA_DEL_CORTE = 16;

    private final LoaderProperties properties;

    public WorkingTimeChangeDateResolver(LoaderProperties properties) {
        this.properties = properties;
    }

    /**
     * Los dias de corte, de mas viejo a mas nuevo. Vacia si el mes partido esta apagado.
     */
    public List<LocalDate> resolve() {
        LoaderProperties.WorkingTimeChange corte = properties.getWorkingTimeChange();
        if (!corte.isEnabled()) {
            log.info("Mes partido: apagado.");
            return List.of();
        }

        LocalDate configurada = corte.getDate();
        if (configurada != null) {
            exigirQueCaigaDentroDelPeriodo(configurada);
            log.info("Mes partido: un solo corte el {}, configurado a mano. Sin el, se repartiria"
                    + " el dia {} de cada mes del ciclo.", configurada, DIA_DEL_CORTE);
            return List.of(configurada);
        }

        List<Integer> meses = properties.cycleMonths();
        if (meses.isEmpty()) {
            throw new IllegalStateException(
                    "El mes partido esta encendido y no hay de donde sacar los dias del corte:"
                            + " declara loader.period (el mes abierto, en yyyyMM) o"
                            + " loader.working-time-change.date. No se usa la fecha de hoy a"
                            + " proposito: la misma semilla tiene que dar lo mismo cualquier dia.");
        }

        List<LocalDate> cortes = new ArrayList<>();
        for (Integer mes : meses) {
            cortes.add(LocalDate.of(mes / 100, mes % 100, DIA_DEL_CORTE));
        }
        log.info("Mes partido: {} cortes, el dia {} de cada mes de {} a {}.",
                cortes.size(), DIA_DEL_CORTE, meses.get(0), meses.get(meses.size() - 1));
        return cortes;
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
