package com.b4rrhh.workforceloader.infrastructure.generator;

import com.b4rrhh.workforceloader.domain.model.SyntheticEmployee;
import com.b4rrhh.workforceloader.domain.model.SyntheticPersonalData;
import com.b4rrhh.workforceloader.domain.model.SyntheticPersonalData.Address;
import com.b4rrhh.workforceloader.domain.model.SyntheticPersonalData.Contact;
import com.b4rrhh.workforceloader.domain.model.SyntheticPersonalData.Identifier;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Direcciones, contactos e identificadores de un empleado sintético (workforce-loader#3).
 *
 * <p>Dos reglas mandan aquí. La primera: <b>no todos llevan de todo</b>. Una plantilla en la que
 * los 310 tienen exactamente un email, un móvil y una dirección se ve tan artificial como una
 * vacía, y las pantallas tienen que enfrentarse a huecos porque en producción los va a haber. Las
 * proporciones están en las constantes {@code *_PERCENT}, con el porqué de cada hueco al lado. Lo
 * único garantizado es lo que pide el issue: una dirección, al menos un contacto y al menos un
 * identificador por empleado.
 *
 * <p>La segunda: los datos son <b>estructuralmente válidos pero visiblemente sintéticos</b>. Un
 * dato de demo que pueda confundirse con uno real es un problema, no un acierto. Los DNI van con
 * su letra pero en la numeración de 1951 ({@code 00000042X}); los pasaportes llevan el prefijo
 * {@code B4R}; la Seguridad Social calcula sus dígitos de control sobre un número relleno de
 * ceros; los teléfonos salen de los rangos que Ofcom reserva para ficción —España no reserva
 * ninguno— y el correo usa {@code b4rrhh.example}, un dominio que la RFC 2606 garantiza que no
 * existe.
 */
final class PersonalDataGenerator {

    // ---- Huecos a propósito, en porcentaje de empleados ----

    /** Sin email quien no lo ha dado en el alta; se fuerza si tampoco hay móvil, para no dejar a nadie sin contacto. */
    static final int EMAIL_PERCENT = 85;
    /** Un móvil personal es lo normal, pero no obligatorio. */
    static final int MOBILE_PERCENT = 80;
    /** El fijo es minoritario: la pantalla debe convivir con el hueco. */
    static final int LANDLINE_PERCENT = 30;
    /** Móvil de empresa: solo una parte de la plantilla lo tiene asignado. */
    static final int COMPANY_MOBILE_PERCENT = 25;
    /** Extensión: quien tiene puesto fijo. */
    static final int EXTENSION_PERCENT = 40;

    /** Sin DNI, el resto: extranjeros, que llevan pasaporte de su país y ese es su identificador principal. */
    static final int NATIONAL_ID_PERCENT = 90;
    /** Pasaporte, además del DNI, en una minoría. */
    static final int PASSPORT_PERCENT = 35;
    /** Número de la Seguridad Social: falta en quien acaba de llegar y aún no lo tiene. */
    static final int SOCIAL_SECURITY_PERCENT = 75;

    /** Domicilio fiscal distinto del habitual, solo cuando alguien lo ha declarado. */
    static final int FISCAL_ADDRESS_PERCENT = 20;
    /** Dirección postal aparte: rara. */
    static final int MAILING_ADDRESS_PERCENT = 8;
    /** Se ha mudado tras el alta: dos HOME encadenadas, la primera cerrada. Ejercita el histórico. */
    static final int MOVED_HOME_PERCENT = 10;
    /** Vive fuera de España. */
    static final int FOREIGN_HOME_PERCENT = 4;

    // ---- Formatos sintéticos ----

    /** Dominio reservado por la RFC 2606: nunca resuelve, nadie recibe estos correos. */
    static final String EMAIL_DOMAIN = "b4rrhh.example";
    /** Ofcom reserva 07700 900000–900999 para ficción; en internacional, +44 7700 900xxx. */
    static final String FICTIONAL_MOBILE_PREFIX = "+44 7700 900";
    /** Ofcom reserva 020 7946 0000–0999 (Londres) para ficción. */
    static final String FICTIONAL_LANDLINE_PREFIX = "+44 20 7946 0";
    /** Tres alfanuméricos y seis dígitos, como un pasaporte español; el prefijo delata la demo. */
    static final String PASSPORT_PREFIX = "B4R";
    /** Provincia de afiliación del número sintético de la Seguridad Social. */
    private static final String SOCIAL_SECURITY_PROVINCE = "28";
    private static final String DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";

    private static final String SPAIN = "ESP";
    private static final List<String> FOREIGN_PASSPORT_COUNTRIES = List.of("PRT", "FRA", "ITA", "DEU", "GBR", "ARG");

    private record City(String name, String postalCodePrefix, String regionCode, String countryCode) {
    }

    private static final List<City> SPANISH_CITIES = List.of(
            new City("Madrid", "28", "ES-MD", SPAIN),
            new City("Barcelona", "08", "ES-CT", SPAIN),
            new City("Valencia", "46", "ES-VC", SPAIN),
            new City("Sevilla", "41", "ES-AN", SPAIN),
            new City("Zaragoza", "50", "ES-AR", SPAIN),
            new City("Malaga", "29", "ES-AN", SPAIN),
            new City("Bilbao", "48", "ES-PV", SPAIN),
            new City("Murcia", "30", "ES-MC", SPAIN),
            new City("Valladolid", "47", "ES-CL", SPAIN),
            new City("A Coruna", "15", "ES-GA", SPAIN)
    );

    private static final List<City> FOREIGN_CITIES = List.of(
            new City("Lisboa", "1000-", "PT-11", "PRT"),
            new City("Porto", "4000-", "PT-13", "PRT"),
            new City("Toulouse", "31", "FR-OCC", "FRA")
    );

    /** Calles que hay en cualquier ciudad, para no señalar ninguna dirección real. */
    private static final Map<String, List<String>> STREETS_BY_COUNTRY = Map.of(
            SPAIN, List.of(
                    "Calle Mayor", "Calle Real", "Avenida de la Constitucion", "Plaza de Espana",
                    "Paseo de la Estacion", "Calle Nueva", "Calle del Sol", "Camino Viejo",
                    "Ronda de Poniente", "Travesia de la Iglesia"
            ),
            "PRT", List.of("Rua Direita", "Avenida da Liberdade", "Rua Nova", "Largo do Mercado"),
            "FRA", List.of("Rue de la Gare", "Avenue de la Republique", "Place du Marche", "Rue Neuve")
    );

    /** Correos ya repartidos en esta tirada: dos «ana.garcia» se distinguen con un sufijo, como en cualquier empresa. */
    private final Set<String> usedEmails = new HashSet<>();

    SyntheticPersonalData generate(SyntheticEmployee.PersonName name, LocalDate hireDate, int sequence, Random random) {
        return new SyntheticPersonalData(
                addresses(hireDate, random),
                contacts(name, sequence, random),
                identifiers(hireDate, sequence, random)
        );
    }

    // ---- Direcciones ----

    private List<Address> addresses(LocalDate hireDate, Random random) {
        List<Address> addresses = new ArrayList<>();

        City firstHome = pickHomeCity(random);
        if (chance(MOVED_HOME_PERCENT, random)) {
            LocalDate movedOut = hireDate.plusDays(60 + random.nextInt(340));
            addresses.add(address("HOME", firstHome, hireDate, movedOut, random));
            addresses.add(address("HOME", pickHomeCity(random), movedOut.plusDays(1), null, random));
        } else {
            addresses.add(address("HOME", firstHome, hireDate, null, random));
        }

        if (chance(FISCAL_ADDRESS_PERCENT, random)) {
            addresses.add(address("FISCAL", pickFrom(SPANISH_CITIES, random), hireDate, null, random));
        }
        if (chance(MAILING_ADDRESS_PERCENT, random)) {
            addresses.add(address("MAILING", pickFrom(SPANISH_CITIES, random), hireDate, null, random));
        }
        return addresses;
    }

    private static City pickHomeCity(Random random) {
        return pickFrom(chance(FOREIGN_HOME_PERCENT, random) ? FOREIGN_CITIES : SPANISH_CITIES, random);
    }

    private static City pickFrom(List<City> cities, Random random) {
        return cities.get(random.nextInt(cities.size()));
    }

    private static Address address(String typeCode, City city, LocalDate startDate, LocalDate endDate, Random random) {
        List<String> streets = STREETS_BY_COUNTRY.get(city.countryCode());
        String street = streets.get(random.nextInt(streets.size())) + ", " + (1 + random.nextInt(120));
        if (SPAIN.equals(city.countryCode()) && random.nextInt(100) < 60) {
            street += " - " + (1 + random.nextInt(7)) + (char) ('A' + random.nextInt(4));
        }
        String postalCode = city.postalCodePrefix() + String.format("%03d", 1 + random.nextInt(999));
        return new Address(typeCode, street, city.name(), city.countryCode(), postalCode, city.regionCode(), startDate, endDate);
    }

    // ---- Contactos ----

    private List<Contact> contacts(SyntheticEmployee.PersonName name, int sequence, Random random) {
        List<Contact> contacts = new ArrayList<>();
        boolean email = chance(EMAIL_PERCENT, random);
        boolean mobile = chance(MOBILE_PERCENT, random);
        if (!email && !mobile) {
            email = true; // nadie se queda sin ningún contacto
        }
        if (email) {
            contacts.add(new Contact("EMAIL", uniqueEmail(name)));
        }
        if (mobile) {
            contacts.add(new Contact("MOBILE", FICTIONAL_MOBILE_PREFIX + String.format("%03d", sequence % 1000)));
        }
        if (chance(LANDLINE_PERCENT, random)) {
            contacts.add(new Contact("PHONE", FICTIONAL_LANDLINE_PREFIX + String.format("%03d", sequence % 1000)));
        }
        if (chance(COMPANY_MOBILE_PERCENT, random)) {
            // Desplazado 500 dentro del mismo rango de ficción para no repetir el móvil personal de nadie
            // mientras la plantilla no pase de 500.
            contacts.add(new Contact("COMPANY_MOBILE", FICTIONAL_MOBILE_PREFIX + String.format("%03d", (sequence + 500) % 1000)));
        }
        if (chance(EXTENSION_PERCENT, random)) {
            contacts.add(new Contact("EXTENSION", String.valueOf(1000 + sequence % 9000)));
        }
        return contacts;
    }

    private String uniqueEmail(SyntheticEmployee.PersonName name) {
        String base = (name.firstName() + "." + name.lastName1()).toLowerCase(Locale.ROOT);
        String local = base;
        for (int suffix = 2; !usedEmails.add(local); suffix++) {
            local = base + suffix;
        }
        return local + "@" + EMAIL_DOMAIN;
    }

    // ---- Identificadores ----

    private List<Identifier> identifiers(LocalDate hireDate, int sequence, Random random) {
        List<Identifier> identifiers = new ArrayList<>();
        boolean spanish = chance(NATIONAL_ID_PERCENT, random);
        if (spanish) {
            identifiers.add(new Identifier("NATIONAL_ID", syntheticDni(sequence), SPAIN,
                    hireDate.plusYears(1 + random.nextInt(10)), true));
        }
        // Sin DNI, el pasaporte extranjero es obligado y principal; con DNI, es un extra minoritario.
        if (!spanish || chance(PASSPORT_PERCENT, random)) {
            String country = spanish ? SPAIN : FOREIGN_PASSPORT_COUNTRIES.get(random.nextInt(FOREIGN_PASSPORT_COUNTRIES.size()));
            identifiers.add(new Identifier("PASSPORT", PASSPORT_PREFIX + String.format("%06d", sequence), country,
                    hireDate.plusYears(2 + random.nextInt(9)), !spanish));
        }
        if (chance(SOCIAL_SECURITY_PERCENT, random)) {
            identifiers.add(new Identifier("SOCIAL_SECURITY", syntheticSocialSecurityNumber(sequence), SPAIN, null, false));
        }
        return identifiers;
    }

    /** Ocho dígitos y su letra; la numeración con seis ceros delante es la de 1951 y no la lleva nadie vivo. */
    static String syntheticDni(int sequence) {
        String digits = String.format("%08d", sequence);
        return digits + DNI_LETTERS.charAt(Integer.parseInt(digits) % 23);
    }

    /** Provincia + ocho dígitos + dos de control (módulo 97 del número completo), como un NAF de verdad. */
    static String syntheticSocialSecurityNumber(int sequence) {
        String body = SOCIAL_SECURITY_PROVINCE + String.format("%08d", sequence);
        return body + String.format("%02d", Long.parseLong(body) % 97);
    }

    private static boolean chance(int percent, Random random) {
        return random.nextInt(100) < percent;
    }
}
