package com.b4rrhh.workforceloader.infrastructure.generator;

import com.b4rrhh.workforceloader.domain.model.SyntheticEmployee;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    // workforce-loader#3: con 8 apellidos, buscar «Sanchez» devolvía uno de cada siete empleados.
    @Test
    void surnamesFollowASkewedDistributionAndTheWholeListGetsUsed() {
        List<SyntheticEmployee> employees = new SyntheticEmployeeGenerator(properties(310)).generateEmployees();

        Map<String, Long> byFirstSurname = employees.stream()
                .collect(Collectors.groupingBy(SyntheticEmployee::lastName1, Collectors.counting()));
        String mostFrequent = Collections.max(byFirstSurname.entrySet(), Map.Entry.comparingByValue()).getKey();
        long hitsInEitherSurname = employees.stream()
                .filter(employee -> mostFrequent.equals(employee.lastName1()) || mostFrequent.equals(employee.lastName2()))
                .count();

        // La forma, no el número: ningún apellido pasa del 8 % como primer apellido (antes, el 12,5 %
        // cada uno), y buscar el más frecuente devuelve del orden de una decena, no una de cada siete.
        assertThat(byFirstSurname.get(mostFrequent)).isLessThanOrEqualTo(310L * 8 / 100);
        assertThat(hitsInEitherSurname).isLessThanOrEqualTo(310L * 10 / 100);

        // Y la cola entera se usa: el segundo apellido sale del mismo reparto que el primero.
        Set<String> used = new HashSet<>(byFirstSurname.keySet());
        employees.stream().map(SyntheticEmployee::lastName2).filter(Objects::nonNull).forEach(used::add);
        assertThat(used).containsExactlyInAnyOrderElementsOf(SyntheticEmployeeGenerator.lastNames());
    }

    @Test
    void theNameValueRefusesTheFirstNameAsNickname() {
        assertThatThrownBy(() -> new SyntheticEmployee.PersonName("Ana", "Garcia", null, "Ana"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
