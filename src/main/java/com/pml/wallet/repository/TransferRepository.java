package com.pml.wallet.repository;

import com.pml.wallet.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query(value = """
            INSERT INTO transfers
                (id, idempotency_key, from_wallet_id, to_wallet_id, amount_paise, status, created_at)
            VALUES (:id, :key, :from, :to, :amount, 'DECLINED', CURRENT_TIMESTAMP)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("key") String key,
            @Param("from") UUID from,
            @Param("to") UUID to,
            @Param("amount") long amount);
}
