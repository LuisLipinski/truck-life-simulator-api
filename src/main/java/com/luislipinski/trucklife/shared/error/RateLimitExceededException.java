package com.luislipinski.trucklife.shared.error;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ApiProblemException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT_EXCEEDED",
                "Rate limit exceeded",
                "Too many requests; retry after the indicated interval"
        );
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
