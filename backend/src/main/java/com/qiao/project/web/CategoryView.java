package com.qiao.project.web;

import com.qiao.project.domain.Category;

public record CategoryView(String code, Names name, boolean allowed) {

    public record Names(String en, String es, String zh) {
    }

    public static CategoryView from(Category c) {
        return new CategoryView(c.getCode(), new Names(c.getNameEn(), c.getNameEs(), c.getNameZh()),
                Boolean.TRUE.equals(c.getAllowed()));
    }
}
