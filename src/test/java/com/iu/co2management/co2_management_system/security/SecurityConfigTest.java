package com.iu.co2management.co2_management_system.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

    @Test
    void passwordIsStoredAsHashNotAsPlainText() {
        String hash = encoder.encode("geheim");

        assertThat(hash).isNotEqualTo("geheim");
        assertThat(encoder.matches("geheim", hash)).isTrue();
    }

    @Test
    void wrongPasswordDoesNotMatch() {
        String hash = encoder.encode("geheim");

        assertThat(encoder.matches("falsch", hash)).isFalse();
    }

    @Test
    void samePasswordGetsDifferentHashesBecauseOfTheSalt() {
        assertThat(encoder.encode("geheim")).isNotEqualTo(encoder.encode("geheim"));
    }
}