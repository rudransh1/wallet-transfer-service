package com.pml.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "balance_paise", nullable = false)
    private long balancePaise;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Wallet() {
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public long getBalancePaise() {
        return balancePaise;
    }

    public void debit(long amountPaise) {
        if (amountPaise <= 0 || balancePaise < amountPaise) {
            throw new IllegalArgumentException("invalid debit");
        }
        balancePaise -= amountPaise;
    }

    public void credit(long amountPaise) {
        if (amountPaise <= 0 || balancePaise > Long.MAX_VALUE - amountPaise) {
            throw new IllegalArgumentException("invalid credit");
        }
        balancePaise += amountPaise;
    }
}
