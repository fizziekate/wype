package com.wype.registerfirst;

import java.util.HashMap;
import java.util.Map;

public final class RegisterFirstApplication {
    private RegisterFirstApplication() {
    }

    public static void main(String[] args) {
        Map<String, String> parsedArgs = parseArgs(args);
        String userName = parsedArgs.getOrDefault("user", "demo-user");
        String deviceId = parsedArgs.getOrDefault("device", "demo-device");

        RegisterFirstService service = new RegisterFirstService();
        RegistrationResult result = service.registerFirst(userName, deviceId);

        System.out.println("=== Register-First Flow ===");
        System.out.println("status: " + result.status());
        System.out.println("message: " + result.message());
        System.out.println("registrationId: " + result.registrationId());
        System.out.println("userName: " + result.userName());
        System.out.println("deviceId: " + result.deviceId());
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> values = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                continue;
            }
            String[] parts = arg.substring(2).split("=", 2);
            if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                values.put(parts[0], parts[1]);
            }
        }
        return values;
    }
}
