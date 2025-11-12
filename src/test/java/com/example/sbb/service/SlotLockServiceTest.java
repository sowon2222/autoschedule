package com.example.sbb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.sbb.domain.SlotLock;
import com.example.sbb.domain.User;
import com.example.sbb.repository.SlotLockRepository;
import com.example.sbb.repository.UserRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlotLockServiceTest {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final OffsetDateTime FIXED_NOW = OffsetDateTime.of(2024, 11, 12, 9, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private SlotLockRepository slotLockRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SlotLockService slotLockService;

    private User owner;
    private User otherUser;

    @BeforeEach
    void setUp() {
        owner = buildUser(1L, "owner@example.com");
        otherUser = buildUser(2L, "other@example.com");
        given(userRepository.findById(owner.getId())).willReturn(Optional.of(owner));
        given(userRepository.findById(otherUser.getId())).willReturn(Optional.of(otherUser));
        given(slotLockRepository.save(any(SlotLock.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void tryLock_createsNewLock_whenNoExisting() {
        given(slotLockRepository.findBySlotKeyForUpdate("slot-1")).willReturn(Optional.empty());

        boolean acquired = slotLockService.tryLock("slot-1", owner.getId(), DEFAULT_TTL);

        assertThat(acquired).isTrue();
        verify(slotLockRepository).save(any(SlotLock.class));
    }

    @Test
    void tryLock_extendsLock_whenOwnerReentersBeforeExpiry() {
        SlotLock existing = buildLock("slot-1", owner, FIXED_NOW.plusMinutes(1));
        given(slotLockRepository.findBySlotKeyForUpdate("slot-1")).willReturn(Optional.of(existing));

        boolean acquired = slotLockService.tryLock("slot-1", owner.getId(), DEFAULT_TTL);

        assertThat(acquired).isTrue();
        assertThat(existing.getUser()).isEqualTo(owner);
        assertThat(existing.getExpiresAt()).isAfter(FIXED_NOW);
    }

    @Test
    void tryLock_reassignsLock_whenExpired() {
        SlotLock expired = buildLock("slot-1", owner, FIXED_NOW.minusMinutes(1));
        given(slotLockRepository.findBySlotKeyForUpdate("slot-1")).willReturn(Optional.of(expired));

        boolean acquired = slotLockService.tryLock("slot-1", otherUser.getId(), DEFAULT_TTL);

        assertThat(acquired).isTrue();
        assertThat(expired.getUser()).isEqualTo(otherUser);
        assertThat(expired.getExpiresAt()).isAfter(FIXED_NOW);
    }

    @Test
    void tryLock_fails_whenLockedByAnotherUser() {
        SlotLock held = buildLock("slot-1", owner, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        given(slotLockRepository.findBySlotKeyForUpdate("slot-1")).willReturn(Optional.of(held));

        boolean acquired = slotLockService.tryLock("slot-1", otherUser.getId(), DEFAULT_TTL);

        assertThat(acquired).isFalse();
        assertThat(held.getUser()).isEqualTo(owner);
    }

    @Test
    void tryLock_succeeds_forSystemLock_whenNoOwner() {
        given(slotLockRepository.findBySlotKeyForUpdate("system-slot")).willReturn(Optional.empty());

        boolean acquired = slotLockService.tryLock("system-slot", null, DEFAULT_TTL);

        assertThat(acquired).isTrue();
        verify(slotLockRepository).save(any(SlotLock.class));
    }

    @Test
    void releaseLock_deletes_whenOwnerMatches() {
        given(slotLockRepository.deleteBySlotKeyAndUserId("slot-1", owner.getId())).willReturn(1);

        boolean released = slotLockService.releaseLock("slot-1", owner.getId());

        assertThat(released).isTrue();
        verify(slotLockRepository).deleteBySlotKeyAndUserId("slot-1", owner.getId());
    }

    @Test
    void releaseLock_noop_whenOwnerDiffers() {
        given(slotLockRepository.deleteBySlotKeyAndUserId("slot-1", otherUser.getId())).willReturn(0);

        boolean released = slotLockService.releaseLock("slot-1", otherUser.getId());

        assertThat(released).isFalse();
        verify(slotLockRepository).deleteBySlotKeyAndUserId("slot-1", otherUser.getId());
    }

    @Test
    void releaseLock_handlesNullOwner() {
        given(slotLockRepository.deleteBySlotKeyAndUserId("system-slot", null)).willReturn(1);

        boolean released = slotLockService.releaseLock("system-slot", null);

        assertThat(released).isTrue();
        verify(slotLockRepository).deleteBySlotKeyAndUserId("system-slot", null);
    }

    @Test
    void cleanExpiredLocks_deletesUsingRepository() {
        given(slotLockRepository.deleteExpired(any(OffsetDateTime.class))).willReturn(3);

        int deleted = slotLockService.cleanExpiredLocks();

        assertThat(deleted).isEqualTo(3);
        verify(slotLockRepository).deleteExpired(any(OffsetDateTime.class));
    }

    @Test
    void renewLock_updatesExpiry_whenOwnerMatches() {
        SlotLock lock = buildLock("slot-1", owner, FIXED_NOW.plusMinutes(1));
        given(slotLockRepository.findBySlotKeyForUpdate("slot-1")).willReturn(Optional.of(lock));

        boolean renewed = slotLockService.renewLock("slot-1", owner.getId(), DEFAULT_TTL);

        assertThat(renewed).isTrue();
        assertThat(lock.getExpiresAt()).isAfter(FIXED_NOW);
    }

    @Test
    void renewLock_fails_whenOwnerDiffers() {
        SlotLock lock = buildLock("slot-1", owner, FIXED_NOW.plusMinutes(1));
        given(slotLockRepository.findBySlotKeyForUpdate("slot-1")).willReturn(Optional.of(lock));

        boolean renewed = slotLockService.renewLock("slot-1", otherUser.getId(), DEFAULT_TTL);

        assertThat(renewed).isFalse();
        assertThat(lock.getUser()).isEqualTo(owner);
    }

    private User buildUser(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName(email);
        user.setPassword("password");
        user.setCreatedAt(FIXED_NOW.minusDays(1));
        return user;
    }

    private SlotLock buildLock(String slotKey, User owner, OffsetDateTime expiresAt) {
        SlotLock lock = new SlotLock();
        lock.setSlotKey(slotKey);
        lock.setUser(owner);
        lock.setExpiresAt(expiresAt);
        return lock;
    }
}
