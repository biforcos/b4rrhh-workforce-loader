package com.b4rrhh.workforceloader.domain.model;

import java.util.List;

public record LoaderRunSummary(
        int totalEmployeesRequested,
        int hiresRequested,
        int hiresSuccess,
        int hiresFailed,
        int terminationsRequested,
        int terminationsSuccess,
        int terminationsFailed,
        int rehiresRequested,
        int rehiresSuccess,
        int rehiresFailed,
        int workCenterChangesRequested,
        int workCenterChangesSuccess,
        int workCenterChangesFailed,
        int contractReplacementsRequested,
        int contractReplacementsSuccess,
        int contractReplacementsFailed,
        int laborClassificationReplacementsRequested,
        int laborClassificationReplacementsSuccess,
        int laborClassificationReplacementsFailed,
        int costCenterReplacementsRequested,
        int costCenterReplacementsSuccess,
        int costCenterReplacementsFailed,
        int personalDataRequested,
        int personalDataSuccess,
        int personalDataFailed,
        List<LifecycleEventExecutionResult> results
) {
}
