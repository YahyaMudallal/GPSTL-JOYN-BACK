package com.gpstl.joyn.resume.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ResumeParseResponse(
    
    @JsonPropertyDescription("Information about the candidate's contact details")
    ContactInfo contactInfo,

    @JsonPropertyDescription ("List of the candidate's educational qualifications")
    List<Education> education,

    @JsonPropertyDescription ("List of the candidate's work experiences")
    List<Experience> experiences,

    @JsonPropertyDescription ("List of the candidate's skills")
    List<String> skills
) {

    public record ContactInfo(
        String firstName,
        String lastName,
        String email,
        String phone,
        String city,
        String country
    ) {}

    public record Experience(
        String jobTitle,
        String company,

        @JsonPropertyDescription("Format YYYY-MM") 
        String startDate,
        
        @JsonPropertyDescription("Format YYYY-MM or 'Present' if currently employed") 
        String endDate
    ) {}

    public record Education(
        String degree,
        String institution,
        
        @JsonPropertyDescription("Format YYYY-MM") 
        String startDate,
        
        @JsonPropertyDescription("Format YYYY-MM or 'Present' if currently employed") 
        String endDate
    ) {}
}