package com.b4rrhh.workforceloader.infrastructure.api;

import com.b4rrhh.workforceloader.infrastructure.api.dto.BulkFinalizePayrollRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.BulkFinalizePayrollResponse;
import com.b4rrhh.workforceloader.infrastructure.api.dto.LaunchPayrollCalculationRequest;
import com.b4rrhh.workforceloader.infrastructure.api.dto.PayrollCalculationRunResponse;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/**
 * Los tres verbos del ciclo de nomina: lanzar, mirar como va y cerrar ({@code workforce-loader#16}).
 *
 * <p>Aparte de {@link B4rrhhLifecycleClient} y no dentro, y no es por tamano: aquel habla del
 * <b>empleado</b> —altas, ceses, ausencias, entradas— y este habla del <b>mes</b>. Son dos
 * vocabularios y mezclarlos dejaria una clase que no se puede leer de un tiron.
 *
 * <p>Lo que si comparten es la guarda del {@code workforce-loader#8}: un backend equivocado
 * contesta 200 igual que el bueno, y lanzar un calculo en la base que no es cuesta tanto como
 * escribir un alta.
 */
@Component
public class PayrollCycleApiClient {

    /**
     * El cierre en masa de mil recibos tarda minutos: archiva un PDF por recibo. El tiempo de
     * espera por omision de {@code WebClient} no llega, y lo que se ve entonces no es un fallo del
     * backend sino un {@code ReadTimeout} del cliente sobre una operacion que <b>si</b> se esta
     * haciendo. Eso es peor que tardar: deja el loader creyendo que fallo algo que va bien.
     */
    private static final Duration ESPERA_LARGA = Duration.ofMinutes(30);

    private final LoaderProperties properties;
    private final BackendTargetGuard backendTargetGuard;
    private final WebClient webClient;

    public PayrollCycleApiClient(
            LoaderProperties properties,
            BackendTargetGuard backendTargetGuard,
            WebClient.Builder webClientBuilder
    ) {
        this.properties = properties;
        this.backendTargetGuard = backendTargetGuard;
        WebClient.Builder builder = webClientBuilder.baseUrl(properties.getBackend().getBaseUrl());
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
        return trimmed.regionMatches(true, 0, "Bearer ", 0, 7) ? trimmed : "Bearer " + trimmed;
    }

    /**
     * Pide el calculo de un mes. Contesta en milisegundos con la identidad de la ejecucion; el
     * calculo sigue por su cuenta (ADR-060), asi que quien llama tiene que sondear.
     */
    public PayrollCalculationRunResponse launch(LaunchPayrollCalculationRequest request) {
        backendTargetGuard.verifyBeforeWriting();
        try {
            PayrollCalculationRunResponse response = webClient.post()
                    .uri("/payroll/calculation-runs/launch")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(PayrollCalculationRunResponse.class)
                    .block(ESPERA_LARGA);
            if (response == null) {
                throw new RuntimeException("El lanzamiento del calculo contesto un cuerpo vacio");
            }
            return response;
        } catch (WebClientResponseException ex) {
            throw new RuntimeException("HTTP al lanzar el calculo: status=" + ex.getStatusCode()
                    + ", body=" + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            throw new RuntimeException("Error al lanzar el calculo: " + ex.getMessage(), ex);
        }
    }

    /** Como va una ejecucion. Es una lectura: no pasa por la guarda de escritura. */
    public PayrollCalculationRunResponse getRun(long runId) {
        try {
            PayrollCalculationRunResponse response = webClient.get()
                    .uri("/payroll/calculation-runs/{runId}", runId)
                    .retrieve()
                    .bodyToMono(PayrollCalculationRunResponse.class)
                    .block(ESPERA_LARGA);
            if (response == null) {
                throw new RuntimeException("La ejecucion " + runId + " contesto un cuerpo vacio");
            }
            return response;
        } catch (WebClientResponseException ex) {
            throw new RuntimeException("HTTP al leer la ejecucion " + runId + ": status="
                    + ex.getStatusCode() + ", body=" + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            throw new RuntimeException("Error al leer la ejecucion " + runId + ": " + ex.getMessage(), ex);
        }
    }

    /** Cierra en masa los recibos del mes. Es sincrono y tarda: archiva un PDF por recibo. */
    public BulkFinalizePayrollResponse bulkFinalize(BulkFinalizePayrollRequest request) {
        backendTargetGuard.verifyBeforeWriting();
        try {
            BulkFinalizePayrollResponse response = webClient.post()
                    .uri("/payrolls/finalize-bulk")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(BulkFinalizePayrollResponse.class)
                    .block(ESPERA_LARGA);
            if (response == null) {
                throw new RuntimeException("El cierre en masa contesto un cuerpo vacio");
            }
            return response;
        } catch (WebClientResponseException ex) {
            throw new RuntimeException("HTTP al cerrar en masa: status=" + ex.getStatusCode()
                    + ", body=" + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            throw new RuntimeException("Error al cerrar en masa: " + ex.getMessage(), ex);
        }
    }

    public LoaderProperties properties() {
        return properties;
    }
}
