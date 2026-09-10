package com.pml.wallet.exception;

import com.pml.wallet.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException exception) {
        return error(HttpStatus.BAD_REQUEST, "bad_request", exception.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "not_found", exception.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException exception) {
        return error(HttpStatus.FORBIDDEN, "forbidden", exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ErrorResponse> handleConflict(ConflictException exception) {
        return error(HttpStatus.CONFLICT, "idempotency_conflict", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        return error(HttpStatus.BAD_REQUEST, "validation_error", "invalid request");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleMissingResource(NoResourceFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "not_found", "not found");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected request failure", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "unexpected server error");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        ErrorResponse response = new ErrorResponse(code, message, MDC.get("correlation_id"));
        return ResponseEntity.status(status).body(response);
    }
}
