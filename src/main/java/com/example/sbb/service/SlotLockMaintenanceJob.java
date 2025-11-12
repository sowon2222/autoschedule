package com.example.sbb.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlotLockMaintenanceJob {

    private static final String JOB_SLOT_KEY = "job:slot-lock:cleanup";
    private static final Duration JOB_TTL = Duration.ofMinutes(1);

    private final SlotLockService slotLockService;

    @Scheduled(fixedDelayString = "${slot-lock.cleanup-interval-ms:60000}")
    public void cleanExpiredLocksSafely() {
        if (!slotLockService.tryLock(JOB_SLOT_KEY, null, JOB_TTL)) {
            return;
        }
        try {
            int deleted = slotLockService.cleanExpiredLocks();
            log.debug("Slot lock cleanup removed {} entries", deleted);
        } finally {
            slotLockService.releaseLock(JOB_SLOT_KEY, null);
        }
    }
}

