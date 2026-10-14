package com.b4rrhh.workforceloader.infrastructure.api;

import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lo que el loader lee del territorio para dar direcciones de verdad (workforce-loader#17): los tipos
 * de vía del catálogo, el municipio que sugiere un código postal y la dirección de un centro. Sólo
 * lee: el maestro {@code geo} lo cargan las migraciones del backend, y el loader no escribe en él.
 */
@Component
public class TerritoryApiClient {

    /** El municipio que el maestro da a un código postal que existe. */
    public record PostalCodeMunicipality(String postalCode, String provinceCode, String municipalityCode,
                                         String municipalityName) {
    }

    /** Lo que el loader necesita de la dirección de un centro: dónde está y si está normalizada. */
    public record WorkCenterLocation(String workCenterCode, String countryCode, String provinceCode,
                                     String municipalityCode, boolean normalized) {
    }

    private final WebClient webClient;

    public TerritoryApiClient(LoaderProperties properties, WebClient.Builder webClientBuilder) {
        WebClient.Builder builder = webClientBuilder
                .baseUrl(properties.getBackend().getBaseUrl());

        // Misma normalizacion que los otros clientes; unificarla es una limpieza aparte.
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

    /** Los códigos de los tipos de vía de un país: el territorio se lee por país (b4rrhh/backend#169). */
    public List<String> streetTypeCodes(String countryCode) {
        JsonNode body = webClient.get()
                .uri("/territory/{countryCode}/street-types", countryCode)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        List<String> codes = new ArrayList<>();
        if (body != null) {
            body.forEach(node -> codes.add(node.path("code").asText()));
        }
        return codes;
    }

    /**
     * El municipio que el maestro sugiere para un código postal español, si el código existe y
     * sugiere alguno. Un código que no existe, o que existe sin sugerencia, no da nada.
     */
    public Optional<PostalCodeMunicipality> postalCodeMunicipality(String countryCode, String postalCode) {
        JsonNode body = webClient.get()
                .uri("/territory/{countryCode}/postal-codes/{postalCode}", countryCode, postalCode)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        if (body == null) {
            return Optional.empty();
        }
        JsonNode municipality = body.path("suggestedMunicipality");
        if (municipality.isMissingNode() || municipality.isNull()) {
            return Optional.empty();
        }
        return Optional.of(new PostalCodeMunicipality(
                postalCode,
                body.path("province").path("code").asText(null),
                municipality.path("code").asText(null),
                municipality.path("name").asText(null)));
    }

    /** Dónde está un centro, según su dirección en el backend; vacío si el centro no existe. */
    public Optional<WorkCenterLocation> workCenterLocation(String ruleSystemCode, String workCenterCode) {
        try {
            JsonNode body = webClient.get()
                    .uri("/work-centers/{ruleSystemCode}/{workCenterCode}", ruleSystemCode, workCenterCode)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if (body == null) {
                return Optional.empty();
            }
            JsonNode address = body.path("address");
            return Optional.of(new WorkCenterLocation(
                    workCenterCode,
                    address.path("countryCode").asText(null),
                    address.path("subdivisionCode").asText(null),
                    address.path("municipalityCode").asText(null),
                    address.path("normalized").asBoolean(false)));
        } catch (WebClientResponseException.NotFound ex) {
            return Optional.empty();
        }
    }
}
