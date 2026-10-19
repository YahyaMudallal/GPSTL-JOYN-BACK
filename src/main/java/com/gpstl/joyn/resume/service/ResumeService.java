package com.gpstl.joyn.resume.service;

import java.io.IOException;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.gpstl.joyn.resume.dto.ResumeParseResponse;
import com.gpstl.joyn.resume.parser.GeminiResumeParser;
import com.gpstl.joyn.resume.parser.PdfTextExtractor;

@Service
public class ResumeService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;  // 5 MB

    private final Tika tika = new Tika();

    private final PdfTextExtractor pdfTextExtractor;
    private final GeminiResumeParser geminiResumeParser;

    public ResumeService(
        PdfTextExtractor pdfTextExtractor,
        GeminiResumeParser geminiResumeParser
    ) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.geminiResumeParser = geminiResumeParser;
    }

    /***
     * Parses the given resume file and extracts relevant information.
     * @param resume    The resume file to parse
     * @return          The parsed resume information
    */
    public ResumeParseResponse parseResume(MultipartFile resume) {
        validateFile(resume);

        try (var inputStream = resume.getInputStream()) {
            String detectedType = tika.detect(inputStream);

            if (!"application/pdf".equals(detectedType)) {
                throw new InvalidResumeException(
                    "The uploaded file must be a PDF"
                );
            }
        } catch (IOException e) {
            throw new InvalidResumeException(
                "Unable to read the uploaded file", e
            );
        }

        final String text;
        try {
            text = pdfTextExtractor.extractText(resume);
        } catch (IOException e) {
            throw new InvalidResumeException(
                "The PDF is invalid, encrypted or unreadable", e
            );
        }

        if (text.isBlank()) {
            throw new InvalidResumeException(
                "No text could be extracted from the PDF. "
                + "The document may be a scanned image."
            );
        }

        return geminiResumeParser.parse(text);
    }

    /**
     * Validates the uploaded resume file, by checking its presence and size.
     * @param resume    The resume file to validate
    */
    private void validateFile(MultipartFile resume) {
        if (resume == null || resume.isEmpty()) {
            throw new InvalidResumeException(
                "A resume file is required"
            );
        }

        if (resume.getSize() > MAX_FILE_SIZE) {
            throw new InvalidResumeException(
                "The PDF must not exceed 5 MB"
            );
        }
    }

}