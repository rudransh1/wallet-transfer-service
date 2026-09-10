package com.pml.wallet.dto;

public record ErrorResponse(String code, String message, String correlationId) {
}
