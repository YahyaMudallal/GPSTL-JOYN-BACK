package com.gpstl.joyn.additional;

import com.gpstl.joyn.application.model.ApplicationCoverPdf;
import com.gpstl.joyn.application.repository.ApplicationCoverPdfRepository;
import com.gpstl.joyn.candidate.dto.CandidateProfileResponse;
import com.gpstl.joyn.candidate.service.CandidateProfileService;
import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@RestController
public class CandidateDocumentsController {
    private static final String DOSSIER = "/api/v1/applications/{id}/dossier";
    private static final int MAX_PDF_BYTES = 5 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final ActorAccess access;
    private final CandidateProfileService profiles;
    private final CandidateResumeStorage resumes;
    private final ApplicationCoverPdfRepository letters;

    public CandidateDocumentsController(JdbcTemplate jdbc, ActorAccess access, CandidateProfileService profiles,
            CandidateResumeStorage resumes, ApplicationCoverPdfRepository letters) {
        this.jdbc = jdbc;
        this.access = access;
        this.profiles = profiles;
        this.resumes = resumes;
        this.letters = letters;
    }

    public record Dossier(CandidateProfileResponse profile, String offerTitle, String coverMessage,
            boolean hasCoverPdf) {}

    @GetMapping(DOSSIER)
    public ResponseEntity<Dossier> dossier(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        long user = Long.parseLong(jwt.getSubject());
        var row = application(user, id);
        var profile = profiles.getProfileById(user, ((Number) row.get("candidate_id")).longValue());
        return privateResponse(new Dossier(profile, (String) row.get("title"),
                (String) row.get("cover_message"), letters.existsById(id)));
    }

    @GetMapping(DOSSIER + "/{document}")
    public ResponseEntity<PdfPage> applicationDocument(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @PathVariable String document, @RequestParam(defaultValue = "1") int page) {
        long user = Long.parseLong(jwt.getSubject());
        var row = application(user, id);
        profiles.getProfileById(user, ((Number) row.get("candidate_id")).longValue());
        byte[] bytes = switch (document) {
            case "cv" -> resume((String) row.get("resume_url"));
            case "lettre" -> letters.findById(id).map(ApplicationCoverPdf::getContent)
                    .orElseThrow(() -> ActorAccess.error(404, "Aucune lettre PDF pour cette candidature"));
            default -> throw ActorAccess.error(404, "Document introuvable");
        };
        return privateResponse(render(bytes, page));
    }

    @GetMapping("/api/v1/candidates/profile/cv-preview")
    public ResponseEntity<PdfPage> ownResume(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page) {
        long user = Long.parseLong(jwt.getSubject());
        access.active(user, "CANDIDATE");
        var rows = jdbc.queryForList("SELECT resume_url FROM candidate_profiles WHERE user_id=?", user);
        if (rows.isEmpty()) throw ActorAccess.error(404, "Profil introuvable");
        return privateResponse(render(resume((String) rows.getFirst().get("resume_url")), page));
    }

    private Map<String, Object> application(long user, long id) {
        access.active(user, "RECRUITER");
        var rows = jdbc.queryForList("""
            SELECT a.candidate_id, cp.resume_url, a.cover_message, j.title
            FROM applications a JOIN job_offers j ON j.id = a.job_offer_id
            JOIN candidate_profiles cp ON cp.id = a.candidate_id
            JOIN companies co ON co.id = j.company_id
            WHERE a.id = ? AND co.status <> 'DELETED'
              AND EXISTS (SELECT 1 FROM recruiter_profiles r WHERE r.user_id = ? AND r.company_id = j.company_id)
            """, id, user);
        if (rows.isEmpty()) throw ActorAccess.error(404, "Candidature introuvable");
        return rows.getFirst();
    }

    private byte[] resume(String key) {
        if (key == null || !CandidateResumeStorage.STORAGE_KEY_PATTERN.matcher(key).matches()) {
            throw ActorAccess.error(404, "Aucun CV PDF disponible");
        }
        var stored = resumes.load(key).orElseThrow(() -> ActorAccess.error(404, "CV introuvable"));
        try (var stream = stored.resource().getInputStream()) {
            byte[] bytes = stream.readNBytes(MAX_PDF_BYTES + 1);
            if (bytes.length > MAX_PDF_BYTES) throw ActorAccess.error(413, "Le PDF dépasse 5 Mo");
            return bytes;
        } catch (IOException ex) {
            throw ActorAccess.error(422, "Impossible de lire le CV");
        }
    }

    public record PdfPage(int page, int totalPages, String pngBase64) {}

    static PdfPage render(byte[] content, int page) {
        try (var pdf = Loader.loadPDF(content); var imageOutput = new ByteArrayOutputStream()) {
            if (page < 1 || page > pdf.getNumberOfPages()) throw ActorAccess.error(404, "Page introuvable");
            var box = pdf.getPage(page - 1).getCropBox();
            float largestSide = Math.max(box.getWidth(), box.getHeight());
            if (!Float.isFinite(largestSide) || largestSide <= 0) {
                throw ActorAccess.error(422, "Taille de page PDF invalide");
            }
            var renderer = new PDFRenderer(pdf);
            renderer.setSubsamplingAllowed(true);
            var image = renderer.renderImage(page - 1, Math.min(2, 1400 / largestSide), ImageType.RGB);
            ImageIO.write(image, "PNG", imageOutput);
            return new PdfPage(page, pdf.getNumberOfPages(), Base64.getEncoder().encodeToString(imageOutput.toByteArray()));
        } catch (IOException | IllegalArgumentException ex) {
            throw ActorAccess.error(422, "Impossible d’afficher ce PDF");
        }
    }

    private static <T> ResponseEntity<T> privateResponse(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }
}
