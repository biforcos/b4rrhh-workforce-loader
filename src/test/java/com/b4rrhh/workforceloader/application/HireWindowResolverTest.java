package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.api.CatalogApiClient;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CatalogOption;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La ventana de contratación sale del catálogo ({@code workforce-loader#1}).
 *
 * <p>El escenario es el de {@code ESP}, con sus dos fechas reales y por un motivo: son las que
 * hacen que este test signifique algo. Los tipos de contrato de la reforma laboral arrancan el
 * <b>2022-03-30</b> y la relación convenio-categoría no entra en vigor hasta el
 * <b>2023-01-01</b>, así que la respuesta correcta es la segunda y <b>no</b> la primera fecha
 * que el catálogo declara. Un catálogo con una sola fecha daría verde con cualquier
 * implementación, incluida una que devolviera siempre la más antigua.
 */
class HireWindowResolverTest {

    private static final LocalDate CONVENIO_DESDE   = LocalDate.of(1980, 1, 1);
    private static final LocalDate CONTRATOS_DESDE  = LocalDate.of(2022, 3, 30);
    private static final LocalDate CATEGORIAS_DESDE = LocalDate.of(2023, 1, 1);

    @Test
    void withNothingConfiguredTheWindowStartsWhereTheCatalogueSaysItCan() {
        CatalogoDePrueba catalogo = new CatalogoDePrueba(CATEGORIAS_DESDE);

        assertThat(resolver(null, catalogo).resolve().desde()).isEqualTo(CATEGORIAS_DESDE);
    }

    /**
     * Y la fecha se pregunta, no se deduce de lo que llega.
     *
     * <p>Es la mitad que hace falta comprobar: la vigencia de la <b>relación</b> convenio-
     * categoría no viaja en la respuesta, así que quien mirase las fechas de las categorías
     * elegiría el 2022-03-30 y se equivocaría. Aquí se ve porque el catálogo contesta lista
     * vacía ese día.
     */
    @Test
    void theAnswerIsADateTheCatalogueNeverDeclares() {
        CatalogoDePrueba catalogo = new CatalogoDePrueba(CATEGORIAS_DESDE);

        LocalDate ventana = resolver(null, catalogo).resolve().desde();

        // Las unicas fechas que este catalogo declara son la del convenio y la del tipo de
        // contrato, como en ESP: las categorias y los subtipos llegan sin fechas. El
        // 2023-01-01 no esta entre ellas y es la respuesta, asi que no ha salido de mirar lo
        // que llega -- ha salido de preguntar.
        assertThat(catalogo.fechasDeclaradas).containsExactly(CONVENIO_DESDE, CONTRATOS_DESDE);
        assertThat(ventana).isEqualTo(CATEGORIAS_DESDE).isNotIn(catalogo.fechasDeclaradas);

        // Y se busca, no se recorre: cuarenta y seis anos son mas de diecisiete mil dias.
        assertThat(catalogo.fechasPreguntadas).hasSizeLessThan(40);
    }

    /** Si una migración adelanta la vigencia, la ventana se mueve sola. */
    @Test
    void movingTheRelationForwardMovesTheWindowWithoutTouchingAnything() {
        CatalogoDePrueba catalogo = new CatalogoDePrueba(CONTRATOS_DESDE);

        assertThat(resolver(null, catalogo).resolve().desde()).isEqualTo(CONTRATOS_DESDE);
    }

    /**
     * El final de la ventana sale del período ({@code workforce-loader#11}).
     *
     * <p>El día antes del mes que se calcula, para que ninguna alta caiga dentro. Aquí había un
     * {@code 2026-05-01} escrito a mano que nadie explicaba y que, mirado en el historial, era
     * «hoy» del día en que se escribió.
     */
    @Test
    void theWindowEndsTheDayBeforeThePeriodItIsGoingToCalculate() {
        LoaderProperties properties = propiedades(null, 202609);

        assertThat(resolverCon(properties, new CatalogoDePrueba(CATEGORIAS_DESDE)).resolve().hasta())
                .isEqualTo(LocalDate.of(2026, 8, 31));
    }

    /** Y sin período ni fecha, se para: un {@code now()} haría que la semilla cambiara cada día. */
    @Test
    void withoutAPeriodItStopsInsteadOfFallingBackToToday() {
        LoaderProperties properties = propiedades(null, null);

        assertThatThrownBy(() -> resolverCon(properties, new CatalogoDePrueba(CATEGORIAS_DESDE)).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loader.period");
    }

    /** Lo configurado manda: es una decisión explícita y no se pisa. */
    @Test
    void anExplicitDateWins() {
        LocalDate aMano = LocalDate.of(2024, 6, 1);

        assertThat(resolver(aMano, new CatalogoDePrueba(CATEGORIAS_DESDE)).resolve().desde()).isEqualTo(aMano);
    }

    /** Y si el catálogo no sabe contestar, se para diciéndolo en vez de elegir mal. */
    @Test
    void aCatalogueThatNeverAnswersStopsTheRunAndSaysSo() {
        CatalogoDePrueba mudo = new CatalogoDePrueba(LocalDate.of(2099, 1, 1));

        assertThatThrownBy(() -> resolver(null, mudo).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hire-date-from");
    }

    // ── el andamio ───────────────────────────────────────────────────────────

    private HireWindowResolver resolver(LocalDate configurada, CatalogoDePrueba catalogo) {
        // El final sale del periodo desde el workforce-loader#11: 202609 -> 2026-08-31.
        return resolverCon(propiedades(configurada, 202609), catalogo);
    }

    private HireWindowResolver resolverCon(LoaderProperties properties, CatalogoDePrueba catalogo) {
        return new HireWindowResolver(properties, new PoolsDePrueba(catalogo, properties), catalogo);
    }

    private static LoaderProperties propiedades(LocalDate desdeConfigurada, Integer periodo) {
        LoaderProperties properties = new LoaderProperties();
        properties.getDefaults().setRuleSystemCode("esp");
        properties.getGeneration().setHireDateFrom(desdeConfigurada);
        properties.setPeriod(periodo);
        return properties;
    }

    /**
     * El catálogo de {@code ESP} en pequeño: un convenio con tres categorías desde una fecha, y
     * un tipo de contrato con un subtipo desde otra anterior.
     *
     * <p>Las categorías se devuelven <b>vacías</b> antes de la fecha de la relación, que es
     * exactamente lo que hace el backend, y apunta cada fecha que se le pregunta para poder
     * afirmar que se preguntó.
     */
    private static final class CatalogoDePrueba extends CatalogApiClient {

        private final LocalDate relacionConvenioCategoria;
        private final List<LocalDate> fechasPreguntadas = new ArrayList<>();
        /** Lo unico que un cliente puede ver sin preguntar: el convenio y el tipo de contrato. */
        private final List<LocalDate> fechasDeclaradas = List.of(CONVENIO_DESDE, CONTRATOS_DESDE);

        private CatalogoDePrueba(LocalDate relacionConvenioCategoria) {
            super(propiedadesMinimas(), WebClient.builder());
            this.relacionConvenioCategoria = relacionConvenioCategoria;
        }

        @Override
        public List<CatalogOption> getAgreementCategories(
                String ruleSystemCode, String agreementCode, LocalDate referenceDate) {
            if (referenceDate != null) {
                fechasPreguntadas.add(referenceDate);
            }
            if (referenceDate != null && referenceDate.isBefore(relacionConvenioCategoria)) {
                return List.of();
            }
            // Sin fechas, como las sirve el backend: la vigencia que decide es la de la
            // relacion convenio-categoria, y esa no viaja.
            return List.of(new CatalogOption("99002405-G2", "Grupo II"));
        }

        @Override
        public List<CatalogOption> getContractSubtypes(
                String ruleSystemCode, String contractTypeCode, LocalDate referenceDate) {
            if (referenceDate != null && referenceDate.isBefore(CONTRATOS_DESDE)) {
                return List.of();
            }
            return List.of(new CatalogOption("401", "Indefinido ordinario"));
        }
    }

    /** Los almacenes precargados, sin red: un convenio y un tipo de contrato. */
    private static final class PoolsDePrueba extends HireReferenceDataResolver {

        private final CatalogoDePrueba catalogo;

        private PoolsDePrueba(CatalogoDePrueba catalogo, LoaderProperties properties) {
            super(catalogo, properties);
            this.catalogo = catalogo;
        }

        @Override
        public ResolvedHireReferencePools preloadPools(String ruleSystemCode) {
            return new ResolvedHireReferencePools(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(new AgreementWithCategories(
                            new CatalogOption("99002405011982", "Grandes almacenes",
                                    CONVENIO_DESDE, null),
                            catalogo.getAgreementCategories(ruleSystemCode, "99002405011982", null))),
                    List.of(new ContractTypeWithSubtypes(
                            new CatalogOption("400", "Indefinido", CONTRATOS_DESDE, null),
                            catalogo.getContractSubtypes(ruleSystemCode, "400", null))),
                    List.of());
        }
    }

    private static LoaderProperties propiedadesMinimas() {
        LoaderProperties properties = new LoaderProperties();
        properties.getBackend().setBaseUrl("http://localhost:8080");
        return properties;
    }
}
