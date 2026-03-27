package com.wype.registerfirst;

public final class RegistrationResult {
    private final String userName;
    private final String deviceId;
    private final String registrationId;
    private final String status;
    private final String message;

    public RegistrationResult(
            String userName,
            String deviceId,
            String registrationId,
            String status,
            String message
    ) {
        this.userName = userName;
        this.deviceId = deviceId;
        this.registrationId = registrationId;
        this.status = status;
        this.message = message;
    }

    public String getUserName() {
        return userName;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return "REGISTERED".equals(status);
    }
}
