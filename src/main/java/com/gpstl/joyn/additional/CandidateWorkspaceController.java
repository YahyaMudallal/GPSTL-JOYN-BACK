package com.gpstl.joyn.additional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.*;

/** Persisted personal workspace: no AI, no cross-candidate access. */
@RestController
@RequestMapping("/api/v1/candidates/workspace")
public class CandidateWorkspaceController {
    private final ActorAccess access;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public CandidateWorkspaceController(ActorAccess access, JdbcTemplate jdbc, ObjectMapper json) {
        this.access = access;
        this.jdbc = jdbc;
        this.json = json;
    }

    private long candidate(Jwt jwt) {
        long id = Long.parseLong(jwt.getSubject());
        access.active(id, "CANDIDATE");
        return id;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String,Object> overview(@AuthenticationPrincipal Jwt jwt) {
        long user = candidate(jwt);
        var bookmarks = jdbc.queryForList("""
            SELECT b.job_offer_id, b.state, b.collection_name, b.note, b.created_at, b.updated_at,
                   j.title, j.location, j.status AS offer_status, c.name AS company_name,
                   ct.code AS contract_type, j.remote_mode, j.salary_min, j.salary_max,
                   j.salary_currency, j.salary_period, j.description
            FROM candidate_offer_bookmarks b JOIN job_offers j ON j.id=b.job_offer_id
            JOIN companies c ON c.id=j.company_id JOIN contract_types ct ON ct.id=j.contract_type_id
            WHERE b.user_id=? ORDER BY b.updated_at DESC
            """, user);
        var searches = jdbc.queryForList("""
            SELECT id, name, filters::text AS filters_json, alerts_enabled, created_at
            FROM candidate_saved_searches WHERE user_id=? ORDER BY created_at DESC
            """,user);
        var projects = jdbc.queryForList("""
            SELECT id,title,description,project_url,skills,created_at,updated_at
            FROM candidate_portfolio_projects WHERE user_id=? ORDER BY updated_at DESC
            """,user);
        var cvs = jdbc.queryForList("""
            SELECT v.id,v.label,v.filename,v.created_at,(v.storage_key=p.resume_url) AS active
            FROM candidate_cv_versions v JOIN candidate_profiles p ON p.user_id=v.user_id
            WHERE v.user_id=? ORDER BY v.created_at DESC
            """,user);
        var notes = jdbc.queryForList("""
            SELECT n.application_id,n.note,n.remind_at,n.updated_at,j.title AS job_title,
                   c.name AS company_name FROM candidate_application_notes n
            JOIN applications a ON a.id=n.application_id
            JOIN job_offers j ON j.id=a.job_offer_id JOIN companies c ON c.id=j.company_id
            JOIN candidate_profiles p ON p.id=a.candidate_id
            WHERE n.user_id=? AND p.user_id=? ORDER BY n.updated_at DESC
            """,user,user);
        var prefs = jdbc.queryForList("""
            SELECT weekly_goal,alerts_enabled,saved_filters::text AS filters_json,
                   hidden_widgets::text AS hidden_widgets_json
            FROM candidate_workspace_preferences WHERE user_id=?
            """,user);
        return Map.of("bookmarks",bookmarks,"searches",searches,"projects",projects,"notes",notes,"cvs",cvs,
            "preferences",prefs.isEmpty() ? Map.of("weekly_goal",5,"alerts_enabled",true,"filters_json","{}",
                "hidden_widgets_json","[]") : prefs.getFirst());
    }

    @PutMapping("/offers/{id}")
    @Transactional
    public Map<String,Object> bookmark(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,
                                        @RequestBody Map<String,Object> input) {
        long user = candidate(jwt);
        if (jdbc.queryForObject("SELECT count(*) FROM job_offers WHERE id=? AND deleted_at IS NULL",Long.class,id)==0)
            throw ActorAccess.error(404,"Offer not found");
        String state = required(input,"state",20);
        if (!Set.of("SAVED","INTERESTED","HIDDEN").contains(state)) throw ActorAccess.error(400,"Invalid state");
        String collection = optional(input,"collection_name",60,"Plus tard");
        String note = optional(input,"note",3000,"");
        jdbc.update("""
            INSERT INTO candidate_offer_bookmarks (user_id,job_offer_id,state,collection_name,note)
            VALUES (?,?,?,?,?) ON CONFLICT (user_id,job_offer_id)
            DO UPDATE SET state=EXCLUDED.state, collection_name=EXCLUDED.collection_name,
                          note=EXCLUDED.note
            """,user,id,state,collection,note);
        return Map.of("job_offer_id",id,"state",state,"collection_name",collection,"note",note);
    }

    @DeleteMapping("/offers/{id}")
    @Transactional
    public void removeBookmark(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) {
        jdbc.update("DELETE FROM candidate_offer_bookmarks WHERE user_id=? AND job_offer_id=?",candidate(jwt),id);
    }

    @PutMapping("/preferences")
    @Transactional
    public Map<String,Object> preferences(@AuthenticationPrincipal Jwt jwt,@RequestBody Map<String,Object> input) {
        long user=candidate(jwt);
        var old=jdbc.queryForList("""
            SELECT weekly_goal,alerts_enabled,saved_filters::text AS filters_json,
                   hidden_widgets::text AS hidden_widgets_json
            FROM candidate_workspace_preferences WHERE user_id=?
            """,user);
        var current=old.isEmpty() ? Map.<String,Object>of() : old.getFirst();
        int goal=integer(input.getOrDefault("weekly_goal",current.getOrDefault("weekly_goal",5)),1,100);
        boolean alerts=bool(input.getOrDefault("alerts_enabled",current.getOrDefault("alerts_enabled",true)));
        Object filters=input.getOrDefault("filters",decodeJson(current.get("filters_json"),Map.of()));
        if (!(filters instanceof Map<?,?>)) throw ActorAccess.error(400,"Expected filter object");
        Object hidden=input.getOrDefault("hidden_widgets",decodeJson(current.get("hidden_widgets_json"),List.of()));
        if (!(hidden instanceof List<?>)) throw ActorAccess.error(400,"Expected hidden_widgets array");
        String filtersJson=toJson(filters); String hiddenJson=toJson(hidden);
        jdbc.update("""
            INSERT INTO candidate_workspace_preferences (user_id,weekly_goal,alerts_enabled,saved_filters,hidden_widgets)
            VALUES (?,?,?,?::jsonb,?::jsonb)
            ON CONFLICT(user_id) DO UPDATE SET weekly_goal=EXCLUDED.weekly_goal,
                alerts_enabled=EXCLUDED.alerts_enabled, saved_filters=EXCLUDED.saved_filters,
                hidden_widgets=EXCLUDED.hidden_widgets
            """,user,goal,alerts,filtersJson,hiddenJson);
        return Map.of("weekly_goal",goal,"alerts_enabled",alerts,"filters",filters,"hidden_widgets",hidden);
    }

    @PostMapping("/searches")
    @Transactional
    public Map<String,Object> createSearch(@AuthenticationPrincipal Jwt jwt,@RequestBody Map<String,Object> input) {
        long user=candidate(jwt);
        String name=required(input,"name",100);
        Object filters=input.getOrDefault("filters",Map.of());
        if (!(filters instanceof Map<?,?>)) throw ActorAccess.error(400,"Invalid search filters");
        boolean alerts=bool(input.getOrDefault("alerts_enabled",true));
        if (jdbc.queryForObject("SELECT count(*) FROM candidate_saved_searches WHERE user_id=?",Long.class,user)>=30)
            throw ActorAccess.error(400,"Maximum of 30 saved searches");
        long id=jdbc.queryForObject("""
            INSERT INTO candidate_saved_searches (user_id,name,filters,alerts_enabled)
            VALUES (?,?,?::jsonb,?) RETURNING id
            """,Long.class,user,name,toJson(filters),alerts);
        return Map.of("id",id,"name",name,"filters",filters,"alerts_enabled",alerts);
    }

    @PutMapping("/searches/{id}")
    @Transactional
    public Map<String,Object> updateSearch(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,
                                            @RequestBody Map<String,Object> input) {
        long user=candidate(jwt);
        String name=required(input,"name",100);
        Object filters=input.getOrDefault("filters",Map.of());
        if (!(filters instanceof Map<?,?>)) throw ActorAccess.error(400,"Invalid filters");
        boolean alerts=bool(input.getOrDefault("alerts_enabled",true));
        int changed=jdbc.update("""
            UPDATE candidate_saved_searches SET name=?,filters=?::jsonb,alerts_enabled=?,last_checked_at=NOW()
            WHERE id=? AND user_id=?
            """,name,toJson(filters),alerts,id,user);
        if (changed==0) throw ActorAccess.error(404,"Saved search not found");
        return Map.of("id",id,"name",name,"alerts_enabled",alerts,"filters",filters);
    }

    @DeleteMapping("/searches/{id}")
    public void deleteSearch(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) {
        jdbc.update("DELETE FROM candidate_saved_searches WHERE id=? AND user_id=?",id,candidate(jwt));
    }

    @PostMapping("/projects")
    @Transactional
    public Map<String,Object> createProject(@AuthenticationPrincipal Jwt jwt,@RequestBody Map<String,Object> input) {
        long user=candidate(jwt);
        String title=required(input,"title",150);
        String desc=optional(input,"description",3000,"");
        String url=projectUrl(input.get("project_url"));
        String skills=optional(input,"skills",1200,"");
        long id=jdbc.queryForObject("""
            INSERT INTO candidate_portfolio_projects(user_id,title,description,project_url,skills)
            VALUES(?,?,?,?,?) RETURNING id
            """,Long.class,user,title,desc,url,skills);
        return Map.of("id",id,"title",title,"description",desc,"skills",skills);
    }

    @PutMapping("/projects/{id}")
    @Transactional
    public void updateProject(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@RequestBody Map<String,Object> input) {
        long user=candidate(jwt);
        int changed=jdbc.update("""
            UPDATE candidate_portfolio_projects SET title=?,description=?,project_url=?,skills=?
            WHERE id=? AND user_id=?
            """,required(input,"title",150),optional(input,"description",3000,""),
            projectUrl(input.get("project_url")),optional(input,"skills",1200,""),id,user);
        if(changed==0) throw ActorAccess.error(404,"Project not found");
    }

    @DeleteMapping("/projects/{id}")
    public void deleteProject(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) {
        jdbc.update("DELETE FROM candidate_portfolio_projects WHERE id=? AND user_id=?",id,candidate(jwt));
    }

    @PutMapping("/applications/{id}/note")
    @Transactional
    public Map<String,Object> saveNote(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,
                                       @RequestBody Map<String,Object> input) {
        long user=candidate(jwt);
        long owns=jdbc.queryForObject("""
            SELECT count(*) FROM applications a JOIN candidate_profiles p ON p.id=a.candidate_id
            WHERE a.id=? AND p.user_id=?
            """,Long.class,id,user);
        if(owns==0) throw ActorAccess.error(404,"Application not found");
        String note=optional(input,"note",3000,"");
        OffsetDateTime remind=null;
        if(input.get("remind_at") instanceof String value && !value.isBlank()) {
            try {remind=OffsetDateTime.parse(value);} catch(Exception e) {throw ActorAccess.error(400,"Invalid reminder date");}
        }
        jdbc.update("""
            INSERT INTO candidate_application_notes(user_id,application_id,note,remind_at)
            VALUES(?,?,?,?) ON CONFLICT (user_id,application_id)
            DO UPDATE SET note=EXCLUDED.note,remind_at=EXCLUDED.remind_at
            """,user,id,note,remind);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("application_id",id);result.put("note",note);result.put("remind_at",remind);
        return result;
    }

    @DeleteMapping("/applications/{id}/note")
    public void deleteNote(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) {
        jdbc.update("DELETE FROM candidate_application_notes WHERE user_id=? AND application_id=?",candidate(jwt),id);
    }

    @GetMapping("/applications/{id}/history")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> applicationHistory(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) {
        long user=candidate(jwt);
        long owns=jdbc.queryForObject("""
            SELECT count(*) FROM applications a JOIN candidate_profiles p ON p.id=a.candidate_id
            WHERE a.id=? AND p.user_id=?
            """,Long.class,id,user);
        if(owns==0) throw ActorAccess.error(404,"Application not found");
        return jdbc.queryForList("""
            SELECT status_code,created_at FROM candidate_application_status_events
            WHERE application_id=? ORDER BY created_at ASC,id ASC
            """,id);
    }

    @GetMapping("/drafts/{offerId}")
    @Transactional(readOnly=true)
    public Map<String,Object> draft(@AuthenticationPrincipal Jwt jwt,@PathVariable long offerId) {
        long user=candidate(jwt);
        var rows=jdbc.queryForList("""
            SELECT cover_message,updated_at FROM candidate_application_drafts
            WHERE user_id=? AND job_offer_id=?
            """,user,offerId);
        return rows.isEmpty()?Map.of("cover_message",""):rows.getFirst();
    }

    @PutMapping("/drafts/{offerId}")
    @Transactional
    public Map<String,Object> saveDraft(@AuthenticationPrincipal Jwt jwt,@PathVariable long offerId,
                                         @RequestBody Map<String,Object> input) {
        long user=candidate(jwt);
        long exists=jdbc.queryForObject("""
            SELECT count(*) FROM job_offers WHERE id=? AND deleted_at IS NULL
            """,Long.class,offerId);
        if(exists==0) throw ActorAccess.error(404,"Offer not found");
        String message=optional(input,"cover_message",10000,"");
        jdbc.update("""
            INSERT INTO candidate_application_drafts(user_id,job_offer_id,cover_message)
            VALUES(?,?,?) ON CONFLICT(user_id,job_offer_id)
            DO UPDATE SET cover_message=EXCLUDED.cover_message
            """,user,offerId,message);
        return Map.of("cover_message",message);
    }

    @DeleteMapping("/drafts/{offerId}")
    public void removeDraft(@AuthenticationPrincipal Jwt jwt,@PathVariable long offerId) {
        jdbc.update("DELETE FROM candidate_application_drafts WHERE user_id=? AND job_offer_id=?",
            candidate(jwt),offerId);
    }

    private static String required(Map<String,Object> input,String name,int max) {
        String text=optional(input,name,max,"");
        if(text.isBlank()) throw ActorAccess.error(400,"Field "+name+" is required");
        return text;
    }
    private static String optional(Map<String,Object> input,String name,int max,String fallback) {
        Object value=input.get(name);
        if(value==null) return fallback;
        if(!(value instanceof String text) || text.length()>max) throw ActorAccess.error(400,"Invalid field "+name);
        return text.trim();
    }
    private static int integer(Object value,int min,int max) {
        if(!(value instanceof Number n) || n.intValue()<min || n.intValue()>max) throw ActorAccess.error(400,"Invalid integer");
        return n.intValue();
    }
    private static boolean bool(Object value) {
        if(!(value instanceof Boolean b)) throw ActorAccess.error(400,"Invalid boolean");
        return b;
    }
    private String projectUrl(Object value) {
        if(value==null || "".equals(value)) return null;
        if(!(value instanceof String url) || url.length()>1200) throw ActorAccess.error(400,"Invalid URL");
        try { URI uri=URI.create(url); if(!"https".equals(uri.getScheme()) || uri.getHost()==null)
            throw new IllegalArgumentException(); }
        catch(RuntimeException e) {throw ActorAccess.error(400,"Only HTTPS project URLs are accepted");}
        return (String)value;
    }
    private Object decodeJson(Object value,Object fallback) {
        if(!(value instanceof String text)) return fallback;
        try {return json.readValue(text,Object.class);} catch(Exception e) {return fallback;}
    }
    private String toJson(Object value) {
        try {String result=json.writeValueAsString(value);
            if(result.length()>8000) throw ActorAccess.error(400,"JSON too large");
            return result;
        } catch(JsonProcessingException e) {throw ActorAccess.error(400,"Invalid JSON");}
    }
}
