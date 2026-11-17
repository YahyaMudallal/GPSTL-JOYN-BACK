package com.gpstl.joyn.company.service;

import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.company.dto.CompanyLogoResponse;
import com.gpstl.joyn.company.exception.CompanyLogoException;
import com.gpstl.joyn.company.model.Company;
import com.gpstl.joyn.company.repository.CompanyRepository;
import com.gpstl.joyn.recruiter.repository.RecruiterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Gestion du logo d'une entreprise (R84) : envoyer, remplacer, supprimer, consulter.
 * Même fonctionnement que les photos de profil (U86, UserProfilePictureService).
 *
 * Qui peut modifier le logo ?
 *   - un recruteur de CETTE entreprise (tous les recruteurs de l'entreprise, comme pour A64) ;
 *   - un administrateur, pour n'importe quelle entreprise.
 * Tout le monde peut consulter le logo : il s'affiche sur les offres publiques.
 */
@Service
public class CompanyLogoService {

    private static final Logger log = LoggerFactory.getLogger(CompanyLogoService.class);

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final RecruiterRepository recruiterRepository;
    private final CompanyLogoImageProcessor imageProcessor;
    private final CompanyLogoStorage storage;

    public CompanyLogoService(CompanyRepository companyRepository,
                              UserRepository userRepository,
                              RecruiterRepository recruiterRepository,
                              CompanyLogoImageProcessor imageProcessor,
                              CompanyLogoStorage storage) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.recruiterRepository = recruiterRepository;
        this.imageProcessor = imageProcessor;
        this.storage = storage;
    }

    /** Envoie ou remplace le logo. L'ancien fichier est supprimé une fois le nouveau bien enregistré. */
    @Transactional
    public CompanyLogoResponse updateLogo(long userId, long companyId, MultipartFile file) {
        Company company = findCompanyEditableBy(userId, companyId);

        // 1. Contrôle et conversion de l'image (avant de toucher au stockage)
        byte[] webpBytes = imageProcessor.processLogo(file);

        // 2. Nouveau fichier d'abord : si l'envoi échoue, l'ancien logo reste en place
        String oldKey = company.getLogoUrl();
        String newKey = storage.store(companyId, webpBytes);

        // 3. La base pointe vers le nouveau logo
        company.setLogoUrl(newKey);
        companyRepository.save(company);

        // 4. Ménage : l'ancien fichier ne sert plus (une ancienne URL externe n'est pas touchée)
        if (oldKey != null && !oldKey.isBlank() && !oldKey.equals(newKey)) {
            storage.delete(oldKey);
        }

        log.info("Logo mis à jour pour l'entreprise id={} par l'utilisateur id={}", companyId, userId);
        return CompanyLogoResponse.of(companyId, storage.createPresignedUrl(newKey));
    }

    /** Supprime le logo : l'entreprise n'en a plus (le front affiche alors l'initiale). */
    @Transactional
    public void deleteLogo(long userId, long companyId) {
        Company company = findCompanyEditableBy(userId, companyId);
        String currentKey = company.getLogoUrl();

        company.setLogoUrl(null);
        companyRepository.save(company);

        if (currentKey != null && !currentKey.isBlank()) {
            storage.delete(currentKey);
        }
        log.info("Logo supprimé pour l'entreprise id={} par l'utilisateur id={}", companyId, userId);
    }

    /** Consulter le logo d'une entreprise (logo_url = null si elle n'en a pas). */
    @Transactional(readOnly = true)
    public CompanyLogoResponse getLogo(long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyLogoException(404, "Entreprise introuvable."));
        return CompanyLogoResponse.of(companyId, storage.createPresignedUrlIfValid(company.getLogoUrl()));
    }

    /** L'entreprise existe-t-elle, et l'utilisateur a-t-il le droit de modifier son logo ? */
    private Company findCompanyEditableBy(long userId, long companyId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CompanyLogoException(401, "Utilisateur introuvable."));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new CompanyLogoException(403, "Compte inactif ou suspendu.");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyLogoException(404, "Entreprise introuvable."));

        if ("ADMIN".equals(user.getRole())) {
            return company;
        }
        if ("RECRUITER".equals(user.getRole())) {
            boolean ownCompany = recruiterRepository.findByUserId(userId)
                    .map(recruiter -> recruiter.getCompany() != null
                            && recruiter.getCompany().getId().equals(companyId))
                    .orElse(false);
            if (ownCompany) {
                return company;
            }
            throw new CompanyLogoException(403, "Vous ne pouvez modifier que le logo de votre entreprise.");
        }
        throw new CompanyLogoException(403, "Seuls les recruteurs de l'entreprise et les administrateurs peuvent modifier le logo.");
    }
}
