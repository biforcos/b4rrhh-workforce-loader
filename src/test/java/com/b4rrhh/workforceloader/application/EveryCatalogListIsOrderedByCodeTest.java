package com.b4rrhh.workforceloader.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ninguna lista del catálogo entra en la simulación sin ordenarse por código (workforce-loader#18).
 *
 * <p>{@code TheSeedDoesNotDependOnTheOrderOfTheCatalogTest} prueba el escenario con el catálogo que
 * conoce; éste vigila el código: toda llamada del loader que trae una lista del catálogo pasa por
 * {@link CatalogOrder#byCode} antes de que nadie elija de ella. Una entrada nueva sin ordenar sale
 * aquí, con su fichero y su línea. Se lee el {@code .java}: una lista que llegue por otro cliente no
 * se ve, y entonces hay que ampliar {@link #LISTAS}.</p>
 */
class EveryCatalogListIsOrderedByCodeTest {

    private static final Path FUENTES = Path.of("src/main/java/com/b4rrhh/workforceloader/application");

    /** Las llamadas del cliente de catálogo que devuelven una lista de opciones. */
    private static final Pattern LISTAS = Pattern.compile(
            "catalogApiClient\\.(getDirectOptions|getDirectOptionsForField|getAgreementCategories"
                    + "|getContractSubtypes|getWorkCentersByCompany)\\s*\\(");

    @Test
    void everyCatalogListGoesThroughTheOrderByCode() throws IOException {
        List<String> sinOrdenar = new ArrayList<>();
        int llamadas = 0;
        try (Stream<Path> ficheros = Files.walk(FUENTES)) {
            for (Path fichero : ficheros.filter(f -> f.toString().endsWith(".java")).toList()) {
                String codigo = Files.readString(fichero, StandardCharsets.UTF_8);
                Matcher llamada = LISTAS.matcher(codigo);
                while (llamada.find()) {
                    llamadas++;
                    String antes = codigo.substring(Math.max(0, llamada.start() - "CatalogOrder.byCode(".length()),
                            llamada.start());
                    if (!antes.equals("CatalogOrder.byCode(")) {
                        int linea = codigo.substring(0, llamada.start()).split("\n", -1).length;
                        sinOrdenar.add(fichero.getFileName() + ":" + linea + " " + llamada.group(1));
                    }
                }
            }
        }

        assertThat(llamadas).as("el test encuentra las llamadas al catálogo, o no está mirando nada").isPositive();
        assertThat(sinOrdenar)
                .withFailMessage("""
                        Estas listas del catálogo entran sin ordenar por código: %s
                        El loader elige por posición; el orden en que llegan lo pone la collation de la base, y
                        con otra la semilla sería otra (workforce-loader#18). Envuélvelas en CatalogOrder.byCode(...).""",
                        sinOrdenar)
                .isEmpty();
    }
}
