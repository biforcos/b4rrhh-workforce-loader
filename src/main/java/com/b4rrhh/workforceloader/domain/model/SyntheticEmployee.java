package com.b4rrhh.workforceloader.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Un empleado sintético. El nombre va en su propio valor, {@link PersonName}, con una fábrica
 * sin apodo: un constructor posicional de nueve cadenas donde {@code firstName} y
 * {@code preferredName} van seguidos es donde se cuela pasar el nombre de pila como apodo
 * (workforce-loader#2), y los 310 salen sin apellido en el directorio.
 */
public record SyntheticEmployee(
        String ruleSystemCode,
        String employeeTypeCode,
        String employeeNumber,
        PersonName name,
        LocalDate hireDate,
        BigDecimal workingTimePercentage
) {

    public SyntheticEmployee {
        Objects.requireNonNull(name, "name must not be null");
    }

    public String firstName() {
        return name.firstName();
    }

    public String lastName1() {
        return name.lastName1();
    }

    public String lastName2() {
        return name.lastName2();
    }

    public String preferredName() {
        return name.preferredName();
    }

    public SyntheticEmployee withEmployeeNumber(String number) {
        return new SyntheticEmployee(
                ruleSystemCode, employeeTypeCode, number, name, hireDate, workingTimePercentage
        );
    }

    /**
     * Nombre de pila, apellidos y, si lo hay, el apodo. El apodo es lo que sustituye al nombre de
     * pila —«Paco» por Francisco—, nunca el nombre de pila repetido.
     */
    public record PersonName(
            String firstName,
            String lastName1,
            String lastName2,
            String preferredName
    ) {

        public PersonName {
            if (preferredName != null && preferredName.equalsIgnoreCase(firstName)) {
                throw new IllegalArgumentException(
                        "preferredName must not repeat firstName (" + firstName + "): pass null instead"
                );
            }
        }

        /** Sin apodo, que es lo normal. */
        public static PersonName of(String firstName, String lastName1, String lastName2) {
            return new PersonName(firstName, lastName1, lastName2, null);
        }

        public PersonName withPreferredName(String preferredName) {
            return new PersonName(firstName, lastName1, lastName2, preferredName);
        }
    }
}
