package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.api.TerritoryApiClient;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dónde vive la gente de un centro (workforce-loader#17): en su provincia casi siempre, si no en una
 * vecina; con un CP que existe y su municipio; y siempre igual para la misma dirección.
 */
class SpanishAddressPlacerTest {

    @Test
    void mostLiveInTheProvinceOfTheirCenterAndTheRestNextDoor() {
        SpanishAddressPlacer placer = new SpanishAddressPlacer(new FakeTerritory());
        Map<String, Integer> byProvince = new HashMap<>();
        int people = 2000;
        for (long seed = 0; seed < people; seed++) {
            byProvince.merge(placer.place("ESP", "MAIN_OFFICE", seed).provinceCode(), 1, Integer::sum);
        }

        assertThat(byProvince.keySet()).isSubsetOf("28", "05", "16", "19", "40", "45");
        assertThat(byProvince.get("28") * 100 / people).isBetween(SpanishAddressPlacer.OWN_PROVINCE_PERCENT - 3,
                SpanishAddressPlacer.OWN_PROVINCE_PERCENT + 3);
        assertThat(byProvince).as("y las vecinas también salen").hasSizeGreaterThan(1);
    }

    @Test
    void thePostalCodeExistsAndTheMunicipalityIsTheOneItSuggests() {
        SpanishAddressPlacer placer = new SpanishAddressPlacer(new FakeTerritory());
        for (long seed = 0; seed < 500; seed++) {
            SpanishAddressPlacer.Place place = placer.place("ESP", "MAIN_OFFICE", seed);
            assertThat(FakeTerritory.KNOWN).containsKey(place.postalCode());
            assertThat(place.municipalityCode()).isEqualTo(FakeTerritory.KNOWN.get(place.postalCode()));
            assertThat(place.postalCode()).startsWith(place.provinceCode());
        }
    }

    @Test
    void theSameAddressAlwaysLandsInTheSamePlace() {
        SpanishAddressPlacer one = new SpanishAddressPlacer(new FakeTerritory());
        SpanishAddressPlacer other = new SpanishAddressPlacer(new FakeTerritory());
        for (long seed = 0; seed < 200; seed++) {
            assertThat(other.place("ESP", "MAIN_OFFICE", seed)).isEqualTo(one.place("ESP", "MAIN_OFFICE", seed));
        }
    }

    @Test
    void aCenterWithoutANormalizedSpanishAddressStopsTheLoader() {
        SpanishAddressPlacer placer = new SpanishAddressPlacer(new FakeTerritory());
        assertThatThrownBy(() -> placer.place("ESP", "REMOTE", 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ESP/REMOTE")
                .hasMessageContaining("normalizada");
    }

    @Test
    void aStreetTypeThatIsNotInTheCatalogStopsTheLoader() {
        SpanishAddressPlacer placer = new SpanishAddressPlacer(new FakeTerritory());
        placer.requireStreetType("ESP", "CL");
        assertThatThrownBy(() -> placer.requireStreetType("ESP", "ZZ")).isInstanceOf(IllegalStateException.class);
    }

    /** Madrid y sus vecinas, con dos CP con municipio cada una; los demás no existen. */
    private static final class FakeTerritory extends TerritoryApiClient {

        static final Map<String, String> KNOWN = Map.ofEntries(
                Map.entry("28001", "28079"), Map.entry("28400", "28058"),
                Map.entry("05001", "05019"), Map.entry("05200", "05014"),
                Map.entry("16001", "16078"), Map.entry("16400", "16203"),
                Map.entry("19001", "19130"), Map.entry("19200", "19024"),
                Map.entry("40001", "40194"), Map.entry("40100", "40181"),
                Map.entry("45001", "45168"), Map.entry("45600", "45165"));

        private FakeTerritory() {
            super(new LoaderProperties(), WebClient.builder());
        }

        @Override
        public List<String> streetTypeCodes(String countryCode) {
            return List.of("AV", "CL", "PZ");
        }

        @Override
        public Optional<PostalCodeMunicipality> postalCodeMunicipality(String countryCode, String postalCode) {
            return Optional.ofNullable(KNOWN.get(postalCode))
                    .map(municipality -> new PostalCodeMunicipality(postalCode, postalCode.substring(0, 2), municipality, "x"));
        }

        @Override
        public Optional<WorkCenterLocation> workCenterLocation(String ruleSystemCode, String workCenterCode) {
            if (Set.of("REMOTE").contains(workCenterCode)) {
                return Optional.of(new WorkCenterLocation(workCenterCode, "ESP", null, null, false));
            }
            return Optional.of(new WorkCenterLocation(workCenterCode, "ESP", "28", "28079", true));
        }
    }
}
