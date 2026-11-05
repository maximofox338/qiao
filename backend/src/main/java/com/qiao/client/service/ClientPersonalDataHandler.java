package com.qiao.client.service;

import com.qiao.account.service.PersonalDataHandler;
import com.qiao.client.web.CompanyView;
import org.springframework.stereotype.Component;

/**
 * Company data is kept on deletion (it belongs to the company, not the person); it is exported for the member.
 */
@Component
public class ClientPersonalDataHandler implements PersonalDataHandler {

    private final CompanyService companyService;

    public ClientPersonalDataHandler(CompanyService companyService) {
        this.companyService = companyService;
    }

    @Override
    public String section() {
        return "company";
    }

    @Override
    public Object export(long userId) {
        return companyService.findCompanyOf(userId).map(CompanyView::from).orElse(null);
    }
}
