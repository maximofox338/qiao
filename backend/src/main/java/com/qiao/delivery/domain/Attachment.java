package com.qiao.delivery.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("attachment")
public class Attachment extends BaseEntity {
    private Long contractId;
    private Long milestoneId;
    private Long uploaderUserId;
    private String filename;
    private String contentType;
    private Long sizeBytes;
    private String storageKey;
    private String sha256;
}
