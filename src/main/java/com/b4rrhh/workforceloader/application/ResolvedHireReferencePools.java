package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.api.dto.CatalogOption;

import java.util.List;

public record ResolvedHireReferencePools(
        List<CatalogOption> companies,
        List<CatalogOption> workCenters,
        List<CatalogOption> entryReasons,
        List<CatalogOption> exitReasons,
        List<AgreementWithCategories> agreementsWithCategories,
        List<ContractTypeWithSubtypes> contractTypesWithSubtypes,
        /** Puede venir vacia: un sistema de reglas sin tipos de ausencia simplemente no las genera. */
        List<CatalogOption> absenceTypes
) {
}