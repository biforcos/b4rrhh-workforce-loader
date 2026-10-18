package com.b4rrhh.workforceloader.infrastructure.api;

import com.b4rrhh.workforceloader.application.EmployeePhotoUploader;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * La foto de un empleado, subida por el mismo camino que la pantalla: un {@code PUT} con el JPEG a la
 * API, que lo guarda y apunta la clave (b4rrhh/backend#177).
 *
 * <p>Hasta el #177 eran tres viajes —URL prefirmada, {@code PUT} directo al almacén sin token y
 * confirmación— y este cliente llevaba dos {@code WebClient}. La URL prefirmada nombraba el almacén tal
 * como lo veía el backend, que desde fuera de su máquina no era ninguno.
 */
@Component
public class EmployeePhotoApiClient implements EmployeePhotoUploader {

    private final BackendTargetGuard backendTargetGuard;
    private final WebClient backend;

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
        backendTargetGuard.verifyBeforeWriting();
        try {
            backend.put()
                    .uri("/employees/{ruleSystemCode}/{employeeTypeCode}/{employeeNumber}/photo",
                            ruleSystemCode, employeeTypeCode, employeeNumber)
                    .contentType(MediaType.IMAGE_JPEG)
                    .bodyValue(jpeg)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException ex) {
            throw new IllegalStateException("HTTP " + ex.getStatusCode() + " subiendo la foto de "
                    + ruleSystemCode + "/" + employeeTypeCode + "/" + employeeNumber
                    + ": " + ex.getResponseBodyAsString(), ex);
        }
    }
}
