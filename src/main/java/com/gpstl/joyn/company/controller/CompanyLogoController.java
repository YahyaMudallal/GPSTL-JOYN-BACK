package com.gpstl.joyn.company.controller;

import com.gpstl.joyn.company.dto.CompanyLogoResponse;
import com.gpstl.joyn.company.exception.CompanyLogoException;
import com.gpstl.joyn.company.service.CompanyLogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

/**
 * R84 – Logo d'une entreprise. Même fonctionnement que la photo de profil (U86).
 *
 *   GET    /api/v1/companies/{companyId}/logo   consulter le logo (public)
 *   PUT    /api/v1/companies/{companyId}/logo   envoyer ou remplacer le logo (multipart, champ "file")
 *   POST   /api/v1/companies/{companyId}/logo   même chose (alias, pour les clients qui n'envoient pas de PUT)
 *   DELETE /api/v1/companies/{companyId}/logo   supprimer le logo
 *
 * Sécurité : SecurityConfig exige un token RECRUITER ou ADMIN pour PUT, POST et DELETE ;
 * le service vérifie ensuite qu'un recruteur ne modifie que SA propre entreprise.
 */
@RestController
@RequestMapping("/api/v1/companies/{companyId}/logo")
@Tag(name = "Companies", description = "Profil public des entreprises")
@SecurityRequirement(name = "bearerAuth")
public class CompanyLogoController {

    private final CompanyLogoService service;

    public CompanyLogoController(CompanyLogoService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtenir l'URL du logo d'une entreprise",
            description = "logo_url vaut null si l'entreprise n'a pas de logo.")
    @ApiResponse(responseCode = "200", description = "URL du logo")
    @ApiResponse(responseCode = "404", description = "Entreprise introuvable")
    public ResponseEntity<CompanyLogoResponse> getLogo(@PathVariable("companyId") Long companyId) {
        return ResponseEntity.ok(service.getLogo(positive(companyId)));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Envoyer ou remplacer le logo de l'entreprise",
            description = "PNG, JPEG, WebP, GIF ou BMP (pas de SVG), 5 Mo maximum. Le logo garde ses proportions, "
                    + "est réduit à 512 px maximum et enregistré en WebP. Champ du fichier : file (ou logo, image).")
    @ApiResponse(responseCode = "200", description = "Logo mis à jour")
    @ApiResponse(responseCode = "400", description = "Fichier absent, pas une image, SVG, trop lourd ou mauvaises dimensions")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    @ApiResponse(responseCode = "403", description = "Pas un recruteur de cette entreprise ni un administrateur")
    @ApiResponse(responseCode = "404", description = "Entreprise introuvable")
    public ResponseEntity<CompanyLogoResponse> updateLogo(
            @PathVariable("companyId") Long companyId,
            @Parameter(description = "Image du logo")
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "image", required = false) MultipartFile image,
            Principal principal) {

        MultipartFile uploaded = resolveFile(file, logo, image);
        return ResponseEntity.ok(service.updateLogo(userId(principal), positive(companyId), uploaded));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Alias POST pour envoyer ou remplacer le logo")
    public ResponseEntity<CompanyLogoResponse> uploadLogoPost(
            @PathVariable("companyId") Long companyId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "image", required = false) MultipartFile image,
            Principal principal) {
        return updateLogo(companyId, file, logo, image, principal);
    }

    @DeleteMapping
    @Operation(summary = "Supprimer le logo de l'entreprise")
    @ApiResponse(responseCode = "204", description = "Logo supprimé")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    @ApiResponse(responseCode = "403", description = "Pas un recruteur de cette entreprise ni un administrateur")
    @ApiResponse(responseCode = "404", description = "Entreprise introuvable")
    public ResponseEntity<Void> deleteLogo(@PathVariable("companyId") Long companyId, Principal principal) {
        service.deleteLogo(userId(principal), positive(companyId));
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ outils

    private static MultipartFile resolveFile(MultipartFile file, MultipartFile logo, MultipartFile image) {
        if (file != null && !file.isEmpty()) return file;
        if (logo != null && !logo.isEmpty()) return logo;
        if (image != null && !image.isEmpty()) return image;
        throw new CompanyLogoException(400, "Veuillez fournir une image valide (paramètre 'file', 'logo' ou 'image').");
    }

    /** users.id vient uniquement du token (claim "sub"). */
    private static long userId(Principal principal) {
        if (principal == null) {
            throw new CompanyLogoException(401, "L'authentification est requise.");
        }
        try {
            long id = Long.parseLong(principal.getName());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new CompanyLogoException(401, "Le jeton ne contient pas un identifiant utilisateur valide.");
        }
    }

    private static long positive(Long companyId) {
        if (companyId == null || companyId <= 0) {
            throw new CompanyLogoException(400, "L'identifiant de l'entreprise doit être un entier positif.");
        }
        return companyId;
    }
}
