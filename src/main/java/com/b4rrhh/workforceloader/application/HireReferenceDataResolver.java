package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.api.CatalogApiClient;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CatalogOption;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class HireReferenceDataResolver {

    private static final Logger log = LoggerFactory.getLogger(HireReferenceDataResolver.class);

    private static final String PRESENCE_RESOURCE = "employee.presence";
    private static final String WORK_CENTER_RESOURCE = "employee.work_center";
    private static final String CONTRACT_RESOURCE = "employee.contract";
    private static final String LABOR_CLASSIFICATION_RESOURCE = "employee.labor_classification";

    private static final String COMPANY_FIELD = "companyCode";
    private static final String WORK_CENTER_FIELD = "workCenterCode";
    private static final String ENTRY_REASON_FIELD = "entryReasonCode";
    private static final String EXIT_REASON_FIELD = "exitReasonCode";
    private static final String CONTRACT_TYPE_FIELD = "contractTypeCode";
    private static final String AGREEMENT_FIELD = "agreementCode";

    private static final String COMPANY = "COMPANY";
    private static final String LEGACY_PRESENCE_COMPANY = "EMPLOYEE_PRESENCE_COMPANY";
    private static final String WORK_CENTER = "WORK_CENTER";
    private static final String ENTRY_REASON = "EMPLOYEE_PRESENCE_ENTRY_REASON";
    private static final String LEGACY_ENTRY_REASON = "ENTRY_REASON";
    private static final String EXIT_REASON = "EMPLOYEE_PRESENCE_EXIT_REASON";
    private static final String LEGACY_EXIT_REASON = "EXIT_REASON";
    private static final String AGREEMENT = "AGREEMENT";
    private static final String CONTRACT = "CONTRACT";

    private final CatalogApiClient catalogApiClient;
    private final LoaderProperties.Filters filters;

    public HireReferenceDataResolver(CatalogApiClient catalogApiClient, LoaderProperties properties) {
        this.catalogApiClient = catalogApiClient;
        this.filters = properties.getFilters();
    }

    public ResolvedHireData resolve(String ruleSystemCode) {
        return resolve(ruleSystemCode, null);
    }

    public ResolvedHireData resolve(String ruleSystemCode, Random random) {
        String normalizedRuleSystemCode = normalizeCode(ruleSystemCode);

        ResolvedHireReferencePools pools = preloadPools(normalizedRuleSystemCode);
        return resolveFromPools(pools, normalizedRuleSystemCode, null, random);
    }

    public ResolvedHireReferencePools preloadPools(String ruleSystemCode) {
        String normalizedRuleSystemCode = normalizeCode(ruleSystemCode);

        List<CatalogOption> companies = catalogApiClient.getDirectOptionsForField(
            normalizedRuleSystemCode,
            PRESENCE_RESOURCE,
            COMPANY_FIELD,
            COMPANY,
            LEGACY_PRESENCE_COMPANY
        );
        List<CatalogOption> workCenters = catalogApiClient.getDirectOptionsForField(
            normalizedRuleSystemCode,
            WORK_CENTER_RESOURCE,
            WORK_CENTER_FIELD,
            WORK_CENTER
        );
        List<CatalogOption> entryReasons = resolveEntryReasonOptions(normalizedRuleSystemCode);
        List<CatalogOption> exitReasons = resolveExitReasonOptions(normalizedRuleSystemCode);

        List<CatalogOption> agreements = catalogApiClient.getDirectOptionsForField(
            normalizedRuleSystemCode,
            LABOR_CLASSIFICATION_RESOURCE,
            AGREEMENT_FIELD,
            AGREEMENT
        );
        List<AgreementWithCategories> agreementsWithCategories = new ArrayList<>(agreements.size());
        for (CatalogOption agreement : agreements) {
            List<CatalogOption> categories = catalogApiClient.getAgreementCategories(normalizedRuleSystemCode, agreement.code());
            agreementsWithCategories.add(new AgreementWithCategories(agreement, categories));
        }

        List<CatalogOption> contractTypes = catalogApiClient.getDirectOptionsForField(
            normalizedRuleSystemCode,
            CONTRACT_RESOURCE,
            CONTRACT_TYPE_FIELD,
            CONTRACT
        );
        List<ContractTypeWithSubtypes> contractTypesWithSubtypes = new ArrayList<>(contractTypes.size());
        for (CatalogOption contractType : contractTypes) {
            List<CatalogOption> subtypes = catalogApiClient.getContractSubtypes(normalizedRuleSystemCode, contractType.code());
            contractTypesWithSubtypes.add(new ContractTypeWithSubtypes(contractType, subtypes));
        }

        return new ResolvedHireReferencePools(
                companies,
                workCenters,
                entryReasons,
                exitReasons,
                applyAgreementFilter(agreementsWithCategories),
                applyContractFilter(contractTypesWithSubtypes)
        );
    }

    private List<AgreementWithCategories> applyAgreementFilter(List<AgreementWithCategories> source) {
        String code = filters.getAgreementCode();
        if (code == null || code.isBlank()) {
            return source;
        }
        String normalizedCode = code.trim().toUpperCase();
        List<String> categoryFilter = filters.getAgreementCategoryCodes().stream()
                .map(c -> c.trim().toUpperCase())
                .toList();

        return source.stream()
                .filter(a -> a.agreement().code().equalsIgnoreCase(normalizedCode))
                .map(a -> categoryFilter.isEmpty() ? a :
                        new AgreementWithCategories(
                                a.agreement(),
                                a.categories().stream()
                                        .filter(c -> categoryFilter.contains(c.code().toUpperCase()))
                                        .toList()
                        )
                )
                .filter(a -> !a.categories().isEmpty())
                .toList();
    }

    private List<ContractTypeWithSubtypes> applyContractFilter(List<ContractTypeWithSubtypes> source) {
        List<ContractTypeWithSubtypes> result = source;

        if (filters.isNumericContractTypesOnly()) {
            result = result.stream()
                    .filter(c -> c.contractType().code().matches("[0-9]+"))
                    .toList();
        }

        if (filters.isRequireContractSubtype()) {
            result = result.stream()
                    .filter(c -> !c.subtypes().isEmpty())
                    .toList();
        }

        return result;
    }

    public ResolvedHireData resolveFromPools(ResolvedHireReferencePools pools, Random random) {
        return resolveFromPools(pools, null, null, random);
    }

    public ResolvedHireData resolveFromPools(
            ResolvedHireReferencePools pools,
            String ruleSystemCode,
            LocalDate referenceDate,
            Random random
    ) {
        ResolvedCompanyWorkCenter companyAndWorkCenter = resolveCompanyAndWorkCenterFromPools(
            pools.companies(),
            ruleSystemCode,
            referenceDate,
            random
        );
        CatalogOption entryReason = pick(vigentes(pools.entryReasons(), referenceDate, "motivos de entrada"), random);

        AgreementWithCategories agreementWithCategories =
                pick(convenidosVigentes(pools.agreementsWithCategories(), referenceDate), random);
        CatalogOption agreement = agreementWithCategories.agreement();
        CatalogOption agreementCategory = pick(
                categoriasDelDia(ruleSystemCode, agreement, agreementWithCategories.categories(), referenceDate),
                random);

        ContractTypeWithSubtypes contractTypeWithSubtypes =
                pick(contratosVigentes(pools.contractTypesWithSubtypes(), referenceDate), random);
        CatalogOption contractType = contractTypeWithSubtypes.contractType();
        CatalogOption contractSubtype = pick(
                subtiposDelDia(ruleSystemCode, contractType, contractTypeWithSubtypes.subtypes(), referenceDate),
                random);

        return new ResolvedHireData(
            companyAndWorkCenter.companyCode(),
            companyAndWorkCenter.workCenterCode(),
                entryReason.code(),
                agreement.code(),
                agreementCategory.code(),
                contractType.code(),
                contractSubtype.code(),
                null
        );
    }

    public String resolveExitReasonFromPools(ResolvedHireReferencePools pools, Random random) {
        return resolveExitReasonFromPools(pools, null, random);
    }

    public String resolveExitReasonFromPools(
            ResolvedHireReferencePools pools,
            LocalDate referenceDate,
            Random random
    ) {
        CatalogOption exitReason = pick(vigentes(pools.exitReasons(), referenceDate, "motivos de salida"), random);
        return exitReason.code();
    }

    /**
     * Categorias del convenio vigentes ese dia, preguntandolas al backend.
     *
     * No vale filtrar en local aunque las categorias traigan sus fechas: lo que
     * caduca es la RELACION convenio-categoria, que tiene vigencia propia y no
     * viaja en la respuesta. Lo comprobamos por las malas: las categorias
     * salian vigentes por sus fechas y el backend las rechazaba igual con
     * INVALID_CATALOG_VALUE.
     *
     * Con los filtros actuales solo hay un convenio, asi que esto es una
     * llamada por fecha distinta, y el cliente las cachea.
     */
    private List<CatalogOption> categoriasDelDia(
            String ruleSystemCode,
            CatalogOption convenio,
            List<CatalogOption> precargadas,
            LocalDate fecha
    ) {
        return delDia(ruleSystemCode, fecha, precargadas,
                () -> catalogApiClient.getAgreementCategories(ruleSystemCode, convenio.code(), fecha),
                "categorias del convenio " + convenio.code());
    }

    /** Mismo motivo: la relacion tipo-subtipo tiene su propia vigencia. */
    private List<CatalogOption> subtiposDelDia(
            String ruleSystemCode,
            CatalogOption tipo,
            List<CatalogOption> precargados,
            LocalDate fecha
    ) {
        return delDia(ruleSystemCode, fecha, precargados,
                () -> catalogApiClient.getContractSubtypes(ruleSystemCode, tipo.code(), fecha),
                "subtipos del contrato " + tipo.code());
    }

    private List<CatalogOption> delDia(
            String ruleSystemCode,
            LocalDate fecha,
            List<CatalogOption> precargadas,
            java.util.function.Supplier<List<CatalogOption>> consulta,
            String queSon
    ) {
        if (fecha == null || ruleSystemCode == null) {
            return precargadas;
        }
        List<CatalogOption> delDia = consulta.get();
        if (delDia == null || delDia.isEmpty()) {
            log.warn("Ninguna opcion vigente el {} entre las {}. Se usan las precargadas y sera el backend"
                    + " quien lo rechace.", fecha, queSon);
            return precargadas;
        }
        return delDia;
    }

    /**
     * Se queda con lo vigente en la fecha. Si no queda nada, devuelve la lista
     * entera y avisa.
     *
     * Es deliberado no lanzar: la planificacion de escenarios no esta dentro de
     * ningun try/catch, asi que una excepcion aqui abortaria la tanda completa
     * por un hueco de catalogo. Degradando al comportamiento anterior, el
     * backend rechaza ese evento con 422 y queda anotado en el informe, que es
     * justo donde se quiere ver.
     */
    private List<CatalogOption> vigentes(List<CatalogOption> opciones, LocalDate fecha, String queSon) {
        if (fecha == null || opciones == null || opciones.isEmpty()) {
            return opciones;
        }
        List<CatalogOption> filtradas = opciones.stream()
                .filter(o -> o.isVigenteEn(fecha))
                .toList();
        if (filtradas.isEmpty()) {
            log.warn("Ningun valor vigente el {} entre los {} disponibles ({}). Se usa el catalogo sin filtrar "
                    + "y sera el backend quien lo rechace.", fecha, queSon, opciones.size());
            return opciones;
        }
        return filtradas;
    }

    private List<ContractTypeWithSubtypes> contratosVigentes(
            List<ContractTypeWithSubtypes> origen,
            LocalDate fecha
    ) {
        if (fecha == null || origen == null || origen.isEmpty()) {
            return origen;
        }
        List<ContractTypeWithSubtypes> filtrados = origen.stream()
                .filter(c -> c.contractType().isVigenteEn(fecha))
                .filter(c -> c.subtypes().stream().anyMatch(sub -> sub.isVigenteEn(fecha)))
                .toList();
        if (filtrados.isEmpty()) {
            log.warn("Ningun tipo de contrato vigente el {} entre {} disponibles. Se usa el catalogo sin filtrar.",
                    fecha, origen.size());
            return origen;
        }
        return filtrados;
    }

    private List<AgreementWithCategories> convenidosVigentes(
            List<AgreementWithCategories> origen,
            LocalDate fecha
    ) {
        if (fecha == null || origen == null || origen.isEmpty()) {
            return origen;
        }
        List<AgreementWithCategories> filtrados = origen.stream()
                .filter(a -> a.agreement().isVigenteEn(fecha))
                .filter(a -> a.categories().stream().anyMatch(cat -> cat.isVigenteEn(fecha)))
                .toList();
        if (filtrados.isEmpty()) {
            log.warn("Ningun convenio vigente el {} entre {} disponibles. Se usa el catalogo sin filtrar.",
                    fecha, origen.size());
            return origen;
        }
        return filtrados;
    }

    public String resolveWorkCenterCodeFromPools(ResolvedHireReferencePools pools, Random random) {
        CatalogOption company = pick(pools.companies(), random);
        return resolveWorkCenterCodeForCompany(null, company.code(), null, null, random);
    }

    public String resolveWorkCenterCodeForCompany(
            String ruleSystemCode,
            String companyCode,
            LocalDate referenceDate,
            String currentWorkCenterCode,
            Random random
    ) {
        List<CatalogOption> workCenters = catalogApiClient.getWorkCentersByCompany(ruleSystemCode, companyCode, referenceDate);
        CatalogOption selected = pickDifferentOrAny(workCenters, currentWorkCenterCode, random);
        return selected.code();
    }

    public String tryResolveWorkCenterCodeForCompany(
            String ruleSystemCode,
            String companyCode,
            LocalDate referenceDate,
            String currentWorkCenterCode,
            Random random
    ) {
        try {
            return resolveWorkCenterCodeForCompany(ruleSystemCode, companyCode, referenceDate, currentWorkCenterCode, random);
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    public ResolvedContractData resolveContractFromPools(ResolvedHireReferencePools pools, Random random) {
        ContractTypeWithSubtypes contractTypeWithSubtypes = pick(pools.contractTypesWithSubtypes(), random);
        CatalogOption contractType = contractTypeWithSubtypes.contractType();
        CatalogOption contractSubtype = pick(contractTypeWithSubtypes.subtypes(), random);
        return new ResolvedContractData(contractType.code(), contractSubtype.code());
    }

    public ResolvedLaborClassificationData resolveLaborClassificationFromPools(
            ResolvedHireReferencePools pools,
            Random random
    ) {
        AgreementWithCategories agreementWithCategories = pick(pools.agreementsWithCategories(), random);
        CatalogOption agreement = agreementWithCategories.agreement();
        CatalogOption agreementCategory = pick(agreementWithCategories.categories(), random);
        return new ResolvedLaborClassificationData(agreement.code(), agreementCategory.code());
    }

    public String resolveExitReasonCode(String ruleSystemCode, Random random) {
        String normalizedRuleSystemCode = normalizeCode(ruleSystemCode);
        ResolvedHireReferencePools pools = preloadPools(normalizedRuleSystemCode);
        return resolveExitReasonFromPools(pools, random);
    }

    private List<CatalogOption> resolveEntryReasonOptions(String ruleSystemCode) {
        return catalogApiClient.getDirectOptionsForField(
                ruleSystemCode,
                PRESENCE_RESOURCE,
                ENTRY_REASON_FIELD,
                ENTRY_REASON,
                LEGACY_ENTRY_REASON
        );
    }

    private List<CatalogOption> resolveExitReasonOptions(String ruleSystemCode) {
        return catalogApiClient.getDirectOptionsForField(
                ruleSystemCode,
                PRESENCE_RESOURCE,
                EXIT_REASON_FIELD,
                EXIT_REASON,
                LEGACY_EXIT_REASON
        );
    }

    private static <T> T pick(List<T> values, Random random) {
        if (random == null) {
            return RandomSelector.pickRandom(values);
        }
        return RandomSelector.pickRandom(values, random);
    }

    private ResolvedCompanyWorkCenter resolveCompanyAndWorkCenterFromPools(
            List<CatalogOption> companies,
            String ruleSystemCode,
            LocalDate referenceDate,
            Random random
    ) {
        if (companies == null || companies.isEmpty()) {
            throw new IllegalStateException("Cannot resolve company and work center from empty company pool");
        }

        int startIndex = random == null ? 0 : random.nextInt(companies.size());
        IllegalStateException lastError = null;

        for (int offset = 0; offset < companies.size(); offset++) {
            CatalogOption company = companies.get((startIndex + offset) % companies.size());
            try {
                String workCenterCode = resolveWorkCenterCodeForCompany(
                        ruleSystemCode,
                        company.code(),
                        referenceDate,
                        null,
                        random
                );
                return new ResolvedCompanyWorkCenter(company.code(), workCenterCode);
            } catch (IllegalStateException ex) {
                lastError = ex;
            }
        }

        throw new IllegalStateException(
                "No work centers available for any company in ruleSystemCode="
                        + normalizeCode(ruleSystemCode)
                        + ", referenceDate="
                        + referenceDate,
                lastError
        );
    }

    private static CatalogOption pickDifferentOrAny(List<CatalogOption> options, String currentCode, Random random) {
        if (options.size() == 1 || currentCode == null || currentCode.isBlank()) {
            return pick(options, random);
        }

        List<CatalogOption> filtered = options.stream()
                .filter(option -> !option.code().equalsIgnoreCase(currentCode))
                .toList();

        if (filtered.isEmpty()) {
            return pick(options, random);
        }

        return pick(filtered, random);
    }

    private static String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private record ResolvedCompanyWorkCenter(String companyCode, String workCenterCode) {
    }
}
