package com.b4rrhh.workforceloader.application;

/**
 * Quien sabe si un convenio prorratea las pagas extras por defecto ({@code b4rrhh/backend#117}).
 *
 * <p>Es una interfaz y no el cliente directamente porque lo que el generador necesita saber es
 * <b>que dice el convenio</b>, y no por que camino se averigua. En la corrida lo contesta el
 * backend; en un test, una lambda.
 */
public interface AgreementExtraPaymentProrationSource {

    boolean proratesExtraPaymentsByDefault(String ruleSystemCode, String agreementCode);
}
