package com.example.wallet.transfer.demo.exception;

public class IdempotencyConflictException extends RuntimeException {

    private final String idempotencyKey;

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key '%s' already used with a different request body".formatted(idempotencyKey));
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
}
