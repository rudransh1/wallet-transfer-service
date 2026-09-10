package com.pml.wallet.dto;

import com.pml.wallet.domain.Transfer;
import com.pml.wallet.enums.TransferStatus;

import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID from,
        UUID to,
        long amountPaise,
        TransferStatus status) {
    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getFromWalletId(),
                transfer.getToWalletId(),
                transfer.getAmountPaise(),
                transfer.getStatus());
    }
}
