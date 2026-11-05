package com.qiao.compliance.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One disclosure of an engineer's personal data to a client company (cross-border transfer counter).
 */
@Getter
@Setter
@TableName("transfer_event")
public class TransferEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer year;
    private Long engineerUserId;
    private Long companyId;
    private TransferDataKind dataKind;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
}
