package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.domain.model.SyntheticEmployee;
import com.b4rrhh.workforceloader.domain.model.SyntheticPersonalData;
import com.b4rrhh.workforceloader.infrastructure.api.CatalogApiClient;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CatalogOption;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La semilla no depende del orden en que el backend sirve el catálogo (workforce-loader#18).
 *
 * <p>El loader elige por posición, y el orden lo ponía la collation de Postgres: el mismo loader
 * contra un Postgres con otro locale sembraba otra plantilla. Éste es el test que antes no se podía
 * escribir: el mismo catálogo servido en su orden y servido al revés da exactamente el mismo
 * escenario, evento a evento. Los códigos tienen guion bajo y mayúsculas a propósito, que es donde
 * {@code en_US} y Java no se ponen de acuerdo.</p>
 *
 * <p>Si se quita {@link CatalogOrder#byCode} de una de las entradas del catálogo, este test se pone
 * rojo. {@code EveryCatalogListIsOrderedByCodeTest} vigila que no falte en ninguna.</p>
 */
class TheSeedDoesNotDependOnTheOrderOfTheCatalogTest {

    private static final HireWindow VENTANA = new HireWindow(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 5, 1));
    private static final List<LocalDate> CORTES = List.of(LocalDate.of(2026, 9, 16));

    @Test
    void theSameCatalogServedBackwardsGivesTheSameScenarios() {
        List<SyntheticEmployee> plantilla = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            plantilla.add(employee(String.format("EMP%06d", i), LocalDate.of(2024, 1, 1).plusDays(i * 9L)));
        }

        List<EmployeeLifecycleScenario> enSuOrden = generatorOver(new ServedCatalog(false)).generate(plantilla, VENTANA, CORTES);
        List<EmployeeLifecycleScenario> alReves = generatorOver(new ServedCatalog(true)).generate(plantilla, VENTANA, CORTES);

        assertThat(alReves).isEqualTo(enSuOrden);
        assertThat(enSuOrden.stream().flatMap(scenario -> scenario.events().stream()).count())
                .as("y hay escenario de verdad, con altas, ceses y cambios, o el test no probaria nada")
                .isGreaterThan(plantilla.size() * 2L);
    }

    private static EmployeeLifecycleScenarioGenerator generatorOver(CatalogApiClient catalog) {
        LoaderProperties properties = properties();
        HireReferenceDataResolver resolver = new HireReferenceDataResolver(catalog, properties);
        return new EmployeeLifecycleScenarioGenerator(
                properties,
                resolver,
                new WorkCenterMutationGenerator(resolver),
                new ContractMutationGenerator(),
                new LaborClassificationMutationGenerator(),
                new CostCenterMutationGenerator(catalog, properties),
                new AbsenceScenarioGenerator(),
                new PayrollInputScenarioGenerator(),
                new ExtraPaymentRegimeScenarioGenerator(),
                (ruleSystemCode, agreementCode) -> false
        );
    }

    private static LoaderProperties properties() {
        LoaderProperties properties = new LoaderProperties();
        properties.getDefaults().setRuleSystemCode("ESP");
        properties.getDefaults().setEmployeeTypeCode("INTERNAL");
        properties.getGeneration().setSeed(12345);
        properties.getGeneration().setHireDateFrom(LocalDate.of(2024, 1, 1));
        properties.getGeneration().setHireDateTo(LocalDate.of(2026, 5, 1));
        properties.getSimulation().setTerminateRate(0.6);
        properties.getSimulation().setRehireRateOfTerminated(0.8);
        properties.getSimulation().setWorkCenterChangeRate(0.35);
        properties.getSimulation().setContractReplaceRate(0.25);
        properties.getSimulation().setLaborClassificationReplaceRate(0.25);
        properties.getSimulation().setCostCenterReplaceRate(0.25);
        properties.getWorkingTimeChange().setDate(LocalDate.of(2026, 9, 16));
        properties.getWorkingTimeChange().setPercentage(new BigDecimal("50"));
        return properties;
    }

    private static SyntheticEmployee employee(String number, LocalDate hireDate) {
        return new SyntheticEmployee("ESP", "INTERNAL", number, SyntheticEmployee.PersonName.of("Lucia", "Suarez", "Diaz"),
                hireDate, new BigDecimal("100"), SyntheticPersonalData.none());
    }

    /** El catálogo de la demo en pequeño, servido en su orden o al revés. Sin red. */
    private static final class ServedCatalog extends CatalogApiClient {

        private static final Map<String, List<CatalogOption>> BY_FIELD = Map.of(
                "companyCode", options("ES01", "ES02", "ES_03"),
                "workCenterCode", options("BRANCH_EAST", "BRANCH_NORTH", "BRANCHA", "MAIN_OFFICE"),
                "entryReasonCode", options("HIRING", "HIRING_TRANSFER"),
                "exitReasonCode", options("DISMISSAL", "RETIREMENT", "VOLUNTARY_LEAVE", "VOLUNTARYLEAVE"),
                "agreementCode", options("99002405011982", "99002405_B"),
                "contractTypeCode", options("100", "189", "401", "502"),
                "absenceTypeCode", options("IT_COMMON", "IT_WORK", "ITX", "VACATION"),
                "costCenterCode", options("CC_ADMIN", "CC_HR", "CC_IT", "CCX"));

        private final boolean backwards;

        private ServedCatalog(boolean backwards) {
            super(new LoaderProperties(), WebClient.builder());
            this.backwards = backwards;
        }

        private List<CatalogOption> served(List<CatalogOption> options) {
            List<CatalogOption> copy = new ArrayList<>(options);
            if (backwards) {
                Collections.reverse(copy);
            }
            return copy;
        }

        @Override
        public List<CatalogOption> getDirectOptionsForField(
                String ruleSystemCode, String resourceCode, String fieldCode, String... fallbackEntityTypeCodes) {
            List<CatalogOption> options = BY_FIELD.get(fieldCode);
            if (options == null) {
                throw new IllegalStateException("sin opciones para " + fieldCode);
            }
            return served(options);
        }

        @Override
        public List<CatalogOption> getAgreementCategories(String ruleSystemCode, String agreementCode) {
            return served(options(agreementCode + "-G1", agreementCode + "-G2", agreementCode + "_G3"));
        }

        @Override
        public List<CatalogOption> getAgreementCategories(String ruleSystemCode, String agreementCode, LocalDate referenceDate) {
            return getAgreementCategories(ruleSystemCode, agreementCode);
        }

        @Override
        public List<CatalogOption> getContractSubtypes(String ruleSystemCode, String contractTypeCode) {
            return served(options("01", "02", "0_3"));
        }

        @Override
        public List<CatalogOption> getContractSubtypes(String ruleSystemCode, String contractTypeCode, LocalDate referenceDate) {
            return getContractSubtypes(ruleSystemCode, contractTypeCode);
        }

        @Override
        public List<CatalogOption> getWorkCentersByCompany(String ruleSystemCode, String companyCode, LocalDate referenceDate) {
            return served(options("BRANCH_EAST", "BRANCH_NORTH", "BRANCHA"));
        }

        private static List<CatalogOption> options(String... codes) {
            List<CatalogOption> options = new ArrayList<>();
            for (String code : codes) {
                options.add(new CatalogOption(code, code));
            }
            return options;
        }
    }
}
