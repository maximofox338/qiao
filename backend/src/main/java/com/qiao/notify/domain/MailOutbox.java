package com.qiao.notify.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("mail_outbox")
public class MailOutbox extends BaseEntity {
    private String toEmail;
    private String subject;
    private String body;
    private MailStatus status;
}
