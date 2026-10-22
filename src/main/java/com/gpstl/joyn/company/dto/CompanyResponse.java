
package com.gpstl.joyn.company.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CompanyResponse(
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
