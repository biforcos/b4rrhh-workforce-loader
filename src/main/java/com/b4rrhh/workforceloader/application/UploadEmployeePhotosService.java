package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Las caras de la semilla (workforce-loader#19).
 *
 * <p>Sube la foto de cada empleado sembrado que tenga fichero, <b>en orden de matrícula</b>, por el
 * camino real de la API. Las fotos no se pueden poner a mano en la demo: se resiembra desde un volcado
 * y se reinicia cada noche, y una foto subida allí muere esa noche. Así que «la demo tiene caras» sólo
 * puede querer decir «la semilla lleva caras», y eso es esto.
 *
 * <p>Una subida que falla tumba la corrida, y a propósito: una semilla con la mitad de las caras no
 * es una semilla con caras, y la siguiente corrida empieza desde una base vacía de todas formas.
 */
@Service
public class UploadEmployeePhotosService {

    private static final Logger log = LoggerFactory.getLogger(UploadEmployeePhotosService.class);

    private final LoaderProperties properties;
    private final EmployeePhotoSource source;
    private final EmployeePhotoUploader uploader;

    public UploadEmployeePhotosService(
            LoaderProperties properties,
            EmployeePhotoSource source,
            EmployeePhotoUploader uploader
    ) {
        this.properties = properties;
        this.source = source;
        this.uploader = uploader;
    }

    public PhotoUploadSummary run(List<SeededEmployee> seeded) {
        // En orden de matrícula, como todo lo que siembra el loader (workforce-loader#18).
        List<EmployeePhoto> fotos = source.photos().stream()
                .sorted(Comparator.comparing(EmployeePhoto::employeeNumber))
                .toList();
        String ruleSystemCode = properties.getDefaults().getRuleSystemCode();
        String employeeTypeCode = properties.getDefaults().getEmployeeTypeCode();

        Set<String> sembrados = new HashSet<>();
        for (SeededEmployee empleado : seeded) {
            if (employeeTypeCode.equals(empleado.employeeTypeCode())) {
                sembrados.add(empleado.employeeNumber());
            }
        }

        boolean enSeco = properties.getRun().isDryRun();
        int subidas = 0;
        int sinEmpleado = 0;
        for (EmployeePhoto foto : fotos) {
            if (!sembrados.contains(foto.employeeNumber())) {
                sinEmpleado++;
                log.warn("Foto sin empleado: {} no lo ha sembrado esta corrida.", foto.employeeNumber());
                continue;
            }
            if (!enSeco) {
                uploader.upload(ruleSystemCode, employeeTypeCode, foto.employeeNumber(), foto.jpeg());
                subidas++;
            }
        }

        PhotoUploadSummary resumen = new PhotoUploadSummary(
                fotos.size(), subidas, sinEmpleado, sembrados.size() - (fotos.size() - sinEmpleado));
        if (enSeco) {
            log.info("Fotos, EN SECO: no se sube ninguna. Subiria {} de {} ficheros; {} sin empleado;"
                    + " {} sembrados sin foto.", fotos.size() - sinEmpleado, resumen.files(),
                    resumen.withoutEmployee(), resumen.employeesWithoutPhoto());
        } else {
            log.info("Fotos: {} subidas de {} ficheros; {} sin empleado; {} sembrados sin foto.",
                    resumen.uploaded(), resumen.files(), resumen.withoutEmployee(),
                    resumen.employeesWithoutPhoto());
        }
        return resumen;
    }
}
