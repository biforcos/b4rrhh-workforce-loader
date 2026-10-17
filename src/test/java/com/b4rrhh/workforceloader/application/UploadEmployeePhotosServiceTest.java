package com.b4rrhh.workforceloader.application;

import com.b4rrhh.workforceloader.infrastructure.config.LoaderProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las caras de la semilla: se suben las que tienen empleado, en orden de matrícula, y el resumen
 * cuenta lo que sobra y lo que falta (workforce-loader#19).
 */
class UploadEmployeePhotosServiceTest {

    private final List<String> subidas = new ArrayList<>();

    @Test
    void uploadsThePhotoOfEverySeededEmployeeThatHasOneInEmployeeNumberOrder() {
        PhotoUploadSummary resumen = servicio(false, "EMP000025", "EMP000001", "EMP000003")
                .run(sembrados("EMP000001", "EMP000002", "EMP000003", "EMP000025"));

        assertThat(subidas).containsExactly("ESP/INTERNAL/EMP000001", "ESP/INTERNAL/EMP000003",
                "ESP/INTERNAL/EMP000025");
        assertThat(resumen).isEqualTo(new PhotoUploadSummary(3, 3, 0, 1));
    }

    @Test
    void aPhotoWhoseEmployeeWasNotSeededIsCountedAndNotUploaded() {
        PhotoUploadSummary resumen = servicio(false, "EMP000001", "EMP009999")
                .run(sembrados("EMP000001"));

        assertThat(subidas).containsExactly("ESP/INTERNAL/EMP000001");
        assertThat(resumen).isEqualTo(new PhotoUploadSummary(2, 1, 1, 0));
    }

    @Test
    void inDryRunNothingIsUploaded() {
        PhotoUploadSummary resumen = servicio(true, "EMP000001", "EMP000002")
                .run(sembrados("EMP000001", "EMP000002"));

        assertThat(subidas).isEmpty();
        assertThat(resumen).isEqualTo(new PhotoUploadSummary(2, 0, 0, 0));
    }

    private UploadEmployeePhotosService servicio(boolean enSeco, String... matriculasConFoto) {
        LoaderProperties properties = new LoaderProperties();
        properties.getRun().setDryRun(enSeco);
        properties.getDefaults().setRuleSystemCode("ESP");
        properties.getDefaults().setEmployeeTypeCode("INTERNAL");
        // En el orden en que se le dan, que no es el de matrícula: ordenar es cosa del servicio.
        List<EmployeePhoto> fotos = java.util.Arrays.stream(matriculasConFoto)
                .map(m -> new EmployeePhoto(m, new byte[] {1})).toList();
        return new UploadEmployeePhotosService(properties, () -> fotos,
                (rs, tipo, matricula, jpeg) -> subidas.add(rs + "/" + tipo + "/" + matricula));
    }

    private static List<SeededEmployee> sembrados(String... matriculas) {
        return java.util.Arrays.stream(matriculas)
                .map(m -> new SeededEmployee("INTERNAL", m, List.of())).toList();
    }
}
