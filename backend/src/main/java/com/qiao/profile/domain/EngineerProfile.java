package com.qiao.profile.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Engineer profile. Never holds real names or ID numbers: the partner does KYC and we only keep {@code partnerRef}.
 */
@Getter
@Setter
@TableName(value = "engineer_profile", autoResultMap = true)
public class EngineerProfile extends BaseEntity {
    private Long userId;
    private String alias;
    private String headline;
    private String bio;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> skills;
    private Integer yearsExperience;
    private String region;
    private LanguageLevel englishLevel;
    private LanguageLevel spanishLevel;
    private BigDecimal hourlyRateUsd;
    private Integer availabilityHoursWeek;
    private String cvText;
    private String education;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> portfolioLinks;
    private Boolean declarationNoRestrictions;
    private Instant declarationAt;
    private ProfileStatus status;
    private String reviewNote;
    private String partnerRef;
    private BigDecimal ratingAvg;
    private Integer ratingCount;
}
