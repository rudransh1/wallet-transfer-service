package com.pml.wallet.dto;

import jakarta.validation.constraints.Min;

public record CreateWalletRequest(@Min(0) Long initialBalancePaise) {
}
