package com.amalitech.idempotencygateway.model;

/**
 * Represents a cached idempotency entry stored in memory.
 * Tracks the original request body hash, the saved response,
 * the HTTP status code, and whether the request is still in-flight.
 */
public class CachedEntry {

    public enum State { IN_FLIGHT, COMPLETED }

    private final String requestBodyHash;
    private PaymentResponse response;
    private int httpStatus;
    private State state;

    public CachedEntry(String requestBodyHash) {
        this.requestBodyHash = requestBodyHash;
        this.state = State.IN_FLIGHT;
    }

    public String getRequestBodyHash() {
        return requestBodyHash;
    }

    public PaymentResponse getResponse() {
        return response;
    }

    public void setResponse(PaymentResponse response) {
        this.response = response;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public boolean isInFlight() {
        return state == State.IN_FLIGHT;
    }

    public boolean isCompleted() {
        return state == State.COMPLETED;
    }
}
