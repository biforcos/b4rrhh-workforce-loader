package com.b4rrhh.workforceloader.infrastructure.api;

import com.b4rrhh.workforceloader.infrastructure.api.dto.CatalogOption;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CatalogApiClient {

    private final WebClient webClient;
    private final Map<String, Map<String, CatalogFieldBinding>> bindingsCache = new ConcurrentHashMap<>();
    private final Map<String, List<CatalogOption>> dependientesCache = new ConcurrentHashMap<>();

    public CatalogApiClient(LoaderProperties properties, WebClient.Builder webClientBuilder) {
        WebClient.Builder builder = webClientBuilder
                .baseUrl(properties.getBackend().getBaseUrl());

        String token = normalizeToken(properties.getBackend().getAuthToken());
        if (token != null) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, token);
        }

        this.webClient = builder.build();
    }

    private static String normalizeToken(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            return null;
        }
        String trimmed = rawToken.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed;
        }
        return "Bearer " + trimmed;
    }

    public List<CatalogOption> getDirectOptions(String ruleSystemCode, String entityTypeCode) {
        DirectCatalogOptionsResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/catalog-options/direct")
                        .queryParam("ruleSystemCode", normalize(ruleSystemCode))
                        .queryParam("ruleEntityTypeCode", normalize(entityTypeCode))
                        .build())
                .retrieve()
                .bodyToMono(DirectCatalogOptionsResponse.class)
                .block();

        List<RawCatalogItem> items = response == null ? List.of() : response.items();
        return mapDirectOptions(items, entityTypeCode);
    }

    public List<CatalogOption> getDirectOptionsForField(
            String ruleSystemCode,
            String resourceCode,
            String fieldCode,
            String... fallbackEntityTypeCodes
    ) {
        LinkedHashSet<String> candidateEntityTypeCodes = new LinkedHashSet<>();

        resolveBinding(resourceCode, fieldCode)
                .map(CatalogFieldBinding::ruleEntityTypeCode)
                .map(CatalogApiClient::normalize)
                .filter(value -> value != null && !value.isBlank())
                .ifPresent(candidateEntityTypeCodes::add);

        if (fallbackEntityTypeCodes != null) {
            for (String fallbackEntityTypeCode : fallbackEntityTypeCodes) {
                String normalizedFallback = normalize(fallbackEntityTypeCode);
                if (normalizedFallback != null && !normalizedFallback.isBlank()) {
                    candidateEntityTypeCodes.add(normalizedFallback);
                }
            }
        }

        IllegalStateException lastError = null;
        for (String candidateEntityTypeCode : candidateEntityTypeCodes) {
            try {
                return getDirectOptions(ruleSystemCode, candidateEntityTypeCode);
            } catch (IllegalStateException ex) {
                lastError = ex;
            }
        }

        if (lastError != null) {
            throw lastError;
        }

        throw new IllegalStateException(
                "No catalog options found for resource=" + resourceCode + ", field=" + fieldCode
        );
    }

    public List<CatalogOption> getAgreementCategories(String ruleSystemCode, String agreementCode) {
        return getAgreementCategories(ruleSystemCode, agreementCode, null);
    }

    /**
     * Categorias de un convenio vigentes en una fecha.
     *
     * Hay que preguntarlo al backend en vez de filtrar aqui: la validez de una
     * categoria depende tambien de la vigencia de su RELACION con el convenio,
     * y esa no viaja en la respuesta. Filtrar en local con las fechas que si
     * llegan daria un resultado que parece bueno y no lo es.
     */
    public List<CatalogOption> getAgreementCategories(
            String ruleSystemCode,
            String agreementCode,
            LocalDate referenceDate
    ) {
        return dependientesEnCache("AGREEMENT_CATEGORY", ruleSystemCode, agreementCode, referenceDate, () ->
                webClient.get()
                        .uri(uriBuilder -> {
                            var builder = uriBuilder
                                    .path("/labor-classification-catalog/agreement-categories")
                                    .queryParam("ruleSystemCode", normalize(ruleSystemCode))
                                    .queryParam("agreementCode", normalize(agreementCode));
                            if (referenceDate != null) {
                                builder = builder.queryParam("referenceDate", referenceDate);
                            }
                            return builder.build();
                        })
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<RawCatalogItem>>() { })
                        .block());
    }

    public List<CatalogOption> getContractSubtypes(String ruleSystemCode, String contractTypeCode) {
        return getContractSubtypes(ruleSystemCode, contractTypeCode, null);
    }

    /** Mismo motivo que en las categorias: la relacion tipo-subtipo tiene su propia vigencia. */
    public List<CatalogOption> getContractSubtypes(
            String ruleSystemCode,
            String contractTypeCode,
            LocalDate referenceDate
    ) {
        return dependientesEnCache("CONTRACT_SUBTYPE", ruleSystemCode, contractTypeCode, referenceDate, () ->
                webClient.get()
                        .uri(uriBuilder -> {
                            var builder = uriBuilder
                                    .path("/contract-catalog/contract-subtypes")
                                    .queryParam("ruleSystemCode", normalize(ruleSystemCode))
                                    .queryParam("contractTypeCode", normalize(contractTypeCode));
                            if (referenceDate != null) {
                                builder = builder.queryParam("referenceDate", referenceDate);
                            }
                            return builder.build();
                        })
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<RawCatalogItem>>() { })
                        .block());
    }

    /**
     * Cachea por (tipo, padre, fecha). Sin esto seria una llamada por evento y
     * las fechas se repiten mucho entre empleados.
     *
     * Cuando se pide con fecha, una lista vacia es una respuesta legitima: ese
     * dia no habia nada vigente. Solo se considera error el vacio sin fecha,
     * que significa que el catalogo esta mal cargado.
     */
    private List<CatalogOption> dependientesEnCache(
            String entityTypeCode,
            String ruleSystemCode,
            String parentCode,
            LocalDate referenceDate,
            java.util.function.Supplier<List<RawCatalogItem>> consulta
    ) {
        String clave = entityTypeCode + "|" + normalize(ruleSystemCode)
                + "|" + normalize(parentCode) + "|" + referenceDate;

        return dependientesCache.computeIfAbsent(clave, ignorada ->
                mapDependentOptions(consulta.get(), entityTypeCode, referenceDate == null));
    }

    public List<CatalogOption> getWorkCentersByCompany(
            String ruleSystemCode,
            String companyCode,
            LocalDate referenceDate
    ) {
        WorkCentersByCompanyResponse response = webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/catalog-options/work-centers-by-company")
                            .queryParam("ruleSystemCode", normalize(ruleSystemCode))
                            .queryParam("companyCode", normalize(companyCode));
                    if (referenceDate != null) {
                        builder.queryParam("referenceDate", referenceDate);
                    }
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(WorkCentersByCompanyResponse.class)
                .block();

            List<WorkCenterByCompanyItem> items = response == null ? List.of() : response.items();
            return mapWorkCentersByCompany(items);
    }

    private List<CatalogOption> mapDirectOptions(List<RawCatalogItem> rawItems, String entityTypeCode) {
        List<CatalogOption> options = (rawItems == null ? List.<RawCatalogItem>of() : rawItems).stream()
                .filter(option -> option != null
                        && option.code() != null
                        && option.name() != null
                        && Boolean.TRUE.equals(option.active()))
                .map(option -> new CatalogOption(
                        normalize(option.code()),
                        option.name().trim(),
                        option.startDate(),
                        option.endDate()))
                .toList();

        if (options.isEmpty()) {
            throw new IllegalStateException("No catalog options found for " + entityTypeCode);
        }

        return options;
    }

    private List<CatalogOption> mapDependentOptions(List<RawCatalogItem> rawItems, String entityTypeCode) {
        return mapDependentOptions(rawItems, entityTypeCode, true);
    }

    private List<CatalogOption> mapDependentOptions(
            List<RawCatalogItem> rawItems,
            String entityTypeCode,
            boolean exigirNoVacio
    ) {
        List<CatalogOption> options = (rawItems == null ? List.<RawCatalogItem>of() : rawItems).stream()
                .filter(option -> option != null
                        && option.code() != null
                        && option.name() != null)
                .map(option -> new CatalogOption(
                        normalize(option.code()),
                        option.name().trim(),
                        option.startDate(),
                        option.endDate()))
                .toList();

        if (options.isEmpty() && exigirNoVacio) {
            throw new IllegalStateException("No catalog options found for " + entityTypeCode);
        }

        return options;
    }

    private List<CatalogOption> mapWorkCentersByCompany(List<WorkCenterByCompanyItem> rawItems) {
        List<CatalogOption> options = (rawItems == null ? List.<WorkCenterByCompanyItem>of() : rawItems).stream()
                .filter(option -> option != null
                        && option.code() != null
                        && option.name() != null)
                .map(option -> new CatalogOption(normalize(option.code()), option.name().trim()))
                .toList();

        if (options.isEmpty()) {
            throw new IllegalStateException("No catalog options found for WORK_CENTER_BY_COMPANY");
        }

        return options;
    }

    private Optional<CatalogFieldBinding> resolveBinding(String resourceCode, String fieldCode) {
        String normalizedResourceCode = normalizeResourceCode(resourceCode);
        String normalizedFieldCode = normalize(fieldCode);
        if (normalizedResourceCode == null || normalizedFieldCode == null) {
            return Optional.empty();
        }

        Map<String, CatalogFieldBinding> bindingsByField = bindingsCache.computeIfAbsent(
                normalizedResourceCode,
                this::loadBindingsByResource
        );
        CatalogFieldBinding binding = bindingsByField.get(normalizedFieldCode);
        if (binding == null || !binding.active() || !"DIRECT".equals(normalize(binding.catalogKind()))) {
            return Optional.empty();
        }
        return Optional.of(binding);
    }

    private Map<String, CatalogFieldBinding> loadBindingsByResource(String resourceCode) {
        try {
            CatalogBindingsByResourceResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/catalog-bindings/{resourceCode}")
                            .build(resourceCode))
                    .retrieve()
                    .bodyToMono(CatalogBindingsByResourceResponse.class)
                    .block();

            List<CatalogFieldBinding> fields = response == null ? List.of() : response.fields();
            Map<String, CatalogFieldBinding> bindingsByField = new ConcurrentHashMap<>();
            for (CatalogFieldBinding field : fields) {
                if (field == null || field.fieldCode() == null) {
                    continue;
                }
                bindingsByField.put(normalize(field.fieldCode()), field);
            }
            return bindingsByField;
        } catch (WebClientResponseException.NotFound ex) {
            return Map.of();
        } catch (WebClientResponseException ex) {
            return Map.of();
        }
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private static String normalizeResourceCode(String value) {
        return value == null ? null : value.trim();
    }

    private record DirectCatalogOptionsResponse(List<RawCatalogItem> items) {
    }

    private record RawCatalogItem(
            String code,
            String name,
            Boolean active,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    private record CatalogBindingsByResourceResponse(List<CatalogFieldBinding> fields) {
    }

        private record WorkCentersByCompanyResponse(
            String ruleSystemCode,
            String companyCode,
            LocalDate referenceDate,
                List<WorkCenterByCompanyItem> items
        ) {
        }

            private record WorkCenterByCompanyItem(String code, String name) {
            }

    private record CatalogFieldBinding(
            String fieldCode,
            String catalogKind,
            String ruleEntityTypeCode,
            boolean active
    ) {
    }
}
