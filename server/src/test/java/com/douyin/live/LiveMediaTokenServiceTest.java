package com.douyin.live;

import com.douyin.service.LiveMediaTokenService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveMediaTokenServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_700_000_000), ZoneOffset.UTC);
    private static final String CALLBACK_SECRET = "callback-secret-0123456789012345678901";

    @Test
    void signsAndValidatesPurposeBoundToken() {
        LiveMediaTokenService service = service(true, CALLBACK_SECRET);
        var issued = service.issue(42L, "live-key", 7L, LiveMediaTokenService.Purpose.PLAY);

        assertTrue(service.validate(issued.value(), 42L, "live-key", LiveMediaTokenService.Purpose.PLAY));
        assertTrue(service.validate(
                issued.value(), 42L, "live-key", LiveMediaTokenService.Purpose.PLAY, 7L));
        assertFalse(service.validate(issued.value(), 42L, "live-key", LiveMediaTokenService.Purpose.INGEST));
        assertFalse(service.validate(issued.value(), 42L, "other-key", LiveMediaTokenService.Purpose.PLAY));
        assertFalse(service.validate(issued.value() + "x", 42L, "live-key", LiveMediaTokenService.Purpose.PLAY));
    }

    @Test
    void rejectsExpiredTokenAndUnconfiguredCallback() {
        LiveMediaTokenService service = new LiveMediaTokenService(
                true,
                "01234567890123456789012345678901",
                30,
                "",
                Clock.fixed(Instant.ofEpochSecond(1_700_000_000), ZoneOffset.UTC));
        var issued = service.issue(1L, "key", 2L, LiveMediaTokenService.Purpose.INGEST);
        LiveMediaTokenService expired = new LiveMediaTokenService(
                true,
                "01234567890123456789012345678901",
                30,
                "",
                Clock.fixed(Instant.ofEpochSecond(1_700_000_031), ZoneOffset.UTC));

        assertFalse(expired.validate(issued.value(), 1L, "key", LiveMediaTokenService.Purpose.INGEST));
        assertFalse(service.validateCallbackToken("callback-secret"));
    }

    @Test
    void validatesSharedCallbackSecretConstantTime() {
        LiveMediaTokenService service = service(true, CALLBACK_SECRET);
        assertTrue(service.validateCallbackToken(" " + CALLBACK_SECRET + " "));
        assertFalse(service.validateCallbackToken("wrong-secret"));
    }

    @Test
    void rejectsWeakCallbackSecretWhenMediaAuthIsEnabled() {
        assertThrows(IllegalStateException.class, () -> service(true, "too-short"));
    }

    private static LiveMediaTokenService service(boolean enabled, String callbackSecret) {
        return new LiveMediaTokenService(
                enabled,
                "01234567890123456789012345678901",
                300,
                callbackSecret,
                CLOCK);
    }
}
