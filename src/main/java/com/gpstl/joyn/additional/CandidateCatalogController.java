package com.gpstl.joyn.additional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Searchable catalog is separate from the recommendation deck; no synthetic match scores. */
@RestController
@RequestMapping("/api/v1/candidates/catalog")
public class CandidateCatalogController {
    private final ActorAccess access;
    private final JdbcTemplate jdbc;
    public CandidateCatalogController(ActorAccess access, JdbcTemplate jdbc) {this.access=access;this.jdbc=jdbc;}

    @GetMapping
    @Transactional(readOnly=true)
    public Map<String,Object> list(@AuthenticationPrincipal Jwt jwt,
         @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int limit,
         @RequestParam(required=false) String q, @RequestParam(required=false) String location,
         @RequestParam(name="contract_type",required=false) String contract,
         @RequestParam(required=false) String remote, @RequestParam(name="rhythm_type",required=false) String rhythm,
         @RequestParam(name="salary_min",required=false) Integer salaryMin) {
        long user=Long.parseLong(jwt.getSubject());access.active(user,"CANDIDATE");
        if(page<1||limit<1||limit>50||salaryMin!=null&&(salaryMin<0||salaryMin>10000000))
           throw ActorAccess.error(400,"Invalid pagination or salary");
        if(remote!=null&&!Set.of("REMOTE","HYBRID","ONSITE").contains(remote))
           throw ActorAccess.error(400,"Invalid work mode");
        StringBuilder where=new StringBuilder("""
            FROM job_offers j JOIN companies c ON c.id=j.company_id
            JOIN contract_types ct ON ct.id=j.contract_type_id
            WHERE j.deleted_at IS NULL AND j.status='PUBLISHED'
              AND c.status <> 'DELETED' AND (j.expires_at IS NULL OR j.expires_at > NOW())
            """);
        List<Object> args=new ArrayList<>();
        if(q!=null&&!q.isBlank()) {where.append(" AND (j.title ILIKE ? OR j.description ILIKE ? OR c.name ILIKE ?)");
            var term="%"+safe(q,150)+"%"; args.add(term);args.add(term);args.add(term);}
        if(location!=null&&!location.isBlank()) {where.append(" AND j.location ILIKE ?");args.add("%"+safe(location,100)+"%");}
        if(contract!=null&&!contract.isBlank()) {where.append(" AND ct.code=?");args.add(safe(contract,40));}
        if(remote!=null) {where.append(" AND j.remote_mode=?");args.add(remote);}
        if(rhythm!=null&&!rhythm.isBlank()) {where.append(" AND j.rhythm_type=?");args.add(safe(rhythm,60));}
        if(salaryMin!=null) {where.append(" AND j.salary_max>=?");args.add(salaryMin);}
        long total=jdbc.queryForObject("SELECT COUNT(*) "+where,Long.class,args.toArray());
        var rowArgs=new ArrayList<>(args);rowArgs.add(limit);rowArgs.add((long)(page-1)*limit);
        var content=jdbc.queryForList("""
            SELECT j.id,j.title,j.description,j.location,j.remote_mode,j.salary_min,j.salary_max,
                   j.salary_currency,j.salary_period,j.rhythm_type,j.start_date,j.duration_months,
                   j.degree_level_required,j.experience_level_required,ct.code AS contract_type,
                   c.id AS company_id,c.name AS company_name,c.sector,j.published_at
            """+where+" ORDER BY j.published_at DESC NULLS LAST,j.id DESC LIMIT ? OFFSET ?",rowArgs.toArray());
        return Map.of("content",content,"page",page,"limit",limit,"totalElements",total,"hasNext",(long)page*limit<total);
    }
    private static String safe(String text,int limit) {
        if(text.length()>limit||text.contains("%")||text.contains("_")) throw ActorAccess.error(400,"Invalid search term");
        return text.trim();
    }
}
