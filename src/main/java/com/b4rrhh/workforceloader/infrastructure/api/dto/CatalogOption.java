package com.b4rrhh.workforceloader.infrastructure.api.dto;

import java.time.LocalDate;

/**
 * Una opcion de catalogo, con su vigencia.
 *
 * Las fechas llegaban del backend y se tiraban. Por eso el loader elegia tipos
 * de contrato sin mirar si existian el dia del alta, y el backend contestaba
 * 422 INVALID_CATALOG_VALUE: los codigos de la reforma laboral no existian en
 * 2021.
 */
public record CatalogOption(
        String code,
        String name,
        LocalDate startDate,
        LocalDate endDate
) {

    /** Para los catalogos que no traen vigencia: se comportan como siempre. */
    public CatalogOption(String code, String name) {
        this(code, name, null, null);
    }

    /**
     * Sin vigencia conocida, o sin fecha con la que comparar, se da por vigente.
     * Es lo que hacia el loader hasta ahora, asi que lo que no tenga fechas se
     * sigue comportando igual y no cambiamos nada por sorpresa.
     */
    public boolean isVigenteEn(LocalDate fecha) {
        if (fecha == null) {
            return true;
        }
        if (startDate != null && fecha.isBefore(startDate)) {
            return false;
        }
        return endDate == null || !fecha.isAfter(endDate);
    }
}
