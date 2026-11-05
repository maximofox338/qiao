package com.qiao.client.web;

import com.qiao.client.domain.ClientCompany;
import com.qiao.client.domain.CompanyStatus;
import com.qiao.client.domain.SanctionsStatus;

import java.time.Instant;
import java.util.List;

public record AdminCompanyView(Long id, String name, String country, String website, String size,
                               CompanyStatus status, SanctionsStatus sanctionsStatus, Instant sanctionsCheckedAt,
                               String declarationsVersion, String declarationsHash, Instant declarationsAt,
                               String reviewNote, List<Member> members, Instant createdAt, Instant updatedAt) {

    public record Member(Long userId, String email) {
    }

    public static AdminCompanyView from(ClientCompany c, List<Member> members) {
        return new AdminCompanyView(c.getId(), c.getName(), c.getCountry(), c.getWebsite(), c.getSize(),
                c.getStatus(), c.getSanctionsStatus(), c.getSanctionsCheckedAt(), c.getDeclarationsVersion(),
                c.getDeclarationsHash(), c.getDeclarationsAt(), c.getReviewNote(), members, c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
