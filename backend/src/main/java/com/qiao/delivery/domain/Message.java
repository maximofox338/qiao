package com.qiao.delivery.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("message")
public class Message extends BaseEntity {
    private Long contractId;
    private Long senderUserId;
    private String body;
    private Boolean flagged;
}
