package com.qiao.notify.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.common.api.PageQuery;
import com.qiao.common.api.PageResult;
import com.qiao.common.util.Texts;
import com.qiao.notify.domain.MailOutbox;
import com.qiao.notify.domain.MailOutboxMapper;
import com.qiao.notify.domain.MailStatus;
import com.qiao.notify.web.MailOutboxView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends mail according to {@code MAIL_MODE}. {@code log} only writes {@code mail_outbox}; {@code smtp} delivers after
 * the surrounding transaction commits and records the outcome. Bodies of sensitive mails (OTP codes) are redacted in
 * the outbox when delivered by SMTP.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final String REDACTED = "[redacted]";

    private final MailOutboxMapper outboxMapper;
    private final MailProperties properties;
    private final JavaMailSender mailSender;

    public MailService(MailOutboxMapper outboxMapper, MailProperties properties, ObjectProvider<JavaMailSender> mailSender) {
        this.outboxMapper = outboxMapper;
        this.properties = properties;
        this.mailSender = mailSender.getIfAvailable();
        if (properties.mode() == MailProperties.Mode.SMTP && this.mailSender == null) {
            throw new IllegalStateException("MAIL_MODE=smtp requires SMTP_HOST");
        }
    }

    public void send(String to, String subject, String body, boolean sensitive) {
        if (properties.mode() == MailProperties.Mode.LOG) {
            store(to, subject, body, MailStatus.LOGGED);
            log.info("Mail logged to={} subject={}", to, subject);
            return;
        }
        Runnable delivery = () -> deliver(to, subject, body, sensitive);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delivery.run();
                }
            });
        } else {
            delivery.run();
        }
    }

    private void deliver(String to, String subject, String body, boolean sensitive) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        MailStatus status;
        try {
            mailSender.send(message);
            status = MailStatus.SENT;
        } catch (MailException e) {
            log.error("Mail delivery failed to={} subject={}", to, subject, e);
            status = MailStatus.FAILED;
        }
        store(to, subject, sensitive ? REDACTED : body, status);
    }

    public PageResult<MailOutboxView> search(String to, PageQuery pageQuery) {
        String email = Texts.normalizeEmail(Texts.trimToNull(to));
        LambdaQueryWrapper<MailOutbox> query = new LambdaQueryWrapper<MailOutbox>()
                .eq(email != null, MailOutbox::getToEmail, email)
                .orderByDesc(MailOutbox::getId);
        return PageResult.of(outboxMapper.selectPage(pageQuery.toPage(), query), MailOutboxView::from);
    }

    private void store(String to, String subject, String body, MailStatus status) {
        MailOutbox entry = new MailOutbox();
        entry.setToEmail(to);
        entry.setSubject(subject);
        entry.setBody(body);
        entry.setStatus(status);
        outboxMapper.insert(entry);
    }
}
