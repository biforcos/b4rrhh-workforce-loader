package com.b4rrhh.workforceloader.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "loader")
public class LoaderProperties {

    @Valid
    @NotNull
    private Backend backend = new Backend();

    @Valid
    @NotNull
    private Run run = new Run();

    @Valid
    @NotNull
    private Generation generation = new Generation();

    @Valid
    @NotNull
    private Defaults defaults = new Defaults();

    @Valid
    @NotNull
    private CostCenter costCenter = new CostCenter();

    @Valid
    @NotNull
    private Simulation simulation = new Simulation();

    @Valid
    @NotNull
    private WorkingTimeChange workingTimeChange = new WorkingTimeChange();

    @Valid
    @NotNull
    private PayrollInput payrollInput = new PayrollInput();

    @Valid
    @NotNull
    private Filters filters = new Filters();

    @Valid
    @NotNull
    private Cycle cycle = new Cycle();

    /**
     * El mes que la demo deja <b>abierto</b>, en {@code yyyyMM}.
     *
     * <p>Es <b>la fuente</b> de la que salen las fechas que dependen de el, y por eso esta aqui
     * arriba y no dentro de un bloque: estaba en {@code payroll-input.period}, donde un lector
     * que buscara «que mes calcula la demo» no iba a mirar ({@code workforce-loader#11}).
     *
     * <p>Va escrito y no sale del reloj. Es la misma regla que gobierna la fecha del mes partido:
     * la misma semilla tiene que dar lo mismo cualquier dia.
     *
     * <p>Desde el {@code workforce-loader#16} ya no es el unico mes que se calcula: es el ultimo
     * del ciclo, el que se queda abierto con sus atrasos. Los demas —de {@link Cycle#getFromPeriod()}
     * en adelante— se calculan y se cierran. Lo que <b>no</b> ha cambiado es de donde sale la
     * ventana de contratacion: sale del PRIMER mes que se calcula, no de este.
     */
    private Integer period;

    public Backend getBackend() {
        return backend;
    }

    public void setBackend(Backend backend) {
        this.backend = backend;
    }

    public Run getRun() {
        return run;
    }

    public void setRun(Run run) {
        this.run = run;
    }

    public Generation getGeneration() {
        return generation;
    }

    public void setGeneration(Generation generation) {
        this.generation = generation;
    }

    public Defaults getDefaults() {
        return defaults;
    }

    public void setDefaults(Defaults defaults) {
        this.defaults = defaults;
    }

    public CostCenter getCostCenter() {
        return costCenter;
    }

    public void setCostCenter(CostCenter costCenter) {
        this.costCenter = costCenter;
    }

    public PayrollInput getPayrollInput() {
        return payrollInput;
    }

    public void setPayrollInput(PayrollInput payrollInput) {
        this.payrollInput = payrollInput;
    }

    public WorkingTimeChange getWorkingTimeChange() {
        return workingTimeChange;
    }

    public void setWorkingTimeChange(WorkingTimeChange workingTimeChange) {
        this.workingTimeChange = workingTimeChange;
    }

    public Simulation getSimulation() {
        return simulation;
    }

    public void setSimulation(Simulation simulation) {
        this.simulation = simulation;
    }

    public Integer getPeriod() {
        return period;
    }

    public void setPeriod(Integer period) {
        this.period = period;
    }

    /**
     * El ultimo dia que puede llevar un alta: el anterior al mes que se calcula.
     *
     * <p>Sale del periodo y no de una fecha escrita ({@code workforce-loader#11}). Que ninguna
     * alta caiga dentro del mes de la demo es una propiedad que se quiere: asi cada recibo cubre
     * el mes entero y lo que parte un recibo es un cambio de jornada, de categoria o de contrato,
     * que es lo que la demo esta ensenando.
     *
     * <p>Si {@code generation.hire-date-to} esta puesto, manda el puesto. Vacio si no hay
     * ninguno de los dos, y entonces el loader se para diciendolo: un {@code now()} por omision
     * haria que la semilla cambiara cada dia.
     */
    public LocalDate hireDateToFromPeriod() {
        Integer primero = firstCalculatedPeriod();
        if (primero == null) {
            return null;
        }
        return LocalDate.of(primero / 100, primero % 100, 1).minusDays(1);
    }

