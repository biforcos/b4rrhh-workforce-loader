package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.api.CatalogApiClient;
import com.b4rrhh.workforceloader.infrastructure.api.dto.CatalogOption;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TreeSet;

/**
 * Desde cuándo se puede contratar de verdad, preguntándoselo al catálogo ({@code
 * workforce-loader#1}).
 *
 * <h2>Qué había</h2>
 *
 * <p>{@code loader.generation.hire-date-from: 2023-01-01}, a mano en el {@code
 * application.yml}. La fecha no era arbitraria —era el primer día con convenio, categoría,
 * tipo de contrato y subtipo simultáneamente vigentes en {@code ESP}, y estaba medida—, pero
 * era <b>un número mágico acoplado a las semillas</b>: el día que una migración mueva esa
 * vigencia, el YAML sigue diciendo 2023 y nadie se entera.
 *
 * <h2>Por qué se pregunta y no se calcula aquí</h2>
 *
 * <p>Porque la respuesta no está en los datos que llegan. Lo que caduca no es la categoría:
 * es la <b>relación</b> convenio-categoría, que tiene vigencia propia y <b>no viaja en la
 * respuesta</b>. Lo mismo con la relación tipo-subtipo. Filtrar aquí por las fechas que sí
 * llegan da un resultado que parece bueno y no lo es — ya pasó, y el backend contestaba
 * {@code INVALID_CATALOG_VALUE} sobre categorías que por sus propias fechas estaban vigentes.
 *
 * <p>Así que esto <b>sondea</b>: propone una fecha, pide los catálogos dependientes con ella
 * y mira si contestan algo. Una respuesta vacía es la vigencia de la relación diciendo que no.
 *
 * <h2>Por qué una bisección y no una lista de fechas candidatas</h2>
 *
 * <p>Porque probar sólo las fechas que el catálogo declara <b>no encuentra la respuesta</b>, y
 * está medido contra {@code ESP}: la relación convenio-categoría empieza el 2023-01-01 y ese
 * día no sale por ninguna parte de la API. El convenio arranca el 1980-01-01, los tipos de
 * contrato el 2022-03-30, y las categorías y los subtipos llegan con {@code startDate} nulo.
 * Se probaron las tres fechas declaradas, ninguna contestó, y la buena no estaba entre ellas —
 * que es justamente el motivo por el que esa vigencia no se puede deducir: no viaja.
 *
 * <p>Así que se busca por bisección entre la vigencia más antigua que el catálogo declara y el
 * final de la ventana. Son unos quince sondeos para cuarenta y seis años, y cada sondeo son
 * dos preguntas que el cliente ya cachea.
 *
 * <p><b>Lo que la bisección da por hecho</b>, dicho para que nadie lo descubra por las malas:
 * que una vez se puede contratar, se sigue pudiendo hasta el final de la ventana. Si una
 * vigencia se cerrara por el medio, esto encontraría el arranque del último tramo y no el del
 * primero. El final de la ventana se comprueba antes que nada por eso mismo: si ahí no se
 * puede contratar, no se busca nada y se para diciéndolo.
 */
@Component
public class HireWindowResolver {

    private static final Logger log = LoggerFactory.getLogger(HireWindowResolver.class);

    private final LoaderProperties properties;
    private final HireReferenceDataResolver hireReferenceDataResolver;
    private final CatalogApiClient catalogApiClient;

    public HireWindowResolver(
            LoaderProperties properties,
            HireReferenceDataResolver hireReferenceDataResolver,
            CatalogApiClient catalogApiClient
    ) {
        this.properties = properties;
        this.hireReferenceDataResolver = hireReferenceDataResolver;
        this.catalogApiClient = catalogApiClient;
    }

    /**
     * La ventana entera, con sus dos extremos resueltos y dichos.
     *
     * <p>Si alguno está configurado, manda el configurado: es una decisión explícita y no se
     * pisa. Pero se compara con lo que sale del catálogo o del período y <b>se dice si no
     * coinciden</b>, que es justo lo que no pasaba — el YAML podía quedarse viejo en silencio.
     */
    public HireWindow resolve() {
        LocalDate hasta = resolveHasta();
        LocalDate desde = resolveDesde(hasta);

        log.info("Ventana de contratacion: desde {} hasta {}.", desde, hasta);
        return new HireWindow(desde, hasta);
    }

    /**
     * El final de la ventana: el día antes del período que la demo calcula
     * ({@code workforce-loader#11}).
     *
     * <p>Aquí había un {@code 2026-05-01} escrito a mano que nadie explicaba. Medido en el
     * historial: entró el 2026-05-10 en un commit sobre filtros, sustituyendo a un
     * {@code 2026-03-31} que había entrado el 2026-04-05. Las dos son «hoy, redondeado», y
     * ninguna puede derivar del período — {@code 202609} no existía en el fichero hasta el
     * 2026-09-16, cuatro meses después.
     *
     * <p>Así que era «hoy». Y «hoy» es exactamente lo que este fichero prohíbe para las otras
     * dos fechas que dependen del período, por el mismo motivo: la misma semilla tiene que dar
     * lo mismo cualquier día. Por eso no se deja caer al {@code now()} del código, se deriva.
     */
    private LocalDate resolveHasta() {
        LocalDate configurada = properties.getGeneration().getHireDateTo();
        LocalDate delPeriodo = properties.hireDateToFromPeriod();

        if (configurada == null) {
            if (delPeriodo == null) {
                throw new IllegalStateException(
                        "No hay de donde sacar el final de la ventana de contratacion: declara"
                                + " loader.period (el mes que se calcula, en yyyyMM) o"
                                + " loader.generation.hire-date-to. No se usa la fecha de hoy a"
                                + " proposito: la misma semilla tiene que dar lo mismo cualquier dia.");
            }
            return delPeriodo;
        }

        if (delPeriodo != null && !configurada.equals(delPeriodo)) {
            log.warn("hire-date-to={} configurada a mano, pero del periodo {} sale {}. Se usa la"
                    + " configurada; quitala del application.yml para que la ponga el periodo.",
                    configurada, properties.getPeriod(), delPeriodo);
        }
        return configurada;
    }

