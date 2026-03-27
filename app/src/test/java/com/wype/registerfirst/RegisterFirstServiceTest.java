package com.wype.registerfirst;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RegisterFirstServiceTest {
    private final RegisterFirstService service = new RegisterFirstService();

    @Test
    public void registerFirst_normalizesAndRegisters() {
        RegistrationResult result = service.registerFirst("  Felicity  ", "  pixel-8-pro  ");

        assertEquals("REGISTERED", result.status());
        assertEquals("Felicity", result.userName());
        assertEquals("PIXEL-8-PRO", result.deviceId());
        assertTrue(result.registrationId().startsWith("felicity-pixel-8-pro"));
    }
}
