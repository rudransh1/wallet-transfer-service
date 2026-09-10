package com.pml.wallet.controller;

import com.pml.wallet.domain.Transfer;
import com.pml.wallet.dto.TransferRequest;
import com.pml.wallet.dto.TransferResponse;
import com.pml.wallet.enums.TransferStatus;
import com.pml.wallet.service.TransferService;
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
@RequestMapping("/transfers")
public class TransferController {
    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody TransferRequest request) {
        Transfer transfer = transferService.transfer(Auth.user(authorization), request);
        HttpStatus status = transfer.getStatus() == TransferStatus.DECLINED
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(TransferResponse.from(transfer));
    }

    @GetMapping("/{id}")
    public TransferResponse get(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID id) {
        return TransferResponse.from(transferService.get(id, Auth.user(authorization)));
    }
}
