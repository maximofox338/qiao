package com.qiao.contract.service;

import com.qiao.account.domain.AppUser;
import com.qiao.account.service.UserService;
import com.qiao.client.service.CompanyService;
import com.qiao.contract.domain.Contract;
import com.qiao.notify.service.MailTemplate;
import com.qiao.notify.service.Notifier;
import org.springframework.stereotype.Component;

/**
 * Mails the parties of a contract in their own locale.
 */
@Component
public class ContractNotifier {

    private final Notifier notifier;
    private final UserService userService;
    private final CompanyService companyService;
    private final ContractParties parties;

    public ContractNotifier(Notifier notifier, UserService userService, CompanyService companyService,
                            ContractParties parties) {
        this.notifier = notifier;
        this.userService = userService;
        this.companyService = companyService;
        this.parties = parties;
    }

    /**
     * Mails the engineer, or every member of a team contract.
     */
    public void toEngineer(Contract contract, MailTemplate template, Object... args) {
        parties.engineerUserIds(contract).forEach(userId -> send(userId, template, args));
    }

    public void toClient(Contract contract, MailTemplate template, Object... args) {
        companyService.memberUserIds(contract.getCompanyId()).forEach(userId -> send(userId, template, args));
    }

    private void send(long userId, MailTemplate template, Object... args) {
        AppUser user = userService.get(userId);
        notifier.send(user.getEmail(), user.getLocale(), template, args);
    }
}
