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

// 슬롯 락 서비스 클래스
@Service
@RequiredArgsConstructor
public class SlotLockService {

    private final SlotLockRepository slotLockRepository;
    private final UserRepository userRepository;

    // 슬롯 락 시도
    @Transactional
    public boolean tryLock(String slotKey, Long userId, Duration ttl) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plus(ttl);
        User userRef = userId != null ? getUserReference(userId) : null;

        // 슬롯 락 조회
        Optional<SlotLock> existingLock = slotLockRepository.findBySlotKeyForUpdate(slotKey);
        // 슬롯 락이 없으면 새로 생성
        if (existingLock.isEmpty()) {
            SlotLock newLock = new SlotLock();
            newLock.setSlotKey(slotKey);
            newLock.setUser(userRef); // 사용자 참조 설정
            newLock.setExpiresAt(expiresAt); // 만료 시간 설정
            slotLockRepository.save(newLock); // 슬롯 락 저장
            return true;
        }
        // 슬롯 락이 있으면 소유자 확인
        SlotLock lock = existingLock.get(); // 슬롯 락 가져오기
        Long currentOwnerId = lock.getUser() != null ? lock.getUser().getId() : null;

        // 내 락이면 TTL 갱신신
        if (Objects.equals(currentOwnerId, userId)) {
            lock.setExpiresAt(expiresAt);
            return true;
        }

        // 슬롯 락이 만료되었으면 소유자 변경(내 락이 아니면 소유자 변경)
        if (!lock.getExpiresAt().isAfter(now)) {
            lock.setUser(userRef); // 사용자 참조 설정
            lock.setExpiresAt(expiresAt); // 만료 시간 설정
            slotLockRepository.save(lock); // 슬롯 락 저장
            return true;
        }
        // 슬롯 락이 있고 소유자가 다르면 실패
        return false;
    }

    // 슬롯 락 갱신
    @Transactional
    public boolean renewLock(String slotKey, Long userId, Duration ttl) {
        // 현재 시간 가져오기
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plus(ttl);
        // 슬롯 락 조회
        return slotLockRepository.findBySlotKeyForUpdate(slotKey)
                .filter(lock -> Objects.equals(lock.getUser() != null ? lock.getUser().getId() : null, userId))
                .map(lock -> {
                    lock.setExpiresAt(expiresAt);
                    return true;
                })
                .orElse(false);
    }

    // 슬롯 락 해제
    @Transactional
    public boolean releaseLock(String slotKey, Long userId) {
        return slotLockRepository.deleteBySlotKeyAndUserId(slotKey, userId) > 0;
    }

    // 만료된 슬롯 락 삭제
    @Transactional
    public int cleanExpiredLocks() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return slotLockRepository.deleteExpired(now);
    }

    // 슬롯 락 조회
    @Transactional(readOnly = true)
    public Optional<SlotLock> findLock(String slotKey) {
        return slotLockRepository.findById(slotKey);
    }

    // 사용자 참조 가져오기
    private User getUserReference(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }
}

