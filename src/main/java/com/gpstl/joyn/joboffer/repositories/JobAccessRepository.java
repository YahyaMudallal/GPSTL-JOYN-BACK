package com.gpstl.joyn.joboffer.repositories;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class JobAccessRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JobAccessRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Map<String, Object>> lockActive(long jobId) {
        return jdbc.query("SELECT * FROM job_offers WHERE id = :id AND deleted_at IS NULL FOR UPDATE",
            Map.of("id", jobId), (rs, row) -> readJob(rs)).stream().findFirst();
    }

    public Optional<Map<String, Object>> actor(long userId) {
        return jdbc.queryForList("""
            SELECT u.role, u.status, rp.company_id FROM users u
            LEFT JOIN recruiter_profiles rp ON rp.user_id = u.id WHERE u.id = :id
            """, Map.of("id", userId)).stream().findFirst();
    }

    public void audit(long userId, long jobId, String action, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("user", userId); params.put("job", jobId);
        params.put("action", action); params.put("reason", reason);
        jdbc.update("""
            INSERT INTO job_offer_audit(user_id, job_offer_id, action, reason)
            VALUES (:user, :job, :action, :reason)
            """, params);
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
