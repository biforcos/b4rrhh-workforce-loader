package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Una parte de la plantilla en el regimen contrario al del convenio ({@code workforce-loader#13}).
 *
 * <h2>Por que hace falta sembrar los dos regimenes</h2>
 *
 * <p>La invariante del paso 4 —<b>la base no sabe si se pago</b>— solo se puede ensenar con
 * empleados de los dos lados: mismo salario, misma {@code B_CC}, distinto liquido. Con la plantilla
 * entera en el regimen del convenio, la consulta daria cero filas por no tener con que compararse,
 * que es la peor forma de que una invariante parezca cierta.
 *
 * <h2>Corregir y no anadir</h2>
 *
 * <p>La contratacion ya crea la primera fila con el testigo del convenio. Lo que hace esto es
 * <b>darle la vuelta desde el mismo dia</b>, o sea corregirla. Anadir una fila con la misma fecha
 * de inicio no es un alta —el backend contesta {@code EXTRA_PAYMENT_REGIME_IS_A_CORRECTION}— y
 * anadirla un dia despues dejaria un tramo de un dia sin significado.
 *
 * <p>Y por eso <b>ninguna fila empieza a mitad del periodo que se calcula</b>: las fechas son las
 * de las presencias, que ya estaban. Lo que parte un recibo en esta demo es el mes partido de la
 * jornada, y confundir dos causas de tramos haria ilegible lo que el paso 4 quiere ensenar.
 *
 * <h2>Un tramo por presencia</h2>
 *
 * <p>Quien se readmite tiene dos presencias y dos filas: la del alta y la de la readmision, en ese
 * orden, porque las crean esos dos flujos y nadie mas. La que decide su recibo del mes de la demo
 * es la ultima, asi que se corrigen las dos — media vuelta dejaria al readmitido en el regimen del
 * convenio justo en el mes que se mira.
 *
 * <h2>Azar propio, a proposito</h2>
 *
 * <p>Como el de las horas extra: lleva su propio {@code Random} derivado de la misma semilla, y lo
 * gasta SIEMPRE, le toque o no. Si gastara del comun desplazaria toda la secuencia posterior y la
 * semilla entera cambiaria —otras direcciones, otras ausencias, otros ceses— para dar la vuelta a
 * unas filas. Asi el diferencial contra la semilla de hoy es exactamente lo que este cambio anade.
 */
@Component
public class ExtraPaymentRegimeScenarioGenerator {

    /** El numero del issue que lo introdujo, para que se sepa de donde sale el desplazamiento. */
    static final int SEED_OFFSET = 13;

    public Random newRandom(long seed) {
        return new Random(seed + SEED_OFFSET);
    }

    /**
     * Los eventos de correccion de un empleado: uno por presencia, o ninguno.
     *
     * @param proratedByTheAgreement lo que dice el convenio que le aplica; se siembra lo contrario
     * @param activeWindows          sus periodos de presencia, en orden
     * @param random                 el azar propio de este generador, uno por corrida
     */
    public List<EmployeeLifecycleEvent> generate(
            LoaderProperties.Simulation simulation,
            boolean proratedByTheAgreement,
            List<ActiveWindow> activeWindows,
            Random random
    ) {
        // Se tira siempre, aunque la tasa sea cero o no haya presencias: la tirada de cada
        // empleado tiene que depender solo de su posicion en la plantilla.
        boolean leToca = random.nextDouble() < simulation.getExtrasProrrateadasRate();

        if (!leToca || activeWindows.isEmpty()) {
            return List.of();
        }

        boolean alReves = !proratedByTheAgreement;
        List<EmployeeLifecycleEvent> eventos = new ArrayList<>(activeWindows.size());
        for (int i = 0; i < activeWindows.size(); i++) {
            eventos.add(new EmployeeLifecycleEvent(
                    LifecycleEventType.CHANGE_EXTRA_PAYMENT_REGIME,
                    activeWindows.get(i).startDate(),
                    // La ocurrencia numero i+1: las crean el alta y la readmision, en ese orden.
                    new ExtraPaymentRegimeChangeEventPayload(i + 1, alReves)
            ));
        }
        return eventos;
    }
}
