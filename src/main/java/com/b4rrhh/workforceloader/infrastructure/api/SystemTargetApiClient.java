package com.b4rrhh.workforceloader.infrastructure.api;

import com.b4rrhh.workforceloader.infrastructure.api.dto.SystemTargetResponse;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Le pregunta al backend a que base escribe.
 *
 * Todo lo que sale de aqui que no sea una respuesta buena es una
 * BackendTargetMismatchException: si no se puede averiguar con quien se esta
 * hablando, no se escribe. Un 404 tiene ademas nombre propio, porque es el
 * caso de aquella noche —el backend del 8080 era anterior a este endpoint— y
 * conviene que el mensaje lo diga en vez de dejar un "404" suelto.
 */
@Component
public class SystemTargetApiClient {

    private final LoaderProperties properties;
    private final WebClient webClient;

    public SystemTargetApiClient(LoaderProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        WebClient.Builder builder = webClientBuilder
                .baseUrl(properties.getBackend().getBaseUrl());

        // Misma normalizacion que los otros dos clientes. Repetida y no
        // extraida a proposito: unificar como se monta el WebClient es una
        // limpieza aparte, y no la mete este issue.
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

    public SystemTargetResponse fetch() {
        String baseUrl = properties.getBackend().getBaseUrl();
        try {
            SystemTargetResponse response = webClient.get()
                    .uri("/system/target")
                    .retrieve()
                    .bodyToMono(SystemTargetResponse.class)
                    .block();

            if (response == null || response.database() == null || response.database().isBlank()) {
                throw new BackendTargetMismatchException(
                        "The backend at " + baseUrl + " answered GET /system/target without saying"
                                + " which database it writes to. Refusing to write.");
            }

            return response;
        } catch (WebClientResponseException.NotFound ex) {
            throw new BackendTargetMismatchException(
                    "The backend at " + baseUrl + " does not serve GET /system/target."
                            + " It is not a B4RRHH backend, or it is older than the commit that added"
                            + " that endpoint. Either way this run cannot check which database it would"
                            + " write to, so it does not write. Refusing to write.",
                    ex);
        } catch (WebClientResponseException ex) {
            throw new BackendTargetMismatchException(
                    "The backend at " + baseUrl + " rejected GET /system/target: status="
                            + ex.getStatusCode() + ", body=" + ex.getResponseBodyAsString()
                            + ". Refusing to write.",
                    ex);
        } catch (BackendTargetMismatchException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BackendTargetMismatchException(
                    "Cannot ask the backend at " + baseUrl + " which database it writes to: "
                            + ex.getMessage() + ". Refusing to write.",
                    ex);
        }
    }
}
