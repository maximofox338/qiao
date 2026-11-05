package com.qiao.project.web;

import com.qiao.project.domain.Category;

public record AdminCategoryView(String code, String nameEn, String nameEs, String nameZh, boolean allowed, int sort) {

    public static AdminCategoryView from(Category c) {
        return new AdminCategoryView(c.getCode(), c.getNameEn(), c.getNameEs(), c.getNameZh(),
                Boolean.TRUE.equals(c.getAllowed()), c.getSort());
    }
}
