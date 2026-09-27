package com.b4rrhh.workforceloader.infrastructure.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Lo que dejo un cierre en masa. Los contadores son el entregable ({@code b4rrhh/backend#102}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BulkFinalizePayrollResponse(
        Integer totalCandidates,
        Integer totalFound,
        Integer totalFinalized,
        Integer totalSkippedAlreadyDefinitive,
        Integer totalSkippedNotEligibleByStatus,
        Integer totalSkippedNotFound
) {
}
