package com.gpstl.joyn.additional;

import com.gpstl.joyn.company.service.CompanyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminBulkModerationController {
    private final ActorAccess access;
    private final AdminStepUp stepUp;
    private final JdbcTemplate jdbc;
    private final CompanyService companies;

    public AdminBulkModerationController(ActorAccess access, AdminStepUp stepUp, JdbcTemplate jdbc, CompanyService companies) {
        this.access = access;
        this.stepUp = stepUp;
        this.jdbc = jdbc;
        this.companies = companies;
    }

    public record Removal(
            @NotEmpty @Size(max=100) List<@NotNull @Positive Long> ids,
            @NotBlank @Size(max=72) String password,
            @NotBlank @Pattern(regexp="[0-9]{6}") String code,
            @NotBlank String confirmation) {}

    @PostMapping("/{kind:users|companies}/bulk-delete")
    @Transactional
    public Map<String, Integer> delete(Authentication authentication, @PathVariable String kind,
            @Valid @RequestBody Removal body) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        long actor = Long.parseLong(jwt.getSubject());
        access.active(actor, "ADMIN");
        if (!"SUPPRIMER".equals(body.confirmation())) throw ActorAccess.error(400, "Confirmation requise");
        List<Long> ids = body.ids().stream().distinct().sorted().toList();
        if (ids.size() != body.ids().size() || ("users".equals(kind) && ids.contains(actor))) {
            throw ActorAccess.error(400, "Sélection invalide");
        }
        stepUp.verify(actor, body.password(), body.code());

        for (long id : ids) {
            if ("users".equals(kind)) {
                int result = jdbc.update("""
                        UPDATE users SET status='SUSPENDED', deleted_at=now(), tokens_valid_after=now()
                        WHERE id=? AND deleted_at IS NULL AND role IN ('CANDIDATE','RECRUITER')
                        """, id);
                if (result != 1) throw ActorAccess.error(409, "Compte indisponible");
                jdbc.update("UPDATE candidate_profiles SET is_visible_to_recruiters=false WHERE user_id=?", id);
            } else {
                jdbc.queryForList("SELECT id FROM companies WHERE id=? FOR UPDATE", id);
                companies.deleteCompany(id, "Suppression groupée", authentication);
            }
            jdbc.update("INSERT INTO admin_removal_audit(actor_user_id, target_type, target_id) VALUES(?,?,?)",
                    actor, kind, id);
        }
        return Map.of("deleted", ids.size());
    }
}
