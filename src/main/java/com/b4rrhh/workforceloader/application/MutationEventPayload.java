package com.b4rrhh.workforceloader.application;

public sealed interface MutationEventPayload permits
        WorkCenterChangeEventPayload,
        ContractReplaceEventPayload,
        LaborClassificationReplaceEventPayload,
        CostCenterReplaceEventPayload,
        WorkingTimeChangeEventPayload,
        ExtraPaymentRegimeChangeEventPayload,
        AbsenceEventPayload,
        PayrollInputEventPayload {
}
