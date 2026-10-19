package com.gpstl.joyn.joboffer.repositories;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class JobUpdateRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JobUpdateRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean contractExists(int id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM contract_types WHERE id = :id)", Map.of("id", id), Boolean.class));
    }

    public void update(long jobId, Map<String, Object> columns) {
        // Column names come exclusively from JobPatch's allowlist, never raw request keys.
        if (columns.isEmpty()) {
            jdbc.update("UPDATE job_offers SET updated_at = NOW() WHERE id = :id", Map.of("id", jobId));
            return;
        }
        String assignments = columns.keySet().stream().map(key -> key + " = :" + key)
            .collect(Collectors.joining(", "));
        Map<String, Object> params = new HashMap<>(columns);
        params.put("id", jobId);
        jdbc.update("UPDATE job_offers SET " + assignments + ", updated_at = NOW() WHERE id = :id", params);
    }

    public void replaceSkills(long jobId, List<String> names) {
        // Keep the required_level of retained skills; new associations use the database default BASIC.
        List<Long> ids = new ArrayList<>();
        // A stable catalog lock order also avoids opposite skill orders deadlocking across offers.
        for (String name : names.stream().sorted().toList()) {
            List<Long> existing = jdbc.queryForList("SELECT id FROM skills WHERE LOWER(name) = LOWER(:name) ORDER BY id LIMIT 1",
                Map.of("name", name), Long.class);
            if (!existing.isEmpty()) {
                ids.add(existing.getFirst());
                continue;
            }
            jdbc.update("INSERT INTO skills(name) VALUES (:name) ON CONFLICT (name) DO NOTHING", Map.of("name", name));
            ids.add(jdbc.queryForObject("SELECT id FROM skills WHERE name = :name", Map.of("name", name), Long.class));
        }
        if (ids.isEmpty()) {
            jdbc.update("DELETE FROM job_offer_skills WHERE job_offer_id = :id", Map.of("id", jobId));
        } else {
            jdbc.update("DELETE FROM job_offer_skills WHERE job_offer_id = :id AND skill_id NOT IN (:skills)",
                Map.of("id", jobId, "skills", ids));
            for (Long skillId : ids) jdbc.update("""
                INSERT INTO job_offer_skills(job_offer_id, skill_id) VALUES (:id, :skill)
                ON CONFLICT (job_offer_id, skill_id) DO NOTHING
                """, Map.of("id", jobId, "skill", skillId));
        }
    }

    public Map<String, Object> response(long jobId) {
        Map<String, Object> job = jdbc.query("SELECT * FROM job_offers WHERE id = :id",
            Map.of("id", jobId), (rs, row) -> readJob(rs)).getFirst();
        job.put("skills", jdbc.queryForList("""
            SELECT s.name FROM skills s JOIN job_offer_skills js ON js.skill_id = s.id
            WHERE js.job_offer_id = :id ORDER BY s.name
            """, Map.of("id", jobId), String.class));
        return job;
    }

    private Map<String, Object> readJob(ResultSet rs) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            String field = rs.getMetaData().getColumnLabel(i);
            Object value = rs.getObject(i);
            if (value instanceof java.sql.Timestamp) value = rs.getObject(i, OffsetDateTime.class).toInstant().toString();
            if (value instanceof java.sql.Date date) value = date.toLocalDate().toString();
            result.put(field, value);
        }
        return result;
    }
}
