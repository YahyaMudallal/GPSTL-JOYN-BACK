package com.gpstl.joyn.joboffer.controller;

import com.gpstl.joyn.joboffer.exception.JobApiException;
import java.security.Principal;

public final class JobRequestIdentity {
    private JobRequestIdentity() {}

    public static long userId(Principal principal) {
        if (principal == null) throw new JobApiException(401, "Authentication is required");
        try {
            long id = Long.parseLong(principal.getName());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            throw new JobApiException(401, "Authentication must provide the database user ID");
        }
    }

    public static void validateId(long id) {
        if (id <= 0) throw new JobApiException(400, "jobId must be a positive integer");
    }
}
