package com.b4rrhh.workforceloader.infrastructure.api;

import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ninguna escritura sale del cliente si la guarda no ha dado el visto bueno
 * (workforce-loader#8).
 *
 * Se cuenta lo que sale por el WebClient, no lo que devuelve el metodo: el
 * criterio del issue es que pare antes de la primera alta, y eso se demuestra
 * con un cero, no con una excepcion.
 *
 * La corrida de estos tests no declara a que base espera escribir, que es una
 * de las dos formas que tiene la guarda de negarse. Sirve igual: lo que se
 * mira aqui es que nadie escribe cuando se niega, no por que se nego.
 */
class B4rrhhLifecycleClientTest {

    @Test
    void doesNotSendTheHireWhenTheRunWasNeverChecked() {
        AtomicInteger requests = new AtomicInteger();
        B4rrhhLifecycleClient client = clientCounting(requests);

        assertThatThrownBy(() -> client.hire(null))
                .isInstanceOf(BackendTargetMismatchException.class);
        assertThat(requests).hasValue(0);
    }

    @Test
    void doesNotSendTheCallsThatGoThroughTheSharedWritePathEither() {
        AtomicInteger requests = new AtomicInteger();
        B4rrhhLifecycleClient client = clientCounting(requests);

        assertThatThrownBy(() -> client.createAddress("ESP", "INTERNAL", "EMP000001", null))
                .isInstanceOf(BackendTargetMismatchException.class);
        assertThat(requests).hasValue(0);
    }

    private static B4rrhhLifecycleClient clientCounting(AtomicInteger requests) {
        LoaderProperties properties = new LoaderProperties();
        properties.getBackend().setBaseUrl("http://localhost:8080/api");

        ExchangeFunction counting = request -> {
            requests.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        };

        BackendTargetGuard guard = new BackendTargetGuard(
                properties,
                new SystemTargetApiClient(properties, WebClient.builder()));

        return new B4rrhhLifecycleClient(
                properties,
                guard,
                WebClient.builder().exchangeFunction(counting));
    }
}
