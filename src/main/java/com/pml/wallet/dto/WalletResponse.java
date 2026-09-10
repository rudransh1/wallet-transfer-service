package com.pml.wallet.dto;

import com.pml.wallet.domain.Wallet;

import java.util.UUID;

public record WalletResponse(UUID id, String userId, long balancePaise) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getUserId(), wallet.getBalancePaise());
    }
}
