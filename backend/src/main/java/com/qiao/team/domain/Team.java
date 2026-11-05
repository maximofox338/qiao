package com.qiao.team.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * A group of approved engineers that applies to projects together. Not a legal entity: contracts and payouts stay
 * per member.
 */
@Getter
@Setter
@TableName("team")
public class Team extends BaseEntity {
    private String name;
    private String description;
    private Long leadUserId;
    private TeamStatus status;
}