    /**
     * El dia en que se parte el mes: el 16 del periodo que se calcula
     * ({@code workforce-loader#12}).
     *
     * <p>El 16 y no otro porque lo que la demo ensena es un mes con dos tramos comparables:
     * partir por la mitad deja los dos con el mismo peso, y un {@code SALARIO_BASE} a dos
     * precios se lee de un vistazo. En un mes de 30 dias son 15 y 15; en uno de 31, 15 y 16.
     *
     * <p>Sale del periodo por lo mismo que el final de la ventana de contratacion: una fecha que
     * depende de otra y se escribe aparte son dos fuentes. Estaba escrita a mano, y el dia que
     * alguien moviera {@code period} a {@code 202610} se habria quedado en septiembre — ningun
     * recibo partido, y el loader sin decir nada.
     */
    public LocalDate workingTimeChangeDateFromPeriod() {
        if (period == null) {
            return null;
        }
        return LocalDate.of(period / 100, period % 100, 16);
    }

    public Cycle getCycle() {
        return cycle;
    }

    public void setCycle(Cycle cycle) {
        this.cycle = cycle;
    }

    /**
     * El primer mes que el ciclo calcula, que es de donde sale la ventana de contratacion.
     *
     * <p>Sin ciclo es el propio {@code period}, que era el unico mes que existia antes del
     * {@code workforce-loader#16}.
     */
    public Integer firstCalculatedPeriod() {
        Integer desde = cycle == null ? null : cycle.getFromPeriod();
        return desde == null ? period : desde;
    }

    /**
     * Los meses del ciclo, de mas viejo a mas nuevo.
     *
     * <p>Vacia si no hay ciclo que correr. Nunca se calcula al reves ni se saltan meses: el
     * calculo de un mes lee el vigente del anterior ({@code b4rrhh/backend#131}), asi que el orden
     * no es cosmetico.
     */
    public List<Integer> cycleMonths() {
        List<Integer> meses = new ArrayList<>();
        Integer desde = firstCalculatedPeriod();
        if (desde == null || period == null) {
            return meses;
        }
        LocalDate actual = LocalDate.of(desde / 100, desde % 100, 1);
        LocalDate ultimo = LocalDate.of(period / 100, period % 100, 1);
        while (!actual.isAfter(ultimo)) {
            meses.add(actual.getYear() * 100 + actual.getMonthValue());
            actual = actual.plusMonths(1);
        }
        return meses;
    }

    /**
     * El ultimo dia del ciclo, que es hasta donde tiene que llegar la simulacion.
     *
     * <p>No es lo mismo que el final de la ventana de contratacion: la ventana termina el dia antes
     * del PRIMER mes que se calcula, y el ciclo sigue nueve meses mas. Sembrar ausencias solo hasta
     * la ventana dejaria los ultimos meses del ciclo vacios de todo lo que no sea nomina pura.
     */
    public LocalDate cycleEnd() {
        return periodEnd();
    }

    /** El primer y el ultimo dia del periodo, para comprobar que una fecha cae dentro. */
    public LocalDate periodStart() {
        return period == null ? null : LocalDate.of(period / 100, period % 100, 1);
    }

    public LocalDate periodEnd() {
        LocalDate inicio = periodStart();
        return inicio == null ? null : inicio.plusMonths(1).minusDays(1);
    }

    public Filters getFilters() {
        return filters;
    }

    public void setFilters(Filters filters) {
        this.filters = filters;
    }

    public static class Backend {

        @NotBlank
        private String baseUrl;

        private String authToken;

        @NotBlank
        private String hirePath = "/employees/hire";

        @Valid
        @NotNull
        private Expected expected = new Expected();

        /**
         * Cada cuantas escrituras se vuelve a preguntar al backend quien es.
         * No basta comprobarlo al arrancar: el backend del otro lado se puede
         * sustituir a mitad de corrida (workforce-loader#8).
         */
        @Min(1)
        private int recheckEveryWrites = 200;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getAuthToken() {
            return authToken;
        }

        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }

