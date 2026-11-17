package com.gpstl.joyn.additional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminAccountController {
    final ActorAccess access;final JdbcTemplate jdbc;
    public AdminAccountController(ActorAccess access,JdbcTemplate jdbc){this.access=access;this.jdbc=jdbc;}
    public record StatusChange(@NotBlank @Pattern(regexp="ACTIVE|SUSPENDED") String status,@Size(max=1000) String reason) {}
    @PatchMapping("/{id}/status")
    @Transactional
    public Map<String,Object> status(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody StatusChange body){
        long actor=Long.parseLong(jwt.getSubject());access.active(actor,"ADMIN");
        if(id==actor)throw ActorAccess.error(403,"Cannot change your own status");
        var rows=jdbc.queryForList("SELECT role FROM users WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty())throw ActorAccess.error(404,"User not found");
        if("ADMIN".equals(rows.getFirst().get("role")))throw ActorAccess.error(403,"Administrator access is managed privately");
        if(body.status().equals("SUSPENDED") && (body.reason()==null||body.reason().trim().length()<5))throw ActorAccess.error(400,"A reason is required");
        jdbc.update("UPDATE users SET status=?,tokens_valid_after=now() WHERE id=?",body.status(),id);
        jdbc.update("INSERT INTO account_status_audit(actor_user_id,target_user_id,status,reason) VALUES(?,?,?,?)",actor,id,body.status(),body.reason());
        return Map.of("id",id,"status",body.status());
    }
}
