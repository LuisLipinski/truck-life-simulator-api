package com.luislipinski.trucklife.identity.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("identity.email")
public record ResendEmailProperties(
        String provider,
        String resendApiKey,
        String from,
        String frontendBaseUrl,
        boolean testMode,
        String testRecipient
) {
    public ResendEmailProperties {
        provider = blankToDefault(provider, "discard");
        from = blankToDefault(from, "Truck Life Simulator <onboarding@resend.dev>");
        frontendBaseUrl = stripTrailingSlash(blankToDefault(
                frontendBaseUrl,
                "https://luislipinski.github.io/truck-life-simulator"
        ));
        testRecipient = blankToDefault(testRecipient, "delivered@resend.dev");
    }

    public String requiredApiKey() {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new IllegalStateException(
                    "RESEND_API_KEY is required when AUTH_EMAIL_PROVIDER=resend"
            );
        }
        return resendApiKey.strip();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.strip();
    }

    private static String stripTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
