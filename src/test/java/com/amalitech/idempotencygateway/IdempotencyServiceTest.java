package com.amalitech.idempotencygateway;

import com.amalitech.idempotencygateway.exception.IdempotencyConflictException;
import com.amalitech.idempotencygateway.model.PaymentRequest;
import com.amalitech.idempotencygateway.service.IdempotencyService;
import com.amalitech.idempotencygateway.service.IdempotencyService.PaymentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyServiceTest {

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService();
    }

    @Test
    void firstRequest_shouldReturnSuccessAndNotBeCacheHit() {
        PaymentRequest request = new PaymentRequest(100.0, "GHS");
        PaymentResult result = service.processPayment("key-001", request);

        assertNotNull(result.response());
        assertEquals(201, result.httpStatus());
        assertFalse(result.cacheHit());
        assertEquals("SUCCESS", result.response().getStatus());
        assertEquals("Charged 100 GHS", result.response().getMessage());
    }

    @Test
    void duplicateRequest_shouldReturnCachedResponseWithCacheHit() {
        PaymentRequest request = new PaymentRequest(200.0, "USD");
        PaymentResult first = service.processPayment("key-002", request);
        PaymentResult second = service.processPayment("key-002", request);

        assertTrue(second.cacheHit());
        assertEquals(first.response().getTransactionId(), second.response().getTransactionId());
        assertEquals(first.response().getMessage(), second.response().getMessage());
    }

    @Test
    void sameKeyDifferentBody_shouldThrowConflictException() {
        PaymentRequest original = new PaymentRequest(100.0, "GHS");
        PaymentRequest different = new PaymentRequest(500.0, "GHS");

        service.processPayment("key-003", original);

        assertThrows(IdempotencyConflictException.class, () ->
                service.processPayment("key-003", different));
    }

    @Test
    void differentKeys_shouldBeProcessedIndependently() {
        PaymentRequest request = new PaymentRequest(50.0, "EUR");
        PaymentResult r1 = service.processPayment("key-004", request);
        PaymentResult r2 = service.processPayment("key-005", request);

        assertFalse(r1.cacheHit());
        assertFalse(r2.cacheHit());
        assertNotEquals(r1.response().getTransactionId(), r2.response().getTransactionId());
    }

    @Test
    void cacheSize_shouldIncrementWithNewKeys() {
        int before = service.getCacheSize();
        service.processPayment("key-size-1", new PaymentRequest(10.0, "GHS"));
        service.processPayment("key-size-2", new PaymentRequest(20.0, "GHS"));
        assertEquals(before + 2, service.getCacheSize());
    }
}
