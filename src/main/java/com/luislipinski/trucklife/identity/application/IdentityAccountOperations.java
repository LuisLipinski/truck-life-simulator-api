package com.luislipinski.trucklife.identity.application;

public interface IdentityAccountOperations {

    void register(
            String email,
            String displayName,
            String rawPassword,
            String clientAddress
    );

    void verifyEmail(String rawToken, String clientAddress);

    void resendVerification(String email, String clientAddress);
}
