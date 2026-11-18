package com.gpstl.joyn.additional;

import com.gpstl.joyn.joboffer.dto.JobOfferResponse;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/candidates/offers")
public class CandidateOfferDetailsController {
    private final ActorAccess access;
    private final JdbcTemplate jdbc;
    private final JobOfferRepository offers;

    public CandidateOfferDetailsController(ActorAccess access, JdbcTemplate jdbc, JobOfferRepository offers) {
        this.access = access;
        this.jdbc = jdbc;
        this.offers = offers;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Map<String, Object> get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        long user = Long.parseLong(jwt.getSubject());
        access.active(user, "CANDIDATE");
        var rows = jdbc.queryForList("""
                SELECT c.name FROM job_offers j JOIN companies c ON c.id=j.company_id
                WHERE j.id=? AND (
                    (j.status='PUBLISHED' AND j.deleted_at IS NULL AND c.status <> 'DELETED'
                     AND (j.expires_at IS NULL OR j.expires_at > now()))
                    OR EXISTS (SELECT 1 FROM applications a JOIN candidate_profiles p ON p.id=a.candidate_id
                               WHERE a.job_offer_id=j.id AND p.user_id=?))
                """, id, user);
        if (rows.isEmpty()) throw ActorAccess.error(404, "Offre indisponible");
        var offer = offers.findById(id).orElseThrow(() -> ActorAccess.error(404, "Offre indisponible"));
        return Map.of("offer", JobOfferResponse.from(offer), "company", Map.of("name", rows.getFirst().get("name")));
    }
}
