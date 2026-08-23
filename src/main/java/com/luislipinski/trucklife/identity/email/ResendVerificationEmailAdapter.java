package com.luislipinski.trucklife.identity.email;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

final class ResendVerificationEmailAdapter implements VerificationEmailDeliveryPort {

    private final RestClient restClient;
    private final ResendEmailProperties properties;

    ResendVerificationEmailAdapter(RestClient restClient, ResendEmailProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public void sendVerificationEmail(
            String recipient,
            String displayName,
            String rawToken,
            Instant expiresAt
    ) {
        String verificationUrl = actionUrl("verify-email", rawToken);
        send(
                recipient,
                "Confirme seu e-mail — Truck Life Simulator",
                emailHtml(
                        displayName,
                        "Confirme seu e-mail",
                        "Use o botão abaixo para ativar sua conta no Truck Life Simulator.",
                        "Confirmar e-mail",
                        verificationUrl,
                        expiresAt
                ),
                "email-verification"
        );
    }

    @Override
    public void sendPasswordResetEmail(
            String recipient,
            String displayName,
            String rawToken,
            Instant expiresAt
    ) {
        String resetUrl = actionUrl("reset-password", rawToken);
        send(
                recipient,
                "Redefina sua senha — Truck Life Simulator",
                emailHtml(
                        displayName,
                        "Redefina sua senha",
                        "Recebemos uma solicitação para criar uma nova senha para sua conta.",
                        "Redefinir senha",
                        resetUrl,
                        expiresAt
                ),
                "password-reset"
        );
    }

    private void send(String intendedRecipient, String subject, String html, String category) {
        String recipient = properties.testMode()
                ? properties.testRecipient()
                : intendedRecipient;

        restClient.post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "from", properties.from(),
                        "to", List.of(recipient),
                        "subject", subject,
                        "html", html,
                        "tags", List.of(Map.of("name", "category", "value", category))
                ))
                .retrieve()
                .toBodilessEntity();
    }

    private String actionUrl(String route, String rawToken) {
        return properties.frontendBaseUrl()
                + "/#/"
                + route
                + "?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private String emailHtml(
            String displayName,
            String title,
            String message,
            String buttonLabel,
            String actionUrl,
            Instant expiresAt
    ) {
        String safeName = HtmlUtils.htmlEscape(displayName == null ? "motorista" : displayName);
        String safeTitle = HtmlUtils.htmlEscape(title);
        String safeMessage = HtmlUtils.htmlEscape(message);
        String safeButton = HtmlUtils.htmlEscape(buttonLabel);
        String safeUrl = HtmlUtils.htmlEscape(actionUrl);
        String safeExpiresAt = HtmlUtils.htmlEscape(expiresAt.toString());

        return """
                <!doctype html>
                <html lang="pt-BR">
                  <body style="font-family:Arial,sans-serif;background:#f8fafc;color:#0f172a;padding:24px">
                    <div style="max-width:560px;margin:auto;background:#ffffff;border-radius:16px;padding:32px">
                      <p style="margin-top:0;color:#64748b">Truck Life Simulator</p>
                      <h1 style="font-size:24px">%s</h1>
                      <p>Olá, %s.</p>
                      <p>%s</p>
                      <p style="margin:28px 0">
                        <a href="%s" style="background:#0284c7;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:10px;font-weight:700">%s</a>
                      </p>
                      <p style="font-size:13px;color:#64748b">Este link expira em %s e pode ser usado apenas uma vez.</p>
                      <p style="font-size:13px;color:#64748b">Se você não solicitou esta ação, ignore esta mensagem.</p>
                    </div>
                  </body>
                </html>
                """.formatted(safeTitle, safeName, safeMessage, safeUrl, safeButton, safeExpiresAt);
    }
}
