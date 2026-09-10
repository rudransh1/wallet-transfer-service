package com.pml.wallet.service;

import com.pml.wallet.domain.Transfer;
import com.pml.wallet.domain.Wallet;
import com.pml.wallet.dto.TransferRequest;
import com.pml.wallet.exception.BadRequestException;
import com.pml.wallet.exception.ConflictException;
import com.pml.wallet.exception.ForbiddenException;
import com.pml.wallet.exception.NotFoundException;
import com.pml.wallet.repository.TransferRepository;
import com.pml.wallet.repository.WalletRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransferService {
    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final WalletRepository wallets;
    private final TransferRepository transfers;
    private final Counter created;
    private final Counter declined;
    private final Counter replays;

    public TransferService(WalletRepository wallets, TransferRepository transfers, MeterRegistry metrics) {
        this.wallets = wallets;
        this.transfers = transfers;
        this.created = metrics.counter("wallet_transfer_created_events");
        this.declined = metrics.counter("wallet_transfers_declined_insufficient_funds_total");
        this.replays = metrics.counter("wallet_transfers_idempotent_replays_total");
    }

    @Transactional
    public Transfer transfer(String caller, TransferRequest request) {
        if (request.from().equals(request.to())) {
            throw new BadRequestException("from and to must be different");
        }
        String owner = wallets.findUserIdById(request.from())
                .orElseThrow(() -> new NotFoundException("source wallet not found"));
        if (!owner.equals(caller)) {
            throw new ForbiddenException("caller does not own source wallet");
        }
        if (!wallets.existsById(request.to())) {
            throw new NotFoundException("destination wallet not found");
        }

        UUID transferId = UUID.randomUUID();
        if (transfers.insertIfAbsent(
                transferId, request.idempotencyKey(), request.from(), request.to(), request.amountPaise()) == 0) {
            Transfer existing = transfers.findByIdempotencyKey(request.idempotencyKey()).orElseThrow();
            if (!sameRequest(existing, request)) {
                throw new ConflictException("idempotency key reused with a different body");
            }
            replays.increment();
            log.info("idempotent replay hit {}", StructuredArguments.kv("transfer_id", existing.getId()));
            return existing;
        }
        log.info("transfer created {}", StructuredArguments.kv("transfer_id", transferId));

        // lock lower UUID first so A→B and B→A cannot deadlock
        UUID firstId = request.from().compareTo(request.to()) < 0 ? request.from() : request.to();
        UUID secondId = firstId.equals(request.from()) ? request.to() : request.from();
        Wallet first = wallets.lockById(firstId).orElseThrow();
        Wallet second = wallets.lockById(secondId).orElseThrow();
        Wallet from = first.getId().equals(request.from()) ? first : second;
        Wallet to = first.getId().equals(request.to()) ? first : second;

        Transfer transfer = transfers.findById(transferId).orElseThrow();
        if (from.getBalancePaise() < request.amountPaise()) {
            declined.increment();
            log.info("transfer declined {}", StructuredArguments.kv("transfer_id", transferId),
                    StructuredArguments.kv("reason", "insufficient_funds"));
            return transfer;
        }

        from.debit(request.amountPaise());
        log.info("wallet debited {}", StructuredArguments.kv("transfer_id", transferId),
                StructuredArguments.kv("amount_paise", request.amountPaise()));
        to.credit(request.amountPaise());
        log.info("wallet credited {}", StructuredArguments.kv("transfer_id", transferId),
                StructuredArguments.kv("amount_paise", request.amountPaise()));
        transfer.complete();
        created.increment();
        log.info("transfer completed {}", StructuredArguments.kv("transfer_id", transferId));
        return transfer;
    }

    @Transactional(readOnly = true)
    public Transfer get(UUID id, String caller) {
        Transfer transfer = transfers.findById(id)
                .orElseThrow(() -> new NotFoundException("transfer not found"));
        String owner = wallets.findUserIdById(transfer.getFromWalletId())
                .orElseThrow(() -> new NotFoundException("source wallet not found"));
        if (!owner.equals(caller)) {
            throw new ForbiddenException("caller does not own source wallet");
        }
        return transfer;
    }

    private boolean sameRequest(Transfer transfer, TransferRequest request) {
        return transfer.getFromWalletId().equals(request.from())
                && transfer.getToWalletId().equals(request.to())
                && transfer.getAmountPaise() == request.amountPaise();
    }
}
