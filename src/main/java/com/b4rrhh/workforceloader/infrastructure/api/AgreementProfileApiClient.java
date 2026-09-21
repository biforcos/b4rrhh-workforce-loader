package com.b4rrhh.workforceloader.infrastructure.api;

import com.b4rrhh.workforceloader.application.AgreementExtraPaymentProrationSource;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El testigo de prorrateo del convenio ({@code b4rrhh/backend#117}).
 *
 * <p>Se pregunta al backend y no se escribe en la configuracion del loader a proposito: lo que el
 * convenio dice es del convenio, y una copia en el {@code application.yml} seria un segundo sitio
 * que mantener y que se quedaria atras el dia que alguien lo cambie por la aplicacion.
 *
 * <p>Se cachea por convenio: la plantilla entera comparte uno, asi que sin cache serian mil
 * peticiones identicas.
 */
@Component
public class AgreementProfileApiClient implements AgreementExtraPaymentProrationSource {

    private final WebClient webClient;
    private final Map<String, Boolean> prorationCache = new ConcurrentHashMap<>();

    public AgreementProfileApiClient(LoaderProperties properties, WebClient.Builder webClientBuilder) {
        WebClient.Builder builder = webClientBuilder
                .baseUrl(properties.getBackend().getBaseUrl());

        String token = normalizeToken(properties.getBackend().getAuthToken());
        if (token != null) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, token);
        }

        this.webClient = builder.build();
    }

    /** Si el convenio prorratea las pagas extras por defecto. */
    @Override
    public boolean proratesExtraPaymentsByDefault(String ruleSystemCode, String agreementCode) {
        return prorationCache.computeIfAbsent(
                ruleSystemCode + "/" + agreementCode,
                clave -> fetch(ruleSystemCode, agreementCode));
    }

    private boolean fetch(String ruleSystemCode, String agreementCode) {
        AgreementProfileResponse response = webClient.get()
                .uri("/agreements/{ruleSystemCode}/{agreementCode}/profile", ruleSystemCode, agreementCode)
                .retrieve()
                .bodyToMono(AgreementProfileResponse.class)
                .block();

        if (response == null || response.extraPaymentsProrated() == null) {
            throw new IllegalStateException(
                    "El convenio " + ruleSystemCode + "/" + agreementCode + " no publica su testigo de"
                            + " prorrateo de pagas extras. Sin el no se sabe que es «al reves del"
                            + " convenio»: comprueba que el backend trae la V144 (b4rrhh/backend#117).");
        }
        return response.extraPaymentsProrated();
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

    record AgreementProfileResponse(Boolean extraPaymentsProrated) {
    }
}
