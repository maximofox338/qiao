package com.qiao.review.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.account.domain.UserRole;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * One review per side of a COMPLETED contract: the client reviews the engineer (or the whole team), the engineer
 * (the lead of a team) reviews the client.
 */
@Getter
@Setter
@TableName("review")
public class Review extends BaseEntity {
    private Long contractId;
    private Long fromUserId;
    private Long toUserId;
    private UserRole fromSide;
    private Integer stars;
    private String comment;
}
