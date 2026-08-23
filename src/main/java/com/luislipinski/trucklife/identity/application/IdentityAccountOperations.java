package com.luislipinski.trucklife.identity.application;

public interface IdentityAccountOperations {

    void register(String email, String displayName, String rawPassword, String clientAddress);

    void verifyEmail(String rawToken, String clientAddress);

    void resendVerification(String email, String clientAddress);

    default void forgotPassword(String email, String clientAddress) {
        throw new UnsupportedOperationException("Password recovery is not implemented");
    }

    default void resetPassword(String rawToken, String newRawPassword, String clientAddress) {
        throw new UnsupportedOperationException("Password recovery is not implemented");
    }
}
