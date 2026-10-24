package com.gpstl.joyn.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Standard error response returned by application endpoints")
public record ApplicationErrorResponse(
        @Schema(example = "400")
        int status,
        @Schema(example = "Bad Request")
        String error,
        @Schema(example = "Request validation failed")
        String message,
        List<FieldError> errors) {

    @Schema(description = "A validation error associated with a request field")
    public record FieldError(String field, String message) {
    }
}
