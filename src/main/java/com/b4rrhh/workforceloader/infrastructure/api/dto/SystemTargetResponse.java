package com.b4rrhh.workforceloader.infrastructure.api.dto;

/**
 * Lo que contesta GET /system/target: a que base escribe el backend.
 *
 * - database: host:puerto/nombre. La direccion, no solo el nombre: la base de
 *   la demo y la de desarrollo se llaman las dos b4rrhh.
 * - schemaVersion: la ultima migracion de Flyway aplicada.
 * - employees: cuantos hay ya.
 */
public record SystemTargetResponse(
        String database,
        String schemaVersion,
        long employees
) {
}
