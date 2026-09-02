package com.b4rrhh.workforceloader.infrastructure.generator;

import com.b4rrhh.workforceloader.domain.model.SyntheticEmployee;
import com.b4rrhh.workforceloader.domain.model.SyntheticPersonalData;
import com.b4rrhh.workforceloader.domain.model.SyntheticPersonalData.Address;
import com.b4rrhh.workforceloader.domain.model.SyntheticPersonalData.Contact;
import com.b4rrhh.workforceloader.domain.model.SyntheticPersonalData.Identifier;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * workforce-loader#3: la mitad «Persona» de la ficha estaba vacía en los 310 empleados. Se afirma
 * sobre la plantilla real que genera el loader, no sobre un empleado suelto, porque lo que importa
 * es la forma del conjunto: que haya huecos, que no todos lleven lo mismo y que nada parezca real.
 */
class PersonalDataGeneratorTest {

    private static final String DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";

    private static List<SyntheticEmployee> plantilla() {
        LoaderProperties properties = new LoaderProperties();
        properties.getGeneration().setCount(310);
        properties.getGeneration().setSeed(12345L);
        properties.getGeneration().setWorkingTimePercentage(new BigDecimal("100"));
        properties.getDefaults().setRuleSystemCode("esp");
        properties.getDefaults().setEmployeeTypeCode("internal");
        return new SyntheticEmployeeGenerator(properties).generateEmployees();
    }

    @Test
    void everyoneHasAnAddressAContactAndAnIdentifierButNotTheSameOnes() {
        List<SyntheticEmployee> employees = plantilla();

        assertThat(employees).allSatisfy(employee -> {
            SyntheticPersonalData data = employee.personalData();
            assertThat(data.addresses()).isNotEmpty();
            assertThat(data.addresses()).anyMatch(address -> "HOME".equals(address.addressTypeCode()) && address.endDate() == null);
            assertThat(data.contacts()).isNotEmpty();
            assertThat(data.identifiers()).isNotEmpty();
        });

        // No todos los mismos: la combinación de tipos que lleva cada uno varía de verdad.
        Set<String> shapes = employees.stream()
                .map(employee -> typeCodes(employee.personalData().addresses(), Address::addressTypeCode)
                        + "|" + typeCodes(employee.personalData().contacts(), Contact::contactTypeCode)
                        + "|" + typeCodes(employee.personalData().identifiers(), Identifier::identifierTypeCode))
                .collect(Collectors.toSet());
        assertThat(shapes).hasSizeGreaterThan(20);
    }

    @Test
    void theGapsArePresentOnPurpose() {
        List<SyntheticEmployee> employees = plantilla();

        assertThat(employees).anyMatch(withoutContact("MOBILE"));
        assertThat(employees).anyMatch(withoutContact("EMAIL"));
        assertThat(employees).anyMatch(withoutIdentifier("PASSPORT"));
        assertThat(employees).anyMatch(withoutIdentifier("SOCIAL_SECURITY"));
        assertThat(employees).anyMatch(withoutIdentifier("NATIONAL_ID"));
        assertThat(employees).anyMatch(employee -> employee.personalData().addresses().size() > 1);
        assertThat(employees).anyMatch(employee -> employee.personalData().addresses().stream()
                .anyMatch(address -> !"ESP".equals(address.countryCode())));
        // Pero las proporciones son las declaradas, no un reparto a partes iguales.
        long withMobile = employees.stream().filter(Predicate.not(withoutContact("MOBILE"))).count();
        assertThat(withMobile * 100 / employees.size()).isBetween(PersonalDataGenerator.MOBILE_PERCENT - 10L, PersonalDataGenerator.MOBILE_PERCENT + 10L);
    }

