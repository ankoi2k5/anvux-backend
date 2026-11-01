package com.anvux.common;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {

    private static final int MAX_KEY_LENGTH = 100;

    private final IdempotencyKeyRepository repository;

    public IdempotencyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    /** Trả về khóa đã chuẩn hóa, hoặc null nếu client không gửi khóa. */
    public String validate(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key tối đa " + MAX_KEY_LENGTH + " ký tự");
        }
        return trimmed;
    }

    public Optional<UUID> findResourceId(UUID userId, String key, String resourceType) {
        return repository.findByUserIdAndRequestKey(userId, key)
                .filter(entry -> resourceType.equals(entry.getResourceType()))
                .map(IdempotencyKey::getResourceId);
    }

    public void remember(UUID userId, String key, String resourceType, UUID resourceId) {
        IdempotencyKey entry = repository.findByUserIdAndRequestKey(userId, key)
                .orElseGet(() -> new IdempotencyKey(userId, key));
        entry.pointTo(resourceType, resourceId);
        repository.save(entry);
    }
}