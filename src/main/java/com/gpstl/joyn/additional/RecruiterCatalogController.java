package com.gpstl.joyn.additional;

import com.gpstl.joyn.joboffer.dto.JobOfferResponse;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;
import com.gpstl.joyn.joboffer.service.JobAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/recruiters/jobs")
public class RecruiterCatalogController {
    final ActorAccess access; final JdbcTemplate jdbc; final JobOfferRepository jobs; final JobAccessService jobAccess;
    public RecruiterCatalogController(ActorAccess access, JdbcTemplate jdbc, JobOfferRepository jobs, JobAccessService jobAccess) {
        this.access=access; this.jdbc=jdbc; this.jobs=jobs; this.jobAccess=jobAccess;
    }
    @GetMapping
    @Transactional(readOnly=true)
    public Map<String,Object> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="50") int limit) {
        long user=Long.parseLong(jwt.getSubject()); access.active(user,"RECRUITER");
        if(page<1 || limit<1 || limit>100) throw ActorAccess.error(400,"Invalid page or limit");
        var ids=jdbc.queryForList("""
            SELECT j.id FROM job_offers j JOIN recruiter_profiles r ON r.company_id=j.company_id
            JOIN companies c ON c.id=j.company_id
            WHERE r.user_id=? AND c.status<>'DELETED'
            ORDER BY j.created_at DESC, j.id DESC LIMIT ? OFFSET ?
            """,Long.class,user,limit,(long)(page-1)*limit);
        long count=jdbc.queryForObject("""
            SELECT count(*) FROM job_offers j JOIN recruiter_profiles r ON r.company_id=j.company_id
            JOIN companies c ON c.id=j.company_id
            WHERE r.user_id=? AND c.status<>'DELETED'
            """,Long.class,user);
        return Map.of("content",ids.stream().map(this::item).toList(),"page",page,"limit",limit,
            "totalElements",count,"hasNext",(long)page*limit<count);
    }
    @GetMapping("/{id}")
    @Transactional(readOnly=true)
    public Map<String,Object> detail(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) {
        long user=Long.parseLong(jwt.getSubject()); access.active(user,"RECRUITER");
        var offer=jobs.findById(id).orElseThrow(()->ActorAccess.error(404,"Offer not found"));
        jobAccess.checkCompanyAccess(user,offer);
        return item(id);
    }
    private Map<String,Object> item(long id) {
        var offer=jobs.findById(id).orElseThrow(()->ActorAccess.error(404,"Offer not found"));
        long count=jdbc.queryForObject("SELECT count(*) FROM applications WHERE job_offer_id=?",Long.class,id);
        // Flatten the existing response to preserve frontend JSON keys without duplicating the entity mapping.
        return Map.of("offer",JobOfferResponse.from(offer),"candidate_count",count,
            "company",Map.of("id",offer.getCompany().getId(),"name",offer.getCompany().getName()));
    }
}
