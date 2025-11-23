package com.example.sbb.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 슬롯 락 유지 관리 작업 클래스
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotLockMaintenanceJob {

    // 슬롯 락 유지 관리 작업 슬롯 키
    private static final String JOB_SLOT_KEY = "job:slot-lock:cleanup";
    // 슬롯 락 유지 관리 작업 슬롯 TTL
    private static final Duration JOB_TTL = Duration.ofMinutes(1);

    // 슬롯 락 서비스
    private final SlotLockService slotLockService;

    // 슬롯 락 유지 관리 작업 슬롯 키 조회
    @Scheduled(fixedDelayString = "${slot-lock.cleanup-interval-ms:60000}")
    public void cleanExpiredLocksSafely() {
        // 슬롯 락 유지 관리 작업 슬롯 키 시도
        if (!slotLockService.tryLock(JOB_SLOT_KEY, null, JOB_TTL)) {
            return;
        }
        // 슬롯 락 유지 관리 작업 슬롯 키 시도
        try {
            // 슬롯 락 유지 관리 작업 슬롯 키 삭제
            int deleted = slotLockService.cleanExpiredLocks();
            log.debug("Slot lock cleanup removed {} entries", deleted);
        } finally {
            // 슬롯 락 유지 관리 작업 슬롯 키 해제
            slotLockService.releaseLock(JOB_SLOT_KEY, null);
        }
    }
}

