package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.domain.model.PayrollCycleMonth;
import com.b4rrhh.workforceloader.domain.model.PayrollCycleSummary;
import com.b4rrhh.workforceloader.infrastructure.api.B4rrhhLifecycleClient;
import com.b4rrhh.workforceloader.infrastructure.api.PayrollCycleApiClient;
import com.b4rrhh.workforceloader.infrastructure.api.dto.BulkFinalizePayrollRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.BulkFinalizePayrollResponse;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CreateEmployeePayrollInputRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.LaunchPayrollCalculationRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.PayrollCalculationRunResponse;
import com.b4rrhh.workforceloader.infrastructure.api.dto.UpdateEmployeePayrollInputRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.UpsertAbsenceRequest;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * El ciclo de nomina, ejecutado ({@code workforce-loader#16}).
 *
 * <p>Es {@code CICLO.md} corriendo. Para cada mes del ciclo:
 *
 * <ol>
 *   <li>calcular el mes entero por la API;</li>
 *   <li>si no es el ultimo, <b>cerrar en masa</b>, como haria una empresa el dia del pago;</li>
 *   <li>y <b>despues del cierre</b>, meter por la API las correcciones a pasado que llegan en la
 *       operativa real. Despues y no antes: una escritura a un mes que todavia no se ha entregado no
 *       es una correccion, es un dato que llega tarde y ya esta. Lo que crea una marca es tocar un
 *       mes que ya tiene recibo entregado ({@code b4rrhh/backend#130}).</li>
 * </ol>
 *
 * <p>El ultimo mes se queda <b>abierto</b>, calculado, con los atrasos de las correcciones del mes
 * anterior dentro. Eso es lo que la demo ensena.
 *
 * <h2>Por que esto no lo hace un script</h2>
 *
 * <p>Porque el paso 4 depende del 3 y el 3 del 2: una correccion escrita antes de cerrar no deja
 * marca, y un mes calculado sin haber cerrado el anterior no lee ninguna base reguladora. Un script
 * de nueve invocaciones sueltas puede hacer lo mismo y no puede <b>garantizar</b> el orden ni
 * contarlo; esto deja una fila por mes con lo que salio de cada paso.
 *
 * <h2>Determinista, y esta vez es una necesidad</h2>
 *
 * <p>Quien recibe cada correccion <b>no sale de un {@code Random}</b>: sale de la identidad del
 * empleado y del mes. Es la decision del {@code workforce-loader#14} aplicada aqui, y con nueve
 * meses encadenados ya no es una comodidad: una secuencia de azar que se desplace por cualquier
 * motivo —un empleado mas, un mes mas— cambia quien tiene atrasos en TODOS los meses siguientes, y
 * entonces dos corridas no se pueden comparar con nada.
 */
@Service
public class RunPayrollCycleService {

    private static final Logger log = LoggerFactory.getLogger(RunPayrollCycleService.class);

    /**
     * El tipo de ausencia de la correccion olvidada, y <b>cuantos dias</b>.
     *
     * <p>Una baja comun de tres dias: el parte que llega tarde es la correccion a pasado mas comun
     * que existe, y tres dias es lo que se tarda en traerlo.
     *
     * <p>Aqui habia un {@code PAID_PERSONAL_LEAVE} de un dia y <b>estaba mal</b>. La corrida lo
     * ensena: se escribieron 96 ausencias olvidadas, sus 96 marcas se consumieron, y la profundidad
     * 2 salio con UNA O DOS lineas en vez de con noventa. El motivo es que un permiso retribuido se
     * cobra entero —solo la baja comun y la excedencia quitan dias—, asi que el delta de aquel mes
     * era CERO y no habia atraso que pagar. Todo verde, y la mitad del escenario sin sembrar.
     *
     * <p>Tres dias y no mas tambien importa: los tres primeros dias de una baja comun no los paga
     * nadie, asi que el atraso sale en negativo —dinero que se devuelve— y ese es el caso que la
     * demo no tenia de ninguna otra forma.
     */
    private static final String TIPO_AUSENCIA_OLVIDADA = "IT_COMMON";
    private static final int DIAS_AUSENCIA_OLVIDADA = 3;

    private final LoaderProperties properties;
    private final PayrollCycleApiClient cycleClient;
    private final B4rrhhLifecycleClient lifecycleClient;

    public RunPayrollCycleService(
            LoaderProperties properties,
            PayrollCycleApiClient cycleClient,
            B4rrhhLifecycleClient lifecycleClient
    ) {
        this.properties = properties;
        this.cycleClient = cycleClient;
        this.lifecycleClient = lifecycleClient;
    }

    public PayrollCycleSummary run(List<SeededEmployee> seeded) {
        LoaderProperties.Cycle cycle = properties.getCycle();
        if (!cycle.isEnabled()) {
            log.info("Ciclo de nomina: apagado. La semilla se queda sin calcular, como antes del"
                    + " workforce-loader#16.");
            return PayrollCycleSummary.empty();
        }

        List<Integer> meses = properties.cycleMonths();
        if (meses.isEmpty()) {
            throw new IllegalStateException(
                    "El ciclo de nomina esta encendido y no hay meses que correr: declara"
                            + " loader.cycle.from-period y loader.period.");
        }

        int abierto = meses.get(meses.size() - 1);
        log.info("Ciclo de nomina: {} meses, de {} a {}. Se cierran todos menos el ultimo, que se"
                + " queda abierto con sus atrasos. Limite de retro: {} meses atras.",
                meses.size(), meses.get(0), abierto, cycle.getRetroLimitMonthsBack());

        if (properties.getRun().isDryRun()) {
            log.info("EN SECO: no se lanza ningun calculo ni se cierra nada. Lo que haria el ciclo"
                    + " son {} lanzamientos y {} cierres sobre {} empleados.",
                    meses.size(), meses.size() - 1, seeded.size());
            return PayrollCycleSummary.empty();
        }

        Instant arranque = Instant.now();
        List<PayrollCycleMonth> filas = new ArrayList<>(meses.size());

        for (Integer mes : meses) {
            Instant arranqueDelMes = Instant.now();
            boolean esElAbierto = mes.equals(abierto);

            PayrollCalculationRunResponse ejecucion = calcular(mes, cycle);

            int cerrados = 0;
            if (!esElAbierto) {
                BulkFinalizePayrollResponse cierre = cerrar(mes);
                cerrados = valor(cierre.totalFinalized());
            }

            // Las correcciones se escriben DESPUES del cierre y solo si hubo cierre: sobre un mes
            // abierto no hay nada entregado que corregir.
            Correcciones correcciones = esElAbierto
                    ? Correcciones.ninguna()
                    : escribirCorrecciones(mes, meses.get(0), seeded, cycle);

            filas.add(new PayrollCycleMonth(
                    mes,
                    ejecucion.runId(),
                    ejecucion.status(),
                    valor(ejecucion.totalCalculated()),
                    valor(ejecucion.totalNotValid()),
                    valor(ejecucion.totalErrors()),
                    valor(ejecucion.totalRetroUnits()),
                    valor(ejecucion.totalRetroRecalculated()),
                    cerrados,
                    correcciones.horas(),
                    correcciones.ausencias(),
                    correcciones.fueraDelLimite(),
                    Duration.between(arranqueDelMes, Instant.now()).toSeconds()
            ));

            log.info("Mes {} listo: {} recibos, {} vigentes, {} cerrados, correcciones {}+{}+{}.",
                    mes, valor(ejecucion.totalCalculated()), valor(ejecucion.totalRetroRecalculated()),
                    cerrados, correcciones.horas(), correcciones.ausencias(),
                    correcciones.fueraDelLimite());
        }

        return new PayrollCycleSummary(
                filas,
                abierto,
                cycle.getRetroLimitMonthsBack(),
                Duration.between(arranque, Instant.now()).toSeconds());
    }

    /** Lanza el calculo del mes y espera a que termine. Si no termina bien, para el ciclo. */
    private PayrollCalculationRunResponse calcular(int mes, LoaderProperties.Cycle cycle) {
        String limite = String.valueOf(restarMeses(mes, cycle.getRetroLimitMonthsBack()));

        log.info("Mes {}: lanzando el calculo (limite de retro {}).", mes, limite);
        PayrollCalculationRunResponse lanzada = cycleClient.launch(new LaunchPayrollCalculationRequest(
                properties.getDefaults().getRuleSystemCode(),
                String.valueOf(mes),
                "NORMAL",
                "GRAPH",
                "1.0",
                LaunchPayrollCalculationRequest.TargetSelectionRequest.allWithPresence(),
                limite,
                null
        ));

        PayrollCalculationRunResponse terminada = esperar(lanzada.runId(), cycle);

        // Un mes que no llego al final no se cierra y no se sigue. Seguir dejaria una semilla en la
        // que un mes tiene la mitad de los recibos, y eso no se ve hasta que alguien abre una ficha
        // en la demo: es el fallo que sale en verde del workspace#3.
        if (terminada.hasFailed()) {
            throw new IllegalStateException("El calculo de " + mes + " termino como FAILED"
                    + " (ejecucion " + terminada.runId() + "). El ciclo para aqui: los meses"
                    + " siguientes leen el vigente de este, asi que seguir daria una semilla que"
                    + " parece entera y no lo es.");
        }
        if (valor(terminada.totalErrors()) > 0) {
            throw new IllegalStateException("El calculo de " + mes + " dejo "
                    + terminada.totalErrors() + " unidades con error (ejecucion " + terminada.runId()
                    + "). El ciclo para: mira sus mensajes antes de seguir.");
        }
        return terminada;
    }

    private PayrollCalculationRunResponse esperar(Long runId, LoaderProperties.Cycle cycle) {
        if (runId == null) {
            throw new IllegalStateException("El lanzamiento no devolvio identidad de ejecucion.");
        }
        Instant limite = Instant.now().plus(Duration.ofMinutes(cycle.getRunTimeoutMinutes()));
        while (true) {
            PayrollCalculationRunResponse actual = cycleClient.getRun(runId);
            if (actual.isFinished()) {
                return actual;
            }
            if (Instant.now().isAfter(limite)) {
                throw new IllegalStateException("La ejecucion " + runId + " lleva mas de "
                        + cycle.getRunTimeoutMinutes() + " minutos sin terminar (estado "
                        + actual.status() + "). El ciclo para: no se sabe si acabara, y lo que"
                        + " viene despues cierra recibos.");
            }
            dormir(cycle.getPollSeconds());
        }
    }

    private BulkFinalizePayrollResponse cerrar(int mes) {
        log.info("Mes {}: cerrando en masa.", mes);
        return cycleClient.bulkFinalize(new BulkFinalizePayrollRequest(
                properties.getDefaults().getRuleSystemCode(),
                String.valueOf(mes),
                "NORMAL",
                LaunchPayrollCalculationRequest.TargetSelectionRequest.allWithPresence()
        ));
    }

    /**
     * Las tres correcciones que llegan despues de cerrar.
     *
     * <p>Quien recibe cada una sale de {@link #tocaA}, que no gasta azar: es la identidad del
     * empleado y del mes.
     */
    private Correcciones escribirCorrecciones(
            int mesCerrado,
            int primerMesDelCiclo,
            List<SeededEmployee> seeded,
            LoaderProperties.Cycle cycle
    ) {
        int horas = 0;
        int ausencias = 0;
        int fueraDelLimite = 0;

        int mesAnterior = restarMeses(mesCerrado, 1);
        int mesProfundo = restarMeses(mesCerrado, cycle.getFueraDelLimiteMesesAtras());
        // Ni la ausencia olvidada ni la correccion profunda se escriben sobre un mes anterior al
        // ciclo: ahi no hay recibo entregado, asi que no dejan marca y lo unico que dejan es una
        // fila de datos que nadie mira. En enero, por ejemplo, el «mes anterior» es diciembre de
        // 2025, que esta fuera.
        boolean hayMesAnterior = mesAnterior >= primerMesDelCiclo;
        // La correccion profunda solo se escribe si su mes ya se cerro en ESTE ciclo. Sobre un mes
        // sin recibo entregado el puerto del backend#130 no deja marca ninguna, asi que el aviso
        // del #132 no existiria y la corrida terminaria en verde sin el caso que venia a sembrar.
        boolean hayMesProfundo = mesProfundo >= primerMesDelCiclo;
        int profundasEscritas = 0;

        for (SeededEmployee empleado : seeded) {
            if (empleado.coversWholeMonth(mesCerrado)
                    && tocaA(empleado, mesCerrado, "horas", cycle.getHorasAlMesCerradoRate())) {
                if (escribirHoras(empleado, mesCerrado)) {
                    horas++;
                }
            }

            if (hayMesAnterior
                    && empleado.coversWholeMonth(mesAnterior)
                    && tocaA(empleado, mesCerrado, "ausencia", cycle.getAusenciaAlMesAnteriorRate())) {
                if (escribirAusenciaOlvidada(empleado, mesAnterior)) {
                    ausencias++;
                }
            }

            // Se cuenta lo ESCRITO y no lo intentado: si una no se pudo escribir, se prueba con
            // el siguiente hasta tener las tres. Contar intentos dejaria meses con cero
            // correcciones profundas y el aviso del #132 sin sembrar, con la corrida en verde.
            if (hayMesProfundo
                    && profundasEscritas < cycle.getFueraDelLimiteEmployees()
                    && empleado.coversWholeMonth(mesProfundo)
                    && escribirHoras(empleado, mesProfundo)) {
                fueraDelLimite++;
                profundasEscritas++;
            }
        }

        return new Correcciones(horas, ausencias, fueraDelLimite);
    }

    /**
     * Declara —o corrige— las horas de un mes ya cerrado.
     *
     * <p>Crear y corregir son el mismo gesto visto desde la operativa: lo que llega a primeros de
     * octubre es «las horas de septiembre fueron estas», y si esa persona ya tenia declaradas otras
     * lo que hay es una cifra distinta, no una segunda fila. Se intenta crear y, si ya existe, se
     * corrige. Las dos cosas dejan marca, que es lo que importa aqui.
     */
    private boolean escribirHoras(SeededEmployee empleado, int periodo) {
        String concepto = properties.getPayrollInput().getConceptCode();
        BigDecimal horas = BigDecimal.valueOf(horasDe(empleado, periodo));
        try {
            lifecycleClient.createPayrollInput(
                    properties.getDefaults().getRuleSystemCode(),
                    empleado.employeeTypeCode(),
                    empleado.employeeNumber(),
                    new CreateEmployeePayrollInputRequest(concepto, periodo, horas));
            return true;
        } catch (RuntimeException ex) {
            log.debug("La entrada de {} en {} ya existia, se corrige: {}",
                    empleado.employeeNumber(), periodo, ex.getMessage());
        }
        try {
            lifecycleClient.updatePayrollInput(
                    properties.getDefaults().getRuleSystemCode(),
                    empleado.employeeTypeCode(),
                    empleado.employeeNumber(),
                    concepto,
                    periodo,
                    new UpdateEmployeePayrollInputRequest(horas.add(BigDecimal.ONE)));
            return true;
        } catch (RuntimeException ex) {
            log.debug("Tampoco se pudo corregir la entrada de {} en {}: {}",
                    empleado.employeeNumber(), periodo, ex.getMessage());
            return false;
        }
    }

    private boolean escribirAusenciaOlvidada(SeededEmployee empleado, int periodo) {
        // El dia 10 y no uno al azar: la ausencia olvidada tiene que caer dentro del mes y lejos de
        // sus bordes, para que no se solape con una que el simulador ya sembrara pegada al cambio
        // de mes.
        LocalDate desde = LocalDate.of(periodo / 100, periodo % 100, 10);
        LocalDate hasta = desde.plusDays(DIAS_AUSENCIA_OLVIDADA - 1L);
        try {
            lifecycleClient.upsertAbsence(
                    properties.getDefaults().getRuleSystemCode(),
                    empleado.employeeTypeCode(),
                    empleado.employeeNumber(),
                    TIPO_AUSENCIA_OLVIDADA,
                    desde,
                    new UpsertAbsenceRequest(hasta, null, null));
            return true;
        } catch (RuntimeException ex) {
            // Un solape con una ausencia ya sembrada es lo normal y no es un fallo: el simulador
            // reparte tres o cuatro ausencias por persona y ano, asi que alguna cae ahi.
            log.debug("No se pudo escribir la ausencia olvidada de {} a {}: {}",
                    empleado.employeeNumber(), periodo, ex.getMessage());
            return false;
        }
    }

    /**
     * Si a este empleado le toca esta correccion este mes, <b>sin gastar azar</b>.
     *
     * <p>Sale de un hash del numero de empleado, el mes y el nombre de la correccion. Dos corridas
     * eligen a los mismos; anadir un empleado mas no cambia a quien le tocaba; y las tres
     * correcciones no se eligen entre si —la de horas y la de ausencias no caen siempre en la misma
     * persona— porque el nombre entra en el hash.
     */
    static boolean tocaA(SeededEmployee empleado, int mes, String correccion, double rate) {
        if (rate <= 0) {
            return false;
        }
        int hash = (empleado.employeeNumber() + "|" + mes + "|" + correccion).hashCode();
        // Math.abs(Integer.MIN_VALUE) es Integer.MIN_VALUE, que sigue siendo negativo: la mascara
        // quita el bit de signo sin ese caso especial.
        int positivo = hash & 0x7fffffff;
        return (positivo % 10_000) < Math.round(rate * 10_000);
    }

    /** Cuantas horas declara. Tambien de la identidad, por lo mismo: entre 4 y 20. */
    private static int horasDe(SeededEmployee empleado, int periodo) {
        int hash = (empleado.employeeNumber() + "|" + periodo + "|horas").hashCode() & 0x7fffffff;
        return 4 + (hash % 17);
    }

    static int restarMeses(int periodo, int meses) {
        LocalDate fecha = LocalDate.of(periodo / 100, periodo % 100, 1).minusMonths(meses);
        return fecha.getYear() * 100 + fecha.getMonthValue();
    }

    private static int valor(Integer i) {
        return i == null ? 0 : i;
    }

    private static void dormir(int segundos) {
        try {
            Thread.sleep(segundos * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ciclo interrumpido mientras esperaba una ejecucion.", ex);
        }
    }

    private record Correcciones(int horas, int ausencias, int fueraDelLimite) {

        static Correcciones ninguna() {
            return new Correcciones(0, 0, 0);
        }
    }
}
