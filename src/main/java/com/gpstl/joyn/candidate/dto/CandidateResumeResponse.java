package com.gpstl.joyn.candidate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CandidateResumeResponse(@JsonProperty("cv_url") String resumeUrl) {
}
