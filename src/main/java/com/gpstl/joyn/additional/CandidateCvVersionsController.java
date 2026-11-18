package com.gpstl.joyn.additional;

import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import org.apache.pdfbox.Loader;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

/** Secure upload, activation and preview of actual PDF CV variants. */
@RestController
@RequestMapping("/api/v1/candidates/workspace/cvs")
public class CandidateCvVersionsController {
    private final ActorAccess access;
    private final JdbcTemplate jdbc;
    private final CandidateResumeStorage storage;
    public CandidateCvVersionsController(ActorAccess access,JdbcTemplate jdbc,CandidateResumeStorage storage) {
        this.access=access;this.jdbc=jdbc;this.storage=storage;
    }
    private long user(Jwt jwt) {
        long user=Long.parseLong(jwt.getSubject());
        access.active(user,"CANDIDATE");return user;
    }
    @GetMapping
    @Transactional(readOnly=true)
    public List<Map<String,Object>> list(@AuthenticationPrincipal Jwt jwt) {
        long user=user(jwt);
        return jdbc.queryForList("""
            SELECT v.id,v.label,v.filename,v.created_at,
                (v.storage_key=p.resume_url) AS active
            FROM candidate_cv_versions v JOIN candidate_profiles p ON p.user_id=v.user_id
            WHERE v.user_id=? ORDER BY v.created_at DESC
            """,user);
    }
    @PostMapping(consumes="multipart/form-data")
    @Transactional
    public Map<String,Object> upload(@AuthenticationPrincipal Jwt jwt,@RequestParam("label") String label,
                                     @RequestParam("file") MultipartFile file) throws IOException {
        long user=user(jwt);
        if(label==null||label.isBlank()||label.length()>100) throw ActorAccess.error(400,"Invalid CV label");
        if(file.isEmpty()||file.getSize()>5*1024*1024) throw ActorAccess.error(413,"Maximum PDF size is 5 MB");
        byte[] bytes=file.getBytes();
        if(bytes.length<5||bytes[0]!='%'||bytes[1]!='P'||bytes[2]!='D'||bytes[3]!='F'||bytes[4]!='-')
            throw ActorAccess.error(400,"A PDF is required");
        try(var pdf=Loader.loadPDF(bytes)) {
            if(pdf.getNumberOfPages()<1||pdf.getNumberOfPages()>40) throw ActorAccess.error(400,"Invalid PDF page count");
        } catch(IOException ex) {throw ActorAccess.error(422,"Unreadable PDF");}
        var ids=jdbc.queryForList("SELECT id FROM candidate_profiles WHERE user_id=?",Long.class,user);
        if(ids.isEmpty()) throw ActorAccess.error(404,"Candidate profile not found");
        if(jdbc.queryForObject("SELECT count(*) FROM candidate_cv_versions WHERE user_id=?",Long.class,user)>=20)
            throw ActorAccess.error(400,"Maximum of 20 CV versions");
        // MultipartFile is stored under an existing backend storage abstraction (local or S3).
        String key=storage.store(ids.getFirst(),file);
        String filename=Optional.ofNullable(file.getOriginalFilename()).orElse("cv.pdf");
        filename=filename.replaceAll("[^a-zA-Z0-9._ -]","_");
        if(filename.length()>180) filename=filename.substring(0,180);
        long id=jdbc.queryForObject("""
            INSERT INTO candidate_cv_versions(user_id,label,filename,storage_key)
            VALUES(?,?,?,?) RETURNING id
            """,Long.class,user,label.trim(),filename,key);
        return Map.of("id",id,"label",label.trim(),"filename",filename);
    }
    @PutMapping("/{id}/activate")
    @Transactional
    public Map<String,Object> activate(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) {
        long user=user(jwt);
        var keys=jdbc.queryForList("SELECT storage_key FROM candidate_cv_versions WHERE id=? AND user_id=?",String.class,id,user);
        if(keys.isEmpty()) throw ActorAccess.error(404,"CV not found");
        jdbc.update("UPDATE candidate_profiles SET resume_url=? WHERE user_id=?",keys.getFirst(),user);
        return Map.of("id",id,"active",true);
    }
    @GetMapping("/{id}/preview")
    @Transactional(readOnly=true)
    public ResponseEntity<CandidateDocumentsController.PdfPage> preview(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable long id,@RequestParam(defaultValue="1") int page) throws IOException {
        long user=user(jwt);
        var keys=jdbc.queryForList("SELECT storage_key FROM candidate_cv_versions WHERE id=? AND user_id=?",String.class,id,user);
        if(keys.isEmpty()) throw ActorAccess.error(404,"CV not found");
        String key=keys.getFirst();
        if(!CandidateResumeStorage.STORAGE_KEY_PATTERN.matcher(key).matches()) throw ActorAccess.error(422,"Invalid CV storage key");
        var resource=storage.load(key).orElseThrow(()->ActorAccess.error(404,"CV file not found"));
        try(var input=resource.resource().getInputStream()) {
            byte[] bytes=input.readNBytes(5*1024*1024+1);
            if(bytes.length>5*1024*1024) throw ActorAccess.error(413,"PDF is too large");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(CandidateDocumentsController.render(bytes,page));
        }
    }
    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) {
        long user=user(jwt);
        int deleted=jdbc.update("""
            DELETE FROM candidate_cv_versions v WHERE v.user_id=? AND v.id=?
              AND NOT EXISTS (SELECT 1 FROM candidate_profiles p WHERE p.user_id=v.user_id AND p.resume_url=v.storage_key)
            """,user,id);
        if(deleted==0) throw ActorAccess.error(409,"CV not found or currently active: activate another CV first");
        // Keep the storage object: application snapshots may still reference that CV.
    }
}
