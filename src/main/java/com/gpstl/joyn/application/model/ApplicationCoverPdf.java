package com.gpstl.joyn.application.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "application_cover_pdfs")
public class ApplicationCoverPdf {
    @Id
    @Column(name = "application_id")
    private Long applicationId;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    protected ApplicationCoverPdf() {}

    public ApplicationCoverPdf(Long applicationId, byte[] content) {
        this.applicationId = applicationId;
        this.content = content;
    }

    public byte[] getContent() {
        return content;
    }
}
