package com.iu.co2management.co2_management_system.security;

import com.iu.co2management.co2_management_system.entity.AppUser;
import com.iu.co2management.co2_management_system.entity.Role;
import com.iu.co2management.co2_management_system.repository.AppUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    private static final String EMAIL = "user@example.com";

    @Mock
    private AppUserRepository appUserRepository;

    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(appUserRepository);
    }

    @Test
    void emailAndPasswordHashAreTakenFromTheStoredUser() {
        when(appUserRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(new AppUser("Test", EMAIL, "stored-hash", null)));

        UserDetails details = service.loadUserByUsername(EMAIL);

        assertThat(details.getUsername()).isEqualTo(EMAIL);
        assertThat(details.getPassword()).isEqualTo("stored-hash");
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleBecomesAnAuthorityWithRolePrefix(Role role) {
        AppUser user = new AppUser("Test", EMAIL, "hash", null);
        user.getRoles().add(role);
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername(EMAIL);

        // "ROLE_" + Enum-Name; dazu passen @RolesAllowed("ADMIN") und die Prüfungen im EmissionService
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_" + role.name());
    }

    @Test
    void multipleRolesAreAllGranted() {
        AppUser user = new AppUser("Test", EMAIL, "hash", null);
        user.getRoles().add(Role.EXECUTIVE);
        user.getRoles().add(Role.SUSTAINABILITY_OFFICER);
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername(EMAIL);

        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_EXECUTIVE", "ROLE_SUSTAINABILITY_OFFICER");
    }

    @Test
    void userWithoutRolesGetsNoAuthorities() {
        when(appUserRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(new AppUser("Test", EMAIL, "hash", null)));

        assertThat(service.loadUserByUsername(EMAIL).getAuthorities()).isEmpty();
    }

    @Test
    void accountIsEnabledAndNotExpiredOrLocked() {
        // Es gibt (noch) kein Deaktivieren von Konten; siehe Löschverhalten und Datenschutz
        when(appUserRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(new AppUser("Test", EMAIL, "hash", null)));

        UserDetails details = service.loadUserByUsername(EMAIL);

        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
    }

    @Test
    void unknownEmailIsRejected() {
        // Ohne Stub liefert das Mock ein leeres Optional
        assertThatThrownBy(() -> service.loadUserByUsername("ghost@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost@example.com");
    }
}