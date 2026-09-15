package com.b4rrhh.workforceloader.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El loader no escribe si nadie se lo dice (workforce-loader#9).
 *
 * <p>El defecto era {@code false} —o sea, escribia— y tres documentos decian lo contrario. Lo que
 * decidio el cambio no es la simetria con la documentacion sino el reparto de costes: con
 * {@code false}, equivocarse cuesta una base escrita y una hora de reconstruirla; con {@code true},
 * una bandera que hay que volver a poner.
 *
 * <p>Este test existe porque el valor vive en dos sitios y los dos tienen que decir lo mismo. El del
 * {@code application.yml} es el que se lee; el del campo de {@link LoaderProperties.Run} es el que
 * manda si la clave falta —un perfil nuevo, un fichero recortado—, y un defecto que solo vive en la
 * configuracion protege mientras nadie toque la configuracion, que es justo cuando hace falta.
 *
 * <p>Lo que este test <b>no</b> puede afirmar es que el modo en seco no escriba: eso lo sujetan las
 * once ramas {@code isDryRun()} de {@code RunLifecycleSimulationService}, y se comprobo contra el
 * backend arrancado en las dos direcciones (workforce-loader#9). Aqui solo se afirma cual es el
 * defecto, que es lo que se puede perder en una edicion distraida.
 */
class TheLoaderDoesNotWriteUnlessItIsToldToTest {

    @Test
    void theShippedConfigurationRunsDry() {
        LoaderProperties.Run run = new Binder(ConfigurationPropertySources.from(applicationYml()))
                .bind("loader.run", LoaderProperties.Run.class)
                .orElseThrow(() -> new AssertionError(
                        "loader.run no esta en el application.yml. Si ha cambiado de sitio, este test"
                                + " hay que actualizarlo, no borrarlo."));

        assertTrue(run.isDryRun(),
                "El application.yml que se empaqueta trae dry-run en false: un mvn spring-boot:run"
                        + " sin tocar nada escribiria de verdad contra el backend al que apunte"
                        + " (workforce-loader#9).");
    }

    @Test
    void andSoDoesTheDefaultThatTakesOverIfTheKeyIsMissing() {
        assertTrue(new LoaderProperties.Run().isDryRun(),
                "El defecto del codigo es false: si algun dia falta la clave en el fichero, el loader"
                        + " escribiria sin que nadie se lo pidiera.");
    }

    private PropertySource<?> applicationYml() {
        try {
            List<PropertySource<?>> cargadas =
                    new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
            assertTrue(!cargadas.isEmpty(), "No he podido leer el application.yml del classpath.");
            return cargadas.get(0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
