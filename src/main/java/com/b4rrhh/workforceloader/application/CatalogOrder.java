package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.api.dto.CatalogOption;

import java.util.Comparator;
import java.util.List;

/**
 * El orden en que el loader ve un catálogo: por código, en orden binario (workforce-loader#18).
 *
 * <p>El loader elige opciones por posición —el azar da un índice— y el orden en que llegan del
 * backend lo decide la collation de Postgres ({@code en_US} trata el {@code _} distinto que Java). Con
 * otra collation, el mismo loader sembraba otra plantilla, y la huella «idéntica» entre máquinas era
 * falsa sin que nada lo dijera. Toda lista que entra del catálogo pasa por aquí antes de que se elija
 * nada de ella, y desde entonces la semilla no depende de quién la sirve.</p>
 *
 * <p>{@link String#compareTo} compara unidades UTF-16: no mira el locale de la JVM ni el de la base.
 * {@code EveryCatalogListIsOrderedByCodeTest} vigila que ninguna lista entre sin pasar por aquí.</p>
 */
public final class CatalogOrder {

    private static final Comparator<CatalogOption> BY_CODE =
            Comparator.comparing(CatalogOption::code, Comparator.nullsFirst(Comparator.naturalOrder()));

    private CatalogOrder() {
    }

    public static List<CatalogOption> byCode(List<CatalogOption> options) {
        return options == null ? List.of() : options.stream().sorted(BY_CODE).toList();
    }
}
