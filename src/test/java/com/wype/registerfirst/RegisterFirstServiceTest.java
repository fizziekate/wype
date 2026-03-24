package com.wype.registerfirst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RegisterFirstServiceTest {

    private final RegisterFirstService service = new RegisterFirstService();

    @Test
    void registersDeviceWhenInputsAreValid() {
        RegistrationResult result = service.registerFirst("Felicity", "PIXEL-8-PRO");

        assertEquals("REGISTERED", result.status());
        assertEquals("Felicity", result.userName());
        assertEquals("PIXEL-8-PRO", result.deviceId());
        assertTrue(result.registrationId().startsWith("felicity-pixel-8-pro-"));
    }

    @Test
    void trimsWhitespaceBeforeRegistration() {
        RegistrationResult result = service.registerFirst("  Felicity  ", "  pixel-8-pro  ");

        assertEquals("Felicity", result.userName());
        assertEquals("PIXEL-8-PRO", result.deviceId());
    }

    @Test
    void rejectsBlankUserName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.registerFirst("   ", "PIXEL-8-PRO"));

        assertEquals("userName must not be blank", exception.getMessage());
    }

    @Test
    void rejectsBlankDeviceId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.registerFirst("Felicity", ""));

        assertEquals("deviceId must not be blank", exception.getMessage());
    }
}
