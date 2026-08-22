package com.luislipinski.trucklife.identity.application;

import com.luislipinski.trucklife.shared.error.ApiProblemException;
import org.springframework.http.HttpStatus;

public class RefreshTokenException extends ApiProblemException {

    private RefreshTokenException(String code, String title, String detail) {
        super(HttpStatus.UNAUTHORIZED, code, title, detail);
    }

    public static RefreshTokenException required() {
        return new RefreshTokenException(
                "REFRESH_TOKEN_REQUIRED",
                "Refresh token required",
                "A valid refresh token is required"
        );
    }

    public static RefreshTokenException invalid() {
        return new RefreshTokenException(
                "REFRESH_TOKEN_INVALID",
                "Invalid refresh token",
                "The refresh token is invalid or no longer active"
        );
    }

    public static RefreshTokenException expired() {
        return new RefreshTokenException(
                "REFRESH_TOKEN_EXPIRED",
                "Refresh token expired",
                "The refresh token has expired"
        );
    }

    public static RefreshTokenException reused() {
        return new RefreshTokenException(
                "REFRESH_TOKEN_REUSED",
                "Refresh token reuse detected",
                "The session was revoked because refresh token reuse was detected"
        );
    }
}
