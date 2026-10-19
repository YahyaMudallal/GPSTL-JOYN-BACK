package com.gpstl.joyn.joboffer.repositories;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class JobClosureRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JobClosureRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void close(long jobId) {
        jdbc.update("UPDATE job_offers SET status = 'CLOSED', deleted_at = NOW(), updated_at = NOW() WHERE id = :id",
            Map.of("id", jobId));
    }
}
