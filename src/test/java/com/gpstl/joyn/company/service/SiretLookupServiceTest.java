package com.gpstl.joyn.company.service;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SiretLookupServiceTest {
    private static final String SIRET = "35600000000048";
    private MockRestServiceServer server;
    private SiretLookupService service;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://registry.test");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new SiretLookupService(builder.build());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123", "356000000000480", "3560000000004a", "356 000 000 00048"})
    void rejectsMalformedNumbersWithoutCallingUpstream(String value) {
        assertThatThrownBy(() -> service.lookup(value))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        server.verify();
    }

    @Test
    void usesExactEstablishmentRatherThanHeadOfficeAndPreservesClosedStatus() {
        respond("""
                {"results":[{
                  "siren":"356000000", "nom_complet":"LA POSTE", "nom_raison_sociale":"LA POSTE SA",
                  "etat_administratif":"A", "tranche_effectif_salarie":"53",
                  "annee_tranche_effectif_salarie":"2024", "unknown_field":"ignored",
                  "siege":{"siret":"35600000000056","libelle_commune":"PARIS","etat_administratif":"A"},
                  "matching_etablissements":[
                    {"siret":"35600000000056","libelle_commune":"PARIS"},
                    {"siret":"35600000000048","nom_commercial":"Agence locale",
                     "libelle_commune":"LYON", "adresse":"1 RUE TEST 69001 LYON", "code_postal":"69001",
                     "activite_principale":"53.10Z", "etat_administratif":"F"}
                  ]
                }]}
                """);

        var result = service.lookup(SIRET);
        assertThat(result.siret()).isEqualTo(SIRET);
        assertThat(result.legalName()).isEqualTo("LA POSTE SA");
        assertThat(result.displayName()).isEqualTo("Agence locale");
        assertThat(result.city()).isEqualTo("LYON");
        assertThat(result.address()).isEqualTo("1 RUE TEST 69001 LYON");
        assertThat(result.postalCode()).isEqualTo("69001");
        assertThat(result.activityCode()).isEqualTo("53.10Z");
        assertThat(result.companyActive()).isTrue();
        assertThat(result.establishmentActive()).isFalse();
        assertThat(result.sizeRange()).isEqualTo("1 000 +");
        assertThat(result.employeeRangeYear()).isEqualTo("2024");
        assertThat(result.emailDomain()).isNull();
        assertThat(result.activity()).isNull();
        assertThat(result.sector()).isNull();
        server.verify();
    }

    @Test
    void allowsPartialInformationAndDoesNotInventAnEmployeeRange() {
        respond("""
                {"results":[{"siren":"356000000","nom_complet":"LA POSTE",
                  "tranche_effectif_salarie":"11",
                  "siege":{"siret":"35600000000048"}}]}
                """);
        var result = service.lookup(SIRET);
        assertThat(result.displayName()).isEqualTo("LA POSTE");
        assertThat(result.city()).isNull();
        assertThat(result.sizeRange()).isNull();
        assertThat(result.employeeRangeCode()).isEqualTo("11");
        assertThat(result.companyActive()).isNull();
        assertThat(result.establishmentActive()).isNull();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"results\":[]}",
            "{\"results\":[{\"siren\":\"356000000\",\"nom_complet\":\"LA POSTE\",\"siege\":{\"siret\":\"35600000000056\"}}]}"
    })
    void returnsNotFoundForEmptyResultsOrDifferentSiret(String json) {
        respond(json);
        assertStatus(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "not json", "{\"results\":{}}",
            "{\"results\":[{\"siren\":\"999999999\",\"nom_complet\":\"Other\",\"siege\":{\"siret\":\"35600000000048\"}}]}"})
    void malformedResponsesAreUnavailableRatherThanNotFound(String json) {
        respond(json);
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 429, 500, 503})
    void upstreamHttpErrorsAreUnavailable(int status) {
        server.expect(requestTo("https://registry.test/search?q=" + SIRET))
                .andRespond(withStatus(HttpStatus.valueOf(status)));
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void timeoutIsUnavailable() {
        server.expect(requestTo("https://registry.test/search?q=" + SIRET))
                .andRespond(withException(new SocketTimeoutException("read timed out")));
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private void respond(String json) {
        server.expect(requestTo("https://registry.test/search?q=" + SIRET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void assertStatus(HttpStatus expected) {
        assertThatThrownBy(() -> service.lookup(SIRET))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(expected));
        server.verify();
    }
}
