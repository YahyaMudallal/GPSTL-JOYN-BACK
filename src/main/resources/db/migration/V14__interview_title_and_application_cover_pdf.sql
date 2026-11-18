ALTER TABLE interviews ADD COLUMN title VARCHAR(160);

CREATE TABLE application_cover_pdfs (
    application_id BIGINT PRIMARY KEY REFERENCES applications(id) ON DELETE CASCADE,
    content BYTEA NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 5242880)
);
