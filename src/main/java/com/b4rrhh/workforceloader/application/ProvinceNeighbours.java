package com.b4rrhh.workforceloader.application;

import java.util.List;
import java.util.Map;

/**
 * Las provincias de al lado de las que tienen centros en la demo (workforce-loader#17): ahí vive la
 * gente que no vive en la provincia de su centro. El territorio del backend no sabe de vecindad, y el
 * loader sólo la necesita para un reparto verosímil, así que está aquí, escrita a mano y por código
 * INE, en orden de código.
 *
 * <p>Sólo las provincias de los centros de hoy: Madrid, Barcelona y Valencia. Un centro en otra
 * provincia no rompe nada: su gente vive toda en ella, y el loader lo avisa al arrancar.</p>
 */
final class ProvinceNeighbours {

    private static final Map<String, List<String>> NEIGHBOURS = Map.of(
            // Barcelona: Girona, Lleida, Tarragona.
            "08", List.of("17", "25", "43"),
            // Madrid: Ávila, Cuenca, Guadalajara, Segovia, Toledo.
            "28", List.of("05", "16", "19", "40", "45"),
            // Valencia: Albacete, Alicante, Castellón, Cuenca, Teruel.
            "46", List.of("02", "03", "12", "16", "44"));

    private ProvinceNeighbours() {
    }

    static List<String> of(String provinceCode) {
        return NEIGHBOURS.getOrDefault(provinceCode, List.of());
    }
}
