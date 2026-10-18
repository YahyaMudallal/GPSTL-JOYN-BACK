package com.gpstl.joyn.recruiter.dto;

public record RecruiterRegisterResponse(
    Long profileId,
    Long userId,
    String email,
    String firstName,
    String lastName,
    String companyName
) {}