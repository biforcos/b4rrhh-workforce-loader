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

        @NotNull
        private LocalDate hireDateFrom = LocalDate.now().minusMonths(3);

        @NotNull
        private LocalDate hireDateTo = LocalDate.now();

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
         * El mes, en {@code yyyyMM}. Va escrito y no se saca del reloj, igual que la fecha del mes
         * partido: una entrada en un mes que la demo no calcula no se ve en ninguna pantalla, y la
         * misma semilla tiene que dar lo mismo cualquier dia.
         */
        private Integer period;

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

        public Integer getPeriod() {
            return period;
        }

        public void setPeriod(Integer period) {
            this.period = period;
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
         * la ventana en vigor el dia de antes (ADR-057). Va escrita y no se calcula del reloj,
         * como el resto de la generacion: la misma semilla tiene que dar lo mismo cualquier dia.
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
}
