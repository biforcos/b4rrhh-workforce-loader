package com.b4rrhh.workforceloader.infrastructure.photo;

import com.b4rrhh.workforceloader.application.EmployeePhoto;
import com.b4rrhh.workforceloader.application.EmployeePhotoSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Las fotos de {@code src/main/resources/fotos/}, una por matrícula: {@code EMP000025.jpg}. Las genera
 * {@code tools/fotos/generar_fotos.py} y van commiteadas; el loader no genera nada (workforce-loader#19).
 */
@Component
public class ClasspathEmployeePhotoSource implements EmployeePhotoSource {

    private static final String PATRON = "classpath*:fotos/*.jpg";

    @Override
    public List<EmployeePhoto> photos() {
        try {
            List<EmployeePhoto> fotos = new ArrayList<>();
            for (Resource fichero : new PathMatchingResourcePatternResolver().getResources(PATRON)) {
                String nombre = fichero.getFilename();
                if (nombre == null) {
                    continue;
                }
                try (var entrada = fichero.getInputStream()) {
                    fotos.add(new EmployeePhoto(nombre.substring(0, nombre.length() - ".jpg".length()),
                            entrada.readAllBytes()));
                }
            }
            return fotos;
        } catch (IOException ex) {
            throw new UncheckedIOException("No se han podido leer las fotos de " + PATRON, ex);
        }
    }
}
