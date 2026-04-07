package com.spring.jwt.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class LoginRequest {

        @Schema(description = "Email of User", example = "example@example.com")
        @JsonAlias("email")
        private String username;

        @Schema(description = "Password to create an account", example = "Pass@1234")
        private String password;
}
