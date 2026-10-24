package com.gpstl.joyn.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Job offer and submission details; the candidate is identified by the authenticated JWT")
public record CreateApplicationRequest(

    @Schema(
        description = "ID of the job offer",
        example = "34")
    @NotNull
    @Positive
    Long jobOfferId,

    @Schema(
        description = "Optional message submitted with the application",
        example = "I am excited to apply for this position.")
    @Size(max = 10000)
    String coverMessage,

    @Schema(
        description = "Optional URL of the resume attached to the application",
        example = "https://example.com/resumes/candidate-12.pdf")
    @Size(max = 2048)
    String resumeUrl
) {
}
