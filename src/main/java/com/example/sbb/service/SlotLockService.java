package com.example.sbb.service;

import com.example.sbb.domain.SlotLock;
import com.example.sbb.domain.User;
import com.example.sbb.repository.SlotLockRepository;
import com.example.sbb.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SlotLockService {

    private final SlotLockRepository slotLockRepository;
    private final UserRepository userRepository;

    @Transactional
    public boolean tryLock(String slotKey, Long userId, Duration ttl) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plus(ttl);
        User userRef = userId != null ? getUserReference(userId) : null;

        Optional<SlotLock> existingLock = slotLockRepository.findBySlotKeyForUpdate(slotKey);
        if (existingLock.isEmpty()) {
            SlotLock newLock = new SlotLock();
            newLock.setSlotKey(slotKey);
            newLock.setUser(userRef);
            newLock.setExpiresAt(expiresAt);
            slotLockRepository.save(newLock);
            return true;
        }

        SlotLock lock = existingLock.get();
        Long currentOwnerId = lock.getUser() != null ? lock.getUser().getId() : null;
        if (Objects.equals(currentOwnerId, userId)) {
            lock.setExpiresAt(expiresAt);
            return true;
        }

        if (!lock.getExpiresAt().isAfter(now)) {
            lock.setUser(userRef);
            lock.setExpiresAt(expiresAt);
            return true;
        }

        return false;
    }

    @Transactional
    public boolean renewLock(String slotKey, Long userId, Duration ttl) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plus(ttl);

        return slotLockRepository.findBySlotKeyForUpdate(slotKey)
                .filter(lock -> Objects.equals(lock.getUser() != null ? lock.getUser().getId() : null, userId))
                .map(lock -> {
                    lock.setExpiresAt(expiresAt);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean releaseLock(String slotKey, Long userId) {
        return slotLockRepository.deleteBySlotKeyAndUserId(slotKey, userId) > 0;
    }

    @Transactional
    public int cleanExpiredLocks() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return slotLockRepository.deleteExpired(now);
    }

    @Transactional(readOnly = true)
    public Optional<SlotLock> findLock(String slotKey) {
        return slotLockRepository.findById(slotKey);
    }

    private User getUserReference(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }
}

