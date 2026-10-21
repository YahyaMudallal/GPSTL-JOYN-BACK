package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.dto.JobPatch;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Vérifie la présence des champs, les types et les valeurs autorisées du PATCH. */
class JobPatchTest {
    @Test void distinguishesOmittedAndExplicitNullFields() {
        Map<String, Object> body = new HashMap<>();
        body.put("location", null);
        JobPatch patch = JobPatch.parse(body);
        assertThat(patch.columns()).containsEntry("location", null).doesNotContainKey("title");
        assertThat(patch.skills()).isNull();
    }

    @Test void validatesAllErrorsTogether() {
        var ex = catchThrowableOfType(() -> JobPatch.parse(Map.of(
            "title", " ", "status", "ACTIVE", "id", 99, "remote_mode", "INVALID")), JobApiException.class);
        assertThat(ex.getStatus()).isEqualTo(400);
        assertThat(ex.getErrors()).containsOnlyKeys("title", "status", "id", "remote_mode");
    }

    @Test void rejectsProtectedFields() {
        for (String key : List.of("id", "company_id", "recruiter_id", "created_at", "updated_at", "deleted_at")) {
            assertThatThrownBy(() -> JobPatch.parse(Map.of(key, 1))).isInstanceOf(JobApiException.class);
        }
    }

    @Test void rejectsNullForRequiredFieldsAndWrongTypes() {
        Map<String, Object> nullTitle = new HashMap<>();
        nullTitle.put("title", null);
        assertThatThrownBy(() -> JobPatch.parse(nullTitle)).isInstanceOf(JobApiException.class);
        for (Map<String, Object> body : List.<Map<String, Object>>of(
                Map.of("salary_min", "100"), Map.of("salary_min", -1),
                Map.of("salary_max", 12.345), Map.of("salary_max", 10000000000L),
                Map.of("contract_type_id", 1.5), Map.of("start_date", "2026-99-99"),
                Map.of("skills", List.of(12)), Map.of("skills", List.of(" ")))) {
            assertThatThrownBy(() -> JobPatch.parse(body)).isInstanceOf(JobApiException.class);
        }
    }

    @Test void normalizesSkillDuplicatesAndAllowsClearing() {
        assertThat(JobPatch.parse(Map.of("skills", List.of("Go", " Go ", "go", "Python"))).skills())
            .containsExactly("Go", "Python");
        assertThat(JobPatch.parse(Map.of("skills", List.of())).skills()).isEmpty();
    }

    @Test void acceptsDatabaseFormatsAndEmptyPatch() {
        var patch = JobPatch.parse(Map.of("salary_min", 52000, "status", "PUBLISHED",
            "remote_mode", "HYBRID", "experience_level_required", "SENIOR", "contract_type_id", 1));
        assertThat(patch.columns()).containsEntry("salary_min", new BigDecimal("52000"));
        assertThat(JobPatch.parse(Map.of()).columns()).isEmpty();
    }
}