    @Test
    void identifiersAreStructurallyValidAndRecognizablySynthetic() {
        List<SyntheticEmployee> employees = plantilla();

        for (SyntheticEmployee employee : employees) {
            List<Identifier> identifiers = employee.personalData().identifiers();
            assertThat(identifiers).extracting(Identifier::identifierTypeCode).doesNotHaveDuplicates();
            assertThat(identifiers).filteredOn(Identifier::primary).hasSize(1);

            for (Identifier identifier : identifiers) {
                switch (identifier.identifierTypeCode()) {
                    case "NATIONAL_ID" -> {
                        assertThat(identifier.issuingCountryCode()).isEqualTo("ESP");
                        assertThat(identifier.identifierValue()).matches("^0000\\d{4}[A-Z]$");
                        int number = Integer.parseInt(identifier.identifierValue().substring(0, 8));
                        assertThat(identifier.identifierValue().charAt(8)).isEqualTo(DNI_LETTERS.charAt(number % 23));
                    }
                    case "PASSPORT" -> assertThat(identifier.identifierValue()).matches("^B4R\\d{6}$");
                    case "SOCIAL_SECURITY" -> {
                        assertThat(identifier.identifierValue()).matches("^\\d{12}$");
                        long body = Long.parseLong(identifier.identifierValue().substring(0, 10));
                        assertThat(Integer.parseInt(identifier.identifierValue().substring(10))).isEqualTo((int) (body % 97));
                    }
                    default -> throw new AssertionError("unexpected identifier type " + identifier.identifierTypeCode());
                }
            }
        }
        // Un extranjero sin DNI lleva pasaporte de su país como identificador principal.
        assertThat(employees).anyMatch(employee -> employee.personalData().identifiers().stream()
                .anyMatch(identifier -> "PASSPORT".equals(identifier.identifierTypeCode())
                        && identifier.primary() && !"ESP".equals(identifier.issuingCountryCode())));
    }

    @Test
    void contactsUseFictionalRangesAndAReservedDomain() {
        List<SyntheticEmployee> employees = plantilla();
        Set<String> emails = new HashSet<>();

        for (SyntheticEmployee employee : employees) {
            List<Contact> contacts = employee.personalData().contacts();
            assertThat(contacts).extracting(Contact::contactTypeCode).doesNotHaveDuplicates();
            for (Contact contact : contacts) {
                switch (contact.contactTypeCode()) {
                    case "EMAIL" -> {
                        assertThat(contact.contactValue()).matches("^[a-z0-9.]+@b4rrhh\\.example$");
                        assertThat(emails.add(contact.contactValue())).as("email repetido: %s", contact.contactValue()).isTrue();
                    }
                    case "MOBILE", "COMPANY_MOBILE" -> assertThat(contact.contactValue()).matches("^\\+44 7700 900\\d{3}$");
                    case "PHONE" -> assertThat(contact.contactValue()).matches("^\\+44 20 7946 0\\d{3}$");
                    case "EXTENSION" -> assertThat(contact.contactValue()).matches("^\\d{4}$");
                    default -> throw new AssertionError("unexpected contact type " + contact.contactTypeCode());
                }
            }
        }
    }

    @Test
    void addressesStartAtHireAndNeverOverlapWithinAType() {
        List<SyntheticEmployee> employees = plantilla();

        for (SyntheticEmployee employee : employees) {
            List<Address> addresses = employee.personalData().addresses();
            assertThat(addresses).allSatisfy(address -> {
                assertThat(address.street()).isNotBlank();
                assertThat(address.city()).isNotBlank();
                assertThat(address.postalCode()).isNotBlank();
                assertThat(address.regionCode()).isNotBlank();
                assertThat(address.startDate()).isAfterOrEqualTo(employee.hireDate());
                if (address.endDate() != null) {
                    assertThat(address.endDate()).isAfter(address.startDate());
                }
            });
            List<Address> homes = addresses.stream().filter(address -> "HOME".equals(address.addressTypeCode())).toList();
            for (int i = 1; i < homes.size(); i++) {
                assertThat(homes.get(i - 1).endDate()).isNotNull().isBefore(homes.get(i).startDate());
            }
        }
    }

    @Test
    void syntheticDniAndSocialSecurityNumberCarryTheirCheckDigits() {
        assertThat(PersonalDataGenerator.syntheticDni(42)).isEqualTo("00000042" + DNI_LETTERS.charAt(42 % 23));
        assertThat(PersonalDataGenerator.syntheticSocialSecurityNumber(42))
                .isEqualTo("2800000042" + String.format("%02d", 2800000042L % 97));
    }

    private static <T> String typeCodes(List<T> items, Function<T, String> typeCode) {
        return items.stream().map(typeCode).sorted().collect(Collectors.joining(","));
    }

    private static Predicate<SyntheticEmployee> withoutContact(String contactTypeCode) {
        return employee -> employee.personalData().contacts().stream()
                .noneMatch(contact -> contactTypeCode.equals(contact.contactTypeCode()));
    }

    private static Predicate<SyntheticEmployee> withoutIdentifier(String identifierTypeCode) {
        return employee -> employee.personalData().identifiers().stream()
                .noneMatch(identifier -> identifierTypeCode.equals(identifier.identifierTypeCode()));
    }
}
