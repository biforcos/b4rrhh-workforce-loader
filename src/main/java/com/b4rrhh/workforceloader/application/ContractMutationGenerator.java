package com.b4rrhh.workforceloader.application;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class ContractMutationGenerator {

    public ContractReplaceEventPayload generate(
            ResolvedHireReferencePools pools,
            EmployeeExecutionState state,
            Random random
    ) {
        return generate(pools, state, null, random);
    }

    /**
     * Sustituir un contrato el 1 de marzo de 2024 significa elegir entre lo que
     * existia ese dia, no entre todo lo que ha existido alguna vez. Sin la
     * fecha, el backend rechazaba la mutacion con 422 INVALID_CATALOG_VALUE.
     */
    public ContractReplaceEventPayload generate(
            ResolvedHireReferencePools pools,
            EmployeeExecutionState state,
            LocalDate effectiveDate,
            Random random
    ) {
        ResolvedContractData selected =
                selectMutation(acotar(pools, effectiveDate), state.getCurrentContractData(), random);
        return new ContractReplaceEventPayload(selected.contractCode(), selected.contractSubtypeCode());
    }

    /**
     * Devuelve los mismos pools con los tipos y subtipos no vigentes fuera.
     *
     * Si no quedara nada vigente se devuelven sin tocar: mejor que el backend
     * rechace ese evento concreto y quede en el informe, a que la planificacion
     * se quede sin ninguna opcion y reviente la tanda.
     */
    private ResolvedHireReferencePools acotar(ResolvedHireReferencePools pools, LocalDate fecha) {
        if (fecha == null) {
            return pools;
        }

        List<ContractTypeWithSubtypes> vigentes = pools.contractTypesWithSubtypes().stream()
                .filter(tipo -> tipo.contractType().isVigenteEn(fecha))
                .map(tipo -> new ContractTypeWithSubtypes(
                        tipo.contractType(),
                        tipo.subtypes().stream().filter(sub -> sub.isVigenteEn(fecha)).toList()))
                .filter(tipo -> !tipo.subtypes().isEmpty())
                .toList();

        if (vigentes.isEmpty()) {
            return pools;
        }

        return new ResolvedHireReferencePools(
                pools.companies(),
                pools.workCenters(),
                pools.entryReasons(),
                pools.exitReasons(),
                pools.agreementsWithCategories(),
                vigentes,
                pools.absenceTypes()
        );
    }

    private ResolvedContractData selectMutation(
            ResolvedHireReferencePools pools,
            ResolvedContractData current,
            Random random
    ) {
        ResolvedContractData lightChange = tryLightChange(pools, current, random);
        ResolvedContractData strongChange = tryStrongChange(pools, current, random);

        if (lightChange != null && strongChange != null) {
            return random.nextDouble() < 0.75 ? lightChange : strongChange;
        }
        if (lightChange != null) {
            return lightChange;
        }
        if (strongChange != null) {
            return strongChange;
        }

        return fallbackSelection(pools, random);
    }

    private ResolvedContractData tryLightChange(
            ResolvedHireReferencePools pools,
            ResolvedContractData current,
            Random random
    ) {
        if (current == null) {
            return null;
        }

        ContractTypeWithSubtypes currentType = pools.contractTypesWithSubtypes().stream()
                .filter(type -> type.contractType().code().equalsIgnoreCase(current.contractCode()))
                .findFirst()
                .orElse(null);

        if (currentType == null) {
            return null;
        }

        var alternativeSubtypes = currentType.subtypes().stream()
                .filter(subtype -> !subtype.code().equalsIgnoreCase(current.contractSubtypeCode()))
                .toList();

        if (alternativeSubtypes.isEmpty()) {
            return null;
        }

        return new ResolvedContractData(
                currentType.contractType().code(),
                RandomSelector.pickRandom(alternativeSubtypes, random).code()
        );
    }

    private ResolvedContractData tryStrongChange(
            ResolvedHireReferencePools pools,
            ResolvedContractData current,
            Random random
    ) {
        var alternativeTypes = pools.contractTypesWithSubtypes().stream()
                .filter(type -> current == null || !type.contractType().code().equalsIgnoreCase(current.contractCode()))
                .toList();

        if (alternativeTypes.isEmpty()) {
            return null;
        }

        ContractTypeWithSubtypes selectedType = RandomSelector.pickRandom(alternativeTypes, random);
        return new ResolvedContractData(
                selectedType.contractType().code(),
                RandomSelector.pickRandom(selectedType.subtypes(), random).code()
        );
    }

    private ResolvedContractData fallbackSelection(ResolvedHireReferencePools pools, Random random) {
        if (pools.contractTypesWithSubtypes().size() == 1
                && pools.contractTypesWithSubtypes().getFirst().subtypes().size() == 1) {
            ContractTypeWithSubtypes onlyType = pools.contractTypesWithSubtypes().getFirst();
            return new ResolvedContractData(onlyType.contractType().code(), onlyType.subtypes().getFirst().code());
        }

        ContractTypeWithSubtypes fallbackType = RandomSelector.pickRandom(pools.contractTypesWithSubtypes(), random);
        return new ResolvedContractData(
                fallbackType.contractType().code(),
                RandomSelector.pickRandom(fallbackType.subtypes(), random).code()
        );
    }
}
