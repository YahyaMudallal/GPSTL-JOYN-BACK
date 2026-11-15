package com.gpstl.joyn.recruiter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RecruiterProfileResponse(
        Long profileId,
        Long userId,
        String email,
        String firstName,
        String lastName,
        String companyName,
        @JsonProperty("photo_url") String photoUrl,
        @JsonProperty("profile_picture_url") String profilePictureUrl
) {
    public RecruiterProfileResponse(
            Long profileId,
            Long userId,
            String email,
            String firstName,
            String lastName,
            String companyName
    ) {
        this(profileId, userId, email, firstName, lastName, companyName, null, null);
    }
}
