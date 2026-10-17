package com.b4rrhh.workforceloader.application;

/**
 * Lo que dejó la subida de fotos (workforce-loader#19).
 *
 * @param files                 las fotos que trae el loader
 * @param uploaded              las que ha subido; en seco, cero
 * @param withoutEmployee       fotos de una matrícula que esta corrida no ha sembrado
 * @param employeesWithoutPhoto sembrados que no tienen fichero, que son la mayoría a propósito
 */
public record PhotoUploadSummary(int files, int uploaded, int withoutEmployee, int employeesWithoutPhoto) {
}
