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
        BigDecimal workingTimePercentage,
        SyntheticPersonalData personalData
) {

    public SyntheticEmployee {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(personalData, "personalData must not be null: use SyntheticPersonalData.none()");
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
                ruleSystemCode, employeeTypeCode, number, name, hireDate, workingTimePercentage, personalData
        );
    }

    public SyntheticEmployee withPersonalData(SyntheticPersonalData data) {
        return new SyntheticEmployee(
                ruleSystemCode, employeeTypeCode, employeeNumber, name, hireDate, workingTimePercentage, data
        );
    }

    /**
     * Nombre de pila, apellidos y, si lo hay, el sustituto del nombre para mostrar.
     *
     * <p>{@code preferredName} no es un hipocorístico que reemplace al nombre de pila: es lo que
     * la persona pide que se enseñe <em>en lugar del nombre entero</em>. En el backend gana sobre
     * el formato normativo de la reglamentación y sale tal cual, sin componer con los apellidos
     * (backend#42). Por eso tiene que poder leerse como un nombre por sí solo —«Paco Martinez»,
     * «Juan Biforcos»—: un «Paco» suelto en el directorio no se lee como una preferencia, sino
     * como un dato que falta (workforce-loader#4). Y nunca el nombre de pila repetido, que es lo
     * que dejó a los 310 sin apellido (workforce-loader#2).
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

        /** Sin sustituto, que es lo normal. */
        public static PersonName of(String firstName, String lastName1, String lastName2) {
            return new PersonName(firstName, lastName1, lastName2, null);
        }

        public PersonName withPreferredName(String preferredName) {
            return new PersonName(firstName, lastName1, lastName2, preferredName);
        }
    }
}
