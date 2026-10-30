package com.anvux.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.anvux.config.SecurityProperties;
import com.anvux.user.User;

@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final SecurityProperties props;

    public RefreshTokenService(RefreshTokenRepository repository, SecurityProperties props) {
        this.repository = repository;
        this.props = props;
    }

    /** Tạo refresh token mới, lưu bản băm vào database, trả về chuỗi gốc để gửi cho app. */
    public String issue(User user) {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(props.refreshTokenDays(), ChronoUnit.DAYS);
        repository.save(new RefreshToken(user, hash(raw), expiresAt));
        return raw;
    }

    public static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 không khả dụng", e);
        }
    }
}