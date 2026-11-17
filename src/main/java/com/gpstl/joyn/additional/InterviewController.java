package com.gpstl.joyn.additional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/interviews")
public class InterviewController {
    private final JdbcTemplate jdbc; private final ActorAccess access;
    private final com.gpstl.joyn.application.service.ApplicationService applications;
    public InterviewController(JdbcTemplate jdbc,ActorAccess access,com.gpstl.joyn.application.service.ApplicationService applications) {this.jdbc=jdbc;this.access=access;this.applications=applications;}
    public record Proposal(@NotNull @Positive Long applicationId, @NotNull @Future OffsetDateTime scheduledAt,
        @Min(5) @Max(480) int durationMinutes, @NotBlank @Pattern(regexp="ONSITE|VIDEO|PHONE") String mode,
        @Size(max=1000) String locationOrLink) {}
    public record Decision(@NotBlank @Pattern(regexp="CONFIRMED|DECLINED|CANCELLED|DONE") String status) {}
    private static final String FROM="""
        FROM interviews i JOIN applications a ON a.id=i.application_id
        JOIN candidate_profiles cp ON cp.id=a.candidate_id
        JOIN job_offers j ON j.id=a.job_offer_id
        JOIN recruiter_profiles rp ON rp.id=j.recruiter_id
        JOIN companies co ON co.id=j.company_id
        """;
    private static final String FIELDS="""
        SELECT i.id, i.application_id AS "applicationId", i.scheduled_at AS "scheduledAt",
        i.duration_minutes AS "durationMinutes", i.mode, i.location_or_link AS "locationOrLink", i.status,
        j.title AS "jobTitle", co.name AS "companyName", cp.first_name AS "firstName", cp.last_name AS "lastName",
        cp.user_id AS "candidateUserId", rp.user_id AS "recruiterUserId", i.proposed_by AS "proposedBy"
        """;
    private Map<String,Object> application(long user,long id) {
        var rows=jdbc.queryForList("""
            SELECT cp.user_id AS candidate_user, rp.user_id AS recruiter_user, j.company_id, s.code,
                   co.status AS company_status
            FROM applications a JOIN candidate_profiles cp ON cp.id=a.candidate_id
            JOIN job_offers j ON j.id=a.job_offer_id JOIN recruiter_profiles rp ON rp.id=j.recruiter_id
            JOIN companies co ON co.id=j.company_id JOIN application_statuses s ON s.id=a.status_id
            WHERE a.id=?
            """,id);
        if(rows.isEmpty())throw ActorAccess.error(404,"Application not found");
        var row=rows.getFirst(); String role=access.active(user,"CANDIDATE","RECRUITER");
        boolean allowed=role.equals("CANDIDATE") ? number(row,"candidate_user")==user :
            jdbc.queryForObject("SELECT count(*) FROM recruiter_profiles WHERE user_id=? AND company_id=?",Long.class,user,row.get("company_id"))>0;
        if(!allowed || "DELETED".equals(row.get("company_status")))throw ActorAccess.error(403,"Application is not accessible");
        return row;
    }
    static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    @GetMapping
    @Transactional(readOnly=true)
    public Map<String,Object> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="50") int limit) {
        long user=Long.parseLong(jwt.getSubject());String role=access.active(user,"CANDIDATE","RECRUITER");
        if(page<1||limit<1||limit>100)throw ActorAccess.error(400,"Invalid page or limit");
        String condition=role.equals("CANDIDATE") ? " cp.user_id=? " :
            " EXISTS(SELECT 1 FROM recruiter_profiles owner WHERE owner.user_id=? AND owner.company_id=j.company_id) ";
        String where=" WHERE co.status<>'DELETED' AND "+condition;
        long total=jdbc.queryForObject("SELECT count(*) "+FROM+where,Long.class,user);
        return Map.of("content",jdbc.queryForList(FIELDS+FROM+where+" ORDER BY i.scheduled_at, i.id LIMIT ? OFFSET ?",user,limit,(long)(page-1)*limit).stream().map(this::normalize).toList(),
            "page",page,"totalElements",total,"hasNext",(long)page*limit<total);
    }
    @PostMapping
    @Transactional
    public Map<String,Object> propose(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Proposal body) {
        long user=Long.parseLong(jwt.getSubject());access.active(user,"RECRUITER");
        jdbc.queryForList("SELECT id FROM applications WHERE id=? FOR UPDATE",body.applicationId());
        var a=application(user,body.applicationId());
        if(List.of("WITHDRAWN","REJECTED","ACCEPTED").contains(a.get("code")))throw ActorAccess.error(409,"Application is no longer in progress");
        long candidate=number(a,"candidate_user");access.active(candidate,"CANDIDATE");
        // A stable lock order serializes overlapping proposals for either participant.
        for(long id:java.util.stream.LongStream.of(user,candidate).sorted().toArray())
            jdbc.execute("SELECT pg_advisory_xact_lock("+id+")");
        long conflicts=jdbc.queryForObject("""
            SELECT count(*) FROM interviews i JOIN applications a ON a.id=i.application_id
            JOIN candidate_profiles cp ON cp.id=a.candidate_id
            WHERE i.status IN ('PROPOSED','CONFIRMED') AND (i.proposed_by=? OR cp.user_id=?)
              AND i.scheduled_at < ? AND i.scheduled_at + i.duration_minutes * interval '1 minute' > ?
            """,Long.class,user,candidate,body.scheduledAt().plusMinutes(body.durationMinutes()),body.scheduledAt());
        if(conflicts>0)throw ActorAccess.error(409,"An interview already occupies this time slot");
        long id=jdbc.queryForObject("""
            INSERT INTO interviews(application_id, proposed_by, scheduled_at, duration_minutes, mode, location_or_link)
            VALUES(?,?,?,?,?,?) RETURNING id
            """,Long.class,body.applicationId(),user,body.scheduledAt(),body.durationMinutes(),body.mode(),body.locationOrLink());
        applications.updateStatus(user,body.applicationId(),new com.gpstl.joyn.application.dto.UpdateApplicationStatusRequest("INTERVIEW"));
        notify(candidate,"Proposition d’entretien", "Un recruteur vous propose un créneau. Consultez votre agenda pour répondre.");
        return detail(user,id);
    }
    @PatchMapping("/{id}/status")
    @Transactional
    public Map<String,Object> decide(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody Decision body) {
        long user=Long.parseLong(jwt.getSubject());
        var locked=jdbc.queryForList("SELECT application_id FROM interviews WHERE id=? FOR UPDATE",id);
        if(locked.isEmpty())throw ActorAccess.error(404,"Interview not found");
        var applicationState=application(user,number(locked.getFirst(),"application_id"));var row=detail(user,id);
        if(body.status().equals("CONFIRMED") && List.of("WITHDRAWN","REJECTED","ACCEPTED").contains(applicationState.get("code")))throw ActorAccess.error(409,"Application is no longer in progress");
        String current=(String)row.get("status"),next=body.status();boolean candidate=number(row,"candidateUserId")==user;
        boolean allowed=switch(next) {
            case "CONFIRMED" -> candidate && current.equals("PROPOSED") && Instant.parse((String)row.get("scheduledAt")).isAfter(Instant.now());
            case "DECLINED" -> candidate && current.equals("PROPOSED");
            case "CANCELLED" -> List.of("PROPOSED","CONFIRMED").contains(current);
            case "DONE" -> !candidate && current.equals("CONFIRMED") &&
                Instant.parse((String)row.get("scheduledAt")).isBefore(Instant.now());
            default -> false;
        };
        if(!allowed)throw ActorAccess.error(409,"Invalid interview transition");
        jdbc.update("UPDATE interviews SET status=? WHERE id=?",next,id);
        notify(candidate?number(row,"proposedBy"):number(row,"candidateUserId"),"Entretien : "+next,"Le statut de votre entretien a changé.");
        return detail(user,id);
    }
    @GetMapping("/{id}")
    @Transactional(readOnly=true)
    public Map<String,Object> get(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return detail(Long.parseLong(jwt.getSubject()),id);}
    private Map<String,Object> detail(long user,long id) {
        var rows=jdbc.queryForList(FIELDS+FROM+" WHERE i.id=?",id);
        if(rows.isEmpty())throw ActorAccess.error(404,"Interview not found");
        var row=rows.getFirst();application(user,number(row,"applicationId"));return normalize(row);
    }
    private Map<String,Object> normalize(Map<String,Object> row) {
        var copy=new LinkedHashMap<String,Object>(row);
        copy.replaceAll((key,value)-> value instanceof java.sql.Timestamp t ? t.toInstant().toString() :
            value instanceof OffsetDateTime t ? t.toInstant().toString() : value);
        return copy;
    }
    private void notify(long user,String title,String message) {
        jdbc.update("INSERT INTO notifications(user_id,type,title,message,link) VALUES(?,'INTERVIEW',?,?,?)",user,title,message,"/interviews");
    }
}
