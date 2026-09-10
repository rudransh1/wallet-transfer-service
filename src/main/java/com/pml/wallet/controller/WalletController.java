package com.pml.wallet.controller;

import com.pml.wallet.domain.Wallet;
import com.pml.wallet.dto.CreateWalletRequest;
import com.pml.wallet.dto.WalletResponse;
import com.pml.wallet.exception.ForbiddenException;
import com.pml.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/wallets")
public class WalletController {
    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody(required = false) CreateWalletRequest request) {
        long initial = request == null || request.initialBalancePaise() == null
                ? 100_000L
                : request.initialBalancePaise();
        Wallet wallet = walletService.getOrCreate(Auth.user(authorization), initial);
        return ResponseEntity.status(HttpStatus.CREATED).body(WalletResponse.from(wallet));
    }

    @GetMapping("/{id}")
    public WalletResponse get(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID id) {
        Wallet wallet = walletService.get(id);
        if (!wallet.getUserId().equals(Auth.user(authorization))) {
            throw new ForbiddenException("caller does not own wallet");
        }
        return WalletResponse.from(wallet);
    }
}
