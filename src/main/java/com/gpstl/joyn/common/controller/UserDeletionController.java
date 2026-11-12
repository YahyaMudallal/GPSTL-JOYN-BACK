
package com.gpstl.joyn.common.controller;

import com.gpstl.joyn.common.service.UserDeletionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserDeletionController {

    private final UserDeletionService userDeletionService;

    public UserDeletionController(UserDeletionService userDeletionService) {
        this.userDeletionService = userDeletionService;
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {

        String reason = body == null ? null : body.get("reason");
        String ipAddress = request.getRemoteAddr();

        userDeletionService.deleteUser(userId, jwt, reason, ipAddress);

        return ResponseEntity.noContent().build();
    }
}
