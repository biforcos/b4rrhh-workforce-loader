package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.api.dto.CatalogOption;
import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

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
 * <h2>Aquí hubo una bisección, y el issue que la retiró</h2>
 *
 * <p>Esto sondeaba: proponía una fecha, pedía los catálogos dependientes con ella y miraba si
 * contestaban algo. Quince sondeos para cuarenta y seis años, porque la API servía las
 * categorías y los subtipos con {@code startDate} nulo y <b>la fecha buena no salía por ningún
 * sitio</b> — la relación convenio-categoría de {@code ESP} empieza el 2023-01-01 y ninguna de
 * las tres fechas que la API declaraba era ésa.
 *
 * <p>Era honesto y era absurdo, y así se dijo en el {@code b4rrhh/backend#115}. Desde ese issue
 * cada opción trae la <b>intersección</b> de las tres vigencias —la del padre, la suya y la de
 * la relación— y aquí sólo hay que leerla. Se queda escrito porque es la prueba de que la
 * fecha se publica de verdad: el consumidor que tuvo que adivinarla ya no la adivina.
 */
@Component
public class HireWindowResolver {

    private static final Logger log = LoggerFactory.getLogger(HireWindowResolver.class);

    private final LoaderProperties properties;
    private final HireReferenceDataResolver hireReferenceDataResolver;

    public HireWindowResolver(
            LoaderProperties properties,
            HireReferenceDataResolver hireReferenceDataResolver
    ) {
        this.properties = properties;
        this.hireReferenceDataResolver = hireReferenceDataResolver;
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
     * vez. {@code null} si el catálogo no lo dice.
     *
     * <p>Se lee, no se busca. Cada opción del catálogo trae desde el {@code b4rrhh/backend#115}
     * su {@code startDate}, y no es la de la entidad: es la <b>intersección</b> de las tres
     * vigencias, la del padre, la de la opción y la de la relación que las une. Esa tercera es
     * la que decide y era la que no se publicaba.
     *
     * <p>La cuenta es la que pide la pregunta: hace falta <b>un</b> convenio con <b>alguna</b>
     * categoría y <b>un</b> tipo de contrato con <b>algún</b> subtipo, así que de cada lado se
     * coge la opción que antes empieza y de los dos lados, la que después.
     */
    private LocalDate firstHireableDate(String ruleSystemCode, LocalDate hasta) {
        ResolvedHireReferencePools pools = hireReferenceDataResolver.preloadPools(ruleSystemCode);

        LocalDate conCategoria = laQueAntesEmpieza(pools.agreementsWithCategories().stream()
                .flatMap(convenio -> convenio.categories().stream())
                .toList());
        LocalDate conSubtipo = laQueAntesEmpieza(pools.contractTypesWithSubtypes().stream()
                .flatMap(tipo -> tipo.subtypes().stream())
                .toList());

        if (conCategoria == null || conSubtipo == null) {
            return null;
        }

        LocalDate desde = conCategoria.isAfter(conSubtipo) ? conCategoria : conSubtipo;
        return desde.isAfter(hasta) ? null : desde;
    }

    /**
     * La opción que antes empieza, o {@code null} si alguna no lo dice.
     *
     * <p><b>Un {@code startDate} nulo no se rellena con nada</b>, y por eso nulo se propaga en
     * vez de ignorarse. Un nulo que significa «no lo sé» y un nulo que significa «desde
     * siempre» son dos cosas distintas y la API no distingue cuál es; inventarse la segunda
     * es cómo se vuelve a un resultado que parece bueno y no lo es.
     */
    private static LocalDate laQueAntesEmpieza(List<CatalogOption> opciones) {
        if (opciones.isEmpty()) {
            return null;
        }
        LocalDate antes = null;
        for (CatalogOption opcion : opciones) {
            if (opcion.startDate() == null) {
                return null;
            }
            if (antes == null || opcion.startDate().isBefore(antes)) {
                antes = opcion.startDate();
            }
        }
        return antes;
    }

    private static String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
