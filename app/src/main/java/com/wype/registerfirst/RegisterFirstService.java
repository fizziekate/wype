package com.wype.registerfirst;

import java.util.Locale;

public class RegisterFirstService {
    public RegistrationResult registerFirst(String userName, String deviceId) {
        String normalizedUser = normalizeUser(userName);
        String normalizedDevice = normalizeDevice(deviceId);

        if (normalizedUser.isBlank()) {
            throw new IllegalArgumentException("userName must not be blank");
        }
        if (normalizedDevice.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }

        String registrationId = createRegistrationId(normalizedUser, normalizedDevice);
        String message = "Registration complete for %s on device %s"
            .formatted(normalizedUser, normalizedDevice);

        return new RegistrationResult(
            normalizedUser,
            normalizedDevice,
            registrationId,
            "REGISTERED",
            message
        );
    }

    private static String normalizeUser(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeDevice(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String createRegistrationId(String userName, String deviceId) {
        return "%s-%s".formatted(userName.toLowerCase(Locale.ROOT), deviceId.toLowerCase(Locale.ROOT));
    }
}
