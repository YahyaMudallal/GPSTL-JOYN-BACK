ALTER TABLE candidate_profiles
    DROP CONSTRAINT ck_candidate_location_coordinates,
    ADD CONSTRAINT ck_candidate_location_coordinates
        CHECK ((latitude IS NULL AND longitude IS NULL)
            OR (latitude IS NOT NULL AND longitude IS NOT NULL
                AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180));

ALTER TABLE job_offers
    DROP CONSTRAINT ck_job_offer_location_coordinates,
    ADD CONSTRAINT ck_job_offer_location_coordinates
        CHECK ((latitude IS NULL AND longitude IS NULL)
            OR (latitude IS NOT NULL AND longitude IS NOT NULL
                AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180));
