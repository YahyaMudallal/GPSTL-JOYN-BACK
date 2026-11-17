package com.gpstl.joyn.additional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class ActorAccess {
    final JdbcTemplate jdbc;
    public ActorAccess(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public String active(long id, String... roles) {
        var users = jdbc.queryForList("SELECT role, status FROM users WHERE id=?", id);
        if (users.isEmpty()) throw error(401, "Account not found");
        var u = users.getFirst();
        String role = (String) u.get("role");
        if (!"ACTIVE".equals(u.get("status")) || (roles.length > 0 && !java.util.List.of(roles).contains(role)))
            throw error(403, "Account is not authorized");
        return role;
    }
    public static ResponseStatusException error(int code, String text) {
        return new ResponseStatusException(HttpStatus.valueOf(code), text);
    }
}
