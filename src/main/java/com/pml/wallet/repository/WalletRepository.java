package com.pml.wallet.repository;

import com.pml.wallet.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByUserId(String userId);

    @Query(value = "SELECT user_id FROM wallets WHERE id = :id", nativeQuery = true)
    Optional<String> findUserIdById(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            INSERT INTO wallets (id, user_id, balance_paise, created_at)
            VALUES (:id, :userId, :initialBalance, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") String userId,
            @Param("initialBalance") long initialBalance);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT wallet FROM Wallet wallet WHERE wallet.id = :id")
    Optional<Wallet> lockById(@Param("id") UUID id);
}
