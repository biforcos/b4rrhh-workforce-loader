package com.b4rrhh.workforceloader.infrastructure.api;

import com.b4rrhh.workforceloader.application.EmployeePhotoUploader;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.util.Map;

/**
 * La foto de un empleado, subida por el mismo camino que la pantalla (workforce-loader#19): el
 * backend da una URL prefirmada, el fichero va por {@code PUT} directo al almacén, y luego se le
 * confirma al backend la clave para que la apunte en el empleado.
 *
 * <p>Son dos clientes y no uno a propósito. El del backend lleva el token; el del almacén no puede
 * llevarlo: la URL prefirmada ya trae su firma, y una cabecera {@code Authorization} de más hace
 * que el almacén la rechace.
 */
@Component
public class EmployeePhotoApiClient implements EmployeePhotoUploader {

    private final BackendTargetGuard backendTargetGuard;
    private final WebClient backend;
    private final WebClient almacen;

    public EmployeePhotoApiClient(
            LoaderProperties properties,
            BackendTargetGuard backendTargetGuard,
            WebClient.Builder webClientBuilder
    ) {
        this.backendTargetGuard = backendTargetGuard;
        WebClient.Builder builder = webClientBuilder.clone()
                .baseUrl(properties.getBackend().getBaseUrl());
        String token = normalizeToken(properties.getBackend().getAuthToken());
        if (token != null) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, token);
        }
        this.backend = builder.build();
        this.almacen = webClientBuilder.clone().build();
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

    @Override
    public void upload(String ruleSystemCode, String employeeTypeCode, String employeeNumber, byte[] jpeg) {
        // Antes de pedir la URL, que ya es una escritura: el backend no apunta nada todavía, pero
        // el fichero que viene detrás sí acaba en el almacén de esa base (workforce-loader#8).
        backendTargetGuard.verifyBeforeWriting();
        String operacion = "foto de " + ruleSystemCode + "/" + employeeTypeCode + "/" + employeeNumber;
        try {
            JsonNode subida = backend.post()
                    .uri("/employees/{ruleSystemCode}/{employeeTypeCode}/{employeeNumber}/photo/upload-url",
                            ruleSystemCode, employeeTypeCode, employeeNumber)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            String uploadUrl = subida == null ? null : subida.path("uploadUrl").asText(null);
            String objectKey = subida == null ? null : subida.path("objectKey").asText(null);
            if (uploadUrl == null || objectKey == null) {
                throw new IllegalStateException("el backend no ha dado URL de subida para la " + operacion);
            }

            // URI y no String: la firma va en la query, y como plantilla se volvería a codificar.
            almacen.put()
                    .uri(URI.create(uploadUrl))
                    .contentType(MediaType.IMAGE_JPEG)
                    .bodyValue(jpeg)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            backend.put()
                    .uri("/employees/{ruleSystemCode}/{employeeTypeCode}/{employeeNumber}/photo",
                            ruleSystemCode, employeeTypeCode, employeeNumber)
                    .bodyValue(Map.of("objectKey", objectKey))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException ex) {
            throw new IllegalStateException("HTTP " + ex.getStatusCode() + " subiendo la " + operacion
                    + ": " + ex.getResponseBodyAsString(), ex);
        }
    }
}
