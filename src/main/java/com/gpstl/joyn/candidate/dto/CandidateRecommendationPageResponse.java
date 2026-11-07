package com.gpstl.joyn.candidate.dto;

import java.util.List;

public record CandidateRecommendationPageResponse(
    List<CandidateRecommendationItem> content,
    int page,
    int limit,
    long totalElements,
    int totalPages,
    boolean hasNext) {}
