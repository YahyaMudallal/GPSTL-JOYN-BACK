package com.gpstl.joyn.auth.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Test unitaire de la protection brute-force, avec une horloge qu'on fait avancer à la main. */
class LoginAttemptServiceTest {

    private static final String EMAIL = "test@joyn.dev";

    /** Horloge de test : on choisit nous-mêmes l'heure "actuelle". */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-21T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private final TestClock clock = new TestClock();
    private final LoginAttemptService service = new LoginAttemptService(clock);

    @Test
    void notBlockedBeforeFiveFailures() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.recordFailure(EMAIL);
        }
        assertThat(service.secondsUntilUnlocked(EMAIL)).isZero();
    }

    @Test
    void blockedFifteenMinutesAfterFiveFailures() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure(EMAIL);
        }
        assertThat(service.secondsUntilUnlocked(EMAIL)).isEqualTo(15 * 60);

        clock.advance(Duration.ofMinutes(10));
        assertThat(service.secondsUntilUnlocked(EMAIL)).isEqualTo(5 * 60);

        clock.advance(Duration.ofMinutes(5));
        assertThat(service.secondsUntilUnlocked(EMAIL)).isZero();
    }

    @Test
    void successResetsTheCounter() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.recordFailure(EMAIL);
        }
        service.recordSuccess(EMAIL);
        service.recordFailure(EMAIL);
        assertThat(service.secondsUntilUnlocked(EMAIL)).isZero();
    }

    @Test
    void otherEmailsAreNotAffected() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure(EMAIL);
        }
        assertThat(service.secondsUntilUnlocked("autre@joyn.dev")).isZero();
    }
}
