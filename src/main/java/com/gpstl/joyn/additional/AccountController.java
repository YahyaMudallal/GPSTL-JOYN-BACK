package com.gpstl.joyn.additional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import java.util.*;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
    final JdbcTemplate jdbc; final ActorAccess access; final PasswordEncoder encoder;
    private final ObjectMapper mapper=new tools.jackson.databind.json.JsonMapper();
    public AccountController(JdbcTemplate jdbc,ActorAccess access,PasswordEncoder encoder){this.jdbc=jdbc;this.access=access;this.encoder=encoder;}
    public record Credentials(@NotBlank String currentPassword,@Email @Size(max=254) String email,
        @Size(min=12,max=72) String newPassword) {}
    public record Confirmation(@NotBlank String currentPassword) {}
    private static final Set<String> FIELDS=Set.of("phone","jobTitle","projects","recommendations","skillLevels",
        "availabilityStart","maxCommuteMinutes","domains","softSkills","companySizes","searchKind","studio");
    @GetMapping
    public Map<String,Object> read(@AuthenticationPrincipal Jwt jwt){
        long user=Long.parseLong(jwt.getSubject());access.active(user);
        var row=jdbc.queryForMap("SELECT id,email,role,settings::text AS settings FROM users WHERE id=?",user);
        row.put("settings",decode((String)row.get("settings")));return row;
    }
    @PatchMapping("/settings")
    @Transactional
    public Map<String,Object> settings(@AuthenticationPrincipal Jwt jwt,@RequestBody Map<String,Object> values){
        long user=Long.parseLong(jwt.getSubject());access.active(user);
        if(!FIELDS.containsAll(values.keySet()))throw ActorAccess.error(400,"Unknown settings field");
        for(var e:values.entrySet()) {
            Object v=e.getValue(); if(v==null)continue;
            boolean valid=switch(e.getKey()) {
                case "phone","jobTitle","availabilityStart","searchKind" -> v instanceof String s && s.length()<=200;
                case "maxCommuteMinutes" -> v instanceof Number n && n.intValue()>=0 && n.intValue()<=300;
                case "studio","skillLevels" -> v instanceof Map<?,?> m && m.size()<=200;
                default -> v instanceof List<?> l && l.size()<=100;
            };
            if(valid && v instanceof List<?> list) {
                if(Set.of("domains","softSkills","companySizes").contains(e.getKey()))
                    valid=list.stream().allMatch(item->item instanceof String text && text.length()<=200);
                else valid=list.stream().allMatch(item->item instanceof Map<?,?> map &&
                    map.values().stream().allMatch(value->value instanceof String || value instanceof List<?>));
            }
            if(valid && e.getKey().equals("skillLevels") && v instanceof Map<?,?> map)
                valid=map.entrySet().stream().allMatch(entry->entry.getKey() instanceof String && entry.getValue() instanceof String && Set.of("beginner","intermediate","advanced").contains(entry.getValue()));
            if(!valid)throw ActorAccess.error(400,"Invalid setting: "+e.getKey());
        }
        values = new LinkedHashMap<>(values);
        if (values.containsKey("phone")) {
            String raw = (String) values.get("phone");
            String phone = raw == null ? null : raw.replaceAll("[\\s().-]", "");
            if (phone != null && phone.startsWith("00")) phone = "+" + phone.substring(2);
            if (phone != null && phone.isEmpty()) phone = null;
            if (phone != null && !phone.matches("\\+[1-9][0-9]{6,14}"))
                throw ActorAccess.error(400, "Use an international phone number, for example +33612345678");
            values.put("phone", phone);
        }
        try {
            String json=mapper.writeValueAsString(values);
            if(json.length()>32000)throw ActorAccess.error(400,"Settings too large");
            jdbc.update("UPDATE users SET settings=settings || ?::jsonb WHERE id=?",json,user);
            if(values.containsKey("phone")) jdbc.update("UPDATE candidate_profiles SET phone=? WHERE user_id=?",values.get("phone"),user);
        }catch(tools.jackson.core.JacksonException e){throw ActorAccess.error(400,"Invalid JSON");}
        return read(jwt);
    }
    @PatchMapping("/credentials")
    @Transactional
    public Map<String,String> credentials(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Credentials body){
        long user=Long.parseLong(jwt.getSubject());verify(user,body.currentPassword());
        if(body.newPassword()!=null && body.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw ActorAccess.error(400,"Password exceeds 72 UTF-8 bytes");
        if(body.email()==null && body.newPassword()==null)throw ActorAccess.error(400,"Nothing to update");
        String email=body.email()==null?null:body.email().trim().toLowerCase(Locale.ROOT);
        if(email!=null && email.isBlank())throw ActorAccess.error(400,"Email required");
        if(email!=null && jdbc.queryForObject("SELECT count(*) FROM users WHERE lower(email)=? AND id<>?",Long.class,email,user)>0)throw ActorAccess.error(409,"Email already in use");
        try {
            jdbc.update("""
                UPDATE users SET email=COALESCE(?,email), password_hash=COALESCE(?,password_hash),
                email_verified_at=CASE WHEN CAST(? AS text) IS NULL OR lower(email)=? THEN email_verified_at ELSE NULL END,
                tokens_valid_after=now() WHERE id=?
                """,email,body.newPassword()==null?null:encoder.encode(body.newPassword()),email,email,user);
        }catch(DataIntegrityViolationException e){throw ActorAccess.error(409,"Email already in use");}
        return Map.of("message","Credentials updated. Sign in again.");
    }
    @PostMapping("/deactivate")
    @Transactional
    public Map<String,String> deactivate(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Confirmation body){
        long user=Long.parseLong(jwt.getSubject());access.active(user,"CANDIDATE","RECRUITER");verify(user,body.currentPassword());
        jdbc.update("UPDATE users SET status='SUSPENDED',tokens_valid_after=now() WHERE id=?",user);
        jdbc.update("UPDATE candidate_profiles SET is_visible_to_recruiters=false WHERE user_id=?",user);
        return Map.of("message","Account deactivated");
    }
    private void verify(long id,String password){
        access.active(id);
        if(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw ActorAccess.error(403,"Current password is incorrect");
        String hash=jdbc.queryForObject("SELECT password_hash FROM users WHERE id=? FOR UPDATE",String.class,id);
        if(!encoder.matches(password,hash))throw ActorAccess.error(403,"Current password is incorrect");
    }
    private Map<String,Object> decode(String json){
        try{return mapper.readValue(json,new TypeReference<Map<String,Object>>(){});}
        catch(Exception e){throw new IllegalStateException("Invalid stored account settings",e);}
    }
}
