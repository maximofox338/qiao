package com.qiao.project.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("category")
public class Category extends BaseEntity {
    private String code;
    private String nameEn;
    private String nameEs;
    private String nameZh;
    private Boolean allowed;
    private Integer sort;
}
