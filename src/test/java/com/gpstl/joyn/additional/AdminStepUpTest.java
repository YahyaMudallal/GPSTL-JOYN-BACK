package com.gpstl.joyn.additional;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminStepUpTest {
    private static final long ADMIN_ID = 42L;

    private static JdbcTemplate database() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(ADMIN_ID))).thenReturn(
                List.of(Map.of("email", "admin@example.com", "password_hash", "hashed")));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(ADMIN_ID))).thenReturn(1);
        return jdbc;
    }

    private static PasswordEncoder passwordEncoder() {
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(passwords.matches("valid-password", "hashed")).thenReturn(true);
        return passwords;
    }

    @Test
    void fixedCodeIsRejectedByDefaultWhenTotpIsNotConfigured() {
        AdminStepUp stepUp = new AdminStepUp(database(), passwordEncoder(), "", false, "123456");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> stepUp.verify(ADMIN_ID, "valid-password", "123456"));
        assertEquals(412, error.getStatusCode().value());
    }

    @Test
    void explicitlyEnabledDemoCodeWorksOnlyWhenTotpConfigurationIsEmpty() {
        AdminStepUp stepUp = new AdminStepUp(database(), passwordEncoder(), "", true, "123456");
        assertDoesNotThrow(() -> stepUp.verify(ADMIN_ID, "valid-password", "123456"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> stepUp.verify(ADMIN_ID, "valid-password", "654321"));
        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void demoCodeDoesNotBypassPasswordVerification() {
        AdminStepUp stepUp = new AdminStepUp(database(), passwordEncoder(), "", true, "123456");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> stepUp.verify(ADMIN_ID, "wrong-password", "123456"));
        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void demoCodeIsDisabledWhenTotpSecretsAreConfigured() {
        String configured = "{\"other@example.com\":\"JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP\"}";
        AdminStepUp stepUp = new AdminStepUp(database(), passwordEncoder(), configured, true, "123456");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> stepUp.verify(ADMIN_ID, "valid-password", "123456"));
        assertEquals(412, error.getStatusCode().value());
    }

    @Test
    void invalidTestCodeConfigurationFailsAtStartup() {
        assertThrows(IllegalStateException.class,
                () -> new AdminStepUp(database(), passwordEncoder(), "", true, "12345"));
    }
}
