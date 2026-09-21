package com.b4rrhh.workforceloader.application;

/**
 * El regimen de pagas extras de un tramo de presencia, al reves del que dice el convenio
 * ({@code workforce-loader#13}).
 *
 * <p>Lleva el numero de la ocurrencia porque esto <b>corrige</b> la fila que ya existe, no anade
 * una. La contratacion crea la primera fila copiando el testigo del convenio ({@code
 * b4rrhh/backend#118}) y esto le da la vuelta desde el mismo dia: anadir otra fila con la misma
 * fecha de inicio no seria un alta, seria la correccion de esa — el backend lo dice con
 * {@code EXTRA_PAYMENT_REGIME_IS_A_CORRECTION} — y anadirla un dia despues dejaria un tramo de un
 * dia que no significa nada.
 */
public record ExtraPaymentRegimeChangeEventPayload(
        int extraPaymentRegimeNumber,
        boolean prorated
) implements MutationEventPayload {
}
