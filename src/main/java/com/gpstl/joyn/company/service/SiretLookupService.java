package com.gpstl.joyn.company.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gpstl.joyn.company.dto.SiretLookupResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SiretLookupService {
    private final RestClient client;

    public SiretLookupService(@Qualifier("companyLookupRestClient") RestClient client) {
        this.client = client;
    }

    public SiretLookupResponse lookup(String siret) {
        // L'existence est confirmée par le registre, y compris les cas particuliers
        // de numérotation qui ne suivent pas tous la même clé de contrôle.
        if (siret == null || !siret.matches("[0-9]{14}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un numéro SIRET doit contenir exactement 14 chiffres, sans espaces.");
        }

        SearchResult search;
        try {
            search = client.get()
                    .uri(builder -> builder.path("/search").queryParam("q", siret).build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(SearchResult.class);
        } catch (RestClientException exception) {
            throw unavailable();
        }
        if (search == null || search.results() == null) {
            throw unavailable();
        }

        for (LegalUnit unit : search.results()) {
            if (unit == null) continue;
            Establishment match = null;
            if (unit.establishments() != null) {
                match = unit.establishments().stream()
                        .filter(item -> item != null && siret.equals(item.siret()))
                        .findFirst().orElse(null);
            }
            // Le siège ne convient que si son SIRET est exactement celui demandé.
            if (match == null && unit.headOffice() != null
                    && siret.equals(unit.headOffice().siret())) {
                match = unit.headOffice();
            }
            if (match != null) return response(siret, unit, match);
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Aucune information d'entreprise trouvée pour ce SIRET dans les données publiques.");
    }

    private SiretLookupResponse response(String siret, LegalUnit unit, Establishment item) {
        String legalName = first(unit.legalName(), unit.fullName());
        if (legalName == null || !siret.substring(0, 9).equals(unit.siren())) {
            throw unavailable();
        }
        String brand = item.brands() == null ? null
                : item.brands().stream().filter(value -> value != null && !value.isBlank())
                        .findFirst().orElse(null);
        return new SiretLookupResponse(
                siret, unit.siren(), legalName,
                first(item.commercialName(), brand, unit.fullName(), legalName),
                first(item.city()), first(item.address()), first(item.postalCode()),
                first(item.activityCode(), unit.activityCode()),
                null, null, null,
                sizeRange(unit.employeeRange()), unit.employeeRange(), unit.employeeYear(),
                active(unit.status(), "C"), active(item.status(), "F"));
    }

    private static Boolean active(String status, String closed) {
        if ("A".equals(status)) return true;
        if (closed.equals(status)) return false;
        return null;
    }

    // Tranches de l'unité légale, pas celles du seul établissement.
    // Les tranches qui chevauchent les choix de l'UI (10–19, par exemple)
    // restent nulles pour ne pas préremplir une taille inexacte.
    private static String sizeRange(String code) {
        if (code == null) return null;
        return switch (code) {
            case "01", "02", "03" -> "1 – 10";
            case "12" -> "11 – 49";
            case "21", "22", "31" -> "50 – 249";
            case "32", "41" -> "250 – 999";
            case "42", "51", "52", "53" -> "1 000 +";
            default -> null;
        };
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Le service de recherche d'entreprise est temporairement indisponible. Réessayez plus tard.");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResult(List<LegalUnit> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LegalUnit(
            String siren,
            @JsonProperty("nom_complet") String fullName,
            @JsonProperty("nom_raison_sociale") String legalName,
            @JsonProperty("activite_principale") String activityCode,
            @JsonProperty("etat_administratif") String status,
            @JsonProperty("tranche_effectif_salarie") String employeeRange,
            @JsonProperty("annee_tranche_effectif_salarie") String employeeYear,
            @JsonProperty("matching_etablissements") List<Establishment> establishments,
            @JsonProperty("siege") Establishment headOffice) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Establishment(
            String siret,
            @JsonProperty("nom_commercial") String commercialName,
            @JsonProperty("liste_enseignes") List<String> brands,
            @JsonProperty("libelle_commune") String city,
            @JsonProperty("adresse") String address,
            @JsonProperty("code_postal") String postalCode,
            @JsonProperty("activite_principale") String activityCode,
            @JsonProperty("etat_administratif") String status) {}
}
