package com.b4rrhh.workforceloader.infrastructure.generator;

import com.b4rrhh.workforceloader.domain.model.SyntheticEmployee;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static java.util.Map.entry;

@Component
public class SyntheticEmployeeGenerator {

    /**
     * Nombres y apellidos con distribución sesgada, no con variedad máxima: en España se repiten
     * García, Fernández, López y Martínez, y una plantilla sin ninguna repetición se ve más falsa,
     * no menos. Sin tildes, como el resto de la semilla, para que buscar «Sanchez» encuentre.
     * Los pesos son una aproximación al padrón; el objetivo es que el apellido más frecuente salga
     * del orden de una decena de veces en 310 y que la cola entera se use (workforce-loader#3).
     */
    private static final WeightedNameList FIRST_NAMES = WeightedNameList.of(
            entry("Maria", 5), entry("Jose", 5), entry("Antonio", 4), entry("Carmen", 4),
            entry("Manuel", 3), entry("Francisco", 3), entry("Ana", 3), entry("David", 3),
            entry("Juan", 3), entry("Laura", 3), entry("Isabel", 3), entry("Javier", 3),
            entry("Carlos", 2), entry("Marta", 2), entry("Cristina", 2), entry("Lucia", 2),
            entry("Elena", 2), entry("Daniel", 2), entry("Miguel", 2), entry("Pablo", 2),
            entry("Sara", 2), entry("Paula", 2), entry("Alejandro", 2), entry("Sergio", 2),
            entry("Jorge", 1), entry("Alberto", 1), entry("Raquel", 1), entry("Rocio", 1),
            entry("Pilar", 1), entry("Beatriz", 1), entry("Nuria", 1), entry("Dolores", 1),
            entry("Francisca", 1), entry("Ruben", 1), entry("Ines", 1), entry("Andres", 1)
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

    private static final WeightedNameList LAST_NAMES = WeightedNameList.of(
            entry("Garcia", 6), entry("Fernandez", 5), entry("Gonzalez", 5), entry("Rodriguez", 5),
            entry("Lopez", 5), entry("Martinez", 5), entry("Sanchez", 5), entry("Perez", 5),
            entry("Gomez", 4), entry("Martin", 4), entry("Jimenez", 4), entry("Ruiz", 4),
            entry("Hernandez", 4), entry("Diaz", 4), entry("Moreno", 4), entry("Alvarez", 4),
            entry("Romero", 3), entry("Alonso", 3), entry("Gutierrez", 3), entry("Navarro", 3),
            entry("Torres", 3), entry("Dominguez", 3), entry("Vazquez", 3), entry("Ramos", 3),
            entry("Gil", 3), entry("Ramirez", 3), entry("Serrano", 3), entry("Blanco", 3),
            entry("Molina", 3), entry("Morales", 3), entry("Suarez", 3), entry("Ortega", 3),
            entry("Delgado", 2), entry("Castro", 2), entry("Ortiz", 2), entry("Rubio", 2),
            entry("Marin", 2), entry("Sanz", 2), entry("Iglesias", 2), entry("Medina", 2),
            entry("Garrido", 2), entry("Cortes", 2), entry("Castillo", 2), entry("Santos", 2),
            entry("Lozano", 2), entry("Guerrero", 2), entry("Cano", 2), entry("Herrera", 2)
    );

    /** Los apellidos de la semilla, para que el test afirme que la cola entera se usa. */
    static List<String> lastNames() {
        return LAST_NAMES.names();
    }

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
        // El segundo apellido sale del mismo reparto que el primero, no de una lista aparte.
        String lastName1 = LAST_NAMES.pick(random);
        String lastName2 = random.nextBoolean() ? LAST_NAMES.pick(random) : null;
        if (random.nextInt(100) < NICKNAME_PERCENT) {
            String firstName = pick(NICKNAMED_FIRST_NAMES, random);
            return SyntheticEmployee.PersonName.of(firstName, lastName1, lastName2)
                    .withPreferredName(NICKNAMES.get(firstName));
        }
        return SyntheticEmployee.PersonName.of(FIRST_NAMES.pick(random), lastName1, lastName2);
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
