package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.api.CatalogApiClient;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CatalogOption;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// workforce-loader#5: employee.cost_center estaba vacia porque el reparto salia de una lista en
// application.yml que nadie rellenaba, con el catalogo COST_CENTER sembrado y a un GET de distancia.
class CostCenterMutationGeneratorTest {

    @Test
    void distributionsUseTheCatalogCodesAndAddUpToOneHundred() {
        CostCenterMutationGenerator generator = new CostCenterMutationGenerator(
                catalogWith("CC_ADMIN", "CC_HR", "CC_IT"), properties(true));
        Random random = new Random(7);

        Set<String> usedCodes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            List<SimulationCostCenterAllocation> distribution =
                    generator.generateDistribution(LocalDate.of(2024, 3, 1), random);

            assertThat(distribution).isNotEmpty().hasSizeLessThanOrEqualTo(2);
            assertThat(distribution.stream().mapToInt(SimulationCostCenterAllocation::allocationPercentage).sum())
                    .isEqualTo(100);
            distribution.forEach(item -> usedCodes.add(item.costCenterCode()));
        }

        assertThat(usedCodes).containsExactlyInAnyOrder("CC_ADMIN", "CC_HR", "CC_IT");
    }

    @Test
    void catalogIsFetchedOnceForTheConfiguredRuleSystem() {
        CountingCatalog catalog = catalogWith("CC_ADMIN", "CC_HR");
        CostCenterMutationGenerator generator = new CostCenterMutationGenerator(catalog, properties(true));

        generator.generateDistribution(LocalDate.of(2024, 3, 1), new Random(1));
        generator.generateDistribution(LocalDate.of(2024, 3, 1), new Random(2));

        assertThat(catalog.calls).isEqualTo(1);
        assertThat(catalog.ruleSystemCode).isEqualTo("ESP");
    }

    @Test
    void onlyCentersInForceOnTheEffectiveDateAreUsed() {
        CountingCatalog catalog = new CountingCatalog(List.of(
                new CatalogOption("CC_OLD", "Closed", LocalDate.of(1900, 1, 1), LocalDate.of(2020, 12, 31)),
                new CatalogOption("CC_NEW", "Open", LocalDate.of(2021, 1, 1), null)
        ));
        CostCenterMutationGenerator generator = new CostCenterMutationGenerator(catalog, properties(true));

        for (int i = 0; i < 50; i++) {
            List<SimulationCostCenterAllocation> distribution =
                    generator.generateDistribution(LocalDate.of(2024, 3, 1), new Random(i));
            assertThat(distribution).extracting(SimulationCostCenterAllocation::costCenterCode).containsOnly("CC_NEW");
        }
    }

    @Test
    void nothingIsGeneratedNorFetchedWhenDisabled() {
        CountingCatalog catalog = catalogWith("CC_ADMIN");
        CostCenterMutationGenerator generator = new CostCenterMutationGenerator(catalog, properties(false));

        assertThat(generator.generateDistribution(LocalDate.of(2024, 3, 1), new Random(1))).isEmpty();
        assertThat(catalog.calls).isZero();
    }

    private static LoaderProperties properties(boolean enabled) {
        LoaderProperties properties = new LoaderProperties();
        properties.getBackend().setBaseUrl("http://localhost:1");
        properties.getDefaults().setRuleSystemCode("esp");
        properties.getDefaults().setEmployeeTypeCode("internal");
        properties.getCostCenter().setEnabled(enabled);
        return properties;
    }

    private static CountingCatalog catalogWith(String... codes) {
        return new CountingCatalog(java.util.Arrays.stream(codes)
                .map(code -> new CatalogOption(code, code))
                .toList());
    }

    private static final class CountingCatalog extends CatalogApiClient {

        private final List<CatalogOption> options;
        private int calls;
        private String ruleSystemCode;

        private CountingCatalog(List<CatalogOption> options) {
            super(properties(true), WebClient.builder());
            this.options = options;
        }

        @Override
        public List<CatalogOption> getDirectOptionsForField(
                String ruleSystemCode, String resourceCode, String fieldCode, String... fallbackEntityTypeCodes
        ) {
            calls++;
            this.ruleSystemCode = ruleSystemCode;
            assertThat(resourceCode).isEqualTo("employee.cost_center");
            assertThat(fieldCode).isEqualTo("costCenterCode");
            assertThat(fallbackEntityTypeCodes).containsExactly("COST_CENTER");
            return options;
        }
    }
}
