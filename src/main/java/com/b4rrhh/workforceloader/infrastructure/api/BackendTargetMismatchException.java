package com.b4rrhh.workforceloader.infrastructure.api;

/**
 * El backend del otro lado no es el que la corrida espera, o no se ha podido
 * averiguar cual es. En los dos casos la corrida para y no escribe.
 *
 * No se captura en ninguna parte a proposito: sube hasta el arranque y mata el
 * proceso. Convertirla en un aviso seria devolver el fallo a lo que era —mil
 * altas en verde sobre la base equivocada (workforce-loader#8).
 */
public class BackendTargetMismatchException extends RuntimeException {

    public BackendTargetMismatchException(String message) {
        super(message);
    }

    public BackendTargetMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
