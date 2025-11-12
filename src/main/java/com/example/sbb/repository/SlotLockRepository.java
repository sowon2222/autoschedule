package com.example.sbb.repository;

import com.example.sbb.domain.SlotLock;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotLockRepository extends JpaRepository<SlotLock, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sl from SlotLock sl where sl.slotKey = :slotKey")
    Optional<SlotLock> findBySlotKeyForUpdate(@Param("slotKey") String slotKey);

    @Modifying
    @Query("""
        delete from SlotLock sl
        where sl.slotKey = :slotKey
          and (
              (:userId is null and sl.user is null)
              or (:userId is not null and sl.user.id = :userId)
          )
        """)
    int deleteBySlotKeyAndUserId(@Param("slotKey") String slotKey, @Param("userId") Long userId);

    @Modifying
    @Query("delete from SlotLock sl where sl.expiresAt <= :now")
    int deleteExpired(@Param("now") OffsetDateTime now);
}

