package com.amalitech.idempotencygateway.service;

import com.amalitech.idempotencygateway.exception.IdempotencyConflictException;
import com.amalitech.idempotencygateway.model.CachedEntry;
import com.amalitech.idempotencygateway.model.PaymentRequest;
import com.amalitech.idempotencygateway.model.PaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core idempotency service.
 *
 * Uses a ConcurrentHashMap as an in-memory store.
 * Handles:
 *   - First-time requests: process and cache the response
 *   - Duplicate requests (same key + same body): return cached response
 *   - Conflict requests (same key + different body): throw 422
 *   - In-flight race conditions: block duplicate until first completes
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    // In-memory store: idempotency key -> cached entry
    private final ConcurrentHashMap<String, CachedEntry> store = new ConcurrentHashMap<>();

    /**
     * Process a payment with idempotency guarantees.
     *
     * @param idempotencyKey unique key from client
     * @param request        payment request body
     * @return result containing the response and whether it was a cache hit
     */
    public PaymentResult processPayment(String idempotencyKey, PaymentRequest request) {
        String bodyHash = hashRequest(request);

        // Atomically insert a new IN_FLIGHT entry only if key is absent
        CachedEntry newEntry = new CachedEntry(bodyHash);
        CachedEntry existing = store.putIfAbsent(idempotencyKey, newEntry);

        if (existing == null) {
            // First time seeing this key — process the payment
            return processAndCache(idempotencyKey, request, newEntry);
        }

        // Key already exists — validate body hash matches
        if (!existing.getRequestBodyHash().equals(bodyHash)) {
            throw new IdempotencyConflictException(
                    "Idempotency key already used for a different request body.");
        }

        // Same key, same body — handle in-flight or completed
        return waitForCompletionOrReturnCached(idempotencyKey, existing);
    }

    /**
     * Simulate payment processing (2-second delay) and cache the result.
     */
    private PaymentResult processAndCache(String key, PaymentRequest request, CachedEntry entry) {
        log.info("Processing new payment for key: {}", key);
        try {
            // Simulate processing delay
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        PaymentResponse response = new PaymentResponse(
                "SUCCESS",
                String.format("Charged %.0f %s", request.getAmount(), request.getCurrency()),
                UUID.randomUUID().toString(),
                Instant.now()
        );

        synchronized (entry) {
            entry.setResponse(response);
            entry.setHttpStatus(201);
            entry.setState(CachedEntry.State.COMPLETED);
            entry.notifyAll(); // wake up any waiting duplicate requests
        }

        log.info("Payment processed and cached for key: {}", key);
        return new PaymentResult(response, 201, false);
    }

    /**
     * If a duplicate request arrives while the first is still in-flight,
     * block until the first completes, then return its result.
     * If already completed, return immediately.
     */
    private PaymentResult waitForCompletionOrReturnCached(String key, CachedEntry entry) {
        synchronized (entry) {
            while (entry.isInFlight()) {
                log.info("Duplicate request waiting for in-flight processing to complete. Key: {}", key);
                try {
                    entry.wait(5000); // wait up to 5 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            log.info("Returning cached response for key: {}", key);
            return new PaymentResult(entry.getResponse(), entry.getHttpStatus(), true);
        }
    }

    /**
     * Hash the request body using SHA-256 for consistent comparison.
     */
    private String hashRequest(PaymentRequest request) {
        try {
            String raw = request.getAmount() + ":" + request.getCurrency();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Returns the number of cached entries (useful for health/stats endpoint).
     */
    public int getCacheSize() {
        return store.size();
    }

    /**
     * Wrapper result returned from processPayment.
     */
    public record PaymentResult(PaymentResponse response, int httpStatus, boolean cacheHit) {}
}
