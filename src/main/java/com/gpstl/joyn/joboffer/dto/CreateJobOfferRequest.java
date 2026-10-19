package com.gpstl.joyn.joboffer.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Corps de la requête POST /api/v1/recruiters/jobs.
 * Les noms JSON sont en snake_case, comme dans la spécification R58.
 */
@Schema(description = "Données d'une nouvelle offre")
public record CreateJobOfferRequest(

        @Schema(example = "Développeur·se Full-Stack en alternance")
        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 200, message = "Le titre ne doit pas dépasser 200 caractères")
        String title,

        @Schema(example = "APPRENTICESHIP", description = "Code de contract_types : CDI, CDD, INTERNSHIP, APPRENTICESHIP, PROFESSIONALIZATION, FREELANCE, TEMPORARY")
        @JsonProperty("contract_type")
        @NotBlank(message = "Le type de contrat est obligatoire")
        String contractType,

        @Schema(example = "Paris 9e")
        @NotBlank(message = "Le lieu est obligatoire")
        String location,

        @Schema(example = "HYBRID", allowableValues = {"ONSITE", "HYBRID", "REMOTE"})
        @JsonProperty("remote_policy")
        @NotBlank(message = "Le mode de travail est obligatoire")
        @Pattern(regexp = "ONSITE|HYBRID|REMOTE", message = "Valeurs acceptées : ONSITE, HYBRID, REMOTE")
        String remotePolicy,

        @Schema(example = "JUNIOR", description = "JUNIOR, MID ou SENIOR (MID_LEVEL est aussi accepté)")
        @JsonProperty("experience_level")
        @Pattern(regexp = "JUNIOR|MID|SENIOR|JUNIOR_LEVEL|MID_LEVEL|SENIOR_LEVEL",
                message = "Valeurs acceptées : JUNIOR, MID, SENIOR")
        String experienceLevel,

        @Schema(example = "Rejoins l'équipe Paiements pour construire le nouveau tableau de bord marchands.")
        @NotBlank(message = "La description est obligatoire")
        String description,

        @Schema(example = "1450")
        @JsonProperty("salary_min")
        @PositiveOrZero(message = "Le salaire minimum doit être positif")
        BigDecimal salaryMin,

        @Schema(example = "1600")
        @JsonProperty("salary_max")
        @PositiveOrZero(message = "Le salaire maximum doit être positif")
        BigDecimal salaryMax,

        @Schema(example = "EUR", description = "Code ISO à 3 lettres, EUR par défaut")
        @Pattern(regexp = "[A-Z]{3}", message = "La devise doit être un code ISO à 3 lettres majuscules (ex. EUR)")
        String currency,

        @Schema(example = "MONTH", allowableValues = {"HOUR", "MONTH", "YEAR"}, description = "YEAR par défaut")
        @JsonProperty("salary_period")
        @Pattern(regexp = "HOUR|MONTH|YEAR", message = "Valeurs acceptées : HOUR, MONTH, YEAR")
        String salaryPeriod,

        @Schema(example = "[\"React\", \"TypeScript\", \"PostgreSQL\"]")
        @NotEmpty(message = "Au moins une compétence est obligatoire")
        List<@NotBlank(message = "Une compétence ne peut pas être vide")
             @Size(max = 100, message = "Une compétence ne doit pas dépasser 100 caractères") String> skills,

        // --- Champs facultatifs propres à l'alternance ---

        @Schema(example = "3 sem / 1 sem")
        @JsonProperty("rhythm_type")
        @Size(max = 100, message = "Le rythme ne doit pas dépasser 100 caractères")
        String rhythmType,

        @Schema(example = "2027-09-01")
        @JsonProperty("start_date")
        LocalDate startDate,

        @Schema(example = "24")
        @JsonProperty("duration_months")
        @Positive(message = "La durée doit être d'au moins 1 mois")
        @Max(value = 60, message = "La durée ne doit pas dépasser 60 mois")
        Integer durationMonths,

        @Schema(example = "BAC_5", allowableValues = {"BAC_2", "BAC_3", "BAC_4", "BAC_5"})
        @JsonProperty("degree_level_required")
        @Pattern(regexp = "BAC_2|BAC_3|BAC_4|BAC_5", message = "Valeurs acceptées : BAC_2, BAC_3, BAC_4, BAC_5")
        String degreeLevelRequired,

        @Schema(example = "PUBLISHED", allowableValues = {"DRAFT", "PUBLISHED"},
                description = "PUBLISHED par défaut ; DRAFT pour enregistrer un brouillon")
        @Pattern(regexp = "DRAFT|PUBLISHED", message = "Valeurs acceptées : DRAFT, PUBLISHED")
        String status
) {

    /** Règle métier : salary_max >= salary_min quand les deux sont renseignés. */
    @JsonIgnore
    @AssertTrue(message = "Le salaire maximum doit être supérieur ou égal au salaire minimum")
    public boolean isSalaryRangeValid() {
        return salaryMin == null || salaryMax == null || salaryMax.compareTo(salaryMin) >= 0;
    }
}
