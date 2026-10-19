package com.gpstl.joyn.joboffer.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Test unitaire : noms de champs Java -> noms JSON renvoyés au front. */
class RecruiterJobOfferExceptionHandlerTest {

    @Test
    void convertsJavaFieldNamesToJsonNames() {
        assertThat(RecruiterJobOfferExceptionHandler.toJsonName("title")).isEqualTo("title");
        assertThat(RecruiterJobOfferExceptionHandler.toJsonName("contractType")).isEqualTo("contract_type");
        assertThat(RecruiterJobOfferExceptionHandler.toJsonName("remotePolicy")).isEqualTo("remote_policy");
        assertThat(RecruiterJobOfferExceptionHandler.toJsonName("degreeLevelRequired")).isEqualTo("degree_level_required");
        assertThat(RecruiterJobOfferExceptionHandler.toJsonName("skills[1]")).isEqualTo("skills");
        assertThat(RecruiterJobOfferExceptionHandler.toJsonName("salaryRangeValid")).isEqualTo("salary_max");
    }
}
