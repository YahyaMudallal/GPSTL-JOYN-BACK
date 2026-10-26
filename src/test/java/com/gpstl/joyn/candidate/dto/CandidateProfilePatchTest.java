package com.gpstl.joyn.candidate.dto;

import com.gpstl.joyn.candidate.exception.CandidateProfileException;
import com.gpstl.joyn.candidate.model.Degree;
import com.gpstl.joyn.candidate.model.PostbacYear;
import com.gpstl.joyn.joboffer.model.RemoteMode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Validation du corps PATCH, sans Spring ni base de données.
 * Les corps sont lus avec Jackson pour obtenir les mêmes types (Integer, Double, List, Map)
 * que ceux reçus par le contrôleur.
 */
class CandidateProfilePatchTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @SuppressWarnings("unchecked")
    private CandidateProfilePatch parse(String body) {
        return CandidateProfilePatch.parse(json.readValue(body, Map.class));
    }

    private Map<String, String> errors(String body) {
        var error = catchThrowableOfType(CandidateProfileException.class, () -> parse(body));
        assertThat(error).as("400 attendu pour " + body).isNotNull();
        assertThat(error.getStatus()).isEqualTo(400);
        return error.getErrors();
    }

    @Test
    void parsesTheTicketExample() {
        var patch = parse("""
            {"first_name":"Yahya","last_name":"Mudallal","headline":"Ingénieur Logiciel & Observabilité",
             "phone":"+33612345678","location":"Paris, France","years_of_experience":2,
             "skills":["Go","Python","Docker","Prometheus"],
             "preferences":{"contract_types":["CDI","FREELANCE"],"remote_policy":"HYBRID","min_salary":48000,"currency":"EUR"},
             "bio":"Spécialisé dans l'architecture distribuée."}""");

        assertThat(patch.values())
                .containsEntry("first_name", "Yahya")
                .containsEntry("years_of_experience", (short) 2)
                .containsEntry("preferences.remote_policy", RemoteMode.HYBRID)
                .containsEntry("preferences.min_salary", new BigDecimal(48000))
                .containsEntry("preferences.currency", "EUR")
                // La liste des contrats est rangée à part, pas dans les champs simples.
                .doesNotContainKey("preferences.contract_types");
        assertThat(patch.skills()).containsExactly("Go", "Python", "Docker", "Prometheus");
        assertThat(patch.contractTypes()).containsExactly("CDI", "FREELANCE");
        assertThat(patch.experiences()).isNull();
        assertThat(patch.educations()).isNull();
        assertThat(patch.changesMatchingCriteria()).isTrue();
    }

    @Test
    void distinguishesOmittedAndExplicitNullFields() {
        var patch = parse("{\"bio\":null,\"links\":{\"github\":null}}");
        // Seuls les champs envoyés sont présents ; null signifie « effacer ».
        assertThat(patch.values()).containsOnlyKeys("bio", "links.github").containsEntry("bio", null);
        assertThat(patch.skills()).isNull();
        assertThat(patch.changesMatchingCriteria()).isFalse();
    }

    @Test
    void acceptsAnEmptyPatch() {
        var patch = parse("{}");
        assertThat(patch.values()).isEmpty();
        assertThat(patch.changesMatchingCriteria()).isFalse();
    }

    @Test
    void mergesNestedObjectsFieldByField() {
        var patch = parse("{\"preferences\":{\"min_salary\":50000},\"visibility\":{\"show_last_name\":false}}");
        assertThat(patch.values()).containsOnlyKeys("preferences.min_salary", "visibility.show_last_name");
        assertThat(patch.contractTypes()).isNull();
        assertThat(patch.changesMatchingCriteria()).isTrue();
    }

    @Test
    void normalizesClientFriendlyInput() {
        var patch = parse("""
            {"phone":"+33 6 12-34-56-78","headline":"  ","years_of_experience":2.0,
             "skills":["Go","go"," Python "],"preferences":{"contract_types":["cdi","CDI"]}}""");
        assertThat(patch.values())
                .containsEntry("phone", "+33612345678")
                .containsEntry("headline", null)              // chaîne vide = champ effacé
                .containsEntry("years_of_experience", (short) 2);
        assertThat(patch.skills()).containsExactly("Go", "Python");
        assertThat(patch.contractTypes()).containsExactly("CDI");
    }

    @Test
    void parsesSectionItemsAndToleratesIdsReturnedByGet() {
        var patch = parse("""
            {"experiences":[{"id":7,"title":" Stage SRE ","organization":"Beta","start_date":"2025-06-01","currently_working":true}],
             "educations":[{"id":3,"school_name":"Sorbonne","field_of_study":"Informatique","degree":"MASTERS",
                            "postbac_year":"BAC_5","start_year":2024,"end_year":2026}]}""");
        var experience = patch.experiences().get(0);
        assertThat(experience.title()).isEqualTo("Stage SRE");
        assertThat(experience.startDate()).isEqualTo(LocalDate.of(2025, 6, 1));
        assertThat(experience.currentlyWorking()).isTrue();
        var education = patch.educations().get(0);
        assertThat(education.degree()).isEqualTo(Degree.MASTERS);
        assertThat(education.postbacYear()).isEqualTo(PostbacYear.BAC_5);
        assertThat(education.endYear()).isEqualTo((short) 2026);
        assertThat(education.currentlyStudying()).isFalse();
    }

    @Test
    void emptyListsClearSections() {
        var patch = parse("{\"skills\":[],\"experiences\":[],\"educations\":[],\"preferences\":{\"contract_types\":[]}}");
        assertThat(patch.skills()).isEmpty();
        assertThat(patch.experiences()).isEmpty();
        assertThat(patch.educations()).isEmpty();
        assertThat(patch.contractTypes()).isEmpty();
    }

    @Test
    void rejectsProtectedUnknownAndMissingRequiredFields() {
        var errors = errors("{\"email\":\"x@y.fr\",\"id\":3,\"cv_url\":\"x\",\"foo\":1,\"first_name\":null,\"last_name\":\" \"}");
        assertThat(errors).containsOnlyKeys("email", "id", "cv_url", "foo", "first_name", "last_name");
        assertThat(errors.get("email")).startsWith("Protected field");
        assertThat(errors.get("foo")).isEqualTo("Unknown field");
        assertThat(errors.get("first_name")).isEqualTo("Required");
        assertThat(errors.get("last_name")).isEqualTo("Must not be blank");
    }

    @Test
    void reportsAllErrorsTogetherWithTheirPath() {
        var errors = errors("""
            {"phone":"0612","years_of_experience":-1,"skills":["Go","",3],
             "links":{"github":"ftp://x","twitter":"https://x.com"},
             "visibility":{"show_last_name":"yes"},
             "preferences":{"min_salary":2.5,"remote_policy":"FULL","currency":"eur","contract_types":["CDI","c-d"]},
             "alternance":"x",
             "experiences":[{"title":"","start_date":"2025-13-01"},
                            {"title":"A","start_date":"2025-06-01","end_date":"2025-01-01"},
                            {"title":"B","currently_working":true,"end_date":"2025-01-01"},"x"],
             "educations":[{"school_name":"S","field_of_study":"F","degree":"PHD","foo":1}]}""");
        assertThat(errors).containsOnlyKeys("phone", "years_of_experience", "skills[1]", "skills[2]",
                "links.github", "links.twitter", "visibility.show_last_name",
                "preferences.min_salary", "preferences.remote_policy", "preferences.currency",
                "preferences.contract_types[1]", "alternance",
                "experiences[0].title", "experiences[0].start_date", "experiences[1].end_date",
                "experiences[2].end_date", "experiences[3]", "educations[0].degree", "educations[0].foo");
        assertThat(errors.get("experiences[1].end_date")).isEqualTo("Must not be before start_date");
        assertThat(errors.get("experiences[2].end_date")).isEqualTo("Must be empty when currently_working is true");
        assertThat(errors.get("preferences.remote_policy")).isEqualTo("Allowed values: [HYBRID, ONSITE, REMOTE]");
    }

    @Test
    void keepsErrorsInTheOrderOfTheBody() {
        var errors = errors("{\"phone\":\"0612\",\"bio\":3,\"email\":\"x\"}");
        assertThat(errors.keySet()).containsExactly("phone", "bio", "email");
    }

    @Test
    void rejectsNullListsAndNonNullableNestedFields() {
        var errors = errors("{\"skills\":null,\"preferences\":{\"contract_types\":null,\"currency\":null},\"experiences\":{}}");
        assertThat(errors).containsOnlyKeys("skills", "preferences.contract_types", "preferences.currency", "experiences");
        assertThat(errors.get("skills")).contains("send []");
    }

    @Test
    void enforcesDatabaseLimits() {
        var errors = errors("{\"headline\":\"" + "a".repeat(256) + "\",\"preferences\":{\"min_salary\":10000000000}}");
        assertThat(errors).containsOnlyKeys("headline", "preferences.min_salary");
    }

    @Test
    void rejectsAMissingBody() {
        var error = catchThrowableOfType(CandidateProfileException.class, () -> CandidateProfilePatch.parse(null));
        assertThat(error.getStatus()).isEqualTo(400);
        assertThat(error.getErrors()).containsKey("body");
    }
}
