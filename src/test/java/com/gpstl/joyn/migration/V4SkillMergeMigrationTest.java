package com.gpstl.joyn.migration;

import com.gpstl.joyn.joboffer.JobDatabaseTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration V4 sur une base existante qui contient des compétences ne différant que par la casse.
 * Flyway est lancé à la main sur un schéma dédié : d'abord jusqu'à V2, puis on insère les doublons,
 * puis on applique les migrations jusqu’à V4 comme le ferait un déploiement.
 */
class V4SkillMergeMigrationTest extends JobDatabaseTestSupport {
    private static final String SCHEMA = "job_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { configureDatabase(registry, SCHEMA); }

    @Autowired DataSource dataSource;

    private Flyway flyway(String schema, String target) {
        var configuration = Flyway.configure().dataSource(dataSource).schemas(schema).locations("classpath:db/migration");
        return (target == null ? configuration : configuration.target(target)).load();
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)   // Flyway utilise ses propres connexions
    void mergesCaseDuplicateSkillsBeforeCreatingTheUniqueIndex() {
        String s = "job_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try {
            flyway(s, "2").migrate();

            long java = id("INSERT INTO " + s + ".skills(name) VALUES ('Java') RETURNING id");
            long lower = id("INSERT INTO " + s + ".skills(name) VALUES ('java') RETURNING id");
            long upper = id("INSERT INTO " + s + ".skills(name) VALUES ('JAVA') RETURNING id");
            long go = id("INSERT INTO " + s + ".skills(name) VALUES ('Go') RETURNING id");
            long recruiterUser = id("INSERT INTO " + s + ".users(email, password_hash, role) VALUES ('r@x.test', 'h', 'RECRUITER') RETURNING id");
            long c1User = id("INSERT INTO " + s + ".users(email, password_hash, role) VALUES ('c1@x.test', 'h', 'CANDIDATE') RETURNING id");
            long c2User = id("INSERT INTO " + s + ".users(email, password_hash, role) VALUES ('c2@x.test', 'h', 'CANDIDATE') RETURNING id");
            long company = id("INSERT INTO " + s + ".companies(name) VALUES ('Acme') RETURNING id");
            long recruiterProfile = id("INSERT INTO " + s + ".recruiter_profiles(user_id, company_id, first_name, last_name) VALUES (?, ?, 'R', 'R') RETURNING id", recruiterUser, company);
            long cdi = id("SELECT id FROM " + s + ".contract_types WHERE code = 'CDI'");
            String offer = "INSERT INTO " + s + ".job_offers(company_id, recruiter_id, contract_type_id, title, description) VALUES (?, ?, ?, ?, 'd') RETURNING id";
            long offerA = id(offer, company, recruiterProfile, cdi, "A");
            long offerB = id(offer, company, recruiterProfile, cdi, "B");
            long c1 = id("INSERT INTO " + s + ".candidate_profiles(user_id, first_name, last_name) VALUES (?, 'C', '1') RETURNING id", c1User);
            long c2 = id("INSERT INTO " + s + ".candidate_profiles(user_id, first_name, last_name) VALUES (?, 'C', '2') RETURNING id", c2User);
            String jobLink = "INSERT INTO " + s + ".job_offer_skills(job_offer_id, skill_id, required_level) VALUES (?, ?, ?)";
            jdbc.update(jobLink, offerA, java, "BASIC");
            jdbc.update(jobLink, offerA, lower, "ADVANCED");        // offre A : deux variantes
            jdbc.update(jobLink, offerA, go, "BASIC");
            jdbc.update(jobLink, offerB, lower, "INTERMEDIATE");    // offre B : seulement la variante
            String candidateLink = "INSERT INTO " + s + ".candidate_skills(candidate_id, skill_id, level) VALUES (?, ?, ?)";
            jdbc.update(candidateLink, c1, upper, "LEARNING");
            jdbc.update(candidateLink, c1, java, "BASIC");          // candidat 1 : deux variantes
            jdbc.update(candidateLink, c2, upper, "ADVANCED");      // candidat 2 : seulement la variante

            // Avant la correction, cette migration échouait sur l'index unique LOWER(name).
            flyway(s, null).migrate();

            assertThat(jdbc.queryForList("SELECT name FROM " + s + ".skills ORDER BY id", String.class)).containsExactly("Java", "Go");
            // Liens reportés sur la compétence conservée, en gardant le niveau le plus élevé.
            assertThat(jdbc.queryForObject("SELECT required_level FROM " + s + ".job_offer_skills WHERE job_offer_id = ? AND skill_id = ?", String.class, offerA, java)).isEqualTo("ADVANCED");
            assertThat(jdbc.queryForObject("SELECT required_level FROM " + s + ".job_offer_skills WHERE job_offer_id = ? AND skill_id = ?", String.class, offerB, java)).isEqualTo("INTERMEDIATE");
            assertThat(jdbc.queryForObject("SELECT required_level FROM " + s + ".job_offer_skills WHERE job_offer_id = ? AND skill_id = ?", String.class, offerA, go)).isEqualTo("BASIC");
            assertThat(jdbc.queryForObject("SELECT level FROM " + s + ".candidate_skills WHERE candidate_id = ? AND skill_id = ?", String.class, c1, java)).isEqualTo("BASIC");
            assertThat(jdbc.queryForObject("SELECT level FROM " + s + ".candidate_skills WHERE candidate_id = ? AND skill_id = ?", String.class, c2, java)).isEqualTo("ADVANCED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + s + ".job_offer_skills", Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + s + ".candidate_skills", Integer.class)).isEqualTo(2);
            // L'index existe et refuse désormais une nouvelle variante.
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE schemaname = ? AND indexname = 'uq_skills_name_lower'", Integer.class, s)).isEqualTo(1);
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + s + " CASCADE");
        }
    }
}