        public String getHirePath() {
            return hirePath;
        }

        public void setHirePath(String hirePath) {
            this.hirePath = hirePath;
        }

        public Expected getExpected() {
            return expected;
        }

        public void setExpected(Expected expected) {
            this.expected = expected;
        }

        public int getRecheckEveryWrites() {
            return recheckEveryWrites;
        }

        public void setRecheckEveryWrites(int recheckEveryWrites) {
            this.recheckEveryWrites = recheckEveryWrites;
        }
    }

    /**
     * Con quien tiene que estar hablando esta corrida, dicho antes de empezar.
     *
     * Sin esto no hay nada que comprobar: el backend puede contestar la verdad
     * sobre si mismo, pero solo la corrida sabe cual era la respuesta buena. Por
     * eso database y employees no tienen valor por defecto y su ausencia para la
     * corrida (workforce-loader#8).
     *
     * - database: host:puerto/nombre, por ejemplo localhost:5432/b4rrhh_wl7. Con
     *   host y puerto, no solo el nombre: la base de la demo y la de desarrollo
     *   se llaman las dos b4rrhh.
     * - employees: los que tiene que haber ya. Una siembra desde cero espera 0;
     *   una corrida incremental, otra cosa.
     * - schemaVersion: opcional. Cuando se declara, se comprueba.
     */
    public static class Expected {

        private String database;

        private Long employees;

        private String schemaVersion;

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public Long getEmployees() {
            return employees;
        }

        public void setEmployees(Long employees) {
            this.employees = employees;
        }

        public String getSchemaVersion() {
            return schemaVersion;
        }

        public void setSchemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
        }
    }

    public static class Run {

        @NotNull
        private RunMode mode = RunMode.LIFECYCLE;

        /**
         * En seco, como el {@code application.yml} (workforce-loader#9).
         *
         * <p>Y aqui tambien, no solo alli: si algun dia falta la clave en el fichero —un perfil
         * nuevo, un fichero recortado— el que decide es este valor. Un defecto que solo vive en
         * la configuracion protege mientras nadie toque la configuracion, que es justo cuando
         * hace falta.
         */
        private boolean dryRun = true;

        public RunMode getMode() {
            return mode;
        }

        public void setMode(RunMode mode) {
            this.mode = mode;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public void setDryRun(boolean dryRun) {
            this.dryRun = dryRun;
        }
    }

    public static class Generation {

        @Min(1)
        private int count = 10;

        private long seed = 12345L;

        @NotBlank
        private String employeeNumberPrefix = "MAS";

        @Min(1)
        private int employeeNumberPadding = 6;

        /**
         * Desde cuando se contrata. <b>Puede faltar</b>: si falta, la pone el catalogo
         * ({@code HireWindowResolver}, {@code workforce-loader#1}).
         *
         * <p>Dejo de ser obligatoria y dejo de tener valor por omision a la vez, y las dos
         * cosas por el mismo motivo: un {@code now().minusMonths(3)} es una fecha que parece
         * razonable y no esta medida contra nada, asi que taparia justo el caso en el que
         * hace falta preguntar.
         */
        private LocalDate hireDateFrom;

        /**
         * Hasta cuando se contrata. <b>Puede faltar</b>: si falta, sale de {@code loader.period}
         * ({@code workforce-loader#11}).
         *
         * <p>Dejo de tener valor por omision igual que su hermana, y por el mismo motivo con una
         * vuelta de tuerca: el valor era {@code LocalDate.now()}, y «hoy» es exactamente lo que
         * este fichero prohibe para las otras dos fechas que dependen del periodo, porque la
         * misma semilla tiene que dar lo mismo cualquier dia.
         */
        private LocalDate hireDateTo;

        private BigDecimal workingTimePercentage;

        private BigDecimal weeklyHours;

        private BigDecimal monthlyHours;

        private BigDecimal dailyHours;

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public long getSeed() {
            return seed;
        }

        public void setSeed(long seed) {
            this.seed = seed;
        }

        public String getEmployeeNumberPrefix() {
            return employeeNumberPrefix;
        }

        public void setEmployeeNumberPrefix(String employeeNumberPrefix) {
            this.employeeNumberPrefix = employeeNumberPrefix;
        }

        public int getEmployeeNumberPadding() {
            return employeeNumberPadding;
        }

        public void setEmployeeNumberPadding(int employeeNumberPadding) {
            this.employeeNumberPadding = employeeNumberPadding;
        }

        public LocalDate getHireDateFrom() {
            return hireDateFrom;
        }

        public void setHireDateFrom(LocalDate hireDateFrom) {
            this.hireDateFrom = hireDateFrom;
        }

        public LocalDate getHireDateTo() {
            return hireDateTo;
        }

        public void setHireDateTo(LocalDate hireDateTo) {
            this.hireDateTo = hireDateTo;
        }

        public BigDecimal getWorkingTimePercentage() {
            return workingTimePercentage;
        }

        public void setWorkingTimePercentage(BigDecimal workingTimePercentage) {
            this.workingTimePercentage = workingTimePercentage;
        }

        public BigDecimal getWeeklyHours() {
            return weeklyHours;
        }

        public void setWeeklyHours(BigDecimal weeklyHours) {
            this.weeklyHours = weeklyHours;
        }

        public BigDecimal getMonthlyHours() {
            return monthlyHours;
        }

        public void setMonthlyHours(BigDecimal monthlyHours) {
            this.monthlyHours = monthlyHours;
        }

        public BigDecimal getDailyHours() {
            return dailyHours;
        }

        public void setDailyHours(BigDecimal dailyHours) {
            this.dailyHours = dailyHours;
        }
    }

    public static class Defaults {

        @NotBlank
        private String ruleSystemCode;

        @NotBlank
        private String employeeTypeCode;

        public String getRuleSystemCode() {
            return ruleSystemCode;
        }

        public void setRuleSystemCode(String ruleSystemCode) {
            this.ruleSystemCode = ruleSystemCode;
        }

        public String getEmployeeTypeCode() {
            return employeeTypeCode;
        }

        public void setEmployeeTypeCode(String employeeTypeCode) {
            this.employeeTypeCode = employeeTypeCode;
        }
    }

    /**
     * Los centros salen del catalogo COST_CENTER del sistema de reglas, como el resto de codigos.
     * Antes venian de una lista aqui que nadie rellenaba, y con la lista vacia el reparto se
     * apagaba solo: mil empleados y ni una fila en employee.cost_center (workforce-loader#5).
     */
    public static class CostCenter {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * El mes partido de la demo: unos cuantos empleados cambian de jornada a mitad de mes,
     * para que los conceptos SEGMENT (ADR-058) tengan dos tramos con precio distinto que
     * ejercitar. Lo hace el loader por API y no una migracion: los empleados son suyos, y
     * un UPDATE escrito en una migracion se ejecuta antes de que exista nadie (backend#74).
     */
    /**
     * Las horas extra de la demo: una parte de la plantilla declara horas en el mes que se calcula
     * (workforce-loader#5, b4rrhh/backend#104).
     *
     * <p>Es la tercera de las tres tablas vacias del issue. Estuvo a cero a proposito hasta que el
     * motor tuvo un concepto {@code EMPLOYEE_INPUT} que las consumiera: sembrar antes habria sido
     * inventar catalogo, que es lo que el issue deja fuera.
     */
    public static class PayrollInput {

        private boolean enabled = true;

        /**
         * El concepto de entrada, el que declara la persona. {@code H01} (HORAS_EXTRA) lo siembra la
         * V133 del backend; lo que se cobra por el es el {@code 102}, que se calcula solo.
         *
         * <p>Si un dia se quita del catalogo, el backend contesta y la corrida lo cuenta: una entrada
         * con un codigo que nadie consume no rompe nada, y por eso <b>esto no lo puede comprobar el
         * loader</b>. Lo que lo sujeta es el propio recibo.
         */
        @NotBlank
        private String conceptCode = "H01";

        /**
         * Que parte de la plantilla las declara. Un plus que cobran los mil no ensena nada; lo que
         * hace que se lea como una plantilla real es que unos lo tengan y otros no.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double rate = 0.25;

        /** Horas de menos, en horas enteras: una tarde larga. */
        @Min(1)
        private int minHours = 4;

        /** Y de mas. Veinte horas es un mes cargado, no un mes imposible. */
        @Min(1)
        private int maxHours = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getConceptCode() {
            return conceptCode;
        }

        public void setConceptCode(String conceptCode) {
            this.conceptCode = conceptCode;
        }

        public double getRate() {
            return rate;
        }

        public void setRate(double rate) {
            this.rate = rate;
        }

        public int getMinHours() {
            return minHours;
        }

        public void setMinHours(int minHours) {
            this.minHours = minHours;
        }

        public int getMaxHours() {
            return maxHours;
        }

        public void setMaxHours(int maxHours) {
            this.maxHours = maxHours;
        }
    }

    public static class WorkingTimeChange {

        private boolean enabled = true;

        /**
         * Dia en que arranca la jornada nueva, no el ultimo de la anterior: el backend cierra
         * la ventana en vigor el dia de antes (ADR-057).
         *
         * <p><b>Puede faltar</b>, y lo normal es que falte: si falta sale del periodo, el dia 16
         * ({@code workforce-loader#12}). Se puede poner para cortar otro dia, y entonces manda
         * lo puesto — pero tiene que caer dentro del periodo, y si no cae el loader se para en
         * vez de sembrar un mes sin tramos.
         */
        private LocalDate date;

        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("100.0")
        private BigDecimal percentage = new BigDecimal("50");

        /** Cuantos empleados lo llevan. Con uno ya se ve; con varios sobrevive a un cese. */
        @Min(1)
        private int employees = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public BigDecimal getPercentage() {
            return percentage;
        }

        public void setPercentage(BigDecimal percentage) {
            this.percentage = percentage;
        }

        public int getEmployees() {
            return employees;
        }

        public void setEmployees(int employees) {
            this.employees = employees;
        }
    }

    public static class Filters {

        private String agreementCode;

        private List<String> agreementCategoryCodes = new ArrayList<>();

        private boolean numericContractTypesOnly = false;

        private boolean requireContractSubtype = true;

        public String getAgreementCode() {
            return agreementCode;
        }

        public void setAgreementCode(String agreementCode) {
            this.agreementCode = agreementCode;
        }

        public List<String> getAgreementCategoryCodes() {
            return agreementCategoryCodes;
        }

        public void setAgreementCategoryCodes(List<String> agreementCategoryCodes) {
            this.agreementCategoryCodes = agreementCategoryCodes;
        }

        public boolean isNumericContractTypesOnly() {
            return numericContractTypesOnly;
        }

        public void setNumericContractTypesOnly(boolean numericContractTypesOnly) {
            this.numericContractTypesOnly = numericContractTypesOnly;
        }

        public boolean isRequireContractSubtype() {
            return requireContractSubtype;
        }

        public void setRequireContractSubtype(boolean requireContractSubtype) {
            this.requireContractSubtype = requireContractSubtype;
        }
    }

    public static class Simulation {

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double terminateRate = 0.30;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double rehireRateOfTerminated = 0.40;

        @Min(1)
        private int terminationMinDaysAfterHire = 30;

        @Min(1)
        private int terminationMaxDaysAfterHire = 180;

        @Min(1)
        private int rehireMinDaysAfterTermination = 15;

        @Min(1)
        private int rehireMaxDaysAfterTermination = 120;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double workCenterChangeRate = 0.35;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double contractReplaceRate = 0.25;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double laborClassificationReplaceRate = 0.25;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double costCenterReplaceRate = 0.20;

        /**
         * Que parte de la plantilla va en el regimen de pagas extras CONTRARIO al del convenio
         * ({@code workforce-loader#13}).
         *
         * <p>Uno de cada cuatro, y no la mitad: lo que la demo ensena es que la base no sabe si se
         * pago, y para eso hace falta una minoria visible enfrente de la mayoria, no dos mitades
         * que no dejan ver cual es el caso normal.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double extrasProrrateadasRate = 0.25;

        /**
         * Que parte de las bajas por enfermedad comun se siembra SIN derecho a prestacion
         * ({@code workforce-loader#14}).
         *
         * <p>Uno de cada veinte, y el numero es lo que se quiere ensenar: <b>sin derecho es la
         * excepcion</b>. La carencia -180 dias cotizados en cinco anos, art. 172.a) de la LGSS- la
         * cumple casi todo el mundo, asi que una demo con la mitad de las bajas sin derecho mentiria
         * sobre cual es el caso normal.
         *
         * <p>Y hacen falta las dos, no solo la mayoria: una baja sin derecho quita dias, no paga nada
         * y no cotiza, y eso es un recibo que se lee distinto. Con cero casos no habria nada que
         * ensenar de la mitad de la regla.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double sinDerechoAPrestacionRate = 0.05;

        public double getSinDerechoAPrestacionRate() {
            return sinDerechoAPrestacionRate;
        }

        public void setSinDerechoAPrestacionRate(double sinDerechoAPrestacionRate) {
            this.sinDerechoAPrestacionRate = sinDerechoAPrestacionRate;
        }

        public double getExtrasProrrateadasRate() {
            return extrasProrrateadasRate;
        }

        public void setExtrasProrrateadasRate(double extrasProrrateadasRate) {
            this.extrasProrrateadasRate = extrasProrrateadasRate;
        }

        public double getTerminateRate() {
            return terminateRate;
        }

        public void setTerminateRate(double terminateRate) {
            this.terminateRate = terminateRate;
        }

        public double getRehireRateOfTerminated() {
            return rehireRateOfTerminated;
        }

        public void setRehireRateOfTerminated(double rehireRateOfTerminated) {
            this.rehireRateOfTerminated = rehireRateOfTerminated;
        }

        public int getTerminationMinDaysAfterHire() {
            return terminationMinDaysAfterHire;
        }

        public void setTerminationMinDaysAfterHire(int terminationMinDaysAfterHire) {
            this.terminationMinDaysAfterHire = terminationMinDaysAfterHire;
        }

        public int getTerminationMaxDaysAfterHire() {
            return terminationMaxDaysAfterHire;
        }

        public void setTerminationMaxDaysAfterHire(int terminationMaxDaysAfterHire) {
            this.terminationMaxDaysAfterHire = terminationMaxDaysAfterHire;
        }

        public int getRehireMinDaysAfterTermination() {
            return rehireMinDaysAfterTermination;
        }

        public void setRehireMinDaysAfterTermination(int rehireMinDaysAfterTermination) {
            this.rehireMinDaysAfterTermination = rehireMinDaysAfterTermination;
        }

        public int getRehireMaxDaysAfterTermination() {
            return rehireMaxDaysAfterTermination;
        }

        public void setRehireMaxDaysAfterTermination(int rehireMaxDaysAfterTermination) {
            this.rehireMaxDaysAfterTermination = rehireMaxDaysAfterTermination;
        }

        public double getWorkCenterChangeRate() {
            return workCenterChangeRate;
        }

        public void setWorkCenterChangeRate(double workCenterChangeRate) {
            this.workCenterChangeRate = workCenterChangeRate;
        }

        public double getContractReplaceRate() {
            return contractReplaceRate;
        }

        public void setContractReplaceRate(double contractReplaceRate) {
            this.contractReplaceRate = contractReplaceRate;
        }

        public double getLaborClassificationReplaceRate() {
            return laborClassificationReplaceRate;
        }

        public void setLaborClassificationReplaceRate(double laborClassificationReplaceRate) {
            this.laborClassificationReplaceRate = laborClassificationReplaceRate;
        }

        public double getCostCenterReplaceRate() {
            return costCenterReplaceRate;
        }

        public void setCostCenterReplaceRate(double costCenterReplaceRate) {
            this.costCenterReplaceRate = costCenterReplaceRate;
        }
    }

    /**
     * El ciclo de nomina que la semilla <b>ejecuta</b> ({@code workforce-loader#16}).
     *
     * <p>Hasta aqui el loader sembraba datos y alguien calculaba un mes a mano. Eso da una foto: mil
     * empleados y un recibo cada uno. Lo que no da es <b>tiempo</b>, y sin tiempo no hay nada que
     * ensenar del paso 6: la base reguladora lee un mes anterior que no existe, el pago delegado
     * necesita bajas que crucen meses, y un atraso necesita un mes <b>entregado</b> al que volver.
     *
     * <p>Asi que el loader corre el ciclo de {@code CICLO.md}: para cada mes, calcular, cerrar en
     * masa, y meter despues del cierre las correcciones a pasado que llegan en la operativa real.
     * El ultimo mes se queda abierto, calculado, con sus atrasos dentro.
     */
    public static class Cycle {

        /**
         * Si el loader corre el ciclo despues de sembrar.
         *
         * <p>Apagado deja el loader como estaba antes del {@code #16}: siembra y no calcula nada.
         * Sirve para sembrar contra un backend que todavia no sabe calcular, y para separar los dos
         * fallos cuando algo va mal.
         */
        private boolean enabled = true;

        /**
         * El primer mes que se calcula y se cierra, en {@code yyyyMM}.
         *
         * <p>De aqui sale la ventana de contratacion: ninguna alta cae dentro de un mes que se
         * calcula, que es la propiedad que ya se queria cuando solo habia un mes
         * ({@code workforce-loader#11}). El ultimo mes es {@code loader.period}.
         *
         * <p>Empieza en enero y no en diciembre a proposito: cruzar el ano mete la retro entre
         * ejercicios —IRPF de anos anteriores, liquidacion distinta— y eso es otro paso.
         */
        private Integer fromPeriod;

        /**
         * Hasta donde atras se le permite recalcular a cada lanzamiento, en meses.
         *
         * <p><b>Tres y no doce</b>, y el numero es una decision de la semilla y no una recomendacion:
         * doce es lo que propondria un formulario a una empresa ({@code b4rrhh/frontend#85}), pero
         * con nueve meses de historia un limite de doce no deja NINGUNA marca fuera, y entonces el
         * aviso del {@code b4rrhh/backend#132} no existe en la demo. Con tres, las dos correcciones
         * normales —al mes que se acaba de cerrar y al anterior— entran, y la profunda no. Es el
         * valor mas alto que sigue dejando ver las dos caras.
         */
        @Min(1)
        private int retroLimitMonthsBack = 3;

        /**
         * De cada cuantos empleados, uno declara horas extra <b>al mes que se acaba de cerrar</b>.
         *
         * <p>Uno de cada veinte. Es la correccion mas comun de una nomina real: las horas de
         * septiembre las cuenta el encargado a primeros de octubre, cuando septiembre ya se pago.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double horasAlMesCerradoRate = 0.05;

        /**
         * De cada cuantos, uno declara una ausencia olvidada <b>al mes anterior al cerrado</b>.
         *
         * <p>Uno de cada cincuenta, y mas profunda que las horas a proposito: asi el recibo del mes
         * abierto lleva atrasos de dos origenes distintos, que es el caso que el {@code deploy#22}
         * pide pegado entero.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double ausenciaAlMesAnteriorRate = 0.02;

        /**
         * Cuantos empleados reciben, cada mes, una correccion <b>mas antigua que el limite</b>.
         *
         * <p>Un punado —tres— y no una fraccion: lo que se ensena es que el caso existe y que el
         * recibo lo dice, no cuanto pesa. Cada una deja un aviso en el recibo del mes abierto y una
         * marca que NO se consume, asi que aparece en la checklist y alguien tiene que decidir
         * ({@code b4rrhh/backend#132}).
         */
        @Min(0)
        private int fueraDelLimiteEmployees = 3;

        /**
         * Cuantos meses atras va esa correccion profunda.
         *
         * <p>Cinco, que con el limite en tres queda fuera con margen. Solo se escribe cuando el mes
         * de destino ya se ha cerrado en este mismo ciclo: escribirla sobre un mes sin recibo
         * entregado no dejaria marca ninguna —el puerto del {@code b4rrhh/backend#130} solo marca lo
         * que toca un mes entregado— y el aviso no existiria, con la corrida en verde.
         */
        @Min(1)
        private int fueraDelLimiteMesesAtras = 5;

        /**
         * Cuantos empleados, cada mes, <b>pierden</b> unas horas que ya se les pagaron
         * ({@code b4rrhh/backend#137}).
         *
         * <p>Las demas correcciones solo anaden, y un atraso que devuelve dinero por un concepto que
         * ya no esta en el vigente es un camino distinto: el que revento en la demo del 162 sin que
         * la semilla lo pudiera ver. Se borran las horas que la correccion de horas escribio al mes
         * anterior, que estan pagadas seguro —como atraso en el recibo del mes que se acaba de
         * cerrar—. Un punado, como la correccion profunda: lo que se ensena es que el caso existe.
         */
        @Min(0)
        private int horasQuitadasEmployees = 3;

        /** Cada cuanto se pregunta si la ejecucion ha terminado, en segundos. */
        @Min(1)
        private int pollSeconds = 10;

        /**
         * Cuanto se espera como mucho a que termine una ejecucion, en minutos.
         *
         * <p>Una corrida de la plantilla entera tarda unos tres minutos; con retro para todos, mas.
         * El tope existe para que una ejecucion muerta no deje el loader esperando toda la noche, y
         * cuando salta el loader <b>para</b>: seguir con el mes siguiente sobre un mes que no se
         * calculo daria una semilla que parece entera y no lo es.
         */
        @Min(1)
        private int runTimeoutMinutes = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getFromPeriod() {
            return fromPeriod;
        }

        public void setFromPeriod(Integer fromPeriod) {
            this.fromPeriod = fromPeriod;
        }

        public int getRetroLimitMonthsBack() {
            return retroLimitMonthsBack;
        }

        public void setRetroLimitMonthsBack(int retroLimitMonthsBack) {
            this.retroLimitMonthsBack = retroLimitMonthsBack;
        }

        public double getHorasAlMesCerradoRate() {
            return horasAlMesCerradoRate;
        }

        public void setHorasAlMesCerradoRate(double horasAlMesCerradoRate) {
            this.horasAlMesCerradoRate = horasAlMesCerradoRate;
        }

        public double getAusenciaAlMesAnteriorRate() {
            return ausenciaAlMesAnteriorRate;
        }

        public void setAusenciaAlMesAnteriorRate(double ausenciaAlMesAnteriorRate) {
            this.ausenciaAlMesAnteriorRate = ausenciaAlMesAnteriorRate;
        }

        public int getFueraDelLimiteEmployees() {
            return fueraDelLimiteEmployees;
        }

        public void setFueraDelLimiteEmployees(int fueraDelLimiteEmployees) {
            this.fueraDelLimiteEmployees = fueraDelLimiteEmployees;
        }

        public int getHorasQuitadasEmployees() {
            return horasQuitadasEmployees;
        }

        public void setHorasQuitadasEmployees(int horasQuitadasEmployees) {
            this.horasQuitadasEmployees = horasQuitadasEmployees;
        }

        public int getFueraDelLimiteMesesAtras() {
            return fueraDelLimiteMesesAtras;
        }

        public void setFueraDelLimiteMesesAtras(int fueraDelLimiteMesesAtras) {
            this.fueraDelLimiteMesesAtras = fueraDelLimiteMesesAtras;
        }

        public int getPollSeconds() {
            return pollSeconds;
        }

        public void setPollSeconds(int pollSeconds) {
            this.pollSeconds = pollSeconds;
        }

        public int getRunTimeoutMinutes() {
            return runTimeoutMinutes;
        }

        public void setRunTimeoutMinutes(int runTimeoutMinutes) {
            this.runTimeoutMinutes = runTimeoutMinutes;
        }
    }
}
