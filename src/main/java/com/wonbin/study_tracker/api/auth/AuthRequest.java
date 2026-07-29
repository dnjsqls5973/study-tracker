package com.wonbin.study_tracker.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

public class AuthRequest {

    @Getter
    public static class Register {
        @NotBlank
        @Email
        private String email;

        @NotBlank
        private String name;

        @NotBlank
        private String password;
    }

    @Getter
    public static class Login {
        @NotBlank
        @Email
        private String email;

        @NotBlank
        private String password;
    }

    @Getter
    public static class DeviceRegister {
        @NotBlank
        private String deviceName;

        @NotBlank
        private String deviceType;
    }
}
