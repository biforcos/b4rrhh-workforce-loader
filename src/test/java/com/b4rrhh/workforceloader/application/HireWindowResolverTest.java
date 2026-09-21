package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.api.dto.CatalogOption;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La ventana de contratación sale del catálogo y del período, y ninguno de los dos se adivina
 * ({@code workforce-loader#1}, {@code #11} y {@code b4rrhh/backend#115}).
 *
 * <h2>El escenario, que es el de {@code ESP} y por un motivo</h2>
 *
 * <p>Los tipos de contrato de la reforma laboral arrancan el <b>2022-03-30</b> y la relación
 * convenio-categoría no entra en vigor hasta el <b>2023-01-01</b>. La respuesta correcta es la
 * segunda, o sea <b>la más tardía de las dos</b>: hace falta un convenio con categoría <i>y</i>
 * un tipo de contrato con subtipo, así que manda el que llega el último. Un catálogo con una
 * sola fecha daría verde con cualquier implementación, incluida una que devolviera siempre la
 * más antigua.
 *
 * <h2>Lo que este test dejó de comprobar, y merece decirse</h2>
 *
 * <p>Aquí se afirmaba que el resolvedor <b>sondeaba</b> —que proponía fechas al catálogo hasta
 * que una contestaba— porque la API servía las categorías y los subtipos con {@code startDate}
 * nulo y la fecha buena no salía por ningún sitio. El {@code b4rrhh/backend#115} la publica,
 * así que ahora se lee. Lo que se defiende es justamente eso: que se lee.
 */
class HireWindowResolverTest {

    private static final LocalDate CONVENIO_DESDE   = LocalDate.of(1980, 1, 1);
    private static final LocalDate CONTRATOS_DESDE  = LocalDate.of(2022, 3, 30);
    private static final LocalDate CATEGORIAS_DESDE = LocalDate.of(2023, 1, 1);

    @Test
    void theWindowStartsOnTheLatestOfTheTwoDatesTheCatalogueGives() {
        assertThat(resolver(null, catalogo(CATEGORIAS_DESDE, CONTRATOS_DESDE)).resolve().desde())
                .isEqualTo(CATEGORIAS_DESDE);
    }

    /**
     * Y la fecha se <b>lee</b>: el catálogo se pide una sola vez y sin fecha.
     *
     * <p>Es la mitad que sostiene el {@code b4rrhh/backend#115}. Mientras la API servía nulos,
     * el loader tenía que proponer fechas y mirar si el catálogo contestaba: quince sondeos
     * para averiguar algo que el servidor sabía. Si esto vuelve a crecer, es que la fecha ha
     * dejado de publicarse y alguien ha vuelto a adivinarla.
     */
    @Test
    void theCatalogueIsAskedOnceAndWithoutADate() {
        CatalogoDePrueba catalogo = catalogo(CATEGORIAS_DESDE, CONTRATOS_DESDE);

        resolver(null, catalogo).resolve();

        assertThat(catalogo.veces).isEqualTo(1);
    }

    /** Si una migración adelanta la vigencia, la ventana se mueve sola. */
    @Test
    void movingTheRelationForwardMovesTheWindowWithoutTouchingAnything() {
        assertThat(resolver(null, catalogo(CONTRATOS_DESDE, CONTRATOS_DESDE)).resolve().desde())
                .isEqualTo(CONTRATOS_DESDE);
    }

    /**
     * Un {@code startDate} nulo no se rellena: se para.
     *
     * <p>Es el «lo que NO hay que hacer» del {@code b4rrhh/backend#115}. Un nulo que significa
     * «no lo sé» y uno que significa «desde siempre» son dos cosas, y hoy la API no distingue
     * cuál manda; inventarse la segunda es volver a un resultado que parece bueno y no lo es.
     */
    @Test
    void aNullStartDateStopsTheRunInsteadOfBeingInvented() {
        assertThatThrownBy(() -> resolver(null, catalogo(null, CONTRATOS_DESDE)).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hire-date-from");
    }

    /** Lo configurado manda: es una decisión explícita y no se pisa. */
    @Test
    void anExplicitDateWins() {
        LocalDate aMano = LocalDate.of(2024, 6, 1);

        assertThat(resolver(aMano, catalogo(CATEGORIAS_DESDE, CONTRATOS_DESDE)).resolve().desde())
                .isEqualTo(aMano);
    }

    /** El final de la ventana sale del período: el día antes del mes que se calcula. */
    @Test
    void theWindowEndsTheDayBeforeThePeriodItIsGoingToCalculate() {
        assertThat(resolver(null, catalogo(CATEGORIAS_DESDE, CONTRATOS_DESDE)).resolve().hasta())
                .isEqualTo(LocalDate.of(2026, 8, 31));
    }

    /** Y sin período ni fecha, se para: un {@code now()} haría que la semilla cambiara cada día. */
    @Test
    void withoutAPeriodItStopsInsteadOfFallingBackToToday() {
        LoaderProperties properties = propiedades(null, null);

        assertThatThrownBy(() ->
                resolverCon(properties, catalogo(CATEGORIAS_DESDE, CONTRATOS_DESDE)).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loader.period");
    }

    // ── el andamio ───────────────────────────────────────────────────────────

    private HireWindowResolver resolver(LocalDate configurada, CatalogoDePrueba catalogo) {
        return resolverCon(propiedades(configurada, 202609), catalogo);
    }

    private HireWindowResolver resolverCon(LoaderProperties properties, CatalogoDePrueba catalogo) {
        return new HireWindowResolver(properties, catalogo);
    }

    private static LoaderProperties propiedades(LocalDate desdeConfigurada, Integer periodo) {
        LoaderProperties properties = new LoaderProperties();
        properties.getDefaults().setRuleSystemCode("esp");
        properties.getGeneration().setHireDateFrom(desdeConfigurada);
        properties.setPeriod(periodo);
        return properties;
    }

    private static CatalogoDePrueba catalogo(LocalDate categoriasDesde, LocalDate subtiposDesde) {
        return new CatalogoDePrueba(categoriasDesde, subtiposDesde);
    }

    /**
     * Los almacenes precargados, con las fechas que el backend publica desde el
     * {@code b4rrhh/backend#115}: la <b>intersección</b> de las tres vigencias, y no la de la
     * entidad.
     *
     * <p>Cuenta cuántas veces se le pide el catálogo, para poder afirmar que se pide una.
     */
    private static final class CatalogoDePrueba extends HireReferenceDataResolver {

        private final LocalDate categoriasDesde;
        private final LocalDate subtiposDesde;
        private int veces;

        private CatalogoDePrueba(LocalDate categoriasDesde, LocalDate subtiposDesde) {
            super(null, new LoaderProperties());
            this.categoriasDesde = categoriasDesde;
            this.subtiposDesde = subtiposDesde;
        }

        @Override
        public ResolvedHireReferencePools preloadPools(String ruleSystemCode) {
            veces++;
            return new ResolvedHireReferencePools(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(new AgreementWithCategories(
                            new CatalogOption("99002405011982", "Grandes almacenes", CONVENIO_DESDE, null),
                            List.of(new CatalogOption("99002405-G2", "Grupo II", categoriasDesde, null)))),
                    List.of(new ContractTypeWithSubtypes(
                            new CatalogOption("100", "Indefinido", CONTRATOS_DESDE, null),
                            List.of(new CatalogOption("01", "Subtipo 01", subtiposDesde, null)))),
                    List.of());
        }
    }
}
