package com.b4rrhh.workforceloader.infrastructure.generator;

import com.b4rrhh.workforceloader.domain.model.SyntheticEmployee;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class SyntheticEmployeeGenerator {

    private static final List<String> FIRST_NAMES = List.of(
            "Ana", "Luis", "Marta", "Carlos", "Elena", "David", "Lucia", "Pablo"
    );

    /**
     * Los nombres con apodo de verdad —«Paco» por Francisco—, que reciben más o menos un 5 % de
     * los empleados: así el nombre preferido lo ejercita alguien, en vez de estar encendido en
     * todos con el nombre de pila repetido (workforce-loader#2).
     */
    private static final Map<String, String> NICKNAMES = Map.of(
            "Francisco", "Paco",
            "Jose", "Pepe",
            "Dolores", "Lola",
            "Francisca", "Paqui"
    );
    private static final List<String> NICKNAMED_FIRST_NAMES = List.copyOf(NICKNAMES.keySet());
    private static final int NICKNAME_PERCENT = 5;

    private static final List<String> LAST_NAMES = List.of(
            "Garcia", "Fernandez", "Lopez", "Sanchez", "Martinez", "Gonzalez", "Ruiz", "Navarro"
    );

    private final LoaderProperties properties;

    public SyntheticEmployeeGenerator(LoaderProperties properties) {
        this.properties = properties;
    }

    public List<SyntheticEmployee> generateEmployees() {
        LoaderProperties.Defaults defaults = properties.getDefaults();
        LoaderProperties.Generation generation = properties.getGeneration();
        WorkingTimePercentageResolver workingTimePercentageResolver = new WorkingTimePercentageResolver();

        Random random = new Random(generation.getSeed());
        List<SyntheticEmployee> employees = new ArrayList<>(generation.getCount());
        var workingTimePercentage = workingTimePercentageResolver.resolve(generation);

        for (int i = 1; i <= generation.getCount(); i++) {
            String employeeNumber = buildEmployeeNumber(generation, i);
            LocalDate hireDate = randomDateBetween(generation.getHireDateFrom(), generation.getHireDateTo(), random);

            employees.add(new SyntheticEmployee(
                    normalizeCode(defaults.getRuleSystemCode()),
                    normalizeCode(defaults.getEmployeeTypeCode()),
                    employeeNumber,
                    randomName(random),
                    hireDate,
                    workingTimePercentage
            ));
        }

        return employees;
    }

    private static SyntheticEmployee.PersonName randomName(Random random) {
        String lastName1 = pick(LAST_NAMES, random);
        String lastName2 = random.nextBoolean() ? pick(LAST_NAMES, random) : null;
        if (random.nextInt(100) < NICKNAME_PERCENT) {
            String firstName = pick(NICKNAMED_FIRST_NAMES, random);
            return SyntheticEmployee.PersonName.of(firstName, lastName1, lastName2)
                    .withPreferredName(NICKNAMES.get(firstName));
        }
        return SyntheticEmployee.PersonName.of(pick(FIRST_NAMES, random), lastName1, lastName2);
    }

    private static String pick(List<String> source, Random random) {
        return source.get(random.nextInt(source.size()));
    }

    private static String buildEmployeeNumber(LoaderProperties.Generation generation, int sequence) {
        String format = "%s%0" + generation.getEmployeeNumberPadding() + "d";
        return String.format(format, normalizeCode(generation.getEmployeeNumberPrefix()), sequence);
    }

    private static LocalDate randomDateBetween(LocalDate from, LocalDate to, Random random) {
        long fromEpochDay = from.toEpochDay();
        long toEpochDay = to.toEpochDay();
        long randomEpochDay = fromEpochDay + random.nextLong(toEpochDay - fromEpochDay + 1);
        return LocalDate.ofEpochDay(randomEpochDay);
    }

    private static String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
