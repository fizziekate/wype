package com.wype.registerfirst;

public record RegistrationResult(
    String userName,
    String deviceId,
    String registrationId,
    String status,
    String message
) {
    public boolean success() {
        return "REGISTERED".equals(status);
    }
}
