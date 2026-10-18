package com.gpstl.joyn.recruiter.dto;

public record RecruiterRegisterRequest(
    String email,
    String password,
    String firstName,
    String lastName,
    String companyName
) {}