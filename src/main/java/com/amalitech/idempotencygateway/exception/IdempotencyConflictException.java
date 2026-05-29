package com.amalitech.idempotencygateway.exception;

/**
 * Thrown when an idempotency key is reused with a different request body.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
