package com.example.sbb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

// 슬롯 락 도메인 클래스
@Entity
@Table(name = "slot_lock")
@Getter
@Setter
public class SlotLock {

    @Id
    @Column(name = "slot_key")
    private String slotKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

}


