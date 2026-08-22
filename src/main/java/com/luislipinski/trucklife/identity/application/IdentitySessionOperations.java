package com.luislipinski.trucklife.identity.application;

public interface IdentitySessionOperations {

    IssuedSession login(
            String email,
            String rawPassword,
            String clientAddress,
            String userAgent
    );

    IssuedSession refresh(
            String rawRefreshToken,
            String clientAddress,
            String userAgent
    );

    void logout(String rawRefreshToken);
}
