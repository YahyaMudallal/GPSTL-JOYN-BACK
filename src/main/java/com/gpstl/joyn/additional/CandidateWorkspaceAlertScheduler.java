package com.gpstl.joyn.additional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

/** In-app saved-search alerts and candidate-defined reminders, scheduled hourly. */
@Component
public class CandidateWorkspaceAlertScheduler {
    private static final Logger log=LoggerFactory.getLogger(CandidateWorkspaceAlertScheduler.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public CandidateWorkspaceAlertScheduler(JdbcTemplate jdbc,ObjectMapper mapper) {this.jdbc=jdbc;this.mapper=mapper;}

    @Scheduled(cron="${joyn.workspace.alerts-cron:0 0 * * * *}")
    @Transactional
    public void notifyCandidates() {
        try { notifySearches(); notifyReminders(); }
        catch(Exception e) { log.error("Workspace notifications failed",e); throw e; }
    }
    private void notifySearches() {
        var searches=jdbc.queryForList("""
            SELECT s.id,s.user_id,s.name,s.filters::text AS filters_json,s.last_checked_at
            FROM candidate_saved_searches s
            JOIN users u ON u.id=s.user_id AND u.status='ACTIVE'
            LEFT JOIN candidate_workspace_preferences w ON w.user_id=s.user_id
            LEFT JOIN notification_preferences p ON p.user_id=s.user_id
            WHERE s.alerts_enabled=TRUE AND COALESCE(w.alerts_enabled,TRUE)=TRUE
              AND COALESCE(p.new_offer_alerts,TRUE)=TRUE
            ORDER BY s.id LIMIT 500 FOR UPDATE OF s SKIP LOCKED
            """);
        OffsetDateTime now=OffsetDateTime.now();
        for(var search:searches) {
            long id=((Number)search.get("id")).longValue();
            long user=((Number)search.get("user_id")).longValue();
            var filters=parse((String)search.get("filters_json"));
            StringBuilder query=new StringBuilder("""
              SELECT j.id,j.title FROM job_offers j
              JOIN companies c ON c.id=j.company_id
              JOIN contract_types ct ON ct.id=j.contract_type_id
              WHERE j.status='PUBLISHED' AND j.deleted_at IS NULL AND c.status<>'DELETED'
                AND (j.expires_at IS NULL OR j.expires_at>NOW())
                AND j.created_at>? AND j.created_at<=?
              """);
            var args=new ArrayList<Object>();args.add(search.get("last_checked_at"));args.add(now);
            for(String field:List.of("location","contract_type","remote","rhythm_type","q")) {
                String val=""+filters.getOrDefault(field,"");
                if(val.isBlank()) continue;
                switch(field) {
                    case "location" -> {query.append(" AND j.location ILIKE ?");args.add("%"+val+"%");}
                    case "contract_type" -> {query.append(" AND ct.code=?");args.add(val);}
                    case "remote" -> {query.append(" AND j.remote_mode=?");args.add(val);}
                    case "rhythm_type" -> {query.append(" AND j.rhythm_type=?");args.add(val);}
                    case "q" -> {query.append(" AND (j.title ILIKE ? OR j.description ILIKE ? OR c.name ILIKE ?)");
                                 for(int i=0;i<3;i++)args.add("%"+val+"%");}
                }
            }
            Object requestedSalary=filters.get("salary_min");
            if(requestedSalary!=null && !"".equals(requestedSalary)) {
                try {
                    var salary=new java.math.BigDecimal(requestedSalary.toString());
                    if(salary.signum()>=0) {
                        query.append(" AND j.salary_max>=?");args.add(salary);
                    }
                } catch(NumberFormatException ignored) {
                    log.warn("Ignoring invalid salary filter for saved search {}",id);
                }
            }
            query.append(" ORDER BY j.created_at LIMIT 10");
            for(var job:jdbc.queryForList(query.toString(),args.toArray())) {
                jdbc.update("""
                  INSERT INTO notifications(user_id,type,title,message,link)
                  VALUES(?,'NEW_OFFER',?,?,?)
                  """,user,"Nouvelle offre correspondant à « "+search.get("name")+" »",
                  "Offre : "+job.get("title"),"/candidate/discover");
            }
            jdbc.update("UPDATE candidate_saved_searches SET last_checked_at=? WHERE id=?",now,id);
        }
    }
    private void notifyReminders() {
        var reminders=jdbc.queryForList("""
          SELECT n.user_id,n.application_id,n.note,j.title FROM candidate_application_notes n
          JOIN users u ON u.id=n.user_id AND u.status='ACTIVE'
          JOIN applications a ON a.id=n.application_id
          JOIN candidate_profiles cp ON cp.id=a.candidate_id AND cp.user_id=n.user_id
          JOIN job_offers j ON j.id=a.job_offer_id
          WHERE n.remind_at IS NOT NULL AND n.remind_at<=NOW()
          LIMIT 500 FOR UPDATE OF n SKIP LOCKED
          """);
        for(var r:reminders) {
            jdbc.update("""
              INSERT INTO notifications(user_id,type,title,message,link)
              VALUES(?,'SYSTEM',?,?,?)
              """,r.get("user_id"),"Rappel de candidature : "+r.get("title"),
              ""+r.get("note"),"/candidate/applications");
            jdbc.update("""
              UPDATE candidate_application_notes SET remind_at=NULL
              WHERE user_id=? AND application_id=?
              """,r.get("user_id"),r.get("application_id"));
        }
    }
    private Map<String,Object> parse(String json) {
        try {return mapper.readValue(json,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}
        catch(Exception e) {log.warn("Invalid saved search filters",e);return Map.of();}
    }
}
