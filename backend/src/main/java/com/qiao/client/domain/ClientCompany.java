package com.qiao.client.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("client_company")
public class ClientCompany extends BaseEntity {
    private String name;
    private String country;
    private String website;
    private String size;
    private CompanyStatus status;
    private SanctionsStatus sanctionsStatus;
    private Instant sanctionsCheckedAt;
    private String declarationsVersion;
    private String declarationsHash;
    private Instant declarationsAt;
    private String reviewNote;
}
