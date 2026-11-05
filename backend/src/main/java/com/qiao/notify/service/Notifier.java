package com.qiao.notify.service;

import com.qiao.common.util.Locales;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Renders a {@link MailTemplate} in the recipient's locale and hands it to {@link MailService}.
 */
@Service
public class Notifier {

    private final MessageSource messageSource;
    private final MailService mailService;

    public Notifier(MessageSource messageSource, MailService mailService) {
        this.messageSource = messageSource;
        this.mailService = mailService;
    }

    public void send(String email, String locale, MailTemplate template, Object... args) {
        Locale target = Locale.forLanguageTag(Locales.normalize(locale, Locales.EN));
        String subject = messageSource.getMessage("mail." + template.key() + ".subject", args, target);
        String body = messageSource.getMessage("mail." + template.key() + ".body", args, target);
        mailService.send(email, subject, body, template.sensitive());
    }
}
