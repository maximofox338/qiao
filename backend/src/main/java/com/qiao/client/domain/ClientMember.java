package com.qiao.client.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("client_member")
public class ClientMember extends BaseEntity {
    private Long companyId;
    private Long userId;
}
