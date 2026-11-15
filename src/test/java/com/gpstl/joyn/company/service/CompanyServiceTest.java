package com.gpstl.joyn.company.service;

import com.gpstl.joyn.company.dto.CompanyResponse;
import com.gpstl.joyn.company.dto.CompanyStatusUpdateRequest;
import com.gpstl.joyn.company.model.Company;
import com.gpstl.joyn.company.repository.CompanyRepository;
import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.model.OfferStatus;
import com.gpstl.joyn.joboffer.repository.JobOfferAuditRepository;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;
import com.gpstl.joyn.recruiter.model.Recruiter;
import com.gpstl.joyn.recruiter.repository.RecruiterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private JobOfferRepository jobOfferRepository;

    @Mock
    private RecruiterRepository recruiterProfileRepository;

    @Mock
    private JobOfferAuditRepository jobOfferAuditRepository;

    @InjectMocks
    private CompanyService companyService;

    @Test
    @DisplayName("getProfile() - Succès pour une entreprise avec statut VERIFIED")
    void getProfile_VerifiedCompany_PublicAccess() {
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(1L);
        when(company.getName()).thenReturn("TechCorp");
        when(company.getVerificationStatus()).thenReturn("VERIFIED");

        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(jobOfferRepository.findPublicOffersByCompanyId(1L, OfferStatus.PUBLISHED)).thenReturn(List.of());

        CompanyResponse response = companyService.getProfile(1L, false, null);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("TechCorp");
        assertThat(response.jobs()).isEmpty();
    }

    @Test
    @DisplayName("getProfile() - Lève 404 pour une entreprise non vérifiée sans authentification")
    void getProfile_UnverifiedCompany_Unauthenticated_ThrowsNotFound() {
        Company company = mock(Company.class);
        when(company.getVerificationStatus()).thenReturn("PENDING");
        when(companyRepository.findById(2L)).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> companyService.getProfile(2L, false, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("getProfile() - Autorisé pour une entreprise non vérifiée si le recruteur lui est rattaché")
    void getProfile_UnverifiedCompany_RecruiterOwner_Allowed() {
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(3L);
        when(company.getName()).thenReturn("SecretStartup");
        when(company.getVerificationStatus()).thenReturn("PENDING");

        // Mock JWT
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("role")).thenReturn("RECRUITER");
        when(jwt.getSubject()).thenReturn("42");

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(jwt);

        // Mock RecruiterProfile rattaché à l'entreprise
        Recruiter profile = mock(Recruiter.class);
        when(profile.getCompany()).thenReturn(company);

        when(companyRepository.findById(3L)).thenReturn(Optional.of(company));
        when(recruiterProfileRepository.findByUserId(42L)).thenReturn(Optional.of(profile));
        when(jobOfferRepository.findPublicOffersByCompanyId(3L, OfferStatus.PUBLISHED)).thenReturn(List.of());

        CompanyResponse response = companyService.getProfile(3L, false, auth);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.name()).isEqualTo("SecretStartup");
    }

    @Test
    @DisplayName("updateVerificationStatus() - 200 OK quand l'admin met à jour le statut vers VERIFIED")
    void updateVerificationStatus_AdminSuccess() {
        Company company = new Company("JoynCorp");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("role")).thenReturn("ADMIN");
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(jwt);

        CompanyResponse response = companyService.updateVerificationStatus(
                1L,
                new CompanyStatusUpdateRequest("VERIFIED"),
                auth
        );

        assertThat(response.id()).isEqualTo(company.getId());
        assertThat(company.getVerificationStatus()).isEqualTo("VERIFIED");
    }

    @Test
    @DisplayName("updateVerificationStatus() - 403 Forbidden si l'utilisateur n'est pas ADMIN")
    void updateVerificationStatus_NonAdmin_ThrowsForbidden() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("role")).thenReturn("RECRUITER");
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(jwt);

        assertThatThrownBy(() -> companyService.updateVerificationStatus(
                1L,
                new CompanyStatusUpdateRequest("VERIFIED"),
                auth
        )).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    @DisplayName("updateVerificationStatus() - 400 Bad Request pour un statut invalide")
    void updateVerificationStatus_InvalidStatus_ThrowsBadRequest() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("role")).thenReturn("ADMIN");
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(jwt);

        assertThatThrownBy(() -> companyService.updateVerificationStatus(
                1L,
                new CompanyStatusUpdateRequest("INVALID_STATUS"),
                auth
        )).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400 BAD_REQUEST");
    }

    // Tests R79 - Modification du profil d'une entreprise

    @Test
    @DisplayName("updateProfile() - Le recruteur peut modifier le profil de son entreprise")
    void updateProfile_RecruiterOwner_Success() {
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(1L);
        when(company.getVerificationStatus()).thenReturn("VERIFIED");

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("role")).thenReturn("RECRUITER");
        when(jwt.getSubject()).thenReturn("42");

        Authentication auth = mock(Authentication.class);
