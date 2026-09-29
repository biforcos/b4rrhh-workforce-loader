package com.b4rrhh.workforceloader.infrastructure.api.dto;

import java.time.LocalDate;
import java.util.List;

public record HireEmployeeRequest(
        String ruleSystemCode,
        String employeeTypeCode,
        String firstName,
        String lastName1,
        String lastName2,
        String preferredName,
        LocalDate hireDate,
        String entryReasonCode,
        String companyCode,
        String workCenterCode,
        Contract contract,
        LaborClassification laborClassification,
        WorkingTime workingTime,
        CostCenterDistribution costCenterDistribution,
        Identifier identifier
) {

    /** El documento de la persona, que el alta exige desde el b4rrhh/backend#141. */
    public record Identifier(
            String identifierTypeCode,
            String identifierValue,
            String issuingCountryCode,
            LocalDate expirationDate
    ) {
    }

    public record Contract(
            String contractTypeCode,
            String contractSubtypeCode
    ) {
    }

    public record LaborClassification(
            String agreementCode,
            String agreementCategoryCode
    ) {
    }

    public record WorkingTime(
            java.math.BigDecimal workingTimePercentage
    ) {
    }

    public record CostCenterDistribution(
            List<Item> items
    ) {
        public record Item(
                String costCenterCode,
                Integer allocationPercentage
        ) {
        }
    }
}
