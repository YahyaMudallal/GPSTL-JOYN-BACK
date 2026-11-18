package com.gpstl.joyn.additional;

import com.gpstl.joyn.company.dto.CompanyStatusUpdateRequest;
import com.gpstl.joyn.company.service.CompanyService;
import com.gpstl.joyn.company.service.SiretLookupService;
import org.apache.pdfbox.Loader;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/verifications")
public class AdminVerificationController {
    private final ActorAccess access;
    private final JdbcTemplate jdbc;
    private final CompanyService companies;
    private final SiretLookupService registry;
    private static final String SELECT = """
            SELECT c.id, c.name, c.siret, c.address, c.verification_status,
                   to_char(c.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS created_at,
                   v.method, v.note, v.evidence_pdf IS NOT NULL AS has_document,
                   to_char(v.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS reviewed_at
            FROM companies c LEFT JOIN LATERAL (
                SELECT method, note, created_at, evidence_pdf FROM company_verification_reviews
                WHERE company_id=c.id ORDER BY id DESC LIMIT 1
            ) v ON true WHERE c.deleted_at IS NULL
            """;

    public AdminVerificationController(ActorAccess access, JdbcTemplate jdbc, CompanyService companies, SiretLookupService registry) {
        this.access = access;
        this.jdbc = jdbc;
        this.companies = companies;
        this.registry = registry;
    }

    @GetMapping
    public List<Map<String, Object>> list(Authentication auth, @RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="1") int page) {
        admin(auth);
        if (page < 1 || search.length() > 200) throw ActorAccess.error(400, "Filtre invalide");
        return jdbc.queryForList(SELECT + """
                AND (strpos(lower(c.name), lower(?)) > 0 OR strpos(coalesce(c.siret,''), ?) > 0 OR ?='')
                ORDER BY c.created_at DESC, c.id DESC LIMIT 100 OFFSET ?
                """, search, search, search, 100L * (page - 1));
    }

    public record Decision(String decision, String note) {}

    @PostMapping("/{id}/decision")
    @Transactional
    public Map<String, Object> decide(Authentication auth, @PathVariable long id, @RequestBody Decision body) {
        long actor = admin(auth);
        if (body.decision() == null) throw ActorAccess.error(400, "Décision requise");
        String note = note(body.note());
        String method = switch (body.decision()) {
            case "approve" -> "MANUAL";
            case "request_document" -> "REQUEST";
            case "refuse" -> "REFUSED";
            default -> throw ActorAccess.error(400, "Décision invalide");
        };
        if (!"MANUAL".equals(method) && note.isBlank()) throw ActorAccess.error(400, "Note requise");
        return record(auth, actor, id, method, note, null);
    }

    @PostMapping(path="/{id}/document", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Map<String, Object> document(Authentication auth, @PathVariable long id,
            @RequestParam("evidence") MultipartFile evidence,
            @RequestParam(defaultValue="") String note) {
        long actor = admin(auth);
        if (evidence.isEmpty() || evidence.getSize() > 5 * 1024 * 1024) throw ActorAccess.error(400, "PDF de 5 Mo maximum");
        try {
            byte[] bytes = evidence.getBytes();
            try (var document = Loader.loadPDF(bytes)) {
                if (document.isEncrypted() || document.getNumberOfPages() < 1 || document.getNumberOfPages() > 20)
                    throw ActorAccess.error(400, "PDF lisible de 1 à 20 pages requis");
            }
            return record(auth, actor, id, "DOCUMENT", note(note), bytes);
        } catch (IOException e) {
            throw ActorAccess.error(400, "Justificatif PDF invalide");
        }
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<CandidateDocumentsController.PdfPage> documentPreview(Authentication auth,
            @PathVariable long id, @RequestParam(defaultValue="1") int page) {
        admin(auth);
        var rows = jdbc.query("""
                SELECT v.evidence_pdf FROM company_verification_reviews v JOIN companies c ON c.id=v.company_id
                WHERE v.company_id=? AND c.deleted_at IS NULL AND v.evidence_pdf IS NOT NULL ORDER BY v.id DESC LIMIT 1
                """, (row, n) -> row.getBytes(1), id);
        if (rows.isEmpty()) throw ActorAccess.error(404, "Justificatif indisponible");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(CandidateDocumentsController.render(rows.getFirst(), page));
    }

    @PostMapping("/{id}/retry")
    @Transactional
    public Map<String, Object> retry(Authentication auth, @PathVariable long id) {
        long actor = admin(auth);
        var current = lock(id);
        String siret = (String) current.get("siret");
        if (siret == null || siret.isBlank()) throw ActorAccess.error(422, "SIRET manquant");
        var found = registry.lookup(siret);
        String name = (String) current.get("name");
        if (!Boolean.TRUE.equals(found.companyActive()) || !Boolean.TRUE.equals(found.establishmentActive())
                || !(name.strip().equalsIgnoreCase(found.legalName()) || name.strip().equalsIgnoreCase(found.displayName()))) {
            throw ActorAccess.error(422, "Le registre ne confirme pas le nom et l’activité de l’établissement");
        }
        return record(auth, actor, id, "REGISTRY", "SIRET et établissement actif confirmés par le registre.", null);
    }

    private long admin(Authentication auth) {
        long id = Long.parseLong(((Jwt) auth.getPrincipal()).getSubject());
        access.active(id, "ADMIN");
        return id;
    }

    private Map<String, Object> lock(long id) {
        var rows = jdbc.queryForList("SELECT name, siret FROM companies WHERE id=? AND deleted_at IS NULL FOR UPDATE", id);
        if (rows.isEmpty()) throw ActorAccess.error(404, "Entreprise introuvable");
        return rows.getFirst();
    }

    private Map<String, Object> record(Authentication auth, long actor, long id, String method, String note, byte[] bytes) {
        lock(id);
        String status = switch (method) {
            case "REQUEST" -> "NOT_VERIFIED";
            case "REFUSED" -> "REJECTED";
            default -> "VERIFIED";
        };
        companies.updateVerificationStatus(id, new CompanyStatusUpdateRequest(status), auth);
        jdbc.update("""
                INSERT INTO company_verification_reviews(company_id, actor_user_id, method, note, evidence_pdf)
                VALUES (?,?,?,?,?)
                """, id, actor, method, note, bytes);
        if ("REQUEST".equals(method) || "REFUSED".equals(method)) {
            String title = "REQUEST".equals(method) ? "Justificatif demandé" : "Vérification de l’entreprise refusée";
            jdbc.update("""
                    INSERT INTO notifications(user_id,type,title,message,link)
                    SELECT r.user_id,'SYSTEM',?,?,'/recruiter/notifications'
                    FROM recruiter_profiles r JOIN users u ON u.id=r.user_id AND u.status='ACTIVE'
                    WHERE r.company_id=?
                    """, title, note, id);
        }
        return jdbc.queryForMap(SELECT + " AND c.id=?", id);
    }

    private static String note(String value) {
        String note = value == null ? "" : value.trim();
        if (note.length() > 2000) throw ActorAccess.error(400, "Note trop longue");
        return note;
    }
}
