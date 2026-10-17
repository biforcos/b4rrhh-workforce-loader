package com.b4rrhh.workforceloader.application;

/** Sube la foto de un empleado por la API (workforce-loader#19). */
public interface EmployeePhotoUploader {

    void upload(String ruleSystemCode, String employeeTypeCode, String employeeNumber, byte[] jpeg);
}
