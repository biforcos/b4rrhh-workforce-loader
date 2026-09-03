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
     * Quienes piden que se les muestre distinto, y cómo. El {@code preferredName} del backend no
     * es un hipocorístico: sustituye al nombre <em>entero</em> en el directorio, gana sobre el
     * formato normativo y sale sin apellidos (backend#42). Un «Paco» suelto entre «Juan Antonio
     * Biforcos Amor» y «Marta Fernandez Lopez» se lee como un dato que falta, así que lo que se
     * siembra es la forma corta más el primer apellido: «Paco Martinez», «Mamen Ruiz». Y sobre
     * todo el caso más común en la vida real, el nombre compuesto del que sólo se usa una parte:
     * «Juan Antonio Biforcos Amor» -> «Juan Biforcos» (workforce-loader#4).
     *
     * <p>Lista y no mapa: el orden de iteración de un {@code Map.of} cambia de una JVM a otra, y
     * la misma semilla tiene que dar la misma plantilla.
     */
    private static final List<Map.Entry<String, String>> SUBSTITUTES = List.of(
            entry("Juan Antonio", "Juan"),
            entry("Jose Antonio", "Antonio"),
            entry("Jose Luis", "Jose"),
            entry("Ana Maria", "Ana"),
            entry("Francisco Javier", "Paco"),
            entry("Maria del Carmen", "Mamen"),
            entry("Maria Isabel", "Maribel"),
            entry("Francisco", "Paco"),
            entry("Jose", "Pepe"),
            entry("Dolores", "Lola"),
            entry("Francisca", "Paqui")
    );

    /**
     * Pedir que te muestren distinto al normativo es cosa de uno o dos de cada cien, no de uno de
     * cada veinte: con 1000 empleados salen unas veinte filas, bastantes para que el caso exista y
     * se pueda mirar, pocas para que el directorio no parezca lleno de datos a medias
     * (workforce-loader#4).
     */
    static final int SUBSTITUTE_PERCENT = 2;

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
        PersonalDataGenerator personalDataGenerator = new PersonalDataGenerator();

        Random random = new Random(generation.getSeed());
        List<SyntheticEmployee> employees = new ArrayList<>(generation.getCount());
        var workingTimePercentage = workingTimePercentageResolver.resolve(generation);

        for (int i = 1; i <= generation.getCount(); i++) {
            String employeeNumber = buildEmployeeNumber(generation, i);
            LocalDate hireDate = randomDateBetween(generation.getHireDateFrom(), generation.getHireDateTo(), random);
            SyntheticEmployee.PersonName name = randomName(random);

            employees.add(new SyntheticEmployee(
                    normalizeCode(defaults.getRuleSystemCode()),
                    normalizeCode(defaults.getEmployeeTypeCode()),
                    employeeNumber,
                    name,
                    hireDate,
                    workingTimePercentage,
                    personalDataGenerator.generate(name, hireDate, i, random)
            ));
        }

        return employees;
    }

    private static SyntheticEmployee.PersonName randomName(Random random) {
        // El segundo apellido sale del mismo reparto que el primero, no de una lista aparte.
        String lastName1 = LAST_NAMES.pick(random);
        String lastName2 = random.nextBoolean() ? LAST_NAMES.pick(random) : null;
        if (random.nextInt(100) < SUBSTITUTE_PERCENT) {
            Map.Entry<String, String> substitute = pick(SUBSTITUTES, random);
            return SyntheticEmployee.PersonName.of(substitute.getKey(), lastName1, lastName2)
                    .withPreferredName(substitute.getValue() + " " + lastName1);
        }
        return SyntheticEmployee.PersonName.of(FIRST_NAMES.pick(random), lastName1, lastName2);
    }

    private static <T> T pick(List<T> source, Random random) {
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
