package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.candidate.dto.CandidateProfileResponse;
import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.model.RemoteMode;
import com.gpstl.joyn.joboffer.repository.*;
import com.gpstl.joyn.user.service.UserProfilePictureStorage;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecruiterApplicantLiveScoreTest {
    @Test
    void seededApplicationGetsCurrentScoreWithoutCacheOrCandidateLogin() {
        var jobs = mock(JobOfferRepository.class);
        var access = mock(JobAccessService.class);
        var rows = mock(RecruiterJobApplicantRepository.class);
        var candidates = mock(CandidateRepository.class);
        var resumes = mock(CandidateResumeStorage.class);
        var photos = mock(UserProfilePictureStorage.class);
        var job = mock(JobOffer.class);
        when(job.getRemoteMode()).thenReturn(RemoteMode.REMOTE);
        when(job.getSkills()).thenReturn(java.util.Set.of());
        var candidate = mock(Candidate.class);
        when(candidate.getSkills()).thenReturn(List.of());
        // Mockito supplies zero for boxed numeric getters; missing coordinates must be explicit.
        when(candidate.getLatitude()).thenReturn(null);
        when(candidate.getLongitude()).thenReturn(null);
        var row = mock(RecruiterJobApplicantProjection.class);
        when(row.getCandidateId()).thenReturn(7L);
        when(row.getApplicationId()).thenReturn(9L);
        when(row.getMatchScore()).thenReturn(null);
        when(jobs.findById(12L)).thenReturn(Optional.of(job));
        when(rows.findApplicants(12L, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of(row)));
        when(candidates.findProfileById(7L)).thenReturn(Optional.of(candidate));
        try (var profiles = mockStatic(CandidateProfileResponse.class)) {
            var service = new RecruiterJobApplicantService(jobs, access, rows, candidates, resumes, photos);
            var response = service.findApplicants(1L, 12L, 1, 10);
            // Remote is the only available criterion: its weight is normalized to 100%.
            assertEquals(100, response.content().getFirst().matchScore());
            assertNull(response.content().getFirst().scoreBreakdown().skills());
            verify(access).checkCompanyAccess(1L, job);
            // A later offer change must not return the earlier score.
            when(job.getRemoteMode()).thenReturn(RemoteMode.ONSITE);
            assertEquals(0, service.findApplicants(1L, 12L, 1, 10).content().getFirst().matchScore());
        }
    }

    @Test
    void authorizationFailureStopsApplicantReads() {
        var jobs = mock(JobOfferRepository.class);
        var access = mock(JobAccessService.class);
        var rows = mock(RecruiterJobApplicantRepository.class);
        var job = mock(JobOffer.class);
        when(jobs.findById(12L)).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("forbidden")).when(access).checkCompanyAccess(1L, job);
        var service = new RecruiterJobApplicantService(jobs, access, rows,
            mock(CandidateRepository.class), mock(CandidateResumeStorage.class), mock(UserProfilePictureStorage.class));
        assertThrows(IllegalStateException.class, () -> service.findApplicants(1L, 12L, 1, 10));
        verifyNoInteractions(rows);
    }
}
