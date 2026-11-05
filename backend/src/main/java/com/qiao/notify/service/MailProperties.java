package com.qiao.notify.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code MAIL_MODE=log|smtp} and {@code SMTP_FROM}. SMTP connection settings live under {@code spring.mail}.
 */
@ConfigurationProperties("app.mail")
public record MailProperties(@DefaultValue("log") Mode mode, @DefaultValue("no-reply@qiao.local") String from) {

    public enum Mode { LOG, SMTP }
}
