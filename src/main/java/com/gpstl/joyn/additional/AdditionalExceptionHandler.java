package com.gpstl.joyn.additional;

import com.gpstl.joyn.application.exception.ApplicationException;
import com.gpstl.joyn.candidate.exception.CandidateProfileException;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice(basePackageClasses=ActorAccess.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdditionalExceptionHandler {
    @ExceptionHandler(JobApiException.class)
    public ResponseEntity<?> job(JobApiException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<?> application(ApplicationException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(CandidateProfileException.class)
    public ResponseEntity<?> candidateProfile(CandidateProfileException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Request refused":e.getReason()));}
}
