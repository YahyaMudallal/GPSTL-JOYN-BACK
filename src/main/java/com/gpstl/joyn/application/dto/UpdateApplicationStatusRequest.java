package com.gpstl.joyn.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "New status for an existing application")
public record UpdateApplicationStatusRequest(

        @Schema(
            description = "Status code from application_statuses",
            example = "IN_REVIEW",
            allowableValues = {
                "PENDING", 
                "VIEWED", 
                "IN_REVIEW", 
                "INTERVIEW",
                "ACCEPTED", 
                "REJECTED", 
                "WITHDRAWN"})
        @NotBlank
        @Pattern(regexp = "PENDING|VIEWED|IN_REVIEW|INTERVIEW|ACCEPTED|REJECTED|WITHDRAWN")
        String status
) {
}
