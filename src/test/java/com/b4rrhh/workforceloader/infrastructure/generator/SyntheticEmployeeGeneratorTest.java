package com.b4rrhh.workforceloader.infrastructure.generator;

import com.b4rrhh.workforceloader.domain.model.SyntheticEmployee;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticEmployeeGeneratorTest {

    private static LoaderProperties properties(int count) {
        LoaderProperties properties = new LoaderProperties();
        properties.getGeneration().setCount(count);
        properties.getGeneration().setSeed(12345L);
        properties.getGeneration().setWorkingTimePercentage(new BigDecimal("100"));
        properties.getDefaults().setRuleSystemCode("esp");
        properties.getDefaults().setEmployeeTypeCode("internal");
        return properties;
    }

    // workforce-loader#2: el apodo era el nombre de pila en los 310, y el directorio perdía los apellidos.
    @Test
    void nobodyGetsTheirFirstNameAsPreferredName() {
        List<SyntheticEmployee> employees = new SyntheticEmployeeGenerator(properties(310)).generateEmployees();

        assertThat(employees).hasSize(310);
        assertThat(employees)
                .allSatisfy(employee -> assertThat(employee.lastName1()).isNotBlank())
                .noneMatch(employee -> employee.firstName().equalsIgnoreCase(employee.preferredName()));
    }

    @Test
    void aFewGetARealNicknameSoThePreferredNameIsExercised() {
        List<SyntheticEmployee> employees = new SyntheticEmployeeGenerator(properties(310)).generateEmployees();

        List<SyntheticEmployee> nicknamed = employees.stream()
                .filter(employee -> employee.preferredName() != null)
                .toList();

        // Alrededor del 5 %: ni ninguno (no se ejercita) ni todos (no se distingue).
        assertThat(nicknamed).hasSizeBetween(3, 40);
        assertThat(nicknamed).allSatisfy(employee ->
                assertThat(employee.preferredName()).isNotEqualToIgnoringCase(employee.firstName()));
    }

    @Test
    void theNameValueRefusesTheFirstNameAsNickname() {
        assertThatThrownBy(() -> new SyntheticEmployee.PersonName("Ana", "Garcia", null, "Ana"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
