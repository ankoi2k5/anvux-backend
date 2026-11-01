package com.anvux.auth;

import java.time.Instant;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anvux.auth.dto.AuthResponse;
import com.anvux.auth.dto.LoginRequest;
import com.anvux.auth.dto.RegisterRequest;
import com.anvux.user.User;
import com.anvux.user.UserRepository;
import com.anvux.user.UserResponse;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository users,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()), request.fullName()));
        return buildResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email()))
                .filter(User::isEnabled)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return buildResponse(user);
    }

    /**
     * Đổi refresh token cũ lấy cặp token mới (xoay vòng).
     * noRollbackFor: khi phát hiện token cũ bị dùng lại, việc thu hồi toàn bộ token
     * vẫn phải được lưu dù sau đó ném lỗi.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenService.findByRaw(rawRefreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (stored.getRevokedAt() != null) {
            // Token đã bị thu hồi mà vẫn có người dùng lại: nghi ngờ bị đánh cắp,
            // thu hồi mọi phiên của tài khoản này.
            refreshTokenService.revokeAll(stored.getUser().getId());
            throw new InvalidRefreshTokenException();
        }

        Instant now = Instant.now();
        User user = stored.getUser();
        if (!stored.isActive(now) || !user.isEnabled()) {
            throw new InvalidRefreshTokenException();
        }

        stored.revoke(now);
        return buildResponse(user);
    }

    /** Đăng xuất: thu hồi refresh token. Gọi nhiều lần hoặc token lạ vẫn không báo lỗi. */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.findByRaw(rawRefreshToken)
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    private AuthResponse buildResponse(User user) {
        return new AuthResponse(
                jwtService.createAccessToken(user),
                refreshTokenService.issue(user),
                "Bearer",
                jwtService.accessTokenSeconds(),
                UserResponse.from(user));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}