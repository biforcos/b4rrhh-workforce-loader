package com.b4rrhh.workforceloader.infrastructure.api;

import com.b4rrhh.workforceloader.infrastructure.api.dto.SystemTargetResponse;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprueba con quien esta hablando el loader antes de dejarle escribir.
 *
 * Contra el estado, no contra el codigo de respuesta. Un token bueno del
 * backend equivocado devuelve 200 igual que el bueno del correcto: la pregunta
 * "me han autenticado" no es la pregunta "estoy hablando con quien creo"
 * (workforce-loader#8).
 *
 * Se llama desde dos sitios y por dos motivos distintos:
 *
 *  - Al arrancar la corrida, antes de generar nada. No solo antes de la
 *    primera escritura: antes de leer los catalogos, porque un backend
 *    equivocado tambien sirve los codigos con los que se construyen los
 *    payloads.
 *  - Desde el cliente de escritura, antes de la primera escritura y cada
 *    loader.backend.recheck-every-writes escrituras. Un backend que se
 *    sustituye a mitad de corrida —que es lo que pasa cuando uno mata el del
 *    8080 y arranca el suyo— se caza en la siguiente ronda.
 *
 * En la revision de media corrida se compara la identidad contra lo que se vio
 * al empezar, no contra lo declarado: asi tambien se caza un cambio en un campo
 * que la corrida no declaro. El recuento de empleados no se revisa, porque
 * crece a proposito.
 */
@Component
public class BackendTargetGuard {

    private static final Logger log = LoggerFactory.getLogger(BackendTargetGuard.class);

    private final LoaderProperties properties;
    private final SystemTargetApiClient systemTargetApiClient;

    private final AtomicInteger writesSinceLastCheck = new AtomicInteger();
    private volatile SystemTargetResponse verified;

    public BackendTargetGuard(LoaderProperties properties, SystemTargetApiClient systemTargetApiClient) {
        this.properties = properties;
        this.systemTargetApiClient = systemTargetApiClient;
    }

    /** Antes de generar, de leer catalogos y de escribir. */
    public void verifyBeforeTheRun() {
        LoaderProperties.Expected expected = properties.getBackend().getExpected();
        requireDeclared(expected);

        SystemTargetResponse target = systemTargetApiClient.fetch();
        checkDatabase(expected, target);
        checkSchemaVersion(expected, target);
        checkEmployees(expected, target);

        verified = target;
        writesSinceLastCheck.set(0);
        log.info(
                "Backend target checked: database={}, schemaVersion={}, employees={}",
                target.database(),
                target.schemaVersion(),
                target.employees());
    }

    /** Antes de cada escritura. Barata salvo una de cada recheck-every-writes. */
    public void verifyBeforeWriting() {
        if (verified == null) {
            // Nadie deberia llegar aqui sin haber pasado por verifyBeforeTheRun,
            // pero si alguien anade una ruta de escritura que se salta el
            // arranque, se comprueba igual antes de escribir.
            verifyBeforeTheRun();
            return;
        }

        int recheckEveryWrites = properties.getBackend().getRecheckEveryWrites();
        if (writesSinceLastCheck.incrementAndGet() < recheckEveryWrites) {
            return;
        }
        writesSinceLastCheck.set(0);

        SystemTargetResponse now = systemTargetApiClient.fetch();
        if (!Objects.equals(verified.database(), now.database())
                || !Objects.equals(verified.schemaVersion(), now.schemaVersion())) {
            throw new BackendTargetMismatchException(
                    "The backend changed under this run. It was " + describe(verified)
                            + " and now it is " + describe(now) + " at " + baseUrl()
                            + ". Refusing to write.");
        }
    }

    private void requireDeclared(LoaderProperties.Expected expected) {
        if (expected.getDatabase() == null || expected.getDatabase().isBlank()) {
            throw new BackendTargetMismatchException(
                    "loader.backend.expected.database is not declared, so this run cannot be checked"
                            + " and does not start. Declare the database this run is meant to write to,"
                            + " as host:port/name, for example localhost:5432/b4rrhh_wl7. Ask the backend"
                            + " at " + baseUrl() + " with GET /system/target if you are not sure.");
        }
        if (expected.getEmployees() == null) {
            throw new BackendTargetMismatchException(
                    "loader.backend.expected.employees is not declared, so this run cannot be checked"
                            + " and does not start. A run against a fresh database expects 0.");
        }
    }

    private void checkDatabase(LoaderProperties.Expected expected, SystemTargetResponse target) {
        if (!expected.getDatabase().trim().equals(target.database())) {
            throw new BackendTargetMismatchException(
                    "Wrong backend. This run expects to write to " + expected.getDatabase().trim()
                            + " and the backend at " + baseUrl() + " writes to " + target.database()
                            + ". Nothing has been written.");
        }
    }

    private void checkSchemaVersion(LoaderProperties.Expected expected, SystemTargetResponse target) {
        String expectedSchemaVersion = expected.getSchemaVersion();
        if (expectedSchemaVersion == null || expectedSchemaVersion.isBlank()) {
            return;
        }
        if (!expectedSchemaVersion.trim().equals(target.schemaVersion())) {
            throw new BackendTargetMismatchException(
                    "Wrong schema. This run expects migration " + expectedSchemaVersion.trim()
                            + " and " + target.database() + " is at " + target.schemaVersion()
                            + ". Nothing has been written.");
        }
    }

    private void checkEmployees(LoaderProperties.Expected expected, SystemTargetResponse target) {
        if (expected.getEmployees() != target.employees()) {
            throw new BackendTargetMismatchException(
                    "The database is not in the state this run expects. It expects "
                            + expected.getEmployees() + " employees in " + target.database()
                            + " and there are " + target.employees()
                            + ". Nothing has been written.");
        }
    }

    private String baseUrl() {
        return properties.getBackend().getBaseUrl();
    }

    private static String describe(SystemTargetResponse target) {
        return target.database() + " (migration " + target.schemaVersion() + ")";
    }
}
