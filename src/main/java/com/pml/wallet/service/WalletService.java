package com.pml.wallet.service;

import com.pml.wallet.domain.Wallet;
import com.pml.wallet.exception.NotFoundException;
import com.pml.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WalletService {
    private final WalletRepository wallets;

    public WalletService(WalletRepository wallets) {
        this.wallets = wallets;
    }

    @Transactional
    public Wallet getOrCreate(String userId, long initialBalancePaise) {
        wallets.insertIfAbsent(UUID.randomUUID(), userId, initialBalancePaise);
        return wallets.findByUserId(userId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Wallet get(UUID id) {
        return wallets.findById(id).orElseThrow(() -> new NotFoundException("wallet not found"));
    }
}