when(auth.getPrincipal()).thenReturn(jwt);

        Recruiter recruiter = mock(Recruiter.class);
        when(recruiter.getCompany()).thenReturn(company);

        when(recruiterProfileRepository.findByUserId(42L)).thenReturn(Optional.of(recruiter));
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(jobOfferRepository.findPublicOffersByCompanyId(1L, OfferStatus.PUBLISHED))
                .thenReturn(List.of());

        CompanyResponse response = companyService.updateProfile(
                1L,
                Map.of("description", "Nouvelle description"),
                auth
        );

        verify(company).setDescription("Nouvelle description");
        verify(companyRepository).save(company);
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("updateProfile() - Un recruteur ne peut pas modifier une autre entreprise")
    void updateProfile_OtherCompany_ThrowsForbidden() {
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(2L);

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("role")).thenReturn("RECRUITER");
        when(jwt.getSubject()).thenReturn("42");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);

        Recruiter recruiter = mock(Recruiter.class);
        when(recruiter.getCompany()).thenReturn(company);

        when(recruiterProfileRepository.findByUserId(42L)).thenReturn(Optional.of(recruiter));

        assertThatThrownBy(() -> companyService.updateProfile(
                1L,
                Map.of("name", "Autre entreprise"),
                auth
        )).isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("403 FORBIDDEN");

        verify(companyRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProfile() - Le recruteur ne peut pas modifier le statut de vérification")
    void updateProfile_ProtectedField_ThrowsBadRequest() {
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(1L);

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("role")).thenReturn("RECRUITER");
        when(jwt.getSubject()).thenReturn("42");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);

        Recruiter recruiter = mock(Recruiter.class);
        when(recruiter.getCompany()).thenReturn(company);

        when(recruiterProfileRepository.findByUserId(42L)).thenReturn(Optional.of(recruiter));
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> companyService.updateProfile(
                1L,
                Map.of("verificationStatus", "VERIFIED"),
                auth
        )).isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("400 BAD_REQUEST");

        verify(companyRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // deleteCompany() : suppression logique
    // ---------------------------------------------------------------------

    private static Authentication jwtAuth(String role, String subject) {
        Jwt jwt = mock(Jwt.class);
        lenient().when(jwt.getClaimAsString("role")).thenReturn(role);
        lenient().when(jwt.getSubject()).thenReturn(subject);
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getPrincipal()).thenReturn(jwt);
        return auth;
    }

    private static Recruiter recruiterOf(Long companyId) {
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(companyId);
        Recruiter recruiter = mock(Recruiter.class);
        when(recruiter.getCompany()).thenReturn(company);
        return recruiter;
    }

    @Test
    @DisplayName("deleteCompany() - ADMIN : entreprise DELETED et offres actives clôturées")
    void deleteCompany_Admin_SoftDeletesCompanyAndClosesOffers() {
        Company company = new Company("JoynCorp");
        JobOffer published = new JobOffer();
        published.setStatus(OfferStatus.PUBLISHED);
        JobOffer draft = new JobOffer();
        draft.setStatus(OfferStatus.DRAFT);

        when(companyRepository.findById(5L)).thenReturn(Optional.of(company));
        when(jobOfferRepository.findByCompanyIdAndDeletedAtIsNull(5L)).thenReturn(List.of(published, draft));

        companyService.deleteCompany(5L, "Cessation d'activité", jwtAuth("ADMIN", "1"));

        assertThat(company.isDeleted()).isTrue();
        assertThat(company.getStatus()).isEqualTo("DELETED");
        assertThat(company.getDeletedAt()).isNotNull();
        assertThat(published.getStatus()).isEqualTo(OfferStatus.CLOSED);
        assertThat(draft.getStatus()).isEqualTo(OfferStatus.CLOSED);
        assertThat(published.getDeletedAt()).isNotNull();
        assertThat(draft.getDeletedAt()).isNotNull();
        verify(companyRepository).save(company);
        verify(jobOfferRepository).saveAll(List.of(published, draft));
        verify(jobOfferAuditRepository).saveAll(argThat(audits -> audits.spliterator().getExactSizeIfKnown() == 2));
    }

    @Test
    @DisplayName("deleteCompany() - RECRUITER de l'entreprise : suppression autorisée")
    void deleteCompany_RecruiterOfCompany_Allowed() {
        Company company = new Company("JoynCorp");
        Recruiter recruiter = recruiterOf(5L);

        when(recruiterProfileRepository.findByUserId(42L)).thenReturn(Optional.of(recruiter));
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company));
        when(jobOfferRepository.findByCompanyIdAndDeletedAtIsNull(5L)).thenReturn(List.of());

        companyService.deleteCompany(5L, null, jwtAuth("RECRUITER", "42"));

        assertThat(company.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("deleteCompany() - 403 pour un recruteur d'une autre entreprise")
    void deleteCompany_RecruiterOfOtherCompany_Forbidden() {
        Recruiter recruiter = recruiterOf(99L);
        when(recruiterProfileRepository.findByUserId(42L)).thenReturn(Optional.of(recruiter));

        assertThatThrownBy(() -> companyService.deleteCompany(5L, null, jwtAuth("RECRUITER", "42")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(companyRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteCompany() - 403 pour un candidat")
    void deleteCompany_Candidate_Forbidden() {
        assertThatThrownBy(() -> companyService.deleteCompany(5L, null, jwtAuth("CANDIDATE", "7")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("deleteCompany() - 401 sans authentification")
    void deleteCompany_Unauthenticated_Unauthorized() {
        assertThatThrownBy(() -> companyService.deleteCompany(5L, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("deleteCompany() - 404 si l'entreprise n'existe pas")
    void deleteCompany_Unknown_NotFound() {
        when(companyRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.deleteCompany(5L, null, jwtAuth("ADMIN", "1")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("deleteCompany() - 404 si l'entreprise est déjà supprimée")
    void deleteCompany_AlreadyDeleted_NotFound() {
        Company company = new Company("JoynCorp");
        company.markDeleted(Instant.now());
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> companyService.deleteCompany(5L, null, jwtAuth("ADMIN", "1")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(jobOfferRepository, never()).findByCompanyIdAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("getProfile() - 404 pour une entreprise supprimée, même vérifiée")
    void getProfile_DeletedCompany_NotFound() {
        Company company = new Company("JoynCorp");
        company.setVerificationStatus("VERIFIED");
        company.markDeleted(Instant.now());
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> companyService.getProfile(5L, false, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