    private LocalDate resolveDesde(LocalDate hasta) {
        String ruleSystemCode = normalizeCode(properties.getDefaults().getRuleSystemCode());
        LocalDate configurada = properties.getGeneration().getHireDateFrom();

        LocalDate delCatalogo = firstHireableDate(ruleSystemCode, hasta);

        if (configurada == null) {
            if (delCatalogo == null) {
                throw new IllegalStateException(
                        "El catalogo de " + ruleSystemCode + " no dice desde cuando se puede"
                                + " contratar: ni siquiera el " + hasta + " devuelve convenio,"
                                + " categoria, tipo de contrato y subtipo a la vez."
                                + " Pon loader.generation.hire-date-from a mano.");
            }
            return delCatalogo;
        }

        if (delCatalogo == null) {
            log.warn("hire-date-from={} configurada a mano, y el catalogo de {} no ha sabido"
                    + " confirmar ninguna fecha. Se usa la configurada.", configurada, ruleSystemCode);
        } else if (!configurada.equals(delCatalogo)) {
            log.warn("hire-date-from={} configurada a mano, pero el catalogo de {} permite"
                    + " contratar desde {}. Se usa la configurada; quitala del application.yml"
                    + " para que la ventana la ponga el catalogo.",
                    configurada, ruleSystemCode, delCatalogo);
        }
        return configurada;
    }

    /**
     * El primer día en que hay convenio con categoría y tipo de contrato con subtipo, todo a la
     * vez. {@code null} si ni siquiera el final de la ventana lo consigue.
     */
    private LocalDate firstHireableDate(String ruleSystemCode, LocalDate hasta) {
        ResolvedHireReferencePools pools = hireReferenceDataResolver.preloadPools(ruleSystemCode);

        if (!seContrata(ruleSystemCode, pools, hasta)) {
            return null;
        }

        LocalDate desde = earliestDeclaredDate(pools, hasta);
        if (seContrata(ruleSystemCode, pools, desde)) {
            return desde;
        }

        // Invariante: en 'no' no se contrata y en 'si' si. Se estrecha hasta que sean
        // consecutivos, y entonces 'si' es el primer dia bueno.
        LocalDate no = desde;
        LocalDate si = hasta;
        while (ChronoUnit.DAYS.between(no, si) > 1) {
            LocalDate medio = no.plusDays(ChronoUnit.DAYS.between(no, si) / 2);
            if (seContrata(ruleSystemCode, pools, medio)) {
                si = medio;
            } else {
                no = medio;
            }
        }
        return si;
    }

    private boolean seContrata(String ruleSystemCode, ResolvedHireReferencePools pools, LocalDate fecha) {
        return hayConvenioConCategoria(ruleSystemCode, pools, fecha)
                && hayContratoConSubtipo(ruleSystemCode, pools, fecha);
    }

    /**
     * El suelo de la búsqueda: la vigencia más antigua que el catálogo declara.
     *
     * <p>No es la respuesta —en {@code ESP} es el 1980-01-01, cuarenta y tres años antes de que
     * se pueda contratar de verdad— pero es un suelo honesto: antes de que exista la primera
     * pieza no puede funcionar nada. Sin ninguna fecha declarada el suelo es el final de la
     * ventana, y entonces la bisección no llega a correr.
     */
    private LocalDate earliestDeclaredDate(ResolvedHireReferencePools pools, LocalDate hasta) {
        TreeSet<LocalDate> fechas = new TreeSet<>();
        for (AgreementWithCategories convenio : pools.agreementsWithCategories()) {
            anadir(fechas, convenio.agreement(), hasta);
            for (CatalogOption categoria : convenio.categories()) {
                anadir(fechas, categoria, hasta);
            }
        }
        for (ContractTypeWithSubtypes tipo : pools.contractTypesWithSubtypes()) {
            anadir(fechas, tipo.contractType(), hasta);
            for (CatalogOption subtipo : tipo.subtypes()) {
                anadir(fechas, subtipo, hasta);
            }
        }
        return fechas.isEmpty() ? hasta : fechas.first();
    }

    private static void anadir(TreeSet<LocalDate> fechas, CatalogOption opcion, LocalDate hasta) {
        LocalDate desde = opcion.startDate();
        if (desde != null && !desde.isAfter(hasta)) {
            fechas.add(desde);
        }
    }

    private boolean hayConvenioConCategoria(
            String ruleSystemCode, ResolvedHireReferencePools pools, LocalDate fecha) {
        for (AgreementWithCategories convenio : pools.agreementsWithCategories()) {
            if (!convenio.agreement().isVigenteEn(fecha)) {
                continue;
            }
            List<CatalogOption> categorias =
                    catalogApiClient.getAgreementCategories(ruleSystemCode, convenio.agreement().code(), fecha);
            if (categorias != null && !categorias.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hayContratoConSubtipo(
            String ruleSystemCode, ResolvedHireReferencePools pools, LocalDate fecha) {
        for (ContractTypeWithSubtypes tipo : pools.contractTypesWithSubtypes()) {
            if (!tipo.contractType().isVigenteEn(fecha)) {
                continue;
            }
            List<CatalogOption> subtipos =
                    catalogApiClient.getContractSubtypes(ruleSystemCode, tipo.contractType().code(), fecha);
            if (subtipos != null && !subtipos.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
