package com.b4rrhh.workforceloader.infrastructure.api;

import com.b4rrhh.workforceloader.infrastructure.api.dto.SystemTargetResponse;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La guarda que impide sembrar en la base equivocada (workforce-loader#8).
 *
 * Los tres enganos de aquella noche fueron que la base existia y estaba
 * migrada, que el puerto contestaba, y que el token era valido. Ninguno de los
 * tres se comprueba aqui, porque ninguno de los tres decia la verdad sobre con
 * quien se estaba hablando.
 */
class BackendTargetGuardTest {

    private static final SystemTargetResponse FRESH_WL7 =
            new SystemTargetResponse("localhost:5432/b4rrhh_wl7", "120", 0);

    @Test
    void doesNotStartWhenTheRunDidNotSayWhichDatabaseItExpects() {
        LoaderProperties properties = properties(null, 0L, null);

        assertThatThrownBy(guard(properties, FRESH_WL7)::verifyBeforeTheRun)
                .isInstanceOf(BackendTargetMismatchException.class)
                .hasMessageContaining("loader.backend.expected.database is not declared");
    }

    @Test
    void doesNotStartWhenTheRunDidNotSayHowManyEmployeesItExpects() {
        LoaderProperties properties = properties("localhost:5432/b4rrhh_wl7", null, null);

        assertThatThrownBy(guard(properties, FRESH_WL7)::verifyBeforeTheRun)
                .isInstanceOf(BackendTargetMismatchException.class)
                .hasMessageContaining("loader.backend.expected.employees is not declared");
    }

    @Test
    void namesTheDatabaseItExpectedAndTheOneItFound() {
        LoaderProperties properties = properties("localhost:5432/b4rrhh_wl7", 0L, null);
        SystemTargetResponse theOldBackend = new SystemTargetResponse("localhost:5432/b4rrhh", "115", 1000);

        assertThatThrownBy(guard(properties, theOldBackend)::verifyBeforeTheRun)
                .isInstanceOf(BackendTargetMismatchException.class)
                .hasMessageContaining("localhost:5432/b4rrhh_wl7")
                .hasMessageContaining("localhost:5432/b4rrhh")
                .hasMessageContaining("Nothing has been written");
    }

    @Test
    void refusesADatabaseThatIsNotInTheStateTheRunExpects() {
        LoaderProperties properties = properties("localhost:5432/b4rrhh_wl7", 0L, null);
        SystemTargetResponse alreadySeeded = new SystemTargetResponse("localhost:5432/b4rrhh_wl7", "120", 1000);

        assertThatThrownBy(guard(properties, alreadySeeded)::verifyBeforeTheRun)
                .isInstanceOf(BackendTargetMismatchException.class)
                .hasMessageContaining("expects 0 employees")
                .hasMessageContaining("there are 1000");
    }

    @Test
    void refusesASchemaThatIsNotTheDeclaredOne() {
        LoaderProperties properties = properties("localhost:5432/b4rrhh_wl7", 0L, "120");
        SystemTargetResponse behind = new SystemTargetResponse("localhost:5432/b4rrhh_wl7", "115", 0);

        assertThatThrownBy(guard(properties, behind)::verifyBeforeTheRun)
                .isInstanceOf(BackendTargetMismatchException.class)
                .hasMessageContaining("expects migration 120")
                .hasMessageContaining("is at 115");
    }

    @Test
    void letsTheRunThroughWhenEverythingMatches() {
        LoaderProperties properties = properties("localhost:5432/b4rrhh_wl7", 0L, "120");

        guard(properties, FRESH_WL7).verifyBeforeTheRun();
    }

    @Test
    void asksAgainOnlyEveryNWritesAndNotOnEveryOne() {
        LoaderProperties properties = properties("localhost:5432/b4rrhh_wl7", 0L, null);
        properties.getBackend().setRecheckEveryWrites(5);
        FixedSystemTargetApiClient backend = new FixedSystemTargetApiClient(properties, FRESH_WL7);
        BackendTargetGuard guard = new BackendTargetGuard(properties, backend);

        guard.verifyBeforeTheRun();
        for (int write = 0; write < 4; write++) {
            guard.verifyBeforeWriting();
        }
        assertThat(backend.calls).isEqualTo(1);

        guard.verifyBeforeWriting();
        assertThat(backend.calls).isEqualTo(2);
    }

    @Test
    void catchesABackendThatIsSwappedHalfwayThroughTheRun() {
        LoaderProperties properties = properties("localhost:5432/b4rrhh_wl7", 0L, null);
        properties.getBackend().setRecheckEveryWrites(2);
        FixedSystemTargetApiClient backend = new FixedSystemTargetApiClient(properties, FRESH_WL7);
        BackendTargetGuard guard = new BackendTargetGuard(properties, backend);

        guard.verifyBeforeTheRun();
        guard.verifyBeforeWriting();

        // Alguien mato el backend del 8080 y arranco otro contra otra base.
        backend.response = new SystemTargetResponse("localhost:5432/b4rrhh", "115", 250);

        assertThatThrownBy(guard::verifyBeforeWriting)
                .isInstanceOf(BackendTargetMismatchException.class)
                .hasMessageContaining("changed under this run")
                .hasMessageContaining("localhost:5432/b4rrhh_wl7")
                .hasMessageContaining("localhost:5432/b4rrhh");
    }

    @Test
    void doesNotComplainOnTheRecheckJustBecauseTheEmployeesGrew() {
        LoaderProperties properties = properties("localhost:5432/b4rrhh_wl7", 0L, null);
        properties.getBackend().setRecheckEveryWrites(1);
        FixedSystemTargetApiClient backend = new FixedSystemTargetApiClient(properties, FRESH_WL7);
        BackendTargetGuard guard = new BackendTargetGuard(properties, backend);

        guard.verifyBeforeTheRun();
        backend.response = new SystemTargetResponse("localhost:5432/b4rrhh_wl7", "120", 340);

        guard.verifyBeforeWriting();
    }

    @Test
    void doesNotLetAWriteThroughWhenTheRunNeverChecked() {
        LoaderProperties properties = properties(null, null, null);

        assertThatThrownBy(guard(properties, FRESH_WL7)::verifyBeforeWriting)
                .isInstanceOf(BackendTargetMismatchException.class);
    }

    private static LoaderProperties properties(String database, Long employees, String schemaVersion) {
        LoaderProperties properties = new LoaderProperties();
        properties.getBackend().setBaseUrl("http://localhost:8080/api");
        properties.getBackend().getExpected().setDatabase(database);
        properties.getBackend().getExpected().setEmployees(employees);
        properties.getBackend().getExpected().setSchemaVersion(schemaVersion);
        return properties;
    }

    private static BackendTargetGuard guard(LoaderProperties properties, SystemTargetResponse response) {
        return new BackendTargetGuard(properties, new FixedSystemTargetApiClient(properties, response));
    }

    static final class FixedSystemTargetApiClient extends SystemTargetApiClient {

        SystemTargetResponse response;
        int calls;

        FixedSystemTargetApiClient(LoaderProperties properties, SystemTargetResponse response) {
            super(properties, WebClient.builder());
            this.response = response;
        }

        @Override
        public SystemTargetResponse fetch() {
            calls++;
            return response;
        }
    }
}
