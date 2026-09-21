package com.b4rrhh.workforceloader.application;

import java.time.LocalDate;

/**
 * La ventana en la que se generan altas, con sus dos extremos ya resueltos
 * ({@code workforce-loader#1} y {@code workforce-loader#11}).
 *
 * <p>Ninguno de los dos sale del reloj y ninguno esta escrito a mano en el {@code yml}: el
 * principio lo dice el catalogo —desde cuando hay convenio, categoria, tipo de contrato y
 * subtipo vigentes a la vez— y el final sale del periodo que la demo calcula. Los dos se pueden
 * fijar a mano, y entonces manda lo fijado y el loader avisa si difiere.
 *
 * @param desde el primer dia que puede llevar un alta
 * @param hasta el ultimo
 */
public record HireWindow(LocalDate desde, LocalDate hasta) {
}
