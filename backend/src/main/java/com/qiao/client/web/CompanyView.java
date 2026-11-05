package com.qiao.client.web;

import com.qiao.client.domain.ClientCompany;
import com.qiao.client.domain.CompanyStatus;
import com.qiao.client.domain.SanctionsStatus;

import java.time.Instant;

public record CompanyView(Long id, String name, String country, String website, String size, CompanyStatus status,
                          SanctionsStatus sanctionsStatus, String declarationsVersion, Instant declarationsAt,
                          String reviewNote) {

    public static CompanyView from(ClientCompany c) {
        return new CompanyView(c.getId(), c.getName(), c.getCountry(), c.getWebsite(), c.getSize(), c.getStatus(),
                c.getSanctionsStatus(), c.getDeclarationsVersion(), c.getDeclarationsAt(), c.getReviewNote());
    }
}
