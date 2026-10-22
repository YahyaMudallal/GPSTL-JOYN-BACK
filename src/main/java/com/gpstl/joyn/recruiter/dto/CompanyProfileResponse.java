
package com.gpstl.joyn.recruiter.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CompanyProfileResponse(
        Long id,
        String name,
        String logoUrl,
        String sector,
        String companySize,
        String website,
        String description,
        String address,
        int activeJobsCount,
        List<ActiveJob> jobs
) {
    public record ActiveJob(
            Long id,
            String title,
            String contractType,
            String location,
            String remotePolicy,
            OffsetDateTime publishedAt
    ) {}
}
