package com.amalitech.idempotencygateway.model;

import java.time.Instant;

public class PaymentResponse {

    private String status;
    private String message;
    private String transactionId;
    private Instant processedAt;

    public PaymentResponse() {}

    public PaymentResponse(String status, String message, String transactionId, Instant processedAt) {
        this.status = status;
        this.message = message;
        this.transactionId = transactionId;
        this.processedAt = processedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
