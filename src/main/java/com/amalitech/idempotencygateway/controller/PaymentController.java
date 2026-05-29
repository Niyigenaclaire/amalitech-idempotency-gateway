package com.amalitech.idempotencygateway.controller;

import com.amalitech.idempotencygateway.model.PaymentRequest;
import com.amalitech.idempotencygateway.service.IdempotencyService;
import com.amalitech.idempotencygateway.service.IdempotencyService.PaymentResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the payment processing endpoint.
 * Accepts POST /process-payment with an Idempotency-Key header.
 */
@RestController
@RequestMapping("/process-payment")
public class PaymentController {

    private final IdempotencyService idempotencyService;

    public PaymentController(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    /**
     * Process a payment request with idempotency guarantees.
     *
     * @param idempotencyKey unique key provided by the client
     * @param request        payment details (amount + currency)
     * @return payment response with X-Cache-Hit header on duplicates
     */
    @PostMapping
    public ResponseEntity<?> processPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Idempotency-Key header must not be blank"));
        }

        PaymentResult result = idempotencyService.processPayment(idempotencyKey, request);

        return ResponseEntity
                .status(result.httpStatus())
                .header("X-Cache-Hit", String.valueOf(result.cacheHit()))
                .body(result.response());
    }
}
