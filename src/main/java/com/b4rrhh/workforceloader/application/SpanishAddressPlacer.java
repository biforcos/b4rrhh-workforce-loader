package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.api.TerritoryApiClient;
import com.b4rrhh.workforceloader.infrastructure.api.TerritoryApiClient.PostalCodeMunicipality;
import com.b4rrhh.workforceloader.infrastructure.api.TerritoryApiClient.WorkCenterLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Dónde vive un empleado de un centro español (workforce-loader#17).
 *
 * <p>Hasta aquí el loader escribía «Valencia» con un CP de la provincia y tres cifras al azar: de
 * 1.341 direcciones españolas, 990 llevaban un CP que no existe (backend#155). Ahora la dirección sale
 * del maestro del territorio, por la API del backend:</p>
 *
 * <ul>
 *   <li><b>La provincia</b>: la del centro de alta en el {@value #OWN_PROVINCE_PERCENT} % de los
 *       casos, y si no una de las de al lado ({@link ProvinceNeighbours}).</li>
 *   <li><b>El CP</b>: uno que existe en el maestro y que tiene municipio sugerido, de esa provincia.
 *       Se descubren preguntando al backend por los mil CP de la provincia, una vez por corrida.</li>
 *   <li><b>El municipio</b>: el que el maestro sugiere para ese CP. La provincia, la del municipio.</li>
 * </ul>
 *
 * <p><b>Determinismo.</b> Las listas se ordenan por código y la elección sale de un azar propio de
 * la dirección ({@code locationSeed}, de la semilla, la fila y el orden de la dirección), nunca del
 * azar compartido de la simulación: colocar una dirección no mueve el resto de la plantilla.</p>
 */
@Component
public class SpanishAddressPlacer {

    private static final Logger log = LoggerFactory.getLogger(SpanishAddressPlacer.class);

    /** Quien vive en la provincia de su centro. */
    static final int OWN_PROVINCE_PERCENT = 85;

    public static final String SPAIN = "ESP";

    /** Dónde queda una dirección española. */
    public record Place(String provinceCode, String municipalityCode, String postalCode) {
    }

    private final TerritoryApiClient territory;
    private final Map<String, String> provinceByWorkCenter = new HashMap<>();
    private final Map<String, List<PostalCodeMunicipality>> postalCodesByProvince = new HashMap<>();
    private final Map<String, Set<String>> streetTypesByRuleSystem = new HashMap<>();

    public SpanishAddressPlacer(TerritoryApiClient territory) {
        this.territory = territory;
    }

    /**
     * El sitio de una dirección española de un empleado dado de alta en {@code workCenterCode}. Falla
     * si el centro no tiene una dirección española normalizada: sin ella no se sabe dónde está, y
     * colocar a su gente al azar es lo que este issue quita.
     */
    public Place place(String ruleSystemCode, String workCenterCode, long locationSeed) {
        String centerProvince = provinceOf(ruleSystemCode, workCenterCode);
        Random random = new Random(locationSeed);

        List<String> neighbours = ProvinceNeighbours.of(centerProvince);
        String province = !neighbours.isEmpty() && random.nextInt(100) >= OWN_PROVINCE_PERCENT
                ? neighbours.get(random.nextInt(neighbours.size()))
                : centerProvince;

        List<PostalCodeMunicipality> postalCodes = postalCodesOf(ruleSystemCode, province);
        PostalCodeMunicipality chosen = postalCodes.get(random.nextInt(postalCodes.size()));
        return new Place(chosen.provinceCode(), chosen.municipalityCode(), chosen.postalCode());
    }

    /** Un tipo de vía que el loader escribe tiene que estar en el catálogo: si no, la dirección saldría sin normalizar. */
    public void requireStreetType(String ruleSystemCode, String streetTypeCode) {
        Set<String> known = streetTypesByRuleSystem.computeIfAbsent(ruleSystemCode,
                key -> new TreeSet<>(territory.streetTypeCodes(key)));
        if (!known.contains(streetTypeCode)) {
            throw new IllegalStateException("El tipo de via " + streetTypeCode + " que usa el loader no esta en el catalogo de "
                    + ruleSystemCode + ": " + known);
        }
    }

    private String provinceOf(String ruleSystemCode, String workCenterCode) {
        return provinceByWorkCenter.computeIfAbsent(ruleSystemCode + "/" + workCenterCode, key -> {
            WorkCenterLocation location = territory.workCenterLocation(ruleSystemCode, workCenterCode)
                    .orElseThrow(() -> new IllegalStateException("No existe el centro " + key));
            if (!SPAIN.equals(location.countryCode()) || !location.normalized() || location.provinceCode() == null) {
                throw new IllegalStateException("El centro " + key + " no tiene una direccion espanola normalizada"
                        + " (pais=" + location.countryCode() + ", provincia=" + location.provinceCode()
                        + ", normalizada=" + location.normalized() + "): sin ella no se sabe donde vive su gente.");
            }
            if (ProvinceNeighbours.of(location.provinceCode()).isEmpty()) {
                log.warn("La provincia {} del centro {} no tiene vecinas apuntadas: su gente vivira toda en ella.",
                        location.provinceCode(), key);
            }
            return location.provinceCode();
        });
    }

    private List<PostalCodeMunicipality> postalCodesOf(String ruleSystemCode, String province) {
        return postalCodesByProvince.computeIfAbsent(province, key -> {
            List<PostalCodeMunicipality> found = new ArrayList<>();
            // En orden de código, y del maestro: la lista no depende ni del orden ni del locale de nadie.
            for (int i = 1; i <= 999; i++) {
                String postalCode = province + String.format("%03d", i);
                territory.postalCodeMunicipality(ruleSystemCode, postalCode)
                        .filter(m -> province.equals(m.provinceCode()))
                        .ifPresent(found::add);
            }
            if (found.isEmpty()) {
                throw new IllegalStateException("El maestro no tiene ningun CP con municipio de la provincia " + province);
            }
            log.info("Provincia {}: {} codigos postales con municipio en el maestro.", province, found.size());
            return List.copyOf(found);
        });
    }
}
