package com.gpstl.joyn.company.service;

import com.gpstl.joyn.company.dto.CompanyResponse;
import com.gpstl.joyn.company.dto.CompanyStatusUpdateRequest;
import com.gpstl.joyn.company.model.Company;
import com.gpstl.joyn.company.repository.CompanyRepository;
import com.gpstl.joyn.joboffer.model.OfferStatus;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;
import com.gpstl.joyn.recruiter.model.Recruiter;
import com.gpstl.joyn.recruiter.repository.RecruiterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
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
}