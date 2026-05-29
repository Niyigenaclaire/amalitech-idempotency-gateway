package com.amalitech.idempotencygateway.controller;

import com.amalitech.idempotencygateway.service.IdempotencyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Developer's Choice Feature: Gateway Stats Endpoint.
 *
 * Exposes a GET /gateway/stats endpoint that returns:
 * - Total number of cached idempotency keys
 * - Server uptime timestamp
 *
 * This is useful for real-world Fintech operations teams to monitor
 * cache growth and detect potential memory pressure without needing
 * to access internal infrastructure directly.
 */
@RestController
@RequestMapping("/gateway")
public class StatsController {

    private final IdempotencyService idempotencyService;
    private final Instant startTime = Instant.now();

    public StatsController(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
                "cachedKeys", idempotencyService.getCacheSize(),
                "serverStartedAt", startTime.toString(),
                "currentTime", Instant.now().toString(),
                "status", "UP"
        ));
    }
}
