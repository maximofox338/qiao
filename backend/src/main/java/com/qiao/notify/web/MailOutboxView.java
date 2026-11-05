package com.qiao.notify.web;

import com.qiao.notify.domain.MailOutbox;
import com.qiao.notify.domain.MailStatus;

import java.time.Instant;

public record MailOutboxView(Long id, String toEmail, String subject, String body, MailStatus status, Instant createdAt) {

    public static MailOutboxView from(MailOutbox mail) {
        return new MailOutboxView(mail.getId(), mail.getToEmail(), mail.getSubject(), mail.getBody(), mail.getStatus(),
                mail.getCreatedAt());
    }
}
