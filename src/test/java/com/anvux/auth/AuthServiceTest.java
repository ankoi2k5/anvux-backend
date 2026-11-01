package com.anvux.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.anvux.auth.dto.AuthResponse;
import com.anvux.auth.dto.LoginRequest;
import com.anvux.auth.dto.RegisterRequest;
import com.anvux.user.User;
import com.anvux.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_withExistingEmail_throwsAndDoesNotSave() {
        when(users.existsByEmail("an@test.com")).thenReturn(true);

        assertThrows(EmailAlreadyUsedException.class,
                () -> authService.register(new RegisterRequest("An", "  An@Test.com ", "12345678")));

        verify(users, never()).save(any(User.class));
    }

    @Test
    void register_storesHashedPasswordNotPlainText() {
        when(passwordEncoder.encode("12345678")).thenReturn("HASHED");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.createAccessToken(any(User.class))).thenReturn("access-token");
        when(refreshTokenService.issue(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = authService.register(new RegisterRequest("An", "An@Test.com", "12345678"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertEquals("HASHED", captor.getValue().getPasswordHash());
        assertEquals("an@test.com", captor.getValue().getEmail());
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
    }

    @Test
    void login_withWrongPassword_throws() {
        User user = new User("an@test.com", "HASHED", "An");
        when(users.findByEmail("an@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("sai-mat-khau", "HASHED")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("an@test.com", "sai-mat-khau")));
    }

    @Test
    void login_withUnknownEmail_throws() {
        when(users.findByEmail("khong-co@test.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("khong-co@test.com", "12345678")));
    }
}