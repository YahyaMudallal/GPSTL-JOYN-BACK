package com.gpstl.joyn.resume.service;

import java.io.IOException;
import java.util.Objects;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.gpstl.joyn.resume.dto.ResumeParseResponse;

@Service 
public class ResumeService {

    private final Tika tika = new Tika();
    
    public ResumeParseResponse parseResume(
        MultipartFile resumeFile
    ) {
        // check that the file is provided
        if (resumeFile == null || resumeFile.isEmpty()) {
            throw new IllegalArgumentException("A resume file is required");
        }

        // check that the file is a PDF
        try (var inputStream = resumeFile.getInputStream()) {
            String detectedType = tika.detect(inputStream);

            if (!Objects.equals(detectedType, "application/pdf")) {
                throw new IllegalArgumentException("The uploaded file must be a PDF");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read the uploaded file", e);
        }

        throw new UnsupportedOperationException("Resume parsing is not implemented yet.");
    }

}
