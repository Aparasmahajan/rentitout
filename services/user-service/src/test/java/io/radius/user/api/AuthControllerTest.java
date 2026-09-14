package io.radius.user.api;

import io.radius.common.config.RadiusWebAutoConfiguration;
import io.radius.common.security.JwtProperties;
import io.radius.common.security.JwtService;
import io.radius.user.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({RadiusWebAutoConfiguration.class, AuthControllerTest.Beans.class})
class AuthControllerTest {

    @TestConfiguration
    static class Beans {
        @Bean
        JwtService jwtService() { return new JwtService(new JwtProperties()); }
    }

    @Autowired MockMvc mvc;
    @MockBean AuthService auth;

    @Test
    void sends_a_code_for_a_valid_number() throws Exception {
        given(auth.startOtp(anyString()))
                .willReturn(new Dtos.OtpStartResponse("+491700000001", 300, "123456"));

        mvc.perform(post("/api/auth/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"+491700000001"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(300));
    }

    @Test
    void rejects_a_number_that_is_not_a_number() throws Exception {
        mvc.perform(post("/api/auth/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"not-a-phone"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.fields.phone").exists());
    }

    @Test
    void verifying_a_code_returns_tokens() throws Exception {
        UUID id = UUID.randomUUID();
        var me = new Dtos.MeResponse(id, "Amara", "+491700000001", null, null, null, null,
                5, true, null, null, 0, false, null, false, "MEMBER", java.util.List.of());
        given(auth.verifyOtp(any(), any()))
                .willReturn(new Dtos.TokenResponse("access-token", "refresh-token", 900, me));

        mvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"+491700000001","code":"123456","displayName":"Amara"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.me.displayName").value("Amara"));
    }

    @Test
    void logout_without_a_token_is_401() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }
}
