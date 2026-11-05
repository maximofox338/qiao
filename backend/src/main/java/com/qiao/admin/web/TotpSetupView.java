package com.qiao.admin.web;

/**
 * A new authenticator secret, shown once: {@code otpauthUri} is rendered as a QR code by the back-office (locally).
 */
public record TotpSetupView(String secret, String otpauthUri, String issuer, String account) {
}
